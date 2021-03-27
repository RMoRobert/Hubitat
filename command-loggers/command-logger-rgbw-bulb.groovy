/*
 * ========+=============  Command Logger: RGBW Bulb (Driver) =======+==================
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
 *  PURPOSE: To see what commands are being sent to devices, switch to this driver temporarily. All commands sent to
 *           device will be logged as "trace" entries in "Logs." Device will not function with this driver, which only
 *           writes logs from commands. Switch back to regular driver for use. May be useful for troubleshooting apps.
 *
 *  Last modified: 2021-03-27
 *
 *  Changelog:
 *  v1.0    - Initial Release
 */ 


metadata {
   definition (name: "Command Logger: RGBW Bulb", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/main/drivers/command-loggers/command-logger-rgbw-bulb.groovy") {
      capability "Actuator"
      capability "Configuration"
      capability "Color Control"
      capability "Color Temperature"
      capability "Refresh"
      capability "Switch"
      capability "Switch Level"
      capability "ChangeLevel"
      capability "Light"
      capability "ColorMode"
   }
       
   preferences {
      input name: "sendEvents", type: "bool", title: "Send events in response to commands (default: do not send; log only)"
   }
}

void installed() {
   log.debug "installed()"
}

void updated() {
   log.debug "updated()"
}

void initialize() {
   log.debug "initialize()"
}

void configure() {
   log.trace "configure()"
}

void on() {
   log.trace "on()"
   doSendEvent("switch", "on")
}

void off() {
   log.trace "off()"
   doSendEvent("switch", "off")
}

void startLevelChange(direction) {
   log.trace "startLevelChange($direction)"
}

void stopLevelChange() {
   log.trace "stopLevelChange()" 
}

void setLevel(value) {
   log.trace "setLevel($value)"
   doSendEvent("level", value, "%")
}

void setLevel(value, rate) {
   log.trace "setLevel($value, $rate)"
   doSendEvent("level", value, "%")
}

void setColorTemperature(value, level=null, rate=null) {
   log.trace "setColorTemperature($value, $level, $rate)"
   doSendEvent("colorTemperature", value, "K")
   if (level) doSendEvent("level", level, "%")
   setGenericTempName(value)
}

void setColor(value) {
   log.trace "setColor($value)"
   doSendEvent("hue", value.hue, "%")
   doSendEvent("saturation", value.saturation, "%")
   if (value.level) doSendEvent("level", value.level, "%")
   setGenericColorName(value.hue)
}

void setHue(value) {
   log.trace "setHue($value)"
   doSendEvent("hue", value, "%")
   setGenericColorName(value)
}

void setSaturation(value) {
   log.trace "setSaturation($value)"
   doSendEvent("saturation", value, "%")   
   setGenericColorName()
}

void flash() {
   log.trace "flash()"
}

void doSendEvent(String eventName, eventValue, String eventUnit=null) {
   if (!sendEvents) return
   String descriptionText = """${device.displayName} $eventName is $eventValue${eventUnit ? "$eventUnit" : ""}"""
   if (eventUnit) {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
   } else {
      sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
   }
}

void refresh() {
   log.trace "refresh()"
}

// Hubiat-provided color/name mappings
void setGenericColorName(hue = device.currentValue("hue")) {
   String colorName
   switch ((hue * 3.6).toInteger()) {
      case 0..15: colorName = "Red"
         break
      case 16..45: colorName = "Orange"
         break
      case 46..75: colorName = "Yellow"
         break
      case 76..105: colorName = "Chartreuse"
         break
      case 106..135: colorName = "Green"
         break
      case 136..165: colorName = "Spring"
         break
      case 166..195: colorName = "Cyan"
         break
      case 196..225: colorName = "Azure"
         break
      case 226..255: colorName = "Blue"
         break
      case 256..285: colorName = "Violet"
         break
      case 286..315: colorName = "Magenta"
         break
      case 316..345: colorName = "Rose"
         break
      case 346..360: colorName = "Red"
         break
      default: colorName = "undefined" // shouldn't happen, but just in case
         break            
   }
   if (device.currentValue("saturation") < 1) colorName = "White"
   doSendEvent("colorName", colorName)
}

// Hubitat-provided ct/name mappings
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
   else genericName = "undefined" // shouldn't happen, but just in case
   doSendEvent("colorName", genericName)
}
