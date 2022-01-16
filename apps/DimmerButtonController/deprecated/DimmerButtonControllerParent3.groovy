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
 * Changelog:
 * 3.0    (2021-07-05)  - Allows creation of DBC 3.0 child apps;
 *                        added "presets" functionality (in parent, plus additional child changes)
 * 2.0.3  (2021-04-21)  - Code formatting cleanup (no functional changes); added documentation link
 * 2.0.2  (2020-10-24)  - Minor bugfix in parent app for new installs
 * 2.0    (2020-04-12)  - Allows creation of DBC 2.0 child apps; prevented child apps from being created and possibly orphaned
 *                        if parent not fully installed first
 * 1.1                  - Removed mode input
 * 1.0    (2018-12-27)  - Parent app first release
 *
 */ 

 import groovy.transform.Field
 
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
               app(name: 'childApps3', appName: 'Dimmer Button Controller (Child App) 3', namespace: 'RMoRobert', title: 'Add new Dimmer Button Controller automation', multiple: true)
               // Show DBC 2.x child (and allow new creation) if any instances already exist:
               if (app?.getChildApps()?.find { it.name == 'Dimmer Button Controller (Child App) 2' }) {
                  app(name: 'childApps2', appName: 'Dimmer Button Controller (Child App) 2', namespace: 'RMoRobert',
                     title: 'Add new Dimmer Button Controller 2.x automation (deprecated)', multiple: true)
               }
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

// "Presets"

List<Map> getAllPresets(Integer version=3) {
   if (version == 3) {
      final List<Map> presets =
      [
         // Preset:
         [
            id: 1,
            name: "Fast Pico (5-Btn) with startLevelChange",
            description: "Turn on (2700K at 100%) with button 1 press, ramp up/down with button 2 and 4 (lights must support startLevelChange), turn off with 5. Middle button 4000K at 100%.",
            settings: [
               ["boolShowSetForAll", "bool", true],
               ["btn1.pushed.Action", "enum", "on"],
               ["btn1.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn1.pushed.Press1.SetForAll", "bool", true],
               ["btn1.pushed.Press1.SetForAll.L", "number", 100],
               ["btn1.pushed.Press1.SetForAll.CT", "number", 2700],
               ["btn2.pushed.Action", "enum", "bri"],
               ["btn2.pushed.UseStartLevelChange", "bool", true],
               ["btn2.released.Action", "string", "StopLevelChange"],
               ["btn3.pushed.Action", "enum", "on"],
               ["btn3.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn3.pushed.Press1.SetForAll", "bool", true],
               ["btn3.pushed.Press1.SetForAll.L", "number", 100],
               ["btn3.pushed.Press1.SetForAll.CT", "number", 4000],
               ["btn4.pushed.Action", "enum", "dim"],
               ["btn4.pushed.UseStartLevelChange", "bool", true],
               ["btn4.released.Action", "string", "StopLevelChange"],
               ["btn5.pushed.Action", "enum", "off"]
            ]
         ],
         // Preset:
         [
            id: 2,
            name: "Hue/Fast Pico (4-Btn) with startLevelChange",
            description: "Turn on (2700K at 100%) with button 1 press, ramp up/down with button 2 and 3 (lights must support startLevelChange), turn off with 4.",
            settings: [
               ["boolShowSetForAll", "bool", true],
               ["btn1.pushed.Action", "enum", "on"],
               ["btn1.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn1.pushed.Press1.SetForAll", "bool", true],
               ["btn1.pushed.Press1.SetForAll.L", "number", 100],
               ["btn1.pushed.Press1.SetForAll.CT", "number", 2700],
               ["btn2.pushed.Action", "enum", "bri"],
               ["btn2.pushed.UseStartLevelChange", "bool", true],
               ["btn2.released.Action", "string", "StopLevelChange"],
               ["btn3.pushed.Action", "enum", "dim"],
               ["btn3.pushed.UseStartLevelChange", "bool", true],
               ["btn3.released.Action", "string", "StopLevelChange"],
               ["btn4.pushed.Action", "enum", "off"]
            ]
         ],
         // Preset:
         [
            id: 3,
            name: "Fast Pico (5-Btn) without startLevelChange",
            description: "Turn on (2700K at 100%) with button 1 press, step up/down 15% with button 2 and 4, turn off with 5. Middle button 4000K at 100%.",
            settings: [
               ["boolShowSetForAll", "bool", true],
               ["dimStep", "number", 15],
               ["btn1.pushed.Action", "enum", "on"],
               ["btn1.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn1.pushed.Press1.SetForAll", "bool", true],
               ["btn1.pushed.Press1.SetForAll.L", "number", 100],
               ["btn1.pushed.Press1.SetForAll.CT", "number", 2700],
               ["btn2.pushed.Action", "enum", "bri"],
               ["btn2.pushed.UseStartLevelChange", "bool", false],
               ["btn3.pushed.Action", "enum", "on"],
               ["btn3.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn3.pushed.Press1.SetForAll", "bool", true],
               ["btn3.pushed.Press1.SetForAll.L", "number", 100],
               ["btn3.pushed.Press1.SetForAll.CT", "number", 4000],
               ["btn4.pushed.Action", "enum", "dim"],
               ["btn4.pushed.UseStartLevelChange", "bool", false],
               ["btn5.pushed.Action", "enum", "off"]
            ]
         ],
         // Preset:
         [
            id: 4,
            name: "Hue/Fast Pico (4-Btn) without startLevelChange",
            description: "Turn on (2700K at 100%) with button 1 press, step up/down 15% with button 2 and 3, turn off with 4.",
            settings: [
               ["boolShowSetForAll", "bool", true],
               ["dimStep", "number", 15],
               ["btn1.pushed.Action", "enum", "on"],
               ["btn1.pushed.Press1.SubAction", "enum", "toSettings"],
               ["btn1.pushed.Press1.SetForAll", "bool", true],
               ["btn1.pushed.Press1.SetForAll.L", "number", 100],
               ["btn1.pushed.Press1.SetForAll.CT", "number", 2700],
               ["btn2.pushed.Action", "enum", "bri"],
               ["btn2.pushed.UseStartLevelChange", "bool", false],
               ["btn3.pushed.Action", "enum", "dim"],
               ["btn3.pushed.UseStartLevelChange", "bool", false],
               ["btn4.pushed.Action", "enum", "off"]
            ]
         ]
      ]
   }
   else {
      log.warn "Unsupported version $version in getAllPresets(). Ensure child and parent app code are both current."
   }
}

Map getPresetByID(Integer id, Integer version =3) {
   return getAllPresets(version).find { it.id == id}
}