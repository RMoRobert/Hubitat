/**
 * ==========================  Blinds Button Controller (Child App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used to used with button devices and makes it easy to
 *  configure each button to open, close, or ajust blinds/shades.
 
 *  TO INSTALL:
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
 *  app only (do not use child app directly).
 *
 *  Copyright 2021 Robert Morris
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
 *  Author: Robert Morris
 *
 *
 * Changelog:
 * 1.0    (2021-05-10) - Initial release
 *
 */ 

import groovy.transform.Field

@Field static final String sPUSHED = "pushed"
@Field static final String sHELD = "held"
@Field static final String sRELEASED = "released"
@Field static final String sDOUBLE_TAPPED = "doubleTapped"

@Field static final String sOPEN = "open"
@Field static final String sCLOSE = "close"
@Field static final String sUP = "up"
@Field static final String sDOWN = "down"
@Field static final String sTO_POSITION = "toPosition"

@Field static final Map<String,Map> eventMap = [
   "pushed": [capability:"PushableButton", userAction: "push", multiPresses: false],
   "held": [capability:"HoldableButton", userAction: "hold", multiPresses: false],
   "doubleTapped": [capability:"DoubleTapableButton", userAction: "double tap", multiPresses: false]
]

@Field static final Map<String,Map<String,String>> actionMap = [
   "open": [displayName: "Open"],
   "close": [displayName: "Close"],
   "up": [displayName: "Adjust up"],
   "down": [displayName: "Adjust down"],
   "toPosition": [displayName: "Set to position"]
]

@Field static java.util.concurrent.ConcurrentHashMap<Long,Byte> lastLevel = [:]
@Field static java.util.concurrent.ConcurrentHashMap<Long,Map<String,Byte>> pressNum = [:]

definition(
   name: "Blinds Button Controller (Child App) 1",
   namespace: "RMoRobert",
   parent: "RMoRobert:Blinds Button Controller",
   author: "Robert Morris",
   description: "Do not install directly. Install Blinds Button Controller parent app, then create new automations using that app.",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""/*,
   documentationLink: ""*/
)

preferences {
   page name: "pageMain"
   page name: "pageFinal"
   page name: "pageButtonConfig"
}

def pageMain() {
   dynamicPage(name: "pageMain", title: "Blinds Button Controller", uninstall: true, install: true) {
      section("Name") {
         label title: "Name this Blinds Button Controller:", required: true
      }
      section("Choose button device") {
         input name: "buttonDevices", type: "capability.pushableButton", title: "Select button device(s)",
            multiple: true, required: true, submitOnChange: true
         if (settings.buttonDevices?.size() > 1) {
            paragraph "When selecting multiple button devices, it is recommended to choose devices " +
                      "of the same type (capabilities and nubmer of buttons, driver, etc.)."
         }
      }
      section("Choose devices to control") {
         input name: "devices.windowShade", type: "capability.windowShade", title: "Window shade devices", multiple: true,
            submitOnChange: true
         input name: "devices.windowBlind", type: "capability.windowBlind", title: "Window blind devices", multiple: true,
            submitOnChange: true
         input name: "devices.switchLevel", type: "capability.switchLevel", title: "Level (dimmer) devices", multiple: true,
            submitOnChange: true
         if (isSameDeviceSelectedTwice()) {
            paragraph "WARNING: You have selected the same device multiple times above. It is recommended to select a device from only one list."
         }
      }
      if (settings.buttonDevices) {
         section("Configure buttons") {
            List<String> caps = getButtonCapabilities()
            (1..getNumberOfButtons()).each { Integer btnNum ->
                  eventMap.each { String key, Map value ->
                  if (value.capability in caps && (key == "released" ? boolShowReleased : true)) {
                     href(name: "pageButtonConfigHref",
                     page: "pageButtonConfig",
                     params: [btnNum: btnNum, action: key, multiPresses: value.multiPresses], title: "Button $btnNum ${deCamelCase(key)}",
                     description: getButtonConfigDescription(btnNum, key, value.multiPresses) ?: "Click/tap to configure",
                     state: getButtonConfigDescription(btnNum, key, value.multiPresses) ? "complete" : null)
                  }
               }
            }
         }
      }
      section("Advanced", hidden: true) {
         input name: "boolShowReleased", type: "bool", title: "Show actions sections for \"released\" events", submitOnChange: true
         input name: "boolShowSetForAll", type: "bool", title: "Always show \"set for all\" option even if only one blind/shade selected (may be useful if frequently change which devices the button controls)"
         input name: "boolInitOnBoot", type: "bool", title: "Initialize app on hub start (may avoid delays with first button presses after reboot)", defaultValue: true
         input name: "travelTime", type: "enum", title: "Travel time allowance (cache target level for \"adjust\" actions for this long instead of fetching from device for subsequent commands):",
            options: [[0: "Do not cache (always fetch"], [15:"15 seconds"],[30:"30 seconds [DEFAULT]"],[60:"1 minute"],[120:"2 minutes"]], defaultValue: 30
         input name: "logEnable", type: "bool", title: "Enable debug logging"
      }
      section("Modes") {
         input "modes", "mode", title: "Only when mode is", multiple: true, required: false
      }
   }
}

List<com.hubitat.app.DeviceWrapper> getAllSelectedBlindsOrShades() {
   List<com.hubitat.app.DeviceWrapper> allDevices = []
   if (settings["devices.windowBlind"]) allDevices += settings["devices.windowBlind"]
   if (settings["devices.windowShade"]) allDevices += settings["devices.windowShade"]
   if (settings["devices.switchLevel"]) allDevices += settings["devices.switchLevel"]
   return allDevices
}

Boolean isAnyBlindOrShadeSelected() {
   return (settings["devices.windowBlind"] || settings["devices.windowShade"] || settings["devices.switchLevel"])
}

Boolean isSameDeviceSelectedTwice() {
   Boolean retVal = false
   List<String> allDevices = getAllSelectedBlindsOrShades().collect { it.deviceNetworkId }
   if (allDevices.size() > allDevices.toSet().size()) {
      retVal = true
   }
   return retVal
}

Integer getNumberOfButtons() {
   Integer num = settings.buttonDevices*.currentValue('numberOfButtons').max()
   if (num) {
      return num
   } else {
      log.warn "Device did not specify number of buttons; using 1. Check or change this in the driver if needed."
      return 1
   }	
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
   if (params) {
      state.currentParams = params
   } else {
      params = state.currentParams
   }
   Integer btnNum = params.btnNum
   String action = params.action
   Boolean multiPresses = params.multiPresses
    dynamicPage(name: "pageButtonConfig", title: "Button ${btnNum} Configuration", uninstall: true, install: false) {
        if(settings.buttonDevices && isAnyBlindOrShadeSelected() && btnNum) {
            String btnActionSettingName = "btn${btnNum}.${action}.Action"
            section("Actions for button ${btnNum} ${deCamelCase(action)}") {
            input(name: btnActionSettingName, type: "enum", title: "Do...",
               options:  actionMap.collect { actMap -> ["${actMap.key}": actMap.value.displayName] },
               submitOnChange: true)
         }
            String actionSetting = settings["${btnActionSettingName}"]
            if (actionSetting) {
               switch(actionSetting) {
                  case sOPEN:
                     makeOpenSection()
                     break
                  case sCLOSE:
                     makeCloseSection()
                     break
                  case sUP:
                     makeAdjustSection(params.btnNum, params.action, sUP)
                     break
                  case sDOWN:
                     makeAdjustSection(params.btnNum, params.action, sDOWN)
                     break
                  case sTO_POSITION:
                     makeSetPoistionSection(btnNum, action, multiPresses)
                     break
                  default:
                     paragraph "Not set"
               }
            }
        }
   }
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
         String end = pressNum.toString()[-1]
         if (end == 1) ordinal += "st"
         else if (end == 2) ordinal += "nd"
         else if (end == 3) ordinal += "rd"
         else ordinal += "th"
   }
   return "$ordinal $actionDisplayName"
}

String getButtonConfigDescription(btnNum, action, multiPresses) {
   StringBuilder sbDesc = new StringBuilder()
   String actionSettingName =  "btn${btnNum}.${action}.Action"
   Integer maxPress = multiPresses ? getMaxPressNum() : 1
   if (settings[actionSettingName] == sOPEN) {
      sbDesc << "Open"
   }
   else if (settings[actionSettingName] == sCLOSE) {
      sbDesc << "Close"
   }
   else if (settings[actionSettingName] == sUP || settings[actionSettingName] == sDOWN) {
      if (settings[actionSettingName]) {
         String actionStr = actionMap[settings[actionSettingName]].displayName
         sbDesc << "${actionStr}"
         String levelChangeSettingName = "btn${btnNum}.${action}.UseStartLevelChange"
         if (settings[levelChangeSettingName]) {
            sbDesc << " until released"
         }
         else {
            String adjustBySetting = "btn${btnNum}.${action}.AdjustBy"
            sbDesc << " by ${settings[adjustBySetting]}"
         }
      }
   }
	else if (settings[actionSettingName] == sTO_POSITION) {
      for (pressNum in 1..maxPress) {
         if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
            String pressNumString = getOrdinal(action, pressNum)
            Boolean toggleSet = settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]
            if (multiPresses) sbDesc <<  "\n<span style=\"font-variant: small-caps\">$pressNumString</span>: "
            if (toggleSet) {
               sbDesc << "\n${multiPresses ? ' ' : ''}Toggle or "
            }
            if (settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
               Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"]
               sbDesc << "Set all to $toPos"
            }
            else {
               getAllSelectedBlindsOrShades().each { com.hubitat.app.DeviceWrapper dev ->
                  Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                  if (toPos != null) sbDesc << "Set ${dev.displayName} to ${toPos}\n"
               }
            }
         }
      }
   }
   else {
      logDebug("Description for button $btnNum $action unspecified", "trace")
   }
   return sbDesc.toString()
}

def makeSetPoistionSection(btnNum, strAction = "pushed", multiPresses = false) {
   logTrace("Running makeSetPoistionSection($btnNum, $strAction, $multiPresses)")
   if (params) {
      state.currentParams = params
   } else {
      params = state.currentParams
   }
   Integer maxPressNum = multiPresses ? getMaxPressNum() : 1
   for (pressNum in 1..maxPressNum) {
      if (pressNum == 1 || (pressNum > 1 && multiPresses && getDoesPressNumHaveAction(btnNum, strAction, pressNum-1))) {
         String sectionTitle = multiPresses ? "Press ${pressNum}:" : "Button ${btnNum} ${strAction}"
         section(sectionTitle, hideable: true, hidden: false) {
            if ((getAllSelectedBlindsOrShades().size() > 1) || settings["boolShowSetForAll"]) {
               input(name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll", type: "bool",
                     title: "Use same position for all blinds/shades", submitOnChange: true, defaultValue: false)
            }
            if (pressNum == 1) {
               input(name: "btn${btnNum}.${strAction}.Press${pressNum}.Toggle", type: "bool",
               title: "Toggle (close if any open, otherwise open)", submitOnChange: true, defaultValue: false)
            } else {
               app.removeSetting("btn${btnNum}.${strAction}.Press${pressNum}.Toggle")
            }
            if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
               paragraph "", width: 6
               paragraph "<strong>Position</strong>", width: 6
               paragraph "Set all:", width: 6
               input name: "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L", type: "number",
                  title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 6
            }
            else {
               paragraph "<strong>Device</strong>", width: 6
               paragraph "<strong>Position</strong>", width: 6
               for (dev in getAllSelectedBlindsOrShades()) {
                  paragraph "${dev.displayName}:", width: 6
                  input name: "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}", type: "number",
                        title: "", description: "0-100", range: "0..100", submitOnChange: false, width: 6
               }
            }
            if (pressNum < maxPressNum) {
               paragraph "", width: 5
               paragraph "", width: 4
                     input name: "btn${btnNum}.${strAction}.Press${pressNum}.SaveButton", type: "button",
                           title: "Save", width: 3, submitOnChange: true
            }
         }
      }
   }
}

def makeOpenSection() {
   section {
      paragraph("Open all selected shades/blinds.")
   }
}

def makeCloseSection() {
   section {
      paragraph("Close all selected shades/blinds.")
   }
}

/** Makes dim/adjust up/down section; direction parameter must be 'up' or 'down' */
def makeAdjustSection(Integer btnNum, String strAction = sPUSHED, String direction) {
   String rampSettingName = "btn${btnNum}.${strAction}.UseStartLevelChange"
   section() {
      if (!settings[rampSettingName]) {
         input name: "btn${btnNum}.${strAction}.AdjustBy", type: "number", range:"0..99", title: "Adjust $direction by:"
      }
      else {
         paragraph "Start adjusting $direction on ${eventMap[strAction]?.userAction}"
      }
   }
   section('<strong>Options</strong>') {
      if (buttonDevices.any { it.hasCapability("ReleasableButton") } && (strAction == sPUSHED || strAction == sHELD)) {
         String settingTitle = "Adjust until release (start position change when button ${strAction}, stop position change when button is released)"
         input(name: rampSettingName, type: 'bool', title: settingTitle, submitOnChange: true)
         List unsupportedBlinds = settings["devices.windowBlind"]?.findAll { !(it.hasCommand('startPositionChange')) }
         List unsupportedShades = settings["devices.windowShade"]?.findAll { !(it.hasCommand('startPositionChange')) }
         List unsupportedDimmers = settings["devices.switchLevel"]?.findAll { !(it.hasCommand('startLevelChange')) } 
         if (unsupportedShades || unsupportedBlinds || unsupportedDimmers) {
            def unsupportedDevices = (unsupportedShades + unsupportedBlinds + unsupportedDimmers).join(', ')
            paragraph("Warning: one or more devices do not support the \"Start Position (or Level) Change\" commands: $unsupportedDevices. " +
                        "The \"Adjust until release\" option above will probably not work.")
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

void buttonHandler(com.hubitat.hub.domain.Event evt) {
   logDebug "buttonHandler() for ${evt.device} ${evt.value} ${evt.name}"
   Integer btnNum = evt.value as Integer
   String action = evt.name
   String actionSettingName =  "btn${btnNum}.${action}.Action"
   switch (settings[actionSettingName]) {
      case sOPEN:
         Integer pressNum = getPressNum(btnNum, action)
         logDebug "Action \"${sOPEN}\" specified for button ${btnNum} ${action} press ${pressNum}"
         settings["devices.switchLevel"]?.on()
         settings["devices.windowBlind"]?.open()
         settings["devices.windowShade"]?.open()
         break
      case sCLOSE:
         logDebug "Action \"${sCLOSE}\" specified for button ${btnNum} ${action} press ${pressNum}"
         settings["devices.switchLevel"]?.off()
         settings["devices.windowBlind"]?.close()
         settings["devices.windowShade"]?.close()
         break
      case sUP:
      case sDOWN:
         logDebug "Action \"${sUP}\" specified for button ${btnNum} ${action} press ${pressNum}"
         String levelChangeSettingName = "btn${btnNum}.${action}.UseStartLevelChange"
         if (lastLevel[app.id] == null) lastLevel[app.id] = [:]
         if (settings[levelChangeSettingName]) {
            String levelDirection = 
            settings["devices.switchLevel"]?.startLevelChange(settings[actionSettingName])
            settings["devices.windowBlind"]?.startPositionChange(settings[actionSettingName] == sUP ? sOPEN : sCLOSE)
            settings["devices.windowShade"]?.startPositionChange(settings[actionSettingName] == sUP ? sOPEN : sCLOSE)
         }
         else {
            String adjustBySetting = "btn${btnNum}.${action}.AdjustBy"
            Integer adjustBy
            if (settings[actionSettingName] == sUP) {
               adjustBy = settings[adjustBySetting]
            }
            else {
               adjustBy = 0 - settings[adjustBySetting]
            }
            // TODO: Remember recently set levels and use instead of always fetching from devices:
            settings["devices.switchLevel"]?.each { com.hubitat.app.DeviceWrapper dev ->
               Integer newLvl
               if (lastLevel[app.id][dev.id]) newLvl = lastLevel[app.id][dev.id] + adjustBy
               else newLvl = dev.currentValue("level") + adjustBy
               if (settings[actionSettingName] == sUP && newLvl > 100) newLvl = 100
               else if (newLvl < 0) newLvl = 0
               dev.setLevel(newLvl)
               lastLevel[app.id][dev.id] = newLvl
               runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
            }
            settings["devices.windowBlind"]?.each { com.hubitat.app.DeviceWrapper dev ->
               Integer newLvl
               if (lastLevel[app.id][dev.id]) newLvl = lastLevel[app.id][dev.id] + adjustBy
               else newLvl = dev.currentValue("level") + adjustBy
               if (settings[actionSettingName] == sUP && newLvl > 100) newLvl = 100
               else if (newLvl < 0) newLvl = 0
               dev.setPosition(newLvl)
               lastLevel[app.id][dev.id] = newLvl
               runIn(settings.travelTime != null ? settings.travelTime  as Long: 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
            }
            settings["devices.windowShade"]?.each { com.hubitat.app.DeviceWrapper dev ->
               Integer newLvl
               if (lastLevel[app.id][dev.id]) newLvl = lastLevel[app.id][dev.id] + adjustBy
               else newLvl = dev.currentValue("level") + adjustBy
               if (settings[actionSettingName] == sUP && newLvl > 100) newLvl = 100
               else if (newLvl < 0) newLvl = 0
               dev.setPosition(newLvl)
               lastLevel[app.id][dev.id] = newLvl
               runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
            }
         }
         break
      case sTO_POSITION:
         Integer pressNum = getPressNum(btnNum, action)
         if (lastLevel[app.id] == null) lastLevel[app.id] = [:]
         if (getDoesPressNumHaveAction(btnNum, action, pressNum)) {
            String pressNumString = getOrdinal(action, pressNum)
            Boolean toggleSet = settings["btn${btnNum}.${action}.Press${pressNum}.Toggle"]
            Boolean didToggle = false
            if (toggleSet) {
               if (getAnySelectedBlindsOrShadesOpen()) {
                  settings["devices.switchLevel"]?.off()
                  settings["devices.windowBlind"]?.close()
                  settings["devices.windowShade"]?.close()
                  didToggle = true
               }
            }
            if (!didToggle && settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll"]) {
                  Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.SetForAll.L"]
                  settings["devices.switchLevel"]?.setLevel(toPos)
                  settings["devices.windowBlind"]?.setPosition(toPos)
                  settings["devices.windowShade"]?.setPosition(toPos)
                  getAllSelectedBlindsOrShades().each { com.hubitat.app.DeviceWrapper dev ->
                     lastLevel[app.id][dev.id] = toPos
                     runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
                  }
            }
            else if (!didToggle) {
               settings["devices.switchLevel"]?.each { com.hubitat.app.DeviceWrapper dev ->
                  Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                  if (toPos != null) dev.setLevel(toPos)
                  lastLevel[app.id][dev.id] = toPos
                  runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
               }
               settings["devices.windowBlind"]?.each { com.hubitat.app.DeviceWrapper dev ->
                  Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                  if (toPos != null) dev.setPosition(toPos)
                  lastLevel[app.id][dev.id] = toPos
                  runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
               }
               settings["devices.windowShade"]?.each { com.hubitat.app.DeviceWrapper dev ->
                  Integer toPos = settings["btn${btnNum}.${action}.Press${pressNum}.L.${dev.id}"]
                  if (toPos != null) dev.setPosition(toPos)
                  lastLevel[app.id][dev.id] = toPos
                  runIn(settings.travelTime != null ? settings.travelTime as Long : 30, "uncacheLevel", [data: [deviceId: dev.id], overwrite: false])
               }
            }
         }
      default:
         logDebug "No action specified"
   }
}

void positionHandler(com.hubitat.hub.domain.Event evt) {
   logDebug "positionHandler() [${evt.name}, ${evt.value}]"
   uncacheLevel([deviceId: evt.device.id])
}

void uncacheLevel(Map data) {
   if (pressNum[app.id] != null) {
      pressNum[app.id].remove(data.deviceId as Long)
   }
}

/** Returns true if any selected blinds/shades/level devices are open, otherwise false
  */
Boolean getAnySelectedBlindsOrShadesOpen() {
   logDebug "Running toggle()..."
   Boolean anyOpen = false
   if (settings["devices.windowBlind"]?.any { it.currentValue("windowShade") == "open" }) anyOpen = true
   if (settings["devices.windowShade"]?.any { it.currentValue("windowShade") == "open" }) anyOpen = true
   if (settings["devices.switchLevel"]?.any { it.currentValue("switch") == "on" }) anyOpen = true
   return anyOpen
}

/** To emulate Hue Dimmer, this app tracks 1-5 button presses
  * for one or more buttons on the button device. This retrieves
  * the current press number for the provided button number.
  */
Byte getPressNum(buttonNum, action = "pushed") {
   logTrace("getPressNum($buttonNum, $action)")
   if (eventMap[action].multiPresses) {
      String theAction = settings["btn${buttonNum}.${action}.Action"]
      Boolean canMulti = actionMap[theAction]?.multiPresses
      if (theAction && canMulti) {
         Byte pressNo = pressNum[app.id]["pressNum${buttonNum}.${action}" as String]
         if (!pressNo) {
            pressNo = 1 
            pressNum[app.id]["pressNum${buttonNum}.${action}" as String] = pressNo
         }
         return pressNo
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
void incrementPressNum(Number buttonNum, strAction = "pushed") {
   Byte currPress = getPressNum(buttonNum, strAction)
   Byte nextPress = 2
   if (currPress) {
      nextPress = currPress + 1
      if (nextPress > getMaxPressNum() || !getDoesPressNumHaveAction(buttonNum, strAction, nextPress)) {
         resetPressNum([btnNum: buttonNum, actions: [strAction]])
      }
      else {
         if (pressNum[app.id] == null) pressNum[app.id] = [:]
         pressNum[app.id]["pressNum${buttonNum}.$strAction"] = nextPress
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
   if (btnNum != null && (pressNum[app.id] != null)) {
      actions.each {
         pressNum[app.id].remove("pressNum${btnNum}.${it}" as String)
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
      if (actionName == sOPEN) {
         hasAction = true
      }
      else if (actionName == sCLOSE) {
         hasAction = true
      }
      else if (actionName == sUP) {
         hasAction = true
      }
      else if (actionName == sDOWN) {
         hasAction = true
      }
      else if (actionName == sTO_POSITION) {
         if (settings["btn${btnNum}.${strAction}.Press${pressNum}.SetForAll"]) {
            def settingL = "btn${btnNum}.${strAction}.Press${pressNum}.SetForAll.L"
            if (settings["$settingL"] != null) {
               hasAction = true
            }
         }
         else {
            for (dev in getAllSelectedBlindsOrShades()) {
               def settingL = "btn${btnNum}.${strAction}.Press${pressNum}.L.${dev.id}"
               if (settings["$settingL"] != null) {
                  hasAction = true
               }
            }
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
   subscribe(buttonDevices, "pushed", buttonHandler)
   subscribe(buttonDevices, "held", buttonHandler)
   subscribe(buttonDevices, "released", buttonHandler)
   subscribe(buttonDevices, "doubleTapped", buttonHandler)
   if (settings.travelTime != 0) {
      subscribe(settings."devices.windowBlind", "position", "positionHandler")
      subscribe(settings."devices.windowShade", "position", "positionHandler")
      subscribe(settings."devices.switchLevel", "level", "positionHandler")
   }
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
   if (settings["debugLogging"] && level=="debug") {
      log.debug striung
   }
   else if (settings["debugLogging"]) {
      log."$level"(string)
   }
}

/** Writes to log.trace; use for development/testing */
void logTrace(string) {
   if (settings["traceLogging"]) {
      log.trace(string)
   }
}
