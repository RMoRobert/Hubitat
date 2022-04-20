/**
 * ====================  Device Status Announcer (Parent App) ====================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2022 Robert Morris
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
 * 3.0   (2022-04-19) - Updated to allow v3 child app creation
 * 2.0   (2020-08-02) - New parent app
 *
 */

definition(
   name: "Device Status Announcer",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Speak or notify status of locks, contact sensors, and other devices on demand",
   category: "Convenience",
   installOnOpen: true,
   singleInstance: true,
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-status-announcer-tts-or-notification-if-lock-unlocked-door-window-open-etc/45723"
)

preferences {
   page(name: "mainPage", title: "Device Status Announcer", install: true, uninstall: true) {
      section {
         if (app.getInstallationState() == "INCOMPLETE") {
            paragraph("<b>Please press \"Done\" to finish installing this app, then re-open it to begin setting up child instances.</b>")
         }
         else {
            app(name: "childApps", appName: "Device Status Announcer Child 3", namespace: "RMoRobert", title: "Add new Device Status Announcer instance...", multiple: true)
            Boolean oldChildApps = app?.getChildApps().find { it.name == 'Device Status Announcer Child 2.x' }
            if (oldChildApps) {
               app(name: "childApps2x", appName: "Device Status Announcer Child 2.x", namespace: "RMoRobert",
                  title: "Add new Device Status Announcer 2.x instance (deprecated)...", multiple: true)
            }
         }
      }
   }
}

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated() with settings: ${settings}"
   //unsubscribe()
   initialize()
}

void initialize() {
   log.debug "initialize()"
}