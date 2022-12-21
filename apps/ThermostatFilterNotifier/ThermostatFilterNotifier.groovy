/**
 *  Thermostat Filter Notifier
 *  Description: Tracks thermostat runtime and can send notification when exceeds certain number of
 *               hours. Timer can be reset at any time.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Last modified: 2022-12-21
 *  Changes:
 *   2022-12-21: Fix runtime calcuation (was reversed)
 *   2022-12-20: Initial release
 *
 */
 
definition(
   name: "Thermostat Filter Notifier",
   namespace: "RMoRobert",
   author: "RMoRboert",
   description: "Track thermostat runtime and get notified when time to change filter (based on preferences)",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

@groovy.transform.Field static final List<String> trackedStates = ["heating", "cooling", "fan only"]

preferences {
mainPage()  
}

def mainPage() {
   page(name:"mainPage", title:"Settings", install: true, uninstall: true) {
      section("Track this thermostat...") {
         input name: "thermoDev", type: "capability.thermostat", title: "Select thermostat", multiple: false
      }
      section("Send notification when...") {
         input name: "runtimeHours", type: "number", title: "Hours of runtime exceeds", defaultValue: 200, required: true
         input name:"notifyDevices", type: "capability.notification", title: "To this notification device", multiple: true
      }
      section("Logging", hideable: true, hidden: true) {
         input "logEnable", "bool", title: "Enable debug logging"
      }
      section("Reset") {
         paragraph "Use the button below to reset the total tracked runtime (e.g., after replacing the filter)"
         input name: "btnReset", type: "button", title: "Reset Runtime"
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
      Double runtimeHrs = (runtime as Double) / 3600000
      if (logEnable == true) log.debug "Converted runtime is $runtimeHrs hr"
      state.totalRuntime = (state.totalRuntime ?: 0) + runtimeHrs
      if (logEnable == true) log.debug "New total runtime is ${state.totalRuntime} hr"
      checkTotalRuntime()		
   }
}

// Checks total runtime, sends notification if exceeds
void checkTotalRuntime() {
   if (state.totalRuntime > runtimeHours) {
      if (logEnable == true) log.debug "Total runtime of ${state.totalRuntime} is greater than $runtimeHours; sending notification"
      notifyDevices?.each {
         it.deviceNotification "${thermoDev.displayName} exceeds ${state.totalRuntime as Integer} hours. Replace filter."
      }
   }
   else {
      if (logEnable == true) log.debug "Total runtime of ${state.totalRuntime} is less than $runtimeHours; no notification"
   }
}

void resetTotalRuntime() {
   if (logEnable == true) log.debug "resetTotalRuntime() -- resetting total runtime to 0"
   state.totalRuntime = 0.0
}

void appButtonHandler(String btn) {
   log.debug "appButtonHandler($btn)"
   switch (btn) {
      case "btnReset":
         resetTotalRuntime()
         break
      default:
         log.warn "Unhandled button: $btn"
   }
   }
