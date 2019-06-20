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
 *  Copyright 2019 Robert Morris
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
 *  Last modified: 2019-06-18
 * 
 *  Changelog:
 * 
 *  1.0 - Initial public release
 *
 */

definition(
    name: "Timed Switch Helper (Child App)",
    namespace: "RMoRobert",
    author: "Robert Morris",
    parent: "RMoRobert:Timed Switch Helper",
    description: "Do not install directly; use Timed Switch Helper app instead.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)


preferences {
    section("") {
        paragraph title: "Timed Switch Helper", "Listens for one (usually virtual) switch to be turned on, then turns on other (usually phyiscal) switch for the configured time before turning both off."
    }
    section("Switches") {
        input "switch1", "capability.switch", title: "Turn on this switch", multiple: false, required: true
        input "switch2", "capability.switch", title: "when this virtual/timed switch is turned on", multiple: false, required: true
        input "minutes1", "number", title: "for this many minutes before turning off both"
        input "debugLogging", "bool", title: "Enable debug logging"
        paragraph 'This app will turn on the first switch for the specified number of minutes when the second switch is turned on, then turn off both. If the second switch is turned off in the meantime, the timer is cancelled. Turning on/off the first switch in the meantime does not affect the timer.'
    }
}

def installed() {
    log.debug "Installed..."
    initialize()
}

def updated() {
    log.debug "Updated..."
    initialize()
}

def initialize() {
    log.debug "Initializing; subscribing to events"
    unsubscribe()
    unschedule()
    subscribe(switch2, "switch", timedSwitchHandler)
}

/**
 * If timed switch turned on, turn on "real" switch and schedule timed switch to turn off in specified
 * amount of time. If/when timed switch turned off, turn off "real" switch.
**/
def timedSwitchHandler(evt) {
    log.trace "Timed switch turned ${evt.value}"
    if (evt.value == "on") {
        logDebug "Timed switch turned on; cancelling existing timers and turning on real switch"
        unschedule()
        switch1.on()
        logDebug "Scheduling real switch to turn off in " + minutes1 + " minutes..."
        runIn(minutes1 * 60, turnOffTimedSwitch)
    }
    else if (evt.value == "off") {
        logDebug "Timed switch turned off; canceling timers (if any remain) and turning off real switch"
        unschedule()
        switch1.off()
    }
}

/**
 * Turns off timed switch, intended to be called when timer is up. Does not directly turn off
 * "real" switch because this is handled in timedSwitchHandler when the timed switch is turned
 * off. This allows manual turning-off of the timed switch before the app's own timer is up with
 * the timer being cancelled in either case.
**/
def turnOffTimedSwitch() {
    logDebug "Turning off timed switch"
    switch2.off()
    logDebug "End"
}


def logDebug(msg) {
    if (debugLogging) log.debug(msg)
}
