/**
 * ==========================  Blinds Button Controller (Parent App) ==========================
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
 
definition(
   name: "Blinds Button Controller",
   namespace: "RMoRobert",
   author: "Robert Morris",
   singleInstance: true,
   description: "Easily configure a button device to open, close, or adjust blinds/shades",
   category: "Convenience",
   installOnOpen: true,
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: ""
)   

preferences {
   page(name: "mainPage", title: "Blinds Button Controller", install: true, uninstall: true) {
      if (app.getInstallationState() == "INCOMPLETE") {
         section() {
            paragraph('Please press "Done" to finish installing this app, then re-open it to continue.')
         }
      } else {
         section ("About Blinds Button Controller") {
            paragraph title: "Blinds Button Controller", "This app helps you create automations that control one or more shade/blind device(s) with a button controller device (a Pico, Hue Dimmer, or paddle remote are recommended, but any button device should work)."
         }         
         section("Blinds Button Controller Child Apps") {
            app(name: "childApps1", appName: "Blinds Button Controller (Child App) 1", namespace: "RMoRobert", title: "Add new Blinds Button Controller automation", multiple: true)
         }
      }
   }
}

void installed() {
   //log.debug "Installed with settings: ${settings}"
   initialize()
}

void updated() {
   //log.debug "Updated with settings: ${settings}"
   unsubscribe()
   initialize()
}

void initialize() {
   log.debug "Initializing; there are ${childApps.size()} child apps installed: ..."
   childApps.each {child ->
      log.debug "  child app: ${child.label}"
   }
}