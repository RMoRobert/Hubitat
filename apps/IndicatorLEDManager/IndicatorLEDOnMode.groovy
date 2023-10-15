/**
 * ==========================  Innovelli Mode LED ==========================
 *
 *  Copyright 2021-2023 Robert Morris
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
 *  Last modified: 2023-10-14
 * 
 *  Changelog:
 * 1.4.1 - Fix for setOnLEDLevel()
 * 1.4   - Move color to names and level to standard 0-100 (update drivers if needed to match!)
 * 1.3   - Add fan LED command for LZW36 device, add ability to override "off" fan LED level
 * 1.2   - Add separate options for on/off levels, add purple
 * 1.1   - Added metering option
 * 1.0   - Initial public release
 *
 */

import groovy.transform.Field

@Field static final List<String> colorNameList = [
   "red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "light blue", "blue", "purple",
   "violet", "magenta", "rose", "white"
]

@Field static Map colorNameMap = [
   "red": 1,
   "red-orange": 3,
   "orange": 21,
   "yellow": 42,
   "chartreuse": 60,
   "green": 85,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "light blue": 164,
   "blue": 170,
   "purple": 185,
   "violet": 212,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

definition(
   name: "Inovelli Mode LED Manager",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Change \"default\"/regular LED color on Inovelli switches and dimmers with mode changes.",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

preferences {
   page name: "pageMain"
}

def pageMain() {
   dynamicPage(name: "pageMain", title: "Inovelli Mode LED Manager", install: true, uninstall: true) {
      section(styleSection("App Name")) {
         label title: "Name this Indicator LED Manager - Mode app:", required: true
      }

      section(styleSection("Devices")) {
         input name: "innoDevs", type: "capability.switch", title: "Inovelli switches/dimmers with LED bars",
            multiple: true
      }

      section(styleSection("Modes")) {
         location.getModes().sort().each { mode ->
            input name: "color.${mode.id}", type: "enum", title: "Color for <strong>${mode.name}</strong> mode:",
               options: colorNameList, width: 4
            input name: "level.${mode.id}", type: "number", title: "Level (when on)", description: "0-100 (100 if not specified)",
               range: "0..100", width: 4
            input name: "level.off.${mode.id}", type: "number", title: "Level (when off)", description: "0-100 (no change if not specified)",
               range: "0..100", width: 4
         }
         input name: "otherModeBehavior", type: "enum", title: "If color not specified for mode above, then...",
            options: [["default": "Use default color/level (specified below)"], ["no": "Do not change color"]],
            defaultValue: "no", submitOnChange: true
         input name: "overrideFanOffLevel", type: "number", title: "Override level for fan off LED?",
            range: "0..100", description: "optional; will use off level above if not specified"
         if (otherModeBehavior == "default") {
            input name: "color.default", type: "enum", title: "Default color if color not specified for mode:",
               options: colorNameList, width: 4, required: true
            input name: "level.default", type: "number", title: "Level (when on)", description: "0-100 (100 if not specified)",
               range: "0..100", width: 4
            input name: "level.off.default", type: "number", title: "Level (when off)", description: "0-100 (no change if not specified)",
               range: "0..100", width: 4
         }
         input name: "btnSave", type: "button", title: "Save", submitOnChange: true
      }

      section(styleSection("Commands")) {
         paragraph "This app will attempt commands in the following order (stopping at the first group where commands match):"
         paragraph "For on/default: <ul><li>setLEDColor(color, level)</li><li>setLightLEDColor(color, level), setFanLEDColor(color, level)</li></ul>"
         paragraph "For off: <ul><li>setOffLEDLevel(level)</li><li>setLightOffLEDLevel(level), setFanOffLEDLevel(level)</li></ul>"
      }

      section(styleSection("Test")) {
         input name: "btnTest", type: "button", title: "Test Settings (run as if mode just changed to current)",
            submitOnChange: true, width: 6
            location.getModes().each { mode ->
            input name: "btnTest.${mode.id}", type: "button", title: "Test Settings for ${mode.name}",
               submitOnChange: true, width: 6
         }
      }

      section(styleSection("Logging, Metering")) {
         input name: "msDelay", type: "number", title: "Command metering (delay between commands), in milliseconds",
            range:"1..10000", description: "(optional) examples: 50 or 200"
         input name: "logEnable", type: "bool", title: "Enable debug logging"
      }
   }
}

void modeChangeHandler(evt=null, Long overrideModeId=null) {
   if (logEnable) log.debug "modeChangeHandler(evt=$evt, overrideModeId=$overrideModeId)"
   Long modeId =  (overrideModeId == null) ? location.getCurrentMode().id : overrideModeId
   if (logEnable) log.trace "Mode is ${location.getCurrentMode()} (ID = $modeId)"
   //Integer intColor = colorNameMap.(settings["color.${modeId}"])
   String color = settings["color.${modeId}"]
   Integer level = (settings["level.${modeId}"] != null) ? settings["level.${modeId}"] : 100
   Integer offLevel = settings["level.off.${modeId}"]
   // Scale 0-100 to Inovelli 0-10:
   //if (level > 0 && level < 10) level  = 10
   //if (offLevel > 0 && offLevel < 10) level  = 10
   //level = Math.round(level/10) as Integer
   //if (offLevel != null) offLevel = Math.round(offLevel/10) as Integer
   if (color == null) {
      if (settings["otherModeBehavior"] == "default") {
         if (logEnable) log.trace "No color specified for this mode; using default color"
         color = settings["color.default"]
      }
      else {
         if (logEnable) log.trace "No color specified for this mode; ignoring mode change"
      }
   }
   // Now, if still null, is configured not to set, so can ignore
   if (color != null) {
      // "On" LED level and LED color:
      if (logEnable) log.trace "Changing color: color = $color, level = $level"
      innoDevs?.each { dev ->
         if (logEnable) log.trace "Setting color for ${dev.displayName}"
         if (dev.hasCommand("setLEDColor")) {
            if (logEnable) log.trace "sending setLEDColor($color, $level)"
            dev.setLEDColor(color, level)
            if (settings.msDelay) {
               pauseExecution(settings.msDelay as Integer)
            }
         }
         else if (dev.hasCommand("setOnLEDLevel")) {
               if (logEnable) log.trace "sending setOnLEDLevel($onLevel)"
               dev.setOnLEDLevel(level)
               if (settings.msDelay) {
                  pauseExecution(settings.msDelay as Integer)
               }
         }
         else if (dev.hasCommand("setLightLEDColor")) {
            if (logEnable) log.trace "sending setLightLEDColor($color, $level)"
            dev.setLightLEDColor(color, level)
            if (settings.msDelay) {
               pauseExecution(settings.msDelay as Integer)
            }
            if (dev.hasCommand("setFanLEDColor")) {
               if (logEnable) log.trace "sending setFanLEDColor($color, $level)"
               dev.setFanLEDColor(color, level)
               if (settings.msDelay) {
                  pauseExecution(settings.msDelay as Integer)
               }
            }
            else {
               if (logEnable) "found setLightLEDColor but not setFanLEDColor"
            }
         }
         else {
            if (logEnable) log.warn "No matching command found for setting color and on level for ${dev.displayName}"
         }
         // "Off" LED level:
         if (offLevel != null) {
            if (logEnable) log.trace "Setting level for ${dev.displayName}"
            if (dev.hasCommand("setOffLEDLevel")) {
               if (logEnable) log.trace "sending setOffLEDLevel($offLevel)"
               dev.setOffLEDLevel(offLevel)
               if (settings.msDelay) {
                  pauseExecution(settings.msDelay as Integer)
               }
            }
            else if (dev.hasCommand("setLightOffLEDLevel")) {
               if (logEnable) log.trace "sending setLightOffLEDLevel($offLevel)"
               dev.setLightOffLEDLevel(offLevel)
               if (settings.msDelay) {
                  pauseExecution(settings.msDelay as Integer)
               }
               if (dev.hasCommand("setFanOffLEDLevel")) {
                  if (logEnable) log.trace "sending setFanOffLEDLevel($offLevel)"
                  Integer fanOffLevel = offLevel
                  if (settings.overrideFanOffLevel != null) {
                     fanOffLevel = settings.overrideFanOffLevel
                     // if (fanOffLevel > 0 && fanOffLevel < 10) fanOffLevel  = 10
                     // fanOffLevel = Math.round(fanOffLevel/10) as Integer
                     if (logEnable) "overriding fan off level as $fanOffLevel"
                  }
                  dev.setFanOffLEDLevel(fanOffLevel)
                  if (settings.msDelay) {
                     pauseExecution(settings.msDelay as Integer)
                  }
               }
               else {
                  if (logEnable) "found setLightOffLEDLevel but not setFanOffLEDLevel"
               }
            }
            else {
               if (logEnable) log.warn "No matching command found for setting color and on level for ${dev.displayName}"
            }
         }
         else {
            if (logEnable) "not setting off level because off level null"
         }
      }
   }
}

void appButtonHandler(btn) {
   switch (btn) {
      case { it.startsWith("btnTest.") }:
         String strModeId = btn - "btnTest."
         Long longModeId = Long.parseLong(strModeId)
         modeChangeHandler(null, longModeId)
         break
      case "btnTest":
         modeChangeHandler()
         break
      case "btnSave":
         // just need to submit
         break
      default:
         break
   }
}

String styleSection(String sectionHeadingText) {
   return """<span style="font-weight:bold; font-size: 115%">$sectionHeadingText</span>"""
}

//=========================================================================
// App Methods
//=========================================================================

void installed() {
   log.debug "${app.label} installed"
   initialize()
}

void updated() {
   log.trace "${app.label} updated"
   initialize()
}

void initialize() {
   log.debug "${app.label} initializing..."
   unschedule()
   unsubscribe()
   subscribe(location, "mode", modeChangeHandler)
   log.debug "Initialized."
}