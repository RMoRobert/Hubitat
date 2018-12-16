/**
 * ==========================  Dimmer Button Controller (Child  App) ==========================
 *  Copyright 2018 Robert Morris
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
 *
 *  Author: Robert Morris
 *
 * Version: 0.9.1 Beta
 *
 * CHANGELOG
 * 0.9.1 Beta - (2018-12-15) Public beta release (bugfix from 0.9)
 *
 */

definition(
        name: "Dimmer Button Controller (Child App)",
        namespace: "RMoRobert",        
        parent: "RMoRobert:Dimmer Button Controller",
        author: "Robert Morris",
        description: "Do not install directly. Install Dimmer Button Controller app, then create new automations using that app.",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: ""
)

preferences {
	page(name: "pageMain")
	page(name: "pageFinal")
	page(name: "pageButtonConfig")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Dimmer Button Controller", uninstall: true, install: false, nextPage: "pageFinal") {
        section("Choose devices") {            
            input(name: "buttonDevice", type: "capability.pushableButton", title: "Select button controller:",
                  description: "", multiple: false, required: true, submitOnChange: true)
            input(name: "bulbs", type: "capability.switch", title: "Select lights for this controller:",
                  description: "", multiple: true, required: true, submitOnChange: true)
		}
		
        if(buttonDevice && bulbs) {
			if (!app.getLabel()) app.updateLabel(getDefaultLabel())
			section("Configure buttons") {
				(1..buttonDevice.currentValue('numberOfButtons')).each {
					def num = it
					//buttonSection(["btnNum": num])
					 href(name: "pageButtonConfigHref",
						page: "pageButtonConfig",
						params: [btnNum: num],
						  title: "Button ${num}",
						  description: getButtonConfigDescription(num))
				}
			}
		}
		
		section("Advanced options", hideable: true, hidden: true) {
			input(name: "transitionTime", type: "number", title: "Transition time", description: "Number of seconds (0 for fastest bulb/dimmer driver allows)", required: true, defaultValue: 0)
			input(name: "dimStep", type: "number", title: "Dimming buttons change level +/- by", description: "0-100", required: true, defaultValue: 10)
			input(name: "debugLogging", type: "bool", description: "", title: "Enable debug logging")
			input(name: "traceLogging", type: "bool", description: "", title: "Enable verbose/trace logging (for development)")
			
		}
	}
}

def pageFinal() {
	dynamicPage(name: "pageFinal", title: "Dimmer Button Controller", uninstall: true, install: true) {
		section("Name app and configure modes") {
				label(title: "Assign a name", required: true)
				input("modes", "mode", title: "Only when mode is", multiple: true, required: false)
		}
	}
}

def getDefaultLabel() {
	def defaultLabel = "${buttonDevice.displayName} Dimmer Button Controller"	
    return defaultLabel
}

def getMaxPressNum() {
	return 5
}

def pageButtonConfig(params) {
	dynamicPage(name: "pageButtonConfig", title: "Button ${params?.btnNum} Configuration", uninstall: true, install: false) {
        if(settings["buttonDevice"] && settings["bulbs"] && params?.btnNum) {
			section("Actions for button ${params.btnNum}") {
				input(name: "btn${params.btnNum}Action", type: "enum", title: "Do...",
					  options: ["Turn on","Brighten","Dim","Turn off"], submitOnChange: true)
			}
			if (settings["btn${params.btnNum}Action"]) {
				switch(settings["btn${params.btnNum}Action"]) {
					case "Turn on":
						makeTurnOnSection(params.btnNum)
						break
					case "Brighten":
						makeDimUpSection()
					break
					case "Dim":
						makeDimDownSection()
					break
					case "Turn off":
						makeTurnOffSection()
					break
					default:
						paragraph("Not set")
				}
			}
        }
	}
}
	
def buttonSection(params) {
	def btnNum = params.btnNum
	section(title: "Button ${btnNum}") {
			input(name: "btn${btnNum}Action", type: "enum", title: "Do...", options: ["Turn on","Brighten","Dim","Turn off"], submitOnChange: true)
	}	
	if (settings["btn${btnNum}Action"]) {
		switch(settings["btn${btnNum}Action"]) {
			case "Turn on":
				makeTurnOnSection(btnNum)
				break
			case "Brighten":
				makeDimUpSection()
			break
			case "Dim":
				makeDimDownSection()
			break
			case "Turn off":
				makeTurnOffSection()
			break
			default:
				paragraph("Not set")
		}
	}
}

def getButtonConfigDescription(btnNum) {
	def desc = "Click/tap to configure button ${btnNum}\n"
	def settingName = "btn${btnNum}Action"
	logDebug (settings[settingName])
	if (settings["btn${btnNum}Action"] == "Turn on") {
		for (pressNum in 1..getMaxPressNum()) {
			if (getDoesPressNumHaveAction(btnNum, pressNum)) {
				desc += "\nPRESS ${pressNum}:"
				for (j in bulbs) {
					def bulbSettingB = "btn${btnNum}_${j.id}B_press${pressNum}"
					def bVal = settings["${bulbSettingB}"]
					def bulbSettingCT = "btn${btnNum}_${j.id}CT_press${pressNum}"
					def ctVal = settings["${bulbSettingCT}"]
					def bulbSettingH = "btn${btnNum}_${j.id}H_press${pressNum}"
					def hVal = settings["${bulbSettingH}"]
					def bulbSettingS = "btn${btnNum}_${j.id}S_press${pressNum}"
					def sVal = settings["${bulbSettingS}"]
					if (settings["${bulbSettingB}"] || settings["${bulbSettingCT}"]) {
						desc += "\nTurn on ${j} - "
					} else {
						desc += "\nTurn on ${j}"
					}
					if (hVal) desc += "Hue: ${hVal}  "
					if (sVal) desc += "Saturation: ${sVal}  "
					if (bVal != null) desc += "Brightness: ${bVal}  "
					if (ctVal) desc += "Color Temperature: ${ctVal} "
				}
			}
		}
	}
	else {
		if (settings["btn${btnNum}Action"]) {
			desc += settings["btn${btnNum}Action"]
		}
	}
	return desc
}

def makeTurnOnSection(btnNum) {
	for (pressNum in 1..getMaxPressNum()) {
		if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, pressNum-1)) {
			section("Press ${pressNum}" , hideable: true, hidden: false) {
				for (j in bulbs) {
					paragraph("<b>Turn on ${j}</b>")
					input(name: "btn${btnNum}_${j.id}B_press${pressNum}", type: "number", title: "and set to this brightness:", description: "0-100 (0 to turn/keep off)", submitOnChange: true, required: false)
					input(name: "btn${btnNum}_${j.id}CT_press${pressNum}", type: "number", title: "and set to this color temperature:", description: "2000-6500", submitOnChange: true, required: false)
					input(name: "btn${btnNum}_${j.id}H_press${pressNum}", type: "number", title: "and set to this color with hue value:", description: "0-100", required: false)
					input(name: "btn${btnNum}_${j.id}S_press${pressNum}", type: "number", title: "and saturation value:", description: "0-100", submitOnChange: true, required: false)
					}
				paragraph("All fields optional; hue and saturation values will be ignored if all HSB values not set, and color temperature takes precedence over hue and saturation.")
			}
		}
	}
}

def makeTurnOffSection() {
	section {
		paragraph("Turn off all (TODO: Will allow choosing individual bulbs here soon)")
	}
}

def makeDimUpSection() {
	section {
		paragraph("Dim all bulbs/dimmers up if on (TODO: Will allow choosing individual or all bulbs here soon)")
	}
}

def makeDimDownSection() {
	section {
		paragraph("Dim all bulbs/dimmers down if on (TODO: Will allow choosing individual or all bulbs here soon)")
	}
}

def isModeOK() {
    def retVal = !modes || modes.contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${retVal}"
    return retVal
}

def buttonHandler(evt) {
	logTrace "Running buttonHandler..."
	if (!isModeOK) {
		return
	}
	def btnNum = evt.value
	def pressType = evt.name
	logTrace "Button ${btnNum} was ${pressType}"
	def action = settings["btn${btnNum}Action"]
	switch (action) {
	case "Turn on":
		def pressNum = getPressNum(btnNum)
		logDebug "Action \"Turn on\" specified for button ${btnNum} press ${pressNum}"
		try {
			for (j in bulbs) {
				def bulbSettingB = "btn${btnNum}_${j.id}B_press${pressNum}"
				def bVal = settings["${bulbSettingB}"]
				def bulbSettingCT = "btn${btnNum}_${j.id}CT_press${pressNum}"
				def ctVal = settings["${bulbSettingCT}"]
				def bulbSettingH = "btn${btnNum}_${j.id}H_press${pressNum}"
				def hVal = settings["${bulbSettingH}"]
				def bulbSettingS = "btn${btnNum}_${j.id}S_press${pressNum}"
				def sVal = settings["${bulbSettingS}"]
				if (bVal != null && (!hVal || !sVal)) {
					setBri(j, bVal)
				}
				if (ctVal) {
					setCT(j, ctVal)
				}
				if (hVal && sVal && bVal != null && !ctVal) {
					setHSB(j, hVal, sVal, bVal)
				}
			}
		} catch (e) {
			log.debug "Error when running turn-on action: ${e}"
		}
		incrementPressNum(btnNum)
		runIn(15, resetPressNum, [data: ["btnNum": btnNum]])
		break
	case "Turn off":
		logDebug "Action \"turn off\" specified for button ${btnNum} press ${pressNum}"
		try {
			turnOff(bulbs)
			(1..buttonDevice.currentValue('numberOfButtons')).each {
				if (getPressNum(it)) {
					resetPressNum(["btnNum": it])
				}
			}
		} catch (e) {
			log.warn "Error when running turn-off action: ${e}"
		}
		break
	case "Dim":
		logDebug "Action \"dim\" specified for button ${btnNum}"
		dimDownIfOn(bulbs, dimStep)
		break
	case "Brighten":
		logDebug "Action \"brighten\" specified for button ${btnNum}"
		dimUpIfOn(bulbs, dimStep)
		break
	default:
		logDebug "Action not specified for button ${btnNum} press ${pressNum}"
	}
}

def turnOn(devices) {
	logDebug "Running turnOn for $devices..."
	devices.on()
}

def turnOff(devices) {
	logDebug "Running turnOff for $devices..."
	devices.off()
}

def setBri(devices, level) {
	logDebug "Dimming (to $level with rate ${transitionTime}): $devices"
	try {
		devices.setLevel(level, transitionTime)
	} catch (e) {
		log.debug("Unable to set brightness level on ${devices}: ${e}")
	}
}

def dimUpIfOn(devices, changeBy) {
	devs = []
	devices.each {
		if (it.currentSwitch == "on") devs.add(it)
	}
	// TODO: Would it help to do all at same time if levels all same?
	devs.each {
		try {
			def currLvl = it.currentLevel
			if (currLvl && currLvl < 100) {
				it.setLevel(currLvl + changeBy, transitionTime)
			}
		} catch (e) {
			log.warn("Unable to dim up ${devices}: ${e}")
		}
	}
}

def dimDownIfOn(devices, changeBy) {
	devs = []
	devices.each {
		if (it.currentSwitch == "on") devs.add(it)
	}
	// TODO: Would it help to do all at same time if levels all same?
	devs.each {
		try {
			def currLvl = it.currentLevel
			def newLevel
			if (currLvl && currLvl > 0) {
				newLevel = currLvl - changeBy
			}
			if (newLevel <= 0) newLevel = 1
			it.setLevel(newLevel, transitionTime)
		} catch (e) {
			log.warn("Unable to dim ${devices}: ${e}")
		}	
	}
}

def setHSB(devices, hueVal, satVal, briVal) {
    logDebug "Running set HSB for $devices..."
    def targetColor = [:]
	try {
		targetColor.hue = hueVal.toInteger()
		targetColor.saturation = satVal.toInteger()
		targetColor.level = briVal.toInteger()
		devices.setColor(targetColor)
	} catch (e) {
		log.warn("Unable to set color for ${devices}: ${e}")
	}
}

def setCT(devices, ct) {
    logDebug "Running setCT with $ct for $devices..."
	try {
    	devices.setColorTemperature(ct)
	} catch (e) {
		log.warn("Unable to set color temperature for ${devices}: ${e}")
	}
}

/* To emulate Hue Dimmer, this app tracks 1-5 button presses
 * for one or more buttons on the button device. This retrieves
 * the current press number for the provided button number.  */
def getPressNum(buttonNum) {
	switch(settings["btn${buttonNum}Action"]) {
		case "Turn on":
			def pressNum = atomicState["pressNum${buttonNum}"]
			if (!pressNum) {
				pressNum = 1
				atomicState["pressNum${buttonNum}"] = pressNum
			}
			logTrace("getPressNum called, returning ${pressNum}")
			return pressNum
			break
		default:
			logTrace "getPressNum for button ${buttonNum} was called but ${buttonNum} is not a special button"
	}
}

/* To emulate Hue Dimmer, this app tracks 1-5 button presses
 * for one or more buttons on the button device. This increases (rolling
 * over if needed) the current press number for the provided button number
 * and is intended to be called after the button is pressed.
 */
def incrementPressNum(buttonNum) {
	def currPress = getPressNum(buttonNum)
	def nextPress = 2
	if (currPress) {
		nextPress = currPress + 1
		if (nextPress > getMaxPressNum() || !getDoesPressNumHaveAction(buttonNum, nextPress)) {
			resetPressNum(["btnNum": buttonNum])
		}
		else {
			atomicState["pressNum${buttonNum}"] = nextPress
			
		}
	}
	logTrace "Incremented pressNum for button ${buttonNum}: ${currPress} to ${getPressNum(buttonNum)}"
}

/* Resets next press for specified button to 1, intended to be called after
 * timeout has elapsed or "off"-type button pressed to reset the count.
 * Usage: params with map; key = "btnNum" and value = button number as integer,
 * e.g., params = {btnNum: 1} */
def resetPressNum(params) {
	logTrace "Running resetPresNum with params: ${params}"
	def btnNum = (params.get("btnNum"))
	if (btnNum) {
		atomicState["pressNum${btnNum}"] = 1
	}
	else {
		log.error "resetPressNum called with improper parameters: ${params}"
	}
	logTrace "Button press reset for button ${btnNum} to " + atomicState["pressNum${btnNum}"]
}

def getDoesPressNumHaveAction(btnNum, pressNum) {
	logTrace "Running getDoesPressNumHaveAction for btn ${btnNum} press ${pressNum}"
	def hasAction = false
	if (settings["btn${btnNum}Action"] && pressNum == 1) {
		hasAction = true
	}
	else {
		for (j in bulbs) {
			def bulbSettingB = "btn${btnNum}_${j.id}B_press${pressNum}"
			def bVal = settings["${bulbSettingB}"]
			def bulbSettingCT = "btn${btnNum}_${j.id}CT_press${pressNum}"
			def ctVal = settings["${bulbSettingCT}"]
			def bulbSettingH = "btn${btnNum}_${j.id}H_press${pressNum}"
			def hVal = settings["${bulbSettingH}"]
			def bulbSettingS = "btn${btnNum}_${j.id}S_press${pressNum}"
			def sVal = settings["${bulbSettingS}"]
			if (bVal && (!hVal || !sVal)) {
				hasAction = true
			}
			if (ctVal) {
				hasAction = true
			}
			if (hVal && sVal && bVal && !ctVal) {
				hasAction = true
			}
		}
	}
	logTrace "Returning hasAction = ${hasAction}"
	return hasAction
}

//=========================================================================
// App Methods
//=========================================================================

def installed() {
    log.trace "Installed"
    initialize()
}

def updated() {
    log.trace "Updated"
    unsubscribe()
    initialize()
}

def initialize() {
	log.trace "Initialized"
	subscribe(buttonDevice, "pushed", buttonHandler)	
}
				   
// Helper methods
def logDebug(string) {
	if (debugLogging) {
		log.debug(string)
	}
}
				   
def logTrace(string) {
	if (debugLogging || traceLogging) {
		log.trace(string)
	}
}
				   
