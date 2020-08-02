/**
 * ====================  Device Status Announcer (Parent App) ====================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2020 Robert Morris
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
 * == App version: 2.0.0 ==
 *
 * Changelog:
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
                app(name: "childApps", appName: "Device Status Announcer Child 2.x", namespace: "RMoRobert", title: "Add new Device Status Announcer child...", multiple: true)
            }
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    //unsubscribe()
    initialize()
}

def initialize() {
    log.debug "Initializing; there are ${childApps.size()} child apps installed:"
    childApps.each {child ->
        log.debug "  child app: ${child.label}"
    }
}