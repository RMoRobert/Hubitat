/*
 * ===================== Inovelli 2-Ch Indoor Plug w/ Scenes (NZW37) Driver =====================
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
 *  v1.0    (2021-03-25) - Initial release
 * 
 */

 /**
  *     BUTTON EVENT MAPPING
  * --------------------------------
  *
  * Double tap = button 1 pushed
  *
  */

import groovy.transform.Field

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "LED indicator",
         options: [[0:"On when any on"],[1:"Off when all off"],[2:"Always off"]]],
      size: 1],
   2: [input: [name: "param.10", type: "number", title: "Automtically turn left outlet off after ... seconds (0=disable auto-off; default)", range: 0..32767],
      size: 2],
   3: [input: [name: "param.11", type: "number", title: "Automtically turn rigth outlet off after ... seconds (0=disable auto-off; default)", range: 0..32767],
      size: 2]
]

@Field static final Map commandClassVersions
	[
     0x20: 1, // Basic
     0x25: 1, // Switch Binary
     0x70: 1, // Configuration
     0x98: 1, // Security
     0x60: 3, // Multi Channel
     0x8E: 2, // Multi Channel Association
     0x87: 1, // Indicator
     0x72: 2, // Manufacturer Specific
     0x5B: 1, // Central Scene
     0x85: 2, // Association
     0x86: 2, // Version
    ]
 
metadata {
   definition(name: "Inovelli 2-Channel Plug (NZW37)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Indoor-2Ch-Plug-NZW37.groovy") {
      capability "Actuator"
      capability "Configuration"
      capability "Switch"
      capability "Refresh"
      capability "PushableButton"
      capability "HoldableButton"
      capability "EnergyMeter"
      capability "PowerMeter"

      command "componentOn"
      command "componentOff"
      command "componentRefresh"

      // Uncomment if switching from another driver and need to "clean up" things--will expose command in UI:
      //command "clearChildDevsAndState"

      fingerprint mfr: "015D", prod: "0221", model: "251C"
      fingerprint mfr: "0312", prod: "B221", model: "251C"
      fingerprint deviceId: "0x1001", inClusters: "0x5E,0x85,0x59,0x5A,0x72,0x60,0x8E,0x73,0x27,0x25,0x86"
      fingerprint deviceId: "0x1001", inClusters: "0x5E,0x25,0x27,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x9F,0x60,0x6C,0x7A"
      fingerprint deviceId: "0x1001", inClusters: "0x5E,0x25,0x27,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x60,0x6C"
   }
    
   preferences {
      zwaveParameters.each {
         input it.value.input
      }
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void parse(String description) {
   if (enableDebug) log.debug "parse(description = $description)"
   hubitat.zwave.Command cmd = zwave.parse(description,commandClassVersions)
   if (cmd != null) {
      zwaveEvent(cmd)
   }
   else {
      if (enableDebug) log.warn "Unable to parse: $cmd"
   }
}

private hubitat.zwave.Command encap(hubitat.zwave.Command cmd, Integer endpoint) {
   if (enableDebug) log.debug "encap($cmd, $endpoint)"
   if (endpoint != null) {
      return zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint).encapsulate(cmd)
   }
   else {
      return cmd
   }
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
   if (enableDebug) log.debug "SecurityMessageEncapsulation: $cmd"
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=null) {
   if (enableDebug) log.debug "SupervisionGet: $cmd, ep = $ep"
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) {
        zwaveEvent(encapCmd, ep)
    }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep = null) {
   if (enableDebug) log.debug "BasicReport: $cmd; ep = $ep"
   if (ep != null) {
      com.hubitat.app.ChildDeviceWrapper cd = getChildDevice("${device.id}-${ep}")
      if (cd != null) {
         String onOff = cmd.value ? "on" : "off"
         cd.parse([[name:"switch", value: onOff, descriptionText:"${cd.displayName} was turned $onOff"]])
      }
      else {
         if (enableDebug) log.warn "Unable to find child device for endpoint $ep; ignoring report"
      }
      runIn(2,"checkParentSwitchStatus")
   }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.debug "BasicSet: $cmd"
   List<hubitat.zwave.Command> cmds = []
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
   sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 300), hubitat.device.Protocol.ZWAVE))
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, ep) {
   if (enableDebug) log.debug "BasicSet: $cmd, ep = $ep"
   List<hubitat.zwave.Command> cmds = []
   String value = (cmd.value ? "on" : "off")
   //if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   //sendEvent(name: "switch", value: value)
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
   sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 325), hubitat.device.Protocol.ZWAVE))
}

// Seems like it should work but doesn't seem to always fire with expected endpoint if only one changed...
/*
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, ep) {
   if (enableDebug) log.debug "BasicSet: $cmd, ep = $ep"
   String value = (cmd.value ? "on" : "off")
   com.hubitat.app.ChildDeviceWrapper cd = getChildDevice("${device.id}-${ep}")
   if (enableDesc && device.currentValue("switch") != value) log.info "${cd.displayName} switch is ${value}"
   cd.parse([[name: "switch", value: value, descriptionText: "${cd.displayName} switch is $value"]])
   Boolean allOff = true
   Integer otherEp = (ep as Integer == 1) ? 2 : 1
   com.hubitat.app.ChildDeviceWrapper otherCd = getChildDevice("${device.id}-${otherEp}")
   if (otherCd.currentValue("switch") != "on") allOff = true
   if (allOff && device.currentValue("switch") != "off") {
      if (enableDesc) log.info "${device.displayName} switch is off"
      sendEvent([name: "switch", value: "off"])
   }
   else if (!allOff && device.currentValue("switch") != "on") {
      if (enableDesc) log.info "${device.displayName} switch is on"
      sendEvent([name: "switch", value: "on"])
   }
}
*/

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep = null) {
   if (enableDebug) log.debug "SwitchBinaryReport: $cmd; ep = $ep"
   if (ep != null) {
      com.hubitat.app.ChildDeviceWrapper cd = getChildDevice("${device.id}-${ep}")
      if (cd != null) {
         String onOff = cmd.value ? "on" : "off"
         cd.parse([[name:"switch", value: onOff, descriptionText:"${cd.displayName} was turned $onOff"]])
      }
      if (cmd.value) {
         if (device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
   }
   runIn(2,"checkParentSwitchStatus")
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
   if (enableDebug) log.debug "MultiChannelCmdEnca v3: $cmd; ep = $ep"
   def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
   if (encapsulatedCommand != null) {
      zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
   }
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) {
   if (enableDebug) log.debug "MultiChannelCmdEncap v4: $cmd; ep = $ep"
   def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
   if (encapsulatedCommand != null) {
      zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
   }
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
   if (enableDebug) log.debug "ManufacturerSpecificReport: $cmd"
   String msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
   updateDataValue("MSR", msr)
}

// Not doing anything with this for now; could update relevant device preferences if found
void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
   if (enableDebug) log.debug "ConfigurationReport: $cmd"
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (enableDebug) log.debug "skip: $cmd"
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
	if (enableDebug) log.debug "VersionReport: ${cmd}"
	device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}")
	device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
	device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	if (enableDebug) log.debug "VersionReport: ${cmd}"
   if(cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
      String firmwareVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
      if (enableDesc) log.info "${device.displayName} firmware version is ${firmwareVersion}"
      device.updateDataValue("firmwareVersion", "$firmwareVersion")
   } else if(cmd.firmware0Version != null && cmd.firmware0SubVersion != null) {
      def firmware = "${cmd.firmware0Version}.${cmd.firmware0SubVersion.toString().padLeft(2,'0')}"
      if (enableDesc) log.info "${device.displayName} firmware version is ${firmwareVersion}"
      device.updateDataValue("firmwareVersion", "$firmwareVersion")
   }
}


void createChildDevices() {
   String thisId = device.id as String
   com.hubitat.app.ChildDeviceWrapper leftChild = getChildDevice("${thisId}-1")
   com.hubitat.app.ChildDeviceWrapper rightChild = getChildDevice("${thisId}-2")
   if (!leftChild) {
      leftChild = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-1", [name: "${device.displayName} Left Outlet", isComponent: false])
   }
   if (!rightChild) {
      rightChild = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-2", [name: "${device.displayName} Right Outlet", isComponent: false])
   }
}

String on() {
   if (enableDebug) log.debug "on()"
   return zwaveSecureEncap(zwave.basicV1.basicSet(value: 0xFF))
}

String off() {
   if (enableDebug) log.debug "off()"
   return zwaveSecureEncap(zwave.basicV1.basicSet(value: 0x00))
}

List<String> refresh() {
   if (enableDesc) log.info "refresh()"
   List<String> cmds = []
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
   cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
   return cmds
}

String componentOn(cd) {
   if (enableDebug) log.debug "componentOn($cd)"
   String cmd = ""
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = zwaveSecureEncap(encap(zwave.basicV1.basicSet(value: 0xFF), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = zwaveSecureEncap(encap(zwave.basicV1.basicSet(value: 0xFF), 2))
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

String componentOff(cd) {
   if (enableDebug) log.debug "componentOff($cd)"
   String cmd = ""
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = zwaveSecureEncap(encap(zwave.basicV1.basicSet(value: 0x00), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = zwaveSecureEncap(encap(zwave.basicV1.basicSet(value: 0x00), 2))
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

String componentRefresh(cd) {
   if (enableDebug) log.debug "componentRefresh($cd)"   
   String cmd = ""
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
   }
   return cmd
}

List<String> configure() {
   log.warn "configure()"
   return initialize()
}

List<String> installed() {
   log.info "installed()..."
   return initialize()
}

// Apply preferences changes, including updating parameters
List<String> updated() {
   log.info "updated()"
   log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
   log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
   if (enableDebug) {
      log.debug "Debug logging will be automatically disabled in 30 minutes..."
      runIn(1800, logsOff)
   }
   try {
      createChildDevices()
   }
   catch (Exception ex) {
      log.warn "Could not create child devices: $ex"
   }
   return initialize()
}

List<String> initialize() {
   List<String> cmds = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size)))
      }
   }
   sendEvent(name: "numberOfButtons", value: 1)
   cmds << zwaveSecureEncap(zwave.versionV1.versionGet())
   cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
   return delayBetween(cmds, 250)
}

void logsOff() {
   log.warn "Disabling debug logging after timeout"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd, ep=null) {
   if (enableDebug) log.debug "CentralSceneNotification: $cmd, ep = $ep"
   String buttonAction = "pushed"
   Integer buttonNumber = 0
   if (cmd.sceneNumber == 1 || cmd.sceneNumber == 2) {
      if (cmd.keyAttributes == 1 || cmd.keyAttributes == 2) {
         buttonAction = cmd.keyAttributes == 2 ? "released" : "held"
         buttonNumber = 1
      }
      else {
         if (cmd.keyAttributes == 0) {
            buttonNumber = 1
         }
         else {
            buttonNumber = cmd.keyAttributes + Math.abs(3 - cmd.keyAttributes)
         }
      }
      if (cmd.sceneNumber == 1) buttonNumber += 1
   }
   else {
      // Fan and light "dimmer" rockers
      if (cmd.sceneNumber == 3) buttonNumber = 11
      else if (cmd.sceneNumber == 4) buttonNumber = 13
      else if (cmd.sceneNumber == 5) buttonNumber = 12
      else if (cmd.sceneNumber == 6) buttonNumber = 14
   }
   buttonEvent(buttonNumber, buttonAction, "phyiscal")
}

void buttonEvent(buttonNumber, buttonAction, type = "digital") {
   sendEvent(name: buttonAction, value: buttonNumber, isStateChange: true, type: type)
   if (enableDesc) log.info "Button $buttonNumber was $buttonAction ($type)"
}

String setConfigParameter(number, value, size) {
   return setParameter(number, value, size)
}

String setParameter(number, value, size) {
   if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)a"
   return zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number.toInteger(), size: size.toInteger()))
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}

// Checks state of both child endpoint and adjusts parent accordingly
void checkParentSwitchStatus() {
   Boolean allOff = false
   if (getChildDevice("${device.id}-1")?.currentValue("switch") == "off" &&
       getChildDevice("${device.id}-2")?.currentValue("switch") == "off") {
          allOff = true
       }
   if (allOff && device.currentValue("switch") != "off") {
      if (enableDesc) log.info "${device.displayName} switch is off"
      sendEvent([name: "switch", value: "off"])
   }
   else if (!allOff && device.currentValue("switch") != "on") {
      if (enableDesc) log.info "${device.displayName} switch is on"
      sendEvent([name: "switch", value: "on"])
   }
}