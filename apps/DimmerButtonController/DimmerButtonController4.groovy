/**
 * ==========================  Dimmer Button Controller (Child  App) ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2018-2022 Robert Morris
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
 * Changelog:
 * 4.0    (2022-01-16) - Add support for setting CT with variable; removed presets (use app cloning instead) and legacy preferences
 * 3.1    (2022-01-04) - Add support for setting to level from hub variable; added command metering preference (optional)
 * 3.0.1  (2021-09-10) - Fix for UI error if device does not support ChangeLevel
 * 3.0    (2021-07-05) - Breaking changes (see release notes; keep 1.x and/or 2.x child app code if still use!);
 *                       Ability to mix actions (specific settings, scene, etc.) within same button event (e.g., button 1 first push = scene, second push = set CT/level)
 *                       Added "presets" for faster configuration; option to use new two-parameter "Set Color Temperature" (CT+level) command
 * 2.1.3  (2021-04-21) - Fix for "togggle" option not showing with only single dimmer selected
 * 2.1.2  (2020-10-10) - Bugfix for display name of press/action numbers greather than 9
 * 2.1.1  (2020-06-01) - Additional clarification of "press" terminology; replaced with button-event-specific languge where appropriate
 * 2.1    (2020-04-27) - Added ability to use individual devices vs. groups for some actions (on/off/setLevel vs. start/stopLevelChange)
 * 2.0    (2020-04-23) - Rewrite of app with cleaner UI more functionality (breaking changes; also keep 1.x child if you have instances)
 * 1.9a   (2020-01-04) - Changes to eliminate warning if no "additional off" devices selected
 * 1.9    (2019-12-06) - Added option to activate CoCoHue scenes
 * 1.8    (2019-08-02) - Added option to send commands twice (shouldn't be needed but is workaround for common bulb problems)
 * 1.7    (2019-04-29) - Added "toggle" action, "additional switches for "off" option; bug fixes (dimming, scene off)
 * 1.6    (2019-01-14) - New "held" functionality
 * 1.5    (2019-01-02) - New press/release dimming action
 * 0.9    (2018-12-27) - (Beta) First public release
 *
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

@Field static final Boolean usePrefixedDefaultLabel = false // set to true to make deafult child app name "DBC - Button Name" instead of "Button Name Dimmer Button Controller"

@Field static final Integer pressNumResetDelay = 15

@Field static final Map<String,Map> eventMap = [
   "pushed": ["capability":"PushableButton", userAction: "push", "multiPresses": true],
   "held": ["capability":"HoldableButton", userAction: "hold", "multiPresses": false],
   "released": ["capability":"ReleasableButton", userAction: "release", "multiPresses": false],
   "doubleTapped": ["capability":"DoubleTapableButton", userAction: "double tap", "multiPresses": true]
]

// Match the above event names:
@Field static final String sPUSHED = "pushed"
@Field static final String sHELD = "held"
@Field static final String sRELEASED = "released"
@Field static final String sDOUBLE_TAPPED = "doubleTapped"

@Field static final Map<String,Map> actionMap = [
   "on": [displayName: "Turn on and set dimmers/bulbs or activate scene", "multiPresses": true],
   "bri": [displayName: "Dim up", "multiPresses": false],
   "dim": [displayName: "Dim down", "multiPresses": false],
   "offLastScene": [displayName: "Turn off last used scene", "multiPresses": false],
   "offScene": [displayName: "Turn off scene", "multiPresses": false],
   "off": [displayName: "Turn off", "multiPresses": false],
]

// Match the above actionMap keys:
@Field static final String sON = "on"
@Field static final String sBRI = "bri"
@Field static final String sDIM = "dim"
@Field static final String sOFF_LAST_SCENE = "offLastScene"
@Field static final String sOFF_SCENE = "offScene"
@Field static final String sOFF = "off"
// Plus others used that are not manaully selectable (so not above):
@Field static final String sSTOP_LEVEL_CHANGE = "stopLevelChange"

@Field static final List<Map<String,String>> onSubActionMap = [
   ["toSettings": "Set dimmers/bulbs to..."],
   ["scene": "Activate scene"],
   ["hueScene": "Activate CoCoHue scene"]
]

// Match the above onSubActionMap keys:
@Field static final String sTO_SETTINGS = "toSettings"
@Field static final String sSCENE = "scene"
@Field static final String sHUE_SCENE = "hueScene"

definition(
   name: "Dimmer Button Controller (Child App) 3",
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
   page name: "pageMain"
   page name: "pageButtonConfig"
   page name: "pageFinal"
}

def pageMain() {
   dynamicPage(name: "pageMain", title: "Dimmer Button Controller", uninstall: true, install: false, nextPage: "pageFinal") {
      section("Choose devices") {
         input name: "buttonDevices", type: "capability.pushableButton", title: "Select button device(s):", multiple: true,
               required: true, submitOnChange: true
         if (settings.buttonDevices?.size() > 1) paragraph("When selecting multiple button devices, it is recommended to choose devices " +
                                                           "of the same type (capabilities and nubmer of buttons, driver, etc.).")
         input name: "dimmers", type: "capability.switchLevel",
            title: "Select lights to turn on/off and dim with below actions:", multiple: true, required: true, submitOnChange: true
         if (settings["boolGroup"]) {
               input(name: "group", type: "capability.switchLevel",
                  title: "Select group device to use when applicable:", multiple: true, required: true, submitOnChange: false)
            paragraph 'If selected, the above group device will be used when possible <em>instead of</em> the above selected lights/dimmers. Choose ' +
                      'a group that contains the same lights as the above, individually-selected bulbs. The group will be used instead for the ' +
                      'following actions: "Turn on" when "Apply settings to all..." selected; "Dim up/down" when "until released" <em>not</em> ' +
                      'selected; and "Turn off."'
         }
         input name: "offDevices", type: "capability.switch", title: "Additional lights to turn off with \"off\" actions only:",
            multiple: true, required: false
         paragraph "Actions to turn on and off lights below allow you to choose scenes <em>or</em> use the above selected lights. " +
                   "Dimming actions apply to above selected lights."
         paragraph "If you use scenes below, it is recommended you choose all bulbs above that are used in your scenes to ensure " +
                   "consistent behavior."
      }
      if(settings.buttonDevices && settings.dimmers) {
         if (!app.getLabel()) app.updateLabel(getDefaultLabel())
         section("Configure buttons") {
            List<String> caps = getButtonCapabilities()
            (1..getNumberOfButtons()).each { btnNum ->
               eventMap.each { key, value ->
                  if (value.capability in caps && (key == sRELEASED ? boolShowReleased : true)) {
                     href name: "pageButtonConfigHref",
                          page: "pageButtonConfig",
                          params: [btnNum: btnNum, action: key, multiPresses: value.multiPresses], title: "Button $btnNum ${deCamelCase(key)}",
                          description: getButtonConfigDescription(btnNum, key, value.multiPresses) ?: "Click/tap to configure",
                          state:  getButtonConfigDescription(btnNum, key, value.multiPresses) ? "complete" : null
                  }
               }
            }
         }
      }
      section("Options", hideable: true, hidden: false) {
         input name: "transitionTime", type: "enum", title: "Transition time (for dimming)", required: true,
            options: [[null:"Unspecified (use device default)"], [0:"ASAP"],[100:"100ms"],[300:"300ms"],
                      [500:"500ms"],[750:"750ms"],[1000:"1s"],[1500:"1.5s"],[3000:"3s"]], defaultValue: 100
         input name: "dimStep", type: "number", title: "Dimming buttons change level +/- by (unless \"dim until release\" enabled on supported devices)",
            description: "0-100", required: true, defaultValue: 15
         input name: "maxPressNum", type: "enum", title: "Maximum number of presses (default: 5)",
            options: [[1:1],[2:2],[3:3],[4:4],[5:5],[6:6],[7:7],[8:8],[9:9],[10:10]], defaultValue: 5
      }
      section("Advanced options", hideable: true, hidden: true) {
         input name: "boolLegacyCT", type: "bool", title: "Use legacy (one-parameter) setColorTemperature() command", defaultValue: false
         input name: "meterDelay", type: "number", title: "Metering: wait this many milliseconds between successive commands (optional)"
         input name: "boolGroup", type: "bool", title: "Allow separate selection of group device besdies individual bulbs (will attempt to use group device to optimize actions where appropriate)", submitOnChange: true
         input name: "boolShowSetForAll", type: "bool", title: "Always show \"set for all\" option even if only one dimmer/light selected (may be useful if frequently change which lights the button controls)"
         input name: "boolShowReleased", type: "bool", title: "Show actions sections for \"released\" events", submitOnChange: true
         input name: "boolToggleInc", type: "bool", title: "If using \"toggle\" option, increment press count even if lights were turned off", defaultValue: false
         input name: "boolInitOnBoot", type: "bool", title: "Initialize app on hub start (may avoid delays with first button presses after reboot)", defaultValue: true
         input name: "debugLogging", type: "bool", title: "Enable debug logging"
         //input name: "traceLogging", type: "bool", title: "Enable trace/verbose logging (for development only)"
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
   String defaultLabel = "${buttonDevices[0]?.displayName} Dimmer Button Controller"
   // see field variable at top of code:
   if (usePrefixedDefaultLabel == true) defaultLabel = "DBC - ${buttonDevices[0]?.displayName}"
   return defaultLabel
}

String getOrdinal(String action="pushed", Integer pressNum=1) {
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
         String end = pressNum.toString()[-1]
         if (end == 1) ordinal += "st"
         else if (end == 2) ordinal += "nd"
         else if (end == 3) ordinal += "rd"
         else ordinal += "th"
   }
   return "$ordinal $actionDisplayName"
}

Integer getNumberOfButtons() {
   Integer num = settings.buttonDevices*.currentValue('numberOfButtons').max()
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

List<String> getButtonCapabilities() {
   List<String> btnCapabs = []
   List<String> allCapabs = settings.buttonDevices*.getCapabilities().name.flatten()
   ["PushableButton", "HoldableButton", "ReleasableButton", "DoubleTapableButton"].each { String c ->
      if (c in allCapabs) btnCapabs.add(c)
   }
   return btnCapabs
}

def pageButtonConfig(params) {
   //logTrace("pageButtonConfig($params)")
   if (params) {
      atomicState.currentParams = params
   }
   else {
      params = atomicState.currentParams
   }
   Integer btnNum = params.btnNum
   String action = params.action
   Boolean multiPresses = params.multiPresses
   dynamicPage(name: "pageButtonConfig", title: "Button ${btnNum} Configuration", uninstall: true, install: false) {
      if(settings.buttonDevices && settings.dimmers && btnNum) {
         String btnActionSettingName = "btn${btnNum}.${action}.Action"
         section("Actions for button ${btnNum} ${deCamelCase(action)}") {
         input(name: btnActionSettingName, type: "enum", title: "Do...",
            options:  actionMap.collect { actMap -> ["${actMap.key}": actMap.value.displayName] },
            submitOnChange: true)
         }
         String actionSetting = settings["${btnActionSettingName}"]
         if (actionSetting) {
            switch(actionSetting) {
               case sON:
                  makeTurnOnSection(btnNum, action, multiPresses)
                  break
               case sSCENE:
                  makeTurnOnSceneSection(params.btnNum, params.action, params.multiPresses)
                  break
               case sHUE_SCENE:
                  makeActivateHueSceneSection(params.btnNum, params.action, params.multiPresses)
                  break
               case sBRI:
                  makeDimSection(params.btnNum, params.action, "up")
                  break
               case sDIM:
                  makeDimSection(params.btnNum, params.action, "down")
                  break
               case sOFF_LAST_SCENE:
                  makeTurnOffLastSceneSection()
                  break
               case sOFF_SCENE:
                  makeTurnOffSceneSection(params.btnNum, params.action)
                  break
               case sOFF:
                  makeTurnOffSection()
                  break
               default:
                  paragraph("Not set")
            }
         }
      }
   }
}

String getButtonConfigDescription(btnNum, String action, Boolean multiPresses) {
   StringBuilder sbDesc = new StringBuilder()
   String actionSettingName =  "btn${btnNum}.${action}.Action"
   Integer maxPress = multiPresses ? getMaxPressNum() : 1
   if (settings[actionSettingName] == sON) {
      for (pressNum in 1..maxPress) {
         if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
            String pressNumString = getOrdinal(action, pressNum)
            Boolean toggleSet = settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]
            String subActionSettingName = "btn${btnNum}.${action}.Press${pressNum}.SubAction"
            if (multiPresses) sbDesc << "\n<span style=\"font-variant: small-caps\">$pressNumString</span>: "
            if (toggleSet) {
               sbDesc << "\n${multiPresses ? '  ' : ''}Toggle or..."
            }
            // IF set dimmer/bulb to...
            if (settings[subActionSettingName] == sTO_SETTINGS || settings[subActionSettingName] == null) {
               if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
                     String lVal = (settings["btn${btnNum}.${action}.Press${pressNum}.UseVarLevel"]) ? 
                                       settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L.Var"] :
                                       settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"]
                     Integer ctVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.CT"]
                     Integer hVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.H"]
                     Integer sVal = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.S"]
                     String dev = "all"
                     sbDesc << "${multiPresses ? '\n  T' : '\nT'}urn on ${dev}"
                     if (lVal != null || ctVal || hVal != 0 || sVal != 0) {
                        sbDesc << " - "
                     }
                     // TODO: Better string-ification of this (and similar section below)
                     if (hVal != null) sbDesc << "Hue: ${hVal}  "
                     if (sVal) sbDesc << "Sat: ${sVal}  "
                     if (lVal != null) sbDesc << "Level: ${lVal}  "
                     if (ctVal) sbDesc << "CT: ${ctVal} "
               }
               else {
                  for (DeviceWrapper dev in settings["dimmers"]) {
                     String lVal = (settings["btn${btnNum}.${action}.Press${pressNum}.UseVarLevel"]) ?
                                    settings["btn${btnNum}.${action}.Press${pressNum}.L.Var.${dev.id}"] :
                                    settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                     Integer ctVal = settings["btn${btnNum}.${action}.Press${pressNum}.CT.${dev.id}"]
                     Integer hVal = settings["btn${btnNum}.${action}.Press${pressNum}.H.${dev.id}"]
                     Integer sVal = settings["btn${btnNum}.${action}.Press${pressNum}.S.${dev.id}"]
                     sbDesc << "${multiPresses ? '\n  T' : '\nT'}urn on ${dev.displayName}"
                     if (lVal != null || ctVal || hVal != 0 || sVal != 0) {
                        sbDesc <<" - "
                     }
                     if (hVal != null) sbDesc << "Hue: ${hVal}  "
                     if (sVal) sbDesc << "Sat: ${sVal}  "
                     if (lVal != null) sbDesc << "Level: ${lVal}  "
                     if (ctVal) sbDesc << "CT: ${ctVal} "
                  }
               }
            }
            // IF activate scene...
            else if (settings[subActionSettingName] == sSCENE) {
                  List<String> devNames = settings["btn${btnNum}.${action}.Press${pressNum}.Scene"].collect { DeviceWrapper dev ->
                     dev.displayName
                  }
                  sbDesc << "Activate scene: ${devNames.join(', ')}"
            }
            // IF activate Hue scene...
            else if (settings[subActionSettingName] == sHUE_SCENE) {
                  List<String> devNames = settings["btn${btnNum}.${action}.Press${pressNum}.HueScene"].collect { DeviceWrapper dev ->
                     dev.displayName
                  }
                  sbDesc << "Activate Hue scene: ${devNames.join(', ')}"
            }
         }
      }
   }
   else if (settings[actionSettingName] == sOFF_SCENE) {
      String scOffSettingName = "btn${btnNum}.${action}.Press${pressNum}.OffScene"
      String sc = settings[scOffSettingName].displayName
      sbDesc << "\nTurn off scene: ${sc}"
   }
   else if (settings[actionSettingName] == sOFF_LAST_SCENE) {
      sbDesc << "\nTurn off last used scene"
   }
   else if (settings[actionSettingName] == sBRI || settings[actionSettingName] == sDIM) {
      if (settings[actionSettingName]) {
         String actionStr = actionMap[settings[actionSettingName]].displayName
         sbDesc << "\n${actionStr}"
         String levelChangeSettingName = "btn${btnNum}.${action}.UseStartLevelChange"
         if (settings[levelChangeSettingName]) sbDesc << " until released"
      }
   }
   else if (settings[actionSettingName] == sOFF) {
      sbDesc << "\nTurn off"
   }
   else {
      logDebug("Description for button $btnNum $action unspecified", "trace")
   }
   //log.warn "Returning: ${sbDesc.toString().trim()}"
   return sbDesc.toString().trim()
}

def makeTurnOnSection(btnNum, strAction = sPUSHED, multiPresses = false) {
   logTrace("Running makeTurnOnSection($btnNum, $strAction, $multiPresses)")
   if (params) {
      atomicState.currentParams = params
   } else {
      params = atomicState.currentParams
   }
   Integer maxPressNum = multiPresses ? getMaxPressNum() : 1
   for (Integer pressNum in 1..maxPressNum) {
      if (pressNum == 1 || (pressNum > 1 && multiPresses && getDoesPressNumHaveAction(btnNum, strAction, pressNum-1))) {
         String sectionTitle = multiPresses ? getOrdinal(strAction, pressNum) : "Button ${btnNum} ${strAction}"
         section(sectionTitle, hideable: true, hidden: false) {
            String subActionSettingName = "btn${btnNum}.${strAction}.Press${pressNum}.SubAction"
            input name: subActionSettingName, type: "enum", title: "Action type:", options: onSubActionMap,
                  submitOnChange: true, defaultValue: sTO_SETTINGS
            // SETTINGS FOR: Turn on and set dimmer/bulb or activate scene
            if (settings[subActionSettingName] == sTO_SETTINGS || settings[subActionSettingName] == null) {
               if (dimmers.size() > 1 || settings["boolShowSetForAll"]) {
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll", type: "bool",
                        title: "Apply same level and color settings to all lights", submitOnChange: true, defaultValue: false
               }
               if (pressNum == 1) {
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
                  title: "Toggle (turn all off if any on; otherwise, turn on as specified)", submitOnChange: true, defaultValue: false
               }
               else {
                  app.removeSetting("btn${btnNum}.${strAction}.Press${pressNum}.Toggle")
               }
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.UseVarLevel", type: "bool",
                     title: "Use hub variable for level", submitOnChange: true
               if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
                  paragraph "", width: 3
                  paragraph "<strong>Level</strong>", width: 2
                  paragraph "<strong>Color Temp.</strong>", width: 3
                  paragraph "<strong>Hue</strong>", width: 2
                  paragraph "<strong>Saturation</strong>", width: 2
                  paragraph "Set all :", width: 3
                  if (settings["btn${btnNum}.${strAction}.Press${pressNum}.UseVarLevel"]) {
                     List<String> vars =  getGlobalVarsByType("integer")?.collect { it.key } ?: []
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L.Var", type: "enum",
                        title: "", options: vars, submitOnChange: false, width: 2, required: false
                  }
                  else {
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L", type: "number",
                        title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 2, required: false
                  }
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.CT", type: "number",
                     title: "", description: "~2000-7000", range: "1000..8000", submitOnChange: false, width: 3, required: false
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.H", type: "number",
                     title: "", range: "0..360", description: "0-100", submitOnChange: false, width: 2, required: false
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.S", type: "number",
                     title: "", range: "0..100", description: "0-100", submitOnChange: false, width: 2, required: false
               }
               else {
                  paragraph "<strong>Device</strong>", width: 3
                  paragraph "<strong>Level</strong>", width: 2
                  paragraph "<strong>Color Temp.</strong>", width: 3
                  paragraph "<strong>Hue</strong>", width: 2
                  paragraph "<strong>Saturation</strong>", width: 2
                  for (dev in settings["dimmers"]) {
                     paragraph "${dev.displayName}:", width: 3
                     if (settings["btn${btnNum}.${strAction}.Press${pressNum}.UseVarLevel"]) {
                        List<String> vars =  getGlobalVarsByType("integer")?.collect { it.key } ?: []
                        input name: "btn${btnNum}.${strAction}.Press${pressNum}.L.Var.${dev.id}", type: "enum",
                           title: "", options: vars, submitOnChange: false, width: 2, required: false
                     }
                     else {
                        input name: "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}", type: "number",
                           title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 2, required: false
                     }
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.CT.${dev.id}", type: "number",
                           title: "", description: "~1000-8000", range: "1000..8000", submitOnChange: false, width: 3, required: false
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.H.${dev.id}", type: "number",
                           title: "", range: "0..360", description: "0-100", submitOnChange: false, width: 2, required: false
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.S.${dev.id}", type: "number",
                           title: "", range: "0..100", description: "0-100", submitOnChange: false, width: 2, required: false
                  }
               }
               if (pressNum < maxPressNum) {
                  paragraph "", width: 5
                  paragraph "", width: 4
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.SaveButton", type: "button",
                        title: "Save Presses", width: 3, submitOnChange: true
               }
            }
            // SETTINGS FOR: Activate scene
            else if (settings[subActionSettingName] == sSCENE) {
               if (pressNum == 1) {
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
                        title: "Toggle (turn all off if any on, or activate scene if all off)", defaultValue: false
               }
               input name: "btn${btnNum}.${strAction}.Press${pressNum}.Scene", type: "device.SceneActivator",
                     title: "Scene(s):", multiple: true, submitOnChange: multiPresses
            }
            // SETTINGS FOR: Activate CoCoHue scene
            else if (settings[subActionSettingName] == sHUE_SCENE) {
               if (pressNum == 1) {
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
                        title: "Toggle (turn all off if any on, or activate scene if all off)", defaultValue: false
               }
               input name: "btn${btnNum}.${strAction}.Press${pressNum}.HueScene", type: "device.CoCoHueScene",
                     title: "Hue scene(s):", multiple: true, submitOnChange: multiPresses
            }
         }
      }
   }
   section {
      paragraph("<small>For setting dimmers/bulbs, at least one field is required to be specified; all are otherwise optional. Color temperature takes precedence over hue and saturation if specified.</small>")
   }
}

def makeTurnOffLastSceneSection() {
   section {
      paragraph "Turn off last scene turned on by this app (will not track scenes turned on by " + 
                "other apps/automations, including other Dimmer Button Controller instances)."
   }
}

def makeTurnOffSceneSection(btnNum, strAction = sPUSHED, multiPresses = false) {
   Integer maxPressNum = multiPresses ? getMaxPressNum() : 1
   section() {
      for (pressNum in 1..maxPressNum) {
         if (pressNum == 1 || getDoesPressNumHaveAction(btnNum, strAction, pressNum-1)) {
            if (mutliPresses) paragraph(getOrdinal(strAction, pressNum))
            input name: "btn${btnNum}.${strAction}.Press${pressNum}.OffScene", type: "device.SceneActivator",
               title: "Turn off scene(s):", submitOnChange: mutliPresses
         }
      }
   }
}

def makeTurnOffSection() {
   section {
      paragraph "Turn off all selected lights."
   }
}

/** Makes dim up/down section; direction must be 'up' or 'down' */
def makeDimSection(btnNum, String strAction = sPUSHED, String direction) {
   String rampSettingName = "btn${btnNum}.${strAction}.UseStartLevelChange"
   section() {
      if (!settings[rampSettingName]) {
      paragraph "Adjust level by ${direction == 'up' ? '+' : '-'}${settings[dimStep] ?: 15}% for any " +
                "lights that are on when button ${btnNum} is $strAction"
      } else {
         paragraph "Dim $direction on ${eventMap[strAction]?.userAction}"
      }
   }
   section('<strong>Options</strong>') {
      if (buttonDevices.any { it.hasCapability("ReleasableButton") } && (strAction == "pushed" || strAction == "held")) {
         String settingTitle = "Dim until release (start level change when button ${strAction}, stop level change when button is released)"
         input name: rampSettingName, type: "bool", title: settingTitle, submitOnChange: true
         if (settings.dimmers?.any { !(it.hasCapability("ChangeLevel")) }) {
            List<DeviceWrapper> unsupportedDevices = settings.dimmers.findAll { !(it.hasCapability("ChangeLevel")) }
            paragraph """Warning: one or more lights do not support the "Start Level Change" commands: ${unsupportedDevices.join(", ")}. """ +
                      "The \"Dim until release\" option above will probably not work."
         }
      } else {
         app.removeSetting("btn${btnNum}.${strAction}.UseStartLevelChange")
         paragraph "No additional options avaiable for this action with this button device"
      }
   }
   if (settings[rampSettingName]) {
      String releaseSettingName = "btn${btnNum}.released.Action"
      app.updateSetting(releaseSettingName, [type:"string", value: sSTOP_LEVEL_CHANGE])
   } else {
      String releaseSettingName = "btn${btnNum}.released.Action"
      app.removeSetting(releaseSettingName)
   }
}

void appButtonHandler(String btn) {
      switch(btn) {
         default:
            log.warn "Unhandled button press: $btn"
      }
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
   Integer btnNum = new Integer(evt.value)
   String action = evt.name
   String actionSettingName =  "btn${btnNum}.${action}.Action"
   //log.error "==== $actionSettingName = ${settings[actionSettingName]} ===="
   switch (settings[actionSettingName] as String) {
      case sON:
         Integer pressNum = getPressNum(btnNum, action)
         logDebug "Action \"Turn on and set... or activate scene\" specified for button ${btnNum} ${action} press ${pressNum}"
         ///// if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
         /////   logDebug "  SetForAll or Toggle configured for press ${pressNum}", "trace"
         Boolean didToggle = false
         if (settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]) {
            logDebug "  Toggle configured for button ${btnNum} press ${pressNum}", "trace"
            if (dimmers.any { it.currentValue("switch") == "on"} ) {
               didToggle = true
               List<DeviceWrapper> devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
               devices.off()
               if (settings['boolToggleInc']) {
                  logTrace "  Incrementing press number because 1+ lights turned off and setting configured to increase"
                  incrementPressNum(btnNum, action)
                  runIn(pressNumResetDelay, "resetPressNum", [data: [btnNum: btnNum, action: [action]]])
               }
               break
            }
         }
         if (!didToggle) {
            String subActionSettingName = "btn${btnNum}.${action}.Press${pressNum}.SubAction"
            // IF set dimmer/bulb to...
            if (settings[subActionSettingName] == sTO_SETTINGS || settings[subActionSettingName] == null) {
               if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
                  logDebug "  SetForAll or configured for press ${pressNum}", "trace"
                  Integer bulbLevel 
                  if (settings["btn${btnNum}.${action}.Press${pressNum}.UseVarLevel"]) {
                     bulbLevel = getGlobalVar(settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L.Var" as String].value)
                  }
                  else {
                     bulbLevel = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"]
                  }
                  String bulbSettingL = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"
                  String bulbSettingCT = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.CT"
                  String bulbSettingH = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.H"
                  String bulbSettingS = "btn${btnNum}.${action}.Press${pressNum}.SetForAll.S"
                  List<DeviceWrapper> devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
                  doActionTurnOn(devices, settings[bulbSettingH], settings[bulbSettingS],
                           bulbLevel, settings[bulbSettingCT])
               }
               else {
                  logTrace "  Iterating over each device..."
                  for (DeviceWrapper dev in dimmers) {
                     Integer bulbLevel 
                     if (settings["btn${btnNum}.${action}.Press${pressNum}.UseVarLevel"]) {
                        bulbLevel = getGlobalVar(settings["btn${btnNum}.${action}.Press${pressNum}.L.Var.${dev.id}" as String]).value
                     }
                     else {
                        bulbLevel = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                     }
                     String bulbSettingCT = "btn${btnNum}.${action}.Press${pressNum}.CT.${dev.id}"
                     String bulbSettingH = "btn${btnNum}.${action}.Press${pressNum}.H.${dev.id}"
                     String bulbSettingS = "btn${btnNum}.${action}.Press${pressNum}.S.${dev.id}"
                     doActionTurnOn(dev, settings[bulbSettingH], settings[bulbSettingS],
                        bulbLevel, settings[bulbSettingCT])
                  }
               }
            }
            // IF activate scene...
            else if (settings[subActionSettingName] == sSCENE) {
               logDebug "Subaction \"Activate scene\" specified..."
               List<DeviceWrapper> devs = settings["btn${btnNum}.${action}.Press${pressNum}.Scene"]
               atomicState.lastScene = "btn${btnNum}.${action}.Press${pressNum}.Scene"
               devs.each { DeviceWrapper dev ->
                  dev.on()
                  if (settings.meterDelay) pauseExecution(settings.meterDelay)
               }
               logDebug "Scene(s) turned on: ${devs}"
            }
            // IF activate Hue scene...
            else if (settings[subActionSettingName] == sHUE_SCENE) {
               logDebug "Subaction \"Activate Hue scene\" specified..."
               List<DeviceWrapper> devs = settings["btn${btnNum}.${action}.Press${pressNum}.HueScene"]
               atomicState.lastScene = "btn${btnNum}.${action}.Press${pressNum}.HueScene"
               devs.each { DeviceWrapper dev ->
                  dev.on()
                  if (settings.meterDelay) pauseExecution(settings.meterDelay)
               }
               logDebug "Hue scene(s) turned on: ${devs}"
            }
         }
         incrementPressNum(btnNum, action)
         runIn(pressNumResetDelay, "resetPressNum", [data: [btnNum: btnNum, action: [action]]])
         break
      case sOFF_LAST_SCENE:
         if (atomicState.lastScene) {   
            logDebug("Action \"Turn off last used scene\" specified for button ${btnNum} ${action}; turning off scene ${settings[atomicState.lastScene]}")
            settings[atomicState.lastScene].off()
         } else {
            log.debug ("Configured to turn off last used scene but no scene was previously used; exiting.")
         }
         resetAllPressNums()
         break
      case sOFF_SCENE:
         logDebug "Action \"Turn off scene\" specified for button ${btnNum} ${action}"
         Integer pressNum = getPressNum(btnNum)      
         def sc = settings["btn${btnNum}.${action}.Press${pressNum}.OffScene"]
         sc?.off()
         resetAllPressNums()
         break
      case sOFF:
         logDebug "Action \"turn off\" specified for button ${btnNum} ${action}"
         try {
            List<DeviceWrapper> devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
            devices.each { DeviceWrapper dev ->
               dev.off()
               if (settings.meterDelay) pauseExecution(settings.meterDelay)
            }
            offDevices?.each { DeviceWrapper dev ->
               dev.off()
               if (settings.meterDelay) pauseExecution(settings.meterDelay)
            }
         } catch (e) {
            log.error "Error when running \"off\" action: ${e}"
         } finally {
            resetAllPressNums()
         }
         break
      case sDIM:
         logDebug "Action \"dim\" specified for button ${btnNum} ${action}"
         if (settings["btn${btnNum}.${action}.UseStartLevelChange"]) {
            //logTrace "UseStartLevelChange option enabled for button ${btnNum} ${action}"
            startLevelChangeIfOn(dimmers, "down")
         }
         else {
            //log.trace "Ramp-down dimming option NOT enabled for button ${btnNum}"
            Integer changeBy = settings[dimStep] ? 0 - settings[dimStep] as Integer : -15
            List<DeviceWrapper> devices = (settings['boolGroup'] && settings['group']) ? group : dimmers
            doActionDim(devices, changeBy)
         }
         break
      case sBRI:
         logDebug "Action \"brighten\" specified for button ${btnNum}"
         if (settings["btn${btnNum}.${action}.UseStartLevelChange"]) {
            //logTrace "Ramp-up dimming option enabled for button ${btnNum}"
            startLevelChangeIfOn(dimmers, "up")
         }
         else {
            //log.trace "Ramp-up dimming option NOT enabled for button ${btnNum}" 
            Integer changeBy = settings[dimStep] ? settings[dimStep] as Integer : 15
            List<DeviceWrapper> devices = (settings['boolGroup'] && settings['group']) ? group : dimmers            
            doActionDim(devices, changeBy)
         }
         break
      case sSTOP_LEVEL_CHANGE:
         Boolean doStop = false
         eventMap.each { key, value ->
            if (settings["btn${btnNum}.${key}.Action"] == sDIM ||
                  settings["btn${btnNum}.${key}.Action"] == sBRI) {
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
   if (devices.any { it.currentValue('switch') == "on" }) {
      devies.each { DeviceWrapper dev ->
         dev.off()
         if (settings.meterDelay) pauseExecution(settings.meterDelay)
      }
      return false
   }
   else  {
      devies.each { DeviceWrapper dev ->
         dev.on()
         if (settings.meterDelay) pauseExecution(settings.meterDelay)
      }
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
      devices?.each { DeviceWrapper dev ->
         dev.setLevel(level, transitionTime)
         if (settings.meterDelay) pauseExecution(settings.meterDelay)
      }
   }
   else {
      devices?.each { DeviceWrapper dev ->
         dev.setLevel(level)
         if (settings.meterDelay) pauseExecution(settings.meterDelay)
      }
   }
}

void doActionDim(devices, Integer changeBy) {
   logDebug("doActionDim($devices, $changeBy)")
   List<DeviceWrapper> devs = devices?.findAll { it.currentValue("switch") != "off" }
   logTrace("  on devices = $devs")
   BigDecimal transitionTime = (settings['transitionTime'] != null  && settings['transitionTime'] != 'null') ?
                                settings['transitionTime'] as BigDecimal : null
   if (transitionTime) transitionTime /= 1000
   devs.each { DeviceWrapper it ->
      Integer currLvl = it.currentValue('level') as Integer
      Integer newLvl = currLvl + changeBy
      if (newLvl > 100) {
         newLvl = 100
      }
      else if (newLvl < 1) {
         newLvl = 1
      }
      if (transitionTime != null) {
         it.setLevel(newLvl, transitionTime)
      }
      else {
         it.setLevel(newLvl)
      }
   }
   if (settings.meterDelay /*&& currDevNum < devs.size()*/) {
      //currDevNum++
      pauseExecution(settings.meterDelay)
   }
}

void startLevelChangeIfOn(List<DeviceWrapper> lights, String direction="up") {
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
void doActionTurnOn(devices, Number hueVal, Number satVal, Number levelVal, Number colorTemperature) {
   logTrace "Running doActionTurnOn($devices, $hueVal, $satVal, $levelVal, $colorTemperature)..."
   if (colorTemperature) {
      if (levelVal) {
         if (settings.boolLegacyCT == true) {
            devices?.setColorTemperature(colorTemperature)
            //Integer currDevNum = 1
            devices?.each { DeviceWrapper dev ->
               dev.setColorTemperature(colorTemperature)
               if (settings.meterDelay /* && currDevNum < devices.size() */) {
                  //currDevNum++
                  if (settings.meterDelay) pauseExecution(settings.meterDelay)
               }
            }
            doSetLevel(devices, levelVal as Integer)
         }
         else {
            //Integer currDevNum = 1
            devices?.each { DeviceWrapper dev ->
               dev.setColorTemperature(colorTemperature, levelVal)
            }
            if (settings.meterDelay /*&& currDevNum < devices.size()*/) {
               //currDevNum++
               if (settings.meterDelay) pauseExecution(settings.meterDelay)
            }
         }
      }
      else {
         //Integer currDevNum = 1
         devices?.each { DeviceWrapper dev ->
            dev.setColorTemperature(colorTemperature)
            if (settings.meterDelay /*&& currDevNum < devices.size()*/) {
               //currDevNum++
               pauseExecution(settings.meterDelay)
            }
         }
      }
   }
   if (levelVal == 0) {
      //Integer currDevNum = 1
      devices.each { DeviceWrapper dev ->
         dev.off()
         if (settings.meterDelay /*&& currDevNum < devices.size()*/) {
            //currDevNum++
            pauseExecution(settings.meterDelay)
         }
      }
   }
   else if (hueVal != null && satVal != null && levelVal != null && !colorTemperature) {
      Map<String,Integer> targetColor = [:]
      targetColor.hue = hueVal as Integer
      targetColor.saturation = satVal as Integer
      targetColor.level = levelVal as Integer
      //Integer currDevNum = 1
      devices.each { DeviceWrapper dev ->
         dev.setColor(targetColor)
         if (settings.meterDelay /*&& devices.size()*/) {
            //currDevNum++
            pauseExecution(settings.meterDelay)
         }
      }
   }
   else if (!colorTemperature) {
      if (hueVal != null) {
         //Integer currDevNum = 1 
         devices.each { DeviceWrapper dev ->
            dev.setHue(hueVal)
            if (settings.meterDelay /* && currDevNum < devices.size()*/) {
               //currDevNum++
               pauseExecution(settings.meterDelay)
            }
         }
      }
      if (satVal != null) {
         //Integer currDevNum = 1 
         devices.each { DeviceWrapper dev ->
            dev.setSaturation(satVal)
            if (settings.meterDelay /* && currDevNum < devices.size()*/) {
               //currDevNum++
               pauseExecution(settings.meterDelay)
            }
         }
      }
      if (levelVal != null)  doSetLevel(devices, levelVal as Integer)
   }
}

/** To emulate Hue Dimmer, this app tracks 1-5 button presses
  * for one or more buttons on the button device. This retrieves
  * the current press number for the provided button number.
  */
Integer getPressNum(buttonNum, action = sPUSHED) {
   logTrace("getPressNum($buttonNum, $action)")
   if (eventMap[action].multiPresses) {
      String theAction = settings["btn${buttonNum}.${action}.Action" as String]
      Boolean canMulti = actionMap[theAction]?.multiPresses
      if (theAction && canMulti) {
      Integer pressNum = atomicState["pressNum${buttonNum}.${action}" as String] as Integer
         if (!pressNum) {
            pressNum = 1
            atomicState["pressNum${buttonNum}.${action}" as String] = pressNum
         }
         return pressNum
      }
      else {
         String reason = ""
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
void incrementPressNum(buttonNum, strAction = sPUSHED) {
   Integer currPress = getPressNum(buttonNum, strAction)
   Integer nextPress = 2
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
Boolean getDoesPressNumHaveAction(btnNum, String strAction = sPUSHED, pressNum) {
   logTrace "Running getDoesPressNumHaveAction(${btnNum}, ${strAction}, ${pressNum})"
   Boolean hasAction = false
   String actionName = settings["btn${btnNum}.${strAction}.Action"]
   if (actionName == null /*|| pressNum == 1*/) {
      if (actionName == null) log.warn "No action specified but returning that pressNum has action"
      hasAction = true
   }
   else {
      if (actionName == sON) {
         String subActionSettingName = "btn${btnNum}.${strAction}.Press${pressNum}.SubAction"
         if (settings[subActionSettingName] == sTO_SETTINGS || settings[subActionSettingName] == null) {
            if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
               String bulbSettingL = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L"
               String bulbSettingCT = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.CT"
               String bulbSettingH = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.H"
               String bulbSettingS = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.S"
               if (settings["$bulbSettingL"] != null || settings["$bulbSettingCT"] ||
                  settings["$bulbSettingH"] || settings["$bulbSettingCT"]) {
                  hasAction = true
               }
            }
            else {
               dimmers.each { DeviceWrapper dev ->
                  String bulbSettingL = "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}"
                  String bulbSettingCT = "btn${btnNum}.${strAction}.Press${pressNum}.CT.${dev.id}"
                  String bulbSettingH = "btn${btnNum}.${strAction}.Press${pressNum}.H.${dev.id}"
                  String bulbSettingS = "btn${btnNum}.${strAction}.Press${pressNum}.S.${dev.id}"
                  if (settings["$bulbSettingL"] != null || settings["$bulbSettingCT"] ||
                     settings["$bulbSettingH"] || settings["$bulbSettingCT"]) {
                     hasAction = true
                  }
            
               }
            }
         }
         else if (settings[subActionSettingName] == sSCENE) {
            if (settings["btn${btnNum}.${strAction}.Press${pressNum}.Scene"]) hasAction = true
         }
         else if (settings[subActionSettingName] == sHUE_SCENE) {
            if (settings["btn${btnNum}.${strAction}.Press${pressNum}.HueScene"]) hasAction = true
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

void installed() {
   log.trace "Installed"
   initialize()
}

void updated() {
   log.trace "Updated"
   unschedule()
   initialize()
}

void initialize() {
   log.trace "Initialized"
   unsubscribe()
   subscribe(buttonDevices, sPUSHED, buttonHandler)
   subscribe(buttonDevices, sHELD, buttonHandler)
   subscribe(buttonDevices, sRELEASED, buttonHandler)
   subscribe(buttonDevices, sDOUBLE_TAPPED, buttonHandler)
   if (settings['boolInitOnBoot'] || settings['boolInitOnBoot'] == null) {
      subscribe(location, "systemStart", hubRestartHandler)   
   }
   registerHubVariables()
   
}

// Adds app to "In use by" for any used hub variables
void registerHubVariables () {
   logDebug "registerHubVariables()"
   removeAllInUseGlobalVar()
   pauseExecution(50)
   List<String> inUseVars = []
   (1..getNumberOfButtons()).each { btnNum ->
      eventMap.each { key, value ->
         // getButtonConfigDescription(btnNum, key, value.multiPresses)
         String actionSettingName = "btn${btnNum}.${key}.Action"
         Integer maxPress = getMaxPressNum() ?: 1
         if (settings[actionSettingName] == sON) {
            for (Integer pressNum in 1..maxPress) {
               if (getDoesPressNumHaveAction(btnNum, key, pressNum)) {
                  String subActionSettingName = "btn${btnNum}.${key}.Press${pressNum}.SubAction"
                  if (settings[subActionSettingName] == sTO_SETTINGS || settings[subActionSettingName] == null) {
                     if (settings["btn${btnNum}.${key}.Press${pressNum}.UseVarLevel"]) {
                        if (settings["btn${btnNum}.${key}.Press${pressNum}.SetForAll"]) {
                           inUseVars << (settings["btn${btnNum}.${key}.Press${pressNum}.SetForAll.L.Var"] as String)
                        }
                        else {
                           for (DeviceWrapper dev in settings["dimmers"]) {
                              String v = settings["btn${btnNum}.${key}.Press${pressNum}.L.Var.${dev.id}"]
                              if (v != null) inUseVars << v
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
   logDebug "In-use variables: $inUseVars"
   if (inUseVars) addInUseGlobalVar(inUseVars)
}

void hubRestartHandler(evt) {
   logDebug("Initializing ${app.label} on reboot")
}

/** Writes to log.debug by default if debug logging setting enabled; can specify
  * other log level (e.g., "info") if desired
  */
void logDebug(string, level="debug") {
   if (settings['debugLogging'] == true && level=="debug") {
      log.debug(string)
   }
   else if (settings['debugLogging'] == true) {
      log."$level"(string)
   }
}

/** Writes to log.trace; use for development/testing */      
void logTrace(string) {
   if (settings['traceLogging'] == true) {
      log.trace(string)
   }
}
