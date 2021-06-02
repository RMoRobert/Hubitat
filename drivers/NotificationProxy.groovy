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
 *  Changelog:
 *  v2.1 (2021-04-19) - Minor code cleanup, removed unused attributes
 *  v2.0 (2020-10-11) - Adjusted capability whitespace to match Hubitat docs;
 *                      Removed stored notifications as mobile app now can
 *  v1.0 (2019-06-19) - Initial Release
 *
 */ 


metadata {
   definition (name: "Notification Proxy Device", namespace: "RMoRobert", author: "Robert Morirs") {
      capability "Notification"

      // Can comment out the below three lines if not using presence, but useful to trick apps that are looking for
      // a presence device such as a Mobile App Device to be able to use this device too:
      capability "PresenceSensor"
      command "arrived"
      command "departed"
   }
	
   preferences() {
      section("") {
         input "debugMode", "bool", title: "Enable debug logging"
      }
   }
}

void installed(){
   initialize()
}

void updated(){
   initialize()
}

void initialize() {
   log.debug "Inititalizing..."
}

void deviceNotification(text) {
   logDebug("Received notification, creating event. Text: ${text}")
   sendEvent(name: "deviceNotification", value: text, isStateChange: true)
}

// Custom command for manipulating presence. Can remove/comment out if not wanted
// but doesn't hurt to leave.
void arrived() {
   logDebug  "Setting sensor to present because 'arrived' command run"
   sendEvent(name: "presence", value: "present")
}

// Custom command for manipulating presence. Can remove/comment out if not wanted
// but doesn't hurt to leave.
void departed() {
   logDebug "Setting sensor to not present because 'departed' command run"
   sendEvent(name: "presence", value: "not present")
}

def logDebug(str) {
   if (settings.debugMode != false) log.debug(str)
}
