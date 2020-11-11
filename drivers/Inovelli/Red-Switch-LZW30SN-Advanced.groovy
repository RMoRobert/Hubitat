/*
 * ===================== Inovelli Red Series Switch (LZW30-SN) Driver =====================
 *
 *  Copyright 2020 Robert Morris
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
 *  v2.1    (2020-11-10) - Added second set of "friendly" setIndicator and setLEDColor commands; allow more unset preferences (will not change if not set)
 *  v2.0    (2020-11-07) - Substantial rewrite, update for S2/C-7 and new switch firmware
 *                         NOTE: See forum thread for details; not 100% compatible with 1.x. Recommend renaming
 *                               and keeping old driver while converting. Old child devs not supported, and
 *                               some custom commands are different.
 *  v1.0    (2020-01-19) - Initial Release   
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
   0x20: 1,    // Basic
   0x25: 1,    // Switch Binary
   0x32: 3,    // Meter
   0x5B: 1,    // CentralScene
   0x70: 1,    // Configuration
   0x86: 2,    // Version
   0x98: 1     // Security
]

@Field static Map colorNameMap = [
   "red": 1,
   "red-orange": 5,
   "orange": 21,
   "yellow": 42,
   "chartreuse": 60,
   "green": 85,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "blue": 170,
   "violet": 212,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

@Field static Map effectNameMap = ["off": 0, "solid": 1, "pulse": 4, /*"fallback" for 31-SN users, can remove if don't want here:*/ "chase": 4, "fast blink": 2, "slow blink": 3]

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "State on power restore",
       options: [[2: "Off"], [1: "On"], [0: "Previous state (default)"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Paddle function",
       options: [[0: "Normal (default)"], [1: "Reverse"]]],
      size: 1],
   3: [input: [name: "param.3", type: "number", title: "Automtically turn switch off after ... seconds (0=disable auto-off; default)", range: 0..32767],
      size: 2],
   10: [input: [name: "param.10", type: "enum", title: "Send new power report when power changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   12: [input: [name: "param.12", type: "enum", title: "Send new energy report when energy changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   11: [input: [name: "param.11", type: "enum", title: "Send power and energy reports every",
        options: [[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour (default)"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],[32400: "9 hours"]]],
      size: 2],
   51: [input: [name: "param.51", type: "enum", title: "Disable physical on/off delay", options: [[0:"No (default)"],[1:"Yes (also disables multi-taps)"]]],
      size: 1]
]

metadata {
   definition (name: "Advanced Inovelli Red Series Switch (LZW30-SN)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Red-Switch-LZW30SN-Advanced.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "EnergyMeter"
      capability "VoltageMeasurement"
      capability "PowerMeter"
      capability "Configuration"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"

      command "flash"
      command "refresh"
      command "push", [[name: "Button Number*", type: "NUMBER"]]
      command "hold", [[name: "Button Number*", type: "NUMBER"]]
      command "release", [[name: "Button Number*", type: "NUMBER"]]
      command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]
      command "setIndicator", [[name: "Notification Value*", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]]
       command "setIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]
      command "setLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli format, 0-10"]]
      command "setLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]]]
      command "setOnLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
      command "setOffLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]

      fingerprint mfr:"031E", prod: "0002", deviceId: "0001", inClusters: "0x5E,0x70,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x32,0x5B,0x98,0x9F,0x25,0x6C,0x75,0x22,0x7A"
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

String secure(String cmd){
   return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
   return zwaveSecureEncap(cmd)
}

void parse(String description){
   if (enableDebug) log.debug "parse description: ${description}"
   hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
   if (cmd) {
      zwaveEvent(cmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	if (enableDebug) log.debug "VersionReport: ${cmd}"
	device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
	device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
	device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
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

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   if (enableDebug) log.debug "BasicReport:  ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}            

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.debug "BasicSet: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
   if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
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
      sendEvent(name: "${btnAction}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")
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
   secure(zwave.basicV1.basicSet(value: 0xFF))
}

String flashOff() {
   if (!state.flashing) return
   runInMillis((flashRate ?: 750).toInteger(), flashOn)
   secure(zwave.basicV1.basicSet(value: 0x00))
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   return delayBetween([
      secure(zwave.switchMultilevelV1.switchMultilevelGet()),
      secure(zwave.meterV3.meterGet(scale: 0)),
      secure(zwave.meterV3.meterGet(scale: 2)),
      secure(zwave.configurationV1.configurationGet()),
      secure(zwave.versionV2.versionGet())
   ], 100)
}

String on() {
   if (enableDebug) log.debug "on()"
   state.flashing = false
   secure(zwave.basicV1.basicSet(value: 0xFF))
}

String off() {
   if (enableDebug) log.debug "off()"
   state.flashing = false
   secure(zwave.basicV1.basicSet(value: 0x00))
}

void push(btnNum) {
   sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
}

void hold(btnNum) {
   sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
}
x
void release(btnNum) {
   sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
}

void installed(){
   log.warn "Installed..."
   sendEvent(name: "level", value: 1)
}

void configure() {
   log.warn "configure..."
   runIn(1800, logsOff)
   sendEvent(name: "numberOfButtons", value: 11)
   refresh() 
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.info "updated..."
   state.lastRan = now()
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, logsOff)
   }

   List<String> cmds = []

   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size)))
      }
    }

   cmds.add(secure(zwave.versionV2.versionGet()))
   cmds.add(secure(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1)))
   cmds.add(secure(zwave.protectionV2.protectionSet(localProtectionState: settings["disableLocal"] ? 1 : 0,
                                             rfProtectionState: settings["disableRemote"] ? 1 : 0)))   
   return delayBetween(cmds, 200)
}



// Sets "notification LED" parameter to calculated value (calculated 4-byte base-10 value, or 0 for none)
String setIndicator(value) {
   if (enableDebug) log.debug "setIndicator($value)"
   return setParameter(16, value, 4)
}

// Sets "notification LED" parameter to value calculated based on provided parameters
String setIndicator(String color, level, String effect, BigDecimal duration) {
   if (enableDebug) log.debug "setIndicator($color, $level, $effect, $duration)"
	Integer calcValue = 0
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   Integer intEffect = 0
   if (effect != null) intEffect = (effectNameMap[effect?.toLowerCase()] != null) ? effectNameMap[effect?.toLowerCase()] : 4

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
   return setParameter(8, calcValue, 4)
}

// Sets default LED color parameter to value (0-255) and level (0-10)
List<String> setLEDColor(value, level=null) {
   if (enableDebug) log.debug "setLEDColor(Object $value, Object $level)"
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: 5, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: level.toInteger(), parameterNumber: 6, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets default LED color parameter to named color (from map) and level (Hubitat 0-100 style)
List<String> setLEDColor(String color, level) {
   if (enableDebug) log.debug "setLEDColor(String $color, Object $level)"
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   intLevel = Math.round(intLevel/10)
   if (intLevel < 0) intLevel = 0
   else if (intLevel > 10) intLevel = 10
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: 5, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: 6, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets "on" LED level parameter to value (0-10)
String setOnLEDLevel(value) {
   if (enableDebug) log.debug "setOnLEDLevel($value)"
   return setParameter(6, value, 1)
}

// Sets "off" LED level parameter to value (0-10)
String setOffLEDLevel(value) {
   if (enableDebug) log.debug "setOffLEDLevel($value)"
   return setParameter(7, value, 1)
}

// Custom command (for apps/users)
String setConfigParameter(number, value, size) {
   return secure(setParameter(number, value, size.toInteger()))
}

// For internal/driver use
String setParameter(number, value, size) {
   if (enableDesc) log.info "Setting parameter $number (size: $size) to: $value"
   return secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number, size: size))
}