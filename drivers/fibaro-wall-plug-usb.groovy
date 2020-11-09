/*
 * ===================== Fibaro Wall Plug (FGWPB-121) Driver =====================
 *
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
 * =======================================================================================
 * 
 *  Changelog:
 *  1.0    (2020-11-08) - Initial release
 *
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
   0x20: 1,    // Basic
   0x25: 1,    // Switch Binary
   0x32: 3,    // Meter
   0x60: 4,    // Multichannel
   0x70: 1,    // Configuration
   0x86: 2,    // Version
]

@Field static final Map zwaveParameters = [
   2: [input: [name: "configParam2", type: "enum", title: "State on power restore", defaultValue: 1,
       options: [[0: "Off"], [1: "Previous state (default)"]]],
      size: 1],
   3: [input: [name: "configParam3", type: "enum", title: "Overload protection enabled", defaultValue: 0,
       options: [[0: "Disabled (default)"], [1000: "100 W"], [2500: "250 W"], [5000: "500 W"], [10000: "1000 W"], [15000: "1500 W"], [18000: "1800 W"]]],
      size: 2],
   11: [input: [name: "configParam11", type: "enum", title: "Send new power report when power changes by", defaultValue: 15,
        options: [[0:"Disabled"],[1: "1%"],[2: "2%"],[5:"5%"],[10:"10%"],[15:"15% (default)"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   12: [input: [name: "configParam12", type: "enum", title: "Send new energy report when energy changes by", defaultValue: 10,
        options: [[0:"Disabled"],[10:"0.1 kWh"],[15:"0.15 kWh"],[20:"0.2 kWh"],[50:"0.5 kWh"],[75:"0.75 kWh"],[100:"1 kWH"],[250:"2.5 kWH"],[500:"5 kWh"]]],
      size: 2],
   13: [input: [name: "configParam13", type: "enum", title: "Send periodic power reports reports every", defaultValue: 3600,
        options: [[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1200:"20 minutes"],[ 1800:"30 minutes"],[3600:"1 hour (default)"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],[32400: "9 hours"]]],
      size: 2],
   14: [input: [name: "configParam14", type: "enum", title: "Send periodic energy reports reports every", defaultValue: 3600,
        options: [[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour (default)"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],[32400: "9 hours"]]],
      size: 2],
   15: [input: [name: "configParam15", type: "enum", title: "Include power consumed by wall plug itself (not just load) in reports", defaultValue: 0,
        options: [[0:"No (load only)"],[1:"Yes (load plus plug)"]]],
      size: 1],
   40: [input: [name: "configParam40", type: "enum", title: "Flash LED violet if power exceeds (only valid if LED set to change based on power)", defaultValue: 18000,
        options: [[0: "Disabled (default)"], [1000: "100 W"], [2500: "250 W"], [5000: "500 W"], [10000: "1000 W"], [15000: "1500 W"], [18000: "1800 W (default)"]]],
      size: 2],
   41: [input: [name: "configParam41", type: "enum", title: "LED color when switch on",
        options: [[0: "Off"], [1: "Based on power (change smoothly; default)"], [2: "Based on power (change in steps)"], [3: "White"], [4: "Red"], [5: "Green"],
                  [6: "Blue"], [7: "Yellow"], [8: "Cyan"], [9: "Magenta"]]],
      size: 1],
   42: [input: [name: "configParam42", type: "enum", title: "LED color when switch off",
        options: [[0: "Off (default)"], [1: "Last measured power when on"], [3: "White"], [4: "Red"], [5: "Green"],
                  [6: "Blue"], [7: "Yellow"], [8: "Cyan"], [9: "Magenta"]]],
      size: 1]
]

@Field static final Map colorMap = [
   "off": 0, "power": 1, "powerStep": 2, "white": 3, "red": 4, "green": 5, "blue": 6, "yellow": 7, "cyan": 8, "magenta": 9
]

metadata {
   definition (name: "Fibaro Wall Plug (FGWPB-121)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/fibaro-wall-plug.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "EnergyMeter"
      capability "PowerMeter"
      capability "Configuration"
      capability "Refresh"

      // Could make child devices...
      attribute "usbPower", "NUMBER"
      attribute "usbEnergy", "NUMBER"

      // Uncommenting if switching from another driver and need to "clean up" things--will expose command in UI:
      //command "clearChildDevsAndState"

      command "reset" // resets metering
      command "setOnLEDColor", [[name:"Color*", type: "ENUM", description: "Color (string value from among options; power = smooth color change depending on power; powerStep = change in steps)", constraints: ["off", "power", "powerStep", "white", "red", "green", "blue", "yellow", "cyan", "magenta"]]]
      command "setOffLEDColor", [[name:"Color*", type: "ENUM", description: "Color (string value from among options; power = last power from when on)", constraints: ["off", "power", "white", "red", "green", "blue", "yellow", "cyan", "magenta"]]]

      fingerprint  mfr:"010F", prod:"1401", deviceId:"2000", inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x32,0x70,0x56,0x71,0x75,0x60,0x7A,0x22" 
    }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "refreshUSB", type: "bool", title: "Refresh USB power usage when outlet usage changes"
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void logsOff() {
   log.warn("Disabling debug logging")
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
   else {
      log.warn "couldn't parse $description"
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd){
   if (enableDebug) log.debug "SupervisionGet: ${cmd}"
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) {
   if (enableDebug) log.debug "MultiChannelCmdEncap"
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
	if (encapCmd) {
      if (enableDebug) log.trace "MultiChannelCmdEncap cmd = $encapCmd, ep = ${cmd.sourceEndPoint}"
		zwaveEvent(encapCmd, cmd.sourceEndPoint as Integer)
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
   if (enableDebug) log.debug "ignorning ProtectionReport: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, ep = null) {
   if (enableDebug) log.debug "MeterReport: ${cmd}, ep = $ep"
   if (ep == 2) { // USB port
      if (cmd.scale == 0) {
      sendEvent(name: "usbEnergy", value: cmd.scaledMeterValue, unit: "kWh")
      if (enableDesc) log.info "$device.displayName USB energy is ${cmd.scaledMeterValue} kWh"
      }
      else if (cmd.scale == 2) {
         sendEvent(name: "usbPower", value: cmd.scaledMeterValue, unit: "W")
         if (enableDesc) log.info "$device.displayName USB power is ${cmd.scaledMeterValue} W"
      }
      else {
         if (enableDebug) log.debug "Unhandled USB MeterReport: $cmd, ep = $ep"
      }
   }
   else if (ep == 1) { // Outlet
      if (cmd.scale == 0) {
      sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
      if (enableDesc) log.info "$device.displayName outlet energy is ${cmd.scaledMeterValue} kWh"
      }
      else if (cmd.scale == 2) {
         sendEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
         if (enableDesc) log.info "$device.displayName outlet power is ${cmd.scaledMeterValue} W"
      }
      else {
         if (enableDebug) log.debug "Unhandled outlet MeterReport: $cmd, ep = $ep"
      }
   }
   else if (ep == null) {    // Get individual ones, ignore this
     /*   
      if (cmd.scale == 0) {
      sendEvent(name: "bothEnergy", value: cmd.scaledMeterValue, unit: "kWh")
      if (enableDesc) log.info "$device.displayName total energy is ${cmd.scaledMeterValue} kWh"
      }
      else if (cmd.scale == 2) {
         sendEvent(name: "bothPower", value: cmd.scaledMeterValue, unit: "W")
         if (enableDesc) log.info "$device.displayName total power is ${cmd.scaledMeterValue} W"
      }
      else {
         if (enableDebug) log.debug "Unhandled outlet MeterReport: $cmd, ep = $ep"
      } */
      if (now() - (state.lastRefresh ?: 0) >= 5000) {
         state.lastRefresh = now()
         sendHubCommand(new hubitat.device.HubMultiAction(delayBetween([
         secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 0))),
         secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 2))),
         secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 0))),
         secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 2)))
         ], 300), hubitat.device.Protocol.ZWAVE))
      }
      else {
         if (enableDebug) log.debug "Not fetching endpoint meters due to being fetched recently"
      }
   }
   else {
      if (enableDebug) log.debug "Unhandled endpoint for MeterReport: $ep"
   }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd, ep = null) {
   if (enableDebug) log.debug "MeterReport: ${cmd}, ep = $ep"
	if (cmd.sensorType == 4) {
      sendEvent(name: "power", value: cmd.scaledSensorValue, unit: "W")
      if (enableDesc) log.info "$device.displayName power is ${cmd.scaledSensorValue} W"
	}
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: ${cmd}"
   if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep = null) {
   if (enableDebug) log.debug "BasicReport:  ${cmd}, ep = $ep"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}            

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, ep = null) {
   if (enableDebug) log.debug "BasicSet: ${cmd}, ep = $ep"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep = null) {
   if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}, ep = $ep"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.Command cmd, ep = null){
    if (enableDebug) log.debug "skip: ${cmd}, ep = $ep"
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   state.lastRefresh = now()
   return delayBetween([
      secure(zwave.basicV1.basicGet()),
      secure(zwave.meterV3.meterGet(scale: 0)),
      secure(zwave.meterV3.meterGet(scale: 2)),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 0))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 2))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 0))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 2))),
      secure(zwave.configurationV1.configurationGet())
   ], 250)
}

List<String> on() {
   if (enableDebug) log.debug "on()"
   return delayBetween([
            secure(zwave.basicV1.basicSet(value: 0xFF))
            ,secure(zwave.basicV1.basicGet())
    ], 250)
}

List<String> off() {
   if (enableDebug) log.debug "off()"
   return delayBetween([
            secure(zwave.basicV1.basicSet(value: 0x00))
            ,secure(zwave.basicV1.basicGet())
    ], 350)
}

void installed(){
   log.warn "installed..."
   sendEvent(name: "level", value: 1)
}

List<String> configure() {
   log.warn "configure..."
   runIn(1800, logsOff)
   sendEvent(name: "numberOfButtons", value: 11)
   refresh() 

   List<String> cmds = []

   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size)))
      }
    }

   cmds.add(secure(zwave.versionV2.versionGet()))
   cmds.add(secure(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1)))
   return delayBetween(cmds, 200)
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.info "updated..."
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, logsOff)
   }
   configure()
}

List<String> reset() {
   return delayBetween([
      secure(zwave.meterV3.meterReset()),
      secure(zwave.meterV3.meterGet(scale: 0)),
      secure(zwave.meterV3.meterGet(scale: 2)),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterReset())),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterReset())),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 0))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 0))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 1).encapsulate(zwave.meterV3.meterGet(scale: 2))),
      secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 2).encapsulate(zwave.meterV3.meterGet(scale: 2))),
      secure(zwave.configurationV1.configurationGet())
   ], 400)
}

// Sets "on" LED color parameter to value (string value from map options)
String setOnLEDColor(value) {
   if (enableDebug) log.debug "setOnLEDColor($value)"
   Integer intVal = colorMap[value] != null ? colorMap[value] : 1
   return setParameter(41, intVal, 1)
}

// Sets "off" LED color parameter to value (string value from map options)
String setOffLEDColor(value) {
   if (enableDebug) log.debug "setOffLEDColor($value)"
   Integer intVal = colorMap[value] != null ? colorMap[value] : 0
   return setParameter(42, intVal, 1)
}

private String setParameter(number, value, size) {
   if (enableDesc) log.info "Setting parameter $number (size: $size) to: $value"
   return secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number, size: size))
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}