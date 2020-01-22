/**
 *  Copyright 2020 Robert Morris
 *  Original includes code copyrigt 2019 iBlinds
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
 *  Adapted for Hubitat by Robert M
 *  last update from iblinds: 9/9/18 - Eric B
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x26: 2,    // Switch Multilevel
    0x70: 1,    // Configuration
]


metadata {
    definition (name: "iBlinds (Community Driver)", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/iBlinds.groovy") {
        capability "Switch Level"
        capability "Actuator"
        capability "Switch"
        capability "Window Shade"   
        capability "Refresh"
        capability "Battery"
        capability "Configuration"
		
		fingerprint deviceId: "0xD", inClusters: "0x5E,0x85,0x59,0x86,0x72,0x5A,0x73,0x26,0x25,0x80,0x70", mfr: "0x287", deviceJoinName: "iBlinds"
    }
		
    preferences {
            input name: "openPosition", type: "number", description: "", title: "Open to this position by default:", range: 1..98, defaultValue: 50, required: true
            input name: "reverse", type: "bool", description: "", title: "Reverse close direction (close up instead of down)", required: true
            input name: "travelTime", type: "enum", description: "", title: "Allowance for travel time", options: [[3000: "3 seconds"], [5000:"5 seconds"],
                [8000:"8 seconds"], [10000:"10 seconds"], [15000:"15 seconds"], [20000:"20 seconds"], [60000:"1 minute"]], defaultValue: 8000
            input name: "refreshTime", type: "enum", description: "", title: "Schedule daily battery level refresh during this hour",
                  options: [[0:"12 Midnight"], [4:"4 AM"], [5:"5 AM"], [6:"6 AM"], [10:"10 AM"], [13:"1 PM"], [15:"3 PM"], [17:"5 PM"], [23:"11 PM"], [1000: "Disabled"], [2000: "Random"]],
                  defaultValue: 4
			input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        	input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}
def installed() {
    logDebug("installed()")
    runIn(15, "getBattery")
    initialize()
}

def updated() {
    logDebug("updated()")
    initialize()
}

// Set daily schedule for battery refresh; schedule disable of debug logging if enabled
def initialize() {    
    logDebug("Initializing; scheduling battery refresh interval")
    unschedule()
    def cronStr
    def s = Math.round(Math.random() * 60)
    def m = Math.round(Math.random() * 60)
    if ((refreshTime as int) == 2000) {
        def h = Math.round(Math.random() * 23)
        if (h == 2) h = 3 // avoid maintenance window
        cronStr = "${s} ${m} ${h} ? * * *"
    } else if ((refreshTime as int) >= 0 && (refreshTime as int) <= 23) {
        cronStr = "${s} ${m} ${refreshTime} ? * * *"
    }
    log.warn "battery schedule = $cronStr"
    if (cronStr) schedule(cronStr, "getBattery")
    int disableTime = 1800
    if (enableDebug) {
        log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
        runIn(disableTime, debugOff)
    }
}

def debugOff() {
    log.warn("Disabling debug logging")
    device.updateSetting("enableDebug", [value:"false", type:"bool"])
}


def parse(String description) {
    logDebug("parse: $description")
    def result
    if (description != "updated") {
        def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logDebug("BasicReport: $cmd")
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    logDebug("BasicSet: $cmd")
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
    logDebug("SwitchMultilevelReport: $cmd")
    dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelSet cmd) {
    logDebug("SwitchMultilevelSet: $cmd")
    dimmerEvents(cmd)
}

private dimmerEvents(hubitat.zwave.Command cmd) {
    logDebug("Dimmer events:  $cmd")
    def position = cmd.value
    if (reverse) {
        position = 99 - position
    }
    def switchValue = "off"
    def shadePosition = "closed"
    if (position > 0 && position < 99) {
        switchValue = "on"
        shadePosition = "open"
    }
    def result = [
        createEvent(name: "switch", value: switchValue),
        createEvent(name: "windowShade", value: shadePosition)
    ]

    if (device.currentValue("switch") != switchValue) logDesc("$device.displayName switch is $switchValue")
    if (device.currentValue("level") != position) logDesc("$device.displayName level is $position")
    if (device.currentValue("windowShade") != shadePosition) logDesc("$device.displayName windowShade position is $shadePosition")

    if (position < 100) {
        result << createEvent(name: "level", value: position, unit: "%")
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logDebug("ConfigurationReport $cmd")
    // Did iBlinds leave this in from a generic driver? I don't think their devices have indicators
    def value = "when off"
    if (cmd.configurationValue[0] == 1) {value = "when on"}
    if (cmd.configurationValue[0] == 2) {value = "never"}
    logDesc("$device.displayName indicatorStatus is $value")
    createEvent([name: "indicatorStatus", value: value])
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
    logDesc("$device.displayName button was pressed")
    createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    logDebug("manufacturerId:   ${cmd.manufacturerId}")
    logDebug("manufacturerName: ${cmd.manufacturerName}")
    logDebug("productId:        ${cmd.productId}")
    logDebug("productTypeId:    ${cmd.productTypeId}")
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelStopLevelChange cmd) {
    logDebug("SwitchMultilevelStopLevelChange: $cmd")
    [createEvent(name:"switch", value:"on"), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    logDebug("BatteryReport $cmd")
    def map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
    } else {
        map.value = cmd.batteryLevel
    }
    createEvent(map)
    if (device.currentValue("battery") != map.value) logDesc("$device.displayName battery level is ${map.value}%")
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug("Skipping: $cmd")
}

def on() {
    logDebug("on()")
    setLevel(openPosition)
}

def off() {
    logDebug("off()")
    setLevel(0)
}

def open() {
    logDebug("open()")
    on()
}

def close() {
    logDebug("close()")
    off()
}

def setPosition(value) {
    logDebug("setPosition($value)")
    setLevel(value)
}

def setLevel(value, duration=0) {
    logDebug("setLevel($value, $duration)")
    def level = Math.max(Math.min(value as Integer, 99), 0)
    // Skip all of this since we should wait to hear back instead?
    /*
    if (level <= 0 || level >= 99) {
         sendEvent(name: "switch", value: "off")
         logDesc("$device.displayName switch is off")
         sendEvent(name: "windowShade", value: "closed")
         logDesc("$device.displayName windowShade is closed")
    } else {
        sendEvent(name: "switch", value: "on")
        logDesc("$device.displayName switch is on")
        sendEvent(name: "windowShade", value: "open")
        logDesc("$device.displayName windowShade is open")
    }
    sendEvent(name: "level", value: level, unit: "%")
    logDesc("$device.displayName level is ${level}%")
    */
    def setLevel = reverse ? 99 - level : level
    def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
    def delayTime = travelTime as int ?: 8000
    delayBetween([
        zwave.switchMultilevelV2.switchMultilevelSet(value: setLevel, dimmingDuration: dimmingDuration).format(),
        zwave.switchMultilevelV2.switchMultilevelGet().format()
    ], delayTime)
}

def getBattery() {
    logDebug("getBattery()")
    sendHubCommand(
        new hubitat.device.HubAction(zwave.batteryV1.batteryGet().format(),
                                     hubitat.device.Protocol.ZWAVE)
    )
}

def refresh() {
    logDebug("refresh()")
    delayBetween([
        // zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.switchMultilevelV2.switchMultilevelGet().format(),
        zwave.batteryV1.batteryGet().format(),
    ], 3000)
}

def logDebug(str) {
    if (settings.enableDebug) log.debug(str)
}

def logDesc(str) {
    if (settings.enableDesc) log.info(str)
}