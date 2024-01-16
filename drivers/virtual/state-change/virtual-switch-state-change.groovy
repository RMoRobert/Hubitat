/*
 * ======================  Virtual Switch: State Change (Driver) ==========================
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
 *  PURPOSE: Virtual switch driver that can optionally always cause events (state change) with on() or off() comamnds,
 *           regardless of current device state.
 *
 *  Last modified: 2023-01-15
 *
 *  Changelog:
 *  v1.1    - Add option to disable forced state change
 *  v1.0    - Initial Release
 */ 

metadata {
   definition (name: "Virtual Switch with Optional State Change", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/state-change/virtual-switch-state-change.groovy") {
      capability "Actuator"
      capability "Switch"
   }
       
   preferences {
      input name: "autoOff", type: "enum", title: "Automatically switch back to off this many seconds after being turned on:",
         options: [[0:"Do not automatically switch off"],[1:"1 second"],[2:"2 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"]]
      input name: "forceStateChange", type: "bool", title: "Force state change for all on/off commands, regarldess of current state?"
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
   sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} is on", isStateChange: forceStateChange ? true : false)
   if (txtEnable == true) log.info "${device.displayName} is on"
   if (autoOff != null) {
      Integer disableTime = autoOff.toInteger()
      if (disableTime > 0) {
         runIn(disableTime, "autoOffHandler")
      }
   }
}

void off() {
   if (logEnable == true) log.debug "off()"
   sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} is off", isStateChange:  forceStateChange ? true : false)
   if (txtEnable == true) log.info "${device.displayName} is off"
}

void autoOffHandler(Map data=null) {
   if (logEnable == true) log.debug "autoOffHandler(data = $data)"
   off()
}