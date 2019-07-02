/**
 *  Thermostat Run Delay
 *  Description: If thermostat nearing setpoint, adjust setpoint up/down to try to delay running of
 *  thermostat. Can be restricted to certain days/times and to only adjust within certain range.
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
 *  Last modified: 2019-01-30
 *  Changes:
 *   20190130: Added temperature constraints
 *   20181108: first release
 *
 */
 
definition(
name: "Thermostat Run Delay",
namespace: "RMoRobert",
author: "RMoRboert",
description: "If certain time of day and thermostat temperature nearing setpoint, modify setpoint to delay turning on (only works in heating mode)",
category: "Convenience",
iconUrl: "",
iconX2Url: "",
iconX3Url: ""
)

preferences {
	mainPage()
}

def mainPage() {
	page(name:"mainPage", title:"Settings", install: true, uninstall: true) {
		section("Turn this thermostat down") {
			input (name:"thermostat", type: "capability.thermostat", title: "Select thermostat", required: true, multiple: false)
		}
		section("by this many degrees") {
	   		input (name:"degToLower", type: "number", title: "Degrees", defaultValue: 2, required: true)
		}	
		section("when thermostat temperature falls to within this many degrees of setpoint") {
	   		input (name:"degDiff", type: "number", title: "Degrees", defaultValue: 1, required: true)
		}	
		section("and time is between") {
			// TODO: Make dynamic?
			input "starting", "time", title: "Starting time", required: false
        	input "ending", "time", title: "Ending time time", required: false
			input "dayOfWeek", "enum", 
			title: "Only on these days",
			multiple: true, required: false,
            options: [
                    'Monday',
                    'Tuesday',
                    'Wednesday',
                    'Thursday',
                    'Friday',
                    'Saturday',
					'Sunday'
			]
			input "onlyInModes", "mode", title: "Only during these modes", multiple: true, required: false			
		}
		section("Temperature Constraints") {
			input (name: "minHeat", type: "number", title: "Do not set heating setpoint below this many degrees:")
			//input (name: "maxCool", type: "number", title: "Do not set cooling setpoint above this many degrees:")		
		}
		
		section("Notify when changed", hideable: true, hidden: true) {
		    //input (name: "notifySpeechDevices", type: "capability.speechSynthesis", title: "Select devices for notifications/announcements", required: false, multiple: true)
			input (name:"notifyDevices", type: "capability.notification", title: "Notify these devices when setpoint changed", required: false, multiple: true)
			input (name:"notifyForExtremes", type: "bool", title: "Notify if setpoint not changed because outside above constraints")
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
	//unschedule()
	initialize()
	adjustThermostat()
}

def initialize() {
	log.debug "Initializing"
	subscribe(thermostat, "temperature", temperatureHandler)
}

def isModeOK() {
    if (debugLogging) log.debug "Running isModeOK()..."
    def retVal = !onlyInModes || onlyInModes.contains(location.mode)
    if (debugLogging) log.debug "Exiting isModeOK(). Return value = ${retVal}"
    return retVal
}

// Returns false if user has specified "run between" times and the current time
// is outside those times. Otherwise, returns true.
def isTimeOK() {
    if (debugLogging) log.debug "Checking if time constraints specified and time is OK..."
    def retVal = true
    if (starting && ending) {
        def currTime = new Date()
        def startTime = timeToday(starting, location.timeZone)
        def stopTime = timeToday(ending, location.timeZone)
        retVal = timeOfDayIsBetween(startTime, stopTime, currTime, location.timeZone)
    }
	if (debugLogging) log.debug "Time OK = ${retVal}"
	if (dayOfWeek && retVal) {
		def dayOK = true	
		def df = new java.text.SimpleDateFormat("EEEE")
    	df.setTimeZone(location.timeZone)
    	def day = df.format(new Date())
    	//Does the preference input Days, i.e., days-of-week, contain today?
    	dayOK = dayOfWeek.contains(day)
		if (debugLogging) log.debug "Day of week OK = ${dayOK}"
		if (!dayOK) retVal = false
	}
    if (debugLogging) log.debug "Done checking time/day constraints. Time/day OK = ${retVal}"
    return retVal
}

def temperatureHandler(evt) {
	if (isModeOK() && isTimeOK()) {
		def currSetpoint = thermostat.currentValue("thermostatSetpoint")
		def currTemp = thermostat.currentValue("temperature")
		if (debugLogging) log.debug ("Setpoint = ${currSetpoint}; temperature = ${currTemp}")
		if (currTemp <= currSetpoint + degDiff) {
			if (debugLogging) log.debug ("Temperature in range of setpoint plus degree difference; calling adjustThermostat()")
			adjustThermostat()
		}
		else {
			if (debugLogging) log.debug ("Not changing setpoint because thermostat temperature outside of range")
		}
	}
	else {
		if (debugLogging) log.debug ("Not handling because outside specified mode and/or time constraints (mode OK = ${isModeOK()}; time OK = ${isTimeOK()})")
	}
}

def adjustThermostat() {
	if (debugLogging) log.debug "Adjusting thermostat..."
	def thermostatMode = thermostat.currentValue("thermostatMode")
	def changed = false
	if (thermostatMode == "off") {		
		log.debug "Not adjusting because thermostat is off"
	}
	else if (!isModeOK() || !isTimeOK()) {
		log.debug "Thermostat not adjusted because outside of specified mode or time restrictions"
	}
	else {
		def currSetpoint = thermostat.currentValue("thermostatSetpoint")
		def targetSetpoint = currSetpoint - degToLower
		if (debugLogging) log.debug "Current setpoint = ${currSetpoint}; target setpoint = ${targetSetpoint}; thermostat mode = ${thermostatMode}"
		// COOL MODE LOGIC
		if (thermostatMode == "cool") {
			if (debugLogging) log.debug "Thermostat in cooling mode; not supported."		
		}
		// HEAT MODE LOGIC
		else if (thermostatMode == "heat") {
			if (debugLogging) log.debug "Thermostat in heat mode"
			if (currSetpoint > targetSetpoint - 0.5 && currSetpoint < targetSetpoint + 0.5) {
				log.debug "Thermostat not changed because setpoint of ${currSetpoint} is already close to target of ${targetSetpoint}"
			}
			else if (minHeat && targetSetpoint < minHeat) {
				def msg = "Thermostat not changed because target setpoint of ${targetSetpoint} is below specified minimum of ${minHeat}"
				log.debug(msg)
				if (notifyDevices && notifyForExtremes) notifyDevices.deviceNotification(msg)
			}
			else {
				thermostat.setHeatingSetpoint(targetSetpoint)
				changed = true
				log.debug "Set thermostat heating setpoint to ${targetSetpoint}"
			}
		}
		// OTHER
		else {
			log.warn "Not adjusting because unable to handle thermostatMode: ${thermostat.thermostatMode}"
		}
		if (changed) {
			def strDirection = "up"
			if (currSetpoint > targetSetpoint) strDirection = "down"
			if (notifyDevices) notifyDevices.deviceNotification("Thermostat turned ${strDirection} to ${targetSetpoint} by ${app.label}")
		}
		if (debugLogging) "Changed = ${changed}"
		if (debugLogging) log.debug "Finished thermostat adjustment"
	}
}
