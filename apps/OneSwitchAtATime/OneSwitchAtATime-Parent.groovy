/**
 * ====================  One Swithc at a Time (Parent App) ====================
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
   name: "One Switch at a Time",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Select a set of switches and have others turned off when one is turned on, so only one will be on at a time",
   category: "Convenience",
   installOnOpen: true,
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/COMING-SOON"
)

preferences {
   page(name: "mainPage", title: "One Switch at a Time", install: true, uninstall: true) {
      section {
         if (app.getInstallationState() == "INCOMPLETE") {
            // shouldn't happen with installOnOpen: true, but just in case...
            paragraph "<b>Please press \"Done\" to finish installing this app, then re-open it to begin setting up child instances.</b>"
         }
         else {
            app name: "childApps", appName: "One Switch at a Time Child", namespace: "RMoRobert", title: "Create new...", multiple: true
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
   //unsubscribe()
   initialize()
}

void initialize() {
   log.trace "initialize()"
}