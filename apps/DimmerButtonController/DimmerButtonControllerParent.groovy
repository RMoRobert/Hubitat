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
 *  Copyright 2020 Robert Morris
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
 * Changelog:
 * 2.0.3  (2021-04-21)  - Code formatting cleanup (no functional changes); added documentation link
 * 2.0.2  (2020-10-24)  - Minor bugfix in parent app for new installs
 * 2.0    (2020-04-12)  - Allows creation of DBC 2.0 child apps; prevented child apps from being created and possibly orphaned
 *                       if parent not fully installed first
 * 1.1                  - Removed mode input
 * 1.0    (2018-12-27)  - Parent app first release
 *
 */ 
 
definition(
   name: 'Dimmer Button Controller',
   namespace: 'RMoRobert',
   author: 'Robert Morris',
   singleInstance: true,
   installOnOpen: true,
   description: 'Easily configure a button device such as a Pico remote to control one or more bulbs/dimmers/switches with on/off, scene switching, and dimming',
   category: 'Convenience',        
   iconUrl: '',
   iconX2Url: '',
   iconX3Url: '',
   documentationLink: "https://community.hubitat.com/t/release-dimmer-button-controller-configure-pico-to-emulate-hue-dimmer-or-any-button-device-to-easily-control-lights/7726"
)

preferences {
   page(name: 'mainPage', title: 'Dimmer Button Controller', install: true, uninstall: true) {
      if (app.getInstallationState() == 'INCOMPLETE') {
         // Shouldn't happen with installOnOpen: true, but just in case...
         section() {
               paragraph('Please press "Done" to finish installing this app, then re-open it to add Dimmer Button Controller child instances.')
         }
      }
      else {
         section ('About Dimmer Button Controller') {
               paragraph title: 'Dimmer Button Controller', 'This app helps you create automations that control one or more bulbs/dimmers/switches with a button controller device (a 5-button Pico with the "fast" driver, a Hue Dimmer, or an Eria dimmer are recommended, but any button device should work).'
         }         
         section('Dimmer Button Controller Child Apps') {
               app(name: 'childApps2', appName: 'Dimmer Button Controller (Child App) 2', namespace: 'RMoRobert', title: 'Add new Dimmer Button Controller automation', multiple: true)
               // Show DBC 1.x child (and allow new creation) if any instances already exist:
               if (app?.getChildApps()?.find { it.name == 'Dimmer Button Controller (Child App)' }) {
                  app(name: 'childApps1', appName: 'Dimmer Button Controller (Child App)', namespace: 'RMoRobert',
                     title: 'Add new Dimmer Button Controller 1.x automation (deprecated)', multiple: true)
               }
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