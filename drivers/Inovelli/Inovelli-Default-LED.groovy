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

import groovy.transform.Field

metadata {
    definition (name: "Inovelli Default LED", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Inovelli-Default-LED.groovy") {
        capability "Actuator"
        capability "ColorControl"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"

        attribute "colorName", "string"
    }
       
   preferences {
        input(name: "hueModel", type: "enum", description: "", title: "Hue (color) model",
              options:[["default":"Hubitat default (0-100)"],["degrees":"Degrees (0-360)"],["inovelli":"Inovelli (0-255)"]], defaultValue: "default")
        input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true)
    }
}

def installed(){
    log.debug "Installed..."

    doSendEvent("saturation", 100) // default value, will never change
    doSendEvent("hue", 0) // default value
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
    logDebug("Setting default LED to on...")
    def level = device.currentValue("level") ?: 100
    def scaledLevel = Math.round(level / 10)
    if (scaledLevel == 0 && device.currentValue("level") != 0) scaledLevel = 1
    if (scaledLevel < 0 || scaledLevel > 10) scaledLevel = 10
    parent.setDefaultLEDLevel(scaledLevel)
    doSendEvent("level", level, "%")
    doSendEvent("switch", "on")
}

def off() {
    logDebug("Setting default LED to off...")
    parent.setDefaultLEDLevel(0)
    doSendEvent("switch", "off")
}

def setLevel(value) {
    logDebug("Setting default LED level to $value...")
    if (value < 0 || value > 100) value = 100
    if (value == 0) {
        return off()
    }
    def scaledValue = Math.round(value/10)
    if (scaledValue == 0 && value > 0) scaledValue = 1
    parent.setDefaultLEDLevel(scaledValue)
    doSendEvent("level", value, "%")
    if (scaledValue >= 1) doSendEvent("switch", "on")
}

def setLevel(value, rate) {
    logDebug("Transition rate not supported; discarding value and setting level.")
    setLevel(value)
}

def setColor(value) {
    logDebug("Setting default LED color to $value (note: only hue and level values supported; saturation is ignored)...")
    if (value.hue == null ) {
        logDebug("Exiting setColor because no hue set")
        return
    }
    logDebug("Setting child LED hue to $value...")
    BigDecimal scaledHue = value.hue
    if (hueModel == 'default') scaledHue = Math.round(scaledHue * 2.55)
    else if (hueModel == 'degrees') scaledHue = Math.round(scaledHue / 1.41)
    if (h > 360) h = 100 // TODO: better range checking?
    doSendEvent("hue", value.hue, null)
    setGenericName(value.hue)
    parent.setDefaultLEDColor(scaledHue)
    if (value.level != null) {
        pauseExecution(250)
        def scaledValue = Math.round(value.level/10)
        if (scaledValue < 0 || scaledValue > 10) scaledValue = 10
        if (value.level < 1) scaledValue = 1
        parent.setDefaultLEDLevel(scaledValue)
        doSendEvent("level", value.level != 0 ? value.level : 1, "%")
    }
}

def setHue(value) {
    logDebug("Setting child LED hue to $value...")
    BigDecimal scaledHue = value
    if (hueModel == 'default') scaledHue = Math.round(scaledHue * 2.55)
    else if (hueModel == 'degrees') scaledHue = Math.round(scaledHue / 1.41)
    if (h > 360) h = 100 // TODO: better range checking?
    doSendEvent("hue", scaledHue, null)
    setGenericName(value)
    parent.setDefaultLEDColor(scaledHue)
}

def setSaturation(value) {
    logDebug("Child LED saturation control not supported; ignoring command")
}

def doSendEvent(eventName, eventValue, eventUnit=null) {
    logDebug("Creating event for $eventName...")
    def descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"
    logDesc(descriptionText)
    def event
    if (eventUnit) {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
    } else {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
    }
    return event
}

// Hubiat-provided color/name mappings
def setGenericName(hue){
    def colorName
    hue = hue.toInteger()
    if (hueModel == 'default') hue = (hue * 3.6)
    else if (hueModel == 'inovelli') hue = (hue * 1.41)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
        default: colorName = "Unspecified"
            break
    }
    if (device.currentValue("colorName") != colorName) doSendEvent("colorName", colorName, null)
}

def logDebug(str) {
    if (settings.enableDebug) log.debug(str)
}

def logDesc(str) {
    if (settings.enableDesc) log.info(str)
} 
