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
    description: "Turn on one or more lights on when motion is detected and off after motion stops. Optionally dim before turning off and remember on/off states of invididual lights when motion stops to restore these states when turning back on rather than turning on all. Version 2 attempst to track light states as change rather than only right before dim/off.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet@2x.png"
)

preferences {
	section("Turn on when there's movement...") {
		input "motion1", "capability.motionSensor", title: "Where?"
	}
	section("Turn on/off light(s)...") {
    	// Really want to use the 'dimmers' variable below for these, but can't seem to access their on/off
        // capabilities (even though the devices support it) unless they are selected as switches, so...
        // Make user select ideally the same lights for both 'switches' and 'dimmers'
		input "switches", "capability.switch", multiple: true
	}    
	section("And off when there's been no movement for...") {
		input "minutes1", "number", title: "Minutes?"
	}
    section("Dim before turning off...") {
        // See comment for 'switches' variable. 
        input "dimmers", "capability.switchLevel", multiple: true, title: "Lights to dim for 1 minute before turning off"
        paragraph "Select the same lights here as you did above, unless you do not want then to dim before turning off. (Selecting lights not chosen above will have no effect.)"
    }
    section("Remember on/off state of individual lights when motion stops and restore when motion starts?"){
		input "boolRemember", "bool", defaultValue: true, title: "Remember states?"
        paragraph "By default, this app will remember the on/off state of each light chosen above and restore the lights to those on/off states when motion resumes after inactivity (unless all were off), rather than turning all lights back on."
    }
    section("Only during certain times...") {
    	//TODO: Would be nice to have sunset/sunrise as options here like stock app
        input "starting", "time", title: "Starting", required: false
		input "ending", "time", title: "Ending", required: false
    } 
    section("Only when illuminance is less than....") {
    	input "lightSensor", "capability.illuminanceMeasurement", required: false
    	input "lightLevel", "number", title: "Below level (0 to over 100,000; recommend 400 lux for sunrise/sunset levels or about 300 lux if inside)", required: false
  	}
    section("Always observe time/illuminance conditions?") {
    	input "boolDontObserve", "bool", defaultValue: false, title: "Turn bulbs off after motion stops even if outside of specified time or illumance thresholds"
    }
}

//=========================================================================
// "Constant" declarations and value-calculator functions
//=========================================================================

// Percentage to dim lights to if dimmed
def getDimToLevel() {
	return 10
}

def getOffThreshold() {
	return 1000 * 60 * minutes1 - 1000
}

def getDimThreshold() {
	// If off threshold is <= 1, make dim threshold 30s (actually 29s) rather than zero
    if (minutes1 <= 1) {
    	return 1000 * 30 - 1000
    }
	return 1000 * 60 * (minutes1 - 1) - 1000 
}

//=========================================================================
// SmartApp Methoods
//=========================================================================

def installed() {
	subscribe(motion1, "motion", motionHandler)
	subscribe(switches, "switch", switchesHandler)
	subscribe(dimmers, "level", dimmersHandler)
    state.mode = "unknown" // Use state.mode to hold one of "unknown", "on", "dim", or "off" depending on current situation
    state.modeChanging = false // Set to true if mode is being changed (by dimming, turning off, or on--so methods that look for changes can ignore ones created by app
}

def updated() {
	unsubscribe()
	subscribe(motion1, "motion", motionHandler)
	subscribe(switches, "switch", switchesHandler)
	subscribe(dimmers, "level", dimmersHandler)
    state.mode = "unknown"
    log.trace "****** App updated *****"
    
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


// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isRunTimeOK() {
	log.debug "Running isRunTimeOK()..."
	def retVal = true
	if (starting && ending) {
		def currTime = now()
		def startTime = timeToday(starting).time
		def stopTime = timeToday(ending).time
		retVal = startTime < stopTime ? currTime >= startTime && currTime <= stopTime : currTime <= stopTime || currTime >= startTime
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

/**
 * Returns "Switch Level" (dimmer) matching "Switch", assuming user has selected both
 */
def getDimmerForSwitch(sw) {
	log.trace "Running getDimmerForSwitch()... sw = ${sw}"
	for (dm in dimmers) {
    	if (dm.id == sw.id) {
        	//log.trace "Found dimmer matching switch ${sw}"
            log.debug "Exiting from getDimmerForSwitch(). Found dimmer, returning ${dm}."
        	return dm
		}
	}
    log.warn "No dimmer matching switch ${sw} was found. Exiting getDimmerForSwitch()."
}

/**
 * Returns "Switch" matching "Switch Level" (dimmer), assuming user has selected both
 */
def getSwitchForDimmer(dm) {
	for (sw in switches) {
    	if (sw.id == dm.id) {
			//log.trace "Found switch matching dimmer ${dm}"
            log.trace "Exiting getSwitchForDimmer(). Found switch, returning ${sw}."
        	return sw
		}
	}
    log.warn "No match for dimmer ${dm} found. Exiting getSwitchForDimmer()."
}

/**
 * Saves on/off status and dimmer level of light
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember. Will try to find same Switch as Switch Level as well
 * if possible to record dimmer status.
 */
def saveLightState(forSwitch) {
	log.debug "Running saveLightState()..."
	if (forSwitch.currentSwitch == "on") {
        def dm = getDimmerForSwitch(forSwitch)
        if (dm) {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": dm.currentLevel])
        } else {
            state.switchStates.put(forSwitch.id, ["switch": "on", "level": 100])   // Guess just store 100 for brightness if can't tell...
            // Can't dim bulb, so I guess don't do anything here
        }
    } else {
            state.switchStates.put(forSwitch.id, ["switch": "off"])
    }
    log.trace "Just saved for " + forSwitch.id + ": " + state.switchStates.get(forSwitch.id)
    log.debug "Exiting saveLightState()."    
}

/**
 * Saves on/off status only. Similar to saveLightState but does not save dimmer status.
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember.
 */
def saveLightOnOffState(forSwitch) {
	log.debug "Running saveLightOnOffState()..."
    log.trace "Switch ${forSwitch.id} currently saved as ${state.switchStates.(forSwitch.id)}"
    state.switchStates.(forSwitch.id).switch = forSwitch.currentSwitch
    log.trace "Just saved for " + forSwitch.id + ": " + state.switchStates.get(forSwitch.id)
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
	log.debug "----------------Begin motionHandler() for ${evt.displayName}: ${evt.name} is ${evt.value} ----------------"
    log.info "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK()) && !boolDontObserve) {
    	log.debug "Outside specified run time or lux level. Returning."
        return
    }	
	if (evt.value == "active") {
		log.trace "Motion active. Turn on lights (or ensure on). Calling turnOnOrRestoreLights()..."
		turnOnOrRestoreLights()
	} else if (evt.value == "inactive") {
    	// Old code to just run scheduleCheck after 'off' threshold:
		//runIn(minutes1 * 60, scheduleCheck, [overwrite: false])  // why not overwrite?
    	log.trace "Motion inactive. Deciding what to do..."
        if (dimmers) {
        	log.trace "Dimming option has been chosen. Scheduling scheduleCheck() to run after 'dimming' threshold reached."
            // Run 1 minute before "off" threshold, unless offThreshold > 1 minute, then aim for 30s
            runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 30, scheduleCheck)            
            //runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 45, scheduleCheck)
        } else {
        	log.trace "Dimming option not chosen. Scheduling scheduleCheck() to run after 'off' threshold reached."
        	runIn(minutes1 * 60, scheduleCheck)
        }
	}
    log.debug "----------------Exiting motionHandler() for  for ${evt.displayName}: ${evt.name} ${evt.value} ----------------"
}

def switchesHandler(evt) {
	log.debug "Running switchesHandler for ${evt.displayName}: ${evt.name} is now ${evt.value}..."
    if (state.modeChanging == true) {
    	log.trace "Mode is in process of changing. Ignore."
    } else {
        if (state.mode == "on") {
            log.trace "state.mode == 'on', so ignore (state should be saved when dim or turn off)"
        } else if (state.mode == "dim") {
            log.trace "state.mode == 'dim', so save on/off state"
            saveLightOnOffState(evt.device)
        }
        else {
            log.trace "state.mode is not 'on' or 'dim.' Ignoring."
        }
    }
    log.debug "Exiting switchesHandler() for ${evt.displayName}: ${evt.name} = ${evt.value}"
}

def dimmersHandler(evt) {
	log.debug "Running dimmersHandler for ${evt.displayName}: ${evt.name} is now ${evt.value}..."
    if (state.modeChanging == true) {
    	log.trace "Mode is in process of changing. Ignore."
    } else {
        if (state.mode == "on") {
            log.trace "state.mode == 'on', so ignore (state should be saved when dim or turn off)"
        } else if (state.mode == "dim") {
            log.trace "state.mode == 'dim', so save on/off and dimmer state"
            saveLightState(evt.device)
        }
        else {
            log.trace "state.mode is not 'on' or 'dim.' Ignoring."
        }
    }
    log.debug "Exiting dimmersHandler() for ${evt.displayName}: ${evt.name} = ${evt.value}."
}

def scheduleCheck() {
	log.debug "Running scheduleCheck()...."
    log.info "state.mode = ${state.mode}"
	def motionState = motion1.currentState("motion")
    if (motionState.value == "inactive") {
        def elapsed = now() - motionState.rawDateCreated.time
        if (elapsed >= getDimThreshold() && elapsed < getOffThreshold() && dimmers) {
        	log.trace "Motion has stayed inactive for amount of time between 'dim' and 'off' thresholds ($elapsed ms). Dimming lights"
            dimLights()
            // Schedule to run again so can check for "off" threshold next:
            runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 30, scheduleCheck)            
            //runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 45, scheduleCheck)
            log.trace "Done dimming. Scheduled scheduleCheck() to run again in ${minutes1 > 1 ? (minutes1 - 1) * 60 : 30} seconds."
        }
    	if (elapsed >= getOffThreshold()) {
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
    if ((!isRunTimeOK() || !isLuxLevelOK()) && !boolDontObserve)   {
    	log.debug "Outside specified run time or lux level. Returning."
		log.debug "Exiting turnOnOrRestoreLights()."
        return
    } else if (boolDontObserve) {
    	log.debug "Outside specified run time or lux level, but configured not to observe. However, this does not apply to turning light on."
		log.debug "Exiting turnOnOrRestoreLights()."
    	return
    }
    if (state.mode != "dim" && isOneRealSwitchOn()) {
    	log.trace "Current mode is not 'dim' and at least on swtich is on. Assume this is desired state."
        log.trace "Setting mode to 'on' in case it isn't."
        state.modeChanging = true
        state.mode = "on"
        state.modeChanging = false
    }
    else if (state.mode == "dim" || state.mode == "off") {
    	if (!isOneSavedSwitchOn) {
        	log.trace "No switches were saved as 'on' when motion last stopped. Turning all on."            
       		state.modeChanging = true
            switches.on
            state.mode = "on"           
        	state.modeChanging = false
        } else { 
        	log.trace "Mode is either 'dim' or 'off' and at leat one switch was saved as on, so restore lights to last known state."
            state.modeChanging = true
            switches.each {
                def savedLightState = getSavedLightOnOffState(it)
                if (savedLightState != "off") {
                    log.trace "${it} was saved as on. Turning on."
                    it.on()
                    def dm = getDimmerForSwitch(it)
                    if (dm) {
                        def prevLevel = getSavedDimLevelState(it)
                        log.trace "Dimmer found for ${it}. Previous level: ${prevLevel}. Setting."
                        dm.setLevel(prevLevel)
                    } else {
                        log.trace "No dimmer found for ${it}. Turning on switch but cannot restore dim level."
                        it.on()
                    }
                } else {
                    log.trace "${it} was saved as off. Not turning on."
                }
            }
            state.modeChanging = false
        }
    }
    if (state.mode == "unknown") {
    	log.trace "State unknown. Turning on all switches."
        state.modeChanging = true
        switches.on()
        state.modeChanging = false
    }
    
    state.modeChanging = true
    state.mode = "on"
    state.modeChanging = false
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting turnOnOrRestoreLights()."
}

/**
  * Dims lights (those configured to be dimmed, which for most users is probably all of them).
  * Intended to be called when motion has been inactive for a period of time between the "dim" and "off" thresholds.
  */
def dimLights() {
	log.debug "Running dimLights()..."
    log.info "state.mode = ${state.mode}"
    state.modeChanging = true
    if ((!isRunTimeOK() || !isLuxLevelOK()) && !boolDontObserve)  {
    	log.debug "Outside specified run time or lux level. Returning."
        return
    } else if (boolDontObserve) {
    	log.debug "Outside specified run time or lux level, but configured not to observe. Continuing..."
    }
    state.switchStates = [:]
    for (sw in switches) {    
    	log.trace "Saving light state for ${sw}"
    	saveLightState(sw)
    	log.trace "Dimming ${sw}"
        def dm = getDimmerForSwitch(sw)
        if (dm) {
            log.trace "Found ${dm}, currently at level ${dm.currentLevel}"
            if (sw.currentSwitch != "off") {
            	def toLevel = dimToLevel
                if (dm.currentLevel <= toLevel) {
                	// If light is currently at or less than "dim to" level, dim it as low as possible
                	toLevel = 1
                } 
            	dm.setLevel(toLevel)
                log.trace "Dimmed ${dm} to ${toLevel}"
            } else {
            	log.trace "Not dimming ${sw} because is off."
            }
            log.trace "${dm} now at level ${dm.currentLevel}"
        } else {
        	log.trace "No dimmer found for ${sw}"
        }
    }
    state.mode = "dim"
    state.modeChanging = false
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting dimLights()."
}

/**
  * Turns off all lights.
  * Intended to be called when motion has been inactive for a period of time greater than or equal to the "off" threshold.
  */
def turnOffLights() {
	log.debug "Running turnOffLights()..."
    state.modeChanging = true
    log.info "state.mode = ${state.mode}"
    if ((!isRunTimeOK() || !isLuxLevelOK()) && !boolDontObserve)  {
    	log.debug "Outside specified run time or lux level. Returning."
        return
    } else if (boolDontObserve) {
    	log.debug "Outside specified run time or lux level, but configured not to observe. Continuing..."
    }
    switches.each {
    	log.trace "Saving on/off state then turning off: ${it}"
    	saveLightOnOffState(it)
        it.off()
    }
    state.mode = "off"
    log.trace "Turned off all lights."
    state.modeChanging = false
    log.info "state.mode = ${state.mode}"
    log.debug "Exiting turnOffLights()."
}