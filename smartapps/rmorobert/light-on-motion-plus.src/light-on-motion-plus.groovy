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
    name: "Light on Motion Plus",
    namespace: "RMoRobert",
    author: "Robert Morris",
    description: "Turn on one or more lights on when motion is detected and off after motion stops. Optionally dim before turning off and remember on/off states of invididual lights when motion stops to restore these states when turning back on rather than turning on all.",
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
    section("Dim for 1 minute before turning off...") {    	
		input "boolDim", "bool", title: "Enable dimming?"
        // See comment for 'switches' variable. 
		input "dimmers", "capability.switchLevel", multiple: true, title: "Bulbs to dim (you probably want to select the same bulbs here as you did above)"
    }
    section("Remember on/off state of individual lights when motion stops and restore when motion starts? (Do not select if you want all bulbs to turn back on when motion is detected regardless of their state when motion stopped.)"){
		input "boolRemember", "bool", title: "Remember states?"
        
	}
}

//=========================================================================
// "Constant" declarations
//=========================================================================

// Percentage to dim lights to if dimmed (could make UI to choose, but this seems like a reasonable default)
def getDimToLevel() {
	return 10
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
    log.debug "APP UDPDATED"
    log.debug "STATE.MODE = " + state.mode
}

// Returns true if at least one switch is turned on at the moment
def isOneRealSwitchOn() {
		def isOneOn = false
    	for (sw in switches) {
			if (sw.currentSwitch == "on") {
				isOneOn = true
			} 
		}
        return isOneOn
}

// Returns true if at least switch in saved switches is turned on
def isOneSavedSwitchOn() {
        def isOneOn = false
        switchStates.each { key, value ->
            if (value["switch"] == "on") {
                isOneOn = true
            }
        }
        return isOneOn
}

/**
 * Returns "Switch Level" (dimmer) matching "Switch", assuming user has selected both
 */
def getDimmerForSwitch(sw) {
	for (dm in dimmers) {
    	if (dm.id == sw.id) {
        	return dm
		}
	}
}

/**
 * Returns "Switch" matching "Switch Level" (dimmer), assuming user has selected both
 */
def getSwitchForDimmer(dm) {
	for (sw in switches) {
    	if (sw.id == dm.id) {
        	return sw
		}
	}
}

def motionHandler(evt) {
	log.debug "$evt.name: $evt.value"
    log.debug "STATE.MODE = " + state.mode
	if (evt.value == "active") {
		log.debug "Motion detected. Deciding how to turn on lights..."                        
        if (isOneRealSwitchOn() && (!state.mode == "dim")) {
        	log.debug "Motion detected, but at least one light is already on. Ignore."
        } else {
        	// No lights on. Turn on.
            turnOnOrRestoreLights()
            }
	} else if (evt.value == "inactive") {
		runIn((minutes1 - 1) * 60, scheduleCheck, [overwrite: false])
        if (boolDim) {
        	runIn((minutes1) * 60, scheduleCheck, [overwrite: false])
        }
	}
}

/**
 * Use when all lights are off and motion is detected, so need to turn some/all on
 */
def turnOnOrRestoreLights() {
	// No lights are on. Turn on all lights if all were off when motion stopped, restore dim level of those
    // with known level saved, or just turn on any where know on/off info but not last dim level.
    if (!isOneSavedSwitchOn() || !boolRemember) {
    	switches.on()
    } else
    {
        for (dm in dimmers) {
            if (state.switchStates[dm.id]) {
                // Restore pre-dim/pre-off brightness if can find
                log.debug "Found previous dim level for" + dm + "; setting."
                dm.setLevel(state.switchStates[dm.id].level)
            } else {
                // If can't find, turn the light back on (and hopefully the device itself remembers)
                def sw = getSwitchForDimmer(dm)
                if (sw) {
                    log.debug "Couldn't find previous dim level, but turning switch device back on."
                    sw.on()
                } else {
                    log.debug "No information found for " + dm + " and couldn't find switch device. Turning dimmer on device to 100%."
                    dm.setLevel(100)
                }
            }
        }
    }
}

/**
 * Dim lights after saving their states.
 */
def dimLights() {
	log.debug "Running dimLights()..."
    state.mode = "dim"
	state.switchStates = [:] // Re-create empty map to store switch states
    for (sw in switches) {
        if (sw.currentSwitch == "on") {
            def dm = getDimmerForSwitch(sw)
            if (dm) {
                state.switchStates.put(sw.id, ["switch": "on", "level": dm.currentLevel])                        
                dm.setLevel(dimToLevel)
            } else {
                state.switchStates.put(sw.id, ["switch": "on", "level": 100])   // Guess just store 100 for brightness if can't tell...
                // Can't dim bulb, so I guess don't do anything here
            }
        } else {
            state.switchStates.put(sw.id, ["switch": "off"])
        }
    }
}

/**
 * Turns off all lights. Intended to be used after motion stops and after dimming if diming enabled.
 */
def turnOffLights() {
	log.debug "Running turnOffLights()..."
    state.mode = "off"
    if (!boolDim) {
        log.debug "Turning off without dimming first"                
        state.switchStates = [:] // Re-create empty map to store switch states
        for (sw in switches) {
            if (sw.currentSwitch == "on") {
                state.switchStates.put(sw.id, ["switch": "on", "level": sw.currentLevel])
            } else {
                state.switchStates.put(sw.id, ["switch": "off"])
            }
        }
    }
    switches.off()
}

def scheduleCheck() {
	log.debug "Starting schedule check..."
    log.debug "STATE.MODE = " + state.mode
	def motionState = motion1.currentState("motion")
    if (motionState.value == "inactive") {
        def elapsed = now() - motionState.rawDateCreated.time
    	def offThreshold = 1000 * 60 * minutes1 - 1000
        def dimThreshold = 1000 * 60 * (minutes1 - 1) - 1000
        // Time elapsed is greater than the "off" threshold = TURN LIGHTS OFF
    	if (elapsed >= offThreshold) {
        	log.debug "Motion has stayed inactive long enough since last check ($elapsed ms):  turning lights off because >= off threshold"
            turnOffLights()
        }
        // Time elapsed is between the "dim" threshold and the "off" threshold = DIM LIGHTS if configured to dim
        else if (boolDim && elapsed >= dimThreshold && elapsed < offThreshold) {
            log.debug "Motion has stayed inactive long enough since last check ($elapsed ms):  dimming lights because between dim/off threshold"        
            dimLights()
    	}
        else {
        	log.debug "Motion has not stayed inactive long enough since last check ($elapsed ms):  doing nothing"
        }
    } else {
    	log.debug "Motion is active, do nothing and wait for inactive"
    }
}