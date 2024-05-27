/**
 *  Zooz ZSE11 (Q Sensor) community driver for Hubitat
 * 
 *  Copyright 2024 Robert Morris
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
 *  2024-05-27: Update for 800LR hardware (new parameters and reports)
 *  2021-04-25: (500 series) Fix displayed temperature units to be correct if hub units different from sensor units
 *  2021-04-23: Pad firmware subversion with 0 as needed
 *  2021-04-19: Added option for temperature and humidity value adjustment
 *  2021-04-18: Fix for parameter 13 and 172 size
 *  2021-04-17: Improvements to initial configuration when DC-powered, minor fixes (serial number parsing)
 *  2021-04-16: Initial release
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x30: 2,    // SensorBinary
   0x31: 5,    // Sensor Multilevel
   0x55: 1,    // Transport Service
   0x59: 1,    // AssociationGrpInfo
   0x5A: 1,    // DeviceResetLocally
   0x5E: 2,    // ZwaveplusInfo
   0x6C: 1,    // Supervision
   0x70: 2,    // Configuration
   0x71: 3,    // Notification
   0x72: 2,    // ManufacturerSpecific
   //0x73: 1,    // Powerlevel
   0x7A: 2,    // FirmwareUpdateMd
   0x80: 1,    // Battery
   0x84: 2,    // WakeUp
   0x85: 2,    // Association
   0x86: 2,    // Version
   0x98: 1,    // Security
	0x9F: 1     // Security 2
]

@Field static final Integer defaultWakeUpInterval = 43200

@Field static final Map zwaveParameters = [
   12: [
      type: "enum", desc: "Motion sensitivity", size: 1,
      options: [[0:"Disabled"],[1:"1 - Low"],[2:"2"],[3:"3"],[4:"4"],[5:"5"],[6:"6 (default)"], [7:"7"],[8:"8 - High"]]
   ],
   13: [
      type: "enum", desc: "Motion re-trigger interval", size: 2,
      options: [[10:"10 seconds"],[15:"15 seconds"],[20:"20 seconds"],[25:"25 seconds"],[30:"30 seconds (default)"],
         [45:"45 seconds"],[60:"1 minute"],[90:"1.5 minutes"],[120:"2 minutes"],[180:"3 minutes"],[240:"4 minutes"],
         [300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1800:"30 minutes"],[2700:"45 minutes"],
         [3600:"1 hour"]]
   ],
   19: [type: "enum", desc: "LED flash for motion", size: 1,
       options: [[0:"Do not flash when motion is detected"],[1:"Flash when motion is detected (default)"]],
   ],
   20: [type: "enum", desc: "Enable vibration/tamper sensor", size: 1,
         options: [[0:"Disable"],[1:"Enable (default)"]],
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   183: [type: "enum", desc: "Temperature report threshold: send new temperature report if temperatue changes by", size: 2,
           options: [[1:"1 °F (default)"],[2:"2 °F"],[3:"3 °F"],[4:"4 °F"],[5:"5 °F"],[6:"6 °F"],[7:"7 °F"],
           [8:"8 °F"],[9:"9 °F"],[10:"10 °F"],[15:"15 °F"],[20:"20 °F"],[25:"25 °F"],[50:"50 °F"],
           [100:"100 °F"],[144:"144 °F"]]
   ],
   22: [type: "enum", desc: "Temperature change probe interval (report still sent only if value change crosses report threshold)",
         options: [[30:"30 seconds"],[60:"1 minute (default)"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],
            [1200:"20 minutes"],[1800:"30 minutes"],[2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
            [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   173: [type: "enum", desc: "Reporting interval for temperature (report sent regardless of reporting threshold)",
         options: [[0:"Disabled; report based on threshold only"],[60:"1 minute"],
         [180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],
         [2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
         [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours (default)"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   184: [type: "enum", desc: "Humidity report threshold: send new humidity report if value changes by", size: 1,
           options: [[1:"1%"],[2:"2%"],[3:"3%"],[4:"4%"],[5:"5% (default)"],[6:"6%"],[7:"7%"],[8:"8%"],[9:"9%"],
           [10:"10%"],[11:"11%"],[12:"12%"],[13:"13%"],[14:"14%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],
           [35:"35%"],[40:"40%"],[45:"45%"],[50:"50%"],[80:"80%"]]
   ],
   23: [type: "enum", desc: "Humidity change probe interval (report still sent only if value change crosses lux report threshold)",
         options: [[10:"10 seconds (default)"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],
            [1200:"20 minutes"],[1800:"30 minutes"],[2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
            [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   174: [type: "enum", desc: "Reporting interval for humidity (report sent regardless of reporting threshold)",
         options: [[0:"Disabled; report based on threshold only (default)"],[60:"1 minute"],
         [180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],
         [2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
         [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   185: [type: "enum", desc: "Lux report threshold: send new illuminance report if value changes by", size: 2,
           options: [[1:"1 lux"],[3:"3 lux"],[5:"5 lux"],[7:"7 lux"],[10:"10 lux"],[12:"12 lux"],[15:"15 lux"],
           [20:"20 lux"],[25:"25 lux"],[30:"30 lux"],[35:"35 lux"],[40:"40 lux"],[45:"45 lux"],
           [50:"50 lux (default)"],[60:"60 lux"],[75:"75 lux"],[100:"100 lux"],[150:"150 lux"],
           [200:"200 lux"],[300:"300 lux"],[400:"400 lux"],[500:"500 lux"],[750:"750 lux"],[1000:"1000 lux"],
           [2000:"2000 lux"],[5000:"5000 lux"],[10000:"10000 lux"],[30000:"30000 lux"]]
   ],
   21: [type: "enum", desc: "Lux change probe interval (report still sent only if value change crosses report threshold)",
         options: [[10:"10 seconds (default)"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],
            [1200:"20 minutes"],[1800:"30 minutes"],[2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
            [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   175: [type: "enum", desc: "Reporting interval for lux (report sent regardless of reporting threshold)",
         options: [[0:"Disabled; report based on threshold only (default)"],[60:"1 minute"],
         [180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],
         [2700:"45 minutes"],[3600:"1 hour"],[5400:"1.5 hours"],[7200:"2 hours"],
         [10800:"3 hours"],[18000:"5 hours"],[28800:"8 hours"],[43200:"12 hours"]],
         size: 2,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   203: [type: "enum", desc: "Adjust lux values by this amount",
         options: [[0:"-100"],[10:"-90"],[20:"-80"],[30:"-70"],[40:"-60"],[50:"-50"],[60:"-40"],[70:"-30"],
         [75:"-25"],[80:"-20"],[85:"-15"],[90:"-10"],[95:"-5"],[98:"-2"],[99:"-1"],[100:"0 (default; must reset device to return to this setting)"],
         [101:"1"],[102:"2"],[105:"5"],[110:"10"],[115:"15"],[120:"20"],[130:"30"],[140:"40"],[150:"50"],[160:"60"],
         [170:"70"],[180:"80"],[190:"90"],[200:"100"]],
         size: 1,
         minMajorFirmwareVersion: 2, minMinorFirmwareVersion: 0
   ],
   172: [type: "enum", desc: "Reporting interval for temperature, humidity, and lux (even if threshold not met)",
         options: [[1:"1 hour"],[2:"2 hours"],[3:"3 hours"],[4:"4 hours (default)"],[5:"5 hours"],
            [6:"6 hours"],[9:"9 hours"],[12:"12 hours"],[18:"18 hours"],[24:"24 hours"],[48:"48 hours"],
            [120:"5 days (120 hours)"],[240:"10 days (240 hours)"],[480:"20 days (480 hours)"],[744:"31 days (744 hours)"]],
         size: 2,
         maxMajorFirmwareVersion: 1, maxMinorFirmwareVersion: 999
   ],
]

@Field static final String wakeUpInstructions = "To wake the device immediately, press and hold the Z-Wave button for 3 seconds."

metadata {
   definition(name: "Zooz ZSE11 Q Sensor", namespace: "RMoRobert", author: "Robert Morris",
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zse11-q-sensor.groovy") {
      capability "Sensor"
      capability "Battery"
      capability "MotionSensor"
      capability "RelativeHumidityMeasurement"
      capability "IlluminanceMeasurement"
      capability "TemperatureMeasurement"
      capability "TamperAlert"
      capability "PowerSource"
      capability "Configuration"
      capability "Refresh"

      // Can uncomment this if want easy way to do without switching to "Device" driver:
      //command "clearChildDevsAndState"

      fingerprint mfr:"027A", prod:"0201", deviceId:"0006", inClusters:"0x5E,0x6C,0x55,0x98,0x9F", controllerType: "ZWV"  // 500-series w/ S2
      fingerprint mfr:"027A", prod:"0201", deviceId:"0006", inClusters:"0x5E,0x22,0x55,0x98,0x9F,0x6C"
      fingerprint mfr:"027A", prod:"0201", deviceId:"0006", inClusters:"0x5E,0x22,0x55,0x9F,0x6C" 
   }

   preferences {
      getZwaveParametersForFwVersion().each { Map.Entry paramDetails ->
         input getInputParamsForZwaveParam(paramDetails)
      }
      // HW v2 supports Z-Wave params for these offsets, but keep preferences so works with all models:
      input name: "tempAdjust", type: "number", title: "Adjust temperature value by this amount", description: "Example: 0.4 or -1.5 (optional)"
      input name: "humidAdjust", type: "number", title: "Adjust humidity value by this amount", description: "Example: 0.4 or -1.5 (optional)"
      input name: "showParamNumbers", type: "bool", title: "Show Z-Wave parameter numbers in preference descriptions"
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

Map getZwaveParametersForFwVersion() {
   String firmwareVersion = getDataValue("firmwareVersion")
   Integer fwMajorVersion = 0
   Integer fwMinorVersion = 0
   if (firmwareVersion != null) {
      try {
         String strMaj
         String strMin
         (strMaj, strMin) = firmwareVersion.split("\\.")
         fwMajorVersion = Integer.parseInt(strMaj)
         fwMinorVersion = Integer.parseInt(strMin)
      }
      catch (Exception ex) {
         log.warn "Error parsing firmware version: $ex"
      }
   }
   return zwaveParameters.findAll { Integer key, Map value ->
      if ( // none specified:
         value.minMajorFirmwareVersion == null && value.maxMajorFirmwareVersion == null &&
         value.minMinorFirmwareVersion == null && value.maxMinorFirmwareVersion == null
      ) {
         return true
      }
      else if ( // min specified and meets:
         (value.minMajorFirmwareVersion != null && value.minMinorFirmwareVersion != null) &&
         (fwMajorVersion >= value.minMajorFirmwareVersion && fwMinorVersion >= value.minMinorFirmwareVersion)
      ) {
         return true
      }
      else if ( // max specified and meets:
         (value.maxMajorFirmwareVersion != null && value.maxMinorFirmwareVersion != null) &&
         (fwMajorVersion <= value.maxMajorFirmwareVersion && fwMinorVersion <= value.maxMinorFirmwareVersion)
      ) {
         return true
      }
      // does not handle min *and* max but also not currently used...if needed in future, add. Otherwise:
      else {
         return false
      }
   }
}

Map getInputParamsForZwaveParam(Map.Entry paramDetails) {
   String title
   if (showParamNumbers) {
      title = "[${paramDetails.key}] " + paramDetails.value.desc
   }
   else {
      title = title = paramDetails.value.desc
   }
   if (paramDetails.value.default && paramDetails.value.type != "enum") title += " [DEFAULT: ${paramDetails.value.default}]"
   if (paramDetails.value.type == "number") {
      return [name: "param.${paramDetails.key}" as String, type: "number", range: paramDetails.value.range, title: title]
   }
   else if (paramDetails.value.type == "enum") {
      return [name: "param.${paramDetails.key}" as String, type: "enum", options: paramDetails.value.options, title: title]
   }
   else {
      log.warn "Unexpected input type ${paramDetails.value.type} for number ${paramDetails.key}"
   }
}

void logsOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("logEnable", [value:"false", type:"bool"])
}

void sendToDevice(List<String> cmds, Integer delay=300) {
   if (logEnable) log.debug "sendToDevice($cmds, $delay)"
   sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
   if (logEnable) log.debug "sendToDevice(String $cmd)"
   sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<String> cmds, Long delay=300) {
   if (logEnable) log.debug "commands($cmds, $delay)"
   return delayBetween(cmds.collect{ it.startsWith("delay ") ? it : zwaveSecureEncap(it) }, delay)
}

void setStoredConfigParamValue(Integer parameterNumber, BigInteger parameterValue) {
   state."configParam${parameterNumber}" = parameterValue
   // Also update preference if present:
   String settingName = "param.${parameterNumber}" as String
   if (settings[settingName] != parameterValue && settings[settingName] != parameterValue.toString()) {
      Map paramData = getZwaveParametersForFwVersion()[parameterNumber]
      if (paramData) {
         if (paramData.type == "enum") {
            device.updateSetting(settingName, [value: parameterValue.toString(), type: "enum"])
          }
          else {
            device.updateSetting(settingName, [value: parameterValue, type: "number"])
         }
      }
   }
}

BigInteger getStoredConfigParamValue(Integer parameterNumber) {
   return state."configParam${parameterNumber}"
}

void parse(String description) {
   if (logEnable) log.debug "parse description: ${description}"
   hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
   if (cmd) {
      zwaveEvent(cmd)
   }
   else {
      if (logEnable) log.debug "unable to parse: ${description}"
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
   if (logEnable) log.debug "SupervisionGet: $cmd"
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
   else {
      if (logEnable) log.debug "Unable to de-encapsulate command from $cmd"
   }
   // Is this necessary (or effective) on sleepy devices?
   /*
   sendHubCommand(new hubitat.device.HubAction(
      zwaveSecureEncap(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID,
         reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)),
         hubitat.device.Protocol.ZWAVE)
   )
   */
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
   if (logEnable) log.debug "VersionReport: ${cmd}"
   device.updateDataValue("firmwareVersion", """${cmd.firmware0Version}.${String.format("%02d", cmd.firmware0SubVersion)}""")
   device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
   device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
   if (logEnable) log.debug "DeviceSpecificReport v2: ${cmd}"
   switch (cmd.deviceIdType) {
      case 1: // apparently not used for this, but just in case?
      case 2:
         // serial number
         String serialNumber = ""
         if (cmd.deviceIdDataFormat==1) {
            cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xFF, 1).padLeft(2, '0')}
         } else {
            cmd.deviceIdData.each { serialNumber += (char)it }
         }
         if (logEnable) log.debug "Device serial number is $serialNumber"
         device.updateDataValue("serialNumber", "$serialNumber")
         break
   }
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (logEnable) log.debug "ConfigurationReport: ${cmd}"
   if (txtEnable) log.info "${device.displayName} parameter ${cmd.parameterNumber} (size ${cmd.size}) is ${cmd.scaledConfigurationValue}"
   setStoredConfigParamValue(cmd.parameterNumber, cmd.scaledConfigurationValue)
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
   if (logEnable) log.debug "BatteryReport: $cmd"
   Integer lvl = cmd.batteryLevel
   if (lvl == 0xFF) {
      lvl = 1
   }
   else if (lvl > 100) {
      lvl = 100
   }
   if (device.currentValue("powerSource") != "battery") {
      sendEvent(name: "powerSource", value: "battery", descriptionText: "${device.displayName} power source is battery")
      if (txtEnable) log.info "${device.displayName} power source is battery"
   }
   String descText = "${device.displayName} battery level is ${lvl}%"
   if (txtEnable) log.info(descText)
   sendEvent(name:"battery", value: lvl, unit:"%", descriptionText: descText, isStateChange: true)
}

void zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
   if (logEnable) log.debug "SensorBinaryReport: $cmd"
   if (cmd.sensorType == hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport.SENSOR_TYPE_MOTION) {
      if (cmd.sensorValue) {
         String descText = "${device.displayName} motion is active"
         sendEvent(name: "motion", value: "active", descriptionText: descText)
         if (txtEnable) log.info descText
      }
      else {
         String descText = "${device.displayName} motion is inactive"
         sendEvent(name: "motion", value: "inactive", descriptionText: descText)
         if (txtEnable) log.info descText
      }
   }
   else {
      if (logEnable) "ignoring unexpected type of SensorBinaryReport: $cmd"
   }
}

void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
   if (logEnable) log.debug "NotificationReport: $cmd"
   if (cmd.notificationType == hubitat.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_BURGLAR) {  //0x07
      if (cmd.event == 3 || cmd.eventParameter[0] == 3) {
         if (cmd.event) {
            sendEvent(name: "tamper", value: "detected", descriptionText: "${device.displayName} tamper is detected (case opened)")
            if (txtEnable) log.info("${device.displayName} tamper is detected (case opened)")
         }
         else {
            sendEvent(name: "tamper", value: "clear", descriptionText: "${device.displayName} tamper is clear")
            if (txtEnable) log.info("${device.displayName} tamper is clear")
         }
      }
      else if (cmd.event == 8 || cmd.eventParameter[0] == 8) {
         if (cmd.event) {
            sendEvent(name: "motion", value: "active", descriptionText: "${device.displayName} motion is active")
            if (txtEnable) log.info("${device.displayName} motion is active")
         }
         else {
            sendEvent(name: "motion", value: "inactive", descriptionText: "${device.displayName} motion is inactive")
            if (txtEnable) log.info("${device.displayName} motion is inactive")
         }
      }
      else if (cmd.event == 9 || cmd.eventParameter[0] == 9) {
         if (cmd.event) {
            sendEvent(name: "tamper", value: "detected", descriptionText: "${device.displayName} tamper is detected (vibration)")
            if (txtEnable) log.info("${device.displayName} tamper is detected (vibration)")
         }
         else {
            sendEvent(name: "tamper", value: "clear", descriptionText: "${device.displayName} tamper is clear")
            if (txtEnable) log.info("${device.displayName} tamper is clear")
         }
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
   if (logEnable) log.debug "SensorMultilevelReport: $cmd"
   switch (cmd.sensorType as Short) {
         case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_TEMPERATURE_VERSION_1:
            String unit = cmd.scale ? "F" : "C"
            String strTemp = convertTemperatureIfNeeded(cmd.scaledSensorValue, unit, cmd.precision)
            BigDecimal temp = new BigDecimal(strTemp)
            if (settings["tempAdjust"]) temp += (settings["tempAdjust"] as BigDecimal)
            String descText = "${device.displayName} temperature is ${temp} °${location.temperatureScale}"
            if (settings["tempAdjust"]) descText += " (adjusted per settings from: $strTemp)"
            sendEvent(name: "temperature", value: temp, unit: "°${location.temperatureScale}", descriptionText: descText)
            if (txtEnable) log.info descText
            break
      case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_LUMINANCE_VERSION_1:
         Integer val = Math.round(cmd.scaledSensorValue)
         String descText = "${device.displayName} illuminance is ${val} lx"
         sendEvent(name: "illuminance", value: val, unit: "lx", descriptionText: descText)
         if (txtEnable) log.info descText
         break
      case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2:
         Integer val = Math.round(cmd.scaledSensorValue + (settings["humidAdjust"] as Double ?: 0))
         String descText = "${device.displayName} humidity is ${val}%"
            if (settings["humidAdjust"]) descText += " (adjusted per settings from: ${cmd.scaledSensorValue})"
         sendEvent(name: "humidity", value: val, unit: "%", descriptionText: descText)
         if (txtEnable) log.info descText
         break
      default:
         if (logEnable) log.debug "skip: $cmd"
   }
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
   if (logEnable) log.debug "WakeUpIntervalReport: $cmd"
   updateDataValue("zwWakeupInterval", "${cmd.seconds}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
   if (logEnable) log.debug "WakeUpNotification: $cmd"
   if (txtEnable) log.info "${device.displayName} woke up"
   List<String> cmds = getPendingConfigureAndRefreshCommands()

   //cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
  
   state.pendingConfigure = false
   state.pendingRefresh = false
   state.initialized = true

   if (cmds) {
      cmds << "delay 2000"
      cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
   }
   
   sendToDevice(cmds, 750)
}

void zwaveEvent(hubitat.zwave.Command cmd){
    if (logEnable) log.debug "skip: ${cmd}"
}

void installed(){
   log.debug "installed()"
   runIn(1, "updated")
}

void refresh() {
   if (logEnable) log.debug "refresh()"
   state.pendingRefresh = true
   if (device.currentValue("powerSource") == "dc") {
      log.debug "Device is DC-powered; running refresh commands now"
      List<String> cmds = getPendingConfigureAndRefreshCommands(true)
      state.pendingRefresh = false
      state.initialized = true
      sendToDevice(cmds, 500)
   }
   else {
      if (logEnable) log.debug "Device will fetch new data when the device wakes up. ${wakeUpInstructions}"
   }
}

void configure() {
   log.debug "configure()"
   if (device.currentValue("powerSource") != "battery") {
      log.debug "running configure commands now (device is DC-powered or just installed/awake)"
      List<String> cmds = getPendingConfigureAndRefreshCommands()   
      state.pendingRefresh = false
      state.pendingConfigure = false
      state.initialized = true
      sendToDevice(cmds, 500)
   }
   else {
      state.pendingConfigure = true
      log.debug "Configuration will be applied when the device wakes up. ${wakeUpInstructions}"
   }
}

// Apply preferences changes, including updating parameters
void updated() {
   log.debug "updated()"
   log.warn "Debug logging is: ${logEnable == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${txtEnable == true ? 'enabled' : 'disabled'}"
   if (logEnable) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, "logsOff")
   }
   if (device.currentValue("powerSource") == null) { // look for Z-Wave Battery command class (0x80)
      String powerSource = (device.getDataValue("inClusters")?.tokenize(",")?.contains("0x80") ||
                           device.getDataValue("secureInClusters")?.tokenize(",")?.contains("0x80")) ? "battery" : "dc"
      sendEvent(name: "powerSource", value: powerSource, descriptionText: "${device.displayName} power source is $powerSource")
      if (txtEnable) log.info "${device.displayName} power source is $powerSource"
   }
   else if (powerSource == "dc") {
      // seems to make sense to do...
      sendEvent(name: "battery", value: 100, unit: "%", descriptionText: "${device.displayName} battery is 100% (DC powered)")
   }
   if (!state.initialized || (device.currentValue("powerSource") == "dc")) {
      List<String> cmds = getPendingConfigureAndRefreshCommands()
      if (device.currentValue("powerSource") == "battery") cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
      state.pendingRefresh = false
      state.pendingConfigure = false
      state.initialized = true
      sendToDevice(cmds, 500)
   }
   else if (getPendingConfigurationChanges()) {
      log.debug "Pending configuration changes will be applied when the device wakes up. ${wakeUpInstructions}"
   }
}

List<String> getPendingConfigureAndRefreshCommands(Boolean refreshOnly=false) {
   List<String> cmds = []

   // refresh (or initial fetch if needed)
   if (state.pendingRefresh || device.currentValue("battery") == null && device.currentValue("powerSource") != "dc") {
      cmds << zwave.batteryV1.batteryGet().format()
   }
   if (state.pendingRefresh || !getDataValue("firmwareVersion")) {
      cmds  << zwave.versionV2.versionGet().format()
      //cmds << zwave.manufacturerSpecificV2.deviceSpecificGet().format()
      cmds << zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 2).format()
   }
   if (state.pendingRefresh || !(getDataValue("zwWakeupInterval"))) {
      cmds << zwave.wakeUpV2.wakeUpIntervalGet().format()
   }
   if (state.pendingRefresh || device.currentValue("temperature") == null) {
      cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_TEMPERATURE_VERSION_1).format()
   }
   if (state.pendingRefresh || device.currentValue("humidity") == null) {
      cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2).format()
   }
   if (state.pendingRefresh || device.currentValue("illuminance") == null) {
      cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_LUMINANCE_VERSION_1).format()
   }
   if (state.pendingRefresh || device.currentValue("motion") == null) {
      cmds << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport.SENSOR_TYPE_MOTION).format()
   }

   // configure (or initial set if needed)
   if (!refreshOnly && getPendingConfigurationChanges()) {
      cmds += getPendingConfigurationChanges()
   }

   return cmds
}

// Checks device preferences (for Z-Wave parameters) against last reported values, returns list of ConfigurationSet commands
// (of differences) if any, or empty list if not
List<String> getPendingConfigurationChanges() {
   List<String> changes = []
   List<Short> paramsToGet = []
   getZwaveParametersForFwVersion().each { Integer key, Map value ->
      if (settings["param.${key}"] != null) {
         Short paramNumber = key as Short
         Short paramSize = value.size as Short
         BigInteger paramValue = settings["param.${key}"] as BigInteger
         if ((getStoredConfigParamValue(paramNumber) == null || getStoredConfigParamValue(paramNumber) != paramValue) || state.pendingConfigure) {
            if (logEnable) log.debug "parameter $paramNumber (size $paramSize) is ${getStoredConfigParamValue(paramNumber)} but should be $paramValue; adding to commands..."
            changes << zwave.configurationV1.configurationSet(scaledConfigurationValue: paramValue,
                                                              parameterNumber: paramNumber,
                                                              size: paramSize).format()
            paramsToGet << paramNumber
         }
      }
   }
   if (paramsToGet) {
      paramsToGet.each {
         changes << zwave.configurationV1.configurationGet(parameterNumber: it).format()
      }
   }
   if (device.currentValue("powerSource") != "dc" &&
      (getDataValue("zwWakeupInterval") && ((getDataValue("zwWakeupInterval") as Integer) != defaultWakeUpInterval)) ||
      state.pendingRefresh) {
      changes << zwave.wakeUpV2.wakeUpIntervalSet(seconds: defaultWakeUpInterval, nodeid: zwaveHubNodeId ?: 1).format()
   }
   if (logEnable) "getPendingConfigurationChanges: $changes"
   return changes
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}