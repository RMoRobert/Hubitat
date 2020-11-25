/**
 *  iBlinds v2 (manufactured by HAB Home Intel) community driver for Hubitat
 * 
 *  Copyright 2020 Robert Morris
 *  Original includes code copyrigt 2020 iBlinds
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
 *  Adapted for Hubitat by Robert M based on 2018-09 and 2019-11 updates by Eric B to original iBlinds driver
 * 
 *  Version History
 *  2020-01-20: Initial release
 *  2020-01-21: Fix for configure() and battery reporting
 *  2020-10-05: Minor improvements (fix battery schedule if lost)
 *  2020-11-23: Minor improvements (refacotring, more static typing)
 *  2020-11-24: Added missing "position" events; updated device fingerprint; battery reports now always generate event (state change)
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x26: 2,    // Switch Multilevel
    0x70: 1,    // Configuration
]

metadata {
   definition (name: "iBlinds v2 (Community Driver)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/iBlinds-v2.groovy") {
      capability "Actuator"
      capability "WindowShade" 
      capability "SwitchLevel"
      capability "Switch"  
      capability "Refresh"
      capability "Battery"
      capability "Configuration"

      fingerprint  mfr: "0287", prod: "0003", deviceId: "000D", inClusters: "0x5E,0x85,0x59,0x86,0x72,0x5A,0x73,0x26,0x25,0x80,0x70" 
   }
   
   preferences {
      input name: "openPosition", type: "number", description: "", title: "Open to this position by default:", range: 1..98, defaultValue: 50, required: true
      input name: "reverse", type: "bool", description: "", title: "Reverse close direction (close up instead of down)", required: true
      input name: "travelTime", type: "enum", description: "", title: "Allowance for travel time", options: [[3000: "3 seconds"], [5000:"5 seconds"],
            [8000:"8 seconds"], [10000:"10 seconds"], [15000:"15 seconds"], [20000:"20 seconds"], [60000:"1 minute"]], defaultValue: 15000
      input name: "refreshTime", type: "enum", description: "", title: "Schedule daily battery level refresh during this hour",
            options: [[0:"12 Midnight"],[1:"1 AM"],[4:"4 AM"],[5:"5 AM"],[6:"6 AM"],[9:"9 AM"],[10:"10 AM"],[11:"11 AM"],[13:"1 PM"],[15:"3 PM"],
                      [17:"5 PM"],[22: "10 PM"],[23:"11 PM"],[1000: "Disabled"],[2000: "Random"]], defaultValue: 4
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void installed() {
   logDebug("installed()")
   runIn(15, "getBattery")
   initialize()
}

void updated() {
   logDebug("updated()")
   initialize()
}

// Set daily schedule for battery refresh; schedule disable of debug logging if enabled
void initialize() {    
   logDebug("Initializing; scheduling battery refresh interval")
   unschedule()
   scheduleBatteryRefresh()
   Integer disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
}

void configure() {
   log.warn "configure()"
   initialize()
}

void scheduleBatteryRefresh() {
   String cronStr
   Integer s = Math.round(Math.random() * 60)
   Integer m = Math.round(Math.random() * 60)
   if (s >= 60) s = 59
   if (m >= 60) m = 59
   Integer hour = (refreshTime != null) ? refreshTime as Integer : 4
   if (hour == 2000) { // if set to random time
      Integer h = Math.round(Math.random() * 23)
      if (h == 2) h = 3 // avoid default maintenance window
      cronStr = "${s} ${m} ${h} ? * * *"
   } else if (hour >= 0 && hour <= 23) {
      cronStr = "${s} ${m} ${hour} ? * * *"
   }
   else if (hour == 1000) {
      logDebug "Not scheduled battery refresh because configured as disabled"
   }
   else {
      log.debug "invalid battery refresh time configuration: hour = $hour"
   }
   logDebug("battery schedule = \"${cronStr}\"")
   if (cronStr) schedule(cronStr, "getBattery")
}

void debugOff() {
   log.warn("Disabling debug logging")
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void parse(String description) {
   logDebug("parse: $description")
   if (description != "updated") {
      def cmd = zwave.parse(description, commandClassVersions)
      if (cmd) {
         zwaveEvent(cmd)
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   logDebug("BasicReport: $cmd")
   dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
   logDebug("SwitchMultilevelReport: $cmd")
   dimmerEvents(cmd)
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
   logDebug("Dimmer events:  $cmd")
   def position = cmd.value
   if (reverse) {
      position = 99 - position
   }
   String switchValue = "off"
   String shadePosition = "closed"
   if (position > 0 && position < 99) {
      switchValue = "on"
      shadePosition = "open"
   } 
   if (position < 100) {
      sendEvent(name: "level", value: position, unit: "%")
      sendEvent(name: "position", value: position, unit: "%")
   }
   sendEvent(name: "switch", value: switchValue)
   sendEvent(name: "windowShade", value: shadePosition)
   if (device.currentValue("switch") != switchValue) logDesc("$device.displayName switch is $switchValue")
   if (device.currentValue("level") != position) logDesc("$device.displayName level is $position")
   if (device.currentValue("position") != position) logDesc("$device.displayName position is $position")
   if (device.currentValue("windowShade") != shadePosition) logDesc("$device.displayName windowShade position is $shadePosition")
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   logDebug("ConfigurationReport $cmd")
   // Did iBlinds leave this in from a generic driver? I don't think their devices have indicators
   String value = "when off"
   if (cmd.configurationValue[0] == 1) {value = "when on"}
   if (cmd.configurationValue[0] == 2) {value = "never"}
   logDesc("$device.displayName indicatorStatus is $value")
   sendEvent([name: "indicatorStatus", value: value])
}

void zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
   logDesc("$device.displayName button was pressed")
   //sendEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed")
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
   logDebug("manufacturerId:   ${cmd.manufacturerId}")
   logDebug("manufacturerName: ${cmd.manufacturerName}")
   logDebug("productId:        ${cmd.productId}")
   logDebug("productTypeId:    ${cmd.productTypeId}")
   String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
   device.updateDataValue("MSR", msr)
   sendEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStopLevelChange cmd) {
   logDebug("SwitchMultilevelStopLevelChange: $cmd")
   sendHubCommand(
      new hubitat.device.HubAction(zwave.switchMultilevelV1.switchMultilevelGet().format(),
                                    hubitat.device.Protocol.ZWAVE)
   )
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
   logDebug("BatteryReport $cmd")
   Integer batteryLevel = cmd.batteryLevel as Integer
   if (cmd.batteryLevel == 0xFF) {
      batteryLevel = 1
   }
   logDesc("$device.displayName battery level is ${batteryLevel}%")
   sendEvent(name: "battery", value: batteryLevel, unit: "%", isStateChange: true)
}

void zwaveEvent(hubitat.zwave.Command cmd) {
   logDebug("Skipping: $cmd")
}

List<String> on() {
   logDebug("on()")
   setLevel(openPosition)
}

List<String> off() {
   logDebug("off()")
   setLevel(0)
}

List<String> open() {
   logDebug("open()")
   on()
}

List<String> close() {
   logDebug("close()")
   off()
}

List<String> setPosition(value) {
   logDebug("setPosition($value)")
   setLevel(value)
}

List<String> setLevel(value, duration=0) {
   logDebug("setLevel($value, $duration)")
   Integer level = Math.max(Math.min(value as Integer, 99), 0)
   // Skip all of this since we should wait to hear back instead?
   /*
   if (level <= 0 || level >= 99) {
      sendEvent(name: "switch", value: "off")
      logDesc("$device.displayName switch is off")
      sendEvent(name: "windowShade", value: "closed")
      logDesc("$device.displayName windowShade is closed")
   } else {
      sendEvent(name: "switch", value: "on")
      logDesc("$device.displayName switch is on")
      sendEvent(name: "windowShade", value: "open")
      logDesc("$device.displayName windowShade is open")
   }
   sendEvent(name: "level", value: level, unit: "%")
   logDesc("$device.displayName level is ${level}%")
   */
   // First, if a battery refresh hasn't happened in the last day and is scheduled to,
   // attempt to recover this schedule by re-scheduling:
   if (refreshTime != "1000" && (now() - (state.lastBattAttemptAt ?: 0) > 86400000)) {
      scheduleBatteryRefresh()
   }

   // Now, send level:
   Integer setLevel = reverse ? 99 - level : level
   Integer dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
   Integer delayTime = travelTime as Integer
   if (travelTime) delayTime = travelTime as int
   if (!delayTime) delayTime = 8000
   delayBetween([
      zwave.switchMultilevelV2.switchMultilevelSet(value: setLevel, dimmingDuration: dimmingDuration).format(),
      zwave.switchMultilevelV2.switchMultilevelGet().format()
   ], delayTime)
}

void getBattery() {
   logDebug("getBattery()")
   state.lastBattAttemptAt = now()
   sendHubCommand(
      new hubitat.device.HubAction(zwave.batteryV1.batteryGet().format(),
                                    hubitat.device.Protocol.ZWAVE)
   )
}

List<String> refresh() {
   logDebug("refresh()")
   state.lastBattAttemptAt = now()
   delayBetween([
      //zwave.switchBinaryV1.switchBinaryGet().format(),
      zwave.switchMultilevelV2.switchMultilevelGet().format(),
      zwave.batteryV1.batteryGet().format(),
   ], 200)
}

void logDebug(str) {
   if (settings.enableDebug) log.debug(str)
}

void logDesc(str) {
   if (settings.enableDesc) log.info(str)
}