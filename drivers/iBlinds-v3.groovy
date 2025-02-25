/**
 *  iBlinds v3 (manufactured by HAB Home Intel) community driver for Hubitat
 * 
 *  Copyright 2020-2025 Robert Morris
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
 *  2025-02-24: Revert to SwitchMultilevel from WindowCovering
 *  2025-02-10: Switch from SwitchMultilevel to WindowCovering command class for setPosition and similar; remove driver-level Supervision
 *  2022-10-09: Fix for error during scheduled battery refresh
 *  2022-09-01: Add initiateCalibration() command, new min/max tilt parameters, add start/stopPositionChange(),
 *              remove inadvertent initialize()/configure() on hub restart
 *  2022-08-31: Change parameter 3 back to 1 per iBlinds' suggestion
 *  2022-08-23: Fix for Version and MSR reports; switch to lifeline instead of parameter 3
 *  2021-12-22: Use device.idAsLong instead of device.id for Maps
 *  2021-11-07: Additional concurrecnty fix
 *  2021-08-18: Concurrency fix for Z-Wave supervision
 *  2021-07-26: Added additional fingerprint
 *  2021-04-24: Added daily battery refresh option in case device does not send on own; Supervision improvements for S2 devices
 *  2020-11-22: Initial release for iBlinds v3 (portions based on v2 driver)
 *  2020-11-24: Added missing "position" events, Z-Wave parameter 1 option and paramter 3 auto-setting (for reporting to hub);
 *              Added option for default digital "on"/"open" position; battery reports now always generate event (state change)
 *  2020-11-25: Minor fixes (parameter 3 auto-set failed with secure pairing) and tweaks to request and parse MSR, DSR, and other Z-Wave reports
 *  2020-11-27: Fixed issues with Z-Wave parameters not getting set correctly in some cases
 */

import groovy.transform.Field

@Field static final BigInteger param3DefaultValue = 1 // 1 = send Report after Set (so don't need to query after setPosition(), etc.)
@Field static final Integer calibrationTime = 60 // Number of seconds to wait before setting paramater 7 back to 0 when set to 1

@Field static final Map<Short,Short> commandClassVersions = [
   0x20: 1,   // Basic
   0x26: 2,   // Switch Multilevel
   0x50: 1,   // Basic Window Covering
   0x55: 1,   // Transport Service
   0x59: 1, // AssociationGrpInfo
   0x5A: 1,   // DeviceResetLocally
   0x5E: 2,   // ZwavePlusInfo
   0x6C: 1,   // Supervision
   0x70: 1,   // Configuration
   0x72: 1,   // ManufacturerSpecific
   //0x7A: 2, // Firmware Update Md (v5)
   0x80: 1,   // Battery
   0x85: 2, // Association
   0x86: 2,   // Version
   0x8E: 2,   // MultiChannelAssociation (v3)
   0x9F: 1    // Security S2
]

@Field static final Map<Short,Map> zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "Tightness of gap when closed (used for auto-calibration)",
         options: [["-1": "Do not configure (keep previous/existing configuration)"],[15: "15 - Tightest"],[16:"16"],[17:"17"],[18:"18"],[19:"19"],[20:"20"],[21:"21"],[22:"22 [DEFAULT]"],[23:"23"],
         [24:"24"],[25:"25"],[26:"26"],[27:"27"],[28:"28"],[29:"16"],[29:"29"],[30:"30 - Least Tight"]]], size: 1, ignoreValue: "-1"],
   6: [input: [name: "param.6", type: "number", title: "Default blind movement speed (0 = ASAP [DEFAULT], larger = slower)", range: 0..100],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Reverse direction of blinds",
           options: [[0:"No (close down) [DEFAULT]"],[1:"Yes (close up)"]]], size: 1],
   /* 3: [input: [name: "param.3", type: "enum", title: "Disable automatic Z-Wave report",
           options: [[0:"Yes [DEFAULT]"],[1:"No (recommended for Hubitat)"]],
      size: 1], */
   4: [input: [name: "param.4", type: "number", title: "Default \"on\" level for manual push button (default = 50)",
           range: 1..99], size: 1],
   8: [input: [name: "param.8", type: "number", title: "Minimum tilt level (default = 0), firmware 3.07/3.12+ only", range:0..25], size:1],
   9: [input: [name: "param.9", type: "number", title: "Maximum tilt level (default = 99; adjust these only if closing too tightly in up/down direction), firmware 3.07/3.12+ only", range:75..99], size:1]
]

metadata {
   definition (name: "iBlinds v3 (Community Driver)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/iBlinds-v3.groovy") {
      capability "Actuator"
      capability "Configuration"
      capability "Refresh"
      capability "Battery"
      capability "Switch"
      capability "SwitchLevel"
      capability "WindowShade"

      command "initiateCalibration"

      fingerprint mfr: "0287", prod: "0004", deviceId: "0071", inClusters: "0x5E,0x55,0x98,0x9F,0x6C"
      fingerprint mfr: "0287", prod: "0004", deviceId: "0072", inClusters: "0x5E,0x55,0x98,0x9F,0x6C"  // v3.1
   }
   
   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "openPosition", type: "number", description: "", title: "\"Open\" command opens to... (default = 50):", defaultValue: 50, range: 1..99
      input name: "refreshTime", type: "enum", description: "", title: "Schedule daily battery level refresh during this hour",
      options: [[0:"12 Midnight"],[1:"1 AM"],[3:"3 AM"],[4:"4 AM"],[5:"5 AM"],[6:"6 AM"],[7:"7 AM"],[8:"8 AM"],[9:"9 AM"],
                 [10:"10 AM"],[11:"11 AM"],[12:"12 Noon"],[13:"1 PM"],[14:"2 PM"],[15:"3 PM"],[16: "4 PM"],
                 [17:"5 PM"],[22: "10 PM"],[23:"11 PM"],[1000: "Disabled"],[2000: "Random"]]
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

// ---------------------
// Required callback methods, configure(), and helper methods:
// ---------------------

List<String> installed() {
   if (enableDebug) log.debug "installed()"
   //runIn(5, "getBattery")
   runIn(5, "refresh")
   initialize()
}

List<String> updated() {
   if (enableDebug) log.debug "updated()"
   initialize()
}

List<String> initialize() {
   if (enableDebug) log.debug "initialize()"
   unschedule()
   scheduleBatteryRefresh()
   Integer disableTime = 1800
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, "debugOff")
   }
   return configure()
}

List<String> configure() {
   log.warn "configure()"
   List<String> cmds = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null && settings[data.input.name] != data.ignoreValue) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   // Parameter 3 = 1 to send Report back to Hubitat after Set:
   cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: param3DefaultValue, parameterNumber: 3, size: 1))
   // Lifeline association (paramater 3 might take care of most/all of this? can remove one if causes problems...)
   cmds << zwaveSecureEncap(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId]))
   cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV1.manufacturerSpecificGet())
   cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
   return delayBetween(cmds, 300)
}

void debugOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void scheduleBatteryRefresh() {
   if (refreshTime != null && refreshTime != 1000) {
      String cronStr
      Integer s = Math.round(Math.random() * 60)
      Integer m = Math.round(Math.random() * 60)
      if (s >= 60) s = 59
      if (m >= 60) m = 59
      Integer hour = refreshTime as Integer
      if (hour == 2000) { // if set to random time
         Integer h = Math.round(Math.random() * 23)
         if (h == 2) h = 3 // avoid default maintenance window
         cronStr = "${s} ${m} ${h} ? * * *"
      } else if (hour >= 0 && hour <= 23) {
         cronStr = "${s} ${m} ${hour} ? * * *"
      }
      else {
         log.debug "invalid battery refresh time configuration: hour = $hour"
      }
      if (enableDebug) log.debug "battery schedule = \"${cronStr}\""
      if (cronStr) schedule(cronStr, "getBattery")
   }
   else {
      if (enableDebug) log.debug "Battery refresh not configured; unscheduling if scheduled"
      unschedule("getBattery")
   }
}

// ---------------------
// Z-Wave parsing methods:
// ---------------------

void parse(String description) {
   if (enableDebug) log.debug "parse: $description"
   if (description != "updated") {
      def cmd = zwave.parse(description, commandClassVersions)
      if (cmd) {
         zwaveEvent(cmd)
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
   sendHubCommand(new hubitat.device.HubAction(
      zwaveSecureEncap(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID,
         reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)),
         hubitat.device.Protocol.ZWAVE)
   )
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   if (enableDebug) log.debug "BasicReport: $cmd"
   positionEvents(cmd)
}

// Should no longer be necessary but keeping in case comes in anyway:
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
   if (enableDebug) log.debug "SwitchMultilevelReport: $cmd"
   positionEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.mtpwindowcoveringv1.MoveToPositionReport cmd) {
   if (enableDebug) log.debug "MoveToPositionReport: $cmd"
   positionEvents(cmd)
}

private void positionEvents(hubitat.zwave.Command cmd) {
   if (enableDebug) log.debug "positionEvents(cmd = $cmd)"
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
   if (device.currentValue("position") != position) {
      sendEvent(name: "position", value: position, unit: "%")
      logDesc("$device.displayName position is $position")
   }
   if (device.currentValue("switch") != switchValue) {
      sendEvent(name: "switch", value: switchValue)
      logDesc("$device.displayName switch is $switchValue")
   }
   if (device.currentValue("windowShade") != windowShadeState) {      
      sendEvent(name: "windowShade", value: windowShadeState)
      logDesc("$device.displayName windowShade position is $windowShadeState")
   }      
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport $cmd"
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

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
   if (enableDebug) log.debug "manufacturerId:   ${cmd.manufacturerId}"
   if (enableDebug) log.debug "manufacturerName: ${cmd.manufacturerName}"
   if (enableDebug) log.debug "productId:        ${cmd.productId}"
   if (enableDebug) log.debug "productTypeId:    ${cmd.productTypeId}"
   String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
   device.updateDataValue("MSR", msr)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStopLevelChange cmd) {
   if (enableDebug) log.debug "SwitchMultilevelStopLevelChange: $cmd"
   sendHubCommand(
      new hubitat.device.HubAction(zwaveSecureEncap(zwave.switchMultilevelV1.switchMultilevelGet()),
                                    hubitat.device.Protocol.ZWAVE)
   )
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
   if (enableDebug) log.debug "BatteryReport $cmd"
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

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
   if (enableDebug) log.debug "DeviceSpecificReport: ${cmd}"
   switch (cmd.deviceIdType) {
      case 1: // Serial number
         String serialNumber= ""
         if (cmd.deviceIdDataFormat == 1) {
               cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff, 1).padLeft(2, '0')}
         } else {
               cmd.deviceIdData.each { serialNumber += (char)it }
         }
         device.updateDataValue("serialNumber", serialNumber)
         break
   }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
   if (enableDebug) log.debug "skip: $cmd"
}

// ---------------------
// Command implementations:
// ---------------------

List<String> on() {
   if (enableDebug) log.debug "on()"
   Integer openTo = settings["openPosition"] ? (settings["openPosition"]  as Integer) : 50   
   setPosition(openTo)
}

List<String> off() {
   if (enableDebug) log.debug "off()"
   setPosition(0)
}

List<String> open() {
   if (enableDebug) log.debug "open()"
   Integer openTo = settings["openPosition"] ? (settings["openPosition"]  as Integer) : 50   
   setPosition(openTo)
}

List<String> close() {
   if (enableDebug) log.debug "close()"
   setPosition(0)
}

List<String> setPosition(Number value) {
   if (enableDebug) log.debug "setPosition($value)"
   Integer level = Math.max(Math.min(value as Integer, 99), 0)
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: level)
   //hubitat.zwave.Command cmd = new hubitat.zwave.commands.windowcoveringv1.WindowCoveringSet(values:[23:value.shortValue()])
   return [zwaveSecureEncap(cmd)]
}

List<String> startPositionChange(String direction) {
   if (enableDebug) log.debug "startPositionChange($direction)"
   Boolean openClose = (direction != "open")
   hubitat.zwave.Command cmd = zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: openClose, ignoreStartLevel: 1, startLevel: 0)
   // Probably not right, but could be starting point if need some day:
   //hubitat.zwave.Command cmd = new hubitat.zwave.commands.windowcoveringv1.WindowCoveringStartLevelChange(parameterId: 23, upDown: openClose)
   return [zwaveSecureEncap(cmd)]
}

List<String> stopPositionChange() {
   if (enableDebug) log.debug "stopPositionChange()"
   hubitat.zwave.Command cmd = zwave.switchMultilevelV1.switchMultilevelStopLevelChange()
   // Probably not right, but could be starting point if need some day:
   //hubitat.zwave.Command cmd = new hubitat.zwave.commands.windowcoveringv1.WindowCoveringStopLevelChange()
   return [zwaveSecureEncap(cmd)]
}

List<String> setLevel(Number value) {
   if (enableDebug) log.debug "setLevel($value)"
   return setPosition(value)
}

List<String> setLevel(Number value, Number duration) {
   if (enableDebug) log.debug "setLevel($value, $duration)"
   Integer level = Math.max(Math.min(value as Integer, 99), 0)
   Integer intDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: intDuration)
   //hubitat.zwave.Command cmd = new hubitat.zwave.commands.windowcoveringv1.WindowCoveringSet(values:[23:value.shortValue()], duration: duration)
   return [zwaveSecureEncap(cmd)]
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh()"
   state.lastBattAttemptAt = now()
   delayBetween([
      //zwaveSecureEncap(zwave.switchBinaryV1.switchBinaryGet()),
      zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelGet().format()),
      //zwaveSecureEncap(zwave.windowCoveringV1.windowCoveringGet().format()),
      zwaveSecureEncap(zwave.batteryV1.batteryGet().format()),
   ], 200)
}

// Sets parameter 7 to 1 (firmware 3.06+ only)
String initiateCalibration() {
   if (enableDebug) log.debug "initiateCalibration()"
   if (getDataValue("firmwareVersion") == "3.03" || getDataValue("firmwareVersion") == "3.02") {
      log.warn "Remote initiation of calibration is not possible on iBlinds v3 firmware versions before 3.06; ignoring command"
      return ""
   }
   String cmd = zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 1, parameterNumber: 7, size: 1).format())
   runIn(calibrationTime, "resetCalibrationParameter")
   return cmd
}

// ---------------------
// Miscellaneous methods:
// ---------------------

// Resets parameter 7 to 0 (recommended after calibration is done after setting to 1)
void resetCalibrationParameter() {
   if (enableDebug) log.debug "resetCalibrationParameter()"
   String cmd = zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 0, parameterNumber: 7, size: 1).format())
   sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

void getBattery() {
   if (enableDebug) log.debug "getBattery()"
   state.lastBattAttemptAt = now()
   String cmd = zwaveSecureEncap(zwave.batteryV1.batteryGet().format())
   sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

void logDesc(str) {
   if (settings.enableDesc == true) log.info(str)
}