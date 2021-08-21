/**
 *  Zooz ZSE41 Contact Sensor (XS Open/Close Sensor) community driver for Hubitat
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
 *  2021-08-19: Initial release
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
   1: [input: [name: "param.1", type: "enum", title: "LED indicator on open/close changes",
           options: [[0:"Do not blink on change"],[1:"Blink on change (default)"]]], size: 1],
   5: [input: [name: "param.5", type: "enum", title: "Reverse open/close reporting?",
           options: [[0:"No; closed when magnet near sensor (default)"],[1:"Yes; open when magnet near sensor"]]], size: 1],
]

@Field static final String wakeUpInstructions = "To wake the device immediately, tap the Z-Wave button (inside the device) 4 times."

metadata {
   definition(name: "Zooz ZSE41 Contact Sensor", namespace: "RMoRobert", author: "Robert Morris", 
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zse41-contact-sensor.groovy") {
      capability "Sensor"
      capability "Battery"
      capability "ContactSensor"
      capability "Configuration"
      capability "Refresh"

      fingerprint  mfr:"027A", prod:"7000", deviceId:"E001", inClusters:"0x5E,0x55,0x9F,0x6C" 
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
   String descText = "${device.displayName} battery level is ${lvl}%"
   if (txtEnable) log.info(descText)
   sendEvent(name:"battery", value: lvl, unit:"%", descriptionText: descText, isStateChange: true)
}

// Skipping since is handled by NotificationReport, but leaving in case any firmware updates, etc. change this...
/*
void zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
   if (logEnable) log.debug "SensorBinaryReport: $cmd"
   if (cmd.sensorType == hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport.SENSOR_TYPE_DOOR_WINDOW) {
      if (cmd.sensorValue) {
         String descText = "${device.displayName} contact is open"
         sendEvent(name: "contact", value: "open", descriptionText: descText)
         if (txtEnable) log.info descText
      }
      else {
         String descText = "${device.displayName} contact is closed"
         sendEvent(name: "contact", value: "closed", descriptionText: descText)
         if (txtEnable) log.info descText
      }
   }
   else {
      if (logEnable) log.debug "ignoring unexpected type of SensorBinaryReport: $cmd"
   }
}
*/

void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
   if (logEnable) log.debug "NotificationReport: $cmd"
   if (cmd.notificationType == hubitat.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_ACCESS_CONTROL) {
      if (cmd.event == 0x16) {
         String descText = "${device.displayName} contact is open"
         sendEvent(name: "contact", value: "open", descriptionText: descText)
         if (txtEnable) log.info descText
      }
      else if (cmd.event == 0x17) {
         String descText = "${device.displayName} contact is closed"
         sendEvent(name: "contact", value: "closed", descriptionText: descText)
         if (txtEnable) log.info descText
      }
      else {
         if (logEnable) log.debug "ignoring unexpected event for Noticiation Report, type Access Control: ${cmd.event}"
      }
   }
   else {
      if (logEnable) log.debug "ignoring unexpected type of NotificationReport: $cmd"
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
   if (logEnable) log.debug "Device will fetch new data when the device wakes up. ${wakeUpInstructions}"

}

void configure() {
   log.debug "configure()"
   if (!(state.initialized)) {
      log.debug "running configure commands now"
      List<String> cmds = getPendingConfigureAndRefreshCommands()
      state.pendingRefresh = false
      state.pendingConfigure = false
      state.initialized = true
      sendToDevice(cmds, 400)
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
   if (getPendingConfigurationChanges()) {
      log.debug "Pending configuration changes will be applied when the device wakes up. ${wakeUpInstructions}"
   }
}

List<String> getPendingConfigureAndRefreshCommands(Boolean refreshOnly=false) {
   List<String> cmds = []

   // refresh (or initial fetch if needed)
   if (state.pendingRefresh || device.currentValue("battery") == null) {
      cmds << zwave.batteryV1.batteryGet().format()
   }
   if (state.pendingRefresh || device.currentValue("contact") == null) {
      cmds << zwave.notificationV3.notificationGet(notificationType: hubitat.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_ACCESS_CONTROL).format()
   }
   if (state.pendingRefresh || !getDataValue("firmwareVersion")) {
      cmds  << zwave.versionV2.versionGet().format()
      //cmds << zwave.manufacturerSpecificV2.deviceSpecificGet().format()
      cmds << zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 2).format()
   }
   if (state.pendingRefresh || !(getDataValue("zwWakeupInterval"))) {
      cmds << zwave.wakeUpV2.wakeUpIntervalGet().format()
   }
   if (state.pendingRefresh || device.currentValue("contact") == null) {
      cmds << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport.SENSOR_TYPE_DOOR_WINDOW).format()
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
   if ((getDataValue("zwWakeupInterval") && ((getDataValue("zwWakeupInterval") as Integer) != defaultWakeUpInterval)) || state.pendingRefresh) {
      changes << zwave.wakeUpV2.wakeUpIntervalSet(seconds: defaultWakeUpInterval, nodeid: zwaveHubNodeId ?: 1).format()
   }
   if (logEnable) "getPendingConfigurationChanges: $changes"
   return changes
}