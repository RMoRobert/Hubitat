/*
 * =============== HomeSeer HS-WX300 (Z-Wave Dimmer/Switch - Switch) Driver ===============
 *
 *  Copyright 2021 Robert Morris
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
 *  v1.0    (2021-11-06) - Initial release
 */

import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static final Map<Short,Short> commandClassVersions = [
   0x20: 1,    // Basic (2)
   0x25: 1,    // Switch Binary (2) - Switch Mode Only
   0x27: 1,    // Switch All
 //0x26: 3,    // Switch Multilevel (4) - Dimmer Mode Only
   0x5B: 1,    // CentralScene (3)
   0x55: 1,    // Transport Service (2))
   0x59: 1,    // AssociationGrpInfo
   0x5A: 1,    // DeviceResetLocally
   0x5E: 2,    // ZwaveplusInfo
   0x6C: 1,    // Supervision
   0x70: 1,    // Configuration (4)
   0x7A: 2,    // FirmwareUpdateMd
   0x72: 2,    // ManufacturerSpecific
   0x85: 2,    // Association
   0x86: 2,    // Version
   0x98: 1,    // Security
   0x9F: 1     // Security S2
]

@Field static final Map<String,Short> normalColorNameMap = [
   "white": 0,
   "red": 1,
   "green": 2,
   "blue": 3,
   "magenta": 4,
   "yellow": 5,
   "cyan": 6
]

@Field static final Map<String,Short> statusColorNameMap = [
   "off": 0,
   "red": 1,
   "green": 2,
   "blue": 3,
   "magenta": 4,
   "yellow": 5,
   "cyan": 6,
   "white": 7
]

@Field static final Map<Short,Map> zwaveParameters = [
   3: [size: 1, input: [name: "param.3", title: "Bottom LED operation (in normal mode); manual may have these reversed",
       type: "enum", options: [[0: "LED on when load off"],[1:"LED off when load off (DEFAULT)"]]]],
   4: [size: 1, input: [name: "param.4", title: "Paddle orientation",
       type: "enum", options: [[0: "Top of paddle turns load on (DEFAULT)"],[1:"Bottom of paddle turns load on"]]]],
   6: [size: 1, input: [name: "param.6", title: "Multi-tap/Central Scene (button events)",
       type: "enum", options: [[0: "Enabled (DEFAULT)"],[1:"Disabled (controls load without delay; eliminates held, released, and mulit-taps)"]]]],
   // This is exposed as a command instead but can be uncommented if desired:
   /*13: [size: 1, input: [name: "param.13", title: "LED disply mode", type: "enum",
           options: [[0:"Normal (LEDs show load status)"],[1:"Status (custom LED status)"]]]],*/
   30: [size: 1, input: [name: "param.30", title: "Blink frequency (when in status mode and blinking)", type: "enum",
      options: [[0: "No blink"], [1: "100 ms on, 100 ms off"], [2: "200 ms on, 200 ms off"], [3: "300 ms on, 300 ms off"],
                [4: "500 ms on, 400 ms off"], [5: "600 ms on, 500 ms off"], [6: "600 ms on, 600 ms off"], [7: "700 ms on, 700 ms off"],
                [8: "800 ms on, 800 ms off"], [10: "1 s on, 1 s off"],  [15: "1.5 s on, 1 s off"], [20: "2 s on, 2 s off"],
                [25: "2.5 s on, 2.5 s off"], [30: "3 s on, 3 s off"], [50: "5 s on, 5 s off"], [100: "10 s on, 10 s off"],
                [150: "15 s on, 15 s off"], [200: "20 s on, 20 s off"], [250: "25 s on, 25 s off" ]]]],
   // This is exposed as a command instead but can be uncommented if desired:
   /*14: [size: 1, input: [name: "param.14", title: "Normal mode LED color", type: "enum",
         options: [[0:"off"],[1:"red"],[2:"green"],[3:"blue"],[4:"magenta"],[5:"yellow"],[6:"cyan"],[7:"white"]]]],*/
   32: [size: 1, input: [name: "param.32", title: "Wire mode (neutral/non-neutral)", type: "enum",
           options: [[0:"3-wire mode (neutral, line, load) (DEFAULT)"],[1:"2-wire mode (line, load)"]]]]
]

@Field static ConcurrentHashMap<Long, ConcurrentHashMap<Short, String>> supervisedPackets = [:]
@Field static ConcurrentHashMap<Long, Short> sessionIDs = [:]
@Field static final Integer supervisionCheckDelay = 5
@Field static final Integer debugAutoDisableMinutes = 30

metadata {
   definition (name: "HomeSeer HS-WX300 Advanced (Switch)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/homeseer/homeseer-hs-wx300-advanced-switch.groovy") {
      capability "Actuator"
      capability "Switch"
      capability "ChangeLevel"
    //capability "Flash"  // does not work well
      capability "Configuration"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "Refresh"

      command "setStatusLED", [[name:"ledNumber*", type: "ENUM", description: "LED number, 1 (bottom) to 7 (top)", constraints: [1,2,3,4,5,6,7]],
                               [name:"colorName*", type: "ENUM", description: "LED color", constraints: statusColorNameMap.collect { e -> e.key } ],
                               [name:"blink", type: "ENUM", description: "Blinking (on) or solid (off)", constraints: ["on", "off"]]]

      command "setNormalLEDColor", [[name: "color*", type: "ENUM", description: "Normal mode LED color", constraints: normalColorNameMap.collect { e -> e.key }],
                                    [name: "setModeToNormal", type: "ENUM", description: "Also set LED to normal mode if currently in status mode?", constraints: ["false", "true"]]]

      command "setLEDMode", [[name:"mode*", type: "ENUM", description: "Normal mode (LEDs show load status) or status mode (custom LED statuses)",
                              constraints: ["normal", "status"]]]

      command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]

      fingerprint mfr: "000C", prod: "4447", deviceId: "4037", inClusters: "0x5E,0x55,0x9F,0x6C"  // WX300 (switch mode, S2)
      fingerprint mfr: "000C", prod: "4447", deviceId: "4037", inClusters: "0x5E,0x85,0x59,0x5A,0x7A,0x87,0x72,0x8E,0x73,0x9F,0x6C,0x55,0x86,0x25,0x70,0x5B"  // WX300 (switch mode)
   }

   preferences {
      input name: "showParamNumbers", type: "bool", title: "Show parameter number in preference description", defaultValue: true
      Map zwaveParams = zwaveParameters
      if (showParamNumbers) {
         zwaveParams.each {
            if (!(it.value.input.title.startsWith("[${it.key}]"))) {
               it.value.input.title = "[${it.key}] " + it.value.input.title
            }
         }
      }
      zwaveParams.each {
         input it.value.input
      }
      input name: "flashRate", type: "enum", title: "Flash rate", options: [[500: "500 ms"], [750: "750 ms"], [1000: "1 second"], [2000: "2 seconds"], [5000: "5 seconds"]], defaultValue: 750
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void installed(){
   log.debug "installed()"
   state.blinkval = 0b0000000
   (1..7).each {
      state."statusled${it}" = 0
   }
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.debug "updated()"
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in ${debugAutoDisableMinutes} minutes..."
      runIn(debugAutoDisableMinutes*60, "logsOff")
   }

   List<String> cmds = []

   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(
            scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size
         )))
      }
   }

   cmds.add(zwaveSecureEncap(zwave.versionV2.versionGet()))
   cmds.add(zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1)))
   return delayBetween(cmds, 200)
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

void supervisionCheck() {
   // re-attempt once
   if (!supervisedPackets[device.idAsLong]) { supervisedPackets[device.idAsLong] = [:] }
   supervisedPackets[device.idAsLong].each { k, v ->
      if (enableDebug) log.debug "re-sending supervised session: ${k}"
      sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(v), hubitat.device.Protocol.ZWAVE))
      supervisedPackets[device.idAsLong].remove(k)
   }
}

Short getSessionId() {
   Short sessId = 1
   if (!sessionIDs[device.idAsLong]) {
      sessionIDs[device.idAsLong] = sessId
      return sessId
   } else {
      sessId = sessId + sessionIDs[device.idAsLong]
      if (sessId > 63) sessId = 1
      sessionIDs[device.idAsLong] = sessId
      return sessId
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
   if (!supervisedPackets[device.idAsLong]) { supervisedPackets[device.idAsLong] = [:] }
   if (supervisedPackets[device.idAsLong][cmd.sessionID] != null) { supervisedPackets[device.idAsLong].remove(cmd.sessionID) }
   unschedule(supervisionCheck)
}

hubitat.zwave.Command supervisedEncap(hubitat.zwave.Command cmd) {
   if (getDataValue("S2")?.toInteger() != null) {
      hubitat.zwave.commands.supervisionv1.SupervisionGet supervised = new hubitat.zwave.commands.supervisionv1.SupervisionGet()
      supervised.sessionID = getSessionId()
      if (enableDebug) log.debug "new supervised packet for session: ${supervised.sessionID}"
      supervised.encapsulate(cmd)
      if (!supervisedPackets[device.idAsLong]) { supervisedPackets[device.idAsLong] = [:] }
      supervisedPackets[device.idAsLong][supervised.sessionID] = supervised.format()
      runIn(supervisionCheckDelay, supervisionCheck)
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

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: ${cmd}"
   if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
   if (cmd.parameterNumber == 31) {
      // Status LED blink bitmask parameter
      state.blinkval = cmd.scaledConfigurationValue
   }
   else if (cmd.parameterNumber >= 21 && cmd.parameterNumber <= 27) {
      // Status LED color parameters
      Integer ledNum = cmd.parameterNumber - 20
      state."statusled${ledNum}" = cmd.scaledConfigurationValue
   }
}

// Might be needed for switch mode? Dimmer does not use
/*
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
   if (enableDebug) log.warn "BasicReport:  ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.warn "BasicSet: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
}
*/

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
   if (cmd.sceneNumber == 1) {  // Up paddle
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
   }
   else if (cmd.sceneNumber == 2) { // Down paddle
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
   }
   else {
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

/*
String flash() {
   if (enableDesc) log.info "${device.getDisplayName()} was set to flash with a rate of ${flashRate ?: 750} milliseconds"
   state.flashing = true
   return flashOn()
}

String flashOn() {
   if (!state.flashing) return
   runInMillis((flashRate ?: 750).toInteger(), flashOff)
   return zwaveSecureEncap(zwave.basicV1.basicSet(value: 0xFF))
}

String flashOff() {
   if (!state.flashing) return
   runInMillis((flashRate ?: 750).toInteger(), flashOn)
   return zwaveSecureEncap(zwave.basicV1.basicSet(value: 0x00))
}
*/

List<String> refresh() {
   if (enableDebug) log.debug "refresh"
   return delayBetween([
      zwaveSecureEncap(zwave.switchMultilevelV1.switchBinaryGet()),
      zwaveSecureEncap(zwave.configurationV1.configurationGet()),
      zwaveSecureEncap(zwave.versionV2.versionGet())
   ], 150)
}

List<String> configure() {
   log.debug "configure()"
   runIn(debugAutoDisableMinutes*60, logsOff)
   sendEvent(name: "numberOfButtons", value: 10)
   updated() 
}

String on() {
   if (enableDebug) log.debug "on()"
   state.flashing = false
   hubitat.zwave.Command cmd = zwave.basicV1.basicSet(value: 0xFF)
   return zwaveSecureEncap(supervisedEncap(cmd))
}

String off() {
   if (enableDebug) log.debug "off()"
   state.flashing = false
   hubitat.zwave.Command cmd = zwave.basicV1.basicSet(value: 0x00)
   return zwaveSecureEncap(supervisedEncap(cmd))
}

void push(Integer btnNum) {
   sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
}

void hold(Integer btnNum) {
   sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
}

void release(Integer btnNum) {
   sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
}

List<Integer> getStatusLEDStates() {
   List<Integer> states = new ArrayList(7)
   (1..7).each {
      states.add(state."statusled${it}" ?: 0)
   }
   return states
}

List<String> setNormalLEDColor(String color, setModeToNormal=false) {
   if (enableDebug) log.debug "setNormalLEDColor(String $color, $setModeToNormal)"
   List<String> cmds = []
   Short colorNum = normalColorNameMap[color]
   if (colorNum == null) {
      log.error "Invalid color name specified: $color"
   }
   Boolean setToNormal = (!setModeToNormal || setModeToNormal == "false" ) ? false : true
   return setNormalLEDColor(colorNum, setToNormal)
}

// Not exposed as command, but could be useful as alternate form; also used internally by the custom command:
List<String> setNormalLEDColor(Short colorNum, Boolean setModeToNormal=false) {
   if (enableDebug) log.debug "setNormalLEDColor(Short $color, $setModeToNormal)"
   List<String> cmds = []
   if (colorNum < 0 || colorNum > 7) {
      log.error "Invalid color number specified: $colorNum"
   }
   else {
      cmds.add(
         setConfigParameter(14, colorNum, 1)
      )
   }
   if (setModeToNormal) {
      cmds.add(setConfigParameter(13, 0, 1))
   }
   return delayBetween(cmds, 750)
}

List<String> setStatusLED(String ledNumber, String colorName, String blink) {
   if (enableDebug) log.debug "setStatusLED(Integer $ledNumber, String $colorName, String $blink)"
   List<String> cmds = []
   Integer ledNum = Integer.parseInt(ledNumber)
   Short colorNum = statusColorNameMap[colorName]
   Boolean blinkOn = blink == "on" || blink == "ON" || blink == "On" || blink == "oN"
   Short blinkBit = blinkOn ? 2**(ledNum-1) : 0b11111111 - 2**(ledNum-1)
   Short newBlinkVal
   if (ledNum < 1 || ledNum > 7) {
      log.error "Invalid LED number specified: $ledNum"
      return []
   }
   else if (colorNum < 0 || colorNum > 7) {
      log.error "Invalid color number specified: $colorNum"
      return []
   }
   if (blinkOn) {
      newBlinkVal = (state.blinkval ?: 0) | blinkBit
   }
   else {
      newBlinkVal = (state.blinkval ?: 0) & blinkBit
   }

   String cmd = setConfigParameter(ledNum + 20, colorNum, 1)
   cmds.add(cmd)
   if (newBlinkVal != state.blinkval) {
      String cmd2 = setConfigParameter(31, newBlinkVal,1)
      cmds.add(cmd2)
      state.blinkval = newBlinkVal
   }
   state."statusled${ledNum}" = colorNum
   // Device manual makes it sound like this happens automatically, but maybe that's HomeSeer behavior?
   // Set to status mode if at least one LED set to status/color, otherwise set to normal
   if (getStatusLEDStates().every { Integer i -> i == 0 }) {
      cmds.add(setLEDMode("normal"))
   }
   else {
      cmds.add(setLEDMode("status"))
   }
   return delayBetween(cmds, 750)
}

String setLEDMode(String mode) {
   if (enableDebug) log.debug "setLEDMode($mode)"
   Byte val = (mode.toLowerCase() == "status") ? 1 : 0
   return setConfigParameter(13, val, 1)
}

String setConfigParameter(Integer number, BigInteger value, Integer size) {
   if (enableDebug) log.debug "setConfigParameter(number: $number, value: $value, size: $size)"
   hubitat.zwave.Command cmd = zwave.configurationV1.configurationSet(parameterNumber: number as Short, scaledConfigurationValue: value as BigInteger, size: size as Short)
   //return zwaveSecureEncap(cmd)
   return zwaveSecureEncap(supervisedEncap(cmd))
}