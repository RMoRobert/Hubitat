
/*
 * ==============  Neo Coolcam Z-Wave 700 Motion Sensor Driver ================
 *
 *  Hubitat Elevation custom driver for:
 *  Neo Coolcam Z-Wave 700 Motion/5-in-1 Sensor (may also work with Haozee)
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
 * Changelog:
 * 2024-05-28: Update fingerprint
 * 2022-12-31: Initial release
 *
 */

import groovy.transform.Field

@Field static final Integer defaultWakeUpInterval = 43200
@Field static final String wakeUpInstructions = "To wake the device immediately, tap the Z-Wave button once."

@Field static final Map<Short,Short> commandClassVersions = 
[
   0x30: 2,  // Sensor Binary
   0x31: 5, // Sensor Multilevel (11)
   0x59: 3,  // Association Group Info
   0x5A: 1,  // Device Reset Locally
   0x6C: 1,  // Supervision
   0x70: 1,  // Configuration (4)
   0x71: 3,  // Notification (8)
   0x72: 2,  // Manufacturer Specific
   0x73: 1,  // Powerlevel
   0x7A: 5,  // Firmware Update MD
   0x80: 1,  // Battery
   0x84: 2,  // Wakeup
   0x85: 2,  // Association
   0x86: 3,  // Version
   0x87: 3,  // Indicator
   0x8E: 3,  // Multi-channel association
]

@Field static final Map<Short,Map> zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "LED indicator",
       options: [[0: "Never"], [1: "Blink on motion event [DEFAULT]"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Motion detection",
       options: [[0: "Disabled"], [1: "Enabled [DEFAULT]"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "Motion reporting behavior",
       options: [[0: "Send every time motion is detected"], [1: "Send once until motion cleared [DEFAULT]"]]],
      size: 1],
   // skip param 4, only for associated devices
   5: [input: [name: "param.5", type: "enum", title: "Sensor Binary reports on motion",
       options: [[0: "Disabled; Basic Report only [DEFAULT]"], [1: "Enabled"]]],
      size: 1],
   6: [input: [name: "param.6", type: "enum", title: "Motion sensitivity",
       options: [[0: "0 - High"], [1: "1"],[2: "2 [DEFAULT]"], [3: "3"], [4: "4"],
         [5: "5"],[6: "6"],[7: "7 - Medium"],[8: "8"],[9: "9"],[10: "10"],[11: "11"],[12: "12"],[13: "13"],[14: "14 - Low"]]],
      size: 1],
   7: [input: [name: "param.7", type: "number", title: "Temperature offset, 1/10ths of degree (e.g., 5 will add 0.5°)",
       range: -120..120],
      size: 1],
   8: [input: [name: "param.8", type: "number", title: "Humidity offset, 1/10ths of % (e.g., -5 will subtract 0.5%)",
       range: -120..120],
      size: 1],
   9: [input: [name: "param.9", type: "enum", title: "Temperature reporting threshold",
       options: [[1: "0.1°"],[2: "0.2°]"], [3: "0.3°"], [4: "0.4°"],
         [5: "0.5°"],[6: "0.6°"],[7: "0.7°"],[8: "0.8°"],[9: "0.9°"],[10: "1.0° [DEFAULT]"],[12: "1.2°"],[15:"1.5°"],
         [17:"1.7°"],[20:"2.0°"],[25:"2.5°"],[30:"3.0°"],[40:"4.0°"],[50:"5.0°"],[60:"6.0°"],[70:"7.0°"],[80:"8.0°"],[90:"9.0°"],
         [100:"10.0°"]]],
      size: 1],
   10: [input: [name: "param.10", type: "enum", title: "Humidity reporting threshold",
       options: [[1: "0.1%"],[2: "0.2%]"], [3: "0.3%"], [4: "0.4%"],
         [5: "0.5%"],[6: "0.6%"],[7: "0.7%"],[8: "0.8%"],[9: "0.9%"],[10: "1.0%"],[12: "1.2%"],[15:"1.5%"],
         [17:"1.7%"],[20:"2.0% [DEFAULT]"],[25:"2.5%"],[30:"3.0%"],[40:"4.0%"],[50:"5.0%"],[60:"6.0%"],[70:"7.0%"],[80:"8.0%"],[90:"9.0%"],
         [100:"10.0%"]]],
      size: 1],
   11: [input: [name: "param.11", type: "enum", title: "Lux reporting threshold",
       options: [[1: "1 lux"],[2: "2 lux]"], [3: "3 lux"], [4: "4 lux"],
         [5: "5 lux"],[6: "6 lux"],[7: "7 lux"],[10: "10 lux"],[15: "15 lux"],[20:"20 lux [DEFAULT]"],
         [25:"25 lux"],[30:"30 lux"],[40:"40 lux"],[50:"50 lux [DEFAULT]"],[60:"60 lux"],[70:"70 lux"],
         [80:"80 lux"],[90:"90 lux"],[100:"100 lux"],[110:"110 lux"],[120:"120 lux"]]],
      size: 1],
   // skip param 12, only for associated devices
   13: [input: [name: "param.13", type: "enum", title: "Motion blind time (minimum interval between motion active reports)",
        options: [[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[6:"6 seconds"],
        [7:"7 seconds"],[8:"8 seconds [DEFAULT]"]]],
      size: 1],
   // skip param 14, only for associated devices
   15: [input: [name: "param.15", type: "enum", title: "Motion clear time (inactive after...)",
        options: [[1:"1 second"],[5:"5 seconds"],[10:"10 seconds"],[15:"15 seconds"],[20:"20 seconds"],[30:"30 seconds [DEFAULT]"],
        [45:"45 seconds"],[60:"1 minute"],[90:"1.5 minutes"],[120:"2 minutes"],[180:"3 minutes"],[240:"4 minutes"],
        [300:"5 minutes"],[420:"7 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1800:"30 minutes"],[3600:"1 hour"],
        [30000:"8.33 hours"]]],
      size: 2],
   // skip param 16, only for associated devices
   // Manual lists next to parameters but haven't seen this on any devices...
   17: [input: [name: "param.17", type: "enum", title: "Lux, temperature, and humidity reporting interval (may not be supported on all)",
        options: [[0:"Disabled"],[10:"10 seconds [default with USB]"],[20:"20 seconds"],[30:"30 seconds"],
        [45:"45 seconds"],[60:"1 minute"],[90:"1.5 minutes"],[120:"2 minutes"],[180:"3 minutes [DEFAULT]"],
        [300:"5 minutes"],[420:"7 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1800:"30 minutes"],[2700:"45 minutes"],
        [3600:"1 hour"],[7200:"2 hours"],[30000:"8.33 hours"]]],
      size: 2],
   18: [input: [name: "param.18", type: "number", title: "Lux offset calibration (1-32767, default 5320; consult manual; may not be supported on all)",
       range: 1..32767],
      size: 1],
]

metadata {
   definition(name: "Neo Coolcam Z-Wave 700 Motion Sensor",
   namespace: "RMoRobert",
   author: "Robert Morris",
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/neo-coolcam-700-motion.groovy") {

      capability "Actuator"
      capability "Battery"
      capability "IlluminanceMeasurement"
      capability "MotionSensor"
      capability "RelativeHumidityMeasurement"
      capability "Sensor"
      capability "TamperAlert"
      capability "TemperatureMeasurement"
      capability "Configuration"
      capability "Refresh"

      fingerprint mfr:"0258", prod:"0020", deviceId:"0720", inClusters:"0x5E,0x98,0x9F,0x6C,0x55", controllerType: "ZWV"
      fingerprint mfr:"0258", prod:"0020", deviceId:"0720", inClusters:"0x5E,0x98,0x9F,0x6C,0x55,0x86,0x73,0x85,0x8E,0x59,0x72,0x5A,0x87,0x71,0x30,0x31,0x70,0x7A", controllerType: "ZWV"
      fingerprint mfr:"0258", prod:"0020", deviceId:"0718", inClusters:"0x5E,0x98,0x9F,0x6C,0x55", controllerType: "ZWV" // EU?
   }

   preferences {
      zwaveParameters.each {
         input it.value.input
      }

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

void sendToDevice(String cmd) {
   if (logEnable) log.debug "sendToDevice(String $cmd)"
   sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<String> cmds, Long delay=300) {
   if (logEnable) log.debug "commands($cmds, $delay)"
   if (!(cmds?.size > 0)) return []
   return delayBetween(cmds.collect{ it.startsWith("delay ") ? it : zwaveSecureEncap(it) }, delay)
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

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
   if (logEnable) log.debug "SupervisionGet: $cmd"
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
   else {
      if (logEnable) log.debug "Unable to de-encapsulate command from $cmd"
   }
   // Is this necessary (or effective) on sleep devices?
   /*
   sendHubCommand(new hubitat.device.HubAction(
      zwaveSecureEncap(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID,
         reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)),
         hubitat.device.Protocol.ZWAVE)
   )
   */
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
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
   if (cmd.notificationType == hubitat.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_BURGLAR
      && (cmd.event == 3 || cmd.eventParameter[0] == 3)) {
      if (cmd.event) {
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
            String unit = cmd.scale ? "F" : "C"
            String strTemp = convertTemperatureIfNeeded(cmd.scaledSensorValue, unit, cmd.precision)
            BigDecimal temp = new BigDecimal(strTemp)
            String descText = "${device.displayName} temperature is ${temp} °${location.temperatureScale}"
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
         Integer val = Math.round(cmd.scaledSensorValue)
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

   //cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
  
   state.pendingConfigure = false
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
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         Short paramNumber = param as Short
         Short paramSize = data.size as Short
         BigInteger paramValue = settings[data.input.name] as BigInteger
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