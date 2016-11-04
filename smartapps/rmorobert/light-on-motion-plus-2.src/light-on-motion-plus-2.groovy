/**
 *  Copyright 2016 Robert Morris
 *  Portions of code based on "Light Follows Me" app by SmartThings, copyright 2014 SmartThings
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
 */

definition(
    name: "Light on Motion Plus 2",
    namespace: "RMoRobert",
    author: "Robert Morris",
    description: "Turn on one or more lights on when motion is detected and off after motion stops. Optionally dim before turning off and remember on/off states of invididual lights when motion stops to restore these states when turning back on rather than turning on all. v2 adds 0-minute 'off' option.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet@2x.png"
)

preferences {
	page(name: "pageBasic", title: "When there's activity on sensor", nextPage: "pageAdvanced", uninstall: true) {
        section("Choose a motion sensor...") {
            input "motion1", "capability.motionSensor", title: "Which motion sensor?"
        }
        section("Turn on/off light(s)...") {
            input "switches", "capability.switch", multiple: true
        }    
        section("And off when there's been no movement for...") {
            input "minutes1", "number", title: "Minutes?"
        }
        section("Dim 1 minute before turning off...") {
        	input "boolDim", "bool", defaultValue: true, required: true, title: "Dim before turning off"
       }     
       section("Only during certain times...") {
            //TODO: Would be nice to have sunset/sunrise as options here like stock app
            input "starting", "time", title: "Starting", required: false
            input "ending", "time", title: "Ending", required: false
        } 
        
        section("Only when illuminance is less than....") {
            input "lightSensor", "capability.illuminanceMeasurement", required: false
            input "lightLevel", "number", title: "Below level", required: false
            paragraph "Levels may range from 0 to over 100,000, depending on the device. Try 400 lux (300 lux if device points indoors) as an esimate for dawn/dusk levels, or check the readings on your device at an appropriate time."
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
        section("Disable app? (Use this to temporarily prevent this app from effecting changes on the lights without needing to actually uninstall the app.)") {
        	input "boolDisable", "bool", defaultValue: false, required: true, title: "Disable app"
        }
    }
    
    page(name: "pageFinal", title: "Name app and configure modes", install: true, uninstall: true) {
        section([mobileOnly:true]) {
            label title: "Assign a name", required: false
            input "modes", "mode", title: "Only when mode is", multiple: true, required: false
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

// Number of seconds that should elapse before scheduleCheck is called again to check if OK to turn off if dimming disabled
def getOffRunDelay() {
	if (minutes1 < 1) {
    	return 1
    }
	return minutes1 * 60
}

// Number of seconds that should elapse before scheduleCheck is called again to check if OK to turn off if after dimming
def getPostDimOffRunDelay() {
	return minutes1 > 1 ? 60 : 30
}

// Number of seconds that shoud elapse before scheduleCheck is called gain to check if OK to dim
def getDimRunDelay() {
	if (minutes1 < 1) {
    	return 1
    }
	else if (minutes1 == 1) {
    	return 30
    }
	// or if minutes > 1, then:
	return (minutes1 - 1) * 60
}

// Number of milliseconds that must have elapsed since motion stop to turn lights off
def getOffThreshold() {
	def retVal = 1000 * 60 * minutes1 - 1000 // 1s before "off" time, just to have some margin
	if (minutes1 == 0) {
    	if (boolDim) {
        	retVal = 29000  // 29 s
        } else {
        	retVal = 0
        }
    }
    log.debug "getOffThreshold() returning ${retVal}"
	return retVal
}

// Number of milliseconds that must have elapsed since motion stop to dim lights before turning off
def getDimThreshold() {
	def retVal = 1000 * 60 * (minutes1 - 1) - 1000 
    if (minutes1 == 1) {
    	retVal = 1000 * 30 - 1000
    } else if (minutes < 1) {
    	retVal = 0
    }
    log.debug "getDimThreshold() returning ${retVal}"
	return retVal
}

//=========================================================================
// SmartApp Methoods
//=========================================================================

def installed() {
	subscribe(motion1, "motion", motionHandler)
    state.mode = "unknown" // Use state.MODE to hold one of "unknown", "on", "dim", or "off" depending on current situation
}

def updated() {
	unsubscribe()
	subscribe(motion1, "motion", motionHandler) 
    state.mode = "unknown"
    log.trace "****** App updated *****"
}

def isModeOK() {	
	log.debug "Running isModeOK()..."
	def retVal = !modes || modes.contains(location.mode)	
	log.debug "Exiting isModeOK(). Return value = ${retVal}"
	return retVal
}

// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isRunTimeOK() {
	log.debug "Running isRunTimeOK()..."
	def retVal = true
	if (starting && ending) {
		def currTime = new Date()
		def startTime = timeToday(starting, location.timeZone)
		def stopTime = timeToday(ending, location.timeZone)
        retVal = timeOfDayIsBetween(startTime, stopTime, currTime, location.timeZone)
	}
	log.debug "Exiting isRunTimeOK(). Return value = ${retVal}"
	return retVal
}

// Returns false if lux level greater than specified threshold. Returns true
// otherwise or if not configured.
def isLuxLevelOK() {
	log.debug "Running is LuxLevelOK()..."
    def retVal = true
    if (lightSensor && lightValue) {
        def currLum = lightSensor.currentValue("illuminance").toInteger()
        if (currLum >= lightValue.toInteger()) {
            retVal = false
            log.trace "Lux level not OK because current value of ${currLum} is greter than threshold of ${lightValue}"        
        } else {
            log.trace "Lux level is OK or not configured"
        }
    }
    log.debug "Exiting isLuxLevelOK(). Return value = ${retVal}"
    return retVal
}

// Returns true if at least one switch is turned on at the moment
def isOneRealSwitchOn() {
    log.debug "Running isOneRealSwitchOn()..."
    def isOneOn = false
    switches.each {
    	log.trace "Checking switch ${it}..."
    	if (it.currentSwitch == "on") {
        	log.trace "Switch ${it} is on."
        	isOneOn = true
        } else {
        	log.trace "Switch ${it} is off."
        }
    }
    log.debug "Ran isOneRealSwitchOn(). Return value = " + isOneOn
    return isOneOn
}

// Returns true if at least switch in saved switches is turned on
def isOneSavedSwitchOn() {
	log.debug "Running isOneSavedSwitchOn()..."
    def isOneOn = false
    state.switchStates.each { key, value ->
        if (value["switch"] == "on") {
            log.trace "Saved switch ${key} is on."
            isOneOn = true
        } else {
        	log.trace "Saved switch ${key} is off."
        }
    }
    log.debug "Ran isOneSavedSwitchOn(). Return value = " + isOneOn
    return isOneOn
}

/**
 * Returns current "level" attribute for device if supports "level", otherwise returns 100 if switch on or 0 if off
 
 */
def getDimmerLevel(sw) {
	log.debug "Running getDimmerLevel for ${sw}..."
	def retVal
    def supportedAttributes
    retVal = -1
    supportedAttributes = sw.getSupportedAttributes()
    supportedAttributes.each {
    	if (it.getName() == "level") {
    		log.trace "Device ${sw} supports 'level'"
    		retVal = sw.currentLevel
        }
    }
    if (retVal == -1) {
        supportedAttributes.each {
            if (it.getName() == "switch") {
            	log.trace "Device ${sw} supports 'switch' but not 'level'"
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
    log.debug "Exiting getDimmerLevel for ${sw}. Returning ${retVal}."
    return retVal
}

/**
 * Sets "level" attribute on device if supports Switch Level capabilities. Otherwise sets "switch" attribute to "on" if lvl > 100 or "off" if = 0.
 * Returns "true" if appears to have succeeded.
 */
def setDimmerLevel(sw, lvl) {
	log.debug "Running setDimmerLevel for '${sw}' with '${lvl}'..."
	def retVal
    def supportedAttributes
    retVal = -1
    supportedAttributes = sw.getSupportedAttributes()
    supportedAttributes.each {
    	if (it.getName() == "level") {
    	log.trace "Device ${sw} supports 'level'"
    	sw.setLevel(lvl)
        retVal = true
        }
    }
    if (retVal == -1) {
        supportedAttributes.each {
            if (it.getName() == "switch") {
            	log.trace "Device ${sw} supports 'switch' but not 'level'"
          		log.trace "Device ${sw} supports 'switch' but not 'level'"
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
    log.debug "Exiting setDimmerLevel for ${sw}. Returning ${retVal}."
    return retVal
}

/**
 * Saves on/off status and dimmer level of light
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember. Will try to find same Switch as Switch Level as well
 * if possible to record dimmer status.
 */
def saveLightState(forSwitch) {
	log.debug "Running saveLightState()..."
	if (forSwitch.currentSwitch == "on") {
        def dimmerLevel = getDimmerLevel(forSwitch)
        if (dimmerLevel) {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": dimmerLevel])
        } else {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": 100])   // Guess just store 100 for brightness if can't tell and getDimmerLevel also failed
            log.warn "Couldn't find 'level' capability for ${forSwitch}, using 100 instead"
            // Can't dim bulb, so I guess don't do anything here
        }
    } else {
            state.switchStates.put(forSwitch.id, ["switch": "off"])
    }
    log.trace "Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id)
    log.debug "Exiting saveLightState()."    
}

/**
 * Saves on/off status only. Similar to saveLightState but does not save dimmer status.
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember.
 */
def saveLightOnOffState(forSwitch) {
	log.debug "Running saveLightOnOffState()..."
    //log.trace "Switch ${forSwitch.id} currently saved as ${state.switchStates.(forSwitch.id)}"
    if (!state.switchStates) {
    	state.switchStates = [:]
    }
    state.switchStates.(forSwitch.id).switch = forSwitch.currentSwitch
    log.trace "Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id)
    log.debug "Exiting saveLightOnOffState()."    
}

/**
 * Gets on/off status for a saved light
 */
def getSavedLightOnOffState(forSwitch) {
	log.debug "Running getLightOnOffState()..."
    log.trace "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
    def swState = state.switchStates.get(forSwitch.id).switch ?: "off"
    log.debug "Exiting getLightOnOffState(), returning ${swState}."    
    return swState    
}

/**
 * Gets dim level for a saved light
 */
def getSavedDimLevelState(forSwitch) {
	log.debug "Running getLightOnOffState()..."
    log.trace "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
    def dmLevel = state.switchStates.get(forSwitch.id).level ?: "100"
    log.debug "Exiting getLightOnOffState(), returning ${dmLevel}."    
    return dmLevel    
}

def motionHandler(evt) {
	log.debug "----------------Begin handling of ${evt.name}: ${evt.value}----------------"
    if (boolDisable) {
    	lob.debug "---------------- App configured to be to disabled. Exiting motion handler. ----------------"
    }
    log.info "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK() || !isModeOK()) && !boolDontObserve) {
    	log.debug "Outside specified run time, lux level, or mode. Returning."
        return
    }	
	if (evt.value == "active") {
		log.trace "Motion active. Turn on lights (or ensure on). Calling turnOnOrRestoreLights()..."
		turnOnOrRestoreLights()
	} else if (evt.value == "inactive") {
    	log.trace "Motion inactive. Deciding what to do..."
        if (boolDim) {
        	log.trace "Dimming option has been chosen. Scheduling scheduleCheck() to run after 'dimming' threshold reached."
            runIn(getDimRunDelay(), scheduleCheck)        
        } else {
        	log.trace "Dimming option not chosen. Scheduling scheduleCheck() to run after 'off' threshold reached."
        	runIn(getOffRunDelay(), scheduleCheck)
        }
	}
    log.debug "----------------End handling of ${evt.name}: ${evt.value}----------------"
}

def scheduleCheck() {
	log.debug "Running scheduleCheck()...."
    log.info "state.mode = ${state.mode}"
	def motionState = motion1.currentState("motion")
    if (motionState.value == "inactive") {
        def elapsed = now() - motionState.rawDateCreated.time
        if (elapsed >= getDimThreshold() && elapsed < getOffThreshold() && boolDim) {
        	log.trace "Motion has stayed inactive for amount of time between 'dim' and 'off' thresholds ($elapsed ms). Dimming lights"
            dimLights()
            runIn(getPostDimOffRunDelay(), scheduleCheck)
            log.trace "Done dimming. Scheduled scheduleCheck() to run again in ${minutes1 > 1 ? 60 : 30} seconds."
        }
    	else if (elapsed >= getOffThreshold()) {
            log.trace "Motion has stayed inactive long enough to cross 'off' threshold ($elapsed ms). Turning lights off."
            turnOffLights()
    	} else {
        	log.trace "Motion has not stayed inactive long enough since last check ($elapsed ms). Doing nothing"
        }
    } else {
    	log.trace "Motion is active. Do nothing and wait for inactive."
    }
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting scheduleCheck()."
}

/**
  * If configured to save previous light states, attemps to restore those. If can't find, simply turns on light.
  * Intended to be called when motion is detected after period of no motion (i.e., when lights are off or dimmed).
  */
def turnOnOrRestoreLights() {
    log.debug "Running turnOnOrRestoreLights()..."
    log.trace "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK() || !isModeOK()) && !boolDontObserve)   {
    	log.trace "Outside specified run time, lux level, or mode. Returning."
		log.debug "Exiting turnOnOrRestoreLights()."
        return
    } else if ((!isRunTimeOK() || !isLuxLevelOK() || !isModeOK()) && boolDontObserve) {
    	log.trace "Outside specified run time, lux level, or mode, but configured not to observe. However, this does not apply to turning light on. Exiting."
		log.debug "Exiting turnOnOrRestoreLights()."
    	return
    }
    
    if (state.mode != "dim" && isOneRealSwitchOn()) {
    	log.trace "Current mode is not 'dim' and at least on swtich is on. Assume this is desired state."
        log.trace "Setting mode to 'on' in case it isn't."
        state.mode = "on"
    }
    else if (state.mode == "dim" || state.mode == "off" || (state.mode == "on" && !isOneRealSwitchOn())) {
    	if (!isOneSavedSwitchOn() || !boolRemember) {
        	log.trace "No switches were saved as 'on' when motion last stopped or app configured to not remember individual light on/off states. Turning all on and restoring to saved dim level."
            switches.each {
            	it.on()
                setDimmerLevel(it, getSavedDimLevelState(it))
            }
            switches.on()
            state.mode = "on"
        } else { 
        	log.trace "Mode is either 'dim' or 'off,' at leat one switch was saved as on, and app configured to restore states, so restore lights to last known state."
            switches.each {
                def savedLightState = getSavedLightOnOffState(it)
                if (savedLightState != "off") {
                    log.trace "${it} was saved as on. Turning on and resotring previous dimmer level."
                    it.on()                    
                    def prevLevel = getSavedDimLevelState(it)
                    setDimmerLevel(it, prevLevel)
                } else {
                    log.trace "${it} was saved as off. Not turning on."
                }
            }
        }
    }
    else if (state.mode == "unknown") {
    	log.trace "State unknown. Turning on all switches."
        switches.on()
    }
    state.mode = "on"    
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting turnOnOrRestoreLights()."
}

/**
  * Dims lights to pre-specified dimming level.
  * Intended to be called when motion has been inactive for a period of time between the "dim" and "off" thresholds.
  */
def dimLights() {
	log.debug "Running dimLights()..."
    log.info "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK() || !isModeOK()) && !boolDontObserve)  {
    	log.debug "Outside specified run time, lux level, or mode. Returning."
        return
    } else if (boolDontObserve) {
    	log.debug "Outside specified run time, lux level, or mode, but configured not to observe. Continuing..."
    }
    state.switchStates = [:]
    for (sw in switches) {    
    	log.trace "Saving light state for ${sw}"
    	saveLightState(sw)
    	log.trace "Dimming ${sw}"
        if (sw.currentSwitch != "off") {
            def toLevel = dimToLevel
            if (getDimmerLevel(sw) <= toLevel) {
                // If light is currently at or less than "dim to" level, dim it as low as possible
                toLevel = 1
            } 
            setDimmerLevel(sw, toLevel)
            log.trace "Dimmed ${sw} to ${toLevel}"
        } else {
            log.trace "Not dimming ${sw} because is off."
        } 
    }
    state.mode = "dim"
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting dimLights()."
}

/**
  * Turns off all lights.
  * Intended to be called when motion has been inactive for a period of time greater than or equal to the "off" threshold.
  */
def turnOffLights() {
	log.debug "Running turnOffLights()..."
    log.info "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK() || !isModeOK()) && !boolDontObserve)  {
    	log.debug "Outside specified run time, lux level, or mode. Returning."
        return
    } else if (boolDontObserve) {
    	log.debug "Outside specified run time, lux level, or mode, but configured not to observe. Continuing..."
    }
    switches.each {
    	log.trace "Saving on/off state then turning off: ${it}"
    	saveLightOnOffState(it)
        it.off()
    }
    state.mode = "off"
    log.trace "Turned off all lights."
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting turnOffLights()."
}