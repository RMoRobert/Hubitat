/**
 * ==========================  Dimmer Button Controller (Parent App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used to used with button devices and makes it easy to
 *  configure each button to turn on/off and/or set level of certain bulbs or to dim/brighten 
 *  bulbs with button presses/holds/releases as specified. It was specifically created with
 *  the goal of emulating a Hue Dimmer (as it behaves when paired to Hue) with a Pico 5-button
 *  remote paired to Hubitat with the "fast" driver, though it should work for any compatible
 *  button device.
 
 *  TO INSTALL:
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
 *  app only (do not use child app directly).
 *
 *  Copyright 2018 Robert Morris
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
 *  Last modified: 2018-12-15
 * 
 *  Changelog:
 * 
 *  1.0 - Parent app first release
 *
 */ 
 
definition(
    name: "Dimmer Button Controller",
    namespace: "RMoRobert",
    author: "Robert Morris",
    singleInstance: true,
    description: "Easily onfigure a button device such as a Pico remote to control one or more bulbs/dimmers/switches",
    category: "Convenience",        
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)   

preferences {
  section ("") {
    paragraph title: "Dimmer Button Controller", 'This app helps you create automations that control one or more bulbs/dimmers/switches with a button controller device (a 5-button Pico with the "fast" driver is recommended, though any button device should work)'
  }
  section {
    app(name: "childApps", appName: "Dimmer Button Controller (Child App)", namespace: "RMoRobert", title: "Add new Dimmer Button Controller automation", multiple: true)
  }
}

def installed() {
    //log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    //log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "Initializing; there are ${childApps.size()} child apps installed:"
    childApps.each {child ->
        log.debug "  child app: ${child.label}"
    }
}
