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
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x26: 2,    // Switch Multilevel
    0x70: 1,    // Configuration
]

@Field static final Map zwaveParameters = [
   /* 1: [input: [name: "param.1", type: "number", title: "Auto-calibrartion tightness for closed (smaller = tighter, default = 22)",
         range: 15..30], size: 1], */
   2: [input: [name: "param.2", type: "enum", title: "Reverse direction of blinds",
           options: [[0:"No (close down) [DEFAULT]"],[1:"Yes (close up)"]]], size: 1],
   /* 3: [input: [name: "param.3", type: "enum", title: "Disable automatic Z-Wave report",
           options: [[0:"Yes [DEFAULT]"],[1:"No (recommended for Hubitat)"]],
      size: 1], */
   4: [input: [name: "param.4", type: "number", title: "Default \"on\" level for manual push button (default = 50)",
           range: 1..99], size: 1],
   6: [input: [name: "param.6", type: "number", title: "Default blind movement speed (0 = ASAP (default), larger = slower)", range: 0..100],
      size: 1]
]

metadata {
   definition (name: "iBlinds v3 (Community Driver)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/iBlinds-v3.groovy") {
      capability "Switch Level"
      capability "Actuator"
      capability "Switch"
      capability "Window Shade"   
      capability "Refresh"
      capability "Battery"
      capability "Configuration"
      
      fingerprint deviceId: "13", inClusters: "0x5E,0x85,0x59,0x86,0x72,0x5A,0x73,0x26,0x25,0x80,0x70", mfr: "647", deviceJoinName: "iBlinds"
   }
   
   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

List<String> installed() {
   logDebug("installed()")
   runIn(15, "getBattery")
   initialize()
}
x
List<String> updated() {
   logDebug("updated()")
   initialize()
}

// Set daily schedule for battery refresh; schedule disable of debug logging if enabled
List<String> initialize() {    
   logDebug("Initializing")
   Integer disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
   List<String> cmds = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
   return delayBetween(cmds, 250)
}

void configure() {
   log.warn "configure()"
   initialize()
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
   }
   sendEvent(name: "switch", value: switchValue)
   sendEvent(name: "windowShade", value: shadePosition)
   if (device.currentValue("switch") != switchValue) logDesc("$device.displayName switch is $switchValue")
   if (device.currentValue("level") != position) logDesc("$device.displayName level is $position")
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
   if (device.currentValue("battery") != batteryLevel) logDesc("$device.displayName battery level is ${batteryLevel}%")
   sendEvent(name: "battery", value: batteryLevel, unit: "%")
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
   setLevel(50)
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
      // zwaveSecureEncap(zwave.switchBinaryV1.switchBinaryGet()),
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