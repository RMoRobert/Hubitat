/**
 * ==========================  Dimmer Button Controller (Child  App) ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2018-2020 Robert Morris
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
 * == Child version: 2.1.1 ==
 *
 * Changelog:
 * 2.1.1  (2020-06-01) - Additional clarification of "press" terminology; replaced with button-event-specific languge where appropriate
 * 2.1    (2020-04-27) - Added ability to use individual devices vs. groups for some actions (on/off/setLevel vs. start/stopLevelChange)
 * 2.0    (2020-04-23) - Rewrite of app with cleaner UI more functionality (breaking changes; also keep 1.x child if you have instances)
 * 1.9a   (2020-01-04) - Changes to eliminate warning if no "additional off" devices selected
 * 1.9    (2019-12-06) - Added option to activate CoCoHue scenes
 * 1.8    (2019-08-02) - Added option to send commands twice (shouldn't be needed but is bug fix for apparent Hubitat problem)
 * 1.7    (2019-04-29) - Added "toggle" action, "additional switches for 'off'" option; bug fixes (dimming, scene off)
 * 1.6    (2019-01-14) - New "held" functionality
 * 1.5    (2019-01-02) - New press/release dimming action
 * 0.9    (2018-12-27) - (Beta) First public release
 *
 */

import groovy.transform.Field

@Field Map eventMap = [
    	"pushed": ["capability":"PushableButton", userAction: "push", "multiPresses": true],
    	"held": ["capability":"HoldableButton", userAction: "hold", "multiPresses": false],
    	"released": ["capability":"ReleasableButton", userAction: "release", "multiPresses": false],
    	"doubleTapped": ["capability":"DoubleTapableButton", userAction: "double tap", "multiPresses": true]
    ]

@Field Map actionMap = [
    	"on": [displayName: "Turn on", "multiPresses": true],
        "scene": [displayName: "Turn on scene", "multiPresses": true],
        "hueScene": [displayName: "Activate CoCoHue scene", "multiPresses": true],
        "bri": [displayName: "Dim up", "multiPresses": false],
        "dim": [displayName: "Dim down", "multiPresses": false],
        "offLastScene": [displayName: "Turn off last used scene", "multiPresses": false],
        "offScene": [displayName: "Turn off scene", "multiPresses": false],
        "off": [displayName: "Turn off", "multiPresses": false],
    ]


definition(
    name: "Dimmer Button Controller (Child App) 2",
    namespace: "RMoRobert",        
    parent: "RMoRobert:Dimmer Button Controller",
    author: "Robert Morris",
    description: "Do not install directly. Install Dimmer Button Controller parent app, then create new automations using that app.",
	category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    documentationLink: "https://community.hubitat.com/t/release-dimmer-button-controller-configure-pico-to-emulate-hue-dimmer-or-any-button-device-to-easily-control-lights/7726"
)

preferences {
	page(name: "pageMain")
	page(name: "pageFinal")
	page(name: "pageButtonConfig")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Dimmer Button Controller", uninstall: true, install: false, nextPage: "pageFinal") {
        section("Choose devices") {
            input(name: "buttonDevices", type: "capability.pushableButton", title: "Select button device(s):", multiple: true,
                  required: true, submitOnChange: true)
            if (settings.buttonDevices?.size() > 1) paragraph("When selecting multiple button devices, it is recommended to choose devices " +
                                                     "of the same type (capabilities and nubmer of buttons, driver, etc.).")
            input(name: "dimmers", type: "capability.switchLevel",
                  title: "Select lights to turn on/off and dim with below actions:", multiple: true, required: true, submitOnChange: true)
			if (settings['boolGroup']) {
            	input(name: "group", type: "capability.switchLevel",
                  	  title: "Select group device to use when applicable:", multiple: true, required: true, submitOnChange: false)
				paragraph('If selected, the above group device will be used when possible <em>instead of</em> the above selected lights/dimmers. Choose ' +
					      'a group that contains the same lights as the above, individually-selected bulbs. The group will be used instead for the ' +
						  'following actions: "Turn on" when "Apply settings to all..." selected; "Dim up/down" when "until released" <em>not</em> ' +
						  'selected; and "Turn off."')
			}
			input(name: "offDevices", type: "capability.switch", title: "Additional lights to turn off with \"off\" actions only:",
                  multiple: true, required: false)
			paragraph("Actions to turn on and off lights below allow you to choose scenes <em>or</em> use the above selected lights. " +
                      "Dimming actions apply to above selected lights.")
			paragraph("If you use scenes below, it is recommended you choose all bulbs above that are used in your scenes to ensure " +
			 	      "consistent behavior.")
		}
        if(settings.buttonDevices && settings.dimmers) {
			if (!app.getLabel()) app.updateLabel(getDefaultLabel())
			section("Configure buttons") {
                def caps = getButtonCapabilities()
				(1..getNumberOfButtons()).each { btnNum ->
				    eventMap.each { key, value ->
						if (value.capability in caps && (key == "released" ? boolShowReleased : true)) {
							href(name: "pageButtonConfigHref",
					    	page: "pageButtonConfig",
							params: [btnNum: btnNum, action: key, multiPresses: value.multiPresses], title: "Button $btnNum ${deCamelCase(key)}",
							description: getButtonConfigDescription(btnNum, key, value.multiPresses) ?: "Click/tap to configure",
							state:  getButtonConfigDescription(btnNum, key, value.multiPresses) ? "complete" : null)
						}
					}
				}
			}
		}
        
        section("Options", hideable: true, hidden: false) {
            input(name: "transitionTime", type: "enum", title: "Transition time (for dimming)", required: true,
                  options: [[null:"Unspecified (use device default)"], [0:"ASAP"],[100:"100ms"],[300:"300ms"],
				  [500:"500ms"],[750:"750ms"],[1000:"1s"],[1500:"1.5s"],[3000:"3s"]], defaultValue: 100)
			input(name: "dimStep", type: "number", title: "Dimming buttons change level +/- by (unless \"dim until release\" enabled on supported devices)",
                  description: "0-100", required: true, defaultValue: 15)
            input(name: "maxPressNum", type: "enum", title: "Maximum number of presses (default: 5)",
                  options: [[1:1],[2:2],[3:3],[4:4],[5:5],[6:6],[7:7],[8:8],[9:9],[10:10]], defaultValue: 5)
        }
		section("Advanced options", hideable: true, hidden: true) {
			input(name: "boolDblCmd", type: "bool", title: "Send on/off and level commands twice (workaround for possible device/hub oddities if bulbs don't change first time)")
			input(name: "boolGroup", type: "bool", title: 'Allow separate selection of group device besdies individual bulbs (will attempt to use group device to optimize actions where appropriate)', submitOnChange: true)
			input(name: "boolShowSetForAll", type: "bool", title: "Always show \"set for all\" option even if only one dimmer/light selected (may be useful if frequently change which lights the button controls)")
            input(name: "boolShowReleased", type: "bool", title: "Show actions sections for \"released\" events", submitOnChange: true)
			input(name: "boolToggleInc", type: "bool", title: "If using \"toggle\" option, increment press count even if lights were turned off", defaultValue: false)
            input(name: "boolInitOnBoot", type: "bool", title: "Initialize app on hub start (may avoid delays with first button presses after reboot)", defaultValue: true)
            input(name: "debugLogging", type: "bool", title: "Enable debug logging")
			input(name: "traceLogging", type: "bool", title: "Enable trace/verbose logging (for development only)")
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

String getDefaultLabel() {
	def defaultLabel = "${buttonDevices[0]?.displayName} Dimmer Button Controller"
	// I prefer this; may provide option to change default: 
    //def defaultLabel = "DBC - ${buttonDevices[0]?.displayName}"	
    return defaultLabel
}

String getOrdinal(String action='pushed', Integer pressNum=1) {
	String actionDisplayName = eventMap[action]?.userAction.capitalize()
	String ordinal = ""
	switch (pressNum) {
		case 1: ordinal = "First"; break
		case 2: ordinal = "Second"; break
		case 3: ordinal = "Third"; break
		case 4: ordinal = "Fourth"; break
		case 5: ordinal = "Fifth"; break
		case 6: ordinal = "Sixth"; break
		case 7: ordinal = "Seventh"; break
		case 8: ordinal = "Eighth"; break
		case 9: ordinal = "Ninth"; break
		default:
			ordinal = pressNum.toString()
			String end = pressNum.toString().right(1)
			if (right == 1) ordinal += "st"
			else if (right == 2) ordinal += "nd"
			else if (right == 3) ordinal += "rd"
			else ordinal += "th"
	}
	return "$ordinal $actionDisplayName"
}

Integer getNumberOfButtons() {
	def num = settings.buttonDevices*.currentValue('numberOfButtons').max()
	if (num) {
		return num as Integer
	} else {
		log.warn "Device did not specify number of buttons; using 1. Check or change this in the driver if needed."
		return 1
	}	
}

Integer getMaxPressNum() {
	return settings["maxPressNum"] as Integer ?: 1
}

List getButtonCapabilities() {
	def btnCapabs = []
	def allCapabs = settings.buttonDevices*.getCapabilities().name.flatten()
	["PushableButton", "HoldableButton", "ReleasableButton", "DoubleTapableButton"].each { c ->
		if (c in allCapabs) btnCapabs.add(c)
	}
	return btnCapabs
}

def pageButtonConfig(params) {
    //logTrace("pageButtonConfig($params)")
    if (params) {
        atomicState.currentParams = params
    } else {
        params = atomicState.currentParams
    }
    def btnNum = params.btnNum
    def action = params.action
    def multiPresses = params.multiPresses
    dynamicPage(name: "pageButtonConfig", title: "Button ${btnNum} Configuration", uninstall: true, install: false) {
        if(settings.buttonDevices && settings.dimmers && btnNum) {
            def btnActionSettingName = "btn${btnNum}.${action}.Action"
            section("Actions for button ${btnNum} ${deCamelCase(action)}") {
				input(name: btnActionSettingName, type: "enum", title: "Do...",
					options:  actionMap.collect { actMap -> ["${actMap.key}": actMap.value.displayName] },
					submitOnChange: true)
			}
            def actionSetting = settings["${btnActionSettingName}"]
            if (actionSetting) {
				switch(actionSetting) {
					case "on":
						makeTurnOnSection(btnNum, action, multiPresses)
						break
					case "scene":
						makeTurnOnSceneSection(params.btnNum, params.action, params.multiPresses)
						break
					case "hueScene":
						makeActivateHueSceneSection(params.btnNum, params.action, params.multiPresses)
						break
					case "bri":
						makeDimSection(params.btnNum, params.action, "up")
						break
					case "dim":
						makeDimSection(params.btnNum, params.action, "down")
						break
					case "offLastScene":
						makeTurnOffLastSceneSection()
						break					
					case "offScene":
						makeTurnOffSceneSection(params.btnNum, params.action)
						break
					case "off":
						makeTurnOffSection()
						break
					default:
						paragraph("Not set")
				}
			}
        }
	}
}

String getButtonConfigDescription(btnNum, action, multiPresses) {
	def desc = ""
	def actionSettingName =  "btn${btnNum}.${action}.Action"
	def maxPress = multiPresses ? getMaxPressNum() : 1
	if (settings[actionSettingName] == "on") {
		for (pressNum in 1..maxPress) {
			if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
				String pressNumString = getOrdinal(action, pressNum)
				Boolean toggleSet = settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]
				if (multiPresses) desc += "\n<span style=\"font-variant: small-caps\">$pressNumString</span>: "
				if (toggleSet) {
					desc += "\n${multiPresses ? '  ' : ''}Toggle or"
				}
				if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
						def lVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"]
						def ctVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.CT"]
						def hVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.H"]
						def sVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.S"]
                        String dev = 'all'
						desc += "${multiPresses ? '\n  T' : '\nT'}urn on ${dev}"
						if (lVal != null || ctVal || hVal != 0 || sVal != 0) {
							desc += " - "
						}
						// TODO: Better string-ification of this (and similar section below)
						if (hVal != null) desc += "Hue: ${hVal}  "
						if (sVal) desc += "Sat: ${sVal}  "
						if (lVal != null) desc += "Level: ${lVal}  "
						if (ctVal) desc += "CT: ${ctVal} "
				}
				else {
					for (dev in settings["dimmers"]) {
						def lVal = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
						def ctVal = settings["btn${btnNum}.${action}.Press${pressNum}.CT.${dev.id}"]
						def hVal = settings["btn${btnNum}.${action}.Press${pressNum}.H.${dev.id}"]
						def sVal = settings["btn${btnNum}.${action}.Press${pressNum}.S.${dev.id}"]
						desc += "${multiPresses ? '\n  T' : '\nT'}urn on ${dev.displayName}"
						if (lVal != null || ctVal || hVal != 0 || sVal != 0) {
							desc += " - "
						}
						if (hVal != null) desc += "Hue: ${hVal}  "
						if (sVal) desc += "Sat: ${sVal}  "
						if (lVal != null) desc += "Level: ${lVal}  "
						if (ctVal) desc += "CT: ${ctVal} "
					}
				}
			}
		}
	}
	else if (settings[actionSettingName] == "scene") {
		if (multiPresses) {
			for (pressNum in 1..maxPress) {
				if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
					def pressNumString = getOrdinal(action, pressNum)
					if (multiPresses) desc += "\n<span style=\"font-variant: small-caps\">$pressNumString</span>: "
					if (settings["btn${btnNum}.${strAction}.Press${pressNum}.Toggle"]) {
						desc += "Toggle or turn on "
					} else {
						desc += "Turn on "
					}
                    def sc = settings["btn${btnNum}.${action}.Press${pressNum}.Scene"]
					desc += "scene: ${sc}"	
				}
			}
		}
		else {
			for (pressNum in 1..maxPress) {
				if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
					if (settings["btn${btnNum}.${strAction}.Press${pressNum}.Toggle"]) {
						desc += "Toggle or turn on "
					} else {
						desc += "Turn on "
					}
                    def sc = settings["btn${btnNum}.${action}.Press${pressNum}.Scene"]
					desc += "scene: ${sc}"				
				}
			}
		}
	}
	else if (settings[actionSettingName] == "hueScene") {
		if (multiPresses) {
			for (pressNum in 1..maxPress) {
				if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
					def pressNumString = getOrdinal(action, pressNum)
					if (multiPresses) desc += "\n<span style=\"font-variant: small-caps\">$pressNumString</span>: "
					if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
						desc += "Toggle or activate "
					} else {
						desc += "Activate "
					}
                    def sc = settings["btn${btnNum}.${action}.Press${pressNum}.HueScene"]
					desc += "Hue scene: ${sc}"	
				}
			}
		}
		else {
			for (pressNum in 1..maxPress) {
				if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
					if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
						desc += "Toggle or activate "
					} else {
						desc += "Activate "
					}
                    def sc = settings["btn${btnNum}.${action}.Press${pressNum}.HueScene"]
					desc += "Hue scene: ${sc}"				
				}
			}
		}
	}
	else if (settings[actionSettingName] == "offScene") {
		def scOffSettingName = "btn${btnNum}.${action}.Press${pressNum}.OffScene"
		def sc = settings[scOffSettingName]
		desc += "\nTurn off scene: ${sc}"				
	}
	else if (settings[actionSettingName] == "offLastScene") {
		desc += "\nTurn off last used scene"				
	}
	else if (settings[actionSettingName] == "bri" || settings[actionSettingName] == "dim") {
		if (settings[actionSettingName]) {
			def actionStr = actionMap[settings[actionSettingName]].displayName
			desc += "\n${actionStr}"
			def levelChangeSettingName = "btn${btnNum}.${action}.UseStartLevelChange"
			if (settings[levelChangeSettingName]) desc += " until released"
		}
	}
	else if (settings[actionSettingName] == "off") {
		desc += "\nTurn off"
	}
	else {
		logDebug("Description for button $btnNum $action unspecified", "trace")
	}
    //log.warn "Returning: $desc"
	return desc.trim()
}

def makeTurnOnSection(btnNum, strAction = "pushed", multiPresses = false) {
    logTrace("Running makeTurnOnSection($btnNum, $strAction, $multiPresses)")
    if (params) {
        atomicState.currentParams = params
    } else {
        params = atomicState.currentParams
    }
	def maxPressNum = multiPresses ? getMaxPressNum() : 1
	for (pressNum in 1..maxPressNum) {
		if (pressNum == 1 || (pressNum > 1 && multiPresses && getDoesPressNumHaveAction(btnNum, strAction, pressNum-1))) {
			def sectionTitle = multiPresses ? getOrdinal(strAction, pressNum) : "Button ${btnNum} ${strAction}"
			section(sectionTitle, hideable: true, hidden: false) {
                if (dimmers.size() > 1 || settings["boolShowSetForAll"]) {
					input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll", type: "bool",
							title: "Apply same level and color settings to all lights", submitOnChange: true, defaultValue: false)
					if (pressNum == 1) {
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
						title: "Toggle (turn all off if any on; otherwise, turn on as specified)", submitOnChange: true, defaultValue: false)
					} else {
						app.removeSetting("btn${btnNum}.${strAction}.Press${pressNum}.Toggle")
					}
                }
                if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
						paragraph("", width: 3)
						paragraph("<strong>Level</strong>", width: 2)
						paragraph("<strong>Color Temp.</strong>", width: 3)
						paragraph("<strong>Hue</strong>", width: 2)
						paragraph("<strong>Saturation</strong>", width: 2)
						paragraph("Set all :", width: 3)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L", type: "number",
							title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 2, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.CT", type: "number",
							title: "", description: "~2000-7000", range: "1000..8000", submitOnChange: false, width: 3, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.H", type: "number",
							title: "", range: "0..360", description: "0-100", submitOnChange: false, width: 2, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.S", type: "number",
							title: "", range: "0..100", description: "0-100", submitOnChange: false, width: 2, required: false)                    
				}
                else {
					paragraph("<strong>Device</strong>", width: 3)
					paragraph("<strong>Level</strong>", width: 2)
					paragraph("<strong>Color Temp.</strong>", width: 3)
					paragraph("<strong>Hue</strong>", width: 2)
					paragraph("<strong>Saturation</strong>", width: 2)
					for (dev in settings["dimmers"]) {
						paragraph("${dev.displayName}:", width: 3)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}", type: "number",
								title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 2, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.CT.${dev.id}", type: "number",
								title: "", description: "~1000-8000", range: "1000..8000", submitOnChange: false, width: 3, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.H.${dev.id}", type: "number",
								title: "", range: "0..360", description: "0-100", submitOnChange: false, width: 2, required: false)
						input(name: "btn${btnNum}.${strAction}.Press${pressNum}.S.${dev.id}", type: "number",
								title: "", range: "0..100", description: "0-100", submitOnChange: false, width: 2, required: false)                    
					}
				}
				if (pressNum < maxPressNum) {
					paragraph("", width: 5)
					paragraph("", width: 4)
                    input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SaveButton", type: "button",
                          title: "Save Presses", width: 3, submitOnChange: true)
                }
			}
		}
	}
	section {
		paragraph("At least one field is required to be specified; all are otherwise optional. Color temperature takes precedence over hue and saturation if specified.")
	}
}
def makeTurnOnSceneSection(btnNum, strAction = "pushed", multiPresses = false) {
    logTrace "Running makeTurnOnSceneSection{$btnNum, $strAction, $multiPresses)"
	def maxPressNum = multiPresses ? getMaxPressNum() : 1
    section() {
		for (pressNum in 1..maxPressNum) {
			if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, strAction, pressNum-1)) {
				if (multiPresses) paragraph(getOrdinal(strAction, pressNum))
				input(name: "btn${btnNum}.${strAction}.Press${pressNum}.Scene", type: "device.SceneActivator",
					  title: "Scene(s):", multiple: true, submitOnChange: multiPresses)
				if (pressNum == 1) {
					input(name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
						  title: "Toggle (turn all off if any on, or activate scene if all off)", defaultValue: false)
				}
			}
		}
    }
}

def makeActivateHueSceneSection(btnNum, strAction = "pushed", multiPresses = false) {
	logTrace "Running makeActivateHueSceneSection for ${btnNum} ${strAction}"
	def maxPressNum = multiPresses ? getMaxPressNum() : 1
    section() {
		for (pressNum in 1..maxPressNum) {
			if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, strAction, pressNum-1)) {
				if (multiPresses) paragraph(getOrdinal(strAction, pressNum))
				input(name: "btn${btnNum}.${strAction}.Press${pressNum}.HueScene", type: "device.CoCoHueScene",
					  title: "Hue scene:", submitOnChange: multiPresses)
				if (pressNum == 1) {
					input(name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
						  title: "Toggle (turn all off if any on, or activate scene if all off)", defaultValue: false)
				}
			}
		}
    }
}

def makeTurnOffLastSceneSection() {
	section {
		paragraph("Turn off last scene turned on by this app (will not track scenes turned on by " + 
		          "other apps/automations, including other Dimmer Button Controller instances).")
	}
}

def makeTurnOffSceneSection(btnNum, strAction = "pushed", multiPresses = false) {
	def maxPressNum = multiPresses ? getMaxPressNum() : 1
    section() {
		for (pressNum in 1..maxPressNum) {
			if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, strAction, pressNum-1)) {
				if (mutliPresses) paragraph(getOrdinal(strAction, pressNum))
				input(name: "btn${btnNum}.${strAction}.Press${pressNum}.OffScene", type: "device.SceneActivator",
					title: "Turn off scene(s):", submitOnChange: mutliPresses)
			}
		}
    }
}

def makeTurnOffSection() {
	section {
		paragraph("Turn off all selected lights.")
	}
}

/** Makes dim up/down section; direction must be 'up' or 'down' */
def makeDimSection(btnNum, String strAction = "pushed", String direction) {
	String rampSettingName = "btn${btnNum}.${strAction}.UseStartLevelChange"
	section() {
		if (!settings[rampSettingName]) {
		paragraph("Adjust level by ${direction == 'up' ? '+' : '-'}${settings[dimStep] ?: 15}% for any " +
		          "lights that are on when button ${btnNum} is $strAction")
		} else {
			paragraph("Dim $direction on ${eventMap[strAction]?.userAction}")
		}
	}
	section('<strong>Options</strong>') {
		if (buttonDevices.any { it.hasCapability('ReleasableButton') } && (strAction == 'pushed' || strAction == 'held')) {
			def settingTitle = "Dim until release (start level change when button ${strAction}, stop level change when button is released)"
			input(name: rampSettingName, type: 'bool', title: settingTitle, submitOnChange: true)
			if (dimmers.any { !(it.hasCapability('ChangeLevel')) }) {
				def unsupportedDevices = dimmers.findAll { !(it.hasCapability('ChangeLevel')) }.join(', ')
				paragraph("Warning: one or more lights do not support the \"Start Level Change\" commands: $unsupportedDevices. " +
				          "The \"Dim until release\" option above will probably not work.")
			}
		} else {
			app.removeSetting("btn${btnNum}.${strAction}.UseStartLevelChange")
			paragraph("No additional options avaiable for this action with this button device")
		}
	}
	if (settings[rampSettingName]) {
		String releaseSettingName = "btn${btnNum}.released.Action"
		app.updateSetting(releaseSettingName, [type:"string", value: "StopLevelChange"])
	} else {
		String releaseSettingName = "btn${btnNum}.released.Action"
		app.removeSetting(releaseSettingName)
	}
}

void appButtonHandler(btn) {
	// I could do something like the below if it ever ends up mattering, but right now I
	// only use it to submit changes on button config pages, so no need:
     /*
	  switch(btn) {
          case "myButtton1":
		  	   // do something
               break
      }
	  */
}

Boolean isModeOK() {
    Boolean isOK = !modes || modes.contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${isOK}"
    return isOK
}

void buttonHandler(evt) {
    logDebug "Running buttonHandler (for ${evt.value} ${evt.name})..."
	if (!isModeOK()) {
		return
	}
	def btnNum = evt.value
	def action = evt.name
	String actionSettingName =  "btn${btnNum}.${action}.Action"
	//log.error "==== $actionSettingName = ${settings[actionSettingName]} ===="
	switch (settings[actionSettingName]) {
        case "on":
			Integer pressNum = getPressNum(btnNum, action)
			logDebug "Action \"Turn on\" specified for button ${btnNum} ${action} press ${pressNum}"
			if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
				logDebug "  SetForAll or Toggle configured for press ${pressNum}", "trace"
				Boolean didToggle = false
				if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
					logDebug "  Toggle configured for button ${btnNum} press ${pressNum}", "trace"
					if (dimmers.any { it.currentValue('switch') == 'on'} ) {
						didToggle = true
						def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
						devices.off()
						if (settings['boolToggleInc']) {
							logTrace "  Incrementing press number because 1+ lights turned off and setting configured to increase"
							incrementPressNum(btnNum, action)
							runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
							break
						}
					}
				}
				if (!didToggle) {
					def bulbSettingL = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"					
					def bulbSettingCT = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.CT"
					def bulbSettingH = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.H"
					def bulbSettingS = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.S"
					def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
					doActionTurnOn(devices, settings[bulbSettingH], settings[bulbSettingS],
								settings[bulbSettingL], settings[bulbSettingCT])
				}
			}
			else {  // if not SetForAll:
				logDebug "  SetForAll not configured for press ${pressNum}", "trace"
				Boolean didToggle = false
				if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
					logDebug "  Toggle configured for button ${btnNum} press ${pressNum}", "trace"
					if (dimmers.any { it.currentValue('switch') == 'on'} ) {
						didToggle = true
						def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
						devices.off()
						if (settings['boolToggleInc']) {
							logTrace "  Incrementing press number because 1+ lights turned off and setting configured to increase"
							incrementPressNum(btnNum, action)
							runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
							break
						}
					}
				}
				if (!didToggle) {
					logTrace "  Iterating over each device..."
					for (dev in dimmers) {
						def bulbSettingL = "btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"					
						def bulbSettingCT = "btn${btnNum}.${action}.Press${pressNum}.CT.${dev.id}"
						def bulbSettingH = "btn${btnNum}.${action}.Press${pressNum}.H.${dev.id}"
						def bulbSettingS = "btn${btnNum}.${action}.Press${pressNum}.S.${dev.id}"
						doActionTurnOn(dev, settings[bulbSettingH], settings[bulbSettingS],
							settings[bulbSettingL], settings[bulbSettingCT])
					}
				}
			}
			incrementPressNum(btnNum, action)
			runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
			break
        case "scene":		
			Integer pressNum = getPressNum(btnNum)
			logDebug "Action \"Turn on scene\" specified for button ${btnNum} press ${pressNum}"
			if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
				logTrace "  Toggle option specified"
				if (dimmers.any { it.currentValue('switch') == 'on'}) {
					logTrace "  Toggle option specified and one or more lights on; turning off"
					dimmers.off()
					if (settings['boolToggleInc']) {
						incrementPressNum(btnNum, action)
						runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
					}
					break
				}
			}
			def sc = settings["btn${btnNum}.${action}.Press${pressNum}.Scene"]
			atomicState.lastScene = "btn${btnNum}.${action}.Press${pressNum}.Scene"
			sc.on()
			if (settings['boolDblCmd']) {
				pauseExecution(250)
				sc.on()
			}
			logDebug "Scene turned on: ${sc}"
			incrementPressNum(btnNum, action)
			runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
			break
        case "hueScene":		
			Integer pressNum = getPressNum(btnNum)
			logDebug "Action \"Turn on Hue scene\" specified for button ${btnNum} ${action} press ${pressNum}"
			if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
				logTrace "  Toggle option specified"
				if (dimmers.any { it.currentValue('switch') == 'on'}) {
					logTrace "  Toggle option specified and one or more lights on; turning off"
					dimmers.off()
					if (settings['boolToggleInc']) {
						incrementPressNum(btnNum, action)
						runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
					}
					break
				}
			}
			def sc = settings["btn${btnNum}.${action}.Press${pressNum}.HueScene"]
			sc.on()
			logDebug "Hue scene turned on: ${sc}"
			incrementPressNum(btnNum, action)
			runIn(15, resetPressNum, [data: [btnNum: btnNum, action: [action]]])
			break
        case "offLastScene":
			if (atomicState.lastScene) {	
				logDebug("Action \"Turn off last used scene\" specified for button ${btnNum} ${action}; turning off scene ${settings[atomicState.lastScene]}")
				settings[atomicState.lastScene].off()
				if (settings['boolDblCmd']) {
					pauseExecution(250)
					settings[atomicState.lastScene].off()
				}
			} else {
				log.debug ("Configured to turn off last used scene but no scene was previously used; exiting.")
			}
			resetAllPressNums()
			break
        case "offScene":
			logDebug "Action \"Turn off scene\" specified for button ${btnNum} ${action}"
			Integer pressNum = getPressNum(btnNum)		
			def sc = settings["btn${btnNum}.${action}.Press${pressNum}.OffScene"]
			sc?.off()
			if (settings['boolDblCmd']) {
				pauseExecution(250)
				sc?.off()
			}
			resetAllPressNums()
			break
        case "off":
			logDebug "Action \"turn off\" specified for button ${btnNum} ${action}"
			try {
				def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
				devices.off()
				offDevices?.off()
				if (settings['boolDblCmd']) {
					pauseExecution(250)
					devices.off()
					pauseExecution(100)
					offDevices?.off()
				}
			} catch (e) {
				log.error "Error when running \"off\" action: ${e}"
			} finally {
				resetAllPressNums()
			}
			break
        case "dim":
			logDebug "Action \"dim\" specified for button ${btnNum} ${action}"
			if (settings["btn${btnNum}.${action}.UseStartLevelChange"]) {
				//logTrace "UseStartLevelChange option enabled for button ${btnNum} ${action}"
				startLevelChangeIfOn(dimmers, "down")
			}
			else {
				//log.trace "Ramp-down dimming option NOT enabled for button ${btnNum}"
				Integer changeBy = settings[dimStep] ? 0 - settings[dimStep] as Integer : -15
				def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
				doActionDim(devices, changeBy)
			}
			break
        case "bri":
			logDebug "Action \"brighten\" specified for button ${btnNum}"
			if (settings["btn${btnNum}.${action}.UseStartLevelChange"]) {
				//logTrace "Ramp-up dimming option enabled for button ${btnNum}"
				startLevelChangeIfOn(dimmers, "up")
			}
			else {
				//log.trace "Ramp-up dimming option NOT enabled for button ${btnNum}" 
				Integer changeBy = settings[dimStep] ? settings[dimStep] as Integer : 15
				def devices = (settings['boolGroup'] && settings['group']) ? group : dimmers				
				doActionDim(devices, changeBy)
			}
        	break
		case "StopLevelChange":
			Boolean doStop = false
			eventMap.each { key, value ->
				if (settings["btn${btnNum}.${key}.Action"] == 'dim' ||
				    settings["btn${btnNum}.${key}.Action"] == 'bri') {
						doStop = true
				}
			}
			logTrace("  doStop for level change = $doStop")
			if (doStop) {
				logTrace("Stopping level change on $dimmers")
				dimmers.stopLevelChange()
			}
			break
        default:
            logDebug "Action not specified for button ${btnNum} ${action}"
	}
}

/** Turns off all devices of any are on; otherwise, turns all on. Returns
  * true if any lights were turned on.
  */
Boolean toggle(devices) {
	logDebug "Running toggle for $devices"
	if (devices.any { it.currentValue('switch') == 'on' }) {
		devices.off()
		return false
	}
	else  {
		devices.on()
		return true
	}
}

/** Performs a setLevel on the specified devices with transition time preferences from this app
  */
void doSetLevel(devices, Integer level) {
	logTrace("doSetLevel($devices, $level)")
	BigDecimal transitionTime = (settings['transitionTime'] != null  && settings['transitionTime'] != 'null') ?
						         settings['transitionTime'] as BigDecimal : null
	if (transitionTime) transitionTime /= 1000
	if (transitionTime != null) {
		devices?.setLevel(level, transitionTime)
		if (settings['boolDblCmd']) {
            pauseExecution(300)
			devices?.setLevel(level, transitionTime)
		}
	} else {
		devices?.setLevel(level)
		if (settings['boolDblCmd']) {
            pauseExecution(300)
			devices?.setLevel(level)
		}
	}
}

void doActionDim(devices, Integer changeBy) {
	logDebug("doActionDim($devices, $changeBy)")
	List devs = devices?.findAll { it.currentValue('switch') != 'off' }
	logTrace("  on devices = $devs")
	BigDecimal transitionTime = (settings['transitionTime'] != null  && settings['transitionTime'] != 'null') ?
						         settings['transitionTime'] as BigDecimal : null
	if (transitionTime) transitionTime /= 1000
	devs.each {
		Integer currLvl = it.currentValue('level') as Integer
		Integer newLvl = currLvl + changeBy
		if (newLvl > 100) {
			newLvl = 100
		} else if (newLvl < 1) {
			newLvl = 1
		}
		if (transitionTime != null) {
			it.setLevel(newLvl, transitionTime)
			if (settings['boolDblCmd']) {
				pauseExecution(250)
				it.setLevel(newLvl, transitionTime)
			}
		} else {
			it.setLevel(newLvl)
			if (settings['boolDblCmd']) {
				pauseExecution(250)
				it.setLevel(newLvl)
			}
		}
	}
}

void startLevelChangeIfOn(lights, String direction="up") {
	// Skipping the usual check to see if lights are on. If they are not on,
	// none I've tested will be affected by startLevelChange commands anyway.
	logTrace("startLevelChangeIfOn($lights, $direction)")
	try {
		lights.startLevelChange(direction)
		//logTrace("Starting level change up on: ${lights}")
	} catch (e) {
	 	log.error("Unable to start level change up on ${lights}: ${e}")
	}
	logTrace("Started level change $direction on all (applicable) lights")
}

/** Turns on specified devices to specificed hue, saturation, level and/or CT;
  * if CT specified, is preferred over hue and saturation; level 0 will turn off
  */
void doActionTurnOn(devices, hueVal, satVal, levelVal, colorTemperature) {
    logTrace "Running doActionTurnOn($devices, $hueVal, $satVal, $levelVal, $colorTemperature)..."
    if (colorTemperature) {
        devices?.setColorTemperature(colorTemperature as Integer)
        if (levelVal) doSetLevel(devices, levelVal as Integer)
        if (settings['boolDblCmd']) {
            pauseExecution(400)
            devices.setColorTemperature(colorTemperature as Integer)
            pauseExecution(100)
            if (levelVal)  doSetLevel(devices, levelVal as Integer)
        }        
    }
	if (levelVal == 0) {
		devices.off()
		if (settings['boolDblCmd']) {
			pauseExecution(250)
			devices.off()
		}
	}
    else if (hueVal != null && satVal != null && levelVal != null) {
        def targetColor = [:]
        targetColor.hue = hueVal as Integer
        targetColor.saturation = satVal as Integer
        targetColor.level = levelVal as Integer
        devices?.setColor(targetColor)
        if (settings['boolDblCmd']) {
            pauseExecution(400)
            devices.setColor(targetColor)
        }
    }
	else {
        if (hueVal != null) devices.setHue(hueVal)
        if (satVal != null) devices.setSaturation(satVal)
        if (levelVal != null)  doSetLevel(devices, levelVal as Integer)
        if (settings['boolDblCmd']) {
            pauseExecution(400)
            if (hueVal != null) devices.setHue(hueVal)
            pauseExecution(100)
            if (satVal != null) devices.setSaturation(satVal)
            pauseExecution(100)
            if (levelVal != null) doSetLevel(devices, levelVal as Integer)
        }        
    }
}

/** To emulate Hue Dimmer, this app tracks 1-5 button presses
  * for one or more buttons on the button device. This retrieves
  * the current press number for the provided button number.
  */
Integer getPressNum(buttonNum, action = "pushed") {
	logTrace("getPressNum($buttonNum, $action)")
    if (eventMap[action].multiPresses) {
        String theAction = settings["btn${buttonNum}.${action}.Action"]
        Boolean canMulti = actionMap[theAction]?.multiPresses
        if (theAction && canMulti) {
        Integer pressNum = atomicState["pressNum${buttonNum}.${action}"] as Integer
            if (!pressNum) {
                pressNum = 1
                atomicState["pressNum${buttonNum}.${action}"] = pressNum
            }
            return pressNum
        } else {
            def reason = ""
            if (!theAction) reason = ", but no action was specified."
            else if (!canMulti) reason = ", but \"${theAction}\" is not a multi-press action."            
            logTrace "getPressNum for ${buttonNum} ${action} was called${reason}; returning 1"
			return 1
        }
    }
    else {
        logTrace "getPressNum for button ${buttonNum} ${action} was called but ${action} is not a " +
		         "multi-press action; returning 1"
        return 1
    }
}

/** To emulate Hue Dimmer, this app tracks 1-5 button presses
  * for one or more buttons on the button device. This increases (rolling
  * over if needed) the current press number for the provided button number
  * and is intended to be called after the button is pressed.
  */
void incrementPressNum(buttonNum, strAction = "pushed") {
	def currPress = getPressNum(buttonNum, strAction)
	def nextPress = 2
	if (currPress) {
		nextPress = currPress + 1
		if (nextPress > getMaxPressNum() || !getDoesPressNumHaveAction(buttonNum, strAction, nextPress)) {
			resetPressNum([btnNum: buttonNum, actions: [strAction]])
		}
		else {
			atomicState["pressNum${buttonNum}.$strAction"] = nextPress
		}
	}
    logTrace "Incremented pressNum for button ${buttonNum} ${strAction}: ${currPress} to ${getPressNum(buttonNum, strAction)}"
}

/** Resets next press for specified button to 1, intended to be called after
  * timeout has elapsed to "reset" count for specific button
  * Usage: params with map; key = "btnNum" and value = button number as integer,
  * optional "actions" key with value of event/attribute names (e.g., ["pushed"]); defaults to all
  * if no actions specified
  * e.g., params = [btnNum: 1, actions: ["pushed"]]
  */
void resetPressNum(Map params) {
	logDebug "Running resetPresNum($params = ${params.btnNum}, ${params.actions})"
	Integer btnNum = params.btnNum as Integer
	Set actions = params.actions ?: eventMap.keySet()
	if (btnNum != null) {
		actions.each {
			if (atomicState["pressNum${btnNum}.${it}"]) atomicState["pressNum${btnNum}.${it}"] = 1
		}
	}
	else {
		log.error "resetPressNum called with missing button number; exiting"
	}
    logTrace "Button press number reset for button ${btnNum} ${actions}"
}

/** Resets all press counts to first press for all button numbers and events/actions,
  * intended to be called after "off"-type button pressed to reset all counts
  */
void resetAllPressNums() {
	(1..getNumberOfButtons()).each { resetPressNum([btnNum: it]) }
	unschedule(resetPressNum)
}

/** Returns true if specified button number, action, and optional press have fully
  * configured action
  */
Boolean getDoesPressNumHaveAction(btnNum, strAction = "pushed", pressNum) {
    logTrace "Running getDoesPressNumHaveAction(${btnNum}, ${strAction}, ${pressNum})"
    Boolean hasAction = false
    def actionName = settings["btn${btnNum}.${strAction}.Action"]
    if (actionName == null /*|| pressNum == 1*/) {
        if (actionName == null) log.warn "No action specified but returning that pressNum has action"
		hasAction = true
	}
	else {
		if (actionName == "on") {
			if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
				def bulbSettingL = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L"					
				def bulbSettingCT = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.CT"
				def bulbSettingH = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.H"
				def bulbSettingS = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.S"
				if (settings["$bulbSettingL"] != null || settings["$bulbSettingCT"] ||
					settings["$bulbSettingH"] || settings["$bulbSettingCT"]) {
					hasAction = true
				}
			} else {
				for (dev in dimmers) {
					def bulbSettingL = "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}"					
					def bulbSettingCT = "btn${btnNum}.${strAction}.Press${pressNum}.CT.${dev.id}"
					def bulbSettingH = "btn${btnNum}.${strAction}.Press${pressNum}.H.${dev.id}"
					def bulbSettingS = "btn${btnNum}.${strAction}.Press${pressNum}.S.${dev.id}"
					if (settings["$bulbSettingL"] != null || settings["$bulbSettingCT"] ||
						settings["$bulbSettingH"] || settings["$bulbSettingCT"]) {
						hasAction = true
					}
				}
			}
		}
		else if (actionName == "scene") {
			def sc = settings["btn${btnNum}.${strAction}.Press${pressNum}.Scene"]
			if (sc) {
				hasAction = true
			}
		}
        else if (actionName == "hueScene") {
			def sc = settings["btn${btnNum}.${strAction}.Press${pressNum}.HueScene"]
			if (sc) {
				hasAction = true
			}
		}
	}
	logTrace "Returning hasAction = ${hasAction}"
	return hasAction
}

String deCamelCase(String camelCasedString) {
	// A bit simplistic but works for the attribute names at hand:
	return camelCasedString.split(/(?=[A-Z]|$)/)*.toLowerCase().join(' ')
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
	subscribe(buttonDevices, "pushed", buttonHandler)
	subscribe(buttonDevices, "held", buttonHandler)
	subscribe(buttonDevices, "released", buttonHandler)
	subscribe(buttonDevices, "doubleTapped", buttonHandler)
    if (settings['boolInitOnBoot'] || settings['boolInitOnBoot'] == null) {
	    subscribe(location, "systemStart", hubRestartHandler)   
    }
}

void hubRestartHandler(evt) {
    logDebug("Initializing ${app.label} on reboot")
}

/** Writes to log.debug by default if debug logging setting enabled; can specify
  * other log level (e.g., "info") if desired
  */
void logDebug(string, level="debug") {
	if (settings['debugLogging'] && level=="debug") {
		log.debug(string)
    } else if (settings['debugLogging']) {
        log."$level"(string)
    }
}

/** Writes to log.trace; use for development/testing */	   
void logTrace(string) {
    if (settings['traceLogging']) {
		log.trace(string)
	}
}
