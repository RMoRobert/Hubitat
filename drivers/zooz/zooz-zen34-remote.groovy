/**
 *  Zooz ZEN34 (Remote) community driver for Hubitat
 * 
 *  Copyright 2021-2025 Robert Morris
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
 *  2025-04-05: Implement association support
 *  2025-02-07: Force string type for zwWakeupInterval data value save
 *  2024-04-17: Fix descriptionText logging for digital button events
 *  2021-07-29: Fix for "released" scene to report correct event
 *  2021-04-11: Initial release
 */

 /* =====================================
  *     BUTTON EVENT MAPPING
  * =====================================
  *
  * TOP BUTTON (taps up):
  * =====================================  
  * Action         Button#        Event
  * -------------------------------------
  * Single tap           1        pushed
  * Double tap           3        pushed
  * Triple tap           5        pushed
  * Quadruple tap        7        pushed              Note: Taps up use odd numbers
  * Quintuple tap        9        pushed
  * Hold                 1        held
  * Release (after hold) 1        released
  *
  * BOTTOM BUTTON (taps down): 
  * =====================================
  * Action         Button#        Event
  * ------------------------------------
  * Single tap           2        pushed
  * Double tap           4        pushed
  * Triple tap           6        pushed
  * Quadruple tap        8        pushed               Note: Taps down use even numbers
  * Quintuple tap        10       pushed
  * Hold                 2        held
  * Release (after hold) 2        released
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
   1: [input: [name: "param.1", type: "enum", title: "LED indicator mode",
         options: [[0:"LED always off"],[1:"LED on when button is pressed (default)"],[2:"LED always on in specified upper-paddle color"],[3:"LED always on in specified lower-paddle color"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Upper paddle LED color",
           options: [[0:"White"],[1:"Blue (default)"],[2:"Green"],[3:"Red"],[4:"Magenta"],[5:"Yellow"],[6:"Cyan"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "Lower paddle LED color",
           options: [[0:"White  (default)"],[1:"Blue"],[2:"Green"],[3:"Red"],[4:"Magenta"],[5:"Yellow"],[6:"Cyan"]]],
      size: 1]
]
@Field static final List<Short> associationGroups = [2, 3]

@Field static final String wakeUpInstructions = "To wake the device immediately, tap up 7x."

metadata {
   definition(name: "Zooz ZEN34 Remote", namespace: "RMoRobert", author: "Robert Morris", 
   importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/zooz/zooz-zen34-remote.groovy") {
      capability "Sensor"
      capability "Battery"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "Configuration"
      capability "Refresh"

      command "setAssociationGroup", [[name: "Group Number*",type:"NUMBER", description: "Association group number (consult device manual)"], 
                                 [name: "Z-Wave Node*", type:"STRING", description: "Node number (in hex) to add/remove from group (be sure to remove here if node ever removed from hub!)"], 
                                 [name: "Action*", type:"ENUM", constraints: ["Add", "Remove"]],
                                 [name:"Multi-channel Endpoint", type:"NUMBER", description: "Currently not implemented"]] 

      fingerprint mfr: "027A", prod: "7000", deviceId: "F001", inClusters: "0x5E,0x55,0x9F,0x6C" 
      fingerprint mfr: "027A", prod: "7000", deviceId: "F001", inClusters: "0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x5B,0x9F,0x70,0x84,0x6C,0x7A"
      fingerprint mfr: "0312", deviceId: "F001", prod: "0004", inClusters: "0x5E,0x55,0x9F,0x6C"
      fingerprint mfr: "0312", deviceId: "F001", prod: "0004", inClusters: "0x5E,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x5B,0x26,0x9F,0x70,0x84,0x6C,0x7A"
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

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {    
   if (logEnable) log .debug "CentralSceneNotification: ${cmd}"
   Integer btnNum = 0
   String btnAction = "pushed"
   if (cmd.sceneNumber == 1) {  // Up paddle
      switch(cmd.keyAttributes) {
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_RELEASED:        //1
            btnAction = "released"
            btnNum = 1
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_HELD_DOWN:       //2
            btnAction = "held"
            btnNum = 1
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_1_TIME:  //0
            btnNum = 1
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_2_TIMES: //3
            btnNum = 3
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_3_TIMES: //4
            btnNum = 5
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_4_TIMES: //5
            btnNum = 7
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_5_TIMES: //6
            btnNum = 9
            break
      }
   } else if (cmd.sceneNumber == 2) { // Down paddle
      switch(cmd.keyAttributes) {
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_RELEASED:        //1
            btnAction = "released"
            btnNum = 2
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_HELD_DOWN:       //2
            btnAction = "held"
            btnNum = 2
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_1_TIME:  //0
            btnNum = 2
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_2_TIMES: //3
            btnNum = 4
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_3_TIMES: //4
            btnNum = 6
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_4_TIMES: //5
            btnNum = 8
            break
         case hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_5_TIMES: //6
            btnNum = 10
            break
      }
   }
   else {
      log.debug "Unable to parse: ${cmd}"
   }

   if (btnNum) {
      String descriptionText = "${device.displayName} button ${btnNum} was ${btnAction} (physical)"
      if (txtEnable) log.info(descriptionText)
      sendEvent(name: "${btnAction}", value: btnNum, descriptionText: descriptionText, isStateChange: true, type: "physical")
   }
}

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
   if (state.pendingRefresh) {
      // everything else:
      associationGroups.each { Integer grpNum ->
         cmds << zwave.associationV2.associationGet(groupingIdentifier: grpNum).format()
      }
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
   Integer grpNum = cmd.groupingIdentifier
   if (logEnable) "Group ${grpNum} Association: ${cmd.nodeId}"

   if (grpNum == 1) {
      if (logEnable) log.debug "Association group 1 (lifeline) reported for ${cmd.nodeId}"
   }
   else if (grpNum in associationGroups) {
      String dnis = convertIntListToHexList(cmd.nodeId)?.join(", ") ?: ""
      if (logEnable) log.debug "Association group ${grpNum} node IDs: ${dnis ?: 'none'}"
      if (cmd.nodeId.size() > 0) {
         state."associationGroup${grpNum}NodeIds" = convertIntListToHexList(cmd.nodeId)
      }
      else {
         state.remove("associationGroup${grpNum}NodeIds" as String)
      }
      state.pendingRefresh = true
   }
   else {
      log.warn "Association group ${grpNum} not supported"
      return
   }
}

void zwaveEvent(hubitat.zwave.Command cmd){
    if (logEnable) log.debug "skip: ${cmd}"
}

void setAssociationGroup(Number groupNum, String nodeId, String action, Integer endpoint=null) {
   if (logEnable) log.debug "setAssociationGroup($groupNum, $nodeId, $action)"
   if (logEnable || txtEnable) log.info "Attempting association group changes. Please wake the device within a few seconds before running this command. ${wakeUpInstructions}"
   List<String> cmds = []
   Integer grpNum = groupNum as Integer
   if (grpNum in associationGroups) {
      List<Integer> nodeIds = convertHexListToIntList(nodeId.split(",")).findAll { it > 0 }
      if (nodeIds.size() > 0) {
         if (action?.toLowerCase().trim() == "add") {
            log.trace "grp, node = $grpNum, $nodeIds"
            log.warn zwave.associationV2.associationSet(groupingIdentifier: grpNum, nodeId: nodeIds).format()
            cmds << zwave.associationV2.associationSet(groupingIdentifier: grpNum, nodeId: nodeIds).format()
            if (logEnable) log.debug "Adding node IDs ${nodeIds} to group ${grpNum}: $cmds"
         }
         else if (action?.toLowerCase().trim() == "remove") {
            cmds << zwave.associationV2.associationRemove(groupingIdentifier: grpNum, nodeId: nodeIds).format()
         }
         else {
            log.error "Invalid action in setAssociationGroup: '${action}'"
         }
         cmds << zwave.associationV2.associationGet(groupingIdentifier: grpNum).format()
         sendToDevice(cmds)
      }
      else {
         log.warn "No node ID or invalid format: ${nodeId}"
      }
   }
   else {
      log.warn "Association group ${grpNum} not supported"
   }
}

void push(btnNum) {
   Integer intNum = btnNum.toInteger()
   sendEvent(name: "pushed", value: intNum, isStateChange: true, type: "digital")
   if (txtEnable) log.info "${device.displayName} button $intNum is pushed (digital)"
}

void hold(btnNum) {
   Integer intNum = btnNum.toInteger()
   sendEvent(name: "held", value: intNum, isStateChange: true, type: "digital")
   if (txtEnable) log.info "${device.displayName} button $intNum is pushed (digital)"
}

void release(btnNum) {
   Integer intNum = btnNum.toInteger()
   sendEvent(name: "released", value: intNum, isStateChange: true, type: "digital")
   if (txtEnable) log.info "${device.displayName} button $intNum is pushed (digital)"
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
   sendEvent(name: "numberOfButtons", value: 10)
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

List<String> convertIntListToHexList(List<Integer> intList) {
   List<String> hexList = []
   intList?.each { Integer i ->
      hexList.add(Integer.toHexString(i).padLeft(2, "0").toUpperCase())
   }
   return hexList
   }

List<Integer> convertHexListToIntList(String[] hexList) {
   List<Integer> intList = []
   hexList?.each { String s ->
      try {
         s = s.trim()
         intList.add(Integer.parseInt(s, 16))
      }
      catch (e) {
         if (logEnable) log.warn ("in convertIntListToHexList: $e")
      }
   }
   return intList
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}