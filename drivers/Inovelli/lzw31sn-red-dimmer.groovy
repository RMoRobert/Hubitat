/*
 * ===================== Inovelli Red Series Dimmer (LZW31-SN) Driver =====================
 *
 *  Copyright 2023 Robert Morris
 *  Portions based on code from Inovelli and Hubitat
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
 *  Changelog:
 *  v2.4.0  (2023-10-14) - Change LED scaling to match Red 2-1/Gen3 (0-100 instead of 0-10)
 *  v2.3.2  (2022-07-04) - Update importUrl
 *  v2.3.1  (2022-11-06) - Add "clear" as LED effect for Blue compatibility (sets parameter to 0)
 *  v2.3.0  (2021-11-07) - Added new preferences for newer firmware versions; update for new Hubitat button and flash capabilities;
 *                         Added target 1 firmware reports to device data (as firmwareTarget1)
 *  v2.2.0  (2021-04-24) - Z-Wave Supervision improvements
 *  v2.1.2  (2021-04-13) - Avoid converting button number to string when creating event
 *  v2.1.2  (2020-12-20) - Modified setParameter() to log only with debug logging enabled
 *  v2.1.1  (2020-11-18) - Fixed local vs. Z-Wave dimming parameter reversal (typo in Inovelli manual)
 *  v2.1    (2020-11-10) - Added second set of "friendly" setIndicator and setLEDColor commands; allow more unset preferences (will not change if not set)
 *  v2.0    (2020-11-07) - Complete rewrite, update for S2/C-7 and new dimmer firmware
 *                         NOTE: See forum thread for details; not 100% compatible with 1.x. Recommend renaming
 *                               and keeping old driver while converting. Old child devs not supported, and
 *                               some custom commands are different.
 *  v1.0    (2020-01-19) - Initial Release
 */

import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static final Map<Short,Short> commandClassVersions = [
   0x20: 1,    // Basic
   0x25: 1,    // Switch Binary
   0x26: 3,    // Switch Multilevel
   0x32: 3,    // Meter
   0x5B: 1,    // CentralScene
   0x6C: 1,    // Supervision
   0x70: 1,    // Configuration
   0x72: 2,    // ManufacturerSpecific
   0x86: 2,    // Version
   0x98: 1     // Security
]

@Field static final Map<String,Short> colorNameMap = [
   "red": 1,
   "red-orange": 4,
   "orange": 10,
   "yellow": 42,
   "chartreuse": 60,
   "green": 85,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "blue": 170,
   "violet": 208,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

@Field static final Map<String,Short> effectNameMap = ["off": 0, "solid": 1, "chase": 2, "fast blink": 3, "slow blink": 4, "pulse": 5]

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "Dimming rate from hub",
         options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds (default)"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Dimming rate from paddle",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[101:"Match \"dimming rate from hub\" (default)"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "On/off fade time from paddle",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[101:"Match \"dimming rate from hub\" (default)"]]],
      size: 1],
   4: [input: [name: "param.4", type: "enum", title: "On/off fade time from hub",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[101:"Match \"dimming rate from hub\" (default)"]]],
      size: 1],
   5: [input: [name: "param.5", type: "number", title: "Minimum dimmer level (default=1)", range: 1..45],
      size: 1],
   6: [input: [name: "param.6", type: "number", title: "Maximum dimmer level (default=99)", range: 55..99],
      size: 1],
   7: [input: [name: "param.7", type: "enum", title: "Paddle function", options: [[0:"Normal (default)"],[1:"Reverse"]]], 
      size: 1],
   8: [input: [name: "param.8", type: "number", title: "Automtically turn switch off after ... seconds (0=disable auto-off)", range: 0..32767],
      size: 2],
   9: [input: [name: "param.9", type: "number", title: "Default level for physical \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   10: [input: [name: "param.10", type: "number", title: "Default level for digital \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   11: [input: [name: "param.11", type: "enum", title: "State on power restore",
        options: [[0:"Off (default)"],[99:"On to full brightness"],[101:"Previous state"]]],
      size: 1],
   17: [input: [name: "param.17", type: "enum", title: "If LED bar disabled, temporarily light for ... seconds after dimmer adjustments",
        options: [[0:"Do not show"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds (default)"],[4:"4 seconds"],[5:"5 seconds"],[6:"6 seconds"],[7:"7 seconds"],[8:"8 seconds"],[9:"9 seconds"],[10:"10 seconds"]]],
      size: 1],
   18: [input: [name: "param.18", type: "enum", title: "Send new power report when power changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   20: [input: [name: "param.20", type: "enum", title: "Send new energy report when energy changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   19: [input: [name: "param.19", type: "enum", title: "Send power and energy reports every",
        options: [[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour (default)"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],[32400: "9 hours"]]],
      size: 2],
   21: [input: [name: "param.21", type: "enum", title: "AC power type", options: [[0:"Non-neutral"],[1:"Neutral (default)"]]],
      size: 1],
   22: [input: [name: "param.22", type: "enum", title: "Switch type", options: [[0:"Single-pole (default)"],[1:"Mutli-way with dumb switch"],[2:"Multi-way with aux switch"]]],
      size: 1],
   50: [input: [name: "param.50", type: "enum", title: "Button press/multi-tap delay (firmware 1.52+ only; only if physical delay/multi-taps not disabled)", options: [[1:"100 ms"],[2:"200 ms"],[3:"300 ms"],[4:"400 ms"],
      [5:"500 ms"],[6:"600 ms"],[7:"700 ms (default)"],[8:"800 ms"],[9:"900 ms"]]],
      size: 1],
   51: [input: [name: "param.51", type: "enum", title: "Disable physical on/off delay", options: [[1:"No (default)"],[0:"Yes (also disables multi-taps)"]]],
      size: 1],
   52: [input: [name: "param.52", type: "enum", title: "Smart bulb mode", options: [[0:"Disabled (default)"],[1:"On/off (prevents dimming below 99 when on)"], [2:"Smart bulb mode (firmare 1.54+ only)"]]],
      size: 1]
]

@Field static ConcurrentHashMap<Long, ConcurrentHashMap<Short, String>> supervisedPackets = [:]
@Field static ConcurrentHashMap<Long, Short> sessionIDs = [:]

metadata {
   definition (name: "Advanced Inovelli Red Series Dimmer (LZW31-SN)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/lzw31sn-red-dimmer.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "SwitchLevel"
      capability "ChangeLevel"
      capability "Flash"
      capability "EnergyMeter"
      capability "VoltageMeasurement"
      capability "PowerMeter"
      capability "Configuration"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"

      command "refresh"
      command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]
      command "setIndicator", [[name: "Notification Value*", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]]
      command "setIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["clear", "off", "solid", "chase", "fast blink", "slow blink", "pulse"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]
      //command "setLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli format, 0-10"]]
      command "setLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]
      command "setOnLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-100, 0=off)", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]
      command "setOffLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-100, 0=off)", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]

      // Uncomment if switching from another driver and need to "clean up" things--will expose command in UI:
      //command "clearChildDevsAndState"

      attribute "amperage", "number"

      fingerprint mfr:"031E", prod:"0001", deviceId:"0001", inClusters:"0x5E,0x26,0x70,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x32,0x98,0x9F,0x5B,0x6C,0x75,0x22,0x7A"
   }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }      
      input name: "disableLocal", type: "bool", title: "Disable local control (at wall/switch)"
      input name: "disableRemote", type: "bool", title: "Disable remote control (from Z-Wave/hub)"
      input name: "flashRate", type: "enum", title: "Flash rate", options: [[500: "500 ms"], [750: "750 ms"], [1000: "1 second"], [2000: "2 seconds"], [5000: "5 seconds"]], defaultValue: 750
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void logsOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void parse(String description) {
   if (enableDebug) log.debug "parse description: ${description}"
   hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
   if (cmd) {
      zwaveEvent(cmd)
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

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {
   if (enableDebug) log.debug "supervision report for session: ${cmd.sessionID}"
   if (!supervisedPackets[device.id]) { supervisedPackets[device.id] = [:] }
   if (supervisedPackets[device.id][cmd.sessionID] != null) { supervisedPackets[device.id].remove(cmd.sessionID) }
   unschedule(supervisionCheck)
}

void supervisionCheck() {
   // re-attempt once
   if (!supervisedPackets[device.id]) { supervisedPackets[device.id] = [:] }
   supervisedPackets[device.id].each { k, v ->
      if (enableDebug) log.debug "re-sending supervised session: ${k}"
      sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(v), hubitat.device.Protocol.ZWAVE))
      supervisedPackets[device.id].remove(k)
   }
}

Short getSessionId() {
   Short sessId = 1
   if (!sessionIDs[device.id]) {
      sessionIDs[device.id] = sessId
      return sessId
   } else {
      sessId = sessId + sessionIDs[device.id]
      if (sessId > 63) sessId = 1
      sessionIDs[device.id] = sessId
      return sessId
   }
}

hubitat.zwave.Command supervisedEncap(hubitat.zwave.Command cmd) {
   if (getDataValue("S2")?.toInteger() != null) {
      hubitat.zwave.commands.supervisionv1.SupervisionGet supervised = new hubitat.zwave.commands.supervisionv1.SupervisionGet()
      supervised.sessionID = getSessionId()
      if (enableDebug) log.debug "new supervised packet for session: ${supervised.sessionID}"
      supervised.encapsulate(cmd)
      if (!supervisedPackets[device.id]) { supervisedPackets[device.id] = [:] }
      supervisedPackets[device.id][supervised.sessionID] = supervised.format()
      runIn(5, supervisionCheck)
      return supervised
   } else {
      return cmd
   }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
   if (enableDebug) log.debug "VersionReport: ${cmd}"
   if (cmd.firmware0Version != null && cmd.firmware0SubVersion != null) {
      device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}")
      if (enableDebug) log.debug "${device.displayName} firmware0Version and subversion is ${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}"
   }
   else if (cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
      device.updateDataValue("applicationVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}")
      if (enableDebug) log.debug "${device.displayName} applicationVersion amd subversion is ${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
   }
   if (cmd.zWaveProtocolVersion != null && cmd.zWaveProtocolSubVersion != null) {
      device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion.toString().padLeft(2,'0')}")
      if (enableDebug) log.debug "${device.displayName} zWaveProtocolVersion and subversion is ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion.toString().padLeft(2,'0')}"
   }
   if (cmd.hardwareVersion != null) {
      device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
      if (enableDebug) log.debug "${device.displayName} hardwareVersion is ${cmd.hardwareVersion}"
   }
   cmd.targetVersions.each { tgt ->
      String tgtFW = "${tgt.version}.${tgt.subVersion.toString().padLeft(2,'0')}"
      device.updateDataValue("firmwareTarget${tgt.version}" as String, tgtFW)
      if (enableDebug) log.debug "Target ${tgt.version} firwmare is $tgtFW"
   }
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
   if (enableDebug) log.debug "DeviceSpecificReport v2: ${cmd}"
   switch (cmd.deviceIdType) {
      case 1:
         // serial number
         String serialNumber = ""
         if (cmd.deviceIdDataFormat==1) {
            cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff, 1).padLeft(2, '0')}
         } else {
            cmd.deviceIdData.each { serialNumber += (char)it }
         }
         if (enableDebug) log.debug "Device serial number is $serialNumber"
         device.updateDataValue("serialNumber", serialNumber)
         break
   }
}

void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd) {
   if (enableDebug) log.debug "ProtectionReport: ${cmd}"
   if (enableDesc) log.info ("${device.displayName}: Protection report received: Local protection = ${cmd.localProtectionState > 0 ? "on" : "off"}, remote protection = ${cmd.rfProtectionState > 0 ? "on" : "off"}")
   device.updateSetting("disableLocal", [value: cmd.localProtectionState > 0 ? true : false, type: "bool"])
   device.updateSetting("disableRemote", [value: cmd.rfProtectionState > 0 ? true : false, type: "bool"])
}

void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
   if (enableDebug) log.debug "MeterReport: ${cmd}"
   if (cmd.scale == 0) {
      if (cmd.meterType == 161) {
         sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
         if (enableDesc) log.info "$device.displayName voltage is ${cmd.scaledMeterValue} V"
      }
      else if (cmd.meterType == 1) {
         sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
         if (enableDesc) log.info "$device.displayName energy is ${cmd.scaledMeterValue} kWh"
      }
   }
   else if (cmd.scale == 1) {
      sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A")
      if (enableDesc) log.info "$device.displayName amperage is ${cmd.scaledMeterValue} A"
   }
   else if (cmd.scale == 2) {
      sendEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
      if (enableDesc) log.info "$device.displayName power is ${cmd.scaledMeterValue} W"
   }
   else {
      if (enableDebug) log.debug "Unhandled MeterReport: $cmd"
   }
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: ${cmd}"
   if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
}

// Do not seem to be needed since also does SwitchMutliLevel. Leaving in case this changes...
/*
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   if (enableDebug) log.warn "BasicReport:  ${cmd}"
   dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.warn "BasicSet: ${cmd}"
   dimmerEvents(cmd)
}
*/

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
   if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}"
   dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
   if (enableDebug) log.debug "SwitchMultilevelReport: ${cmd}"
   dimmerEvents(cmd)
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
   if (enableDebug) log.debug "dimmerEvents value: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
   if (cmd.value) {
      if (enableDesc && device.currentValue("level") != cmd.value) log.info "${device.displayName} level is ${cmd.value}%"
      sendEvent(name: "level", value: cmd.value, unit: "%")
   }
}

void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {    
   if (enableDebug) log.debug "CentralSceneNotification: ${cmd}"
   Integer btnNum = 0
   String btnAction = "pushed"
   if (cmd.sceneNumber == 2) {  // Up paddle
      switch(cmd.keyAttributes as int) {
         case 0:
            btnNum = 1
            break
         case 1:
            btnNum = 1
            btnAction = "released"
            break
         case 2:
            btnNum = 1
            btnAction = "held"
            break
         case 3:
            btnNum = 3
            break
         case 4:
            btnNum = 5
            break
         case 5:
            btnNum = 7
            break
         case 6:
            btnNum = 9
            break
      }
   } else if (cmd.sceneNumber == 1) { // Down paddle
      switch(cmd.keyAttributes as int) {
         case 0:
            btnNum = 2
            break
         case 1:
            btnNum = 2
            btnAction = "released"
            break
         case 2:
            btnNum = 2
            btnAction = "held"
            break
         case 3:
            btnNum = 4
            break
         case 4:
            btnNum = 6
            break
         case 5:
            btnNum = 8
            break
         case 6:
            btnNum = 10
            break
      }
   } else if (cmd.sceneNumber == 3) { // Config button
      btnNum = 11
   } else {
      log.debug "Unable to parse: ${cmd}"
   }

   if (btnNum) {
      String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
      if (enableDesc) log.info "${descriptionText}"
      sendEvent(name: "${btnAction}", value: btnNum, descriptionText: descriptionText, isStateChange: true, type: "physical")
   }
}

void zwaveEvent(hubitat.zwave.Command cmd){
   if (enableDebug) log.debug "skip: ${cmd}"
}

String flash() {
   if (enableDesc) log.info "${device.getDisplayName()} was set to flash with a rate of ${flashRate ?: 750} milliseconds"
   state.flashing = true
   return flashOn()
}

String flashOn() {
   if (!state.flashing) return
   runInMillis((flashRate ?: 750).toInteger(), flashOff)
   return zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelSet(value: 0xFF, dimmingDuration: 0))
}

String flashOff() {
   if (!state.flashing) return
   runInMillis((flashRate ?: 750).toInteger(), flashOn)
   return zwaveSecureEncap(zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00, dimmingDuration: 0))
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   return delayBetween([
      zwaveSecureEncap(zwave.switchMultilevelV1.switchMultilevelGet()),
      zwaveSecureEncap(zwave.meterV3.meterGet(scale: 0)),
      zwaveSecureEncap(zwave.meterV3.meterGet(scale: 2)),
      zwaveSecureEncap(zwave.configurationV1.configurationGet()),
      zwaveSecureEncap(zwave.versionV2.versionGet())
   ], 125)
}

String on() {
   if (enableDebug) log.debug "on()"
   state.flashing = false
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: 0xFF)
   return zwaveSecureEncap(cmd)
   //return zwaveSecureEncap(supervisedEncap(cmd))
}

String off() {
   if (enableDebug) log.debug "off()"
   state.flashing = false
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00)
   return zwaveSecureEncap(cmd)
   //return zwaveSecureEncap(supervisedEncap(cmd))
}

String setLevel(value) {
   if (enableDebug) log.debug "setLevel($value)"
   state.flashing = false
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: value < 100 ? value : 99)
   return zwaveSecureEncap(cmd)
   //return zwaveSecureEncap(supervisedEncap(cmd))
}

String setLevel(value, duration) {
   if (enableDebug) log.debug "setLevel($value, $duration)"
   state.flashing = false
   Integer dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
   hubitat.zwave.Command cmd = zwave.switchMultilevelV2.switchMultilevelSet(value: value < 100 ? value : 99, dimmingDuration: dimmingDuration)
   return zwaveSecureEncap(cmd)
   //return zwaveSecureEncap(supervisedEncap(cmd))
}

String startLevelChange(direction) {
   Integer upDown = direction == "down" ? 1 : 0
   return zwaveSecureEncap(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0))
}

String stopLevelChange() {
   return zwaveSecureEncap(zwave.switchMultilevelV1.switchMultilevelStopLevelChange())
}

void push(btnNum) {
   sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
}

void hold(btnNum) {
   sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
}

void release(btnNum) {
   sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
}

void installed(){
   log.warn "Installed..."
   sendEvent(name: "level", value: 1)
}

List<String> configure() {
   log.warn "configure..."
   runIn(1800, "logsOff")
   sendEvent(name: "numberOfButtons", value: 11)
   updated() 
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.info "updated..."
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, "logsOff")
   }

   List<String> cmds = []

   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(
            zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger,
                                                                    parameterNumber: param,
                                                                    size: data.size))
         )
      }
    }

   cmds.add(zwaveSecureEncap(zwave.versionV2.versionGet()))
   cmds.add(zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1)))
   cmds.add(zwaveSecureEncap(zwave.protectionV2.protectionSet(localProtectionState: settings["disableLocal"] ? 1 : 0,
                                             rfProtectionState: settings["disableRemote"] ? 1 : 0)))   
   return delayBetween(cmds, 200)
}


// Sets "notification LED" parameter to calculated value (calculated 4-byte base-10 value, or 0 for none)
String setIndicator(value) {
   if (enableDebug) log.debug "setIndicator($value)"
   return setParameter(16, value, 4)
}

// Sets "notification LED" parameter to value calculated based on provided parameters
String setIndicator(String color, level, String effect, BigDecimal duration=255) {
   if (enableDebug) log.debug "setIndicator($color, $level, $effect, $duration)"
   if (effect == "clear") {
      return setParameter(16, 0, 4)
   }
   else {
      Integer calcValue = 0
      Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
      Integer intLevel = level as Integer
      Integer intEffect = 0
      if (effect != null) intEffect = (effectNameMap[effect?.toLowerCase()] != null) ? effectNameMap[effect.toLowerCase()] : 4
      // Convert level from 0-100 to 0-10:
      intLevel = Math.round(intLevel/10)
      // Range check:
      if (intLevel < 0) intLevel = 0
      else if (intLevel > 10) intLevel = 10
      if (duration < 1) duration = 1
      else if (duration > 255) duration = 255
      if (intEffect != 0) {
         calcValue += intColor // * 1
         calcValue += intLevel * 256
         calcValue += duration * 65536
         calcValue += intEffect * 16777216
      }
      return setParameter(16, calcValue, 4)
   }
}

// Sets default LED color parameter to named color (from map) and level (0-100, scaled in method to Inovelli 0-10 format)
List<String> setLEDColor(String color, level=null) {
   if (enableDebug) log.debug "setLEDColor(String $color, Object $level)"
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   List<String> cmds = []
   cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: 13, size: 2)))
   if (level != null) {
      Double dblLevel = level.toDouble()
      Integer scaledIntLevel = Math.round(dblLevel/10)
      if (dblLevel >= 0.1 && scaledIntLevel == 0) scaledIntLevel = 1
      if (scaledIntLevel < 0) scaledIntLevel = 0
      else if (scaledIntLevel > 10) scaledIntLevel = 10
      cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: scaledIntLevel, parameterNumber: 14, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets "on" LED level parameter to value (0-100)
String setOnLEDLevel(value) {
   if (enableDebug) log.debug "setOnLEDLevel($value)"
   Double dblLevel = value.toDouble()
   Integer scaledIntLevel = Math.round(dblLevel/10)
   if (dblLevel >= 0.1 && scaledIntLevel == 0) scaledIntLevel = 1
   return setParameter(14, scaledIntLevel, 1)
}

// Sets "off" LED level parameter to value (0-100)
String setOffLEDLevel(value) {
   if (enableDebug) log.debug "setOffLEDLevel($value)"
   Double dblLevel = value.toDouble()
   Integer scaledIntLevel = Math.round(dblLevel/10)
   if (dblLevel >= 0.1 && scaledIntLevel == 0) scaledIntLevel = 1
   return setParameter(15, scaledIntLevel, 1)
}

// Custom command (for apps/users)
String setConfigParameter(number, value, size) {
   return setParameter(number, value, size.toBigInteger())
}

// For internal/driver use
String setParameter(number, value, size) {
   if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)"
   hubitat.zwave.Command cmd = zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toBigInteger(), parameterNumber: number as Short, size: size as Short)
   return zwaveSecureEncap(cmd)
   //return zwaveSecureEncap(supervisedEncap(cmd))
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}