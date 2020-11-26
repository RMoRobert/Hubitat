/**
 * ==========================  Lights on Motion Plus (Child App) ==========================
 *  TO INSTALL:
 *  Add code for parent app first and then and child app (this). To use, install/create new
 *  instance of parent app.
 *
 *  Copyright 2018-2020 Robert Morris
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  Last modified: 2020-11-25
 *
 *  Changelog:
 * 
 * 5.1.0 - Light states restored even if non-dimming "inactive" action configured (in case other modes are different); fixed bug with scenes not being activated if "off only" inactive actino configured
 * 5.0.5 - Additional fix for lights not turning on in some situations
 * 5.0.4 - Fix for lights not turning on in some situations (5.0.2 bug)
 * 5.0.3 - Fix for ooccasional issue where LoMP gets stuck thinking lights are always dimmmed and "restores" with any motion
 * 5.0.2 - Fix "don't perform 'on' action..." setting being ignored in most cases
 * 5.0.1 - Per-mode level exception fix
 * 5.0   - Total rewrite with per-mode options, optional dim-only (no off) settings, button device support, etc.
 *         Do *not* overwrite 4.x app with this app; install as new app and update parent, which will allow continued use of both 4.x and 5.x child apps
 * 4.2c  - Fixed issue if "dim to level" is not set or defaults to 0 instead of 10; will no longer send setLevel(0) to dim
 * 4.2b  - Fixed issue where 1-minute "off" timer dimmed at 0s instead of 30s
 * 4.2   - Added ability to activate Hue Bridge scene (via CoCoHue) for night mode instead of settings
 * 4.1a: - Improved logic for "keep on" sensors (4.1a contains small bugfix for lux levels/sensors)
 * 4.0:  - Added "night mode" lighting option (turns on to specified level/settings if run in "night" mode[s]; restores "normal"/previous settings when next run in non-night mode in mode that will turn lights on)
 * 3.1:  - Added "kill switch" option (completely disables app regardless of any other options selected in app)
 *       - Changed boolean in-app "disable app" option to "soft kill switch" option (if switch on, app will not turn lights on; turn-off behavior determined by other app options)
 *       - Added option for additional sensors to keep (but not turn) lights on;
 *       - Fixed bug with multiple "turn on" sensors
 * 3.0: Moved to parent/child app model; bug fixes/improvements for when motion detected/lights on after mode changed
 *
 */

import groovy.transform.Field

@Field static final List<Map<String,String>> activeActions = [
   [on: 'Turn on lights'],
   [onColor: 'Turn on lights and set color/CT or level'],
   [onScene: 'Activate CoCoHue scene'],
   [no: 'No action (do not turn on)']
]

@Field static final List<Map<String,String>> inactiveActions = [
   [dimOff: 'Dim lights to "warn," then turn off'],
   [offOnly: 'Turn off lights'],
   [dimOnly: 'Dim lights only (do not turn off)'],
   [no: 'No action (do not turn off)']
]

definition(
   name: "Lights on Motion Plus (Child App) 5",
   namespace: "RMoRobert",      
   parent: "RMoRobert:Lights on Motion Plus",
   author: "Robert Morris",
   description: "Do not install directly. Install Lights on Motion Plus app, then create new automations using that app.",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

preferences {
   page name: "pageMain", content: "pageMain"
   page name: "pagePerModeSettings", content: "pagePerModeSettings"
   page name: "pageDeletePerMode", content: "pageDeletePerMode"
}

def pageMain() {
   state.remove('perModeSettingsRemoved')
   state.remove('perModePageModeName')
   state.remove('perModePageModeID')
   dynamicPage(name: "pageMain", title: "Lights on Motion Plus", install: true, uninstall: true) {
      section() {
         label title: "Name this Lights on Motion Plus app:", required: true
      }
      section("Lights and Sensors${perMode ? ' (for non-exception modes)' : ''}") {
         input name: "onSensors", type: "capability.motionSensor", title: "Turn on lights when motion detected on", multiple: true, required: true
         input name: "keepOnSensors", type: "capability.motionSensor", title: "Select additional sensors to keep lights on (optional)", multiple: true
         input name: "lights", type: "capability.switch", title: "Choose lights to turn on/off/dim", multiple: true, required: (activeAction != "no" && activeAction == "onScene" && sceneGroup == null), submitOnChange: true
         input name: "activeAction", type: "enum", title: "When motion is detected...", options: activeActions, defaultVaule: "on", required: true, submitOnChange: true
         if (activeAction == "onScene") {
            input name: "scene", type: "device.CoCoHueScene", title: "Activate CoCoHue scene", required: true, submitOnChange: true
         }
         else if (activeAction == "onColor") {                  
            input name: "onColor.L", type: "number",
               title: "level", description: "0-100", range: "0..100", width: 2, required: false
            input name: "onColor.CT", type: "number",
               title: "CT", description: "~2000-7000", range: "1000..8000", width: 3, required: false
            input name: "onColor.H", type: "number",
               title: "hue", range: "0..360", description: "0-100", width: 2, required: false
            input name: "onColor.S", type: "number",
               title: "saturation", range: "0..100", description: "0-100", width: 2, required: false
            paragraph "(at least one field required)"
         }
         if (activeAction) {
            input name: "notIfOn", type: "bool", title: "Don't perform \"on\" action if any specified lights are already on",
               defaultValue: true
         }
         input name: "inactiveAction", type: "enum", title: "When motion becomes inactive...", options: inactiveActions, defaultValue: "dimOff",
            required: true, submitOnChange: true
         if (settings.any { it.key.startsWith("inactiveAction") && it.value != "no" }) {
            input name: "inactiveMinutes", type: "number", title: "Minutes to wait after motion becomes inactive before dimming or turning off (if configured)", required: true,
               description: "number of minutes"
            input name: "dimToLevel", type: "number", options: 1..99, title: "Dim to level (if configured to dim)",  width: 6, defaultValue: 10
            input name: "boolRemember", type: "bool", title: "Capture light states before dimming/turning off; restore with \"Turn on lights\" " +
               "(note: only saved within Lights on Motion Plus)", width: 6, defaultValue: true
            input name: "dimTime", type: "number", title: "Dim for this many seconds before turning off (if configured to turn dim and then turn off)",
               range: "5..86400", defaultValue: 30, required: true
         }
         input name: "offLights", type: "capability.switch", title: "Choose additional lights to turn off or dim (optional)", multiple: true
      }
      section("Modes") {
         if (!perMode) input name: "perMode", type: "bool", title: "Configure exceptions per mode", submitOnChange: true 
         if (perMode) {
            location.getModes().each { mode ->
               href(name: "pagePerModeSettings${mode.id}",
               page: "pagePerModeSettings",
               title: "${mode.name} mode",
               params: [modeName: mode.name, modeID: mode.id],
               description: getPerModeDescription(mode.id) ?: "Click/tap to configure ${mode.name} mode exception...",
               state: getPerModeDescription(mode.id) ? "complete" : null)
            }
            input name: "changeWithMode", type: "bool", title: "If lights are on when mode changes and \"${activeActions.findResult {it.on}}\", \"${activeActions.findResult {it.onColor}}\", or \"${activeActions.findResult {it.onScene}}\" is configured, change lights to those settings on mode change", submitOnChange: true
            /*if (changeWithMode) {
               paragraph "Note: Light states will be saved (to per-mode or general cache as configured) if configured to save before changing to new states"
               input name: "changeWithModeTransitionTime", type: "number", title: "Transition time (in seconds) to new mode settings (optional)", options: 1..60, required: false
            }*/
            href(name: "pageDeletePerModeHref",
             page: "pageDeletePerMode",
             title: "Remove all per-mode settings",
             description: "Warning: this will remove all selected options (including devices and other settings) for per-mode exceptions")
         }
      } 
      /*section("Buttons") {
         paragraph "Coming soon: use button devices to perform actions for active/inactive (besides sensor)"
      }*/
      section("Restrictions") {
         input name: "onKillSwitch", type: "capability.switch", title: "Do not turn lights on if this switch is on", multiple: true
         input name: "offKillSwitch", type: "capability.switch", title: "Do not turn lights off (or dim) if this switch is on", multiple: true
         input name: "timeRestrict", type: "bool", title: "Use time restrictions", submitOnChange: true
         if (timeRestrict) {
            paragraph "Turn lights on only if between start time and end time"
            input name: "startTimeType", type: "enum", title: "Starting at", options: [[time: "Specific time"], [sunrise: "Sunrise"], [sunset: "Sunset" ]],
               defaultValue: 'time', width: 6, submitOnChange: true, required: true
            if (startTimeType == 'time') {               
               input name: "startTime", type: "time", title: "Start time", width: 6, required: true
            }
            else if (startTimeType) {
               input name: "startTimeOffset", type: "number", range: "-720..720", title: "Offset (minutes)", width: 6
            }
            else {
               paragraph "", width: 6
            }
            input name: "endTimeType", type: "enum", title: "Ending at", options: [[time: "Specific time"], [sunrise: "Sunrise"], [sunset: "Sunset" ]],
               defaultValue: 'time', width: 6, submitOnChange: true,  required: true
            if (endTimeType == 'time') {               
               input name: "endTime", type: "time", title: "End time", width: 6, required: true
            }
            else if (startTimeType) {
               input name: "endTimeOffset", type: "number", range: "-720..720", title: "Offset (minutes)", width: 6
            }
            else {
               paragraph "", width: 6
            }
            input name: "timeBehavior", type: "enum", title: "If outside time range", options: [[no: "Do nothing (do not turn on or off/dim)"], [noOn: "Do not turn on, but dim/turn off if configured"]],
               defaultValue: 'no', required: true
         }
         input name: "luxRestrict", type: "bool", title: "Use lux restrictions", submitOnChange: true
         if (luxRestrict) {
            // TODO: Multiple with option to average, min, or max?
            input name: "luxSensor", type: "capability.illuminanceMeasurement", title: "Select lux (illuminance) sensor", multiple: false
            input name: "luxNumber", type: "number", title: "Illuminance threshold"
            input name: "luxBehavior", type: "enum", title: "If lux above this range, then...", required: true,
               options: [[no: "Do nothing (do not turn on or off/dim)"], [noOn: "Do not turn on, but dim/turn off if configured"]],
               defaultValue: 'no'
         }
      }

      section("Advanced Settings") {
         input name: "logLevel", type: "enum", title: "Debug logging level",
            options: [[0: 'Disabled'], [1: 'Moderate logging'], [2: 'Verbose logging']],
            defaultValue: 0
         input name: "noRestoreScene", type: "bool", title: 'Re-activate "Turn on and set scene" or "Turn on and set color..." settings instead of restoring saved state when motion detected during dim'
         input name: "doOn", type: "bool", title: "Send \"On\" command after \"Set Level\" or \"Set Color\" when restoring states (enable if devices use prestaging)"
         input name: "btnClearCaptured", type: "button", title: "Clear all captured states"
      }
   }
}

def pagePerModeSettings(Map params) {
   if (params) {
      state.perModePageModeName = params.modeName
      state.perModePageModeID = params.modeID
   }
   Long modeID = modeID ?: state.perModePageModeID
   String modeName = modeName ?: state.perModePageModeName
   dynamicPage(name: "pagePerModeSettings", title: "${modeName} Mode Settings", uninstall: false, install: false, nextPage: "pageMain") {
      section("Exception") {
         input name: "perMode.${modeID}", type: "bool", title: "Configure exception for <strong>${modeName}</strong> mode? (if not configured, default/non-per-mode settings will be used)", submitOnChange: true
      }
      section("Lights and Sensors") {
         if (settings["perMode.${modeID}"]) {
            input name: "lights.override.${modeID}", type: "bool", title: "Override default light selection?", submitOnChange: true
            if (settings["lights.override.${modeID}"]) {
               input name: "lights.${modeID}", type: "capability.switch", title: "Choose lights to turn on/off/dim", multiple: true
            }
            input name: "activeAction.${modeID}", type: "enum", title: "When motion is detected...", options: activeActions, required: true, submitOnChange: true              
            if (settings["activeAction.${modeID}"] == "onScene") {
               input name: "scene.${modeID}", type: "device.CoCoHueScene", title: "Activate CoCoHue scene", required: true, submitOnChange: true
            }
            else {
               if (settings["activeAction.${modeID}"] == "onColor") {
                  if (settings["activeAction.${modeID}"] == "onColor") {                  
                     input name: "onColor.L.${modeID}", type: "number",
                        title: "level", description: "0-100", range: "0..100", width: 2, required: false
                     input name: "onColor.CT.${modeID}", type: "number",
                        title: "CT", description: "~2000-7000", range: "1000..8000", width: 3, required: false
                     input name: "onColor.H.${modeID}", type: "number",
                        title: "hue", range: "0..360", description: "0-100", width: 2, required: false
                     input name: "onColor.S.${modeID}", type: "number",
                        title: "saturation", range: "0..100", description: "0-100", width: 2, required: false
                     paragraph "(at least one field required)"
                  }
               }
            }
            input name: "inactiveAction.${modeID}", type: "enum", title: "When motion becomes inactive...", options: inactiveActions, defaultValue: 'dimOff',
               required: true, submitOnChange: true
            if (settings["inactiveAction.${modeID}"]?.contains('dim')) {
               input name: "dimToLevel.override.${modeID}", type: "bool", title: "Dim to different level than default above?", submitOnChange: true, width: 6
               if (settings["dimToLevel.override.${modeID}"]) { 
                  input name: "dimToLevel.${modeID}", type: "number", options: 1..99, title: "Dim to level", width: 6, defaultValue: 10
               }
               input name: "boolRemember.${modeID}", type: "bool", title: "If light states captured (before dim/off), save to ${modeName} mode-specific cache (turn off to save to non-exception cache)", defaultValue: true
            }
            if (settings["inactiveAction.${modeID}"] != ('no')) {
               input name: "offLights.${modeID}", type: "capability.switch", title: "Choose additional lights to dim or turn off (optional; will override non-exception \"additional off\" light(s) if selected)", multiple: true
            }
         }      
      }
   }
}

// Returns String summary of per-mode settings, or empty string if that mode is not configured
String getPerModeDescription(Long modeID) {
   String desc = ""
   if (settings["perMode"] && settings["perMode.${modeID}"]) {
      if (settings["lights.override.${modeID}"]) {
         String devList = ""
         settings["lights.${modeID}"].eachWithIndex { dev, idx ->
            devList += dev.displayName
            if (idx < settings["lights.${modeID}"].size() - 1) devList += ", "
         }
         desc += "Lights: ${devList}\n"
      }
      String settingName = "activeAction.${modeID}"
      desc += """When active: ${activeActions.findResult {it."${settings[settingName]}"}}\n"""
      settingName = "inactiveAction.${modeID}"
      desc += """When inactive: ${inactiveActions.findResult {it."${settings[settingName]}"}}\n"""
      desc += "\n(Click/tap to see more details)"
   }
   return desc

}

def pageDeletePerMode() {
   dynamicPage(name: "pageDeletePerMode", title: "Remove per-mode settings?", uninstall: false, install: false, nextPage: "pageMain") {
      section() {
         if (!(state.perModeSettingsRemoved)) {
            paragraph "Press the button below to confirm the removal of all per-mode settings (devices and all other options). Press " +
               "\"Next\" to continue without removing these settings."
            input name: "btnRemovePerMode", type: "button", title: "Confirm removal of per-mode settings"
         }
         else {
            paragraph("Per-mode settings removed. Press \"Next\" to continue.")
         }
      }
   }
}

def motionHandler(evt) {
   logDebug "motionHandler: ${evt.device} ${evt.value} (mode ${location.currentMode.name} [${location.currentMode.id}]) ===", 1, "trace"
   // Before we start, set isDimmed to false if no lights on (which could have happened if user or other app
   // turned lights off before app did)
   if (verifyNoneOn()) {
      state.isDimmed = false
   }
   // Now, handle motion:
   if (evt.value == "active") {
      unschedule("scheduledDimHandler")
      unschedule("scheduledOffHandler")
      // If it's a "turn on sensor" or lights were dimmed (from inactivity)...
      if (onSensors.any { it.deviceId == evt.deviceId} || state.isDimmed) {
         // If no lights on or configured to not care or currenly dimmed...
         if ((settings["notIfOn"] == false) || state.isDimmed || verifyNoneOn()) {
            // If dimmed or all restrictions OK, then perform active action
            if (state.isDimmed ||
                (isTimeOK() && isLuxOK()) &&
                (settings["onKillSwitch"] == null || onKillSwitch.every {it.currentValue("switch") != "on"})) {
               // TODO: Change motionHandler or performActiveAciton to avoid unnecessary
               // actions (e.g., restoring lights if not really needed bc already on and not
               // dimmed, etc.) while stil respecting above settings
               performActiveAction()
            }
         }
      }
   }
   else { // evt.value == inactive
      if (!(onSensors.any {it.currentValue("motion") == "active"} || keepOnSensors?.any {it.currentValue("motion") == "active" })) {
         if (!verifyNoneOn(true)) {
            logDebug "Motion inactive and at least one light on", 2, "debug"
            if ((settings["luxBehavior"] == "noOn" || isLuxOK()) &&
                isTimeOK() &&
                (settings["offKillSwitch"] == null || offKillSwitch.every { it.currentValue("switch") != "on"}))
            {
               performInactiveAction()
            }
         }
         else {
            logDebug "  All sensors inactive, but ignoring because no lights on", 2, "debug"
         }
      }
      else {
         logDebug "  No action performed because one or more sensors still active", 1, "debug"
      }
   }
}

// Returns true if all lights are off, false if any (as configured) on
// Lights to check are determined based on user settings and, if applicable, current mode
// includeExtraOffLights: include "additional lights to turn off"? (will choose per-mode or default set, depending on configuration)
Boolean verifyNoneOn(Boolean includeExtraOffLights=false) {
   logDebug "verifyNoneOn($includeExtraOffLights)...", 2, "trace"
   List<com.hubitat.app.DeviceWrapper> devsToCheck = []
   if (settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"] && settings["lights.override.${location.getCurrentMode().id}"]) {
      devsToCheck = (settings["lights.${location.getCurrentMode().id}"]) 
   }
   else {
      devsToCheck = settings["lights"]
   }
   if (includeExtraOffLights) {
      if (settings["offLights.${location.getCurrentMode().id}"] != null) {
         devsToCheck += settings["offLights.${location.getCurrentMode().id}"]
      }
      else if (settings["offLights"] != null) {
         devsToCheck += settings["offLights"]
      }
   }
   return !(devsToCheck.any { it.currentValue("switch") == "on"})
}

List<com.hubitat.app.DeviceWrapper> getDevicesToTurnOn() {
   logDebug "getDevicesToTurnOn()...", 2, "trace"
   List<com.hubitat.app.DeviceWrapper> devsToOn = []
   if (settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"]) {
      if (settings["activeAction.${location.getCurrentMode().id}"] == "onScene") {
         devsToOn = [settings["scene.${location.getCurrentMode().id}"]]
      }
      else {
         devsToOn = (settings["lights.override.${location.getCurrentMode().id}"]) ?
                     (settings["lights.${location.getCurrentMode().id}"]) : (settings["lights"])
      }
   }
   else {
      if (settings["activeAction"] == "onScene") {
         devsToOn = [settings["scene"]]
      }
      else {
         devsToOn = settings["lights"]
      }
   }
   return devsToOn
}

// Gets devices to dim or turn off; also can be used to get lights to restore
// includeExtraOffLights: include "additional lights to turn off"? (will choose per-mode or default set, depending on configuration)
List<com.hubitat.app.DeviceWrapper> getDevicesToTurnOff(Boolean includeExtraOffLights=true) {
   logDebug "getDevicesToTurnOff(includeExtraOffLights=$includeExtraOffLights)", 2, "trace"
   List<com.hubitat.app.DeviceWrapper> devsToOff = []
   if (settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"] && settings["lights.override.${location.getCurrentMode().id}"]) {
      devsToOff = settings["lights.${location.getCurrentMode().id}"]
   }
   else {
      devsToOff = settings["lights"]
   }
   if (includeExtraOffLights) {
      if (settings["offLights.${location.getCurrentMode().id}"] != null) {
         devsToOff += settings["offLights.${location.getCurrentMode().id}"]
      }
      else if (settings["offLights"] != null) {
         devsToOff += settings["offLights"]
      }
   }
   return devsToOff
}

// Returns applicable mode setting "suffix", e.g., ".2" (with mode ID) or "" (if not configured per mode).
// Recommended use: in GString when retriving setting value, e.g., settings["onColor.CT.${returnValue}"]
String getSettingModeSuffix() {
   String suffix = ""
   if (settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"]) {
      suffix = ".${location.getCurrentMode().id}"
   }
   return suffix
}

// Performs specified action for "active"/on-type action (will check per-mode exceptions but not restrictions)
void performActiveAction() {
   logDebug "performActiveAction", 2, "trace"
   String suffix = getSettingModeSuffix()
   Boolean anyOn = !verifyNoneOn()
   switch (settings["activeAction${suffix}"]) {
      case "on":
         logDebug '  action is "on"', 2, "debug"
         if (!anyOn || state.isDimmed || settings["notIfOn"] == false) {
            logDebug  "    -> none on, is dimmed, or configured to always turn on, so restoring... (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
            restoreStates()
         }
         else {
            logDebug "    -> not performing any action (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
         }
         state.isDimmed = false
         break
      case "onColor":
         logDebug '  action is "onColor"', 2, "debug"
         Boolean doOnAction = true
         if (!anyOn || state.isDimmed || settings["notIfOn"] == false) {
            if (state.isDimmed && settings["noRestoreScene"] != true) {
               logDebug  "    -> none on, is dimmed, or configured to always turn on, so restoring... (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
               doOnAction = false
               restoreStates()
            }
            else {
               logDebug  "    -> none on, is dimmed, or configured to always turn on, so (re)activating settings... (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
            }
         }
         else {
            logDebug "    -> lights on, not configured to change if on, and not dimmed, so doing nothing (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
         }
         if (doOnAction) {
            logDebug "        -> now performing setting activations...", 2, "debug"
            if (settings["onColor.CT${suffix}"]) {
               logDebug '  action is "ct"', 2, "debug"
               getDevicesToTurnOn().each { it.setColorTemperature(settings["onColor.CT${suffix}"]) }
               if (settings["onColor.L${suffix}"]) getDevicesToTurnOn().each { it.setLevel(settings["onColor.L${suffix}"]) }
            }
            else {
               if (settings["onColor.H${suffix}"] != null &&
                  settings["onColor.S${suffix}"] != null &&
                  settings["onColor.L${suffix}"])
               {
                  getDevicesToTurnOn().each { it.setColor([hue: settings["onColor.H${suffix}"],
                     saturation: settings["onColor.S${suffix}"], level: settings["onColor.L${suffix}"]]) }
               }
               else {
                  if (settings["onColor.H${suffix}"] != null) getDevicesToTurnOn().each { it.setHue(settings["onColor.H${suffix}"]) }
                  if (settings["onColor.S${suffix}"] != null) getDevicesToTurnOn().each { it.setSaturation(settings["onColor.S${suffix}"]) }
                  if (settings["onColor.L${suffix}"] != null) getDevicesToTurnOn().each { it.setLevel(settings["onColor.L${suffix}"]) }
               }
            }
         }
         state.isDimmed = false
         break
      case "onScene":
         logDebug '  action is "onScene"', 2, "debug"
         Boolean doOnAction = true
         if (!anyOn || state.isDimmed || settings["notIfOn"] == false) {
            if (state.isDimmed && settings["noRestoreScene"] != true) {
               logDebug  "    -> none on, is dimmed, or configured to always turn on, so restoring...", 2, "debug"
               doOnAction = false
               restoreStates()
            }
            else {
               logDebug  "    -> none on, is dimmed, or configured to always turn on, so activating scene...", 2, "debug"
            }
         }
         else {
            logDebug "    -> lights on, not configured to change if on, and not dimmed, so doing nothing (anyOn = $anyOn; dimmed = ${state.isDimmed}; notIfOn = $notIfOn)", 2, "debug"
         }
         if (doOnAction) {
            logDebug "        -> now performing scene activation...", 2, "debug"            
            getDevicesToTurnOn().each { it.on() }
         }
         state.isDimmed = false
         break
      case "no":
         logDebug "  action is 'no'", 2, "debug"
         if (state.isDimmed) {
            restoreStates()
            state.isDimmed = false
            logDebug "Restored light states even though no action was configured because lights were dimmed", 1, "debug"
         }
         else {
            logDebug "Not turning lights on because no action configured", 2, "debug"
         }
         break
   }
}

// Performs specified action for "inactive"/dim/off-type action (will check per-mode exceptions but not restrictions)
void performInactiveAction() {
   logDebug "performInactiveAction", 2, "trace"
   String suffix = getSettingModeSuffix()
   switch (settings["inactiveAction${suffix}"]) {
      case "dimOff":
         logDebug "  Dim then off configured; scheduling dim for ${(settings['inactiveMinutes'] ?: 0) * 60}s and off after dim interval", 2, "debug"
         runIn((settings["inactiveMinutes"] ?: 0) * 60, scheduledDimHandler)
         runIn((settings["inactiveMinutes"] ?: 0) * 60 +
               (settings["dimTime"] ?: 30), scheduledOffHandler)
         break
      case "offOnly":
         logDebug "  Off configured; scheduling off for ${(settings['inactiveMinutes'] ?: 0) * 60}s", 2, "debug"
         runIn((settings["inactiveMinutes"] ?: 0) * 60, scheduledOffHandler)
         break
      case "dimOnly":
         logDebug "  Dim only configured; scheduling dim for ${(settings['inactiveMinutes'] ?: 0) * 2}s", 2, "debug"
         runIn((settings["inactiveMinutes"] ?: 0) * 60, scheduledDimHandler)
         break
      case "no":
         break
   }
}

// Returns true if configured lux restrictions are OK (or not set), otherwise false
Boolean isLuxOK() {
   Boolean isOK = true
   if (settings["luxRestrict"] && settings["luxSensor"] != null) {
      isOK = luxSensor.currentValue("illuminance") <= (settings["luxNumber"] ?: 0)
   }
   logDebug "isOK = $isOK", 2, "trace"
   return isOK
}

// Returns true if configured time restrictions are currently OK (or not set), otherwise false
Boolean isTimeOK() {
   logDebug "isTimeOK()", 2, "trace"
   Boolean timeOK = true
   if (settings["timeRestrict"]) {
      Date currTimeD, startTimeD, endTimeD
      currTimeD = new Date()
      // Determine start time:
      if (settings["startTimeType"] == "sunrise") startTimeD = getSunriseAndSunset(sunriseOffset: settings["startTimeOffset"] ?: 0).sunrise
      else if (settings["startTimeType"] == "sunset") startTimeD = getSunriseAndSunset(sunsetOffset: settings["startTimeOffset"] ?: 0, sunriseOffset: 0).sunset
      else startTimeD = timeToday(settings["startTime"], location.timeZone)
      // Determine end time:
      if (settings["endTimeType"] == "sunrise") endTimeD = getSunriseAndSunset(sunriseOffset: settings["endTimeOffset"] ?: 0).sunrise
      else if (settings["endTimeType"] == "sunset") endTimeD = getSunriseAndSunset(sunsetOffset:  settings["endTimeOffset"] ?: 0).sunset
      else endTimeD = timeToday(settings["endTime"], location.timeZone)
      // Calculate result
      if (startTimeD > endTimeD) {
         // If start to end time spans midnight, then check if current time is between either start time and next midnight or current/last
         // midnight and end time (would use timeTodayAfter but seems equally awkward to coerce sunrinse/sunset time, if using, into string...)
         timeOK = timeOfDayIsBetween(startTimeD, timeToday("00:00", location.timeZone)+1, currTimeD, location.timeZone) ||
                  timeOfDayIsBetween(timeToday("00:00", location.timeZone), endTimeD, currTimeD, location.timeZone)
      }
      else {
         // Start time does not span midnight, so "regular" comparison
         timeOK = startTimeD <= currTimeD && endTimeD >= currTimeD
      }
      // Maybe help GC?
      currTimeD = null
      startTimeD = null
      endTimeD = null
   }
   logDebug "timeOK = $timeOK", 2, "debug"
   return timeOK
}

def scheduledOffHandler() {
   logDebug "scheduledOffHandler", 2, "trace"
   if (!state.isDimmed) captureStates()
   getDevicesToTurnOff(true).each { it.off() }
   state.isDimmed = false
   logDebug "Turned off all lights", 1, "debug"
}

def scheduledDimHandler() {
   state.isDimmed = true
   logDebug "scheduledDimHandler", 2, "trace"
   captureStates()
   Integer dimToLevel = settings["dimToLevel"] ?: 10
   if (settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"] && settings["dimToLevel.override.${location.getCurrentMode().id}"]) {
      dimToLevel = settings["dimToLevel.${location.getCurrentMode().id}"]
   }
   settings["perMode"] && settings["perMode.${location.getCurrentMode().id}"]
   getDevicesToTurnOff(true).each {
      if (it.currentValue("switch") == 'on') {
         if (it.hasCommand('setLevel')) it.setLevel(dimToLevel)
      }
      else {
         logDebug "Not dimming ${it.displayName} because not on", 2, "debug"
      }
   }
   logDebug "Dimmed all applicable lights", 1, "debug"
}

def modeChangeHandler(evt) {
   if (settings["changeWithMode"]) {
      logDebug "modeChangeHandler: configured to handle", 2, "trace"
      if (state.lastMode != null) {
         logDebug "  Capturing states for last mode (ID = ${state.lastMode})", 2, "debug"
         captureStates(state.lastMode)
      }
      else {
         logDebug "Not capturing pre-mode-change states because previous mode is unknown", 2, "debug"
      }
      state.lastMode = location.getCurrentMode().id
      // Adapted from (older incarnation of) performActiveAction, but note lack of restore for most actions
      if (!verifyNoneOn()) {
         String suffix = getSettingModeSuffix()
         switch (settings["activeAction${suffix}"]) {
            case "on":
               restoreStates()
               state.isDimmed = false
               state.isDimmed = false
               break
            case "onColor":
               if (settings["onColor.CT${suffix}"]) {
                  getDevicesToTurnOn().each { it.setColorTemperature(settings["onColor.CT${suffix}"]) }
                  if (settings["onColor.L${suffix}"]) getDevicesToTurnOn().each { it.setLevel(settings["onColor.L${suffix}"]) }
               }
               else {
                  if (settings["onColor.H${suffix}"] != null &&
                     settings["onColor.S${suffix}"] != null &&
                     settings["onColor.L${suffix}"])
                  {
                     getDevicesToTurnOn().each { it.setColor([hue: settings["onColor.H${suffix}"],
                        saturation: settings["onColor.S${suffix}"], level: settings["onColor.L${suffix}"]]) }
                  }
                  else {
                     if (settings["onColor.H${suffix}"] != null) getDevicesToTurnOn().each { it.setHue(settings["onColor.H${suffix}"]) }
                     if (settings["onColor.S${suffix}"] != null) getDevicesToTurnOn().each { it.setSaturation(settings["onColor.S${suffix}"]) }
                     if (settings["onColor.L${suffix}"] != null) getDevicesToTurnOn().each { it.setLevel(settings["onColor.L${suffix}"]) }
                  }
               }
               state.isDimmed = false
               break
            case "onScene":
               getDevicesToTurnOn().each { it.on() }
               state.isDimmed = false
               break
            default:
               logDebug "Not adjusting lights on mode change because not applicable for configured action for this mode", 2, "debug"
         }
      }
      else {
         logDebug "Not adjusting lights on mode change because none currently on", 2, "debug"
      }
   }
   else {
      // This shouldn't ever be callled because this subscription shouldn't exist, but just in case...
      logDebug "modeChangeHandler: Mode changed but not handling because configured not to", 2, "debug"
   }
}

// Captures light states (to general cache if not configured for per mode, or mode cache if configured
// to save to per-mode cache; optional modeID can save to different mode cache but also only if that mode is
// configured to save, or will save to general)
void captureStates(Long modeID=location.getCurrentMode().id) {
   logDebug "captureStates", 1, "trace"
   if (settings["boolRemember"]) {
      logDebug "  Configured to remember...", 2, "debug"
      String stateKey = "capturedStates"
      if (settings["perMode"] && settings["boolRemember.${modeID}"]) stateKey += ".${modeID}"
      List<com.hubitat.app.DeviceWrapper> devsToCapture = getDevicesToTurnOff(true)
      if (devsToCapture.any { it.currentValue("switch") == "on" }) {
         if (!(state."$stateKey")) state."$stateKey" = [:]
         devsToCapture.each {
            state."$stateKey"[it.id] = [:]
            state."$stateKey"[it.id].switch = it.currentValue("switch")
            if (it.currentValue("level") != null && it.currentValue("level") != 0) {
               state."$stateKey"[it.id].level = it.currentValue("level")
            }
            if (it.currentValue("colorMode") == "RGB") {
               state."$stateKey"[it.id].colorMode = "RGB"
               state."$stateKey"[it.id].H = it.currentValue("hue")            
               state."$stateKey"[it.id].S = it.currentValue("saturation")
            }
            else if (it.hasAttribute("colorTemperature")) {
               state."$stateKey"[it.id].colorMode = "CT"
               state."$stateKey"[it.id].CT = it.currentValue("colorTemperature")
            }
         }
      }
      else {
         logDebug "Skipping capturing of light states because none on", 2, "debug"
      }
   }
}

void restoreStates() {
   // TODO: Add option to pass "fallback action" or device to on() if states not found? For scenes, may want to activate instead
   logDebug "restoreStates", 1, "trace"
   if (settings["boolRemember"]) {
      logDebug "  Configured to remember...", 2, "debug"
      String stateKey = "capturedStates"
      if (settings["perMode"] && settings["boolRemember.${location.getCurrentMode().id}"]) stateKey += ".${location.getCurrentMode().id}"
      List<com.hubitat.app.DeviceWrapper> devsToRestore = getDevicesToTurnOff(state.isDimmed) // Get all devices if dimmed, on-only devices if not
      if (!(state."$stateKey")) stateKey = "capturedStates"  // Fall back to non-per-mode settings if can't find per-mode
      Boolean anySavedOn = false
      devsToRestore.each {
         if (state."$stateKey" && state."$stateKey"[it.id]?.switch == "on") {
            anySavedOn = true
            if (state."$stateKey"[it.id]?.colorMode == "RGB") {
               Integer h, s, l
               h = state."$stateKey"[it.id]?.H
               s = state."$stateKey"[it.id]?.S
               l = state."$stateKey"[it.id]?.level
               if (h != null && s != null && l != null) {
                  it.setColor([hue: h, saturation: s, level: l])
                  if (settings["doOn"]) it.on()
               }
               else {
                  it.on()
               }
            }
            else {
               if (state."$stateKey"[it.id]?.colorMode == "CT") {
                  it.setLevel(state."$stateKey"[it.id]?.level ?: 100)
                  it.setColorTemperature(state."$stateKey"[it.id]?.CT ?: 2700)
                  if (settings["doOn"]) it.on()
               }
               else {
                  if (it.hasCommand("setLevel")) {
                     it.setLevel(state."$stateKey"[it.id]?.level ?: 100)
                     if (settings["doOn"]) it.on()
                  }
                  else {
                     it.on()
                  }
               }
            }
         }
         logDebug "  Finished resotring state for ${it.id}: ${it.displayName}", 2, "debug"
      }
      logDebug """Finished restoring all states: ${state."$stateKey"}"""
      if (!anySavedOn) {
         lights.each { it.on() }
         logDebug "No captured light states were on; turned on all lights", 2, "debug"
      }
   }
}

// Un-selects "use per mode exceptions" and erases all settings (devices and other inputs) associated with them
void removePerModeSettings() {
   def settingNamesToRemove = [] as Set
   List perModeSettingStrings = [
      "perMode", "activeAction.", "scene.", "sceneGroup.", "lights.", "onColor.L.", "onColor.CT.",
      "onColor.H.", "onColor.S.", "inactiveAction.", "dimToLevel."            
   ]
   perModeSettingStrings.each { startsWithString ->
      settingNamesToRemove += settings?.keySet()?.findAll{ it.startsWith(startsWithString) }
   }
   logDebug "Removing: $settingNamesToRemove", 2, "warn"
   settingNamesToRemove.each { settingName ->
      app.removeSetting(settingName)
   }
   app.updateSetting('perMode', [value: false, type: "bool"])
   app.removeSetting('changeWithMode')
   state.perModeSettingsRemoved = true
}

void removeCapturedStates() {
   def toRemove = [] as Set
   toRemove = state?.keySet()?.findAll{ it.startsWith("capturedStates") }
   logDebug "Removing: $toRemove", 2, "warn"
   toRemove.each {
      state.remove(it)
   }
}

void appButtonHandler(btn) {
   switch (btn) {
      case 'btnRemovePerMode':
         removePerModeSettings()
         break
      case 'btnClearCaptured':
         removeCapturedStates()
         break
      default:
         break
   }
}

//=========================================================================
// App Methods
//=========================================================================

def installed() {
   log.debug "${app.label} installed"
   initialize()
}

def updated() {
   log.trace "${app.label} updated"
   initialize()
}

def initialize() {   
   log.trace "${app.label} initializing..."
   unschedule()   
   unsubscribe()
   state.isDimmed = false
   subscribe(onSensors, "motion", motionHandler)
   subscribe(keepOnSensors, "motion", motionHandler)
   if (changeWithMode) {
      subscribe(location, "mode", modeChangeHandler)
      state.lastMode = location.getCurrentMode().id
   }
   logDebug "${app.label} initialized."
}

// Writes text to log.debug if level >= user's logLevel setting; can redirect to trace, info, or warn with type parameter
void logDebug(String text, Integer level=1, String type='debug') {
   if (settings['logLevel'] != null && (settings['logLevel'] as Integer) >= level) {
      if (type == 'debug') log.debug text      
      else log."$type" text
   }
}