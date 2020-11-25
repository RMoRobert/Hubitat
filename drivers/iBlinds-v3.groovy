/**
 *  Copyright 2020 Robert Morris
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
 *  Version History
 *  2020-11-22: Initial release for iBlinds v3 (portions based on v2 driver)
 *  2020-11-24: Added missing "position" events, Z-Wave parameter 1 option and paramter 3 auto-setting (for reporting to hub);
 *              Added option for default digital "on"/"open" position; battery reports now always generate event (state change)
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x26: 2,    // Switch Multilevel
    0x70: 1,    // Configuration
]

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "Tightness of gap when closed (used for auto-calibration)",
         options: [["-1": "Do not configure (keep previous/existing configuration)"],[15: "15 - Tightest"],[16:"16"],[17:"17"],[18:"18"],[19:"19"],[20:"20"],[21:"21"],[22:"22 [DEFAULT]"],[23:"23"],
         [24:"24"],[25:"25"],[26:"26"],[27:"27"],[28:"28"],[29:"16"],[29:"29"],[30:"30 - Least Tight"]]], size: 1, ignoreValue: "-1"],
   6: [input: [name: "param.6", type: "number", title: "Default blind movement speed (0 = ASAP [default], larger = slower)", range: 0..100],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Reverse direction of blinds",
           options: [[0:"No (close down) [DEFAULT]"],[1:"Yes (close up)"]]], size: 1],
   /* 3: [input: [name: "param.3", type: "enum", title: "Disable automatic Z-Wave report",
           options: [[0:"Yes [DEFAULT]"],[1:"No (recommended for Hubitat)"]],
      size: 1], */
   4: [input: [name: "param.4", type: "number", title: "Default \"on\" level for manual push button (default = 50)",
           range: 1..99], size: 1]
]

metadata {
   definition (name: "iBlinds v3 (Community Driver)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/iBlinds-v3.groovy") {
      capability "Actuator"
      capability "WindowShade" 
      capability "SwitchLevel"
      capability "Switch"  
      capability "Refresh"
      capability "Battery"
      capability "Configuration"

      fingerprint  mfr: "0287", prod: "0004", deviceId: "0071", inClusters: "0x5E,0x55,0x98,0x9F,0x6C"
   }
   
   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "openPosition", type: "number", description: "", title: "\"Open\" command opens to... (default = 50):", defaultValue: 50, range: 1..99
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

List<String> installed() {
   logDebug("installed()")
   runIn(15, "getBattery")
   initialize()
}

List<String> updated() {
   logDebug("updated()")
   initialize()
}

List<String> initialize() {    
   logDebug("Initializing")
   unschedule()
   Integer disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
   return configure()
}

List<String> configure() {
   log.warn "configure()"   
   List<String> cmds = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null && settings[data.input.name] != data.ignoreValue) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   // Parameter 3 = 1 to send Report back to Hubitat after Set:
   cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: 1, parameterNumber: 3, size: 1))
   cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
   return delayBetween(cmds, 250)
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
   Integer position = cmd.value as Integer
   String switchValue = "off"
   String windowShadeState = "closed"
   if (position > 0 && position < 99) {
      switchValue = "on"
      windowShadeState = "open"
   } 
   if (position < 100 && device.currentValue("level") != position) {
      logDesc("$device.displayName level is $position")
      sendEvent(name: "level", value: position, unit: "%")
   }
   if (device.currentValue("switch") != switchValue) {
      sendEvent(name: "switch", value: switchValue)
      logDesc("$device.displayName switch is $switchValue")
   }
   if (device.currentValue("level") != position) {
      logDesc("$device.displayName level is $position")
   }
   if (device.currentValue("position") != position) {
      logDesc("$device.displayName position is $position")
   }
   if (device.currentValue("windowShade") != windowShadeState) {
      logDesc("$device.displayName windowShade position is $windowShadeState")
   }      
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
      new hubitat.device.HubAction(zwaveSecureEncap(zwave.switchMultilevelV1.switchMultilevelGet()),
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

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	if (enableDebug) log.debug "VersionReport: ${cmd}"
	device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}")
	device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
	device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.Command cmd) {
   logDebug("Skipping: $cmd")
}

List<String> on() {
   logDebug("on()")
   Integer openTo = settings["openPosition"] ? (settings["openPosition"]  as Integer) : 50   
   setLevel(openTo)
}

List<String> off() {
   logDebug("off()")
   setLevel(0)
}

List<String> open() {
   logDebug("open()")
   Integer openTo = settings["openPosition"] ? (settings["openPosition"]  as Integer) : 50   
   setLevel(openTo)
}

List<String> close() {
   logDebug("close()")
   setLevel(0)
}

List<String> setPosition(value) {
   logDebug("setPosition($value)")
   setLevel(value)
}

List<String> setLevel(value) {
   logDebug("setLevel($value)")
   Integer level = Math.max(Math.min(value as Integer, 99), 0)
   return [zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelSet(value: level))]
}

List<String> setLevel(value, duration) {
   logDebug("setLevel($value, $duration)")
   Integer level = Math.max(Math.min(value as Integer, 99), 0)
   Integer dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
   return [zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration))]
}

List<String> refresh() {
   logDebug("refresh()")
   delayBetween([
      //zwaveSecureEncap(zwave.switchBinaryV1.switchBinaryGet()),
      zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelGet()),
      zwaveSecureEncap(zwave.batteryV1.batteryGet()),
   ], 200)
}

void logDebug(str) {
   if (settings.enableDebug) log.debug(str)
}

void logDesc(str) {
   if (settings.enableDesc) log.info(str)
}