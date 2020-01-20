/*
 * =============================  Inovelli Notification LED Child (Driver) ===============================
 *
 *  Copyright 2020 Robert Morris
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
 *  v1.0 - Initial Release
 */ 

metadata {
    definition (name: "Inovelli Off LED", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Inovelli-Off-LED.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
    }
       
    preferences {
        input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true)
    }
}

def installed(){
    log.debug "Installed..."
    doSendEvent("level", 100) // default value
    doSendEvent("switch", "on") // default value

    initialize()
}

def updated(){
    log.debug "Updated..."
    initialize()
}

def initialize() {
    log.debug "Initializing"
    int disableTime = 1800
    if (enableDebug) {
        log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
        runIn(disableTime, debugOff)
    }
    //configure()
}

def debugOff() {
    log.warn("Disabling debug logging")
    device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

def on() {    
    logDebug("Setting off LED to on...")
    def level = device.currentValue("level") ?: 100
    def scaledLevel = Math.round(level / 10)
    if (scaledLevel == 0 && device.currentValue("level") != 0) scaledLevel = 1
    if (scaledLevel < 0 || scaledLevel > 10) scaledLevel = 10
    parent.setOffLEDLevel(scaledLevel)
    doSendEvent("level", level, "%")
    doSendEvent("switch", "on")
}

def off() {
    logDebug("Setting off LED to off...")
    parent.setOffLEDLevel(0)
    doSendEvent("switch", "off")
}

def setLevel(value) {
    logDebug("Setting off LED level to $value...")
    if (value < 0 || value > 100) value = 100
    if (value == 0) {
        return off()
    }
    def scaledValue = Math.round(value/10)
    if (scaledValue == 0 && value > 0) scaledValue = 1
    parent.setOffLEDLevel(scaledValue)
    doSendEvent("level", value, "%")
    if (scaledValue >= 1) doSendEvent("switch", "on")
}

def setLevel(value, rate) {
    logDebug("Transition rate not supported; discarding value and setting level.")
    setLevel(value)
}

def doSendEvent(eventName, eventValue, eventUnit=null) {
    logDebug("Creating event for $eventName...")
    def descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"
    if (device.currentValue("eventName") != eventValue) logDesc(descriptionText)
    def event
    if (eventUnit) {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
    } else {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
    }
    return event
}

def logDebug(str) {
    if (settings.enableDebug) log.debug(str)
}

def logDesc(str) {
    if (settings.enableDesc) log.info(str)
}
