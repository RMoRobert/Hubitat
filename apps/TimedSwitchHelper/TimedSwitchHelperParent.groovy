/**
 * ==========================  Timed Switch Helper (Parent App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used to tie together a "real" switch and a virtual switch that you
 *  intend to use as a timer on the "real" switch. This app will watch for the virtual switch to be
 *  turned on, then turn off the real (and virtual) switch after the configured time.
 *
 *  TO INSTALL:
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
 *  app and begin using.
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
 *  Last modified: 2021-04-26
 * 
 *  Changelog:
 *
 *  2.0 - Minor code cleanup; removed mode selector from parent app
 *  1.0 - Initial release
 *
 */ 
 
definition(
   name: "Timed Switch Helper",
   namespace: "RMoRobert",
   author: "Robert Morris",
   singleInstance: true,
   installOnOpen: true,
   description: "Listens for one (usually virtual) switch to be turned on, then turns on other (usually phyiscal) switch for the configured time before turning both off.",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
)   

preferences {
   page(name: 'mainPage', title: 'Timed Switch Helper - App', install: true, uninstall: true) {
      section ("Timed Switch Helper") {
         paragraph "Listens for one (usually virtual) switch to be turned on, then turns on other (usually phyiscal) switch for the configured time before turning both off."
      }
      section {
         app(name: "childApps", appName: "Timed Switch Helper (Child App)", namespace: "RMoRobert", title: "Add New Timed Switch Helper", multiple: true)
      }
   }
}

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "Updated with settings: ${settings}"
   initialize()
}

void initialize() {
   log.debug "initialize()"
   childApps.each {child ->
      log.debug "  child app: ${child.label}"
   }
}