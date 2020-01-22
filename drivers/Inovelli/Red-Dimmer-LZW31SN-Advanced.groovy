/*
 * ===================== Inovelli Red Series Dimmer (LZW31-SN) Driver =====================
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
    0x26: 3,    // Switch Multilevel
    0x32: 3,    // Meter
    0x5B: 1,    // CentralScene
    0x70: 1,    // Configuration
    0x98: 1     // Security
]

metadata {
    definition (name: "Advanced Inovelli Red Series Dimmer (LZW31-SN)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Red-Dimmer-LZW31SN-Advanced.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "ChangeLevel"
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

        fingerprint mfr: "031E", prod: "0001", model: "0001"
        fingerprint deviceId: "0x1101", inClusters: "0x5E,0x55,0x98,0x9F,0x6C,0x22,0x26,0x70,0x85,0x59,0x86,0x32,0x72,0x5A,0x5B,0x73,0x75,0x7A"
        fingerprint deviceId: "0x1101", inClusters: "0x5E,0x26,0x70,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x32,0x98,0x9F,0x5B,0x6C,0x75,0x22,0x7A"
    }
    
    preferences {
        input name: "param7", type: "enum", title: "Paddle function", options:[[0:"Normal"],[1:"Reverse"]], defaultValue: 0
        input name: "param1", type: "enum", title: "Dimming rate from paddle",
            options:[[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]], defaultValue: 3
        input name: "param2", type: "enum", title: "Dimming rate from hub",
            options:[[101:"Match phyiscal dimming ramp rate"],[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],
            [10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]], defaultValue: 101
        input name: "param3", type: "enum", title: "On/off fade time from paddle",
            options:[[101:"Match phyiscal dimming ramp rate"],[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],
            [10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]], defaultValue: 101
        input name: "param4", type: "enum", title: "On/off fade time from hub",
            options:[[101:"Match phyiscal dimming ramp rate"],[0:"ASAP"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],
            [10:"10 seconds"],[30:"30 seconds"],[100:"100 seconds"]], defaultValue: 101
        input name: "param5", type: "number", title: "Minimum dimmer level", range: 1..45, defaultValue: 1
        input name: "param6", type: "number", title: "Maximum dimmer level", range: 55..99, defaultValue: 99
        input name: "param8", type: "number", title: "Automatically turn switch off after ... seconds (0=disable auto-off)", range: 0..32767, defaultValue: 0
        input name: "param9", type: "number", title: "Default level for physical \"on\" (0=previous)", range: 0..99, defaultValue: 0
        input name: "param10", type: "number", title: "Default level for digital \"on\" (0=previous)", range: 0..99, defaultValue: 0
        input name: "param11", type: "enum", title: "State on power restore", options:[[0:"Off"],[99:"On to highest level"],[101:"Previous"]], defaultValue: 0
        input name: "param17", type: "enum", title: "If LED bar disabled, indicate level while adjusting and after for",
            options:[[0:"Do not show"],[1:"1 second"],[2:"2 seconds"],[3:"3 seconds"],[4:"4 seconds"],[5:"5 seconds"],[6:"6 seconds"],
            [7:"7 seconds"],[8:"8 seconds"],[9:"9 seconds"],[10:"10 seconds"]], defaultValue: 3
        input name: "param18", type: "enum", title: "Send new power report when level changes by",
            options:[[0:"Disabled"],[5:"5%"],[10:"10%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],
            [80:"80%"],[90:"90%"],[100:"100%"]], defaultValue: 10
        input name: "param20", type: "enum", title: "Send new energy report when level changes by",
            options:[[0:"Disabled"],[5:"5%"],[10:"10%"],[15:"15%"],[20:"20%"],[25:"25%"],[30:"30%"],[40:"40%"],[50:"50%"],[60:"60%"],[70:"70%"],
            [80:"80%"],[90:"90%"],[100:"100%"]], defaultValue: 10
        input name: "param19", type: "enum", title: "Power and energy reporting interval",
            options:[[0:"Disabled"],[30:"30 seconds"],[60:"1 minute"],[180:"3 minutes"],[300:"5 minutes"],[600:"10 minutes"],[900:"15 minutes"],
            [1200:"20 minutes"],[1800:"30 minutes"],[3600:"1 hour"],[7200:"2 hours"],[10800:"3 hours"],[18000:"5 hours"],
            [32400: "9 hours"]], defaultValue: 3600
        input name: "param21", type: "enum", title: "AC power type", options:[[0:"No neutral"],[1:"Neutral"]], defaultValue: 1
        input name: "param22", type: "enum", title: "Switch type", options:[[0:"Single-pole"],[1:"Multi-way with dumb switch"],[2:"Multi-way with aux switch"]], defaultValue: 0
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

// Does Inovelli dimmer send these?
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
    logDebug("${device.displayName} BasicReport:  ${cmd}")
    //logDesc("${device.displayName} Basic report received with value of ${cmd.value ? "on" : "off"} ($cmd.value)"
    // Switch is sending SwitchMultilevelReport as well (which we will use)
    dimmerEvents(cmd)
}            

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    logDebug("${device.displayName}: ${cmd}")
    //logDebug("${device.displayName}: Basic set received with value of ${cmd.value ? "on" : "off"}")
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    logDebug("${device.displayName}: ${cmd}")
    //logDesc("${device.displayName}: Switch Binary report received with value of ${cmd.value ? "on" : "off"}")
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    logDebug("${device.displayName}: ${cmd}")
    //logDebug("${device.displayName}: Switch Multilevel report received with value of ${cmd.value ? "on" : "off"} ($cmd.value)")
    dimmerEvents(cmd)
}

private dimmerEvents(hubitat.zwave.Command cmd) {
    def value = (cmd.value ? "on" : "off")
    logDesc("${device.displayName} switch is ${cmd.value ? "on" : "off"}")
    def result = [createEvent(name: "switch", value: value)]
    if (cmd.value) {
        logDesc("${device.displayName} level is ${cmd.value}%")
        result << createEvent(name: "level", value: cmd.value, unit: "%")
    }
    return result
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
    return command(zwave.switchMultilevelV1.switchMultilevelSet(value: 0xFF))
}

def off() {
    logDebug("${device.displayName} off()")
    state.flashing = false
    return command(zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00))
}

def setLevel(value) {
    logDebug("${device.displayName} setLevel($value)")
    state.flashing = false
    return command(zwave.basicV1.basicSet(value: value < 100 ? value : 99))
}

def setLevel(value, duration) {
    logDebug("${device.displayName} setLevel($value, $duration)")
    state.flashing = false
    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
    return command(zwave.switchMultilevelV2.switchMultilevelSet(value: value < 100 ? value : 99, dimmingDuration: dimmingDuration))
}

def startLevelChange(direction) {
    def upDown = direction == "down" ? 1 : 0
    return command(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0))
}

def stopLevelChange() {
    return command(zwave.switchMultilevelV1.switchMultilevelStopLevelChange().format(), "delay 200")
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
    return command(zwave.switchMultilevelV2.switchMultilevelSet(value: 0xFF, dimmingDuration: 0))
}

def flashOff() {
    if (!state.flashing) return
    runInMillis((flashRate ?: 750).toInteger(), flashOn)
    return command(zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00, dimmingDuration: 0))
}

def refresh() {
    logDebug("refresh()")
    def cmds = []
    cmds << zwave.switchMultilevelV1.switchMultilevelGet()
    cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
    return commands(cmds)
}

def installed(){
    log.warn "Installed..."
    sendEvent(name: "level", value: 1)
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
    def params = [1: 1, 2: 1, 3: 1, 4: 1, 5: 1, 6: 1, 7: 1, 8: 2,
                  9: 1, 10: 1, 11: 1, 17: 1, 18: 1, 19: 2, 20: 1,
                  21: 1, 22: 1]
    params.each {
        def p = settings["param${it.key}"]
        if (p != null) {
            logDebug("Setting parameter $it.key (size $it.value) to ${p.toInteger()}")
            cmds += zwave.configurationV1.configurationSet(scaledConfigurationValue: p.toInteger(), parameterNumber: it.key, size: it.value).format()
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
    def number = 16
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 4))
}

// Sets default/on LED color parameter to value (0-255)
def setDefaultLEDColor(value) {
    def number = 13
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 2))
}

// Sets default/on LED level parameter to value (0-10)
def setDefaultLEDLevel(value) {
    def number = 14
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 1))
}

// Sets "off" LED level parameter to value (0-10)
def setOffLEDLevel(value) {
	def number = 15
    logDebug("Setting parameter $number to $value...")
    return command(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: number, size: 1))
}

def createChildDevicesIfNeeded() {
    // Notification LED name should have "-31" for dimmers or "-30" for switches
    def notificationLEDDNI = "${device.deviceNetworkId}-31NotifyLED"
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
s
