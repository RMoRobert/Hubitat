/**
 * ==========================  Timed Switch Helper (Child App)  ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used to tie together a "real" switch and a virtual switch that you
 *  intend to use as a timer on the "real" switch. This app will watch for the virtual switch to be
 *  turned on, then turn off the real (and virtual) switch after the configured time.
 *
 *  TO INSTALL:
 *  Add code for parent app and then and child app (this). Install/create new instance of parent
 *  app and begin using.
 *
 *  Copyright 2024 Robert Morris
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
 *  Last modified: 2024-02-18
 * 
 *  Changelog:
 * 
 *  2.0.1 - Fix for "also turn off (second switch) when (first switch) turned off behavior"
 *  2.0   - Added options to cancel or restart timer with "main" switch change
          - Easier path to timing one swith witout separate virtual/timed switch; code cleanup
 *  1.0   - Initial public release
 *
 */

import groovy.transform.Field

definition(
   name: "Timed Switch Helper (Child App)",
   namespace: "RMoRobert",
   author: "Robert Morris",
   parent: "RMoRobert:Timed Switch Helper",
   description: "Listens for one (usually virtual) switch to be turned on, then turns on other (usually phyiscal) switch for the configured time before turning both off.",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-timed-switch-helper/17672"
)

@Field static final String sWATCH_NEITHER = "no"
@Field static final String sWATCH_TIMED = "timed"
@Field static final String sWATCH_BOTH = "both"

@Field static final String sTIMED_SWITCH = "timed"
@Field static final String sSAME_SWITCH = "same"

@Field static final List<Map<String,String>> appTypeOptions = [["timed": "Separate \"timed\"/\"real\" switches: turn on switch when timed switch turned on [DEFAULT]"],   // sTIMED_SWITCH
                                                               ["same": "Same switch: automatically turn off after certain time when turned on"] // sSAME_SWITCH
                                                              ]

@Field static final List<Map<String,String>> watchOptions = [["no": "Never cancel timer"],                                // sWATCH_NEITHER
                                                             ["timed": "Cancel timer if second/timed switch turned off [DEFAULT]"], // sWATCH_TIMED[]
                                                             ["both": "Cancel timer if either switch turned off"]         // sWATCH_BOTH
                                                            ]
@Field static final List<Map<String,String>> watchOptionsSame = [["no": "Never cancel timer (not recommended)"],           // sWATCH_NEITHER
                                                                 ["timed": "Cancel timer if switch turned off [DEFAULT]"], // sWATCH_TIMED[]
                                                                ]

//@Field static final Integer defaultDebounceTime = 5

preferences {
   page name: "mainPage"
}

def mainPage() {
   dynamicPage(title: "Timed Switch Helper", name: "mainPage", install: true, uninstall: true) {
      section("Timed Switch Helper") {
         paragraph "Listens for one (usually virtual) switch to be turned on, then turns on other (usually phyiscal) switch for the configured time before turning both off. " +
            "Alternatively, listens for one switch to be turned on, then turns that switch off after the specified period of time."
         label description: "Enter a name for this Timed Switch Helper instance:", required: true
      }
      section("Switches") {
         input "appType", "enum", title: "Timed switch type:", options: appTypeOptions, defaultValue: sWATCH_TIMED, required: true, submitOnChange: true
         if (settings.appType == sSAME_SWITCH) {
            input "switch2", "capability.switch", title: "When this switch turns on:", multiple: false, required: true
            input "minutes1", "number", title: "Turn off after this many minutes:"
            app.removeSetting("switch1")
         }
         else {
            input "switch1", "capability.switch", title: "Turn on this switch", multiple: false, required: true
            input "switch2", "capability.switch", title: "when this virtual/timed switch is turned on", multiple: false, required: true
            input "minutes1", "number", title: "for this many minutes before turning off both"
         }
         input "toWatch", "enum", title: "Cancel timer when...", options: (settings.appType == sSAME_SWITCH ? watchOptionsSame : watchOptions), defaultValue: "timed", required: true
         if (settings.toWatch == sWATCH_BOTH) {
            input "turnOff1", "bool", title: "Also turn off ${switch2?.displayName ?: 'second switch'} when ${switch1?.displayName ?: 'first switch'} turned off", submitOnChange: true
            /* if (settings.turnOff1) {
               input "debounceTime", "number", title: "Ignore ${switch1?.displayName ?: 'first switch'} events for this many seconds after turning off due to above (avoids excessive un-scheduling; optional)",
                  defaultValue: defaultDebounceTime, range: "0..30"
            } */
         }
         input "debugLogging", "bool", title: "Enable debug logging"
      }
   }
}

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated()"
   initialize()
}

void initialize() {
   log.debug "initialize()"
   unsubscribe()
   unschedule()
   if (settings.toWatch == sWATCH_BOTH && settings.appType != sSAME_SWITCH) {
      subscribe(switch1, "switch", "switch1Handler")
   }
   subscribe(switch2, "switch", "switch2Handler")
}

/**
 * If real switch turned off and configured to watch both switches, cancel timer and also turn
*  off "timed" switch if configured to do so
**/
void switch1Handler(evt) {
   logDebug "switch1 turned ${evt.value}"
   if (evt.value == "on") {
      logDebug "ignoring \"on\" event"
   }
   else if (evt.value == "off") {
      if (settings.toWatch == sWATCH_BOTH && settings.appType != sSAME_SWITCH) {
         unschedule()
         logDebug "Scheduling real switch to turn off in " + minutes1 + " minutes..."
         runIn(minutes1 * 60, "turnOffTimedSwitch")
      }
      else {
         log.warn "Unexpected execution of switch1Handler: not configured to watch both or configured for same switch. Re-open app, verify settings, and hit \"Done.\""
      }
      logDebug "First switch (${switch1.displayName}) turned off; canceling timers (if any)"
      unschedule()
      if (settings.turnOff1) {
         log.debug "Also turning off second switch (${switch2.displayName})"
         //if (settings.debounceTime) setRecentOffTrue()
         switch2.off()
         //if (settings.debounceTime) runIn(settings.debounceTime as Integer, "setRecentOffFalse")
      }
   }
}

/**
 * If timed switch turned on, turn on "real" switch and schedule timed switch to turn off in specified
 * amount of time. If/when timed switch turned off, turn off "real" switch (unless not configured to)
**/
void switch2Handler(evt) {
   logDebug "switch2 turned ${evt.value}"
   if (evt.value == "on") {
      logDebug "turned on..."
      if (settings.toWatch != sWATCH_NEITHER) {
         unschedule()
      }
      logDebug "Scheduling ${switch2?.displayName ?: 'switch2'} to turn off in " + minutes1 + " minutes..."
      runIn(minutes1 * 60, "turnOffSwitch2")
      if (settings.appType != sSAME_SWITCH) {
         logDebug "Turning on ${switch1?.displayName ?: 'first switch'}"
         switch1.on()
      }
   }
   else if (evt.value == "off") {
      logDebug "turned off..."
      if (settings.toWatch != sWATCH_NEITHER) {
         logDebug "canceling timers (if any remain)"
         unschedule()
      }
      if (settings.appType != sSAME_SWITCH) {
         logDebug "not cancelling timers because configured to ignore"
         logDebug "Turning off other switch (${switch1.displayName})"
         switch1.off()
      }
   }
}

/**
 * Turns off timed switch, intended to be called when timer is up. Does not directly turn off
 * "real" switch because this is handled in switch2Handler when the timed switch is turned
 * off. This allows manual turning-off of the timed switch before the app's own timer is up with
 * the timer being cancelled in either case.
**/
void turnOffSwitch2() {
    logDebug "Turning off ${switch2?.displayName ?: 'second switch'}"
    switch2.off()
}

/*
void setRecentOffTrue() {
   atomicState.recentOff = true
}

void setRecentOffFalse() {
   atomicState.recentOff = false
}
*/

void logDebug(msg) {
    if (debugLogging) log.debug(msg)
}