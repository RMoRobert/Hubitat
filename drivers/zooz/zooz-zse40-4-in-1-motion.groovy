/**
 *  Zooz ZSE40 (4-in-1 Motion Sensor) community driver for Hubitat
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
 *  Version History
 *  2025-02-09: Force zwWakeupInterval to save as string per device.data value requirements
 *  2021-04-23: Pad firmware subversion with 0 as needed
 *  2021-04-11: Initial release
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x20: 1,    // Basic
   0x31: 5,    // Sensor Multilevel
   0x59: 1,    // AssociationGrpInfo
   0x5A: 1,    // DeviceResetLocally
   0x5E: 2,    // ZwaveplusInfo
   0x70: 2,    // Configuration
   0x71: 3,    // Notification
   0x72: 2,    // ManufacturerSpecific
   0x73: 1,    // Powerlevel
   0x7A: 2,    // FirmwareUpdateMd
   0x80: 1,    // Battery
   0x84: 2,    // WakeUp
   0x85: 2,    // Association
   0x86: 2,    // Version
   0x98: 1     // Security
]

@Field static final Integer defaultWakeUpInterval = 43200

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "[1] Temperature scale",
         options: [[0:"Celsius"],[1:"Fahrenheit (default)"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "[2] Temperature reporting threshold",
           options: [[1:"0.1 &deg;C (0.18 &deg;F)"],[2:"0.2 &deg;C (0.36 &deg;F)"],[3:"0.3 &deg;C (0.54 &deg;F)"],
           [4:"0.4 &deg;C (0.72 &deg;F)"],[5:"0.5 &deg;C (0.9 &deg;F)"],[6:"0.6 &deg;C (1.08 &deg;F)"],
           [7:"0.7 &deg;C (1.26 &deg;F)"],[8:"0.8 &deg;C (1.44 &deg;F)"],[9:"0.9 &deg;C (1.62 &deg;F)"],
           [10:"1.0 &deg;C (1.8 &deg;F) (default)"],[11:"1.1 &deg;C (1.98 &deg;F)"],
           [20:"2.0 &deg;C (3.6 &deg;F)"],[30:"3.0 &deg;C (5.4 &deg;F)"],[50:"5.0 &deg;C (9.0 &deg;F)"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "Humidity reporting threshold",
           options: [[1:"1%"],[2:"2%"],[3:"3%"],[4:"4%"],[5:"5%"],[6:"6%"],[7:"7%"],[8:"8%"],[9:"9%"],
           [10:"10% (default)"],[11:"11%"],[12:"12%"],[13:"13%"],[14:"14%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],
           [35:"35%"],[40:"40%"],[45:"45%"],[50:"50%"]]],
      size: 1],
   4: [input: [name: "param.4", type: "enum", title: "Illuminance reporting threshold",
           options: [[5:"5%"],[6:"6%"],[7:"7%"],[8:"8%"],[9:"9%"],
           [10:"10% (default)"],[11:"11%"],[12:"12%"],[13:"13%"],[14:"14%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],
           [35:"35%"],[40:"40%"],[45:"45%"],[50:"50%"]]],
      size: 1],
   5: [input: [name: "param.5", type: "enum", title: "Motion re-trigger interval",
           options: [[15:"15 seconds (default)"],[20:"20 seconds"],[25:"25 seconds"],[30:"30 seconds"],
           [45:"45 seconds"],[60:"1 minute"],[90:"1.5 minutes"],[120:"2 minutes"],[180:"3 minutes"],[240:"4 minutes"]]],
      size: 1],
   6: [input: [name: "param.6", type: "enum", title: "Motion sensitivity",
           options: [[1:"1 - High"],[2:"2"],[3:"3 (default)"],[4:"4"],[5:"5"],[6:"6"],[7:"7 - Low"]]],
      size: 1],
   7: [input: [name: "param.7", type: "enum", title: "LED indicator mode",
           options: [[1:"Temperature off/motion off"],[2:"Temperature pulse/motion flash"],
           [3:"Temperature flash (every 3 minutes)/motion flash"],
           [4:"Temperature off/motion flash (default)"]]],
      size: 1]
]

@Field static final String wakeUpInstructions = "To wake the device immediately, press the Z-Wave button (on bottom) once."

metadata {
   definition(name: "Zooz ZSE40 4-in-1 Motion Sensor", namespace: "RMoRobert", author: "Robert Morris", 
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zse40-4-in-1-motion.groovy") {
      capability "Sensor"
      capability "Battery"
      capability "MotionSensor"
      capability "RelativeHumidityMeasurement"
      capability "IlluminanceMeasurement"
      capability "TemperatureMeasurement"
      capability "TamperAlert"
      capability "Configuration"
      capability "Refresh"

      fingerprint mfr: "027A", prod: "2021", deviceId: "2101", inClusters: "0x5E,0x86,0x72,0x5A,0x85,0x59,0x73,0x80,0x71,0x31,0x70,0x84,0x7A"
   }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "scaleToLux", type: "bool", title: "Convert illuminance readings to lux (may lose resolution)"
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
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

void sendToDevice(String cmd, Long delay=300) {
   if (logEnable) log.debug "sendToDevice(String $cmd, $delay)"
   sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<String> cmds, Long delay=300) {
   if (logEnable) log.debug "commands($cmds, $delay)"
   return delayBetween(cmds.collect{ zwaveSecureEncap(it) }, delay)
}

void setStoredConfigParamValue(Integer parameterNumber, BigInteger parameterValue) {
   state."configParam${parameterNumber}" = parameterValue
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

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
   if (logEnable) log.debug "VersionReport: ${cmd}"
   device.updateDataValue("firmwareVersion", """${cmd.firmware0Version}.${String.format("%02d", cmd.firmware0SubVersion)}""")
   device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
   device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
   if (logEnable) log.debug "DeviceSpecificReport v2: ${cmd}"
   switch (cmd.deviceIdType) {
      case 1:
         // serial number
         String serialNumber = ""
         if (cmd.deviceIdDataFormat==1) {
            cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff, 1).padLeft(2, '0')}
         } else {
            cmd.deviceIdData.each { serialNumber += (char)it }
         }
         if (logEnable) log.debug "Device serial number is $serialNumber"
         device.updateDataValue("serialNumber", serialNumber)
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
   String descText = "${device.displayName} battery level is ${lvl}%"
   if (txtEnable) log.info(descText)
   sendEvent(name:"battery", value: lvl, unit:"%", descriptionText: descText, isStateChange: true)
}


// Motion comes in as Basic and NotificationReport; using Basic here
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	if (logEnable) "BasicSet: $cmd"
	if (cmd.value == 0xFF) {
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

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	if (logEnable) "BasicReport: $cmd"
	if (cmd.value == 0xFF) {
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

void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
   if (logEnable) log.debug "NotificationReport: $cmd"
   if (cmd.notificationType == 7 && cmd.eventParameter[0] == 3) {
      if (cmd.v1AlarmLevel == 0xFF) {
         sendEvent(name: "tamper", value: "detected", descriptionText: "${device.displayName} tamper is detected")
         if (txtEnable) log.info("${device.displayName} tamper is detected")
      }
      else {
         sendEvent(name: "tamper", value: "clear", descriptionText: "${device.displayName} tamper is clear")
         if (txtEnable) log.info("${device.displayName} tamper is clear")
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
   if (logEnable) log.debug "SensorMultilevelReport: $cmd"
   switch (cmd.sensorType as Short) {
         case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_TEMPERATURE_VERSION_1:
            String unit = (settings["param.1"] != 0) ? "°F" : "°C"
            BigDecimal temp = cmd.scaledSensorValue
            String descText = "${device.displayName} temperature is ${temp} ${unit}"
            sendEvent(name: "temperature", value: temp, unit: unit, descriptionText: descText)
            if (txtEnable) log.info descText
            break
      case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_LUMINANCE_VERSION_1:
         String unit = scaleToLux ? "lx" : "%"
         BigDecimal val = cmd.scaledSensorValue
         if (scaleToLux) val = Math.round((val * 0.6)) // This is approximate; I think Zooz has a formula somewhere that may get closer...
         String descText = "${device.displayName} illuminance is ${val}${unit == lx ? ' ' : ''}${unit}"
         sendEvent(name: "illuminance", value: val, unit: unit, descriptionText: descText)
         if (txtEnable) log.info descText
         break
      case hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2:
         BigDecimal val = cmd.scaledSensorValue
         String descText = "${device.displayName} humidity is ${val}%"
         sendEvent(name: "humidity", value: val, unit: "%", descriptionText: descText)
         if (txtEnable) log.info descText
         break
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

   cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
  
   state.pendingRefresh = false
   state.pendingConfigure = false
   state.initialized = true

   sendToDevice(cmds, 500)
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
   if (logEnable) log.debug "Device will fetch new data when the device wakes up. ${wakeUpInstructions}"
}

void configure() {
   log.debug "configure()"
   state.pendingConfigure = true
   log.debug "Configuration will be applied when the device wakes up. ${wakeUpInstructions}"
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
   if (!state.initialized) {
      List<String> cmds = getPendingConfigureAndRefreshCommands()
      cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
      state.pendingRefresh = false
      state.pendingConfigure = false
      state.initialized = true
      sendToDevice(cmds, 500)
   }
   else if (getPendingConfigurationChanges()) {
      log.debug "Pending configuration changes will be applied when the device wakes up. ${wakeUpInstructions}"
   }
}

List<String> getPendingConfigureAndRefreshCommands() {
    List<String> cmds = []

   // refresh (or initial fetch if needed)
   if (state.pendingRefresh || device.currentValue("battery") == null) {
      cmds << zwave.batteryV1.batteryGet().format()
   }
   if (state.pendingRefresh || !getDataValue("firmwareVersion")) {
      cmds  << zwave.versionV2.versionGet().format()
      //cmds << zwave.manufacturerSpecificV2.deviceSpecificGet().format()
      cmds << zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1).format()
   }
   if (state.pendingRefresh || !(getDataValue("zwWakeupInterval"))) {
      cmds << zwave.wakeUpV2.wakeUpIntervalGet().format()
   }
   if (state.pendingRefresh || device.currentValue("temperature") == null) {
	   cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_TEMPERATURE_VERSION_1)
   }
   if (state.pendingRefresh || device.currentValue("humidity") == null) {
	   cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_RELATIVE_HUMIDITY_VERSION_2)
   }
   if (state.pendingRefresh || device.currentValue("illuminance") == null) {
	   cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2,
         sensorType: hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelSupportedScaleReport.SENSOR_TYPE_LUMINANCE_VERSION_1)
   }

   // configure (or initial set if needed)
   if (getPendingConfigurationChanges()) {
      cmds += getPendingConfigurationChanges()
   }

   return cmds
}

// Checks device preferences (for Z-Wave parameters) against last reported values, returns list of ConfigurationSet commands
// (of differences) if any, or empty list if not
List<String> getPendingConfigurationChanges() {
   List<String> changes = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if ((getStoredConfigParamValue(param) == null || getStoredConfigParamValue(param) != settings[data.input.name] as BigInteger) || state.pendingConfigure) {
            changes << zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size).format()
            changes << zwave.configurationV1.configurationGet(parameterNumber: param).format()
         }
      }
   }
   if ((getDataValue("zwWakeupInterval") as Integer) != defaultWakeUpInterval || state.pendingRefresh) {\
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