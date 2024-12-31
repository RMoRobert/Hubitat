/**
 *  HVAC Filter Notifier
 *
 *  Description: Tracks furnace or AC (based on thermostat) runtime and can send notification when exceeds
 *               certain number of hours. Timer can be reset at any time.
 *
 *  Copyright © 2022-2024 Robert Morris
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Changes:
 *   2024-12-30: Add replace history
 *   2023-02-12: Avoid reset with initialize()
 *   2023-01-28: Add "snooze" option
 *   2022-12-28: Add runtime and last reset to UI
 *   2022-12-23: Rename to HVAC Filter Notifier
 *   2022-12-21: Fix runtime calcuation (was reversed)
 *   2022-12-20: Initial release
 *
 */
 
definition(
   name: "HVAC Filter Notifier",
   namespace: "RMoRobert",
   author: "RMoRboert",
   description: "Track furnace/AC thermostat runtime and get notified when time to change filter (based on preferences)",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

@Field static final List<String> trackedStates = ["heating", "cooling", "fan only"]
@Field static final Long MILLISECONDS_PER_HOUR = 3600000
@Field static final Integer MAX_HISTORY_DATES = 20

preferences {
   mainPage()
}

def mainPage() {
   page(name:"mainPage", title:"HVAC Filter Notifier Configuration", install: true, uninstall: true) {
      section(styleSection("Track this thermostat:")) {
         input name: "thermoDev", type: "capability.thermostat", title: "Select thermostat", multiple: false
      }
      section(styleSection("Send notification when...")) {
         input name: "runtimeHours", type: "number", title: "Hours of runtime exceeds:", defaultValue: 300, required: true
         input name: "notifyDevices", type: "capability.notification", title: "To this notification device:", multiple: true
         input name: "btnSnooze_12", type: "button", title: "Snooze for 12 hr", width: 3
         input name: "btnSnooze_24", type: "button", title: "Snooze for 1 day", width: 3
         input name: "btnSnooze_48", type: "button", title: "Snooze for 2 days", width: 3
         input name: "btnSnooze_168", type: "button", title: "Snooze for 1 week", width: 3
         if (state.snoozeUntil > 0 && state.snoozeUntil > now()) {
            String strSnoozeEnd = new Date(state.snoozeUntil).format("YYYY-MM-dd HH:mm z")
            paragraph "<strong>Snoozing notifications until:</strong> ${strSnoozeEnd}", width: 6
            input name: "btnSnooze_Cancel", type: "button", title: "Cancel Snooze", width: 6
         }
         else {
            if (state.snoozeUntil != null) state.remove("snoozeUntil")
         }
      }
      section(styleSection("Statistics")) {
         if (state.totalRuntime != null) {
            paragraph """<span style="font-weight:bold">Current Total Runtime:</span> ${String.format("%.2f", state.totalRuntime)} hr"""
            paragraph "<small>NOTE: Total is not updated until thermostat returns to idle (or other non-tracked state).</small>"
         }
         input name: "saveHistory", type: "bool", title: "Save reset history (up to last ${MAX_HISTORY_DATES} reset dates)", defaultValue: true
         if (state.lastReset) {
            String strLastReset = new Date(state.lastReset).format("yyyy-MM-dd HH:mm z", location.timeZone)
            paragraph """<span style="font-weight:bold">Last Reset:</span> ${strLastReset}"""
         }
         paragraph "Use the button below to reset the total tracked runtime (e.g., after replacing the filter):"
         input name: "btnReset", type: "button", title: "Reset Runtime", submitOnChange: true
      }
      if (saveHistory != false && state.resetHistory) {
         section("Reset History", hideable: true, hidden: true) {
            state.resetHistory.each { Long resetTime ->
               String strResetTime = new Date(resetTime).format("yyyy-MM-dd HH:mm z", location.timeZone)
               paragraph "$strResetTime", width: 6
            }
         }
      }
      section(styleSection("Name and Logging")) {
         label title: "Customize installed app name:", required: true
         input "logEnable", "bool", title: "Enable debug logging"
      }
   }
}

void installed() {
   initialize()
}

void updated() {
   unsubscribe()
   //unschedule()
   initialize()
}

void initialize() {
   log.debug "Initializing"
   subscribe(thermoDev, "thermostatOperatingState", "operatingStateHandler")
   if (state.lastReset == null) state.lastReset = now()
   if (state.resetHistory == null || saveHistory == false) state.resetHistory = []
}

void operatingStateHandler(evt) {
   if (logEnable == true) log.debug "operatingStateHandler: ${evt.device?.displayName} ${evt.name} is ${evt.value}"
   if (evt.value in trackedStates) {
      state.startTime = now()
      if (logEnable == true) log.debug "Saving start time as ${state.startTime}"
   }
   else {
      Long endTime = now()
      Long runtime = endTime - (state.startTime ?: 0)
      if (logEnable == true) log.debug "Calculated runtime is $runtime ms"
      if (runtime < 0) {
         log.warn "Runtime was less than zero: $runtime; this runtime will be counted as 0 and not have an effect on the total"
         runtime = 0
      }
      Double runtimeHrs = (runtime as Double) / MILLISECONDS_PER_HOUR
      if (logEnable == true) log.debug "Converted runtime is $runtimeHrs hr"
      state.totalRuntime = (state.totalRuntime ?: 0) + runtimeHrs
      if (logEnable == true) log.debug "New total runtime is ${state.totalRuntime} hr"
      checkTotalRuntime()
   }
}

// Checks total runtime, sends notification if exceeds
void checkTotalRuntime() {
   if (state.totalRuntime > runtimeHours) {
      if (logEnable == true) log.debug "Total runtime of ${state.totalRuntime} is greater than $runtimeHours; preparing notification"
      Boolean isOKToSend = true
      if (state.snoozeUntil > 0) {
         if (now() < state.snoozeUntil) {
            if (logEnable == true) log.debug "Not sending notification; snoozed until ${state.snoozeUntil}"
            isOKToSend = false
         }
         else {
            if (logEnable == true) log.debug "Past snooze time; removing snooze state"
            state.remove("snoozeUntil")
         }
      }
      if (isOKToSend) {
         notifyDevices?.each { DeviceWrapper notifDev ->
            notifDev.deviceNotification "${thermoDev.displayName} of ${state.totalRuntime as Integer} hr exceeds ${runtimeHours} hr. Replace filter."
         }
      }
   }
   else {
      if (logEnable == true) log.debug "Total runtime of ${state.totalRuntime} is less than $runtimeHours; no notification"
   }
}

void resetTotalRuntime() {
   if (logEnable == true) log.debug "resetTotalRuntime() -- resetting total runtime to 0"
   state.totalRuntime = 0.0
   state.lastReset = now()
   if (saveHistory) addDateTimeToResetHistory(state.lastReset)
}

void addDateTimeToResetHistory(Long unixTime) {
   if (logEnable) log.trace "addDateTimeToResetHistory($unixTime)"
   if (state.resetHistory == null) state.resetHistory
   state.resetHistory << unixTime
   state.resetHistory = state.resetHistory.sort().reverse()
   if (state.resetHistory.size() > MAX_HISTORY_DATES) {
      state.resetHistory = state.resetHistory.take(MAX_HISTORY_DATES)
   }
}

String styleSection(String sectionHeadingText) {
   return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

void appButtonHandler(String btn) {
   log.debug "appButtonHandler($btn)"
   switch (btn) {
      case "btnReset":
         resetTotalRuntime()
         break
      case "btnSnooze_Cancel":
         state.remove("snoozeUntil")
         break
      case { it.startsWith("btnSnooze_") }:
         String strHours = btn - "btnSnooze_"
         Long msToAdd = Long.parseLong(strHours) * MILLISECONDS_PER_HOUR
         state.snoozeUntil = now() + msToAdd
         if (logEnable) "snoozing until ${state.snoozeUntil}"
         break
      default:
         log.warn "Unhandled button: $btn"
   }
}
