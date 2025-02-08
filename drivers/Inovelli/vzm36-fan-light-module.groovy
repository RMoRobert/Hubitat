/*
 * ================ Inovelli Blue Series Fan/Light Canopy (VZM36) Driver ==================
 *
 *  Copyright 2024-2025 Robert Morris
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
 *  v1.0.6  (2022-02-07) - Fix for attribute 258 (dimmer vs. on/off-only)
 *  v1.0.5  (2025-02-06) - Additional fix for configure() Long type error
 *  v1.0.3  (2025-01-28) - Slower default startLevelChange duration
 *  v1.0.2  (2024-05-27) - Improve initial child device creation
 *  v1.0.1  (2024-04-05) - Add updateFirmware command, importUrl, other minor changes
 *  v1.0    (2024-04-03) - Initial release
 * 
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper
import com.hubitat.zigbee.DataType

@Field static final List supportedFanSpeeds = ["low", "medium", "high", "off"]
@Field static final Integer INOVELLI_CLUSTER = 0xFC31
@Field static final Integer INOVELLI_MFG_CODE = 0x122F

@Field static final String LIGHT_EP = "01"
@Field static final String FAN_EP = "02"

@Field static final BigDecimal defaultLightTransitionTimeS = 0.400;

// "Parameters" (manufacturer-specific cluster attributes) for light/ep1:
@Field static final Map mscAttributesEp1 = [
   1:  [
          desc: "Light: dimming speed up", type: "enum", dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s  [DEFAULT]"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"12.7 s"]]
       ],
   3:  [
          desc: "Light: ramp rate - off to on", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   5:  [
          desc: "Light: dimming speed down", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   7:  [
          desc: "Light: ramp rate - on to off", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   9:  [
          desc: "Light: minimum dim level (1-254, to be scaled to 1-100)", range: "1..254", type: "number", default: 1, dataType: DataType.UINT8
       ],
   10: [
          desc: "Light: maximum dim level (2-255, to be scaled to 1-100)", range: "2..255", type: "number", default: 255, dataType: DataType.UINT8
       ],
   12: [
          desc: "Light: auto-off timer (seconds, 1-32767; 0=disabled)", range: "0..32767", type: "number", default: 0, dataType: DataType.UINT16
       ],
   14:  [
          desc: "Light: default dim level when turned on", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[1: "1%"],[5:"2%"], [13:"5%"], [25:"10%"],[38:"15%"],[50:"20%"],[64:"25%"],[76:"30%"],[102:"40%"],[127:"50%"],
           [152:"60%"],[178:"70%"],[191:"75%"],[203:"80%"],[216:"85%"],[229:"90%"],[241:"95%"],[254:"100%"],
           [255:"Previous level [DEFAULT]"]]
       ],
   15:  [
          desc: "Light: state after power restored", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[0: "Off"],[1: "1%"],[5:"2%"], [13:"5%"], [25:"10%"],[38:"15%"],[50:"20%"],[64:"25%"],[76:"30%"],[102:"40%"],[127:"50%"],
           [152:"60%"],[178:"70%"],[191:"75%"],[203:"80%"],[216:"85%"],[229:"90%"],[241:"95%"],[254:"100%"],
           [255:"Previous state [DEFAULT]"]]
       ],
   23: [
          desc: "Light: quick start duration (higher power output for off-to-on transition in 60ths of a second, e.g., 15 =  0.25 sec.; 0 = disabled)", range: "0..60", type: "number", default: 0, dataType: DataType.UINT8
       ],
   24: [
          desc: "Light: quick start power level (1-254, scaled to 1-100%)", range: "1..254", type: "number", default: 254, dataType: DataType.UINT8
       ],
   25:  [
          desc: "Light: higher output in non-neutral", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
          [[0:"No [DEFAULT]"], [1:"Yes"]]
         ],
   // is this supported on VZM36? In manual, but Zigbee Herdsman converter lacks:
   // 26:  [
   //        desc: "Light: leading/trailing edge (see manual for supported trailing-edge configurations)", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
   //        [[0:"Leading edge [DEFAULT]"], [1:"Trailing edge (no-neutral or dumb 3-way not supported)"]]
   //       ],
   52:  [
          desc: "Light: smart bulb mode", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0:"Regular bulbs [DEFAULT]"], [1:"Smart bulbs"]]
       ],
   // 257:  [
   //        desc: "Light: remote protection", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
   //        [[0: "Disabled [DEFAULT]"],[1: "Enabled (no commands accepted from hub)"]]
   //     ],
   258:  [
          desc: "Light: switch mode", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0: "Dimmer"],[1: "On/Off Only [DEFAULT]"]]
       ],
]

// "Parameters" (manufacturer-specific cluster attributes) for fan/ep2:
@Field static final Map mscAttributesEp2 = [
   1:  [
          desc: "Fan: speed change rate up", type: "enum", dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s  [DEFAULT]"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"12.7 s"]]
       ],
   3:  [
          desc: "Fan: ramp rate - off to on", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   5:  [
          desc: "Fan: speed change rate down", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   7:  [
          desc: "Fan: ramp rate - on to off", type: "enum", default: 127, dataType: DataType.UINT8, options:
          [[0: "ASAP"],[1:"100 ms"], [2:"200 ms"], [3:"300 ms"],[4:"400 ms"],
           [5:"500 ms"],[6:"600 ms"],[7:"700 ms"],[8:"800 ms"],[9:"900 ms"],[10:"1.0 s"],[12:"1.2 s"],
           [15:"1.5 s"],[20:"2.0 s"],[25:"2.5 s"],[30:"3.0 s"],[35:"3.5 s"],[40:"4.0 s"],[50:"5.0 s"],
           [60:"6.0 s"],[75:"7.5 s"],[100:"10 s"],[127:"Match light dimming speed up [DEFAULT]"]]
       ],
   9:  [
          desc: "Fan: minimum speed level (1-254, to be scaled to 1-100)", range: "1..254", type: "number", default: 1, dataType: DataType.UINT8
       ],
   10: [
          desc: "Fan: maximum speed level (2-255, to be scaled to 1-100)", range: "2..255", type: "number", default: 255, dataType: DataType.UINT8
       ],
   12: [
          desc: "Fan: auto-off timer (seconds, 1-32767; 0=disabled)", range: "0..32767", type: "number", default: 0, dataType: DataType.UINT16
       ],
   14:  [
          desc: "Fan: default speed when turned on", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[1: "1%"],[25:"10%"],[50:"20%"],[76:"30%"],[102:"40%"],[127:"50%"],[152:"60%"],[178:"70%"],
          [203:"80%"],[229:"90%"],[254:"100%"],[255:"Previous speed [DEFAULT]"]]
       ],
   15:  [
          desc: "Fan: state after power restored", type: "enum", default: 255, dataType: DataType.UINT8, options:
          [[0: "Off"],[1: "1%"],[25:"10%"],[50:"20%"],[76:"30%"],[102:"40%"],[127:"50%"],[152:"60%"],
          [178:"70%"],[203:"80%"],[229:"90%"],[254:"100%"],[255:"Previous state [DEFAULT]"]]
       ],
   23: [
          desc: "Fan: quick start duration (full power output for off-to-on transition in 60ths of a second, e.g., 30 = 0.5 sec.; 0 = disabled)", range: "0..60", type: "number", default: 0, dataType: DataType.UINT8
       ],
   52:  [
          desc: "Fan: remote control/smart fan mode", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0:"Regular control [DEFAULT]"],[1:"Smart fan/remote-control fan"]]
       ],
   // 257:  [
   //        desc: "Fan: remote protection", type: "enum", default: 0, dataType: DataType.BOOLEAN, options:
   //        [[0: "Disabled [DEFAULT]"],[1: "Enabled (no commands accepted from hub)"]]
   //     ],
   258:  [
          desc: "Fan: switch mode", type: "enum", default: 1, dataType: DataType.BOOLEAN, options:
          [[0: "Speed and on/off control"],[1: "On/off only [DEFAULT]"]]
       ],
]

metadata {
   definition (name: "Inovelli VZM36 Fan/Light Canopy Module", namespace: "RMoRobert", author: "Robert Morris",
               importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/vzm36-fan-light-module.groovy") {
      capability "Actuator"
      capability "Configuration"
      capability "Refresh"

      command "updateFirmware"

      fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0008,0B05,1000,FC31,FC57", outClusters:"0019", model:"VZM36", manufacturer:"Inovelli" 
   }

   preferences {
      mscAttributesEp1.each { Map.Entry attrDetails ->
         input(getInputParamsForMscPreference(attrDetails, 1))
      }
      mscAttributesEp2.each { Map.Entry attrDetails ->
         input(getInputParamsForMscPreference(attrDetails, 2))
      }
      input name: "showAttrNumber", type: "bool", title: "Show attribute (\"parameter\") numbers for device-specific preferences in UI"
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

Map getInputParamsForMscPreference(Map.Entry attrDetails, Integer endpoint) {
   String title
   if (showAttrNumber) {
      title = "[${attrDetails.key}] " + attrDetails.value.desc
   }
   else {
      title = title = attrDetails.value.desc
   }
   if (attrDetails.value.default && attrDetails.value.type != "enum") title += " [DEFAULT: ${attrDetails.value.default}]"
   if (attrDetails.value.type == "number") {
      return [name: getSettingNameForMscPreference(attrDetails, endpoint), type: "number", range: attrDetails.value.range, title: title]
   }
   else if (attrDetails.value.type == "enum") {
      return [name: getSettingNameForMscPreference(attrDetails, endpoint), type: "enum", options: attrDetails.value.options, title: title]
   }
   else {
      log.warn "Unexpected input type ${attrDetails.value.type} for number ${attrDetails.key}"
   }
}

String getSettingNameForMscPreference(Map.Entry attrDetails, Integer endpoint) {
   return "attr_${attrDetails.key}_ep${String.format('%02d',endpoint)}"
}

void installed() {
   log.debug "installed()"
   runIn(10, "createChildDevicesIfNeeded")
}

void updated() {
   log.debug "updated()"
   log.warn "debug logging is: ${logEnable == true}"
   log.warn "description logging is: ${txtEnable == true}"
   if (logEnable) runIn(1800, "logsOff")
   createChildDevicesIfNeeded()
   configure()
}

void configure() {
   if (logEnable) log.debug "configure()"
   List<String> cmds = []
   // Preferences:
   mscAttributesEp1.each { attrDetails ->
      def settingVal = settings[getSettingNameForMscPreference(attrDetails, 1)]
      if (settingVal != null) {
         // log.trace "setting name = ${getSettingNameForMscPreference(attrDetails, 1)}; setting value = $settingVal"
         // All the parameters are numeric, but some may be saved as string, so:
         Integer intVal = Integer.parseInt("$settingVal")
         List<String> cmd = zigbee.writeAttribute(INOVELLI_CLUSTER, attrDetails.key, attrDetails.value.dataType,
                               intVal, [destEndpoint: 1, mfgCode: INOVELLI_MFG_CODE], 0)
         if (logEnable) log.debug "Setting MSC attribute ${attrDetails.key} to ${intVal} for ep1"
         cmds += cmd
      }
   }
   mscAttributesEp2.each { attrDetails ->
      def settingVal = settings[getSettingNameForMscPreference(attrDetails, 2)]
      if (settingVal != null) {
         // log.trace "setting name = ${getSettingNameForMscPreference(attrDetails, 2)}; setting value = $settingVal"
         Integer intVal = Integer.parseInt("$settingVal")
         cmds += zigbee.writeAttribute(INOVELLI_CLUSTER, attrDetails.key, attrDetails.value.dataType,
                                      intVal, [destEndpoint: 2, mfgCode: INOVELLI_MFG_CODE], 0)
         if (logEnable) log.debug "Setting MSC attribute ${attrDetails.key} to ${intVal} for ep2"
      }
   }

   // Bindings:
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x01 0x0006 {${device.zigbeeId}} {}"  // on/off
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${FAN_EP} 0x01 0x0006 {${device.zigbeeId}} {}"  // on/off
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x01 0x0008 {${device.zigbeeId}} {}"  // level
   cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${FAN_EP} 0x01 0x0008 {${device.zigbeeId}} {}"  // level
   // Not currently used in driver, so no need:
   // cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x01 $INOVELLI_CLUSTER {${device.zigbeeId}} {}"  // manufacturer-specific for light/ep1
   // cmds.add "zdo bind 0x${device.deviceNetworkId} 0x${FAN_EP} 0x01 $INOVELLI_CLUSTER {${device.zigbeeId}} {}"  // manufacturer-specific for fan/ep2

   // Reporting -- not necessary on this device?
   // cmds += zigbee.configureReporting(0x0006, 0, DataType.BOOLEAN, 0, 0xFFFF, null, [destEndpoint: 1], 0)
   // cmds += zigbee.configureReporting(0x0006, 0, DataType.BOOLEAN, 0, 0xFFFF, null, [destEndpoint: 2], 0)
   // cmds += zigbee.configureReporting(0x0008, 0, DataType.UINT8, 0, 0xFFFF, null, [destEndpoint: 1], 0)
   // cmds += zigbee.configureReporting(0x0008, 0, DataType.UINT8, 0, 0xFFFF, null, [destEndpoint: 2], 0)
   sendToDevice(cmds)
   createChildDevicesIfNeeded()
}

void parse(String description) {
   if (logEnable) log.debug "parse description: ${description}"
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
            Integer rawValue = Integer.parseInt(descMap.value, 16)
            String switchValue = (rawValue == 0) ? "off" : "on"
            String ep = descMap.endpoint
            DeviceWrapper cd = getChildDevice("${device.id}-${ep}")
            cd.parse([[name: "switch", value: switchValue, descriptionText: "${cd.displayName} switch is ${switchValue}"]])
            if (ep == FAN_EP) {
               // Wait a bit since seems to send new level events as is (and after) turning off:
               runIn(4, "sendSpeedEventFromLevelAndSwitch")
            }
         }
         else {
            if (logEnable) log.debug "skipping 0x0006:${descMap.attrId}"
         }
         break
      case 0x0008:  // Level
         if (descMap.attrInt == 0) {
            Integer rawValue = Integer.parseInt(descMap.value, 16)
            Integer levelValue = Math.round(rawValue/2.55)
            if (levelValue == 0 && rawValue > 0) levelValue = 1
            String ep = descMap.endpoint
            DeviceWrapper cd = getChildDevice("${device.id}-${ep}")
            cd.parse([[name: "level", value: levelValue, descriptionText: "${cd.displayName} level is ${levelValue}"]])
            if (ep == FAN_EP) {
               // Wait a bit since seems to send new level events as is (and after) turning off:
               runIn(4, "sendSpeedEventFromLevelAndSwitch")
            }
         }
         else {
            if (logEnable) log.debug "skipping 0x0008:${descMap.attrId}"
         }
         break
      default:
         if (logEnable) log.debug "ignoring ${descMap.clusterId}:${$descMap?.attrId}"
         break
   }
}

void sendSpeedEventFromLevelAndSwitch(Map<String,Object> options) {
   String newSpeed
   DeviceWrapper cd = getChildDevice("${device.id}-${FAN_EP}")
   if (cd.currentSwitch == "off") newSpeed = "off"
   //else if (cd.currentLevel == 0) newSpeed = "off" // <- ignoring since seems to send right when turns on (before new value)
   else if (cd.currentLevel < 21) newSpeed = "low"
   else if (cd.currentLevel < 61) newSpeed = "medium"
   else if (cd.currentLevel <= 100) newSpeed = "high"
   Map<String,String> evt = [name: "speed", value: newSpeed, descriptionText: "${cd.displayName} speed is ${newSpeed}"]
   cd.parse([evt])
}

/********************
** PARENT COMMANDS **
*********************/

void refresh() {
   if (logEnable) log.debug "refresh()"
   List<String> cmds = []
   List<Integer> epIds = [1, 2]
   epIds.each { Integer epId ->
      cmds += zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: epId], 200) // On/off
      cmds += zigbee.readAttribute(0x0008, 0x0000, [destEndpoint: epId], 200) // Level
      // TODO: private clusters for "parameters"?
   }
   cmds += zigbee.readAttribute(0x0000, 0x0006, null, 200)  // Basic - SW Date
   cmds += zigbee.readAttribute(0x0000, 0x4000, null, 200)  // Basic - SW Version
   sendToDevice(cmds)
}

List<String> updateFirmware() {
    if (logEnable) log.debug "updateFirmware()"
    return zigbee.updateFirmware()
}

/***********************
** COMPONENT COMMANDS **
************************/

void componentOn(DeviceWrapper cd) {
   if (logEnable) log.debug "componentOn(${cd.displayName})"
   if (cd.deviceNetworkId.endsWith(LIGHT_EP)) {
      sendToDevice "he cmd 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x0006 1 {}"
   }
   else if (cd.deviceNetworkId.endsWith(FAN_EP)) {
      sendToDevice "he cmd 0x${device.deviceNetworkId} 0x${FAN_EP} 0x0006 1 {}"
   }
}

void componentOff(DeviceWrapper cd) {
   if (logEnable) log.debug "componentOn(${cd.displayName})"
   if (cd.deviceNetworkId.endsWith(LIGHT_EP)) {
      sendToDevice("he cmd 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x0006 0 {}")
   }
   else if (cd.deviceNetworkId.endsWith(FAN_EP)) {
      sendToDevice "he cmd 0x${device.deviceNetworkId} 0x${FAN_EP} 0x0006 0 {}"
   }
}

void componentSetLevel(DeviceWrapper cd, level, Number transitionTime=null) {
   if (logEnable) log.debug "componentSetLevel(${cd.displayName}, $level, $transitionTime)"
   Integer intLevel = Math.round(level.toDouble() * 2.55)
   String zigbeeLevel = "0x${intTo8bitUnsignedHex(intLevel)}"
   if (cd.deviceNetworkId.endsWith(LIGHT_EP)) {
      Integer scaledRate = ((transitionTime == null ? defaultLightTransitionTimeS : transitionTime) * 10).toInteger()
      String zigbeeRate = DataType.pack(scaledRate, DataType.UINT16, true)
      String cmd = "he cmd 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x0008 4 {$zigbeeLevel $zigbeeRate}"
      sendToDevice(cmd)
   }
   else if (cd.deviceNetworkId.endsWith(FAN_EP)) {
      String cmd = "he cmd 0x${device.deviceNetworkId} 0x${FAN_EP} 0x0008 4 {$zigbeeLevel 0xFFFF}"
      sendToDevice(cmd)
   }
}

void componentSetSpeed(DeviceWrapper cd, String speed) {
   if (logEnable) log.debug "componentSetSpeed(${cd.displayName}, $speed)"
   if (cd.deviceNetworkId.endsWith(FAN_EP)) {
      switch (speed) {
         case "off":
            componentOff(cd)
            break
         case "on":
            componentOn(cd)
            break
         case "low":
            componentSetLevel(cd, 20)
            break
         case "medium-low":
         case "medium":
         case "medium-high":
            componentSetLevel(cd, 60)
            break
         case "high":
            componentSetLevel(cd, 100)
            break
         default:
            log.warn "Unexpected fan speed; ignoring. Speed: $speed"
      }
   }
   else {
      log.warn "Unexpected endpoint in componentSetSpeed() (child device DNI: ${cd.deviceNetworkId})"
   }
}

void componentStartLevelChange(DeviceWrapper cd, String direction) {
   if (logEnable) log.debug "componentStartLevelChange(${cd.displayName}, $direction)"
   if (cd.deviceNetworkId.endsWith(LIGHT_EP)) {
      Integer upDown = (direction == "down") ? 1 : 0
      Integer unitsPerSecond = 300
      String cmd = "he cmd 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x0008 1 { 0x${intTo8bitUnsignedHex(upDown)} 0x${DataType.pack(unitsPerSecond, DataType.UINT16, true)} }"
      sendToDevice(cmd)
   }
   else {
      log.warn "Unexpected endpoint in componentStartLevelChange() (child device DNI: ${cd.deviceNetworkId})"
   }
}

void componentStopLevelChange(DeviceWrapper cd) {
   if (logEnable) log.debug "componentStopLevelChange(${cd.displayName})"
   if (cd.deviceNetworkId.endsWith(LIGHT_EP)) {
      String cmd = "he cmd 0x${device.deviceNetworkId} 0x${LIGHT_EP} 0x0008 3 {}}"
      sendToDevice(cmd)
   }
   else {
      log.warn "Unexpected endpoint in componentStartLevelChange() (child device DNI: ${cd.deviceNetworkId})"
   }
}

void componentCycleSpeed(DeviceWrapper cd) {
   if (logEnable) log.debug "componentCycleSpeed($cd.displayName)"
   // Should only happen on fan child, so skipping any checks...
   DeviceWrapper fanChild = getChildDevice("${device.id}-02")
   String currentSpeed = fanChild.currentValue("speed") ?: "off"
   switch (currentSpeed) {
      case "off":
         componentSetLevel(fanChild, 20)
      break
      case "low":
         componentSetLevel(fanChild, 60)
      break
      case "medium-low":
      case "medium":
      case "medium-high":
         componentSetLevel(fanChild, 100)
      break
      case "high":
         componentOff(fanChild)
      break
   }
}

void componentRefresh(DeviceWrapper cd) {
   if (logEnable) log.debug "componentRefresh($cd.displayName)"
   String endpointId = cd.deviceNetworkId.split("-")[-1] 
   if (logEnable) log.debug "refresh()"    
   List<String> cmds = []
   cmds += zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: Integer.parseInt(endpointId)], 0) // on/off
   cmds += zigbee.readAttribute(0x0008, 0x0000, [destEndpoint: Integer.parseInt(endpointId)], 0) // level
   sendToDevice(cmds)
}

/******************
** OTHER METHODS **
*******************/

void sendToDevice(List<String> cmds, Long delay=250) {
   //log.trace cmds
   delayBetween(cmds, delay)
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

void createChildDevicesIfNeeded() {
   String lightDNI = "${device.id}-${LIGHT_EP}"
   String fanDNI = "${device.id}-${FAN_EP}"
   com.hubitat.app.ChildDeviceWrapper lightChild = getChildDevice(lightDNI)
   com.hubitat.app.ChildDeviceWrapper fanChild = getChildDevice(fanDNI)
   if (lightChild == null) {
      lightChild = addChildDevice("hubitat", "Generic Component Dimmer", lightDNI, [name: "${device.displayName} Light", isComponent: false])
   }
   if (fanChild == null) {
      fanChild = addChildDevice("hubitat", "Generic Component Fan Control", fanDNI, [name: "${device.displayName} Fan", isComponent: false])
      pauseExecution(100)
      fanChild.sendEvent([name: "supportedFanSpeeds", value: new groovy.json.JsonBuilder(supportedFanSpeeds).toString()])
   }
}

String intTo8bitUnsignedHex(value) {
   return zigbee.convertToHexString(value.toInteger(), 2)
}