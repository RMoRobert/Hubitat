/**
 * ==========================  Lights on Motion Plus (Child App) ==========================
 *  TO INSTALL:
 *  Add code for parent app first and then and child app (this). To use, install/create new
 *  instance of parent app.
 *
 *  Copyright 2018-2019 Robert Morris
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  Last modified: 2019-04-18
 * 
 *  Changelog:
 * 
 * 4.0: - Added "night mode" lighting option (turns on to specified level/settings if run in "night" mode[s]; restores "normal"/previous settings when next run in non-night mode in mode that will turn lights on)
 * 3.1: - Added "kill switch" option (completely disables app regardless of any other options selected in app)
 * 	- Changed boolean in-app "disable app" option to "soft kill switch" option (if switch on, app will not turn lights on; turn-off behavior determined by other app options)
 *      - Added option for additional sensors to keep (but not turn) lights on;
 *	- Fixed bug with multiple "turn on" sensors
 * 3.0: Moved to parent/child app model; bug fixes/improvements for when motion detected/lights on after mode changed
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
    page(name: "pageMain", title: "Lights on Motion Plus", install: true, uninstall: true) {
        section("Choose lights and sensors") {
            input "motion1", "capability.motionSensor", title: "When motion is detected on sensor(s)", multiple: true
            input "switches", "capability.switch", title: "Turn on these lights (if none already on)", multiple: true, required: true
            input "minutes1", "number", title: "Turn all off after this many minutes", required: true
            input "boolDim", "bool", defaultValue: true, title: "Dim lights before turning off"
			input "motion2", "capability.motionSensor", title: "Select additional motion sensors to keep lights on (but not turn them on)", multiple: true, required: false
        }
        section("Restrictions", hideable: true, hidden: false) {
            //TODO: Would be nice to have sunset/sunrise as options here
            input "starting", "time", title: "Ony after this time", required: false
            input "ending", "time", title: "Only before this time", required: false
            input "lightSensor", "capability.illuminanceMeasurement", title: "Only when illuminance on this light sensor", required: false, width: 6
            input "lightLevel", "number", title: "is below this illuminance level", required: false, width: 6
			input "softKillSwitch", "capability.switch", title: "Do not turn lights on when this switch is on (will still turn off if \"always turn off\" selected below; otherwise will also not turn off)"
			input "killSwitch", "capability.switch", title: "Do not turn lights on or off when this switch is on (no exceptions; \"kill switch\")"
        }
		
		section("Night Mode", hideable: true, hidden: true ) {
			input "nightModes", "mode", title: 'Select mode(s) to consider "night mode"', multiple: true, required: false
			paragraph("In night mode(s), turn lights on to...")
			input(name: "nightBri", type: "number", title: "brightness/level:", description: "1-100", width: 3, required: false)
			input(name: "nightCt", type: "number", title: "color temperature:", description: "~2000-6500", width: 3, required: false)
			input(name: "nightHue", type: "number", title: "color with hue value:", description: "0-100", width: 3, required: false)
			input(name: "nightSat", type: "number", title: "saturation value:", description: "0-100", width: 3, required: false)
			paragraph("You must set brightnes and/or color temperature <em>or</em> all of brightness, hue and saturation")
            input "nightIgnoreSwitches", "capability.switch", title: "Do NOT turn on these lights in night mode (optional; will otherwise turn on all lights selected for non-night modes)", multiple: true, required: false
			paragraph("Note that lights will immediately turn off (ater delay) in night mode(s) rather than dimming first.")
		}
		
		section("Customizations", hideable: true, hidden: true) {
			input "postDimTime", "number", defaultValue: 30, required: true, title: "Number of seconds to dim before turning off"
			input "dimToLevel", "number", defaultValue: 10, required: true, description: "0-100", title: "Dim to this level"
            input "boolDontObserve", "bool", defaultValue: true, title: "Always turn off after motion stops, even if outside specified time/motion/lux/etc. or \"do not turn on\"-switch conditions (unless \"kill switch\" restriction enabled)"
            input "boolRemember", "bool", defaultValue: true, title: "Remember states of indiviudal lights before dimming and turning off (do not necessarily turn all back on with motion)"
		}

        section("Name and modes", hideable: true, hidden: false) {
            label title: "Assign a name", required: false
            input "modes", "mode", title: "Only turn on lights when mode is (may still turn off; see above)", multiple: true, required: false
            input "enableDebugLogging", "bool", title: "Enable debug logging", required: false
            //input "enableTraceLogging", "bool", title: "Enable verbose/trace logging (for development)", required: false
        }
    }
}

//=========================================================================
// "Constant" declarations and value-calculator functions
//=========================================================================

// Number of seconds that should elapse between motion inactivity (if dimming disabled) and light turninng off
def getOffRunDelay() {
    if (minutes1 < 1) {
        return 1
    }
    return minutes1 * 60
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
	log.trace "App installed"
	initialize()
}

def updated() {
    log.trace "App updated"
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	state.switchStates = [:]
    subscribe(motion1, "motion", motionHandler)
    subscribe(motion2, "motion", motionHandler)
    state.isDimmed = false
	log.trace "App initialized"
	
}

def isNightMode() {
    logTrace("Running isNightMode()...")
    def retVal = nightModes && nightModes.contains(location.mode)
    logTrace("Exiting isNightMode(). Return value = ${retVal}")
    return retVal
}

// Returns true if mode in one of specified "turn on" modes (does NOT include night modes)
def isModeOK() {
    logTrace("Running isModeOK()...")
    def retVal = !modes || modes.contains(location.mode)
    logTrace("Exiting isModeOK(). Return value = ${retVal}")
    return retVal
}

// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isRunTimeOK() {
    logTrace("Running isRunTimeOK()...")
    def retVal = true
    if (starting && ending) {
        def currTime = new Date()
        def startTime = timeToday(starting, location.timeZone)
        def stopTime = timeToday(ending, location.timeZone)
        retVal = timeOfDayIsBetween(startTime, stopTime, currTime, location.timeZone)
    }
    logTrace("Exiting isRunTimeOK(). Return value = ${retVal}")
    return retVal
}

// Returns false if lux level greater than specified threshold. Returns true
// otherwise or if not configured.
def isLuxLevelOK() {
    logTrace("Running is LuxLevelOK()...")
    def retVal = true
    if (lightSensor && lightValue) {
        def currLum = lightSensor.currentValue("illuminance").toInteger()
        if (currLum >= lightValue.toInteger()) {
            retVal = false
            logTrace("Lux level not OK because current value of ${currLum} is greter than threshold of ${lightValue}")
        } else {
			logTrace("Lux level is OK or not configured")
        }
	}
	logTrace("Exiting isLuxLevelOK(). Return value = ${retVal}")
    return retVal
}

def isKillSwitchOK() {
	def retVal = true
	retVal = killSwitch?.currentSwitch != "on"
	logTrace("Ran isKillSwitchOK(); returning ${retVal}")
	return retVal
}

def isSoftKillSwitchOK() {
	def retVal = true
	retVal = softKillSwitch?.currentSwitch != "on"
	logTrace("Ran isSoftKillSwitchOK(); returning ${retVal}")
	return retVal
}

// Returns true if time, mode, and lux, and hard and soft killswitches are all OK; otherwise, false. (Night mode considered OK.)
def isAllOK() {
    return (isRunTimeOK() && isLuxLevelOK() && (isModeOK() || isNightMode()) && isKillSwitchOK() && isSoftKillSwitchOK())
}

// Returns true if time, mode, lux, and hard killswitch are all OK; otherwise, false.
def isAllButKillOK() {
    return (isRunTimeOK() && isLuxLevelOK() && isModeOK() && isSoftKillSwitchOK())
}

// Returns true if time, mode, lux, and soft killswitch are all OK; otherwise, false.
def isAllButSoftKillOK() {
    return (isRunTimeOK() && isLuxLevelOK() && isModeOK() && isKillSwitchOK())
}

// Returns true if at least one switch is turned on at the moment
def isOneRealSwitchOn() {
    logTrace("Running isOneRealSwitchOn()...")
    def isOneOn = false
    switches.each {
        if (logEnable) log.trace "Checking switch ${it}..."
        if (it.currentSwitch == "on") {
            logTrace("Switch ${it} is on.")
            isOneOn = true
        } else {
            logTrace("Switch ${it} is off.")
        }
    }
    logTrace("Ran isOneRealSwitchOn(). Return value = " + isOneOn)
    return isOneOn
}

// Returns true if at least switch in saved switches is turned on
def isOneSavedSwitchOn() {
    logTrace("Running isOneSavedSwitchOn()...")
    def isOneOn = false
    state.switchStates.each { key, value ->
        if (value["switch"] == "on") {
            logTrace("Saved switch ${key} is on.")
            isOneOn = true
        } else {
            logTrace("Saved switch ${key} is off.")
        }
    }
    logTrace("Ran isOneSavedSwitchOn(). Return value = " + isOneOn)
    return isOneOn
}

/**
 * Returns current "level" attribute for device if supports "level", otherwise returns 100 if switch on or 0 if off
 */
def getDimmerLevel(sw) {
    logTrace("Running getDimmerLevel for ${sw}...")
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
                logDebug("Device ${sw} supports 'switch' but not 'level'")
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
    logTrace("Exiting getDimmerLevel for ${sw}. Returning ${retVal}.")
    return retVal
}

/**
 * Sets "level" attribute on device if supports Switch Level capabilities. Otherwise sets "switch" attribute to "on" if lvl > 0 or "off" if == 0
 * Returns "true" if appears to have succeeded.
 */
def setDimmerLevel(sw, lvl) {
    logTrace("Running setDimmerLevel for '${sw}' with '${lvl}'...")
    def retVal
    def supportedAttributes
    retVal = -1
    supportedAttributes = sw.getSupportedAttributes()
    supportedAttributes.each {
        if (it.getName() == "level") {
            logTrace("Device ${sw} supports 'level'")
            sw.setLevel(lvl)
            retVal = true
        }
    }
    if (retVal == -1) {
        supportedAttributes.each {
            if (it.getName() == "switch") {
                logDebug("Device ${sw} supports 'switch' but not 'level'")
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
    logTrace("Exiting setDimmerLevel for ${sw}. Returning ${retVal}.")
    return retVal
}

/**
 * Saves on/off status and dimmer level of light
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember. Will try to find same Switch as Switch Level as well
 * if possible to record dimmer status.
 */
def saveLightState(forSwitch) {
    logTrace("Running saveLightState()...")
    if (forSwitch.currentSwitch == "on") {
        def dimmerLevel = getDimmerLevel(forSwitch)
        if (dimmerLevel) {
			def ct = getColorTemperature(forSwitch)
			if (ct) {
				state.switchStates.put(forSwitch.id, ["switch": "on", "level": dimmerLevel, "ct": ct])
			} else {
            	state.switchStates.put(forSwitch.id, ["switch": "on", "level": dimmerLevel])
			}
        } else {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": 100])   // Guess just store 100 for brightness if can't tell and getDimmerLevel also failed
            logDebug("Couldn't find 'level' capability for ${forSwitch}, using 100 instead")
            // Can't dim bulb, so I guess don't do anything here
        }
    } else {
        state.switchStates.put(forSwitch.id, ["switch": "off"])
    }
    logTrace("Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id))
    logTrace("Exiting saveLightState().")
}

/**
 * Saves on/off status only. Similar to saveLightState but does not save dimmer status.
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember.
 */
def saveLightOnOffState(forSwitch) {
    logTrace("---------------  <b>Running saveLightOnOffState()...</b> ------------")
	if (isNightMode()) {
		log.warn "Night mode, not saving light state even though called to"
		return
	}
    if (!state.switchStates) {
        state.switchStates = [:]
    }
	logTrace("Switch ${forSwitch} is ${forSwitch.currentSwitch}")
	if (state.switchStates.get(forSwitch.id) && state.switchStates.get(forSwitch.id).get("switch")) {
    	state.switchStates.get(forSwitch.id).put("switch", forSwitch.currentSwitch)
	}
	else {
    	state.switchStates.put(forSwitch.id, ["switch": forSwitch.currentSwitch])
	}
    logTrace("Just saved for ${forSwitch}: " + state.switchStates.get(forSwitch.id))
    logTrace("Exiting saveLightOnOffState().")	
}

/**
 * Returns color temperature of given switch/bulb *if* colorTemperature attribute exists, otherwise returns null
 */
def getColorTemperature(forSwitch) {
    logTrace("Running getColorTemperature()...")
	def retVal = null
	forSwitch.getSupportedAttributes().each {
		if (it.getName() == "colorTemperature") {
			retVal = forSwitch.currentValue("colorTemperature")
		}
	}
	logTrace("Exiting getColorTemperature(), returning ${retVal}")
	return retVal
}


/**
 * Gets on/off status for a saved light
 */
def getSavedLightOnOffState(forSwitch) {
    logTrace("Running getLightOnOffState()...")
    logTrace("Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}")
    def swState = state.switchStates.get(forSwitch.id).switch ?: "off"
    logTrace("Exiting getLightOnOffState(), returning ${swState}.")
    return swState
}

/**
 * Gets dim level for a saved light
 */
def getSavedDimLevelState(forSwitch) {
    logTrace("Running getLightOnOffState()...")
    logTrace("Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}")
    def dmLevel = state.switchStates.get(forSwitch.id).level ?: "100"
    logTrace("Exiting getLightOnOffState(), returning ${dmLevel}.")
    return dmLevel
}

/**
 * Returns true if all sensors (turn-on and stay-on sensors) are inactive, intended
 * to be called when checking if OK to dim (or turn off) lights
*/
def isEverySensorInactive() {
	logTrace("-- Running isEverySensorInactive() ... --")
	def allInactive = true
	motion1.each {
		logTrace(/${it} is ${it.latestValue("motion")}/)
		if(it.latestValue("motion") == "active") {
			allInactive = false
		}
	}
	motion2.each {
		logTrace(/${it} is ${it.latestValue("motion")}/)
		if(it.latestValue("motion") == "active") {
			allInactive = false
		}
	}
	logTrace("-- Exiting isEverySensorInactive(), returning ${allInactive} --")
	return allInactive
}

def motionHandler(evt) {
    logTrace("----------------Begin handling of ${evt.name}: ${evt.value}----------------")
	def isTurnOnSensor = false 
	motion1.each {
		if(it.deviceId == evt.deviceId) {
			isTurnOnSensor = true
		}
	}
    if (isTurnOnSensor && evt.value == "active") {
        log.debug "Motion detected; cancelling dim/off timers and deciding what to do"
        unschedule(dimLights)
        unschedule(turnOffLights)
        if (isAllOK()) {
            logTrace("Mode, etc. not prohibited; continuing")
            if (state.isDimmed) {
                log.debug "Lights dimmed; restoring to previous states/levels"
                restoreAllLights()
            } else {
                logTrace("Lights not currently dimmed; checking if any on")
                if (isOneRealSwitchOn()) {
                    logDebug("Lights not changed/turned on because one or more already on")
                } else {
					if (isNightMode()) {
						logTrace("All OK and in night mode. Turning on to night mode settings.")
						turnOnToNightMode()
					} else {
						log.debug "No lights on; restoring lights..."
						restoreAllLights()
					}
				}
            }
        } else {
			if (state.isDimmed) {
				logTrace("Outside specified run time, lux level, or mode but lights are dimmed; restoring if any are on.")
				if (isOneRealSwitchOn()) {
					logTrace("Outside specified mode, time, etc., but lights were dimmed and still on; restoring state")
					restoreAllLights()
				}
			} else {
            	logTrace("Outside specified run time, lux level, or mode; not doing anything.")
			}
				return
        }
    } else if (evt.value == "inactive") {
		if (!isEverySensorInactive()) {
			logTrace("One motion sensor became in active but other(s) still reporting active; returning")
			return
		}
        logTrace("Motion inactive; cancelling timers")
        unschedule(dimLights)
        unschedule(turnOffLights)
        if (isAllOK()) {
            if (boolDim && !isNightMode()) {
                logDebug("Setting dim timer for ${getDimRunDelay()}s because motion inactive")
                runIn(getDimRunDelay(), dimLights)
            } else {
				logDebug("Setting off timer for ${getOffRunDelay()}s because motion inactive and ${isNightMode ? 'is night mode' : 'dimming disabled'}")
                runIn(getOffRunDelay(), turnOffLights)
            }
        } else if (isKillSwitchOK() && boolDontObserve) {
            logDebug("Motion inactive but outside of specified mode/time/lux/soft kill/etc., but configured to not observe for \"off\"")
                if (boolDim && !isNightMode()) {
                    logDebug("Configured to always turn off lights; setting \"dim\" timer for {$getDimRunDelay()}s")
                    runIn(getDimRunDelay(), dimLights)
                } else {
                    logDebug("Configured to always turn off lights and dimming disabled or is night mode; setting \"off\" timer for {$getDimRunDelay()}s")
                    runIn(getOffRunDelay(), turnOffLights)
                }
        }
		else {
			logDebug("Motion inactive but restrictions have prevented any actions")
		}
    }
    logTrace("----------------End handling of ${evt.name}: ${evt.value}----------------")
}

/**
 * If configured to save previous light states, attempts to restore those. If can't find, simply turns on light.
 * Intended to be called when motion is detected
 */
def restoreAllLights() {
    logTrace("Running restoreAllLights()...")
    logTrace("state.isDimmed = ${state.isDimmed}")

    if (isOneSavedSwitchOn()) {
        switches.each {
            def savedState = getSavedLightOnOffState(it)
            logTrace("  Saved light state for ${it} was ${savedState}")
            if (savedState != "off") {
                def savedLevel = getSavedDimLevelState(it)
                logTrace("  Saved light level was ${savedLevel}")
                if (savedLevel.toString() == "0") {
                    // Just in case this happens...
                    savedLevel = 100
                    logTrace("  Brightness was returned as 0; set to ${savedLevel}")
                }
                setDimmerLevel(it, savedLevel)
				if (state.switchStates.get(it.id).ct) {
					it.setColorTemperature(state.switchStates.get(it.id).ct)
					logTrace("  Color temperature restored")
				}
                it.on()
                logTrace("  Restored previous brightness and turned on ${it}")
            } else {
                logDebug("${it} was saved as off; not turning on")
            }
        }
    } else {
        logDebug("No saved lights were on; turning all on")
        // TODO: Want to specify default brightness?
        switches.on()
    }

    state.isDimmed = false
    logTrace("Exiting turnOnOrRestoreLights().")
}

/**
 * Turns lights on with "night mode" settings
 */
def turnOnToNightMode() {
	logTrace "-- Running turnOnToNightMode() --"
	
	//input(name: "nightBri"
	//input(name: "nightCt"
	//input(name: "nightHue"
	//input(name: "nightSat"
	//input(name: "nightIgnoreSwitches"
	switches.each { sw ->
		def ignore = false
		nightIgnoreSwitches.each { x ->
			if (sw.id == x.id) {
				ignore = true
				logTrace ("${sw} configured to be ignored in night mode")
			}
		}
		if (!ignore) {
			if (nightCt && !nightBri)  {
				try {
					sw.setColorTemperature(nightCt)
				} catch (e) {
					log.warn("Unable to set color temperature for ${it}: ${e}")
				}
			} else if (nightCt && nightBri) {
				try {
					sw.setLevel(nightBri, 0)
					sw.setColorTemperature(nightCt)
				} catch (e) {
					log.warn("Unable to set color temperature or level for ${it}: ${e}")
				}
				
			} else if (nightBri && nightHue && nightSat) {
				def targetColor = [:]
				try {
					targetColor.hue = nightHue.toInteger()
					targetColor.saturation = nightSat.toInteger()
					targetColor.level = nightBri.toInteger()
					sw.setColor(targetColor)
				} catch (e) {
					log.warn("Unable to set color for ${it}: ${e}")
				}				
			}
			else {
				log.warn "CT, CT+Bri, or Bri+Hue+Sat not set for ${it}; turning on without using night mode settings"
				sw.on()
			}
		}
	}
	
	logTrace "-- Exiting turnOnToNightMode() --"
}

/**
 * Dims lights to pre-specified dimming level.
 * Intended to be called when motion has been inactive for a period of time between the "dim" and "off" thresholds.
 */
def dimLights() {
    logTrace("Running dimLights()...")
    if (!isOneRealSwitchOn()) {
        logDebug("  Not dimming because no lights on; returning")
        return
    }
    state.isDimmed = true
    state.switchStates = [:]
    for (sw in switches) {
        logTrace("  Saving light state for ${sw}")
        saveLightState(sw)
        logDebug("    Dimming ${sw}")
        if (sw.currentSwitch != "off") {
            def toLevel = dimToLevel
            if (getDimmerLevel(sw) <= toLevel) {
                // If light is currently at or less than "dim to" level, dim it as low as possible
                toLevel = 1
            }
            setDimmerLevel(sw, toLevel)
            logDebug("  Dimmed ${sw} to ${toLevel}")
        } else {
            logTrace("  Not dimming ${sw} because is off.")
        }
    }
    state.isDimmed = true
    logDebug("  Setting 'off' timer for ${postDimTime}s")
    unschedule(dimLights)
    unschedule(turnOffLights)
    runIn(postDimTime, turnOffLights)
    logTrace("Exiting dimLights().")
}

/**
 * Turns off all lights.
 * Intended to be called when motion has been inactive and specified intervals have passed; saves states before
 * turning off unless in prohibited mode
 */
def turnOffLights() {
    logTrace( "Running turnOffLights()...")
    if ((!isAllOK()) && !boolDontObserve)  {
        logDebug("  Outside specified run time, lux level, or mode and configured to observe; returning without turning off lights.")
        return
    } else if (boolDontObserve) {
        logDebug("  Outside specified run time, lux level, or mode, but configured not to observe. Turning off all lights without saving.")
        switches.off()

    }
    switches.each {
        if (isAllOK() && !isNightMode) {
            logTrace("  Saving on/off state then turning off: ${it}")
            saveLightOnOffState(it)
			//pauseExecution(1)   // Below seems to sometimes run before this if no slight pause on ST; not sure if needed on Hubitat
            it.off()
        }
        else {
            logTrace("  Outside specified mode/time/lux/etc. conditions or is night mode; turning off without saving state: ${it}")
            it.off()
        }
    }
    state.isDimmed = false
    log.debug "  Turned off all lights."
    logTrace("Exiting turnOffLights().")
}

def logDebug(text) {
	if (enableDebugLogging || enableTraceLogging) {
		log.debug(text)
	}
}

def logTrace(text) {
	if (enableTraceLogging) {
		log.trace(text)
	}
}
