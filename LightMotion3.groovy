/**
 *  Copyright 2018 Robert Morris
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Author: Robert Morris
 *
 * Version: 3.0 Beta 2
 *
 * CHANGELOG
 * 3.0: Moved to parent/child app model; bug fixes/improvements for when motion detected/lights on after mode changed
*  2.0: Code refactoring, better handling of light changes when motion inactive
 * WISHLIST: multiuple motion sensors, introduction of different level for "night mode", possible default settings/scene support

 *
 */

definition(
        name: "Lights on Motion Plus (Child App)",
        namespace: "RMoRobert",        
        parent: "RMoRobert:Lights on Motion Plus",
        author: "Robert Morris",
        description: "Do not install directly. Install Lights on Motion Plus app, then create new automations using that app.",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: ""
)

preferences {
    page(name: "pageBasic", title: "When there's activity on sensor", nextPage: "pageAdvanced", uninstall: true) {
        section("Choose a motion sensor...") {
            input "motion1", "capability.motionSensor", title: "Which motion sensor(s)?", multiple: true
        }
        section("Turn on/off light(s)...") {
            input "switches", "capability.switch", multiple: true
        }
        section("And off when there's been no movement for...") {
            input "minutes1", "number", title: "Minutes?"
        }
        section("Dim for 30 seconds before turning off...") {
            input "boolDim", "bool", defaultValue: true, required: true, title: "Dim before turning off"
        }
        section("Only during certain times...") {
            //TODO: Would be nice to have sunset/sunrise as options here
            input "starting", "time", title: "Starting", required: false
            input "ending", "time", title: "Ending", required: false
        }

        section("Only when illuminance is less than....") {
            input "lightSensor", "capability.illuminanceMeasurement", required: false
            input "lightLevel", "number", title: "Below level", required: false
        }
    }
    page(name: "pageAdvanced", title: "Advanced options", nextPage: "pageFinal") {

        section("Always turn off lights after motion stops, even if outside of specifed time, illuminance, or mode conditions?") {
            input "boolDontObserve", "bool", defaultValue: false, required: true, title: "Always turn off after motion stops"
        }
        section("If multiple lights are selected, remember on/off state of individual lights when motion stops and restore when motion starts?"){
            input "boolRemember", "bool", defaultValue: true, required: true, title: "Remember states?"
            paragraph "By default, this app remembers the on/off state of each light chosen previously and restores the on/off state of each light when motion resumes after inactivity, rather than turning all lights back on (unless all were off, then all are turned back on)."
        }
        
        section("Logging") {
            input "logEnable", "bool", title: "Enable verbose/debug logging", required: false
        }
        
        section("Disable app? (Use this to temporarily prevent this app from effecting changes on the lights without needing to actually uninstall the app.)") {
            input "boolDisable", "bool", defaultValue: false, required: true, title: "Disable app"
        }
    }

    page(name: "pageFinal", title: "Name app and configure modes", install: true, uninstall: true) {
        section([mobileOnly:true]) {
            label title: "Assign a name", required: false
            input "modes", "mode", title: "Only turn on/off lights when mode is", multiple: true, required: false
            //input "nightModes", "mode", title: 'Turn lights on/off with "night mode" setting if mode is', multiple: true, required: false
        }
    }
}

//=========================================================================
// "Constant" declarations and value-calculator functions
//=========================================================================

// Percentage to dim lights to if dimmed
def getDimToLevel() {
    return 10
}

// Number of seconds that should elapse between motion inactivity (if dimming disabled) and light turninng off
def getOffRunDelay() {
    if (minutes1 < 1) {
        return 1
    }
    return minutes1 * 60
}

// Number of seconds that should elapse between dimming (if enabled) and light turning off
def getPostDimOffRunDelay() {
    return 30
}

// Number of seconds that shoud elapse between motion inactivity and light dimming
def getDimRunDelay() {
    if (minutes1 < 1) {
        return 1
    }
    // or if minutes > 1, then:
    return (minutes1 - 1) * 60
}


//=========================================================================
// App Methods
//=========================================================================

def installed() {
    subscribe(motion1, "motion", motionHandler)
    state.isDimmed = false
}

def updated() {
    unsubscribe()
    subscribe(motion1, "motion", motionHandler)
    log.trace "****** App updated *****"
    state.isDimmed = false
}

def isModeOK() {
    if (logEnable) log.debug "Running isModeOK()..."
    def retVal = !modes || modes.contains(location.mode)
    if (logEnable) log.debug "Exiting isModeOK(). Return value = ${retVal}"
    return retVal
}

// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isRunTimeOK() {
    if (logEnable) log.trace "Running isRunTimeOK()..."
    def retVal = true
    if (starting && ending) {
        def currTime = new Date()
        def startTime = timeToday(starting, location.timeZone)
        def stopTime = timeToday(ending, location.timeZone)
        retVal = timeOfDayIsBetween(startTime, stopTime, currTime, location.timeZone)
    }
    if (logEnable) log.trace "Exiting isRunTimeOK(). Return value = ${retVal}"
    return retVal
}

// Returns false if lux level greater than specified threshold. Returns true
// otherwise or if not configured.
def isLuxLevelOK() {
    if (logEnable) log.trace "Running is LuxLevelOK()..."
    def retVal = true
    if (lightSensor && lightValue) {
        def currLum = lightSensor.currentValue("illuminance").toInteger()
        if (currLum >= lightValue.toInteger()) {
            retVal = false
            if (logEnable) log.debug "Lux level not OK because current value of ${currLum} is greter than threshold of ${lightValue}"
        } else {
            if (logEnable) log.debug "Lux level is OK or not configured"
        }
    }
    if (logEnable) log.trace "Exiting isLuxLevelOK(). Return value = ${retVal}"
    return retVal
}

// Returns true if time, mode, and lux are all OK; otherwise, false.
def isAllOK() {
    return (isRunTimeOK() && isLuxLevelOK() && isModeOK())
}

// Returns true if at least one switch is turned on at the moment
def isOneRealSwitchOn() {
    if (logEnable) log.trace "Running isOneRealSwitchOn()..."
    def isOneOn = false
    switches.each {
        if (logEnable) log.trace "Checking switch ${it}..."
        if (it.currentSwitch == "on") {
            if (logEnable) log.debug "Switch ${it} is on."
            isOneOn = true
        } else {
            if (logEnable) log.debug "Switch ${it} is off."
        }
    }
    if (logEnable) log.trace "Ran isOneRealSwitchOn(). Return value = " + isOneOn
    return isOneOn
}

// Returns true if at least switch in saved switches is turned on
def isOneSavedSwitchOn() {
    if (logEnable) log.trace "Running isOneSavedSwitchOn()..."
    def isOneOn = false
    state.switchStates.each { key, value ->
        if (value["switch"] == "on") {
            if (logEnable) log.debug "Saved switch ${key} is on."
            isOneOn = true
        } else {
            if (logEnable) log.debug "Saved switch ${key} is off."
        }
    }
    if (logEnable) log.trace "Ran isOneSavedSwitchOn(). Return value = " + isOneOn
    return isOneOn
}

/**
 * Returns current "level" attribute for device if supports "level", otherwise returns 100 if switch on or 0 if off
 */
def getDimmerLevel(sw) {
    if (logEnable) log.trace "Running getDimmerLevel for ${sw}..."
    def retVal
    def supportedAttributes
    retVal = -1
    supportedAttributes = sw.getSupportedAttributes()
    supportedAttributes.each {
        if (it.getName() == "level") {
            if (logEnable) log.debug "Device ${sw} supports 'level'"
            retVal = sw.currentLevel
        }
    }
    if (retVal == -1) {
        supportedAttributes.each {
            if (it.getName() == "switch") {
                if (logEnable) log.debug "Device ${sw} supports 'switch' but not 'level'"
                if (sw.currentSwitch == "on") {
                    retVal = 100
                } else {
                    retVal = 0
                }
            }
        }
    }
    if (retVal == -1 ) {
        log.warn "Device ${sw} does not support 'level' or 'switch' capabilties."
        retVal = 0
    }
    if (logEnable) log.trace "Exiting getDimmerLevel for ${sw}. Returning ${retVal}."
    return retVal
}

/**
 * Sets "level" attribute on device if supports Switch Level capabilities. Otherwise sets "switch" attribute to "on" if lvl > 0 or "off" if = 0.
 * Returns "true" if appears to have succeeded.
 */
def setDimmerLevel(sw, lvl) {
    if (logEnable) log.trace "Running setDimmerLevel for '${sw}' with '${lvl}'..."
    def retVal
    def supportedAttributes
    retVal = -1
    supportedAttributes = sw.getSupportedAttributes()
    supportedAttributes.each {
        if (it.getName() == "level") {
            if (logEnable) log.debug "Device ${sw} supports 'level'"
            sw.setLevel(lvl)
            retVal = true
        }
    }
    if (retVal == -1) {
        supportedAttributes.each {
            if (it.getName() == "switch") {
                if (logEnable) log.debug "Device ${sw} supports 'switch' but not 'level'"
                if (logEnable) log.debug "Device ${sw} supports 'switch' but not 'level'"
                if (lvl > 0) {
                    sw.on()
                } else {
                    sw.off()
                }
                // Ambiguous case, not sure if should do or not:
                //retVal = true
            }
        }
    }
    if (retVal == -1 ) {
        log.warn "Device ${sw} does not support 'level' or 'switch' capabilties."
    }
    if (logEnable) log.trace "Exiting setDimmerLevel for ${sw}. Returning ${retVal}."
    return retVal
}

/**
 * Saves on/off status and dimmer level of light
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember. Will try to find same Switch as Switch Level as well
 * if possible to record dimmer status.
 */
def saveLightState(forSwitch) {
    if (logEnable) log.trace "Running saveLightState()..."
    if (forSwitch.currentSwitch == "on") {
        def dimmerLevel = getDimmerLevel(forSwitch)
        if (dimmerLevel) {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": dimmerLevel])
        } else {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": 100])   // Guess just store 100 for brightness if can't tell and getDimmerLevel also failed
            log.debug "Couldn't find 'level' capability for ${forSwitch}, using 100 instead"
            // Can't dim bulb, so I guess don't do anything here
        }
    } else {
        state.switchStates.put(forSwitch.id, ["switch": "off"])
    }
    if (logEnable) log.debug "Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id)
    log.trace "Exiting saveLightState()."
}

/**
 * Saves on/off status only. Similar to saveLightState but does not save dimmer status.
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember.
 */
def saveLightOnOffState(forSwitch) {
    if (logEnable) log.trace "Running saveLightOnOffState()..."
    //if (logEnable) log.trace "Switch ${forSwitch.id} currently saved as ${state.switchStates.(forSwitch.id)}"
    if (!state.switchStates) {
        state.switchStates = [:]
    }
    state.switchStates.(forSwitch.id).switch = forSwitch.currentSwitch
    if (logEnable) {
        log.debug "Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id)
        log.trace "Exiting saveLightOnOffState()."
    }
}

/**
 * Gets on/off status for a saved light
 */
def getSavedLightOnOffState(forSwitch) {
    if (logEnable) {
        log.trace "Running getLightOnOffState()..."
        log.debug "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
     }
    def swState = state.switchStates.get(forSwitch.id).switch ?: "off"
    if (logEnable) log.trace "Exiting getLightOnOffState(), returning ${swState}."
    return swState
}

/**
 * Gets dim level for a saved light
 */
def getSavedDimLevelState(forSwitch) {
    if (logEnable) {
        log.trace "Running getLightOnOffState()..."
        log.debug "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
    }
    def dmLevel = state.switchStates.get(forSwitch.id).level ?: "100"
    if (logEnable) log.trace "Exiting getLightOnOffState(), returning ${dmLevel}."
    return dmLevel
}

def motionHandler(evt) {
    //TODO: Not tested with multiple sensors. Provide way to make sure does not do inactive tasks when other(s) still active, etc.
    if (logEnable) log.trace "----------------Begin handling of ${evt.name}: ${evt.value}----------------"
    if (boolDisable) {
        log.trace "App configured to be to disabled. Exiting motion handler."
        return
    }
    if (evt.value == "active") {
        log.debug "Motion detected; cancelling dim/off timers and deciding what to do"
        unschedule(dimLights)
        unschedule(turnOffLights)

        if (isAllOK()) {
            log.trace "Mode, etc. not prohibited; continuing"
            if (state.isDimmed) {
                log.debug "Lights dimmed; restoring to previous states/levels"
                restoreAllLights()
            } else {
                log.trace "Lights not currently dimmed; checking if any on"
                if (isOneRealSwitchOn()) {
                    log.debug "Lights not changed/turned on because one or more already on"
                } else {
                    log.debug "No lights on; restoring lights..."
                    restoreAllLights()
                }
            }
        } else {
            log.trace "Outside specified run time, lux level, or mode; not doing anything."
            return
        }
    } else if (evt.value == "inactive") {
        log.trace "Motion inactive; cancelling timers"
        unschedule(dimLights)
        unschedule(turnOffLights)
        if (isAllOK()) {
            if (boolDim) {
                log.debug "Setting dim timer for ${getDimRunDelay()}s because motion inactive"
                runIn(getDimRunDelay(), dimLights)
            } else {
                log.debug "Setting off timer for ${getOffRunDelay()}s because motion inactive and dimming disabled"
                runIn(getOffRunDelay(), turnOffLights)
            }
        } else {
            log.debug "Motion inactive but outside of specified mode/time/lux/etc."
            if (boolDontObserve) {
                if (boolDim) {
                    log.debug "Configured to always turn off lights; setting 'dim' timer for {$getDimRunDelay()}s"
                    runIn(getDimRunDelay(), dimLights)
                } else {
                    log.debug "Configured to always turn off lights; setting 'off' timer for {$getDimRunDelay()}s"
                    runIn(getOffRunDelay(), turnOffLights)
                }
            } else {
                log.debug "Configured to strictly observe mode/time/lux/etc; will not dim/turn off"
            }
        }
    }
    log.trace "----------------End handling of ${evt.name}: ${evt.value}----------------"
}

/**
 * If configured to save previous light states, attempts to restore those. If can't find, simply turns on light.
 * Intended to be called when motion is detected
 */
def restoreAllLights() {
    if (logEnable) log.trace "Running restoreAllLights()..."
    if (logEnable) log.debug "state.isDimmed = ${state.isDimmed}"

    if (isOneSavedSwitchOn()) {
        switches.each {
            def savedState = getSavedLightOnOffState(it)
            if (logEnable) log.trace "  Saved light state for ${it} was ${savedState}"
            if (savedState != "off") {
                def savedLevel = getSavedDimLevelState(it)
                if (logEnable) log.trace "  Saved light level was ${savedLevel}"
                if (savedLevel <= 0) {
                    // Just in case this happens...
                    savedLevel = 100
                    if (logEnable) log.trace "  Brightness was saved as 0; set to ${savedLevel}"
                }
                setDimmerLevel(it, savedLevel)
                it.on()
                if (logEnable) log.trace "  Restored previous brightness and turned on ${it}"
            } else {
                if (logEnable) log.debug("${it} was saved as off; not turning on")
            }
        }
    } else {
        log.debug "No saved lights were on; turning all on"
        // TODO: Want to specify default brightness?
        switches.on()
    }

    state.isDimmed = false
    if (logEnable) log.trace "Exiting turnOnOrRestoreLights()."
}

/**
 * Dims lights to pre-specified dimming level.
 * Intended to be called when motion has been inactive for a period of time between the "dim" and "off" thresholds.
 */
def dimLights() {
    if (logEnable) log.trace "Running dimLights()..."
    if (!isOneRealSwitchOn()) {
        log.debug "  Not dimming because no lights on; returning"
        return
    }
    state.isDimmed = true
    state.switchStates = [:]
    for (sw in switches) {
        if (isAllOK()) {
            if (logEnable) log.debug "  Saving light state for ${sw}"
            saveLightState(sw)
        } else {
            log.debug "  Not saving state because outside specified mode/lux/etc. conditions"
        }
        log.debug "    Dimming ${sw}"
        if (sw.currentSwitch != "off") {
            def toLevel = dimToLevel
            if (getDimmerLevel(sw) <= toLevel) {
                // If light is currently at or less than "dim to" level, dim it as low as possible
                toLevel = 1
            }
            setDimmerLevel(sw, toLevel)
            if (logEnable) log.debug "  Dimmed ${sw} to ${toLevel}"
        } else {
            if (logEnable) log.debug "  Not dimming ${sw} because is off."
        }
    }
    state.isDimmed = true
    if (logEnable) log.debug "  Setting 'off' timer for ${getPostDimOffRunDelay()}s"
    unschedule(dimLights)
    unschedule(turnOffLights)
    runIn(getPostDimOffRunDelay(), turnOffLights)
    if (logEnable) log.trace "Exiting dimLights()."
}

/**
 * Turns off all lights.
 * Intended to be called when motion has been inactive and specified intervals have passed; saves states before
 * turning off unless in prohibited mode
 */
def turnOffLights() {
    if (logEnable) log.trace "Running turnOffLights()..."
    if ((!isAllOK()) && !boolDontObserve)  {
        if (logEnable) log.debug "  Outside specified run time, lux level, or mode. Returning."
        return
    } else if (boolDontObserve) {
        if (logEnable) log.debug "  Outside specified run time, lux level, or mode, but configured not to observe. Continuing..."
        switches.off()

    }
    switches.each {
        if (isAllOK()) {
            if (logEnable) log.trace "  Saving on/off state then turning off: ${it}"
            saveLightOnOffState(it)
            it.off()
        }
        else {
            if (logEnable) log.trace "  Outside specified mode/time/lux/etc. conditions; turning off without saving state: ${it}"
            it.off()
        }
    }
    state.isDimmed = false
    log.debug "  Turned off all lights."
    if (logEnable) log.trace "Exiting turnOffLights()."
}