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
    description: "Turn on one or more lights on when motion is detected and off after motion stops. Optionally dim before turning off and remember on/off states of invididual lights when motion stops to restore these states when turning back on rather than turning on all. Version 2 tracks light states as change rathern than waiting until moment of dimming/turning off.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet@2x.png"
)

preferences {
	section("Turn on when there's movement..."){
		input "motion1", "capability.motionSensor", title: "Where?"
	}
	section("Turn on/off light(s)..."){
    	// Really want to use the 'dimmers' variable below for these, but can't seem to access their on/off
        // capabilities (even though the devices support it) unless they are selected as switches, so...
        // Make user select ideally the same lights for both 'switches' and 'dimmers'
		input "switches", "capability.switch", multiple: true
	}    
	section("And off when there's been no movement for..."){
		input "minutes1", "number", title: "Minutes?"
	}
    section("Dim before turning off...") {
        // See comment for 'switches' variable. 
        input "dimmers", "capability.switchLevel", multiple: true, title: "Lights to dim for 1 minute before turning off"
        paragraph "If you want the lights to turn off without first dimming, select no lights here. Otherwise, you probably want to select the same lights here as you did above. (Selecting lights not chosen above will have no effect.)"
    }
    section("Remember on/off state of individual lights when motion stops and restore when motion starts?"){
		input "boolRemember", "bool", defaultValue: true, title: "Remember states?"
        paragraph "By default, this app will remember the on/off state of each light chosen above and restore the lights to those on/off states when motion resumes after inactivity, rather than turning all lights back on. You can disable this below if desired, which will make this app function more like most others."
    }
    section("Only during certain times...") {
    	//TODO: Would be nice to have sunset/sunrise as options here like stock app
        input "starting", "time", title: "Starting", required: false
		input "ending", "time", title: "Ending", required: false
    }
}

//=========================================================================
// "Constant" declarations and value-calculator functions
//=========================================================================

// Percentage to dim lights to if dimmed (TODO: what if current level less than 10?)
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
    state.mode = "unknown" // Use state.MODE to hold one of "unknown", "on", "dim", or "off" depending on current situation
}

def updated() {
	unsubscribe()
	subscribe(motion1, "motion", motionHandler) 
    state.mode = "unknown"
    log.debug "****** App updated *****"
    
}

// Returns true if at least one switch is turned on at the moment
def isOneRealSwitchOn() {
    log.trace "Running isOneRealSwitchOn()..."
    def isOneOn = false
    for (sw in switches) {
        if (sw.currentSwitch == "on") {
            isOneOn = true
        } 
    }
    log.trace "Ran isOneRealSwitchOn(). Return value = " + isOneOn
    return isOneOn
}

// Returns true if at least switch in saved switches is turned on
def isOneSavedSwitchOn() {
	log.trace "Running isOneSavedSwitchOn()..."
    def isOneOn = false
    state.switchStates.each { key, value ->
        if (value["switch"] == "on") {
            log.debug "Saved switch is on"
            isOneOn = true
        }
    }
    log.trace "Ran isOneSavedSwitchOn(). Return value = " + isOneOn
    return isOneOn
}


// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isRunTimeOK() {
	log.trace "Running isRunTimeOK()..."
	def retVal = true
	if (starting && ending) {
		def currTime = now()
		def startTime = timeToday(starting).time
		def stopTime = timeToday(ending).time
		retVal = startTime < stopTime ? currTime >= startTime && currTime <= stopTime : currTime <= stopTime || currTime >= startTime
	}
	log.trace "Exiting isRunTimeOK(). Return value = $retVal"
	return retVal
}

/**
 * Returns "Switch Level" (dimmer) matching "Switch", assuming user has selected both
 */
def getDimmerForSwitch(sw) {
	log.debug "Running getDimmerForSwitch()... sw = ${sw}"
	for (dm in dimmers) {
    	log.debug "dm.id = ${dm.id}"
        log.debug "sw.id = ${sw.id}"
    	if (dm.id == sw.id) {
        	log.debug "Found dimmer matching switch ${sw}!"
            log.trace "Exiting from getDimmerForSwitch(). Returning ${dm}."
        	return dm
		}
	}
    log.debug "NO MATCH FOR SWITCH FOUND."
    log.trace "Exiting getDimmerForSwitch()."
}

/**
 * Returns "Switch" matching "Switch Level" (dimmer), assuming user has selected both
 */
def getSwitchForDimmer(dm) {
	for (sw in switches) {
    	if (sw.id == dm.id) {
			log.debug "    Found match for dimmer! sw = " + sw
        	return sw
		}
	}
    log.debug "*** NO MATCH FOR DIMMER FOUND. Exiting getSwitchForDimmer()."
}

/**
 * Saves on/off status and dimmer level of light
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember. Will try to find same Switch as Switch Level as well
 * if possible to record dimmer status.
 */
def saveLightState(forSwitch) {
	log.trace "Running saveLightState()..."
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
    log.debug "Just saved for " + forSwitch.id + ": " + state.switchStates.get(forSwitch.id)
    log.trace "Exiting saveLightState()."    
}

/**
 * Saves on/off status only. Similar to saveLightState but does not save dimmer status.
 * Parameter forSwitch: a Switch-capable object (supporting on/off) to remember.
 */
def saveLightOnOffState(forSwitch) {
	log.trace "Running saveLightOnOffState()..."
    log.debug "Switch ${forSwitch.id} currently saved as ${state.switchStates.(forSwitch.id)}"
    state.switchStates.(forSwitch.id).switch = forSwitch.currentSwitch
    log.debug "Just saved for " + forSwitch.id + ": " + state.switchStates.get(forSwitch.id)
    log.trace "Exiting saveLightOnOffState()."    
}

/**
 * Gets on/off status for a saved light
 */
def getSavedLightOnOffState(forSwitch) {
	log.trace "Running getLightOnOffState()..."
    log.debug "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
    def swState = state.switchStates.get(forSwitch.id).switch ?: "off"
    log.trace "Exiting getLightOnOffState(), returning ${swState}."    
    return swState    
}

/**
 * Gets dim level for a saved light
 */
def getSavedDimLevelState(forSwitch) {
	log.trace "Running getLightOnOffState()..."
    log.debug "Saved data for ${forSwitch.id} = ${state.switchStates.get(forSwitch.id)}"
    def dmLevel = state.switchStates.get(forSwitch.id).level ?: "100"
    log.trace "Exiting getLightOnOffState(), returning ${dmLevel}."    
    return dmLevel    
}

def motionHandler(evt) {
	log.trace "----------------Begin handling of ${evt.name}: ${evt.value}----------------"
    log.debug "state.mode = ${state.mode}"
    if (!isRunTimeOK()) {
    	log.trace "Outside specified run time. Returning."
        return
    }	
	if (evt.value == "active") {
		log.debug "Motion active. Turn on lights (or ensure on). Calling turnOnOrRestoreLights()..."
		turnOnOrRestoreLights()
	} else if (evt.value == "inactive") {
    	// Old code to just run scheduleCheck after 'off' threshold:
		//runIn(minutes1 * 60, scheduleCheck, [overwrite: false])  // why not overwrite?
    	log.debug "Motion inactive. Deciding what to do..."
        if (dimmers) {
        	log.debug "Dimming option has been chosen. Scheduling scheduleCheck() to run after 'dimming' threshold reached."
            // Run 1 minute before "off" threshold, unless offThreshold > 1 minute, then aim for 45s
            runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 30, scheduleCheck)            
            //runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 45, scheduleCheck)
        } else {
        	log.debug "Dimming option not chosen. Scheduling scheduleCheck() to run after 'off' threshold reached."
        	runIn(minutes1 * 60, scheduleCheck)
        }
	}
    log.trace "----------------End handling of ${evt.name}: ${evt.value}----------------"
}

def scheduleCheck() {
	log.trace "Running scheduleCheck()...."
    log.debug "state.mode = ${state.mode}"
	def motionState = motion1.currentState("motion")
    if (motionState.value == "inactive") {
        def elapsed = now() - motionState.rawDateCreated.time
        if (elapsed >= getDimThreshold() && elapsed < getOffThreshold() && dimmers) {
        	log.debug "Motion has stayed inactive for amount of time between 'dim' and 'off' thresholds ($elapsed ms). Dimming lights"
            dimLights()
            // Schedule to run again so can check for "off" threshold next:
            runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 30, scheduleCheck)            
            //runIn(minutes1 > 1 ? (minutes1 - 1) * 60 : 45, scheduleCheck)
            log.debug "Done dimming. Scheduled scheduleCheck() to run again in ${minutes1 > 1 ? (minutes1 - 1) * 60 : 30} seconds."
        }
    	if (elapsed >= getOffThreshold()) {
            log.debug "Motion has stayed inactive long enough to cross 'off' threshold ($elapsed ms). Turning lights off."
            turnOffLights()
    	} else {
        	log.debug "Motion has not stayed inactive long enough since last check ($elapsed ms). Doing nothing"
        }
    } else {
    	log.debug "Motion is active. Do nothing and wait for inactive."
    }
    log.debug "state.mode = ${state.mode}"
    log.trace "Exiting scheduleCheck()."
}

/**
  * If configured to save previous light states, attemps to restore those. If can't find, simply turns on light.
  * Intended to be called when motion is detected after period of no motion (i.e., when lights are off or dimmed).
  */
def turnOnOrRestoreLights() {
    log.trace "Running turnOnOrRestoreLights()..."
    log.debug "state.mode = ${state.mode}"
    if (!isRunTimeOK()) {
    	log.trace "Outside specified run time. Returning."
        return
    }
    if (state.mode != "dim" && isOneRealSwitchOn()) {
    	log.debug "Current mode is not 'dim' and at least on swtich is on. Assume this is desired state."
        log.debug "Setting mode to 'on' in case it isn't."
        state.mode = "on"
    }
    else if (state.mode == "dim" || state.mode == "off") {
    	if (!isOneSavedSwitchOn) {
        	log.debug "No switches were saved as 'on' when motion last stopped. Turning all on."
            switches.on
            state.mode = "on"
        } else {
        	log.debug "Mode is either 'dim' or 'off,' so restore lights to last known state."
            switches.each {
                def savedLightState = getSavedLightOnOffState(it)
                if (savedLightState != "off") {
                    log.debug "${it} was saved as on. Turning on."
                    it.on()
                    def dm = getDimmerForSwitch(it)
                    if (dm) {
                        def prevLevel = getSavedDimLevelState(it)
                        log.debug "Dimmer found for ${it}. Previous level: ${prevLevel}. Setting."
                        dm.setLevel(prevLevel)
                    } else {
                        log.debug "No dimmer found for ${it}. Turning on switch but cannot restore dim level."
                        it.on()
                    }
                } else {
                    log.debug "${it} was saved as off. Not turning on."
                }
            }
        }
    }
    if (state.mode == "unknown") {
    	log.debug "State unknown. Turning on all switches."
        switches.on()
    }
    state.mode = "on"    
    log.debug "state.mode = ${state.mode}"
    log.trace "Exiting turnOnOrRestoreLights()."
}

/**
  * Dims lights (those configured to be dimmed, which for most users is probably all of them).
  * Intended to be called when motion has been inactive for a period of time between the "dim" and "off" thresholds.
  */
def dimLights() {
	log.trace "Running dimLights()..."
    log.debug "state.mode = ${state.mode}"
    if (!isRunTimeOK()) {
    	log.trace "Outside specified run time. Returning."
        return
    }
    state.switchStates = [:]
    for (sw in switches) {    
    	log.debug "Saving light state for ${sw}"
    	saveLightState(sw)
    	log.debug "Dimming ${sw}"
        def dm = getDimmerForSwitch(sw)
        if (dm) {
            log.debug "Found ${dm}, currently at level ${dm.currentLevel}"
            if (sw.currentSwitch != "off") {
            	def toLevel = dimToLevel
                if (dm.currentLevel <= toLevel) {
                	// If light is currently at or less than "dim to" level, dim it as low as possible
                	toLevel = 1
                } 
            	dm.setLevel(toLevel)
                log.debug "Dimmed ${dm} to ${toLevel}"
            } else {
            	log.debug "Not dimming ${sw} because is off."
            }
            log.debug "${dm} now at level ${dm.currentLevel}"
        } else {
        	log.debug "No dimmer found for ${sw}"
        }
    }
    state.mode = "dim"
    log.debug "state.mode = ${state.mode}"
    log.trace "Exiting dimLights()."
}

/**
  * Turns off all lights.
  * Intended to be called when motion has been inactive for a period of time greater than or equal to the "off" threshold.
  */
def turnOffLights() {
	log.trace "Running turnOffLights()..."
    log.debug "state.mode = ${state.mode}"
    if (!isRunTimeOK()) {
    	log.trace "Outside specified run time. Returning."
        return
    }
    switches.each {
    	log.debug "Saving on/off state then turning off: ${it}"
    	saveLightOnOffState(it)
        it.off()
    }
    state.mode = "off"
    log.debug "Turned off all lights."
    log.debug "state.mode = ${state.mode}"
    log.trace "Exiting turnOffLights()."
}
