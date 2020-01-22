/**
 * =========================  Notification Proxy Device (Driver) ==========================
 *
 *  DESCRIPTION:
 *  Hubitat driver for use with 'Notification Proxy' app. Install both the app and driver.
 *  Create a virtual device using this driver, then use the Notification Proxy app to
 *  route devices sent to this virtual/proxy device  to one or more "real" notificaiton
 *  devices, enabling you to select only one device (this one) in apps/rules to send notifications
 *  to multiple "real" devices as specified in the app (useful if you have mutiple Hubitat Mobile
 *  App Devices and want notifications to all of them without needing to select each in all automations).
 *
 *  Copyright 2019 Robert Morris
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
 *  Last modified: 2019-06-19
 * 
 *  Changelog:
 * 
 *  v1.0 (2019-06-19) - Initial Release
 *
 */ 


metadata {
	definition (name: "Notification Proxy Device", namespace: "RMoRobert", author: "Robert Morirs") {
		capability "Notification"
		attribute "lastNotification", "string"
		attribute "secondToLastNotification", "string"
		
		// Can comment out the below three lines if not using presence, but useful to trick apps that are looking for
		// a presence device such as a Mobile App Device to be able to use this device too:
		capability "Presence Sensor"
		command "arrived"
        command "departed"
	}
	
	preferences() {
    	section("") {
			input "storeNotifications", "bool", title: "Store previous two notifications as device attributes"
        	input "debugMode", "bool", title: "Enable debug logging"
		}
	}   
}

def installed(){
    initialize()
}

def updated(){
    initialize()
}

def initialize() {
	if (storeNotifications) {
		logDebug("Resetting last two stored notification values to empty strings because device re-initialized")
		state.secondToLastNotification = ""
 		state.lastNotification = ""
	}
	else {
		logDebug("Removing last two stored notification values, if present, since configured to not store")
		state.remove("secondToLastNotification")
		state.remove("lastNotification")
	}
}

// Probably won't happen in a virtual driver but...
def parse(String description) {
	logDebug("Parsing '${description}'")
}

def deviceNotification(text) {
	logDebug("Received notification, sending to devices: ${text}")
    sendEvent(name: "deviceNotification", value: text)
	if (secondToLastNotification) {
		state.secondToLastNotification = state.lastNotification
		state.lastNotification = text
	}	
}

// Custom command for manipulating presence. Can remove/comment out if not wanted
// but doesn't hurt to leave.
def arrived() {
    logDebug  "Setting sensor to presnent because 'arrived' command run"
    sendEvent(name: "presence", value: "present")
}

// Custom command for manipulating presence. Can remove/comment out if not wanted
// but doesn't hurt to leave.
def departed() {
    logDebug "Setting sensor to not present because 'departed' command run"
    sendEvent(name: "presence", value: "not present")
}

def logDebug(str) {
    try {
    	if (settings.debugMode) log.debug(str)
    } catch(ex) {
		log.error("Error in logDebug: ${ex}")
    }
}
