/**
 *
 *  Inovelli 2-Channel Smart Plug NZW37
 *   
 *  Original driver by Eric Maycock (erocm123), 2018-06-05
 *  Copyright Eric Maycock
 *  Updates for Hubitat by Robert Morris (RMoRobert)
 *
 *  Includes all configuration parameters and ease of advanced configuration. 
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
 *  2021-03-04: Hubitat updates by Robert Morris (S2 support, use built-in component driver, conventional logging, etc.)
 *  -- Original driver: --
 *  2018-06-05: Switching back to child device configuration
 *  2018-04-09: Changed back to use encapsulation commands and removed child device references
 *  2018-03-27: Adapted for Hubitat.
 */

 /*
  * BUTTON MAPPING:
  * 2x tap = button 1 pushed
  */

@groovy.transform.Field static final Map commandClassVersions = [
     0x20: 1, // Basic
     0x25: 1, // Switch Binary
     0x70: 2, // Configuration
     0x98: 1, // Security
     0x60: 3, // Multi Channel
     0x8E: 2, // Multi Channel Association
     0x72: 2, // Manufacturer Specific
     0x5B: 1, // Central Scene
     0x63: 1, // Supervision
     0x85: 2, // Association
     0x86: 2, // Version
    ]

metadata {
    definition(name: "Inovelli 2-Channel Smart Plug NZW37", namespace: "RMoRobert", author: "Robert Morris") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Refresh"
        capability "PushableButton"
        capability "HoldableButton" // check if reports these?
        capability "Configuration"

        command "componentOn"
        command "componentOff"
        command "componentRefresh"

        fingerprint mfr: "015D", prod: "0221", model: "251C"
        fingerprint mfr: "0312", prod: "B221", model: "251C"
        fingerprint deviceId: "0x1001", inClusters: "0x5E,0x85,0x59,0x5A,0x72,0x60,0x8E,0x73,0x27,0x25,0x86"
        fingerprint deviceId: "0x1001", inClusters: "0x5E,0x25,0x27,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x9F,0x60,0x6C,0x7A"
        fingerprint deviceId: "0x1001", inClusters: "0x5E,0x25,0x27,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x60,0x6C"
    }
        
    preferences {
        input "autoOff1", "number", title: "Auto Off Channel 1\n\nAutomatically turn switch off after this number of seconds\nRange: 0 to 32767", required: false, range: "0..32767"
        input "autoOff2", "number", title: "Auto Off Channel 2\n\nAutomatically turn switch off after this number of seconds\nRange: 0 to 32767", required: false, range: "0..32767"
        input "ledIndicator", "enum", title: "LED Indicator\n\nTurn LED indicator on when switch is:\n", required: false, options:[["0": "On"], ["1": "Off"], ["2": "Disable"]], defaultValue: "0"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def parse(String description) {
    if (logEnable) log.debug "parse(description = $description)"
    def result = []
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
        result += zwaveEvent(cmd)
        if (logEnable) log.trace "Parsed ${cmd} to ${result.inspect()}"
    } else {
        if (logEnable) log.trace "Non-parsed event: ${description}"
    }    
    return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep = null) {
    if (logEnable) log.debug "BasicReport: cmd = ${cmd}, ep = $ep"
    if (ep) {
        def event
        childDevices.each {
            childDevice ->
                if (childDevice.deviceNetworkId == "$device.deviceNetworkId-ep$ep") {
                    List l = [[name: "switch", value: cmd.value ? "on" : "off", descriptionText: "${childDevice.displayName} was turned ${cmd.value ? 'on' : 'off'}"]]
                    log.trace "CD1 parse: $l"
                    childDevice.parse([[name: "switch", value: cmd.value ? "on" : "off", descriptionText: "${childDevice.displayName} was turned ${cmd.value ? 'on' : 'off'}"]])
                }
        }
        if (cmd.value) {
            event = [createEvent([name: "switch", value: "on"])]
        } else {
            Boolean allOff = true
            childDevices.each {
                childDevice ->
				    if (childDevice.deviceNetworkId != "$device.deviceNetworkId-ep$ep") 
                       if (childDevice.currentState("switch") != "off") allOff = false
            }
            if (allOff) {
                event = [createEvent([name: "switch", value: "off"])]
            } else {
                event = [createEvent([name: "switch", value: "on"])]
            }
        }
        return event
    }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    if (logEnable) log.debug "BasicSet: ${cmd}"
    def result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
    def cmds = []
    cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
    cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
    return response(commands(cmds)) // returns the result of reponse()
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, ep) {
    if (logEnable) log.debug "BasicSet: cmd = $cmd, ep = $ep"
    def result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
    return zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), ep))
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep = null) {
    if (logEnable) log.debug "SwitchBinaryReport: cmd = $cmd, ep = $ep"
    if (ep) {
        def event
        def childDevice = childDevices.find {
            it.deviceNetworkId == "$device.deviceNetworkId-ep$ep"
        }
        List l = [[name: "switch", value: cmd.value ? "on" : "off", descriptionText: "${childDevice.displayName} was turned ${cmd.value ? 'on' : 'off'}"]]
        log.trace "CD2 parse: $l"
        if (childDevice) childDevice.parse([[name: "switch", value: cmd.value ? "on" : "off", descriptionText: "${childDevice.displayName} was turned ${cmd.value ? 'on' : 'off'}"]])
        if (cmd.value) {
            event = [createEvent([name: "switch", value: "on"])]
        } else {
            Boolean allOff = true
            childDevices.each {
                n->
                    if (n.deviceNetworkId != "$device.deviceNetworkId-ep$ep" && n.currentState("switch").value != "off") allOff = false
            }
            if (allOff) {
                event = [createEvent([name: "switch", value: "off"])]
            } else {
                event = [createEvent([name: "switch", value: "on"])]
            }
        }
        return event
    } else {
        def result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
        def cmds = []
        cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 1))
        cmds << zwaveSecureEncap(encap(zwave.switchBinaryV1.switchBinaryGet(), 2))
        return [result, response(commands(cmds))] // returns the result of reponse()
    }
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    if (logEnable) log.debug "MultiChannelCmdEncap: ${cmd}"
    def encapsulatedCommand = cmd.encapsulatedCommand([0x32: 3, 0x25: 1, 0x20: 1])
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    }
}

def zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=null){
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) {
        zwaveEvent(encapCmd, ep)
    }
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    if (logEnable) log.debug "ManufacturerSpecificReport: ${cmd}"
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    if (logEnable) log.debug "CentralSceneNotification: ${cmd}"
    if (cmd.sceneNumber == 2) {
        createEvent(name: "held", value: 1, type: "physical")
    }
    else {
        createEvent(name: "pushed", value: 1, type: "physical")
    }
}

def zwaveEvent(hubitat.zwave.Command cmd, ep=null) {
    // This will capture any commands not handled by other instances of zwaveEvent
    // and is recommended for development so you can see every command the device sends
    if (logEnable) log.debug "Unhandled Event: cmd = ${cmd}, ep = ${ep}"
}

def on() {
    log.debug "on()"
    commands([
            zwave.switchAllV1.switchAllOn(),
            encap(zwave.basicV1.basicGet(), 1),
            encap(zwave.basicV1.basicGet(), 2)
    ])
}

def off() {
    log.debug "off()"
    commands([
            zwave.switchAllV1.switchAllOff(),
            encap(zwave.basicV1.basicGet(), 1),
            encap(zwave.basicV1.basicGet(), 2)
    ])
}

def componentOn(cd) {
    if (logEnable) log.info "received on request from ${cd.displayName}"
    List<String> cmds = []
    commands([
		encap(zwave.basicV1.basicSet(value: 0xFF), channelNumber(cd.deviceNetworkId)),
        encap(zwave.basicV1.basicGet(), channelNumber(cd.deviceNetworkId))
    ])
}

def componentOff(cd) {
    if (logEnable) log.info "received off request from ${cd.displayName}"
    List<String> cmds = []
    commands([
		encap(zwave.basicV1.basicSet(value: 0x00), channelNumber(cd.deviceNetworkId)),
        encap(zwave.basicV1.basicGet(), channelNumber(cd.deviceNetworkId))
    ])
}

def componentRefresh(cd) {
    if (logEnable) log.info "received refresh request from ${cd.displayName}"
    zwaveSecureEncap(encap(zwave.basicV1.basicGet(), channelNumber(cd.deviceNetworkId)))
}

def refresh() {
    if (logEnable) log.debug "refresh()"
    commands([
            encap(zwave.basicV1.basicGet(), 1),
            encap(zwave.basicV1.basicGet(), 2)
    ])
}

void push(Number buttonNumber) {    
    sendEvent(name: "pushed", value: buttonNumber, type: "digital")
}

void hold(Number buttonNumber) {    
    sendEvent(name: "held", value: buttonNumber, type: "digital")
}

def installed() {
    refresh()
}

def configure() {
    log.debug "configure()"
    def cmds = initialize()
    commands(cmds)
}

def updated() {
    if (!state.lastRan || now() >= state.lastRan + 2000) {
        if (logEnable) log.debug "updated()"
        state.lastRan = now()
        def cmds = initialize()
        commands(cmds)
    } else {
        if (logEnable) log.debug "updated() ran within the last 2 seconds. Skipping execution."
    }
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800, logsOff)
}

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}

def integer2Cmd(value, size) {
    try{
	switch(size) {
	case 1:
		[value]
    break
	case 2:
    	short value1   = value & 0xFF
        short value2 = (value >> 8) & 0xFF
        [value2, value1]
    break
    case 3:
    	short value1   = value & 0xFF
        short value2 = (value >> 8) & 0xFF
        short value3 = (value >> 16) & 0xFF
        [value3, value2, value1]
    break
	case 4:
    	short value1 = value & 0xFF
        short value2 = (value >> 8) & 0xFF
        short value3 = (value >> 16) & 0xFF
        short value4 = (value >> 24) & 0xFF
		[value4, value3, value2, value1]
	break
	}
    } catch (e) {
        log.debug "Error: integer2Cmd $e Value: $value"
    }
}

def initialize() {
    if (logEnable) log.debug "initialize()"
    if (!childDevices) {
        createChildDevices()
    }
    sendEvent(name: "numberOfButtons", value: 1)
    List<String> cmds = []
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: ledIndicator!=null? ledIndicator.toInteger() : 0, parameterNumber: 1, size: 1))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 1))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(configurationValue: autoOff1!=null? integer2Cmd(autoOff1.toInteger(), 2) : integer2Cmd(0,2), parameterNumber: 2, size: 2))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 2))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(configurationValue: autoOff2!=null? integer2Cmd(autoOff2.toInteger(), 2) : integer2Cmd(0,2), parameterNumber: 3, size: 2))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 3))
    return cmds
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
    if (logEnable) log.info "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'"
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (logEnable) log.debug "VersionReport: $cmd"
    if(cmd.applicationVersion && cmd.applicationSubVersion) {
	    def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
        sendEvent(name: "status", value: "fw: ${firmware}")
        updateDataValue("firmware", firmware)
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

private String command(hubitat.zwave.Command cmd) {
   zwaveSecureEncap(cmd)
}

private List<String> commands(commands, delay = 1000) {
    delayBetween(commands.collect {
        zwaveSecureEncap(it)
    }, delay)
}

private Integer channelNumber(String dni) {
    dni.split("-ep")[-1] as Integer
}

private void createChildDevices() {
    addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-ep1", [name: "${device.displayName} - Left Outlet"])
    addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-ep2", [name: "${device.displayName} - Right Outlet"])
}