/*
 * ======================  Virtual Switch (Community Driver) ==========================
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
 * =======================================================================================
 *
 *  PLATFORM: Hubitat
 *  PURPOSE: Virtual switch driver including "auto-off" functionality (similar to built-in driver)
 *  Last modified: 2021-06-29
 *
 *  Changelog:
 *  v1.0    - Initial Release
 */ 


metadata {
   definition (name: "Virtual Switch (Community)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/virtual/virtual-switch-with-auto-off.groovy") {
      capability "Actuator"
      capability "Switch"
   }
       
   preferences {
      input name: "autoOff", type: "enum", title: "Automatically switch back to off this many seconds after being turned on:",
         options: [[0:"Do not automatically switch off"],[1:"1 second"],[2:"2 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"]]
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true 
   }
}

void installed() {
   log.debug "installed()"
}

void updated() {
   log.debug "updated()"
}

void on() {
   if (logEnable == true) log.debug "on()"
   if (txtEnable == true && device.currentValue("switch") != "on") log.info "${device.displayName} is on"
   sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} is on")
   if (autoOff) {
      Integer disableTime = autoOff as Integer
      if (disableTime > 0) {
         runIn(disableTime,"off")
      }
   }
}

void off() {
   if (logEnable == true) log.debug "off()"
   if (txtEnable == true && device.currentValue("switch") != "off") log.info "${device.displayName} is off"
   sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} is off")
}