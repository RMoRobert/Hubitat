/*
 * ===================== Advanced Inovelli Red Series Fan + Light Switch (LZW36) Driver =====================
 *
 *  Copyright 2020 Robert Morris
 *  Portions based on code from Inovelli and Hubitat
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
 *  v1.0    (2020-11-14) - Initial release
 * 
 */

 /**
  *     BUTTON EVENT MAPPING
  * --------------------------------
  *
  * LIGHT BUTTONS (TOP):  
  * Action         Button#  Event
  * -----------------------------
  * Single tap           1        pushed
  * Double tap           3        pushed
  * Triple tap           5        pushed
  * Quadruple tap        7        pushed              Note: Light (top) portion uses odd
  * Quintuple tap        9        pushed                    button numbers
  * Hold                 1        held
  * Release (after hold) 1        released
  * Dimmer tap up        11       pushed
  * Dimmer tap down      13       pushed
  *
  * FAN BUTTONS (BOTTOM):  
  * Action         Button#  Event
  * -----------------------------
  * Single tap           2        pushed
  * Double tap           4        pushed
  * Triple tap           6        pushed
  * Quadruple tap        8        pushed               Note: Fan (bottom) portion uses even
  * Quintuple tap        10       pushed                     button numbers
  * Hold                 2        held
  * Release (after hold) 2        released
  * Dimmer tap up        12       pushed
  * Dimmer tap down      14       pushed
  *
  */

import groovy.transform.Field

@Field static Map colorNameMap = [
   "red": 1,
   "red-orange": 4,
   "orange": 21,
   "yellow": 42,
   "chartreuse": 60,
   "green": 85,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "blue": 170,
   "violet": 212,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

@Field static Map effectNameMap = ["off": 0, "solid": 1, "chase": 2, "fast blink": 3, "slow blink": 4, "pulse": 5]

@Field static final Map zwaveParameters = [
   1: [input: [name: "param.1", type: "enum", title: "Dimming rate for light from hub",
         options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds (default)"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]]],
      size: 1],
   2: [input: [name: "param.2", type: "enum", title: "Dimming rate for light from switch",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[99:"Match rate from hub (default)"]]],
      size: 1],
   3: [input: [name: "param.3", type: "enum", title: "On/off fade time for light from paddle",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[99:"Match dimming rate from hub (default)"]]],
      size: 1],
   4: [input: [name: "param.4", type: "enum", title: "On/off fade time for light from hub",
           options: [[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[99:"Match dimming rate from hub (default)"]]],
      size: 1],
   5: [input: [name: "param.5", type: "number", title: "Minimum light dimmer level (default=1)", range: 1..45],
      size: 1],
   6: [input: [name: "param.6", type: "number", title: "Maximum light dimmer level (default=99)", range: 55..99],
      size: 1],
   7: [input: [name: "param.7", type: "number", title: "Minimum fan level (default=1)", range: 1..45],
      size: 1],
   8: [input: [name: "param.8", type: "number", title: "Maximum fan level (default=99)", range: 55..99],
      size: 1],
   10: [input: [name: "param.10", type: "number", title: "Automtically turn light off after ... seconds (0=disable auto-off; default)", range: 0..32767],
      size: 2],
   11: [input: [name: "param.11", type: "number", title: "Automtically turn fan off after ... seconds (0=disable auto-off; default)", range: 0..32767],
      size: 2],
   12: [input: [name: "param.12", type: "number", title: "Default light level for physical \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   13: [input: [name: "param.13", type: "number", title: "Default light level for digital \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   14: [input: [name: "param.14", type: "number", title: "Default fan level for physical \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   15: [input: [name: "param.15", type: "number", title: "Default fan level for digital \"on\" (0=previous; default)", range: 0..99],
      size: 1],
   16: [input: [name: "param.16", type: "enum", title: "State for light on power restore",
        options: [[0:"Off"],[25:"On to 25%"],[33:"On to 33%"],[50:"On to 50%"],[66:"On to 66%"],[75:"On to 75%"],[99:"On to full brightness"],[100:"Previous state (default)"]]],
      size: 1],
   17: [input: [name: "param.17", type: "enum", title: "State for fan on power restore",
        options: [[0:"Off (default)"],[25:"On to 25%"],[50:"On to 50%"],[75:"On to 75%"],[99:"On to full speed"],[100:"Previous state (default)"]]],
      size: 1],
   26: [input: [name: "param.26", type: "enum", title: "If light LED bar disabled, temporarily light for ... seconds after dimmer adjustments",
        options: [[0:"Do not show"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds (default)"],[4:"4 seconds"],[5:"5 seconds"],[6:"6 seconds"],[7:"7 seconds"],[8:"8 seconds"],[9:"9 seconds"],[10:"10 seconds"]]],
      size: 1],
   27: [input: [name: "param.27", type: "enum", title: "If fan LED bar disabled, temporarily light for ... seconds after dimmer adjustments",
        options: [[0:"Do not show"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds (default)"],[4:"4 seconds"],[5:"5 seconds"],[6:"6 seconds"],[7:"7 seconds"],[8:"8 seconds"],[9:"9 seconds"],[10:"10 seconds"]]],
      size: 1],
   28: [input: [name: "param.28", type: "enum", title: "Send new power report when power changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   30: [input: [name: "param.30", type: "enum", title: "Send new energy report when energy changes by",
        options: [[0:"Disabled"],[5:"5%"],[10:"10% (default)"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],[80:"80%"],[90:"90%"],[100:"100%"]]],
      size: 1],
   29: [input: [name: "param.29", type: "enum", title: "Send power and energy reports every",
        options: [[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],[1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour (default)"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],[32400: "9 hours"]]],
      size: 2],
   51: [input: [name: "param.51", type: "enum", title: "Disable physical on/off delay", options: [[1:"No (default)"],[0:"Yes (also disables multi-taps)"]]],
      size: 1]
]

@Field static final Map commandClassVersions =
	[
     0x20: 1, // Basic
     0x25: 1, // Switch Binary
     0x70: 1, // Configuration
     0x98: 1, // Security
     0x60: 3, // Multi Channel
     0x8E: 2, // Multi Channel Association
     0x26: 3, // Switch Multilevel
     0x87: 1, // Indicator
     0x72: 2, // Manufacturer Specific
     0x5B: 1, // Central Scene
     0x32: 3, // Meter
     0x85: 2, // Association
     0x86: 1, // Version
     0x75: 2  // Protection
    ]
 
metadata {
   definition(name: "Advanced Inovelli Fan + Light Switch (LZW36)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Fan-Light-LZW36-Advanced.groovy") {      
      capability "Actuator"
      capability "Configuration"
      capability "Switch"
      capability "SwitchLevel"
      capability "Refresh"
      capability "Sensor"
      capability "PushableButton"
      capability "HoldableButton"
      capability "ReleasableButton"
      capability "EnergyMeter"
      capability "PowerMeter"

      command "componentOn"
      command "componentOff"
      command "componentRefresh"
      command "componentSetLevel"
      command "componentStartLevelChange"
      command "componentStopLevelChange"
      command "componentSetSpeed"
      command "componentCycleSpeed"

      command "reset"
      
      command "setFanIndicator", [[name: "Notification Value*", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]]
      command "setFanIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]
      command "setLightIndicator", [[name: "Notification Value*", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]]
      command "setLightIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]
      command "setFanLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli format, 0-10"]]
      command "setFanLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]
      command "setLightLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli format, 0-10"]]
      command "setLightLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]
      command "setFanOnLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
      command "setFanOffLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
      command "setLightOnLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
      command "setLightOffLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]

      // Uncomment if switching from another driver and need to "clean up" things--will expose command in UI:
      //command "clearChildDevsAndState"

      fingerprint mfr: "031E", prod: "000E", deviceId: "0001", inClusters:"0x5E,0x55,0x98,0x9F,0x22,0x6C"
      fingerprint mfr: "031E", prod: "000E", deviceId: "0001", inClusters:"0x5E,0x55,0x98,0x9F,0x6C,0x26,0x70,0x85,0x59,0x8E,0x86,0x72,0x5A,0x73,0x75,0x22,0x7A,0x5B,0x87,0x60,0x32" 
   }
    
   preferences {
      zwaveParameters.each {
         input it.value.input
      }      
      input name: "disableLocal", type: "bool", title: "Disable local control (at wall/switch)"
      input name: "disableRemote", type: "bool", title: "Disable remote control (from Z-Wave/hub)"
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }
}

void parse(String description) {
    if (enableDebug) log.debug "parse description: ${description}"
    hubitat.zwave.Command cmd = zwave.parse(description,commandClassVersions)
    if (description.startsWith("Err 106")) {
      log.warn "The device failed to include securely. Remove and re-pair to pair securely or if unable to control."
    }
    else if (description != "updated") {
      if (cmd != null) {
         zwaveEvent(cmd)
      }
      else {
         log.warn "Unable to parse: $cmd"
      }
    }
    else {
      if (enableDebug) log.debug "skipping parse for: $description"
    }
}

private hubitat.zwave.Command encap(hubitat.zwave.Command cmd, Integer endpoint) {
   if (endpoint != null) {
      return zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint).encapsulate(cmd)
   }
   else {
      return cmd
   }
}

String secure(String cmd) {
   return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd) {
   return zwaveSecureEncap(cmd)
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
   hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
   if (encapCmd) {
      zwaveEvent(encapCmd)
   }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=null){
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
         if (cmd.value && cmd.value <= 100) {
            cd.parse([[name:"level", value: cmd.value, descriptionText:"${cd.displayName} level was set to ${cmd.value}"]])
         }
      }
      else {
         if (enableDebug) log.warn "Unable to find child device for endpoint $ep; ignoring report"
      }      
      if (cmd.value) {
         if (device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
      else {
         Boolean allOff = true
         Integer otherEp = (ep as Integer == 1 ? 2 : 1)
         com.hubitat.app.ChildDeviceWrapper otherCd = getChildDevice("${device.id}-${otherEp}")
         if (otherCd.currentValue("switch") != "off") allOff = false
         if (allOff && device.currentValue("switch") != "off") {
            if (enableDesc) log.info "${device.displayName} switch is off"
            sendEvent([name: "switch", value: "off"])
         }
         else if (allOff && device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
   }
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
   if (enableDebug) log.debug "dimmerEvents value: ${cmd}"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
   if (cmd.value) {
      if (enableDesc && device.currentValue("level") != cmd.value) log.info "${device.displayName} level is ${cmd.value}%"
      sendEvent(name: "level", value: cmd.value, unit: "%")
   }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
   if (enableDebug) log.debug "BasicSet: $cmd"
   String value = (cmd.value ? "on" : "off")
   if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
   sendEvent(name: "switch", value: value)
   if (cmd.value) {
      if (enableDesc && device.currentValue("level") != cmd.value) log.info "${device.displayName} level is ${cmd.value}%"
      sendEvent(name: "level", value: cmd.value, unit: "%")
   }
   List<hubitat.zwave.Command> cmds = []
   cmds << secure(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
   cmds << secure(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
   sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds), 300), hubitat.device.Protocol.ZWAVE)
}

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
      else {
         Boolean allOff = true
         Integer otherEp = (ep as Integer == 1 ? 2 : 1)
         com.hubitat.app.ChildDeviceWrapper otherCd = getChildDevice("${device.id}-${otherEp}")
         if (otherCd.currentValue("switch") != "off") allOff = false
         if (allOff && device.currentValue("switch") != "off") {
            if (enableDesc) log.info "${device.displayName} switch is off"
            sendEvent([name: "switch", value: "off"])
         }
         else if (allOff && device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
   }
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd, ep = null) {
   if (enableDebug) log.debug "SwitchMultilevelReport: $cmd; ep = $ep"
   if (ep != null) {
      com.hubitat.app.ChildDeviceWrapper cd = getChildDevice("${device.id}-${ep}")
      if (cd != null) {
         if (ep as Integer == 1) {            
            if (cmd.value && cmd.value <= 100) {
               if (cd.currentValue("level") != cmd.value) cd.parse([[name:"level", value: cmd.value, descriptionText:"${cd.displayName} level is ${cmd.value}"]])
               if (cd.currentValue("switch") != "on") cd.parse([[name:"switch", value: "on", descriptionText:"${cd.displayName} switch is on"]])
            }
            else {
               if (cd.currentValue("switch") != "off") cd.parse([[name:"switch", value: "off", descriptionText:"${cd.displayName} switch is off"]])
            }
         }
         else if (ep as Integer == 2) {
            String speed = "off"
            if (cmd.value == 1) speed = "auto" // actually "breeze mode"
            else if (cmd.value > 1 && cmd.value <= 33) speed = "low"
            else if (cmd.value > 33 && cmd.value <= 66) speed = "medium"
            else if (cmd.value > 66) speed = "high"
            cd.parse([[name:"speed", value: onOff, descriptionText:"${cd.displayName} fan speed is $speed"]])
            if (cmd.value && cmd.value <= 100) {
               if (cd.currentValue("level") != cmd.value) cd.parse([[name:"level", value: cmd.value, descriptionText:"${cd.displayName} level is ${cmd.value}"]])
               if (cd.currentValue("speed") != speed) cd.parse([[name:"speed", value: speed, descriptionText:"${cd.displayName} level is $speed"]])
            }
            else {
               if (cd.currentValue("switch") != "off") cd.parse([[name:"switch", value: "off", descriptionText:"${cd.displayName} switch is off"]])
               if (cd.currentValue("speed") != "off") cd.parse([[name:"speed", value: "off", descriptionText:"${cd.displayName} speed is off"]])
            }
         }
      }
      if (cmd.value) {
         if (device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
      else {
         Boolean allOff = true
         Integer otherEp = (ep as Integer == 1 ? 2 : 1)
         com.hubitat.app.ChildDeviceWrapper otherCd = getChildDevice("${device.id}-${otherEp}")
         if (otherCd.currentValue("switch") != "off") allOff = false
         if (allOff && device.currentValue("switch") != "off") {
            if (enableDesc) log.info "${device.displayName} switch is off"
            sendEvent([name: "switch", value: "off"])
         }
         else if (allOff && device.currentValue("switch") != "on") {
            if (enableDesc) log.info "${device.displayName} switch is on"
            sendEvent([name: "switch", value: "on"])
         }
      }
   }
   else {
      if (enableDebug) log.warn "Ignoring SwitchMultiLevelReport for ep $ep"
   }
}

void zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd, ep=null) {
   if (enableDebug) log.debug "IndicatorReport: $cmd; ep = $ep"
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
   if (enableDebug) log.debug "MultiChannelCmdEncap: $cmd; ep = ep"
   def encapsulatedCommand = cmd.encapsulatedCommand([0x32: 3, 0x25: 1, 0x20: 1])
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
	device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
	device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
	device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd, ep=null) {
   if (enableDebug) log.debug "ProtectionReport: ${cmd}, ep: $ep"
   if (enableDesc) log.info "${device.displayName} local protection is ${cmd.localProtectionState > 0 ? 'on' : 'off'} and remote protection is ${cmd.rfProtectionState > 0 ? 'on' : 'off'}"
   device.updateSetting("disableLocal", [value:cmd.localProtectionState ? true : false, type:"bool"])
   device.updateSetting("disableRemote", [value:cmd.rfProtectionState ? true : false, type:"bool"])
}

void createFanAndLightChildDevices() {
   String thisId = device.id
   com.hubitat.app.ChildDeviceWrapper lightChild = getChildDevice("${thisId}-1")
   com.hubitat.app.ChildDeviceWrapper fanChild = getChildDevice("${thisId}-2")
   if (!lightChild) {
      lightChild = addChildDevice("hubitat", "Generic Component Dimmer", "${thisId}-1", [name: "${device.displayName} Light", isComponent: false])
   }
   if (!fanChild) {
      fanChild = addChildDevice("hubitat", "Generic Component Fan Control", "${thisId}-2", [name: "${device.displayName} Fan", isComponent: false])
   }   
}

// Parent device commands:

String on() {
   if (enableDebug) log.debug "on()"
   return secure(zwave.basicV1.basicSet(value: 0xFF))   
   // Try this instead?   
   //return secure(zwave.switchMultilevelV1.switchMultilevelSet(value: 0xFF))
}

String off() {
   if (enableDebug) log.debug "off()"
   return secure(zwave.basicV1.basicSet(value: 0x00))   
   // Try this instead?   
   //return secure(zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00))
}

String setLevel(value) {
   if (enableDebug) log.debug "setLevel($value)"
   return secure(zwave.switchMultilevelV2.switchMultilevelSet(value: value < 100 ? value : 99))
   // TEST, if above doesn't work, try:
   // zwave.basicV1.basicSet(value: value < 100 ? value : 99)
}

String setLevel(value, duration) {
   if (enableDebug) log.debug "setLevel($value, $duration)"
   Integer dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
}

String startLevelChange(direction) {
   Integer upDown = direction == "down" ? 1 : 0
   return secure(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0))
}

String stopLevelChange() {
   return secure(zwave.switchMultilevelV1.switchMultilevelStopLevelChange())
}

String setSpeed(value) {
   if (enableDebug) log.debug "setSpeed($value)"
   com.hubitat.app.ChildDeviceWrapper fanChild = getChildDevice("${device.id}-2")
   if (fanChild != null) {
      return componentSetSpeed(fanChild, value)
   }
}


String cycleSpeed() {
   if (enableDebug) log.debug "cycleSpeed()"
   String currentSpeed = "off"
   com.hubitat.app.ChildDeviceWrapper fanChild = getChildDevice("${device.id}-2")
   if (fanChild) currentSpeed = fanChild.currentValue("speed") ?: "off"
   switch (currentSpeed) {
      case "off":
         return componentSetLevel(fanChild, 33)
      break
      case "low":
         return componentSetLevel(fanChild, 66)
      break
      case "medium-low":
      case "medium":
      case "medium-high":
         return componentSetLevel(fanChild, 99)
      break
      case "high":
         return componentOff(fanChild)
      break
   }
}

// Component device commands:

String componentOn(cd) {
   if (enableDebug) log.debug "componentOn($cd)"
   String cmd = ""
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: 0xFF), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = secure(encap(zwave.basicV1.basicSet(value: 0xFF), 2)) 
      //cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: 0xFF), 2))
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
      cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {   
      cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00), 2))
      cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00), 2))
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

String componentCycleSpeed(cd) {
   if (enableDebug) log.debug "componentCycleSpeed()"
   // Should only happen on fan child, so skipping any checks...
   return cycleSpeed()
}

String componentSetSpeed(cd, value) {
   if (enableDebug) log.debug "componentSetSpeed($cd, $value)"
   switch (value) {
      case "low":
         return componentSetLevel(cd, 33)
         break
      case "medium-low":
      case "medium":
      case "medium-high":
         return componentSetLevel(cd, 66)
         break
      case "high":
         return componentSetLevel(cd, 99)
         break
      case "auto":
         return componentSetLevel(cd, 1)
         break
      case "on":
         return componentOn(cd)
         break
      case "off":
         return componentOff(cd)
         break
   }
}

String componentSetLevel(cd, level, transitionTime = null) {
   if (enableDebug) log.debug "componentSetLevel($cd, $level, $transitionTime)"
   level = Math.max(Math.min(level as Integer, 99), 0)
   if (transitionTime > 255) transitionTime = 255
   String cmd = ""
   if (cd.deviceNetworkId.endsWith("-1")) { // Light
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelSet(value: level < 100 ? level : 99, dimmingDuration: transitionTime), 1))      
   }
   else if (cd.deviceNetworkId.endsWith("-2")) { // Fan
      //cmd = secure(encap(zwave.switchMultilevelV1.switchMultilevelSet(value: level), 2))
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelSet(value: level < 100 ? level : 99, dimmingDuration: transitionTime), 2))  
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

void componentRefresh(cd) {
   if (enableDebug) log.debug "componentRefresh($cd)"
   log.debug "Component refresh not supported; refresh parent device"
}

String componentStartLevelChange(cd, direction) {
   if (enableDebug) log.debug "componentStartLevelChange($cd, $direction)"
   Boolean upDownVal = direction == "down" ? true : false
   Integer startLevel = 0
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelStartLevelChange(ignoreStartLevel: true,
                                 startLevel: startLevel, upDown: upDownVal, dimmingDuration:4), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelStartLevelChange(ignoreStartLevel: true,
                                 startLevel: startLevel, upDown: upDownVal, dimmingDuration:4), 2))
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

String componentStopLevelChange(cd) {
   if (enableDebug) log.debug "componentStopLevelChange($cd)"
   if (cd.deviceNetworkId.endsWith("-1")) {
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelStopLevelChange(), 1))
   }
   else if (cd.deviceNetworkId.endsWith("-2")) {
      cmd = secure(encap(zwave.switchMultilevelV2.switchMultilevelStopLevelChange(), 2))
   }
   else {
      log.warn "Unknown child device: $ep"
   }
   return cmd
}

List<String> refresh() {
   if (enableDesc) log.info "refresh()"
   List<String> cmds = []
   cmds << secure(encap(zwave.switchMultilevelV1.switchMultilevelGet(), 1))
   cmds << secure(encap(zwave.switchMultilevelV1.switchMultilevelGet(), 2))
   cmds << secure(zwave.meterV2.meterGet(scale: 0))
   cmds << secure(zwave.meterV2.meterGet(scale: 2))
   return cmds
}

List<String> reset() {
   if (enableDebug) log.info "reset(): resetting power/energy statistics for ${device.displayName}"
   List<String> cmds = []
   cmds << secure(zwave.meterV2.meterReset())
   cmds << secure(zwave.meterV2.meterGet(scale: 0))
   cmds << secure(zwave.meterV2.meterGet(scale: 2))
   return delayBetween(cmds, 1000)
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
      createFanAndLightChildDevices()
   }
   catch (Exception ex) {
      log.warn "Could not create fan and/or light child devices: $ex"
   }
   return initialize()
}

List<String> initialize() {
   List<String> cmds = []
   zwaveParameters.each { param, data ->
      if (settings[data.input.name] != null) {
         if (enableDebug) log.debug "Setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
         cmds.add(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
      }
   }
   sendEvent(name: "numberOfButtons", value: 14)
   cmds << secure(zwave.versionV2.versionGet())
   cmds << secure(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
   cmds << secure(zwave.protectionV2.protectionSet(localProtectionState: settings["disableLocal"] ? 1 : 0,
                                             rfProtectionState: settings["disableRemote"] ? 1 : 0))
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

List<String> zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, ep=null) {
   if (enableDebug) log.debug "MeterReport: $cmd"
   def event
   def cmds = []
   if (cmd.meterValue != []) {
      if (cmd.scale == 0) {
         if (cmd.meterType == 161) {
            sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
               if (enableDesc) log.info "${device.displayName} voltage is ${cmd.scaledMeterValue} V"
         }
         else if (cmd.meterType == 1) {
            sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
               if (enableDesc) log.info "${device.displayName} energy is ${cmd.scaledMeterValue} kWh"
         }
      }
      else if (cmd.scale == 1) {
         sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A")
         if (enableDesc) log.info "${device.displayName} amperage is ${cmd.scaledMeterValue} A"
      }
      else if (cmd.scale == 2) {
         sendEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
         if (enableDesc) log.info "${device.displayName}: power is ${cmd.scaledMeterValue} W"
      }
   }
   else {
      if (cmd.scale == 0) cmds << secure(zwave.meterV2.meterGet(scale: 0))
      if (cmd.scale == 2) cmds << secure(zwave.meterV2.meterGet(scale: 2))
   }
   if (cmds) return response(cmds) else return null
}

String setConfigParameter(number, value, size) {
   return setParameter(number, value, size)
}

String setParameter(number, value, size) {
   if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)a"
   return secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number.toInteger(), size: size.toInteger()))
}

void clearChildDevsAndState() {
   state.clear()
   getChildDevices()?.each {
      deleteChildDevice(it.deviceNetworkId)
   }
}

// Sets fan "notification LED" parameter to calculated value (calculated 4-byte base-10 value, or 0 for none)
String setFanIndicator(value) {
   if (enableDebug) log.debug "setIndicator($value)"
   return setParameter(25, value, 4)
}

// Sets fan "notification LED" parameter to value calculated based on provided parameters
String setFanIndicator(String color, level, String effect, BigDecimal duration=255) {
   if (enableDebug) log.debug "setIndicator($color, $level, $effect, $duration)"
	Integer calcValue = 0
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   Integer intEffect = 0
   if (effect != null) intEffect = (effectNameMap[effect?.toLowerCase()] != null) ? effectNameMap[effect?.toLowerCase()] : 4
   // Convert level from 0-100 to 0-9:
   intLevel = Math.round(intLevel/(100.0/9.0))
   // Range check:
   if (intLevel < 0) intLevel = 0
   else if (intLevel > 9) intLevel = 9
   if (duration < 1) duration = 1
   else if (duration > 255) duration = 255
   if (intEffect != 0) {
      calcValue += intColor // * 1
      calcValue += intLevel * 256
      calcValue += duration * 65536
      calcValue += intEffect * 16777216
   }
   return setParameter(25, calcValue, 4)
}

// Sets light "notification LED" parameter to calculated value (calculated 4-byte base-10 value, or 0 for none)
String setLightIndicator(value) {
   if (enableDebug) log.debug "setIndicator($value)"
   return setParameter(24, value, 4)
}

// Sets light "notification LED" parameter to value calculated based on provided parameters
String setLightIndicator(String color, level, String effect, BigDecimal duration=255) {
   if (enableDebug) log.debug "setIndicator($color, $level, $effect, $duration)"
	Integer calcValue = 0
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   Integer intEffect = 0
   if (effect != null) intEffect = (effectNameMap[effect?.toLowerCase()] != null) ? effectNameMap[effect?.toLowerCase()] : 4
   // Convert level from 0-100 to 0-9:
   intLevel = Math.round(intLevel/(100.0/9.0))
   // Range check:
   if (intLevel < 0) intLevel = 0
   else if (intLevel > 9) intLevel = 9
   if (duration < 1) duration = 1
   else if (duration > 255) duration = 255
   if (intEffect != 0) {
      calcValue += intColor // * 1
      calcValue += intLevel * 256
      calcValue += duration * 65536
      calcValue += intEffect * 16777216
   }
   return setParameter(24, calcValue, 4)
}

// Sets default fan LED color parameter to value (0-255) and level (0-10)
List<String> setFanLEDColor(value, level=null) {
   if (enableDebug) log.debug "setLEDColor(Object $value, Object $level)"
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: 20, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: level.toInteger(), parameterNumber: 21, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets default fan LED color parameter to named color (from map) and level (Hubitat 0-100 style)
List<String> setFanLEDColor(String color, level) {
   if (enableDebug) log.debug "setLEDColor(String $color, Object $level)"
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   intLevel = Math.round(intLevel/10)
   if (intLevel < 0) intLevel = 0
   else if (intLevel > 10) intLevel = 10
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: 20, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: 21, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets fan "on" LED level parameter to value (0-10)
String setFanOnLEDLevel(value) {
   if (enableDebug) log.debug "setOnLEDLevel($value)"
   return setParameter(21, value, 1)
}

// Sets fan "off" LED level parameter to value (0-10)
String setFanOffLEDLevel(value) {
   if (enableDebug) log.debug "setOffLEDLevel($value)"
   return setParameter(23, value, 1)
}

// Sets default light LED color parameter to value (0-255) and level (0-10)
List<String> setLightLEDColor(value, level=null) {
   if (enableDebug) log.debug "setLEDColor(Object $value, Object $level)"
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: 18, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: level.toInteger(), parameterNumber: 19, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets default light LED color parameter to named color (from map) and level (Hubitat 0-100 style)
List<String> setLightLEDColor(String color, level) {
   if (enableDebug) log.debug "setLEDColor(String $color, Object $level)"
   Integer intColor = colorNameMap[color?.toLowerCase()] ?: 170
   Integer intLevel = level as Integer
   intLevel = Math.round(intLevel/10)
   if (intLevel < 0) intLevel = 0
   else if (intLevel > 10) intLevel = 10
   List<String> cmds = []   
   cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: 18, size: 2)))
   if (level != null) {
      cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: 19, size: 1)))
   }
   return delayBetween(cmds, 750)
}

// Sets light "on" LED level parameter to value (0-10)
String setLightOnLEDLevel(value) {
   if (enableDebug) log.debug "setOnLEDLevel($value)"
   return setParameter(19, value, 1)
}

// Sets light "off" LED level parameter to value (0-10)
String setLightOffLEDLevel(value) {
   if (enableDebug) log.debug "setOffLEDLevel($value)"
   return setParameter(22, value, 1)
}
