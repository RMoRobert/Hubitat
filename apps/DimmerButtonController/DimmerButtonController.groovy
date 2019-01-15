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
 * Version: 1.6
 *
 * CHANGELOG
 * 1.6 (2019-01-14) - New "held" functionality
 * 1.5 (2019-01-02) - New press/release dimming action
 * 0.9 Beta - (2018-12-27) First public release
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
            input(name: "buttonDevice", type: "capability.pushableButton", title: "Select button controller:", multiple: false, required: true, submitOnChange: true)
            input(name: "bulbs", type: "capability.switch", title: "Select lights for this controller:", multiple: true, required: true, submitOnChange: true)
			paragraph("Actions to turn on and off lights below allow you to choose scenes; the above lights will be used for dim/brighten actions and non-scene \"off\" actions. It is recommended you choose all bulbs used in your scenes (though it is optional if you do not use dim/brighten and use only scene-enabled features below).")
		}
		
        if(buttonDevice && bulbs) {
			if (!app.getLabel()) app.updateLabel(getDefaultLabel())
			section("Configure buttons") {
				(1..getNumberOfButtons()).each {
					def num = it
					 href(name: "pageButtonConfigHref",
						page: "pageButtonConfig",
						params: [btnNum: num, action: "pushed"],
						  title: "Button ${num} - Pressed",
						  description: getButtonConfigDescription(num, "pushed"))
					if (buttonDevice.hasCapability("HoldableButton")) {
						href(name: "pageButtonConfigHref",
						page: "pageButtonConfig",
						params: [btnNum: num, action: "held"],
						  title: "Button ${num} - Held",
						  description: getButtonConfigDescription(num, "held"))
					}
					//if (buttonDevice.hasCapability("DoubleTapableButton")) {
					//	paragraph("Button ${num} - Double-Tapped: Functionality not yet supported by Dimmer button Controller")
					//}
				}
			}
		}
		
		section("Advanced options", hideable: true, hidden: true) {
			input(name: "transitionTime", type: "number", title: "Transition time", description: "Number of seconds (0 for fastest bulb/dimmer driver allows)", required: true, defaultValue: 0)
			input(name: "dimStep", type: "number", title: "Dimming buttons change level +/- by (unless \"dim while holding\" enabled on supported devices)", description: "0-100", required: true, defaultValue: 10)
			input(name: "debugLogging", type: "bool", title: "Enable debug logging")
			//input(name: "traceLogging", type: "bool", title: "Enable verbose/trace logging (for development)")
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

def getNumberOfButtons() {
	def num = buttonDevice.currentValue('numberOfButtons')
	if (num) {
		return num
	} else {
		log.warn "Device did not specify number of buttons; using 1. Check or change this in the driver if needed."
		return 1
	}
}

def getMaxPressNum() {
	return 5
}

def pageButtonConfig(params) {
	dynamicPage(name: "pageButtonConfig", title: "Button ${params?.btnNum} Configuration", uninstall: true, install: false) {
        if(settings["buttonDevice"] && settings["bulbs"] && params?.btnNum) {
			def btnActionSettingName = "btn${params.btnNum}Action"
			section("Actions for button ${params.btnNum}") {
				if (params.action == "held") btnActionSettingName = "btn${params.btnNum}HeldAction"
				input(name: btnActionSettingName, type: "enum", title: "Do...",
					options: ["Turn on", "Turn on scene", "Brighten", "Dim", "Turn off last used scene", "Turn off scene", "Turn off"], submitOnChange: true)
			}
			if (settings[btnActionSettingName]) {
				switch(settings[btnActionSettingName]) {
					case "Turn on":
						makeTurnOnSection(params.btnNum, params.action)
						break
					case "Turn on scene":
						makeTurnOnSceneSection(params.btnNum, params.action)
						break
					case "Brighten":
						makeDimUpSection(params.btnNum, params.action)
					break
					case "Dim":
						makeDimDownSection(params.btnNum, params.action)
					break
					case "Turn off last used scene":
						makeTurnOffLastSceneSection()
					break					
					case "Turn off scene":
						makeTurnOffSceneSection(params.btnNum, params.action)
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

def getButtonConfigDescription(btnNum, strAction) {
	def desc = "Click/tap to configure button ${btnNum}\n"
	def actionSettingName
	if (strAction == "held") {
		actionSettingName = "btn${btnNum}HeldAction"
	} else {
		// pushed:
		actionSettingName = "btn${btnNum}Action"
	}
	if (settings[actionSettingName] == "Turn on") { //TODO: Add checks here for press (show max presses) vs held (show only one hold)
		if (strAction != "held") {
			for (pressNum in 1..getMaxPressNum()) {
				if (getDoesPressNumHaveAction(btnNum, pressNum)) {
					desc += "\nPRESS ${pressNum}: "
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
		} else {
			for (j in bulbs) {
				def bulbSettingB = "btn${btnNum}_${j.id}B_Held"
				def bVal = settings["${bulbSettingB}"]
				def bulbSettingCT = "btn${btnNum}_${j.id}CT_Held"
				def ctVal = settings["${bulbSettingCT}"]
				def bulbSettingH = "btn${btnNum}_${j.id}H_Held"
				def hVal = settings["${bulbSettingH}"]
				def bulbSettingS = "btn${btnNum}_${j.id}S_Held"
				def sVal = settings["${bulbSettingS}"]
				if (settings["${bulbSettingB}"] || settings["${bulbSettingCT}"]) {
					desc += "Turn on ${j} - "
				} else {
					desc += "Turn on ${j}"
				}
				if (hVal) desc += "Hue: ${hVal}  "
				if (sVal) desc += "Saturation: ${sVal}  "
				if (bVal != null) desc += "Brightness: ${bVal}  "
				if (ctVal) desc += "Color Temperature: ${ctVal} "	
			}
		}
	}
	else if (settings[actionSettingName] == "Turn on scene") {
		if (strAction != "held") {
			for (pressNum in 1..getMaxPressNum()) {
				if (getDoesPressNumHaveAction(btnNum, pressNum)) {
					desc += "\nPRESS ${pressNum}: "
					def sc = settings["btn${btnNum}_Scene_press${pressNum}"]
					desc += "Turn on scene: ${sc}"				
				}
			}
		} else {
			def sc = settings["btn${btnNum}_Scene_Held"]
			desc += "\nTurn on scene: ${sc}"				
		}
	}
	else if (settings[actionSettingName] == "Turn off scene") {
		def scOffSettingName = "btn${btnNum}_SceneOff"
		if (strAction == "held") scOffSettingName = "btn${btnNum}Held_SceneOff"
		def sc = settings[scOffSettingName]
		desc += "\nTurn off scene: ${sc}"				
	}
	else if (settings[actionSettingName] == "Turn off last used scene") {
		desc += "\nTurn off last used scene"				
	}
	else {
		if (settings[actionSettingName]) {
			def actionStr = settings[actionSettingName]
			desc += "\n${actionStr}"
			rampUpSettingName = "btn${btnNum}_RampUp"
			ramDownSettingName = "btn${btnNum}_RampDown"
			if (strAction == "held") {
				rampUpSettingName = "btn${btnNum}Held_RampUp"
				ramDownSettingName = "btn${btnNum}Held_RampDown"
			}
			if (settings[actionSettingName] == "Brighten" && settings[rampUpSettingName]) desc += " while held"
			if (settings[actionSettingName] == "Dim" && settings[ramDownSettingName]) desc += " while held"
		}
	}
	return desc
}

def makeTurnOnSection(btnNum, strAction = "pushed") {
	logTrace "Running makeTurnOnsection for ${btnNum} ${strAction}"
	if (strAction != "held") {
		for (pressNum in 1..getMaxPressNum()) {
			if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, pressNum-1)) {
				section("Press ${pressNum}" , hideable: true, hidden: false) {
					for (j in bulbs) {
						paragraph("<b>Turn on ${j}</b> and set to...")
						input(name: "btn${btnNum}_${j.id}B_press${pressNum}", type: "number", title: "brightness or level:", description: "0-100 (0 to turn/keep off)", submitOnChange: true, width: 3, required: false)
						input(name: "btn${btnNum}_${j.id}CT_press${pressNum}", type: "number", title: "color temperature:", description: "2000-6500", submitOnChange: true, width: 3, required: false)
						input(name: "btn${btnNum}_${j.id}H_press${pressNum}", type: "number", title: "color with hue value:", description: "0-100", width: 3, required: false)
						input(name: "btn${btnNum}_${j.id}S_press${pressNum}", type: "number", title: "saturation value:", description: "0-100", submitOnChange: true, width: 3, required: false)
						}
				}
			}
		}
	} else {
		section("Button Held" , hideable: true, hidden: false) {
				for (j in bulbs) {
					paragraph("<b>Turn on ${j}</b> and set to...")
					input(name: "btn${btnNum}_${j.id}B_Held", type: "number", title: "brightness or level:", description: "0-100 (0 to turn/keep off)", submitOnChange: true, width: 3, required: false)
					input(name: "btn${btnNum}_${j.id}CT_Held", type: "number", title: "color temperature:", description: "2000-6500", submitOnChange: true, width: 3, required: false)
					input(name: "btn${btnNum}_${j.id}H_Held", type: "number", title: "color with hue value:", description: "0-100", width: 3, required: false)
					input(name: "btn${btnNum}_${j.id}S_Held", type: "number", title: "saturation value:", description: "0-100", submitOnChange: true, width: 3, required: false)
					}
			}
	}
	section {
		paragraph("All fields optional; hue and saturation values will be ignored if all HSL values not set. Color temperature takes precedence over hue and saturation.")
	}
}

def makeTurnOnSceneSection(btnNum, strAction = "pushed") {
	if (strAction != "held") {
		for (pressNum in 1..getMaxPressNum()) {
			if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, pressNum-1)) {
				section("Press ${pressNum}" , hideable: true, hidden: false) {
							input(name: "btn${btnNum}_Scene_press${pressNum}", type: "device.SceneActivator", title: "Activate scene(s):", multiple: true)
				}
			}
		}
	} else {
		section("Button Held" , hideable: true, hidden: false) {
			input(name: "btn${btnNum}_Scene_Held", type: "device.SceneActivator", title: "Activate scene(s):", multiple: true)
		}
	}
}

def makeTurnOffLastSceneSection() {
	section {
		paragraph("Turn off last scene turned on by this app (will not track scenes turned on by other apps/automations, including other Dimmer Button Controller instances).")
	}
}

def makeTurnOffSceneSection(btnNum, strAction = "pushed") {
	section {
		def sceneOffSettingName = "btn${btnNum}_SceneOff"
		if (strAction == "held") sceneOffSettingName = "btn${btnNum}Held_SceneOff"
		input(name: sceneOffSettingName, type: "device.SceneActivator", title: "Turn off scene(s):", multiple: true)
	}
}

def makeTurnOffSection() {
	section {
		paragraph("Turn off all selected lights.")
	}
}

def makeDimUpSection(btnNum, strAction = "pushed") {
	section("<strong>Description</strong>") {
		paragraph("Brightens (dims up) any specified lights that are on when button ${btnNum} is pressed")
	}
	section("<strong>Options</strong>") {
		if (buttonDevice.hasCapability("ReleasableButton")) {
			def rampUpSettingName = "btn${btnNum}_RampUp"
			if (strAction == "held") rampUpSettingName = "btn${btnNum}Held_RampUp"
			input(name: rampUpSettingName, type: "bool", title: "Dim while held (start dimming when button ${strAction}, stop dimming when button is released)")
			paragraph("If enabled, the above will continuously increase the brightness level while the button is held. The button " +
					  "device must support \"${strAction}\" and \"released\" actions for this button, and the lights must support " +
					  "the \"start level change\" and \"stop level change\" commands.")
			paragraph("If you enable this option and notice problems, disable this option.")
		} else {
			paragraph("No additional options avaiable for this action with this button device")
		}
	}
}

def makeDimDownSection(btnNum, strAction = "pushed") {	
	section("<strong>Description</strong>") {
		paragraph("Dims (decreases bightness on) any specified lights that are on when button ${btnNum} is pressed")
	}
	section("<strong>Options</strong>") {
		if (buttonDevice.hasCapability("ReleasableButton")) {
			def rampDownSettingName = "btn${btnNum}_RampDown"
			if (strAction == "held") rampDownSettingName = "btn${btnNum}Held_RampDown"			
			input(name: rampDownSettingName, type: "bool", title: "Dim while held (start dimming when button ${strAction}, stop dimming when button is released)")
			paragraph("If enabled, the above will continuously decrease the brightness level while the button is held. The button " +
					  "device must support \"${strAction}\" and \"released\" actions for this button, and the lights must support " +
					  "the \"start level change\" and \"stop level change\" commands.")
			paragraph("If you enable this option and notice problems, disable this option.")
		} else {
			paragraph("No additional options avaiable for this action with this button device")
		}
	}
}

def isModeOK() {
    def retVal = !modes || modes.contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${retVal}"
    return retVal
}

def pushedHandler(evt) {
	logTrace "Running pushedHandler..."
	if (!isModeOK()) {
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
	case "Turn on scene":		
		def pressNum = getPressNum(btnNum)
		logDebug "Action \"Turn on scene\" specified for button ${btnNum} press ${pressNum}"	
		def sc = settings["btn${btnNum}_Scene_press${pressNum}"]
		atomicState.lastScene = "btn${btnNum}_Scene_press${pressNum}"
		sc.push()
		incrementPressNum(btnNum)
		runIn(15, resetPressNum, [data: ["btnNum": btnNum]])
		break
	case "Turn off last used scene":
		if (atomicState.lastScene) {	
			logDebug("Action \"Turn off last used scene\" specified for button ${btnNum}; turning off scene ${settings[atomicState.lastScene]}")
			settings[atomicState.lastScene].off()
		} else {
			log.debug ("Configured to turn off last used scene but no scene was previously used; not doing anything.")
		}
		resetAllPressNums()
		break
	case "Turn off scene":
		logDebug "Action \"Turn off scene\" specified for button ${btnNum}"	
		def sc = settings["btn${btnNum}Held_SceneOff"]
		sc.off()
		resetAllPressNums()
		break
	case "Turn off":
		logDebug "Action \"turn off\" specified for button ${btnNum}"
		try {
			turnOff(bulbs)
			resetAllPressNums()
		} catch (e) {
			log.warn "Error when running turn-off action: ${e}"
		}
		break
	case "Dim":
		logDebug "Action \"dim\" specified for button ${btnNum}"
		if (settings["btn${btnNum}_RampDown"]) {
			//logTrace "Ramp-down dimming option enabled for button ${btnNum}"
			startLevelChangeDownIfOn(bulbs)
		}
		else {
			//log.trace "Ramp-down dimming option NOT enabled for button ${btnNum}" 
			dimDownIfOn(bulbs, dimStep)
		}
		break
	case "Brighten":
		logDebug "Action \"brighten\" specified for button ${btnNum}"
		if (settings["btn${btnNum}_RampUp"]) {
			//logTrace "Ramp-up dimming option enabled for button ${btnNum}"
			startLevelChangeUpIfOn(bulbs)
		}
		else {
			//log.trace "Ramp-up dimming option NOT enabled for button ${btnNum}" 
			dimUpIfOn(bulbs, dimStep)
		}
		break
	default:
		logDebug "Action not specified for button ${btnNum}"
	}
}

def heldHandler(evt) {
	logTrace "Running heldHandler..."
	if (!isModeOK()) {
		return
	}
	def btnNum = evt.value
	def pressType = evt.name
	logTrace "Button ${btnNum} was ${pressType}"
	def action = settings["btn${btnNum}HeldAction"]
	switch (action) {
	case "Turn on":
		logDebug "Action \"Turn on\" specified for button ${btnNum} held"
		try {
			for (j in bulbs) {
				def bulbSettingB = "btn${btnNum}_${j.id}B_Held"
				def bVal = settings["${bulbSettingB}"]
				def bulbSettingCT = "btn${btnNum}_${j.id}CT_Held"
				def ctVal = settings["${bulbSettingCT}"]
				def bulbSettingH = "btn${btnNum}_${j.id}H_Held"
				def hVal = settings["${bulbSettingH}"]
				def bulbSettingS = "btn${btnNum}_${j.id}S_Held"
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
		break		
	case "Turn on scene":
		logDebug "Action \"Turn on scene\" specified for button ${btnNum} held"	
		def sc = settings["btn${btnNum}_Scene_Held"]
		atomicState.lastScene = "btn${btnNum}_Scene_Held"
		sc.push()
		break
	case "Turn off last used scene":
		if (atomicState.lastScene) {	
			logDebug("Action \"Turn off last used scene\" specified for button ${btnNum}; turning off scene ${settings[atomicState.lastScene]}")
			settings[atomicState.lastScene].off()
		} else {
			log.debug ("Configured to turn off last used scene but no scene was previously used; not doing anything.")
		}
		resetAllPressNums()
		break
	case "Turn off scene":
		logDebug "Action \"Turn off scene\" specified for button ${btnNum} held"	
		def sc = settings["btn${btnNum}_SceneOff"]
		sc.off()
		resetAllPressNums()
		break
	case "Turn off":
		logDebug "Action \"turn off\" specified for button ${btnNum} held"
		try {
			turnOff(bulbs)
			resetAllPressNums()
		} catch (e) {
			log.warn "Error when running turn-off action: ${e}"
		}
		break
	case "Dim":
		logDebug "Action \"dim\" specified for button ${btnNum}"
		if (settings["btn${btnNum}Held_RampDown"]) {
			//logTrace "Ramp-down dimming option enabled for button ${btnNum}"
			startLevelChangeDownIfOn(bulbs)
		}
		else {
			//log.trace "Ramp-down dimming option NOT enabled for button ${btnNum}" 
			dimDownIfOn(bulbs, dimStep)
		}
		break
	case "Brighten":
		logDebug "Action \"brighten\" specified for button ${btnNum}"
		if (settings["btn${btnNum}Held_RampUp"]) {
			//logTrace "Ramp-up dimming option enabled for button ${btnNum}"
			startLevelChangeUpIfOn(bulbs)
		}
		else {
			//log.trace "Ramp-up dimming option NOT enabled for button ${btnNum}" 
			dimUpIfOn(bulbs, dimStep)
		}
		break
	default:
		logDebug "Action not specified for button ${btnNum}"
	}
}

def releasedHandler(evt) {
	logTrace "Running releaseHandler..."
	if (!isModeOK()) {
		return
	}
	def btnNum = evt.value
	def pressType = evt.name
	//logTrace "Button ${btnNum} was ${pressType}"
	def rampSetting1 = (settings["btn${btnNum}Action"] == "Dim" && settings["btn${btnNum}_RampDown"])
	def rampSetting2 = (settings["btn${btnNum}Action"] == "Brighten" && settings["btn${btnNum}_RampUp"])	
	def rampSetting3 = (settings["btn${btnNum}HeldAction"] == "Dim" && settings["btn${btnNum}Held_RampDown"])
	def rampSetting4 = (settings["btn${btnNum}HeldAction"] == "Brighten" && settings["btn${btnNum}Held_RampUp"])
	if (rampSetting1 || rampSetting2 || rampSetting3 || rampSetting4) {
		bulbs.stopLevelChange()
		logDebug "Stopped level change on ${bulbs} per button ${btnNum} release"
	} else {
		logTrace "No action set to occur for release button ${btnNum}"
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
			log.warn("Unable to dim up ${it}: ${e}")
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
			if (currLvl <= 1) return
			def newLevel
			if (currLvl && currLvl > 0) {
				newLevel = currLvl - changeBy
			}
			if (newLevel <= 0) newLevel = 1
			it.setLevel(newLevel, transitionTime)
		} catch (e) {
			log.warn("Unable to dim ${it}: ${e}")
		}	
	}
}

def stopLevelChangeIfOn(bulbs) {
	// Skipping the usual check to see if bulbs are on. If they are not on,
	// this should have no effect anyway
	try {
		bulbs.stopLevelChange()
	} catch (e) {
	 	log.warn("Unable to start level change down on ${bulbs}: ${e}")
	}
	logTrace "Finished stopping level change on all bulbs"
}

def startLevelChangeUpIfOn(bulbs) {
	// Skipping the usual check to see if bulbs are on. If they are not on,
	// none I've tested will be affected by startLevelChange commands anyway.
	try {
		bulbs.startLevelChange("up")
		//logTrace("Starting level change up on: ${bulbs}")
	} catch (e) {
	 	log.warn("Unable to start level change up on ${bulbs}: ${e}")
	}
	logTrace("Started level change up on all (applicable) lights")
}

def startLevelChangeDownIfOn(bulbs) {
	// Skipping the usual check to see if bulbs are on. If they are not on,
	// none I've tested will dim anyway (makes sense)
	try {
		bulbs.startLevelChange("down")
		//logTrace("Starting level change down on: ${bulbs}")
	} catch (e) {
	 	log.warn("Unable to start level change down on ${bulbs}: ${e}")
	}
	logTrace("Started level change down on all lights")
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
		case "Turn on scene":
			//intentionally continuing
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
 * timeout has elapsed to "reset" count for specific button
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

/* Resets all press counts to first press, intended to be called after "off"-type
 * button pressed to reset all button-press counts
*/
def resetAllPressNums() {
	(1..getNumberOfButtons()).each {
		if (getPressNum(it)) {
			resetPressNum(["btnNum": it])
		}
	}
}

def getDoesPressNumHaveAction(btnNum, pressNum) {
	logTrace "Running getDoesPressNumHaveAction for btn ${btnNum} press ${pressNum}"
	def hasAction = false
	if (settings["btn${btnNum}Action"] && pressNum == 1) {
		hasAction = true
	}
	else {
		if (settings["btn${btnNum}Action"] == "Turn on") {
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
		else if (settings["btn${btnNum}Action"] == "Turn on scene") {
			def sc = settings["btn${btnNum}_Scene_press${pressNum}"]
			if (sc) {
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
    unschedule()
    initialize()
}

def initialize() {
	log.trace "Initialized"
	unsubscribe()
	subscribe(buttonDevice, "pushed", pushedHandler)
	subscribe(buttonDevice, "released", releasedHandler)
	subscribe(buttonDevice, "held", heldHandler)
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
				   
