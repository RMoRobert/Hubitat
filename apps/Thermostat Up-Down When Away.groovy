/**
 *  Thermostat Up/Down When Away
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
 */
definition(
name: "Thermostat Up/Down When Away",
namespace: "RMoRobert",
author: "RMoRboert",
description: "Automatically turn thermostat up/down when you're not home based on motion sensors",
category: "Convenience",
iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
	mainPage()
}

def mainPage() {
	page(name:"mainPage", title:"Settings", install: true, uninstall: true) {
		section("Turn this thermostat up/down") {
			input (name:"thermostat", type: "capability.thermostat", title: "Select thermostat", required: true, multiple: false)
		}
		section("When these sensors are inactive") {
	   		input (name:"motions", type: "capability.motionSensor", title: "Select motion sensors", required: false, multiple: true)
	   		//input (name:"contacts", type: "capability.contactSensor", title: "Select contact sensors", required: false, multiple: true)			
			input ("minutesToDelay", "number", title: "for this many minutes", required: true)
		}
		section("Setpoints when away") {
			input ("setpointHeat", "number", title: "Heating setpoint", required: true)
			input ("setpointCool", "number", title: "Cooling setpoint", required: true)
			//input ("boolAlwaysChange", "bool", title: "Change to configured setpoint even if above cooling setpoint or below heating setpoint")
		}
		section("Restrictions (TODO: Not yet implemented)", hideable: true, hidden: false) {
			// TODO: Make dynamic?
			input "onlyBeforeTime", "time", title: "Only before this time", required: false
        	input "onlyAfterTime", "time", title: "Only after time", required: false
			input "onlyInModes", "mode", title: "Only during these modes (NOT YET IMPLEMENTED)", multiple: true, required: false			
		}
   
		section("Notify when changed") {
		  input (name: "notifySpeechDevices", type: "capability.speechSynthesis", title: "Select devices for notifications/announcements", required: false, multiple: true)
		  //input (name:"notifyDevices", type: "capability.notification", title: "Notify these devices", required: false, multiple: true)
		}
		
		section("Logging", hideable: true, hidden: true) {
			input ("debugLogging", "bool", title: "Enable verbose/debug logging")
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	log.debug "Initializing"
	subscribe(motions, "motion", motionHandler)
}

def motionHandler(evt){
	if (debugLogging) log.debug ("Motion sensor state changed: ${evt.descriptionText}")
	def activeMotionSensors = motions.findAll { it?.latestValue("motion") == "active" }
	if (!activeMotionSensors) {
		log.debug "No active motion sensors; scheduling execution of adjustment in ${minutesToDelay} minutes"
		runIn(minutesToDelay * 60, adjustThermostat)
	}
	else {
		if (debugLogging) log.debug "Some motion sensors still active; unsubscribing from scheduled adjustment"
		unschedule(adjustThermostat)
	}
}

def adjustThermostat() {
	if (debugLogging) log.debug "Adjusting thermostat..."
	def thermostatMode = thermostat.currentValue("thermostatMode")
	def changed = false
	if (thermostatMode == "off") {		
		log.debug "Not adjusting because thermostat is off"
	}
	else {
		def targetSetpoint = setpointHeat
		def currSetpoint = thermostat.currentValue("thermostatSetpoint")
		if (debugLogging) log.debug "Current setpoint = ${currSetpoint}"
		if (debugLogging) log.debug("Thermostat mode = ${thermostatMode}")
		// COOL MODE LOGIC
		if (thermostatMode == "cool") {
			if (debugLogging) log.debug "Thermostat in cool mode"
			targetSetpoint = setpointCool
			if (currSetpoint > targetSetpoint - 0.9 && currSetpoint < targetSetpoint + 0.9) {
				if (debugLogging) log.debug "Thermostat not changed because setpoint of ${targetSetpoint} is already close to target of ${targetSetpoint}"
				} else {
				thermostat.setCoolingSetpoint(targetSetpoint)
				changed = true
				if (debugLogging) log.debug "Set thermostat cooling setpoint to ${targetSetpoint}"
			}			
		}
		// HEAT MODE LOGIC
		else if (thermostatMode == "heat") {
			if (debugLogging) log.debug "Thermostat in heat mode"
			if (currSetpoint > targetSetpoint - 0.9 && currSetpoint < targetSetpoint + 0.9) {
				if (debugLogging) log.debug "Thermostat not changed because setpoint of ${currSetpoint} is already close to target of ${targetSetpoint}"
			}
			else {
				thermostat.setHeatingSetpoint(targetSetpoint)
				changed = true
				if (debugLogging) log.debug "Set thermostat heating setpoint to ${targetSetpoint}"
			}
		}
		// OTHER
		else {
			log.warn "Not adjusting because unable to handle thermostatMode: ${thermostat.thermostatMode}"
		}
		if (changed) {
			def strDirection = "up"
			if (currSetpoint > targetSetpoint) strDirection = "down"
			if (notifySpeechDevices) notifySpeechDevices.speak("Thermostat turned ${strDirection} to ${targetSetpoint} because of inactivity")
			//if (notifySpeechDevices) notifySpeechDevices.speak("Thermostat adjusted at ${new Date().toLocaleString()} because of inactivity")
		}
		if (debugLogging) "Changed = ${changed}"
		log.debug "Finished thermostat adjustment"
	}
}
