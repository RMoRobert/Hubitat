/*
 * ===================== Zooz Scene Controller (ZEN32) Driver =====================
 *
 *  Copyright 2021 Robert Morris
 *  
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
 * BUTTON NUMBER/EVENT MAPPING:
 * 
 * "Base" button number:
 *   - relay/large button = button 5
 *   - small top left = button 1
 *   - small top right = button 2
 *   - small bottom left = button 3
 *   - small bottom right = button 4
 * Single taps, hold, and release:
 *  - base button number pushed, held, and released events
 * Multi-taps:
 *  - mathematically, a pushed event for button number = (base button number)  + (5 * (number of taps - 1))
      ... or specifically:
 *     * "button 1" taps: button 1, 6, 11, 16, or 21 pushed (taps 1-5)
 *     * "button 2" taps: button 2, 7, 12, 17, or 22 pushed (taps 1-5)
 *     * "button 3" taps: button 3, 8, 13, 18, or 23 pushed (taps 1-5)
 *     * "button 4" taps: button 4, 9, 14, 19, or 24 pushed (taps 1-5)
 *     * "button 5" taps: button 5, 10, 15, 20, or 25 pushed (taps 1-5)
 *
 * 
 *  Changelog:
 *  v1.0.1  (2021-04-23): Fix typo in BasicGet; pad firmware subversion with 0 as needed
 *  v1.0    (2021-04-01): Initial Release   
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x20: 2,    // Basic
   0x25: 2,    // SwitchBinary
   0x55: 2,    // TransportService
   0x59: 3,    // AssociationGroupInfo
   0x5B: 3,    // CentralScene
   0x6C: 1,    // Supervision
   0x70: 2,    // Configuration
   0x72: 2,    // ManufacturerSpecific
   0x85: 3,    // Association
   0x86: 3,    // Version
   0x87: 3,    // Indicator
   0x8E: 4,    // MultichannelAssociation
   0x9F: 1     // Security S2
]

// color name to parameter value mapping:
@Field static Map<String,Integer> colorNameMap = [
   "white": 0,
   "blue": 1,
   "green": 2,
   "red": 3
]

// LED/button number to parameter value mappings:
@Field static Map<Integer,Integer> ledIndicatorParams = [1: 2, 2: 3, 3: 4, 4: 5, 5: 1]
@Field static Map<Integer,Integer> ledColorParams = [1: 7, 2: 8, 3: 9, 4: 10, 5: 6]
@Field static Map<Integer,Integer> ledBrightnessParams = [1: 12, 2: 13, 3: 14, 4: 15, 5: 11]

@Field static final Map zwaveParameters = [
   16: [input: [name: "param.16", type: "number", title: "[16] Automtically turn relay off after ... minutes (0=disable auto-off; default)", range: 0..65535],
      size: 4],
   17: [input: [name: "param.17", type: "number", title: "[17] Automtically turn relay on after ... minutes (0=disable auto-on; default)", range: 0..65535],
      size: 4],
   18: [input: [name: "param.18", type: "enum", title: "[18] State on power restore (relay and buttons)",
       options: [[2: "Off"], [1: "On"], [0: "Previous state (default)"]]],
      size: 1],
   19: [input: [name: "param.19", type: "enum", title: "[19] Local (phyiscal) and Z-Wave control/smart bulb mode",
       options: [[0: "Disable local control, enable Z-Wave"], [1: "Enable local and Z-Wave control (default)"], [2: "Disable local and Z-Wave control"]]],
      size: 1],
   20: [input: [name: "param.20", type: "enum", title: "[20] Behavior of relay reports if local and/or Z-Wave control disabled (scene/button events are always sent)",
        options: [[0:"Send on/off reports and change LED"],[1:"Do not send on/off reports or change LED (default)"]]],
      size: 1],
   21: [input: [name: "param.21", type: "enum", title: "[21] 3-way switch type",
        options: [[0:"Mechanical (connected 3-way turns on/off) (default)"],[1:"Momentary (connected 3-way toggles on/off)"]]],
      size: 1]
]

// These get set by configure(), "default" parameter values necessary to make this driver work as expected and generally not exposed in the UI:
@Field static final Map defaultZwaveParameters = [
   // LED indicator mode for relay and buttons 1-4:
   //1: [value: 3, size: 1], // leaving this one (relay LED) out since it's set by separate preference
   2: [value: 3, size: 1], // these could default to 2 or 3 (always off or always on); defaulting to 3 for now, but could read/compare in future and not override if set to one...
   3: [value: 3, size: 1],
   4: [value: 3, size: 1],
   5: [value: 3, size: 1]
]

metadata {
   definition (name: "Zooz Scene Controller (ZEN32)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zen32-scene-ctlr.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "Configuration"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"

      command "refresh"

      command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]

      command "setLED", [[name:"ledNumber", type: "NUMBER", description: "LED/button number (1-5, 5=large/relay button)", constraints: 1..5],
                         [name:"colorName", type: "ENUM", description: "Color name (white, blue, green, red)", constraints: ["white", "blue", "green", "red"]],
                         [name:"brightness", type: "NUMBER", description: "Brightness level (100, 60, or 30%; will round to nearest; 0 for off)", constraints: [100,60,30,0]],
                        ]

      // Uncomment if switching from another driver and need to "clean up" things--will expose command in UI:
      //command "clearChildDevsAndState"

      //command "indicatorGet" //, [[name:"ledNumber", type: "NUMBER", description: "LED/button number (1-5, 5=large/relay button)", constraints: 1..5] ]
      command "indicatorSet", [[name:"Mode*", type: "ENUM", description: "Select the mode", constraints: ["Flash", "On", "Off"]],
                         [name:"ledNumber*", type: "NUMBER", description: "LED/Button number (1-5, 5=large button, 0=all)", constraints: 0..5],
                         [name:"flashSettings", type: "STRING", description: "Format: 1,2,3 : 1-On/Off period length in tenths of seconds, 2-Number of On/Off periods, 3-On time in tenths of seconds (asymmetric periods)"],
                        ]

      fingerprint mfr:"027A", prod:"7000", deviceId:"A008"
   }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "relayLEDBehavior", type: "enum", title: "Relay LED behavior", options: [[0:"LED on when relay off, off when on (default)"],[1:"LED on when relay on, off when off"],
         [2:"LED on or off as modified by \"Set LED\" command (recommended in some use cases)"]]
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

List<String> indicatorGet() {
   List<String> cmds = []
   cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSupportedGet(indicatorId:0x00))
   return delayBetween(cmds,400)
}

List<String> indicatorSet(String mode, Number ledNumber=0, String indSettings=null) {
   Map indMap = [0:0x50, 5:0x43, 1:0x44, 2:0x45, 3:0x46, 4:0x47]
   Map propValues = [3:2, 4:10, 5:0]
   Number indID = indMap[ledNumber as Integer]
   if (indSettings) {
      String[] setSplit = indSettings.split(',')
      //log.debug "indicatorSet: ${setSplit}, ${setSplit.size()}"
      for (int idx=0; idx < setSplit.size(); idx++) {
         if (setSplit[idx]) { propValues[idx+3] = setSplit[idx] as Integer }
      }
   }

   //log.debug "indicatorSet: ${ledNumber}, ${indSettings}, ${indID}, ${propValues}"
   List<String> cmds = []
   if (mode.equalsIgnoreCase("flash")) {
      cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value:0xFF, indicatorCount:3, indicatorValues:[
            [indicatorId:indID, propertyId:3, value:propValues[3]], //This property is used to set the duration in tenth of seconds of an On/Off period.
            [indicatorId:indID, propertyId:4, value:propValues[4]], //This property is used to set the number of On/Off periods to run
            [indicatorId:indID, propertyId:5, value:propValues[5]] //This property is used to set the length of the On time during an On/Off period. It allows asymetic On/Off periods.
         ]))
   }
   else {
      def onOff = (mode.equalsIgnoreCase("on") ? 0xFF : 0x00)
      cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value:0xFF, indicatorCount:1, indicatorValues:[
            [indicatorId:indID, propertyId:2, value:onOff]
         ]))
   }
   
   return delayBetween(cmds,400)
}


void logsOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
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

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
   if (logEnable) log.debug "VersionReport: ${cmd}"
   device.updateDataValue("firmwareVersion", """${cmd.firmware0Version}.${String.format("%02d", cmd.firmware0SubVersion)}""")
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

void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: ${cmd}"
   if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
}

void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd) {
   if (enableDebug) log.debug "BasicReport:  ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}            

// void zwaveEvent(hubitat.zwave.commands.basicv2.BasicSet cmd) {
//    if (enableDebug) log.debug "BasicSet: ${cmd}"
//    String value = (cmd.value ? "on" : "off")
//    if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
//    sendEvent(name: "switch", value: value)
// }

void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd) {
   if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {    
   if (enableDebug) log.debug "CentralSceneNotification: ${cmd}"
   Integer btnBaseNum = cmd.sceneNumber ?: 0
   Integer btnNum = btnBaseNum
   String btnAction = "pushed"
   if (cmd.keyAttributes as Integer == 2) btnAction = "held"
   else if (cmd.keyAttributes as Integer == 1) btnAction = "released"
   if ((cmd.keyAttributes as Integer) >= 3) {
      btnNum = btnBaseNum + (5 * ((cmd.keyAttributes as Integer) - 2))
   }

   if (btnNum) {
      String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
      if (enableDesc) log.info "${descriptionText}"
      sendEvent(name: "${btnAction}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")
   }
}

void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd) {
   if (enableDebug) log.debug "IndicatorReport: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport cmd) {
   if (enableDebug) log.debug "IndicatorSupportedReport: ${cmd}"
   if (cmd.nextIndicatorId > 0) {
      sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.indicatorV3.indicatorSupportedGet(indicatorId:cmd.nextIndicatorId)), hubitat.device.Protocol.ZWAVE)) 
   }
}

void zwaveEvent(hubitat.zwave.Command cmd){
   if (enableDebug) log.debug "skip: ${cmd}"
}

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   return delayBetween([
      zwaveSecureEncap(zwave.basicV2.basicGet()),
      zwaveSecureEncap(zwave.configurationV1.configurationGet()),
      zwaveSecureEncap(zwave.versionV3.versionGet())
   ], 100)
}

String on() {
   if (enableDebug) log.debug "on()"
   state.flashing = false
   zwaveSecureEncap(zwave.basicV2.basicSet(value: 0xFF))
}

String off() {
   if (enableDebug) log.debug "off()"
   state.flashing = false
   zwaveSecureEncap(zwave.basicV2.basicSet(value: 0x00))
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
   List<String> cmds = []
   
   sendEvent(name: "numberOfButtons", value: 25)

   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Preference parameter: setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }

   defaultZwaveParameters.each { param, data ->
      if (enableDebug) log.debug "Default parameter: setting parameter $param (size:  ${data.size}) to ${data.value}"
      cmds <<zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: data.value as BigInteger, parameterNumber: param, size: data.size))
   }

   if (relayLEDBehavior != null) {
      BigInteger val = relayLEDBehavior as BigInteger
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: val, parameterNumber: 1, size: 1))
   }

   cmds << zwaveSecureEncap(zwave.versionV3.versionGet())
   cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet())
   return delayBetween(cmds, 150)
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

   List<String> cmds = []
   
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Preference parameter: setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   
   if (relayLEDBehavior != null) {
      BigInteger val = relayLEDBehavior as BigInteger
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: val, parameterNumber: 1, size: 1))
   }

   cmds << zwaveSecureEncap(zwave.versionV3.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
   return delayBetween(cmds, 200)
}

List<String> setLED(Number ledNumber, String colorName, brightness) {
   if (enableDebug) log.debug "setLED(Number $ledNumber, String $colorName, Object $brightness)"
   Integer intColor = colorNameMap[colorName?.toLowerCase()] ?: 0
   Integer intLevel = 0
   switch (brightness as Integer) {
      case 1..44:
         intLevel = 2
         break
      case 45..74:
         intLevel = 1
         break
      default:
         intLevel = 0
   }
   List<String> cmds = []
   if ((brightness as Integer) <= 0) {
      // Set LED to "always off" (may want to change in future if add association), unless #5/relay and configured not to:
      if ((ledNumber as Integer) != 5 || ((ledNumber as Integer) == 5 && (relayLEDBehavior as Integer == 2))) {
         cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 2, parameterNumber: ledIndicatorParams[ledNumber as Integer], size: 1))
      }
      else {
         if (enableDebug) log.debug "LED number is 5 and level is 0 but configured to not allow turning off"
      }
   }
   else {
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: ledColorParams[ledNumber as Integer], size: 1))
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: ledBrightnessParams[ledNumber as Integer], size: 1))
      // Set LED to "always on" (may want to change in future if add association):
      cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 3, parameterNumber: ledIndicatorParams[ledNumber as Integer], size: 1))
   }
   return delayBetween(cmds, 500)
}

// Custom command (for apps/users)
String setConfigParameter(number, value, size) {
   return zwaveSecureEncap(setParameter(number, value, size.toInteger()))
}

// For internal/driver use
String setParameter(number, value, size) {
   if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)"
   return zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number, size: size))
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}
