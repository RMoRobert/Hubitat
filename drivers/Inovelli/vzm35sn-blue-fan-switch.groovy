/*
 * =========== Inovelli Blue Series Fan Switch (VZM35-SN) Driver ===============
 *
 *  Copyright 2025 Robert Morris
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
 *  v1.0.1  (2025-01-24) - Initial release, based on VZM31-SN driver
 * 
 */

 /*
TO-DO:
[ ] see if VZM31 new ep3 binding applies here too and add to both?
[ ] ?
*/

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import com.hubitat.zigbee.DataType

@Field static final List supportedFanSpeeds = ["low", "medium", "high", "off"]

@Field static final Integer INOVELLI_CLUSTER = 0xFC31
@Field static final Integer INOVELLI_MFG_CODE = 0x122F

@Field static final BigDecimal defaultLightTransitionTimeS = 0.400;

@Field static final Integer disableDebugLogging = 30 // minutes before auto-disabling debug logging

@Field static final Map<String,Integer> colorNameMap = [
   "red": 0,
   "red-orange": 2,
   "orange": 8,
   "yellow": 30,
   "chartreuse": 60,
   "green": 86,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "blue": 170,
   "violet": 208,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

@Field static final Map<String,Short> effectNameAllMap = ["off": 0, "solid": 1, "chase": 5, "fast blink": 2, "slow blink": 3, "pulse": 4, "open/close": 6,
                                                       "small to big": 7, "aurora": 8, "slow falling": 9, "medium falling": 10, "fast falling": 11, "slow rising": 12, "medium rising": 13, "fast rising": 14, "medium blink": 15,
                                                       "slow chase": 16, "fast chase": 17, "fast siren": 18, "slow siren": 19, "clear": 255]

@Field static final Map<String,Short> effectNameOneMap = ["off": 0, "solid": 1, "chase": 5, "fast blink": 2, "slow blink": 3, "pulse": 4, "clear": 255]

//startLevelChangeRate options
@Field static final Map startLevelChangeRateOptions = [
        defaultValue: 64,
        defaultText: "Fast",
        options:[10:"Slow", 40:"Medium", 64:"Fast"]
]

// "Parameters" (manufacturer-specific cluster attributes):
@Field static final Map mscAttributes = [
   1:  [
          desc: "Fan \"dimming\" speed up (remote)", type: "enum", dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s  [DEFAULT]"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[126:"12.6 s"]]
       ],
   2:  [
          desc: "Fan \"dimming\" speed up (local)", type: "enum", dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match dimming speed up (remote) [DEFAULT]"]]
       ],
   3:  [
          desc: "Ramp rate - off to on (remote) ", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match dimming speed up (remote) [DEFAULT]"]]
       ],
   4:  [
          desc: "Ramp rate - off to on (local)", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match ramp rate  off to on (remote)  [DEFAULT]"]]
       ],
   5:  [
          desc: "Fan \"dimming\" speed down (remote)", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match dimming speed up (remote) [DEFAULT]"]]
       ],
   6:  [
          desc: "Fan \"dimming\" speed down (local)", type: "enum", dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match dimming speed up (local) (remote)  [DEFAULT]"]]
       ],
   7:  [
          desc: "Ramp rate - on to off (remote)", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match ramp rate - off to on (remote)  [DEFAULT]"]]
       ],
   8:  [
          desc: "Ramp rate - on to off (local)", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match ramp rate - off to on (local)  [DEFAULT]"]]
       ],
   9:  [
          desc: "Minimum fan speed/level (1-254, to be scaled to 1-100)", range: "1..254", type: "number", default: 1, dataType: DataType.UINT8
       ],
   10: [
          desc: "Maximum fan speed/level (2-255, to be scaled to 1-100)", range: "2..255", type: "number", default: 255, dataType: DataType.UINT8
       ],
   11:  [
          desc: "Invert switch", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"No [DEFAULT]"], [1:"Yes (tap down turns on, up turns off)"]]
         ],
   12: [
          desc: "Auto-off timer (seconds, 1-32767; 0=disabled)", range: "0..32767", type: "number", default: 0, dataType: DataType.UINT16
       ],
   13:  [
          desc: "Default fan speed/level (local)", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[1: "1%"],[5:"2%"], [13:"5%"], [25:"10%"],[38:"15%"],[50:"20%"],[64:"25%"],[76:"30%"],[102:"40%"],[127:"50%"],
           [152:"60%"],[178:"70%"],[191:"75%"],[203:"80%"],[216:"85%"],[229:"90%"],[241:"95%"],[254:"100%"],
           [255:"Previous level  [DEFAULT]"]]
       ],
   14:  [
          desc: "Default fan speed/level (remote)", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[1: "1%"],[5:"2%"], [13:"5%"], [25:"10%"],[38:"15%"],[50:"20%"],[64:"25%"],[76:"30%"],[102:"40%"],[127:"50%"],
           [152:"60%"],[178:"70%"],[191:"75%"],[203:"80%"],[216:"85%"],[229:"90%"],[241:"95%"],[254:"100%"],
           [255:"Previous level  [DEFAULT]"]]
       ],
   15:  [
          desc: "Fan speed after power restored", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[0: "Off"],[1: "1%"],[5:"2%"], [13:"5%"], [25:"10%"],[38:"15%"],[50:"20%"],[64:"25%"],[76:"30%"],[102:"40%"],[127:"50%"],
           [152:"60%"],[178:"70%"],[191:"75%"],[203:"80%"],[216:"85%"],[229:"90%"],[241:"95%"],[254:"100%"],
           [255:"Previous state  [DEFAULT]"]]
       ],
   17:  [
          desc: "LED indicator timeout", type: "enum", default: 11, dataType: DataType.UINT8, options:
          [[0: "Always off"],[1: "1 s"],[2:"2 s"], [3:"3 s"], [4:"4 s"],[5:"5 s"],[6:"6 s"],[7:"7 s"],[8:"8 s"],
           [9:"9 s"],[10:"10 s"],[11:"Always on  [DEFAULT]"]]
      ],
   22:  [
          desc: "Switch type", type: "enum", default: 0, dataType: DataType.UINT8, options:
          [[0:"Single-pole [DEFAULT]"], [1:"Multi-way with dumb switch"],[2:"Multi-way with aux switch"],
           [3:"Full sine wave (firmwre 2.15+ only)"]]
         ],
   23: [
          desc: "Quick start duration (higher power output for off-to-on transition in 60ths of a second, e.g., 15 =  0.25 sec.; 0 = disabled)", range: "0..60", type: "number", default: 0, dataType: DataType.UINT8
       ],
   // Not in docs or Z2M converter, leaving for now in case is actually used on fan too:
   // 24: [
   //        desc: "Quick start power level (1-254, scaled to 1-100%)", range: "1..254", type: "number", default: 254, dataType: DataType.UINT8
   //     ],
   50:  [
          desc: "Button press delay", type: "enum", default: 5, dataType: DataType.UINT8, options:
          [[0: "None (disables multi-taps)"],[3:"300 ms"],[4:"400 ms"],[5:"500 ms [DEFAULT"],
          [6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"]]
       ],
   52:  [
          desc: "Smart fan mode", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"Regular fan [DEFAULT]"], [1:"Smart fan"]]
       ],
   53:  [
          desc: "Enable double-tap up to double-tap up level", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"Disabled [DEFAULT]"], [1:"Enabled"]],
          minimumFirmwareVersion: 0x0102020C
       ],
   54:  [
          desc: "Enable double-tap down to double-tap down level", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"Disabled [DEFAULT]"], [1:"Enabled"]],
          minimumFirmwareVersion: 0x0102020C
       ],
   55: [
          desc: "Double-tap up percentage if enabled (2-254, scaled to 1-100%)", range: "2..254", type: "number",
          default: 254, dataType: DataType.UINT8
       ],
   56: [
          desc: "Double-tap down percentage if enabled (1-254, scaled to 1-100%, or 0 to turn off)", range: "0..254", type: "number",
          default: 2, dataType: DataType.UINT8
       ],
   // 100:  [
   //        desc: "Match Red Gen 2 (LZW model numbers) LED bar scaling", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
   //        [[0:"No (VZW/VZM-style) [DEFAULT]"], [1:"Yes (LZW-style)"]],
   //        minimumFirmwareVersion: 0x0102020E
   //       ],
   // 120:  [
   //        desc: "Single-tap behavior", type: "enum", default: 0, dataType: DataType.UINT8, options:
   //        [[0: "Traditional (up on, down off)"],[1:"Cycle preset single-tap levels"],[2:"Cylce preset levels on up, down always off"]],
   //        minimumFirmwareVersion: 0x01020211
   //     ],
   //     // TODO: Add reset of fan/ep3 parameters to complete this
   121:  [
          desc: "Advanced timer mode: act like bathroom fan timer with multi-taps and LED bar", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0: "Disabled [DEFAULT]"],[1: "Enabled"]],
          minimumFirmwareVersion: 0x02020105
       ],
   261:  [
          desc: "Disable relay click (see manual for more)", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"No [DEFAULT]"], [1:"Yes (disable click)"]]
         ],
   256:  [
          desc: "Local protection", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0: "Disabled [DEFAULT]"],[1: "Enabled (physical control disabled)"]]
       ],
   257:  [
          desc: "Remote protection", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0: "Disabled [DEFAULT]"],[1: "Enabled (no commands accepted from hub)"]]
       ],
   258:  [
          desc: "Fan (speed/level) mode", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0: "3-speed control (ceiling fan)"],[1: "On/off only (exhaust fan) [DEFAULT]"]]
       ],
]

metadata {
   definition (name: "Inovelli VZM35-SN Blue Series Fan Switch", namespace: "RMoRobert", author: "Robert Morris") {
      capability "Actuator"
      capability "Configuration"
      capability "FanControl"
      capability "Refresh"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"


      command "setIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 1, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse", "open/close",
                                                       "small to big", "aurora", "slow falling", "medium falling", "fast falling", "slow rising", "medium rising", "fast rising", "medium blink",
                                                       "slow chase", "fast chase", "fast siren", "slow siren", "clear"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]

      command "setLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "NUMBER", description: "Level, 0-100", range:0..100],
                              [name: "OnOrOff", type: "ENUM", constraints: ["on","off","both"], description: "Apply to LED when on, off, or both (deafult: both)"]]
      
      command "setOnLEDLevel", [[name:"Level*", type: "NUMBER", description: "Brightess (0-100; 0=off)", range: 0..100]]

      command "setOffLEDLevel", [[name:"Level*", type: "NUMBER", description: "Brightess (0-100; 0=off)", range: 0..100]]

      command "updateFirmware"


      fingerprint profileId:"0104", endpointId:"02", inClusters:"0000,0003", outClusters:"0003,0006,0008,FC31", model:"VZM31-SN", manufacturer:"Inovelli"
   }

   preferences {
      getMscAttributesForFwVersion().each { Map.Entry attrDetails ->
         input(getInputParamsForMscPreference(attrDetails))
      }
      input name: "showAttrNumber", type: "bool", title: "Show attribute (\"parameter\") numbers for device-specific preferences in UI"
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

Map getMscAttributesForFwVersion() {
   String softwareBuild = getDataValue("softwareBuild")
   Integer minBuild = 0
   Integer currBuild
   try {
      currBuild = Integer.parseInt(softwareBuild, 16)
   }
   catch (Exception ex) {
      if (logEnable) log.warn "Error parsing softwareBuild data/firmware version: $ex"
   }
   return mscAttributes.findAll { Integer key, Map value ->
      value.minimumFirmwareVersion == null || currBuild >= value.minimumFirmwareVersion
   }
}

Map getInputParamsForMscPreference(Map.Entry attrDetails) {
   String title
   if (showAttrNumber) {
      title = "[${attrDetails.key}] " + attrDetails.value.desc
   }
   else {
      title = title = attrDetails.value.desc
   }
   if (attrDetails.value.default && attrDetails.value.type != "enum") title += " [DEFAULT: ${attrDetails.value.default}]"
   if (attrDetails.value.type == "number") {
      return [name: getSettingNameForMscPreference(attrDetails), type: "number", range: attrDetails.value.range, title: title]
   }
   else if (attrDetails.value.type == "enum") {
      return [name: getSettingNameForMscPreference(attrDetails), type: "enum", options: attrDetails.value.options, title: title]
   }
   else {
      log.warn "Unexpected input type ${attrDetails.value.type} for number ${attrDetails.key}"
   }
}

String getSettingNameForMscPreference(attrDetails) {
   return "attr_${attrDetails.key}"
}

void installed() {
   log.debug "installed()"
}

void updated() {
   log.debug "updated()"
   log.warn "debug logging is: ${logEnable == true}"
   log.warn "description logging is: ${txtEnable == true}"
   if (logEnable) runIn(1800, "logsOff")
   configure()
}

void configure() {
   if (logEnable) log.debug "configure()"
   sendEvent(name: "supportedFanSpeeds", value: new groovy.json.JsonBuilder(supportedFanSpeeds).toString())
   sendEvent(name: "numberOfButtons", value: 15)
   
   List<String> cmds = []
   // Preferences:
   getMscAttributesForFwVersion().each { Map.Entry attrDetails ->
      def settingVal = settings[getSettingNameForMscPreference(attrDetails)]
      if (settingVal != null) {
         // OK to do here since all this device's parameters are numeric:
         Integer intVal = settingVal instanceof Number ? settingVal : Integer.parseInt(settingVal)
         List<String> cmd = zigbee.writeAttribute(INOVELLI_CLUSTER, attrDetails.key, attrDetails.value.dataType,
                               intVal, [destEndpoint: 1, mfgCode: INOVELLI_MFG_CODE], 0)
         if (logEnable) log.debug "Setting MSC attribute ${attrDetails.key} to ${intVal} for ep1"
         cmds += cmd
      }
   }
   // Bindings:
   //cmds.add "zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}"
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}"  // on/off
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}"  // level
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 $INOVELLI_CLUSTER {${device.zigbeeId}} {}"  // manufacturer-specific
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 $INOVELLI_CLUSTER {${device.zigbeeId}} {}"  // manufacturer-specific ep2 = buttons evts

   // Defaults seem OK?
   // cmds += zigbee.onOffConfig()
   // cmds += zigbee.levelConfig

   sendToDevice(cmds)
}

void parse(String description) {
   if (logEnable) log.debug "<b>parse</b> description: ${description}"
   Map descMap = zigbee.parseDescriptionAsMap(description)
   if (logEnable) log.debug "parsed map: $descMap"
   switch (descMap.clusterInt) {
      case 0x0000: // Basic
         switch (descMap.attrInt) {
            case 0x0006:  // SW Date
               // Putting these in state since matches Inovelli driver:
               state.fwDate = descMap.value
               break
            case 0x4000: // SW Build ID
               state.fwVersion = descMap.value
               break
         }
      case 0x0006: // On/Off
         if (descMap.attrInt == 0) {
            Integer rawValue = zigbee.convertHexToInt(descMap.value)
            String switchValue = (rawValue == 0) ? "off" : "on"
            if (txtEnable) log.info "${device.displayName} switch is ${switchValue}"
            sendEvent(name: "switch", value: switchValue, descriptionText: "${device.displayName} switch is ${switchValue}")
         }
         else {
            if (logEnable) log.debug "skipping 0x0006:${descMap.attrId}"
         }
         break
      case 0x0008:  // Level
         if (descMap.attrInt == 0) {
            Integer rawValue = Integer.parseInt(descMap.value, 16)
            Integer levelValue = Math.round(rawValue/2.55)
            if (logEnable) log.info "${device.displayName} raw level is ${levelValue}"
            if (levelValue <= 0) newSpeed = "off"
            else if (levelValue < 21) newSpeed = "low"
            else if (levelValue < 61) newSpeed = "medium"
            else if (levelValue <= 100) newSpeed = "high"
            String descText = "${device.displayName} speed is ${newSpeed}"
            if (logEnable) log.info descText
            sendEvent(name: "speed", value: newSpeed, descriptionText: descText)
         }
         else {
            if (logEnable) log.debug "skipping 0x0008:${descMap.attrId}"
         }
         break
      case INOVELLI_CLUSTER:
         if (descMap.attrInt == null && descMap.isClusterSpecific) {
            if (descMap.command == "00") {
               parseButtonEvent(descMap.data)
            }
         }
         break
      case 0x8021:  // Bind response
         if (logEnable) log.debug "Bind Response (0x8021): $descMap"
         break
      case 0x8022: // Unbind response
         if (logEnable) log.debug "Unbind Response (0x8022): $descMap"
         break
      default:
         if (logEnable) log.debug "ignoring ${descMap.clusterId}:${$descMap?.attrId}"
         break
   }
}

void refresh() {
   if (logEnable) log.debug "refresh()"
   List<String> cmds = []
   List<Integer> epIds = [1, 2]
   cmds += zigbee.readAttribute(0x0006, 0x0000, null, 200) // On/off
   cmds += zigbee.readAttribute(0x0008, 0x0000, null, 200) // Level
   cmds += zigbee.readAttribute(0x0B04, 0x0501, null, 200) // Electrical Measurement - Amps
   cmds += zigbee.readAttribute(0x0B04, 0x050B, null, 200) // Electrical Measurement - Watts
   cmds += zigbee.readAttribute(0x0702, 0x0000, null, 200) // Simple Metering
   cmds += zigbee.readAttribute(0x0000, 0x0006, null, 200)  // Basic - SW Date
   cmds += zigbee.readAttribute(0x0000, 0x4000, null, 200)  // Basic - SW Version
   // TODO: private clusters for "parameters"?
   sendToDevice(cmds)
}

List<String> updateFirmware() {
    if (logEnable) log.debug "updateFirmware()"
    return zigbee.updateFirmware()
}

void on() {
   if (logEnable) log.debug "on()"
   sendToDevice "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}"
}

void off() {
   if (logEnable) log.debug "off()"
   sendToDevice "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
}

void setSpeed(String speed) {
   if (logEnable) log.debug "setSpeed($speed)"
   switch (speed) {
      case "off":
         off()
         break
      case "on":
         on()
         break
      case "low":
         scaledetLevel(20)
         break
      case "medium-low":
      case "medium":
      case "medium-high":
         setLevel(60)
         break
      case "high":
         setLevel(100)
         break
      default:
         log.warn "Unexpected fan speed; ignoring. Speed: $speed"
   }
}

void cycleSpeed() {
   if (logEnable) log.debug "cycleSpeed()"
   String currentSpeed = device.currentValue("speed") ?: "off"
   switch (currentSpeed) {
      case "off":
         setLevel(20)
      break
      case "low":
         setLevel(60)
      break
      case "medium-low":
      case "medium":
      case "medium-high":
         setLevel(100)
      break
      case "high":
         off()
      break
   }
}

void setLevel(level, Number transitionTime=null) {
   if (logEnable) log.debug "setLevel($level, $transitionTime)"
   Integer intLevel = Math.round(level.toDouble() * 2.55)
   String zigbeeLevel = "0x${intTo8bitUnsignedHex(intLevel)}"
   Integer scaledRate = ((transitionTime == null ? defaultLightTransitionTimeS : transitionTime) * 10).toInteger()
   String zigbeeRate = DataType.pack(scaledRate, DataType.UINT16, true)
   String cmd = "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 4 {$zigbeeLevel $zigbeeRate}"
   sendToDevice(cmd)
}

void startLevelChange(String direction) {
   if (logEnable) log.debug "startLevelChange($direction)"
   Integer upDown = (direction == "down") ? 1 : 0
   Integer unitsPerSecond = 100
   String cmd = "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 1 { 0x${intTo8bitUnsignedHex(upDown)} 0x${DataType.pack(unitsPerSecond, DataType.UINT16, true)} }"
   sendToDevice(cmd)
}

void stopLevelChange() {
   if (logEnable) log.debug "stopLevelChange()"
   String cmd = "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 3 {}}"
   sendToDevice(cmd)
}

void setIndicator(String color, Object level, String effect, Number duration=255) {
   if (logEnable) log.debug "setIndicator($color, $level, $effect, $duration)"
   Integer intColor = colorNameMap[color?.toLowerCase()]
   if (intColor == null) intColor = 170
   Integer intEffect = (effectNameAllMap[effect?.toLowerCase()] != null) ? effectNameAllMap[effect.toLowerCase()] : 4
   Integer intLevel = level.toInteger()
   intLevel = Math.min(Math.max((level != null ? level : 100).toInteger(), 0), 100)
   Integer intDuration = duration.toInteger()
   intDuration = Math.min(Math.max((duration!=null ? duration : 255).toInteger(), 0), 255) 
   List<String> cmds = zigbee.command(INOVELLI_CLUSTER, 0x01, ["mfgCode": INOVELLI_MFG_CODE], 200,
      "${intTo8bitUnsignedHex(intEffect)} ${intTo8bitUnsignedHex(intColor)} ${intTo8bitUnsignedHex(intLevel)} ${intTo8bitUnsignedHex(intDuration)}")
   sendToDevice(cmds)
}


// Sets default on and/or off LED color parameter to value (0-255) and level (0-100)
void setLEDColor(Number intValue, level=null, String onOrOffOrBoth="both") {
   if (logEnable) log.debug "setLEDColor(Number $intValue, Object $level, onOrOffOrBoth=$onOrOffOrBoth)"
   Integer intColor = intValue.toInteger()
   List<String> cmds = []
   if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 0x005F, 0x20, intColor, ["mfgCode": INOVELLI_MFG_CODE], 250) // for on
   if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 0x0060, 0x20, intColor, ["mfgCode": INOVELLI_MFG_CODE], 250) // for off
   if (level != null) {
      Integer intLevel = level.toInteger()
      if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 0x0061, 0x20, intLevel, ["mfgCode": INOVELLI_MFG_CODE], 250)
      if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 0x0062, 0x20, intLevel, ["mfgCode": INOVELLI_MFG_CODE], 250)
   }
   if (logEnable) "setLEDColor cmds = $cmds"
   sendToDevice(cmds)
}

// Sets default on and/or off LED color parameter to named color (from map) and level (Hubitat 0-100 style)
void setLEDColor(String color, level=null, String onOrOffOrBoth="both") {
   if (logEnable) log.debug "setLEDColor(String $color, Object $level, onOrOffOrBoth=$onOrOffOrBoth)"
   Integer intColor = colorNameMap[color?.toLowerCase()]
   if (intColor == null) intColor = 170
   setLEDColor(intColor, level, onOrOffOrBoth)
}

void setOnLEDLevel(Number level) {
   if (logEnable) log.debug "setOnLEDLevel($level)"
   Integer intLevel = level.toInteger()
   List<String> cmds = []
   cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 97, DataType.UINT8, intLevel, ["mfgCode": INOVELLI_MFG_CODE], 250)
   sendToDevice(cmds)
}

void setOffLEDLevel(Number level) {
   if (logEnable) log.debug "setOffLEDLevel($level)"
   Integer intLevel = level.toInteger()
   List<String> cmds = []
   cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, 98, DataType.UINT8, intLevel, ["mfgCode": INOVELLI_MFG_CODE], 250)
   sendToDevice(cmds)
}

void push(btnNumber) {
   Integer num = btnNumber.toInteger()
   if (txtEnable) log.info "${device.displayName} button $num is pushed"
   sendEvent(name: "pushed", value: num, descriptionText: "${device.displayName} button $num is pushed", type: "digital", isStateChange: true)
}

void hold(btnNumber) {
   Integer num = btnNumber.toInteger()
   if (txtEnable) log.info "${device.displayName} button $num is held"
   sendEvent(name: "held", value: num, descriptionText: "${device.displayName} button $num is held", type: "digital", isStateChange: true)
}

void release(btnNumber) {
   Integer num = btnNumber.toInteger()
   if (txtEnable) log.info "${device.displayName} button $num is released"
   sendEvent(name: "released", value: num, descriptionText: "${device.displayName} button $num is released", type: "digital", isStateChange: true)
}

/*****************
** SUB-METHODS
******************/

void parseButtonEvent(data) {
   if (logEnable) log.debug "parsebuttonEvent($data)"
   Integer rawBtnNumber = zigbee.convertHexToInt(data[0])
   Integer rawBtnAttrs = zigbee.convertHexToInt(data[1])
   //Integer baseBtnNum = rawBtnNumber == 3 ? rawBtnNumber : (rawBtnNumber == 2 ? 1 : 2)
   Integer btnNumber
   String btnEvtName
   if (rawBtnAttrs == 2) {
      btnEvtName = "held"
   }
   else if (rawBtnAttrs == 1) {
      btnEvtName = "released"
   }
   else {
      btnEvtName = "pushed"
   }
   if (rawBtnNumber != 3) { // not config btn
      btnNumber = Math.max(rawBtnAttrs - 1, 1) * 2
      if (rawBtnNumber == 2) btnNumber -= 1 // if up
   }
   else {
      btnNumber = 10 + Math.max(rawBtnAttrs - 1, 1)
   }
   if (txtEnable) log.info "${device.displayName} button $btnNumber is ${btnEvtName} (physical)"
   sendEvent(name: btnEvtName, value: btnNumber, type: "phyiscal",
      descriptionText: "${device.displayName} button $btnNumber is $btnEvtName (physical)",
      isStateChange: true)
}

/******************
** OTHER METHODS **
*******************/

void sendToDevice(List<String> cmds, Long delay=250) {
   //log.trace cmds
   sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, delay), hubitat.device.Protocol.ZIGBEE))
}

void sendToDevice(String cmd) {
   //log.trace cmd
   sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE))
}

void logsOff() {
   log.warn "debug logging disabled..."
   device.updateSetting("logEnable", [value:"false", type:"bool"])
}

String intTo8bitUnsignedHex(value) {
   return zigbee.convertToHexString(value.toInteger(), 2)
}