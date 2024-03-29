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
 *  Copyright 2018-2023 Robert Morris
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
 *  Last modified: 2023-01-24
 * 
 *  Changelog:
 * 
 *  5.0.1 - Minor updates
 *  5.0   - Added ability to create 5.0 child apps; ensure parent is installed before child apps can be created
 *  3.0   - Initial release of v3 app
 *
 */ 
 
definition(
  name: "Lights on Motion Plus",
  namespace: "RMoRobert",
  author: "Robert Morris",
  singleInstance: true,
  installOnOpen: true,
  description: "Turn lights on/off based on motion; optionally dim before turning off and remember/restore previous state of lights when motion resumes",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: "",
)   

preferences {
  page(name: "mainPage", title: "Lights on Motion Plus", install: true, uninstall: true) {
    section(""){
      paragraph "Turn lights on/off based on motion; optionally dim before (or instead of) turning off and remember/restore previous state of lights when motion resumes"
    }
    section("") {
      Boolean oldChildApps = app?.getChildApps().find { it.name == 'Lights on Motion Plus (Child App)' }
      String newChildButtonText = "Add new Lights on Motion Plus ${oldChildApps ? '5 ' : ''}automation"
      app(name: 'childApps5x', appName: 'Lights on Motion Plus (Child App) 5', namespace: 'RMoRobert', title: newChildButtonText, multiple: true)
      if (oldChildApps) {
          app(name: 'childApps4x', appName: 'Lights on Motion Plus (Child App)', namespace: 'RMoRobert',
              title: 'Add new Lights on Motion Plus 4.x automation (deprecated)', multiple: true)
      }
    }
  }
}

void installed() {
   log.debug "Installed with settings: ${settings}"
   initialize()
}

void updated() {
   log.debug "Updated with settings: ${settings}"
   unsubscribe()
   initialize()
}

void initialize() {
   log.debug "Initializing; there are ${childApps.size()} child apps installed"
   childApps.each { child ->
      log.debug "  child app: ${child.label}"
   }
}