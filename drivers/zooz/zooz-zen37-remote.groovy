/**
 *  Zooz ZEN37 (4-Button Remote) community driver for Hubitat
 * 
 *  Copyright 2023 Robert Morris
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
 *  2023-12-12: Initial release
 *
 * BUTTON NUMBER/EVENT MAPPING:
 * 
 * "Base" button number:
 *   - Top button (large)          = button 1
 *   - Middle button (large)       = button 2
 *   - Bottom left button (small)  = button 3 
 *   - Bottom right button (small) = button 4 
 * Single taps, hold, and release:
 *  - base button number pushed, held, and released events
 * Multi-taps:
 *  - mathematically, a pushed event for button number = (base button number) + (4 * (number of taps - 1))
      ... or specifically:
 *     * "button 1" events: button 1, 5, 9, 13, or 17 pushed (taps 1-5)
 *     * "button 2" events: button 2, 6, 10, 14, or 18 pushed (taps 1-5)
 *     * "button 3" events: button 3, 7, 11, 15, or 19 pushed (taps 1-5)
 *     * "button 4" events: button 4, 8, 12, 16, or 20 pushed (taps 1-5)
  *
  */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x20: 1,    // Basic
   0x26: 3,    // SwitchMultilevel
   0x55: 1,    // TransportService
   0x59: 1,    // AssociationGrpInfo
   0x5A: 1,    // DeviceResetLocally
   0x5B: 3,    // CentralScene
   0x5E: 2,    // ZwaveplusInfo
   0x6C: 1,    // Supervision
   0x70: 1,    // Configuration
   0x72: 2,    // ManufacturerSpecific
   0x73: 1,    // Powerlevel
   0x7A: 2,    // Firmware Update Md
   0x80: 1,    // Battery
   0x84: 2,    // WakeUp
   0x85: 2,    // Association
   0x86: 2,    // Version
   0x8E: 2,    // MultiChannelAssociation
   0x9F: 1     // Security S2
]

@Field static final Integer defaultWakeUpInterval = 43200

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "Low battery threshold",
         options: [[5:"5% [DEFAULT]"],[6:"6%"],[7:"7%"],[8:"8%"],[9:"9%"],[10:"10%"],]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "LED color for button 1 actions",
           options: [[0:"White [DEFAULT]"],[1:"Purple"],[2:"Orange"],[3:"Cyan"],[4:"Red"],[5:"Green"],[6:"Blue"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "LED color for button 2 actions",
           options: [[0:"White [DEFAULT]"],[1:"Purple"],[2:"Orange"],[3:"Cyan"],[4:"Red"],[5:"Green"],[6:"Blue"]]],
      size: 1],
   4: [input: [name: "param.4", type: "enum", title: "LED color for button 3 actions",
           options: [[0:"White [DEFAULT]"],[1:"Purple"],[2:"Orange"],[3:"Cyan"],[4:"Red"],[5:"Green"],[6:"Blue"]]],
      size: 1],
   5: [input: [name: "param.5", type: "enum", title: "LED color for button 4 actions",
           options: [[0:"White [DEFAULT]"],[1:"Purple"],[2:"Orange"],[3:"Cyan"],[4:"Red"],[5:"Green"],[6:"Blue"]]],
      size: 1],
   6: [input: [name: "param.6", type: "enum", title: "LED brightness",
           options: [[0:"Off"],[1:"10%"],[2:"20%"],[3:"30%"],[4:"40%"],[5:"50% [DEFAULT]"],[6:"60%"],
                     [7:"70%"],[8:"80%"],[9:"90%"],[10:"100%"]]],
      size: 1] //,
   // 7: [input: [name: "param.7", type: "number", title: "Time from 0-100% for Group 4 and 5 Assosication devices (1-99 in seconds)",
   //         range: 1..99],
   //    size: 1]
]

//@Field static final Map<Short,String> associationGroups = [2:"associationGroupTwo", 3:"associationGroupThree"]

@Field static final String wakeUpInstructions = "To wake the device immediately, tap button 3 (bottom left button) 6x."

metadata {
   definition(name: "Zooz ZEN37 Remote", namespace: "RMoRobert", author: "Robert Morris", 
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zen37-remote.groovy") {
      capability "Sensor"
      capability "Battery"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "Configuration"
      capability "Refresh"

      // command "setAssociationGroup", [[name: "Group Number*",type:"NUMBER", description: "Association group number (consult device manual)"], 
      //                            [name: "Z-Wave Node*", type:"STRING", description: "Node number (in hex) to add/remove from group"], 
      //                            [name: "Action*", type:"ENUM", constraints: ["Add", "Remove"]],
      //                            [name:"Multi-channel Endpoint", type:"NUMBER", description: "Currently not implemented"]] 

      fingerprint mfr:"027A", prod:"7000", deviceId:"F003", inClusters:"0x5E,0x9F,0x55,0x6C"
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
	device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
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

void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {    
   if (enableDebug) log.debug "CentralSceneNotification: ${cmd}"
   Integer btnBaseNum = cmd.sceneNumber ?: 0
   Integer btnNum = btnBaseNum
   String btnAction = "pushed"
   if (cmd.keyAttributes as Integer == 2) btnAction = "held"
   else if (cmd.keyAttributes as Integer == 1) btnAction = "released"
   if ((cmd.keyAttributes as Integer) >= 3) {
      btnNum = btnBaseNum + (4 * ((cmd.keyAttributes as Integer) - 2))
   }

   if (btnNum) {
      String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
      if (enableDesc) log.info "${descriptionText}"
      sendEvent(name: "${btnAction}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")
   }
}

// void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {    
//    if (logEnable) log.debug "CentralSceneNotification: ${cmd}"
//    Integer btnNum = 0
//    String btnAction = "pushed"
//    if (cmd.sceneNumber == 1) {  // Up paddle
//       switch(cmd.keyAttributes) {
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_RELEASED:        //1
//             btnAction = "released"
//             btnNum = 1
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_HELD_DOWN:       //2
//             btnAction = "held"
//             btnNum = 1
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_1_TIME:  //0
//             btnNum = 1
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_2_TIMES: //3
//             btnNum = 3
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_3_TIMES: //4
//             btnNum = 5
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_4_TIMES: //5
//             btnNum = 7
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_5_TIMES: //6
//             btnNum = 9
//             break
//       }
//    } else if (cmd.sceneNumber == 2) { // Down paddle
//       switch(cmd.keyAttributes) {
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_RELEASED:        //1
//             btnAction = "released"
//             btnNum = 2
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_HELD_DOWN:       //2
//             btnAction = "held"
//             btnNum = 2
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_1_TIME:  //0
//             btnNum = 2
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_2_TIMES: //3
//             btnNum = 4
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_3_TIMES: //4
//             btnNum = 6
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_4_TIMES: //5
//             btnNum = 8
//             break
//          case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_5_TIMES: //6
//             btnNum = 10
//             break
//       }
//    }
//    else {
//       log.debug "Unable to parse: ${cmd}"
//    }

//    if (btnNum) {
//       String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction}"
//       if (txtEnable) log.info(descriptionText)
//       sendEvent(name: "${btnAction}", value: btnNum, descriptionText: descriptionText, isStateChange: true, type: "physical")
//    }
// }

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
   if (logEnable) log.debug "WakeUpIntervalReport: $cmd"
   updateDataValue("zwWakeupInterval", "${cmd.seconds}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
   if (logEnable) log.debug "WakeUpNotification: $cmd"
   if (txtEnable) log.info "${device.displayName} woke up"
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
   state.pendingRefresh = false

   // configure (or initial set if needed)
   if (getPendingConfigurationChanges()) {
      cmds += getPendingConfigurationChanges()
   }
   state.pendingConfigure = false

   state.initialized = true

   cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()

   sendToDevice(cmds, 500)
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
   if (logEnable) log.debug "AssociationReport: $cmd"
   if (logEnable) "Group ${cmd.groupingIdentifier} Association: ${cmd.nodeId}"

   String name = associationGroups[cmd.groupingIdentifier]
   if (name) {
      state["${name}NodeIds"] = cmd.nodeId
      //List<String> dnis = convertIntListToHexList(cmd.nodeId)?.join(", ") ?: ""
      //sendEventIfNew(name, (dnis ?: "none"))
   }
}

void zwaveEvent(hubitat.zwave.Command cmd){
    if (logEnable) log.debug "skip: ${cmd}"
}

void push(Number btnNum) {
   sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
}

void hold(Number btnNum) {
   sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
}

void release(Number btnNum) {
   sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
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
   sendEvent(name: "numberOfButtons", value: 20)
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
   if (getPendingConfigurationChanges()) {
      log.debug "Pending configuration changes will be applied when the device wakes up. ${wakeUpInstructions}"
   }
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
   if ((getDataValue("zwWakeupInterval") as Integer) != defaultWakeUpInterval || state.pendingRefresh) {
      changes << zwave.wakeUpV2.wakeUpIntervalSet(seconds: defaultWakeUpInterval, nodeid: zwaveHubNodeId ?: 1).format()
   }
   if (logEnable) "getPendingConfigurationChanges: $changes"
   return changes
}

// List<String> convertIntListToHexList(List<Integer> intList) {
//    List<String> hexList = []
//    intList?.each { Integer i ->
//       hexList.add(Integer.toHexString(i).padLeft(2, "0").toUpperCase())
//    }
//    return hexList
//    }

// List<Integer> convertHexListToIntList(String[] hexList) {
//    List<Integer> intList = []
//    hexList?.each { String s ->
//       try {
//          s = s.trim()
//          intList.add(Integer.parseInt(s, 16))
//       }
//       catch (e) {
//          if (logEnable) log.warn ("in convertIntListToHexList: $e")
//       }
//    }
//    return intList
// }