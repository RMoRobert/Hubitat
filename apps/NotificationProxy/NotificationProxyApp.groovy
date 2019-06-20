/**
 * =============================  Notification Proxy App ===================================
 *
 *  DESCRIPTION:
 *  Designed to be used with Notification Proxy Device driver; allows you to use one "proxy"/virtual device (with that
 *  driver) to route notifications to one or more "real" (or other proxy) notification devices
 
 *  TO INSTALL:
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
 *  app only (do not use child app directly).
 *
 *  Copyright 2010 Robert Morris
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
 *  v1.0 (2019-06-19) - First release
 *
 */ 

definition(
    name: "Notification Proxy",
    namespace: "RMoRobert",
    author: "Robert Morris",
    description: 'Use one "proxy" notification device to route notifications to any number of real notification devices',
    iconUrl: "",
    iconX2Url: ""
)
preferences {
	page(name: "mainPage", install: true, uninstall: true) {  
		section("Choose devices") {
			input "proxyDevice", "device.NotificationProxyDevice", title: "Proxy Notification Device (Virtual)"		
      		input "notificationDevice", "capability.notification", title: "Notification Devices", multiple: true
			paragraph("When a notification device is sent to the proxy notification device, it will send the notification to all of the notification devices selected.")
			input "debugMode", "bool", title: "Enable debug logging", defaultValue: false  
		}
		section("Name") {
			label title: "Enter a name for this automation", required: true
		}
	}
}

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.info "initialize()"	
	subscribe(proxyDevice, "deviceNotification", notificationHandler)
}

def notificationHandler(evt) {
	logDebug("Sending ${evt.value} to ${notificationDevice}")
	def text = evt.value
	try {
		notificationDevice.deviceNotification(text)
	}
	catch (ex) {
		log.error("Error sending notification to devices: ${ex}")
	}
}

def logDebug(str) {
    try {
    	if (settings.debugMode) log.debug(str)
    } catch(ex) {
		log.error("Error in logDebug: ${ex}")
    }
}
