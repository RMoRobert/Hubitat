/**
 *  Advanced HomeSeer HS-WS200+
 *
 *  Original Copyright 2018 HomeSeer
 *
 *  Modified from HomeSeer fork of WS-200+ ST DTH based on work by DarwinsDen DTH
 *  originally for the WD100 version 1.03
 *
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
 *	Original Author: HomeSeer
 *	Original Date: 12/2017
 *  Modified Date: 11/2019
 *
 *	Changelog:
 *
 *	Based on HomeSeer 1.0 Initial Version code
 *
 *  11/2019: Modified button events/numbers (odds = up paddle, events = down paddle) and events (pushed/held) for
 *           better use with Hubitat; ported ST namespaces and capabilities to Hubitat model
 *           Added "released" events (undocumented but switch appears to send them, so might as well make use)
 *
 *
 *   Button Mappings:
 *
 *   ACTION          BUTTON #   EVENT
 *   Single-Tap Up     1        pushed (also does switch "on")
 *   Single-Tap Down   2        pushed (also does switch "off")
 *   Double-Tap Up     3        pushed
 *   Double-Tap Down   4        pushed
 *   3 taps up  	   5        pushed
 *   3 taps down	   6        pushed
 *   4 taps up         7        pushed
 *   4 taps down       8        pushed
 *   5 taps up         9        pushed
 *   5 taps down       10       pushed
 *   Hold Up           1 	    held (followed by released); also does switch "on"
 *   Hold Down         2 	    held (followed by released); also does switch "off"
 *
 */
 
metadata {
	definition (name: "Advanced HomeSeer WS200+ Switch", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/HS-WS200%2B-Driver.groovy") {
		capability "Actuator"
		//capability "Indicator"
		capability "Switch"
		//capability "Polling"
		capability "Refresh"
		capability "Sensor"
        capability "PushableButton"
		capability "HoldableButton"
		capability "ReleasableButton"
        capability "Configuration"
		
		command "push", [[name:"btnNum", type:"NUMBER", description: "Button Number", constraints:["NUMBER"]]]
		command "hold", [[name:"btnNum", type:"NUMBER", description: "Button Number", constraints:["NUMBER"]]]
		command "release", [[name:"btnNum", type:"NUMBER", description: "Button Number", constraints:["NUMBER"]]]
		
        
        command "setSwitchModeNormal"
        command "setSwitchModeStatus"
		command "setDefaultLEDColor", [[name:"color", type:"NUMBER", description: "LED color (0=off; 1=red, 2=green, 3=blue, 4=magenta, 5=yellow, 6=cyan, 7=white)", constraints:["NUMBER"]]]
		command "setStatusLED", [[name:"led",type:"NUMBER", description:"LED (always set to 1 for switch)", constraints:["NUMBER"]],
								 [name:"color",type:"NUMBER", description:"LED color (0=off; 1=red, 2=green, 3=blue, 4=magenta, 5=yellow, 6=cyan, 7=white)", constraints:["NUMBER"]],
								 [name:"blink",type:"NUMBER", description:"Blink? (0=no, 1=blink)", constraints:["NUMBER"]]]		
        
        fingerprint mfr: "000C", prod: "4447", model: "3035"
}


    preferences {   
       input "reverseSwitch", "bool", title: "Reverse Switch",  defaultValue: false, required: false
       input "bottomled", "bool", title: "Turn on indicator LED when load is off",  defaultValue: false, required: false              
       input "color", "enum", title: "Default LED Color", options: ["White", "Red", "Green", "Blue", "Magenta", "Yellow", "Cyan"], description: "Select Color", required: false
       input "enableInfo", "bool", title: "Enable info logging", defaultValue: true, required: false
	   input "enableDebug", "bool", title: "Enable debug logging", defaultValue: true, required: false
	}
}

def parse(String description) {
	def result = null
    logDebug (description)
    if (description != "updated") {
	    def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])	
        if (cmd) {
		    result = zwaveEvent(cmd)
	    }
    }
    if (!result){
        logDebug("Parse returned ${result} for command ${cmd}")
    }
    else {
		logDebug("Parse returned ${result}")
    }   
	return result
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	createEvent([name: "switch", value: cmd.value ? "on" : "off", type: "physical"])
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	createEvent([name: "switch", value: cmd.value ? "on" : "off", type: "physical"])
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent([name: "switch", value: cmd.value ? "on" : "off", type: "digital"])
}


def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	logDebug "ConfigurationReport $cmd"
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	createEvent([name: "indicatorStatus", value: value])
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	logDebug "manufacturerId:   ${cmd.manufacturerId}"
	logDebug "manufacturerName: ${cmd.manufacturerName}"
    state.manufacturer=cmd.manufacturerName
	logDebug "productId:        ${cmd.productId}"
	logDebug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
    setFirmwareVersion()
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {	
    //updateDataValue("applicationVersion", "${cmd.applicationVersion}")
    logDebug ("received Version Report")
    logDebug "applicationVersion:      ${cmd.applicationVersion}"
    logDebug "applicationSubVersion:   ${cmd.applicationSubVersion}"
    state.firmwareVersion=cmd.applicationVersion+'.'+cmd.applicationSubVersion
    logDebug "zWaveLibraryType:        ${cmd.zWaveLibraryType}"
    logDebug "zWaveProtocolVersion:    ${cmd.zWaveProtocolVersion}"
    logDebug "zWaveProtocolSubVersion: ${cmd.zWaveProtocolSubVersion}"
    setFirmwareVersion()
    createEvent([descriptionText: "Firmware V"+state.firmwareVersion, isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) { 
    logDebug ("received Firmware Report")
    logDebug "checksum:       ${cmd.checksum}"
    logDebug "firmwareId:     ${cmd.firmwareId}"
    logDebug "manufacturerId: ${cmd.manufacturerId}"
    [:]
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
	[createEvent(name:"switch", value:"on"), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	sendEvent(tapUp1Response("digital"))
	delayBetween([
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	])
}

def off() {
	sendEvent(tapDown1Response("digital"))
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	])
}


/*
 *  Set dimmer to status mode, then set the color of the individual LED
 *
 *  led = 1-7
 *  color = 0=0ff
 *          1=red
 *          2=green
 *          3=blue
 *          4=magenta
 *          5=yellow
 *          6=cyan
 *          7=white
 */
def setStatusLed(led, color, blink) {    
    def cmds= []
    
    if(state.statusled1 == null) {    	
    	state.statusled1=0        
        state.blinkval=0
    }
    
    /* set led # and color */
    switch(led) {
    	case 1:
        	state.statusled1=color
            break
    }
    
    if(state.statusled1==0)
    {
    	// no LEDS are set, put back to NORMAL mode
        cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 13, size: 1).format() 
    }
    else
    {
    	// at least one LED is set, put to status mode
        cmds << zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 13, size: 1).format()
        // set the LED to color
        cmds << zwave.configurationV2.configurationSet(configurationValue: [color], parameterNumber: 21, size: 1).format()
        // check if LED should be blinking
        def blinkval = state.blinkval
        if(blink)
        {
            // set blink speed, also enables blink, 1=100ms blink frequency
            cmds << zwave.configurationV2.configurationSet(configurationValue: [5], parameterNumber: 31, size: 1).format()
            state.blinkval = blinkval
        }
        else
        {
            cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 31, size: 1).format()
            state.blinkval = blinkval
        }
    }
 	delayBetween(cmds, 500)
}

/*
 * Set switch to Normal mode (exit status mode)
 *
 */
def setSwitchModeNormal() {
	def cmds= []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 13, size: 1).format()
    delayBetween(cmds, 500)
}

/*
 * Set switch to Status mode (exit normal mode)
 *
 */
def setSwitchModeStatus() {
	def cmds= []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 13, size: 1).format()
    delayBetween(cmds, 500)
}

/*
 * Set the color of the LEDS for normal dimming mode, shows the current dim level
 */
def setDefaultColor(color) {
	def cmds= []
    cmds << zwave.configurationV2.configurationSet(configurationValue: [color], parameterNumber: 14, size: 1).format()
    delayBetween(cmds, 500)
}


def push(btnNum) {
	return buttonEvent(btnNum, "pushed")
}

def hold(btnNum) {
	return buttonEvent(btnNum, "held")
}

def release(btnNum) {
	return buttonEvent(btnNum, "released")
}

def refresh() {
	logDebug "refresh() called"
    configure()
	zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    if (debugEnable) logDebug "${device.label?device.label:device.name}: ${cmd}"
    def eventType = "pushed"
    def btnNum = 0
    if (cmd.sceneNumber == 1) { // Up paddle
        def mapping = [0: 1, 1: 1, 2: 1, 3: 3, 4: 5, 5: 7, 6: 9]
        btnNum = mapping[cmd.keyAttributes as int]
        if (cmd.keyAttributes == 2) eventType = "held"
        else if (cmd.keyAttributes == 1) eventType = "released"
    } else if (cmd.sceneNumber == 2) { // Down paddle
        def mapping = [0: 2, 1: 2, 2: 2, 3: 4, 4: 6, 5: 8, 6: 10]
        btnNum = mapping[cmd.keyAttributes as int]
        if (cmd.keyAttributes == 2) eventType = "held"
        else if (cmd.keyAttributes == 1) eventType = "released"
    } else {
        log.warn "Unable to parse: ${cmd}"
    }
    createEvent(buttonEvent(btnNum, eventType, "physical"))
}

def buttonEvent(button, value, type = "digital") {
    sendEvent(name:"lastEvent", value: "Button ${button} ${value}", displayed: false)
    if (infoEnable) logInfo "${device.label?device.label:device.name}: Button ${button} was ${value}"
    [name: value, value: button, isStateChange:true]
}

def setFirmwareVersion() {
   def versionInfo = ''
   if (state.manufacturer)
   {
      versionInfo=state.manufacturer+' '
   }
   if (state.firmwareVersion)
   {
      versionInfo=versionInfo+"Firmware V"+state.firmwareVersion
   }
   else 
   {
     versionInfo=versionInfo+"Firmware unknown"
   }   
   sendEvent(name: "firmwareVersion",  value: versionInfo, isStateChange: true, displayed: false)
}

def configure() {
   logDebug ("configure() called")
 
   sendEvent(name: "numberOfButtons", value: 10, displayed: false)
   def commands = []
   commands << setDimRatePrefs()   
   commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
   commands << zwave.versionV1.versionGet().format()
   delayBetween(commands,500)
}

def setDimRatePrefs() 
{
   logDebug ("set prefs")
   def cmds = []

	if (color)
    {
        switch (color) {
        	case "White":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 14, size: 1).format()
                break
      		case "Red":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 14, size: 1).format()
                break
            case "Green":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [2], parameterNumber: 14, size: 1).format()
                break
            case "Blue":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [3], parameterNumber: 14, size: 1).format()
                break
            case "Magenta":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [4], parameterNumber: 14, size: 1).format()
                break
            case "Yellow":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [5], parameterNumber: 14, size: 1).format()
                break
            case "Cyan":
            	cmds << zwave.configurationV2.configurationSet(configurationValue: [6], parameterNumber: 14, size: 1).format()
                break
            
            
      	}
    }    
   
      
   if (reverseSwitch)
   {
       cmds << zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 4, size: 1).format()
   }
   else
   {
      cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 4, size: 1).format()
   }
   
   if (bottomled)
   {
       cmds << zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()
   }
   else
   {
      cmds << zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()
   }
   
   //Enable the following configuration gets to verify configuration in the logs
   //cmds << zwave.configurationV1.configurationGet(parameterNumber: 7).format()
   //cmds << zwave.configurationV1.configurationGet(parameterNumber: 8).format()
   //cmds << zwave.configurationV1.configurationGet(parameterNumber: 9).format()
   //cmds << zwave.configurationV1.configurationGet(parameterNumber: 10).format()
   
   return cmds
}
 
def updated()
{
 def cmds= []
 cmds << setDimRatePrefs
 delayBetween(cmds, 500)
}

def logDebug(str) {
	if (enableDebug) log.debug (str)
}

def logInfo(str) {
	if (enableInfo) log.info(str)
}
