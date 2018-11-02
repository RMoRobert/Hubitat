/**
 * ==========================  Lights on Motion Plus (Parent App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used to turn on lights/switches according to the state of
 *  motion sensor. It extends the ability of most existing similar apps by adding the
 *  ability to dim the lights for 30 seconds as a "warning" before turning off, and it is
 *  also capable of "remembering" the levels and on/off states of individual bulbs (or
 *  switches or dimmers) so that all associated lights will not turn on simply because
 *  motion is detected if one or more are already on (useful for multiple bulbs in a room
 *  where you want them to all turn off after motion stops and turn on only those that
 *  were previously on when motion resumes rather than the entire set of bulbs--useful
 *  if not all bulbs are usually used).
 
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
 *  Last modified: 2018-10-05
 * 
 *  Changelog:
 * 
 *  3.0 - Initial release of v3 app
 *
 */ 
 
definition(
    name: "Lights on Motion Plus",
    namespace: "RMoRobert",
    author: "Robert Morris",
    singleInstance: true,
    description: "Turn lights on/off based on motion; optionally dim before turning off and remember/restore previous state of lights when motion resumes",
    category: "Convenience",        
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)   

preferences {
  section ("") {
    paragraph title: "Lights on Motion Plus", "Turn lights on/off based on motion; optionally dim before turning off and remember/restore previous state of lights when motion resumes"
  }
  section {
    app(name: "childApps", appName: "Lights on Motion Plus (Child App)", namespace: "RMoRobert", title: "Add Lights on Motion Plus automation...", multiple: true)
  }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "Initializing; there are ${childApps.size()} child apps installed"
    childApps.each {child ->
        log.debug "  child app: ${child.label}"
    }
}