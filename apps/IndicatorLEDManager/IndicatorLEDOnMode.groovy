/**
 * ==========================  Innovelli Mode LED ==========================
 *
 *  Copyright 2021 Robert Morris
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
 *  Last modified: 2021-04-25
 * 
 *  Changelog:
 *
 * 1.1   - Added metering option
 * 1.0   - Initial public release
 *
 */

import groovy.transform.Field

@Field static final List<String> colorNameList = [
   "red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "light blue", "blue", "violet",
   "magenta", "rose", "white"
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
      section("App Name") {
         label title: "Name this Indicator LED Manager - Mode app:", required: true
      }

		section("Devices") {
         input name: "innoDevs", type: "capability.switch", title: "Inovelli Red Series switches/dimmers",
            multiple: true
      }

      section("Modes") {
         location.getModes().each { mode ->
            input name: "color.${mode.id}", type: "enum", title: "Color for <strong>${mode.name}</strong> mode:",
               options: colorNameList, width: 6
            input name: "level.${mode.id}", type: "number", title: "Level", description: "0-100 (100 if not specified)",
               range: "0..100", width: 6
         }
         input name: "otherModeBehavior", type: "enum", title: "If color not specified for mode above, then...",
            options: [["default": "Use default color/level (specified below)"], ["no": "Do not change color"]],
            defaultValue: "no", submitOnChange: true
         if (otherModeBehavior == "default") {
            input name: "color.default", type: "enum", title: "Default color if color not specified for mode:",
               options: colorNameList, width: 6, required: true
            input name: "level.default", type: "number", title: "Level", description: "0-100 (100 if not specified)",
               range: "0..100", width: 6
         }
         input name: "btnTest", type: "button", title: "Test Settings (run as if mode just changed to current)",
            submitOnChange: true, width: 8
         input name: "btnSave", type: "button", title: "Save", submitOnChange: true, width: 4
      }

      section("Commands") {
         paragraph "This app will attempt commands in the following order and stop after the first is reached:"
         paragraph "<ul><li>setLEDColor(color, level)</li><li>setLightLEDColor(color, level)</li></ul>"
      }

      section("Logging, Metering") {
         input name: "msDelay", type: "number", title: "Command metering (delay between commands), in milliseconds",
            range:"1..10000", description: "(optional) examples: 50 or 200"
         input name: "logEnable", type: "bool", title: "Enable debug logging"
      }
   }
}

void modeChangeHandler(evt=null) {
   if (logEnable) log.debug "modeChangeHandler()"
   Long modeId = location.getCurrentMode().id
   if (logEnable) log.trace "Mode is ${location.getCurrentMode()} (ID = $modeId)"
   Integer color = colorNameMap.(settings["color.${modeId}"])
   Integer level = (settings["level.${modeId}"] != null) ? settings["level.${modeId}"] : 100
   // Scale 0-100 to Inovelli 0-10:
   if (level > 0 && level < 10) level  = 10
   level = Math.round(level/10) as Integer
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
      if (logEnable) log.trace "Changing color: color = $color, scaled level = $level"
      innoDevs?.each { dev ->
         if (logEnable) log.trace "Setting color for ${dev.displayName}"
         if (dev.hasCommand("setLEDColor")) {
            if (logEnable) log.trace "sending setLEDColor($color, $level)"
            dev.setLEDColor(color, level)
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
         }
         else {
            if (logEnable) log.warn "No matching command found for ${dev.displayName}"
         }
      }
   }
}

void appButtonHandler(btn) {
   switch (btn) {
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