/**
 * ====================  One Swithc at a Time (Child App) =====================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2023 Robert Morris
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
 * 1.0   (2023-07-06) - Initial release
 *
 */

definition(
   name: "One Switch at a Time Child",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Speak or notify status of locks, contact sensors, and other devices on demand",
   category: "Convenience",
   parent: "RMoRobert:One Switch at a Time",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/COMING-SOON"
)

preferences {
   page name: "pageMain"
}

Map pageMain() {
   dynamicPage(name: "pageMain", title: "One Switch at a Time", uninstall: true, install: true) {
      section("Name") {
         input name: "customLabel", title: "Name this app:", type: "text", defaultValue: "One Switch at a Time", required: true
      }

      section("Choose Devices") {
         input name: "theSwitches", type: "capability.switch", title: "Select switches", multiple: true
         paragraph "When any of the above switches turn on, all of the others will be turned off."
      }
      
      section("Advanced Options", hideable: true, hidden: true) {
         input name: "onOffOptimization", type: "bool", title: "Enable on/off optimizations"
         input name: "modes", type: "mode", title: "Restrict to mode(s)", multiple: true
         paragraph "<details><summary>Help</summary><ul><li>On/off optimization: will not send \"Off\" command to devices that already report as off</li><li>Restrict to mode(s): app only operate while in the selected hub modes</li></ul></details>"
      }

      section("Logging") {
         input name: "logEnable", type: "bool", title: "Enable debug logging" 
      }
   }
}

void switchOnHandler(evt) {
   if (logEnable) log.debug "switchOnHandler(): ${evt.name} is ${evt.value}"
   if (modes) {
      if (logEnable) log.debug "Checking modes because mode restriction selected..."
      if (!(location.currentMode.name in modes)) {
         if (logEnable) log.debug "Exiting because mode ${location.currentMode.name} is not one of ${modes}"
      }
   }
   Long triggerDeviceId = evt.getDeviceId()
   theSwitches.each { com.hubitat.app.DeviceWrapper sw ->
      if (sw.idAsLong != triggerDeviceId) {
         if (onOffOptimization != true || sw.currentSwitch == "on") {
            if (logEnable) log.debug "Turing off: ${sw.displayName}"
            sw.off()
         }
      }
   }
}

void installed() {
   log.trace "installed()"
   initialize()
}

void updated() {
   log.trace "updated()"
   initialize()
}

void initialize() {
   log.trace "initialize()"
   if (logEnable) {
      log.debug "Debug logging is enabled. It will remain enabled until manually disabled."
   }
   unsubscribe()
   subscribe theSwitches, "switch.on", "switchOnHandler"
   app.updateLabel(customLabel)
}