/*
 * ===================== Inovelli Red Series Switch (LZW30-SN) Driver =====================
 *
 *  Copyright 2020 Robert Morris
 *  Portions based on code from Hubitat and Inovelli
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
 *  Last modified: 2020-01-19
 * 
 *  Changelog:
 * 
 *  v1.0 Initial Release   
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x25: 1,    // Switch Binary
    0x32: 3,    // Meter
    0x5B: 1,    // CentralScene
    0x70: 1,    // Configuration
    0x98: 1     // Security
]

metadata {
    definition (name: "Advanced Inovelli Red Series Switch (LZW30-SN)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Red-Switch-LZW30SN-Advanced.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "PowerMeter"
        capability "Configuration"
        capability "PushableButton"
        capability "HoldableButton"
        capability "ReleasableButton"

        command "flash"
        command "refresh"
        command "push", ["NUMBER"]
        command "hold", ["NUMBER"]
        command "release", ["NUMBER"]
        command "setIndicator", [[name: "Calcualted notification value", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]]
        command "setDefaultLEDColor", [[name: "Color", type: "NUMBER", description: "Inovelli format, 0-255"]]
        command "setDefaultLEDLevel", [[name:"Level", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
        command "setOffLEDLevel", [[name:"Level", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]

        attribute "firmware", "String"
        attribute "amperage", "number"

        fingerprint mfr: "031E", prod: "0002", model: "0001", deviceJoinName: "Inovelli Switch Red Series" 
        fingerprint deviceId: "0x1001", inClusters: "0x5E,0x6C,0x55,0x98,0x9F,0x22,0x70,0x85,0x59,0x86,0x32,0x72,0x5A,0x5B,0x73,0x75,0x7A"
        fingerprint deviceId: "0x1001", inClusters: "0x5E,0x70,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x32,0x5B,0x98,0x9F,0x25,0x6C,0x75,0x22,0x7A"
    }
    
    preferences {
        input name: "param2", type: "enum", title: "Paddle function", options:[[0:"Normal"],[1:"Reverse"]], defaultValue: 0
        input name: "param3", type: "number", title: "Automatically turn switch off after ... seconds (0=disable auto-off)", range: 0..32767, defaultValue: 0
        input name: "param1", type: "enum", title: "State on power restore", options:[[0:"Previous"],[1:"On"],[2:"Off"]], defaultValue: 0
        input name: "param10", type: "enum", title: "Send new power report when level changes by",
            options:[[0:"Disabled"],[5:"5%"],[10:"10%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],
            [80:"80%"],[90:"90%"],[100:"100%"]], defaultValue: 10
        input name: "param12", type: "enum", title: "Send new energy report when level changes by",
            options:[[0:"Disabled"],[5:"5%"],[10:"10%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],
            [80:"80%"],[90:"90%"],[100:"100%"]], defaultValue: 10
        input name: "param11", type: "enum", title: "Power and energy reporting interval",
            options:[[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],
            [1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],
            [32400: "9 hours"]], defaultValue: 3600
        //input name: "disableLocal", type: "bool", title: "Disable local control (on switch)", required: false, defaultValue: false
        //input name: "disableRemote", type: "bool", title: "Disable remote control (from Hubitat)", required: false, defaultValue: false
        input name: "flashRate", type: "enum", title: "Flash rate", options:[[750:"750ms"],[1000:"1s"],[2000:"2s"],[5000:"5s"]], defaultValue: 750
        input name: "createChildDevs", type: "bool", title: "Create LED child devices", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true

    }
}

def logsOff() {
    log.warn("Disabling debug logging")
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}

private command(hubitat.zwave.Command cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true") {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=1000) {
    delayBetween(commands.collect{ command(it) }, delay)
}

def parse(description) {
    def result
    if (description.startsWith("Err 106")) {
        state.sec = 0
        result = createEvent(descriptionText: description, isStateChange: true)
    } else if (description != "updated") {
        def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
            result = zwaveEvent(cmd)
            //log.debug("'$cmd' parsed to $result")
        } else {
            logDebug("Couldn't zwave.parse '$description'")
        }
    }
    return result
}

// Does Inovelli switch send these?
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logDebug("VersionReport value: ${cmd}")
    if(cmd.applicationVersion != null && cmd.applicationSubVersion != null) {
	    def firmware = "${cmd.applicationVersion}.${cmd.applicationSubVersion.toString().padLeft(2,'0')}"
        logDesc("${device.displayName}: Firmware report received: ${firmware}")
        //state.needfwUpdate = "false"
        createEvent(name: "firmware", value: "${firmware}")
    }
}

def zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd) {
    logDebug("${device.displayName}: ${device.displayName}: ${cmd}")
    logDebug("${device.displayName}: Protection report received: Local protection = ${cmd.localProtectionState > 0 ? "on" : "off"}, remote protection = ${cmd.rfProtectionState > 0 ? "on" : "off"}")
    if (!state.lastRan || now() <= state.lastRan + 60000) {
        state.localProtectionState = cmd.localProtectionState
        state.rfProtectionState = cmd.rfProtectionState
    } else {
        logDebug("${device.displayName}: Protection report received more than 60 seconds after running updated(). Possible configuration made at switch")
    }
    //device.updateSetting("disableLocal",[value:cmd.localProtectionState?cmd.localProtectionState:0,type:"enum"])
    //device.updateSetting("disableRemote",[value:cmd.rfProtectionState?cmd.rfProtectionState:0,type:"enum"])
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    logDebug("$device.displayName MeterReport: ${cmd}")
    def event
	if (cmd.scale == 0) {
    	if (cmd.meterType == 161) {
		    event = createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
            logDesc("$device.displayName voltage report received: ${cmd.scaledMeterValue} V")
        } else if (cmd.meterType == 1) {
        	event = createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
            logDesc("$device.displayName energy report received: ${cmd.scaledMeterValue} kWh")
        }
	} else if (cmd.scale == 1) {
		event = createEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A")
        logDesc("$device.displayName amperage report received: ${cmd.scaledMeterValue} A")
	} else if (cmd.scale == 2) {
		event = createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
        logDesc("$device.displayName power report received: ${cmd.scaledMeterValue} W")
	}
    return event
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logDebug("${device.displayName} ConfigurationReport: ${cmd}")
    logDesc("${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd2Integer(cmd.configurationValue)}'")
    if (!state.lastRan || now() <= state.lastRan + 60000) {
        state."parameter${cmd.parameterNumber}value" = cmd2Integer(cmd.configurationValue)
    } else {
        if (infoEnable) log.debug "${device.displayName} configuration report received more than 60 seconds after running updated(). Possible configuration made at switch."
    }
    /* def integerValue = cmd2Integer(cmd.configurationValue)
    switch (cmd.parameterNumber) {
        case 9:
            def children = childDevices
            def childDevice = children.find{it.deviceNetworkId.endsWith("ep9")}
            if (childDevice) {
            childDevice.sendEvent(name: "switch", value: integerValue > 0 ? "on" : "off")
            childDevice.sendEvent(name: "level", value: integerValue)            
            }
        break
        case 10:
            def children = childDevices
            def childDevice = children.find{it.deviceNetworkId.endsWith("ep10")}
            if (childDevice) {
            childDevice.sendEvent(name: "switch", value: integerValue > 0 ? "on" : "off")
            childDevice.sendEvent(name: "level", value: integerValue)
            }
        break
    } */
}

def cmd2Integer(array) {
    switch(array.size()) {
        case 1:
            array[0]
            break
        case 2:
            ((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
            break
        case 3:
            ((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
            break
        case 4:
            ((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
            break
    }
}

def integer2Cmd(value, size) {
    try{
	switch(size) {
	case 1:
		[value]
    break
	case 2:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        [value2, value1]
    break
    case 3:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        [value3, value2, value1]
    break
	case 4:
    	def short value1 = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        def short value4 = (value >> 24) & 0xFF
		[value4, value3, value2, value1]
	break
	}
    } catch (e) {
        logDebug( "Error: integer2Cmd $e Value: $value")
    }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logDebug("${device.displayName} BasicReport: ${cmd}")
    logDesc("${device.displayName} is ${cmd.value ? "on" : "off"} (physical)")
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    logDebug("${device.displayName} BasicSet: ${cmd}")
    logDesc("${device.displayName} is ${cmd.value ? "on" : "off"} (physical)")
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (debugEnable) log.debug "${device.displayName}: ${cmd}"
    logDesc("${device.displayName} is ${cmd.value ? "on" : "off"} (digital)")
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    logDebug("${device.displayName} CentralSceneNotification: ${cmd}")
    def btnNum = 0
    def eventType = "pushed"
    if (cmd.sceneNumber == 2) {  // Up paddle
        switch(cmd.keyAttributes as int) {
            case 0:
                btnNum = 1
                break
            case 1:
                btnNum = 1
                eventType = "released"
                break
            case 2:
                btnNum = 1
                eventType = "held"
                break
            case 3:
                btnNum = 3
                break
            case 4:
                btnNum = 5
                break
            case 5:
                btnNum = 7
                break
            case 6:
                btnNum = 9
                break
        }
    } else if (cmd.sceneNumber == 1) { // Down paddle
        switch(cmd.keyAttributes as int) {
            case 0:
                btnNum = 2
                break
            case 1:
                btnNum = 2
                eventType = "released"
                break
            case 2:
                btnNum = 2
                eventType = "held"
                break
            case 3:
                btnNum = 4
                break
            case 4:
                btnNum = 6
                break
            case 5:
                btnNum = 8
                break
            case 6:
                btnNum = 10
                break
        }
    } else if (cmd.sceneNumber == 3) { // Config button
        btnNum = 11
    } else {
        logDebug("Unable to parse: ${cmd}")
    }

    if (btnNum) {
        def descriptionText = "${device.displayName} button ${btnNum} was ${eventType}"
        logDesc("${descriptionText}")
        sendEvent(name: "${eventType}", value: "${btnNum}", descriptionText: descriptionText, isStateChange: true, type: "physical")
    }
    return
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug("Skipping: ${cmd}")
}

def push(button) {
    def descriptionText = "${device.displayName} button ${button} was pushed"
    logDesc("${descriptionText}")
    sendEvent(name: "pushed", value: "${button}", descriptionText: descriptionText, isStateChange: true, type: "digital")
}

def hold(button) {
    def descriptionText = "${device.displayName} button ${button} was held"
    logDesc("${descriptionText}")
    sendEvent(name: "held", value: "${button}", descriptionText: descriptionText, isStateChange: true, type: "digital")
}

def release(button) {
    def descriptionText = "${device.displayName} button ${button} was released"
    logDesc("${descriptionText}")
    sendEvent(name: "released", value: "${button}", descriptionText: descriptionText, isStateChange: true, type: "digital")
}

def doubleTap(button){
    def descriptionText = "${device.displayName} button ${button} was doubleTapped"
    logDesc("${descriptionText}")
    sendEvent(name: "doubleTapped", value: "${button}", descriptionText: descriptionText, isStateChange: true, type: "digital")
}

def on() {
    logDebug("${device.displayName} on()")
    state.flashing = false
    return command(zwave.basicV1.basicSet(value: 0xFF))
}

def off() {
    logDebug("${device.displayName} off()")
    state.flashing = false
    return command(zwave.basicV1.basicSet(value: 0x00))
}

def flash() {
    def descriptionText = "${device.getDisplayName()} was set to flash with a rate of ${flashRate ?: 750} milliseconds"
    logDesc("${descriptionText}")
    state.flashing = true
    flashOn()
}

def flashOn() {
    if (!state.flashing) return
    runInMillis((flashRate ?: 750).toInteger(), flashOff)
    return command(zwave.basicV1.basicSet(value: 0xFF))
}

def flashOff() {
    if (!state.flashing) return
    runInMillis((flashRate ?: 750).toInteger(), flashOn)
    return command(zwave.basicV1.basicSet(value: 0x00))
}

def refresh() {
    logDebug("refresh()")
    def cmds = []
    cmds << zwave.basicV1.basicGet()
    cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
    return commands(cmds)
}

def installed(){
    log.warn "Installed..."
    refresh() 
}

def configure() {
    log.warn "configure..."
    runIn(1800, logsOff)
    sendEvent(name: "numberOfButtons", value: 11)
    refresh() 
}

// Apply preferences changes, including updating parameters
def updated() {
    log.info "updated..."
    state.lastRan = now()
    log.warn "Debug logging is: ${logEnable == true}"
    log.warn "Description logging is: ${txtEnable == true}"
    if (logEnable) {
        log.debug("Debug logging will be automatically disabled in 30 minutes...") 
        runIn(1800,logsOff)
    }

    if (createChildDevs) createChildDevicesIfNeeded()

    def cmds = []
    
    // [Parameter number: size] map, assuming variable of name "paramX" where X is parameter number
    def params = [1: 1, 2: 1, 3: 2,
                 10: 1, 11: 2, 12: 1]
    params.each {
        def p = settings["param${it.key}"]
        if (p != null) {
            logDebug("Setting parameter $it.key (size $it.value) to ${p.toInteger()}")
            cmds += zwave.configurationV1.configurationSet(scaledConfigurationValue: p.toInteger(), parameterNumber: it.key, size: it.value)
        }
    }
    // TODO: Add settings and fix this
    //setProtectionStates()
    logDebug("Running commands to set parameters...")
    if (cmds) return commands(cmds, 500)
}

def setProtectionStates() {
    logDebug("Setting protection states ($disableLocal, $disableRemote)")
    cmds = []
    cmds += zwave.protectionV2.protectionSet(localProtectionState: disableLocal ? 1 : 0,
                                             rfProtectionState: disableRemote ? 1 : 0)
    return cmds
}

// Sets "notification LED" parameter to value (0 for none or calculated 4-byte value)
def setIndicator(value) {
    def number = 8
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 4))
}

// Sets default/on LED color parameter to value (0-255)
def setDefaultLEDColor(value) {
    def number = 5
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 2))
}

// Sets default/on LED level parameter to value (0-10)
def setDefaultLEDLevel(value) {
    def number = 6
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 1))
}

// Sets "off" LED level parameter to value (0-10)
def setOffLEDLevel(value) {
	def number = 7
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 1))
}

def createChildDevicesIfNeeded() {
    // Notification LED name should have "-31" for dimmers or "-30" for switches
    def notificationLEDDNI = "${device.deviceNetworkId}-30NotifyLED"
    def defaultLEDDNI = "${device.deviceNetworkId}-DefaultLED"
    def offLEDDNI = "${device.deviceNetworkId}-OffLED"
    if (!getChildDevice(notificationLEDDNI)) {
        try {
            def dev = addChildDevice("Inovelli Notification LED", notificationLEDDNI,
                            ["label": "${device.displayName} Notification LED",
                             "isComponent": false])
            dev.installed()
        } catch (Exception e) {
            log.error "Error creating notification LED child device: $e"
        }    
    }
    if (!getChildDevice(defaultLEDDNI)) {
        try {
            def dev = addChildDevice("Inovelli Default LED", defaultLEDDNI, ["label": "${device.displayName} Default LED"])
        } catch (Exception e) {
            log.error "Error creating default LED child device: $e"
        }    
    }
    if (!getChildDevice(offLEDDNI)) {
        try {
            def dev = addChildDevice("Inovelli Off LED", offLEDDNI, ["label": "${device.displayName} Off LED"])
        } catch (Exception e) {
            log.error "Error creating \"off\" LED child device: $e"
        }    
    }
}

def logDebug(str) {
    if (settings.logEnable) log.debug(str)
}

def logDesc(str) {
    if (settings.txtEnable) log.info(str)
}