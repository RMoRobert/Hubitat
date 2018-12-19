/**
 *  HomeSeer HS-WS200+
 *
 *  Copyright 2018 HomeSeer, modified by RMoRobert
 *
 *  Modified from HomeSeer ST DTH, which was based the work by DarwinsDen device handler for the WD100 version 1.03
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
 *	Author: HomeSeer, modified by RMoRobert
 *	Date: 12/2017 (original), 12/2018 (modification)
 *
 *	Changelog:
 *
 *	1.0	(HomeSeer) Initial Version
 *  Modified for Hubitat
 *
 *
 *   Button Mappings -- note differences from HomeSeer driver!
 *
 * 
 *   ACTION          BUTTON#    BUTTON ACTION
 *   1 tap up          1        pushed (also, switch: on)
 *   2 taps up         2        pushed
 *   3 taps up         3        pushed
 *   4 taps up         4        pushed
 *   5 taps up         5        pushed
 *   Hold up           1        held   (also, switch: on)
 *   1 tap down        6        pushed (also, switch: off)
 *   2 taps down       7        pushed
 *   3 taps down       8        pushed
 *   4 taps down       9        pushed
 *   5 taps down       10       pushed
 *   Hold down         6        held   (also, switch: off)
 *
 */
 
metadata {
	definition (name: "HomeSeer WS200+ Switch (for Hubitat)", namespace: "RMoRobert", author: "HomeSeer, RMoRobert") {
		capability "Actuator"
		capability "Indicator"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
        capability "PushableButton"
		capability "HoldableButton"
        capability "Configuration"
        
        command "tapUp2"
        command "tapDown2"
        command "tapUp3"
        command "tapDown3"
        command "tapUp4"
        command "tapDown4"
        command "tapUp5"
        command "tapDown5"
        command "holdUp"
        command "holdDown"
        command "setStatusLed"
        command "setSwitchModeNormal"
        command "setSwitchModeStatus"
        command "setDefaultColor"
        
        fingerprint mfr: "000C", prod: "4447", model: "3035"
	}
}

def parse(String description) {
	def result = null
    log.debug (description)
    if (description != "updated") {
	    def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])	
        if (cmd) {
		    result = zwaveEvent(cmd)
	    }
    }
    if (!result){
        log.debug "Parse returned ${result} for command ${cmd}"
    }
    else {
		log.debug "Parse returned ${result}"
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
	log.debug "ConfigurationReport $cmd"
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	createEvent([name: "indicatorStatus", value: value])
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
    state.manufacturer=cmd.manufacturerName
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
    setFirmwareVersion()
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {	
    //updateDataValue("applicationVersion", "${cmd.applicationVersion}")
    log.debug ("received Version Report")
    log.debug "applicationVersion:      ${cmd.applicationVersion}"
    log.debug "applicationSubVersion:   ${cmd.applicationSubVersion}"
    state.firmwareVersion=cmd.applicationVersion+'.'+cmd.applicationSubVersion
    log.debug "zWaveLibraryType:        ${cmd.zWaveLibraryType}"
    log.debug "zWaveProtocolVersion:    ${cmd.zWaveProtocolVersion}"
    log.debug "zWaveProtocolSubVersion: ${cmd.zWaveProtocolSubVersion}"
    setFirmwareVersion()
    createEvent([descriptionText: "Firmware V"+state.firmwareVersion, isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) { 
    log.debug ("received Firmware Report")
    log.debug "checksum:       ${cmd.checksum}"
    log.debug "firmwareId:     ${cmd.firmwareId}"
    log.debug "manufacturerId: ${cmd.manufacturerId}"
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
def setStatusLed (led,color,blink) {    
    def cmds= []
    
    if(state.statusled1==null) {    	
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


def poll() {
	zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def refresh() {
	log.debug "refresh() called"
    configure()
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    log.debug("sceneNumber: ${cmd.sceneNumber} keyAttributes: ${cmd.keyAttributes}")
    def result = []
    
    switch (cmd.sceneNumber) {
      case 1:
          // Up
          switch (cmd.keyAttributes) {
              case 0:
                   // Press Once
                  result += createEvent(tapUp1Response("physical"))  
                  result += createEvent([name: "switch", value: "on", type: "physical"])   
                  break
              case 1:
                  result=createEvent([name: "switch", value: "on", type: "physical"])
                  break
              case 2:
                  // Hold
                  result += createEvent(holdUpResponse("physical"))  
                  result += createEvent([name: "switch", value: "on", type: "physical"])    
                  break
              case 3: 
                  // 2 Times
                  result +=createEvent(tapUp2Response("physical"))                                  
                  break
              case 4:
                  // 3 times
                  result=createEvent(tapUp3Response("physical"))
                  break
              case 5:
                  // 4 times
                  result=createEvent(tapUp4Response("physical"))
                  break
              case 6:
                  // 5 times
                  result=createEvent(tapUp5Response("physical"))
                  break
              default:
                  log.debug ("unexpected up press keyAttribute: $cmd.keyAttributes")
          }
          break
          
      case 2:
          // Down
          switch (cmd.keyAttributes) {
              case 0:
                  // Press Once
                  result += createEvent(tapDown1Response("physical"))
                  result += createEvent([name: "switch", value: "off", type: "physical"]) 
                  break
              case 1:
                  result=createEvent([name: "switch", value: "off", type: "physical"])
                  break
              case 2:
                  // Hold
                  result += createEvent(holdDownResponse("physical"))
                  result += createEvent([name: "switch", value: "off", type: "physical"]) 
                  break
              case 3: 
                  // 2 Times
                  result+=createEvent(tapDown2Response("physical"))
                  if (doubleTapDownToDim)
                  {
                     result += setLevel(25)
                     result += response("delay 5000")
                     result += response(zwave.switchMultilevelV1.switchMultilevelGet())
                  }  
                  break
              case 4:
                  // 3 Times
                  result=createEvent(tapDown3Response("physical"))
                  break
              case 5:
                  // 4 Times
                  result=createEvent(tapDown4Response("physical"))
                  break
              case 6:
                  // 5 Times
                  result=createEvent(tapDown5Response("physical"))
                  break
              default:
                  log.debug ("unexpected down press keyAttribute: $cmd.keyAttributes")
           } 
           break
           
      default:
           // unexpected case
           log.debug ("unexpected scene: $cmd.sceneNumber")
   }  
   return result
}

def tapUp1Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▲")
	[name: "pushed", value: "1", descriptionText: "$device.displayName Tap-Up-1 (button 1) pushed", 
       isStateChange: true, type: "$buttonType"]
}

def tapDown1Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▼")
	[name: "pushed", value: "6", descriptionText: "$device.displayName Tap-Down-1 (button 6) pushed", 
      isStateChange: true, type: "$buttonType"]
}

def tapUp2Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▲▲")
	[name: "pushed", value: "2", descriptionText: "$device.displayName Tap-Up-2 (button 2) pushed", 
       isStateChange: true, type: "$buttonType"]
}

def tapDown2Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▼▼")
	[name: "pushed", value: "7", descriptionText: "$device.displayName Tap-Down-2 (button 7) pushed",
      isStateChange: true, type: "$buttonType"]
}

def tapUp3Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▲▲▲")
	[name: "pushed", value: "3", descriptionText: "$device.displayName Tap-Up-3 (button 3) pushed", 
    isStateChange: true, type: "$buttonType"]
}

def tapUp4Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▲▲▲▲")
	[name: "pushed", value: "4", descriptionText: "$device.displayName Tap-Up-4 (button 4) pushed",
    isStateChange: true, type: "$buttonType"]
}

def tapUp5Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▲▲▲▲▲")
	[name: "pushed", value: "5", descriptionText: "$device.displayName Tap-Up-5 (button 5) pushed", 
    isStateChange: true, type: "$buttonType"]
}

def tapDown3Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▼▼▼")
	[name: "pushed", value: "8", descriptionText: "$device.displayName Tap-Down-3 (button 8) pushed",
    isStateChange: true, type: "$buttonType"]
}

def tapDown4Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▼▼▼▼")
	[name: "pushed", value: "9", descriptionText: "$device.displayName Tap-Down-4 (button 9) pushed", 
    isStateChange: true, type: "$buttonType"]
}

def tapDown5Response(String buttonType) {
    sendEvent(name: "status" , value: "Tap ▼▼▼▼▼")
	[name: "pushed", value: "10", descriptionText: "$device.displayName Tap-Down-5 (button 10) pushed",
    isStateChange: true, type: "$buttonType"]
}

def holdUpResponse(String buttonType) {
    sendEvent(name: "status" , value: "Hold ▲")	 
	[name: "pushed", value: "1", descriptionText: "$device.displayName Hold-Up-1 (button 1) held",
    isStateChange: true, type: "$buttonType"]
}

def holdDownResponse(String buttonType) {
    sendEvent(name: "status" , value: "Hold ▼")
	[name: "pushed", value: "6", descriptionText: "$device.displayName Hold-Down-1 (button 6) held",
    isStateChange: true, type: "$buttonType"]
}

def tapUp2() {
	sendEvent(tapUp2Response("digital"))
}

def tapDown2() {
	sendEvent(tapDown2Response("digital"))
}

def tapUp3() {
	sendEvent(tapUp3Response("digital"))
}

def tapDown3() {
	sendEvent(tapDown3Response("digital"))
}

def tapUp4() {
	sendEvent(tapUp4Response("digital"))
}

def tapDown4() {
	sendEvent(tapDown4Response("digital"))
}

def tapUp5() {
	sendEvent(tapUp5Response("digital"))
}

def tapDown5() {
	sendEvent(tapDown5Response("digital"))
}

def holdUp() {
	sendEvent(holdUpResponse("digital"))
}

def holdDown() {
	sendEvent(holdDownResponse("digital"))
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
   log.debug ("configure() called") 
   sendEvent(name: "numberOfButtons", value: 10, displayed: false)
   def commands = []
   commands << setDimRatePrefs()   
   commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
   commands << zwave.versionV1.versionGet().format()
   delayBetween(commands,500)
}

def setDimRatePrefs() 
{
   log.debug ("set prefs")
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
