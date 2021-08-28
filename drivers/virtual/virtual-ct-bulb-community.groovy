/*
 * ======================  Virtual CT Bulb (Community Driver) ==========================
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
 *  PURPOSE: Virtual CT bulb driver (community-written alternative to built-in driver)
 *
 *  Last modified: 2021-08-27
 *
 *  Changelog:
 *  v1.0.1  - Moved default value initialization to separate method
 *  v1.0    - Initial Release
 */ 


metadata {
   definition (name: "Virtual CT Bulb (Custom)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/virtual/virtual-ct-bulb-community.groovy") {
      capability "Actuator"
      capability "ColorTemperature"
      capability "Switch"
      capability "SwitchLevel"
      capability "Light"
   }
       
   preferences {
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void installed() {
   log.debug "installed()"
   initialize()
   runIn(1, "setDefaultValues")
}

void updated() {
   log.debug "updated()"
   initialize()
}

void initialize() {
   log.debug "initialize()"
   log.warn "Debug logging is: ${logEnable == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${txtEnable == true ? 'enabled' : 'disabled'}"
   if (logEnable) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, "logsOff")
   }
}

void setDefaultValues() {
   // sensible initial values:
   off()
   setColorTemperature(2700,100)
}

void logsOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("logEnable", [value:"false", type:"bool"])
}

void on() {
   if (logEnable) log.debug "on()"
   doSendEvent("switch", "on")
}

void off() {
   if (logEnable) log.debug "off()"
   doSendEvent("switch", "off")
}

void setLevel(value) {
   if (logEnable) log.debug  "setLevel($value)"
   doSendEvent("level", value, "%")
   if (device.currentValue("switch") != "on") on()
}

void setLevel(value, rate) {
   if (logEnable) log.debug  "setLevel($value, $rate)"
   doSendEvent("level", value, "%")
   if (device.currentValue("switch") != "on") on()
}

void setColorTemperature(value, level=null, rate=null) {
   if (logEnable) log.debug  "setColorTemperature($value, $level, $rate)"
   doSendEvent("colorTemperature", value, "K")
   if (level) doSendEvent("level", level, "%")
   setGenericTempName(value)
   if (device.currentValue("switch") != "on") on()
}

void doSendEvent(String eventName, eventValue, String eventUnit=null) {
   String descriptionText = """${device.displayName} $eventName is $eventValue${eventUnit ? "$eventUnit" : ""}"""
   if (txtEnable && (device.currentValue(eventName) != eventValue)) {
      log.info descriptionText
   }
   if (eventUnit) {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
   } else {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
   }
}

void setGenericTempName(temp) {
   if (!temp) return
   String genericName
   Integer value = temp.toInteger()
   if (value <= 2000) genericName = "Sodium"
   else if (value <= 2100) genericName = "Starlight"
   else if (value < 2400) genericName = "Sunrise"
   else if (value < 2800) genericName = "Incandescent"
   else if (value < 3300) genericName = "Soft White"
   else if (value < 3500) genericName = "Warm White"
   else if (value < 4150) genericName = "Moonlight"
   else if (value <= 5000) genericName = "Horizon"
   else if (value < 5500) genericName = "Daylight"
   else if (value < 6000) genericName = "Electronic"
   else if (value <= 6500) genericName = "Skylight"
   else if (value < 20000) genericName = "Polar"
   doSendEvent("colorName", genericName)
}