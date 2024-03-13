/**
 * ==========================  Device Status Announcer ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2024 Robert Morris
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
 *
 *  Author: Robert Morris
 *
 * Changelog:
 * 3.2.2 (2024-03-13) - Add option to omit attribute name for custom devices
 * 3.2.1 (2023-12-28) - Add option to use hub variable for TTS speak level
 * 3.2   (2023-06-13) - Add support for hub variables in notification/speech and prepend/append text
 * 3.1.1 (2023-02-02) - Remove inadvertent logging even when disabled
 * 3.1   (2023-02-25) - Add power meteter
 * 3.0.1 (2022-04-23) - Added optional volume parameter for speech command
 * 3.0   (2022-04-19) - Added ability to check "custom" devices (additional capabilities/attributes besides the pre-provided options)
 * 2.6   (2020-12-17) - Added options to turn switches off/on if devices are/aren't in expected states
 * 2.5   (2020-09-20) - Added thermostats and contact/lock "name grouping"
 * 2.0.1 (2020-08-02) - Made easier to remove "all OK" notification/TTS if desired
 * 2.0   (2020-08-02) - New parent/child strucutre, additional notification options
 * 1.0   (2020-07-25) - First public release
 *
 */

import groovy.transform.Field

@Field static final Map<String,Map<String,Object>> customDeviceCapabilities  = [
   "switch": [displayName: "switch", attribute: "switch", type: "string", values: ["on", "off"]],
   motionSensor: [displayName: "motion sensor", attribute: "motion", type: "string", values: ["active", "inactive"]],
   waterSensor: [displayName: "water sensor", attribute: "water", type: "string", values: ["wet", "dry"]],
   windowShade: [displayName: "window shade", attribute: "windowShade", type: "string", values: ["closed", "open", "unknown"]],
   windowBlind: [displayName: "window blind", attribute: "windowBlind", type: "string", values: ["closed", "open", "unknown"]],
   powerMeter: [displayName: "power reading", attribute: "power", type: "integer"],
   presenceSensor: [displayName: "presence", attribute: "presence", type: "string", values: ["present", "not present"]],
   illuminanceMeasurement: [displayName: "illuminance sensor", attribute: "illuminance", type: "integer"],
   switchLevel: [displayName: "dimmer level", attribute: "level", type: "integer"],
   battery: [displayName: "battery level", attribute: "battery", type: "integer"]
]

@Field static final String hubVarRegEx = '%(.*?)%'

definition(
   name: "Device Status Announcer Child 3",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Speak or notify status of locks, contact sensors, and other devices on demand",
   category: "Convenience",
   parent: "RMoRobert:Device Status Announcer", // Remove or comment out this line if 1.x upgrader or don't want to use parent
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-status-announcer-tts-or-notification-if-lock-unlocked-door-window-open-etc/45723"
)

preferences {
   page name: "pageMain"
   page name: "pageCustomDeviceGroup"
   page name: "pageRemoveContactLockGroup"
   page name: "pageRemoveCustomDeviceGroup"
   page name: "pageViewReport"
}

Map pageMain() {
   state.remove("removedGroup")
   state.remove("removeGroup")
   dynamicPage(name: "pageMain", title: "Device Status Announcer", uninstall: true, install: true) {
      section(styleSection("Name")) {
         label title: "Name this Device Status Announcer:", required: true
      }

      section(styleSection("Choose Devices")) {
         input name: "contactSensors", type: "capability.contactSensor", title: "Choose contact sensors:", submitOnChange: true, multiple: true
         input name: "boolContactOpen", type: "bool", title: "Announce only if sensor(s) open", defaultValue: true
         input name: "doorLocks", type: "capability.lock", title: "Choose locks:", submitOnChange: true, multiple: true
         input name: "boolDoorUnlocked", type: "bool", title: "Announce only if lock(s) unlocked", defaultValue: true
         input name: "thermostats", type: "capability.thermostat", title: "Choose thermostats:", submitOnChange: true, multiple: true
         if (settings["thermostats"]) {
            input name: "thermostatCoolThreshold", type: "number", title: "if cooling setpoint below:", width: 6
            input name: "thermostatHeatThreshold", type: "number", title: "if heating setpoint above:", width: 6
            paragraph "(heating and cooling setpoints will be evaluated only when thermostat in heating or cooling mode, respectively)"
         }
      }
      
      section(styleSection("Custom Devices")) {
         paragraph "Custom device groups allow you to announce/notify based on states beyond those offered above."
         state.customDeviceGroups?.each { Integer groupNum ->
            String capability = settings."customDeviceGroup_${groupNum}_capability"
            String strTitle = customDeviceCapabilities."$capability"?.displayName ?
                              """Custom ${customDeviceCapabilities."$capability".displayName} devices""" :
                              "Custom devices (click/tap to configure)"
            href(name: "pageCustomDeviceGroup${groupNum}Href",
               page: "pageCustomDeviceGroup",
               params: [groupNumber: groupNum],
               title: strTitle,
               description: getDeviceGroupDescription(groupNum) ?: "Click/tap to choose devices and options...",
               state: getDeviceGroupDescription(groupNum) ? "complete" : null)
         }
         input name: "btnNewCustomDeviceGroup", type: "button", title: "Add new custom device group"
      }
      if (contactSensors && doorLocks) {
         section(styleSection("Naming/Grouping Devices")) {
            List<Map<Long, String>> allContactsAndLocks = []
            (contactSensors + doorLocks).each {
               allContactsAndLocks.add([(it.deviceId): it.displayName])
            }
            paragraph "To group contact sensors and door locks into a joint announcement (e.g., \"Back door is open and unlocked\"), " +
                     "choose the appropriate options below. (Do not add the same device to multiple groups.)"
            if (state.contactLockGroups == null || state.contactLockGroups == []) state.contactLockGroups = [0]
            (state.contactLockGroups).each {
               input name: "contactLockGroup_${it}_devs", type: "enum", title: "Devices to group into single name:", multiple: true, submitOnChange: true,
                  options: allContactsAndLocks, width: 5
               if (settings["contactLockGroup_${it}_devs"]) {
                  input name: "contactLockGroup_${it}_name", type: "text", title: "Name for this group:", width: 4, submitOnChange: true
                  href name: "contactLockGroup_${it}_hrefRemove", page: "pageRemoveContactLockGroup", title: "Remove", description: "", params: [groupNumber: it], width: 3
               }
            }
            if (state.contactLockGroups?.size() > 0 && settings["contactLockGroup_${state.contactLockGroups[-1]}_name"] && settings["contactLockGroup_${state.contactLockGroups[-1]}_devs"]) {
               input name: "btnNewContactLockGroup", type: "button", title: "New Group", submitOnChange: true
            }
         }
      }

      section(styleSection("Notification Options")) {
         input name: "speechDevice", type: "capability.speechSynthesis", title: "Announce on this device:",  multiple: true
         input name: "notificationDevice", type: "capability.notification", title: "Send notification to this device:", multiple: true
         input name: "notificationTime", type: "time", title: "Daily at this time (optional):"
         input name: "sensorAway", type: "capability.presenceSensor", title: "Or any time this presence sensor becomes not present", multiple: true 
         input name: "announcementSwitch", type: "capability.switch", title: "Or any time this switch is turned on"
         input name: "allGoodSpeech", type: "text", title: "Text to speak if all devices are OK (blank for no speech if all devices OK):",
            defaultValue: (app.getInstallationState() == "INCOMPLETE" ? "All devices are OK" : ""), required: false
         input name: "allGoodNotification", type: "text", title: "Notification text to send if all devices are OK (blank for no notification if all devices OK):",
            defaultValue: "", required: false
         input name: "prependText", type: "text", title: "Text to prepend to announcements/notifications (optional)",
            defaultValue: ""
         input name: "appendText", type: "text", title: "Text to append to announcements/notifications (optional)",
            defaultValue: ""
         paragraph "<small>NOTE: Hub variables can be used in any of the above text by using the <code>%variable-name%</code> format.</small>"
         input name: "goodSwitches", type: "capability.switch", title: "Turn this switch on if all devices are OK when announcement or notification is requested", multiple: true
         input name: "badSwitches", type: "capability.switch", title: "Turn this switch on if any devices are not OK when announcement or notification is requested", multiple: true
         paragraph "The above switches will also be turned off if the stated condition is no longer true when an annoucement or notification is requested."
      }

      section(styleSection("View/Test Report")) {
         href(name: "pageViewReportHref",
               page: "pageViewReport",
               title: "View current report",
               description: "Evaluate all devices now according to the criteria above, and display a report of devices in undesired state (the same information that would be spoken or sent in a real notification/announcement).")
         paragraph "The \"Test Announcement/Notification Now\" button will send a TTS announcement and/or notification to your selected device(s) if any current device states and options would cause an annoucement or notification. (Note: if you have changed options since you last loaded this page, press \"Done\" to save settings and re-enter the app before testing.) This a manual method to trigger the same actions the above options can automate:"
         input name: "btnTestNotification", type: "button", title: "Test Announcement/Notification Now", submitOnChange: true
      }
      
      section("Advanced Options", hideable: true, hidden: !(settings.ttsVolumeUseVariable == true)) {
         if (ttsVolumeUseVariable == true) {
            List<String> vars =  getGlobalVarsByType("integer")?.collect { it.key } ?: []
            input name: "ttsVolumeVariable", type: "enum", title: "Specify volume for \"Speak\" command:", width: 6, options: vars
         }
         else {
            input name: "ttsVolume", type: "number", title: "Specify volume for \"Speak\" command (optional)", width: 6
         }
         input name: "ttsVolumeUseVariable", type: "bool", title: "Use variable for \"Speak\" command volume?", width: 6, submitOnChange: true
         input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
         input "modes", "mode", title: "Only make announcements/notifications when mode is", multiple: true, required: false
         input name: "debugLogging", type: "bool", title: "Enable debug logging" 
      }

      section() {
         input name: "btnApply", type: "button", title: "Apply Settings"
      }
   }
}

def pageCustomDeviceGroup(Map params) {
   Integer groupNum
   if (params?.groupNumber != null) {
      state.currGroupNum = params.groupNumber
      groupNum = params.groupNumber
   }
   else {
      groupNum = state.currGroupNum
   }
   state.remove("cancelDelete")
   dynamicPage(name: "pageCustomDeviceGroup", title: "Custom device group", uninstall: false, install: false, nextPage: "pageMain") {
      section(styleSection("Choose Devices")) {
         input name: "customDeviceGroup_${groupNum}_capability", type: "enum", title: "Select capability for custom devices:",
            options: customDeviceCapabilities.collect { [(it.key): it.value.displayName.capitalize()] },
            submitOnChange: true
         if (settings."customDeviceGroup_${groupNum}_capability") {
            String capability = settings."customDeviceGroup_${groupNum}_capability"
            input name: "customDeviceGroup_${groupNum}_devs", type: "capability.${capability}", multiple: true
            input name: "customDeviceGroup_${groupNum}_onlyIf", type: "bool",
               title: "Announce/notify only if device state is (or isn't)...", submitOnChange: true
            if (settings."customDeviceGroup_${groupNum}_onlyIf" == true) {
               if (customDeviceCapabilities[capability]?.type == "string") {
                  input name: "customDeviceGroup_${groupNum}_isOrIsnt", type: "enum",
                     title: "Announce/notify only if device state...",
                     options: ["is", "is not"], required: true,  defaultValue: "is"
                  input name: "customDeviceGroup_${groupNum}_stateString", type: "enum", title: "...in this state (or states):",
                     required: true, multiple: true, options: customDeviceCapabilities[capability].values
               }
               else if (customDeviceCapabilities[capability]?.type == "integer") {
                  input name: "customDeviceGroup_${groupNum}_aboveBelowEqual", type: "enum",
                     title: "Announce/notify only if device state...", options: [">=", ">", "=", "<", "<=", "!="],
                     required: true, defaultValue: "=", width: 6
                  input name: "customDeviceGroup_${groupNum}_stateNumber", type: "number", title: "this value:",
                     required: true, width: 6
               }
               else {
                  paragraph "Unsupported capability; verify settings."
               }
            }
            input name: "customDeviceGroup_${groupNum}_omitAttributeName", type: "bool", title: "Omit attribute name (${customDeviceCapabilities[capability]?.attribute}) in notification"
         }
         href(name: "hrefRemoveCustomDeviceGroup",
               page: "pageRemoveCustomDeviceGroup",
               title: "Remove group",
               description: "This will remove this group and all settings related to it.",
               params: [groupNumber: groupNum]
         )
      }
   }
}

Map pageRemoveContactLockGroup(Map params) {
   dynamicPage(name: "pageRemoveContactLockGroup", title: "Remove Contact/Lock Group", uninstall: false, install: false, nextPage: "pageMain") {
      if (params?.groupNumber != null) state.removeGroup = params.groupNumber
      section("") {
         if (state.removeGroup != null && state.removedGroup != true) {
            paragraph "Press the button below to confirm removal of this group. Press \"Next\" to continue without removing."
            input name: "btnRemoveCLGroup.${state.removeGroup}", type: "button", title: "Remove Group", submitOnChange: true
         }
         else {
            if (state.removedGroup) paragraph "Group removed. Press \"Next\" to continue."
            else paragraph "Unknown group removal status. Try again if it did not suceed. Press \"Next\" to continue."
         }
      }
   }
}

Map pageRemoveCustomDeviceGroup(Map params) {
   dynamicPage(name: "pageRemoveCustomDeviceGroup", title: "Remove Custom Device Group", uninstall: false, install: false, nextPage: "pageMain") {
      if (params?.groupNumber != null) state.removeGroup = params.groupNumber
      section("") {
         if (state.removeGroup != null && state.removedGroup != true) {
            paragraph "Press the button below to confirm removal of this group. Press \"Next\" to continue without removing."
            input name: "btnRemoveCustomDeviceGroup.${state.removeGroup}", type: "button", title: "Remove Group", submitOnChange: true
         }
         else {
            if (state.removedGroup) paragraph "Group removed. Press \"Next\" to continue."
            else paragraph "Unknown group removal status. Try again if it did not suceed. Press \"Next\" to continue."
         }
      }
   }
}

Map pageViewReport() {
   dynamicPage(name: "pageViewReport", title: "Device Status Announcer", uninstall: false, install: false, nextPage: "pageMain") {
      section(styleSection("Device Report")) {
         String deviceReport = getDeviceStatusReport()
         if (deviceReport) {
            paragraph "$deviceReport"
         }
         else {
            paragraph '<span style="font-style: italic">(no devices to report)</span>'
         }
      }
   }
}

String getDeviceGroupDescription(Integer groupNum) {
   String desc = ""
   if (settings."customDeviceGroup_${groupNum}_capability") {
      String deviceNames = settings."customDeviceGroup_${groupNum}_devs"?.collect { it.displayName }?.join(", ")
      String capability = settings."customDeviceGroup_${groupNum}_capability"
      String attribute = customDeviceCapabilities."${capability}"?.attribute ?: "(unsupported attribute; please re-select)"
      String attributeDisplayName = customDeviceCapabilities."${capability}"?.displayName ?: "(unknown)"
      //desc = "Custom ${attributeDisplayName} devices - ${deviceNames}"  // already in header on page; is this needed anywhere else?
      desc = deviceNames
      if (settings."customDeviceGroup_${groupNum}_onlyIf") {
         if (customDeviceCapabilities."$capability"?.type == "string") {
            String isOrNot = (settings."customDeviceGroup_${groupNum}_isOrIsnt" == "is not") ? " not" : ""
            String devState = settings."customDeviceGroup_${groupNum}_stateString"?.join(", ")
            desc += "\nOnly if${isOrNot} ${devState}"

         }
         else if (customDeviceCapabilities."$capability"?.type == "integer") {
            String aboveBelowEqual = settings."customDeviceGroup_${groupNum}_aboveBelowEqual"
            String devState = settings."customDeviceGroup_${groupNum}_stateNumber"
            desc += "\nOnly if ${attributeDisplayName} ${aboveBelowEqual} ${devState}"
         }
         else {
            String attrType = customDeviceCapabilities."$capability"?.type 
            log.warn "unsupported capabability: ${attrType}"
         }
      }
   }
   return desc
}

String getDeviceStatusReport() {
   logDebug "Generating device status report..."
   List<String> statusReportList = []
   String statusReport = ""
   com.hubitat.app.DeviceWrapperList contacts = settings["contactSensors"]
   com.hubitat.app.DeviceWrapperList locks = settings["doorLocks"]
   if (!(settings["boolIncludeDisabled"])) {
      contacts = contacts?.findAll { it.isDisabled != true }
      locks = locks?.findAll { it.isDisabled != true }
      // TODO: check custom devices and thermostats here, too!
   }
   state.contactLockGroups?.each { groupId ->
      if (settings["contactLockGroup_${groupId}_devs"] && settings["contactLockGroup_${groupId}_name"]) {
         List<String> groupState = []
         if (boolContactOpen != false && boolDoorUnlocked != false) {
            contacts.find { it.id in settings["contactLockGroup_${groupId}_devs"] && it.currentValue("contact") == "open" }?.each {
               groupState << "open"
               contacts.remove(it)
            }
            locks.find { it.id in settings["contactLockGroup_${groupId}_devs"] && it.currentValue("lock") == "unlocked" }?.each {
               groupState << "unlocked"
               locks.remove(it)
            }
         }
         else {
            contacts.find { it.id in settings["contactLockGroup_${groupId}_devs"]}?.each {
               groupState << it.currentValue("contact")
               contacts.remove(it)
            }
            locks.find { it.id in settings["contactLockGroup_${groupId}_devs"] }?.each {
               groupState << it.currentValue("lock")
               locks.remove(it)
            }
         }
         if (groupState.size() > 0) {
            statusReportList << """${settings["contactLockGroup_${groupId}_name"]} is ${groupState.join(" and ")}"""
         }
      }
   }
   contacts?.each {
      if (boolContactOpen != false) {
         if (it.currentValue("contact") == "open") statusReportList << "${it.displayName} is open"
      }
      else {
         statusReportList << "${it.displayName} is ${it.currentValue("contact")}"
      }
   }
   locks?.each {
      if (boolDoorUnlocked != false) {
         if (it.currentValue("lock") == "unlocked")  statusReportList << "${it.displayName} is unlocked"
      }
      else {
         statusReportList << "${it.displayName} is ${it.currentValue("lock")}"
      }
   }
   thermostats?.each {
      if ((it.currentValue("thermostatMode") == "cool" ||
          it.currentValue("thermostatMode") == "auto") &&
          (settings["thermostatCoolThreshold"] != null &&
          it.currentValue("coolingSetpoint") < settings["thermostatCoolThreshold"]))
      {
         statusReportList << "${it.displayName} is set to ${it.currentValue('coolingSetpoint')}"
      }
      else if ((it.currentValue("thermostatMode") == "heat" ||
               it.currentValue("thermostatMode") == "emergency heat" ||
               it.currentValue("thermostatMode") == "auto") &&
               (settings["thermostatCoolThreshold"] != null &&
               it.currentValue("heatingSetpoint") > settings["thermostatHeatThreshold"]))
      {
         statusReportList << "${it.displayName} is set to ${it.currentValue('heatingSetpoint')}"
      }
   }
   state.customDeviceGroups?.each { Integer groupNum ->
      com.hubitat.app.DeviceWrapperList devs = settings."customDeviceGroup_${groupNum}_devs"
      String capability = settings."customDeviceGroup_${groupNum}_capability"
      String attribute = customDeviceCapabilities."${capability}"?.attribute
      if (attribute == null) {
         log.warn "Unsupported attribute $attribute for device setting group number $groupNum"
      }
      else {
         if (settings."customDeviceGroup_${groupNum}_onlyIf") {
            if (customDeviceCapabilities."$capability"?.type == "string") {
               String isOrIsnt = settings."customDeviceGroup_${groupNum}_isOrIsnt"
               List<String> values = settings."customDeviceGroup_${groupNum}_stateString"
               devs.each { com.hubitat.app.DeviceWrapper d ->
                  if (isOrIsnt == "is not") {
                     if (!(values.contains(d.currentValue(attribute)))) {
                       statusReportList << """${d.displayName} ${customDeviceCapabilities."$capability"?.displayName} is ${d.currentValue(attribute)}"""
                     }
                  }
                  else if ((values.contains(d.currentValue(attribute)))) {
                     statusReportList << """${d.displayName} ${customDeviceCapabilities."$capability"?.displayName} is ${d.currentValue(attribute)}"""
                  }
               }
            }
            else if (customDeviceCapabilities."$capability"?.type == "integer") {
               String aboveBelowEqual = settings."customDeviceGroup_${groupNum}_aboveBelowEqual"
               Integer value = settings."customDeviceGroup_${groupNum}_stateNumber"
               devs.each { com.hubitat.app.DeviceWrapper d ->
                  // Should handle cases where integer values are actually decimals, but might be overkill...
                  Integer currentValue = Math.round((d.currentValue(attribute) ?: 0) as BigDecimal)
                  // If so, this should suffice:
                  //Integer currentValue = d.currentValue(attribute)
                  String statusText // may or may not actually be appended, depending on comparisions a few lines below
                  if (settings."customDeviceGroup_${groupNum}_omitAttributeName" == true) {
                     statusText = """${d.displayName} is ${currentValue}"""
                  }
                  else {
                     statusText = """${d.displayName} ${customDeviceCapabilities."$capability"?.displayName} is ${currentValue}"""
                  }                  
                  if (aboveBelowEqual == "=") {
                     if (currentValue == value) statusReportList << statusText
                  }
                  else if (aboveBelowEqual == "!=") {
                     if (currentValue != value) statusReportList << statusText
                  }
                  else if (aboveBelowEqual == "<=") {
                     if (currentValue <= value) statusReportList << statusText
                  }
                  else if (aboveBelowEqual == "<") {
                     if (currentValue < value) statusReportList << statusText
                  }
                  else if (aboveBelowEqual == ">") {
                     if (currentValue > value) statusReportList << statusText
                  }
                  else if (aboveBelowEqual == ">=") {
                     if (currentValue >= value) statusReportList << statusText
                  }
                  else {
                     log.warn "unsupported comparison: $aboveBelowEqual"
                  }
               }
            }
            else {
               log.warn """unsupported attribute type: ${customDeviceCapabilities."$capability"?.type}"""
            }
         }
         else {
            devs.each { com.hubitat.app.DeviceWrapper d ->
               if (settings."customDeviceGroup_${groupNum}_omitAttributeName" == true) {
                  statusReportList << """${d.displayName} is ${d.currentValue(attribute)}"""
               }
               else {
                  statusReportList << """${d.displayName} ${customDeviceCapabilities."$capability"?.displayName} is ${d.currentValue(attribute)}"""
               }
            }
         }
      }
   }
   if (statusReportList.size() >= 2) {
      statusReport = statusReportList[0..-2].join(", ") + ", and " + statusReportList[-1]
   }
   else {
      statusReport = statusReportList.join(", ")
   }
   if (statusReport) {
      if (settings["prependText"]) statusReport = replaceVariablesInText(settings["prependText"]) + statusReport
      if (settings["appendText"]) statusReport = statusReport + replaceVariablesInText(settings["appendText"])
      statusReport += "."
   }
   logDebug "Device status list: $statusReportList"
   return statusReport
}

String replaceVariablesInText(String text) {
   String newText = text
   text.findAll(hubVarRegEx).each { String hubVarWithPct ->
      String hubVarName = hubVarWithPct.replaceAll("%", "")
      String hubVarValue = getGlobalVar(hubVarName)?.value ?: ""
      newText = newText.replaceAll(hubVarWithPct, hubVarValue)
   }
   return newText
}

String registerHubVariables() {
   if (settings.allGoodSpeech) {
      registerHubVariablesFromText(settings.allGoodSpeech)
   }
   if (settings.allGoodNotification) {
      registerHubVariablesFromText(settings.allGoodNotification)
   }
   if (settings.prependText) {
      registerHubVariablesFromText(settings.prependText)
   }
   if (settings.appendText) {
      registerHubVariablesFromText(settings.appendText)
   }
   if (settings.ttsVolumeUseVariable && settings.ttsVolumeVariable != null) {
      addInUseGlobalVar(settings.ttsVolumeVariable)
   }
}

String registerHubVariablesFromText(String text) {
   List<String> varNames = []
   text.findAll(hubVarRegEx).each { String hubVarWithPct ->
      String hubVarName = hubVarWithPct.replaceAll("%", "")
      varNames << hubVarName
   }
   addInUseGlobalVar(varNames)
}

void renameVariable(String oldName, String newName) {
   if (settings.allGoodSpeech) app.updateSetting("allGoodSpeech", [type: "text", value: settings.allGoodSpeech.replaceAll("%${oldName}%", "%${newName}%")])
   if (settings.allGoodNotification) app.updateSetting("allGoodNotification", [type: "text", value: settings.allGoodNotification.replaceAll("%${oldName}%", "%${newName}%")])
   if (settings.prependText) app.updateSetting("prependText", [type: "text", value: settings.prependText.replaceAll("%${oldName}%", "%${newName}%")])
   if (settings.appendText) app.updateSetting("appendText", [type: "text", value: settings.appendText.replaceAll("%${oldName}%", "%${newName}%")])
   if (settings.ttsVolumeUseVariable && oldName == settings.ttsVolumeVariable) app.updateSetting("ttsVolumeVariable", [type: "enum", value: newName])
}

/** Sends notification and/or TTS announcement with list of devices in undesired state unless none and not configured to send/speak if none
 *  Also, turn on switches if configured
 */
void doNotificationOrAnnouncement() {
   logDebug "doNotificationOrAnnouncement() called...preparing report."
   String notificationText = getDeviceStatusReport()
   String speechText = "$notificationText"
   if (notificationText) {
      settings["badSwitches"]?.each { it.on() }
      settings["goodSwitches"]?.each { it.off() }
   }
   else {
      settings["goodSwitches"]?.each { it.on() }
      settings["badSwitches"]?.each { it.off() }
   }
   if (!notificationText && allGoodNotification) notificationText = replaceVariablesInText(allGoodNotification)
   if (!speechText && allGoodSpeech) speechText = replaceVariablesInText(allGoodSpeech)
   if (isModeOK()) {
      if (notificationText) {
         logDebug "Sending notification for undesired devices: \"${notificationText}\""
         notificationDevice?.deviceNotification(notificationText)
      }
      else {
         logDebug "Notification skipped: nothing to report"
      }
      if (speechText) {
         logDebug "Doing TTS for undesired devices: \"${speechText}\""
         Integer ttsVol
         if (settings.ttsVolumeUseVariable) {
            ttsVol = getGlobalVar(settings.ttsVolumeVariable)?.value ?: null
         }
         else if (settings.ttsVolume != null) {
            ttsVol = settings.ttsVolume
         }
         // Now, actually speak
         // Using both separaretly for now in case any "legacy" drivers don't like second parameter:
         if (ttsVol != null) speechDevice?.speak(speechText, ttsVol)
         else speechDevice?.speak(speechText)
      }
      else {
         logDebug "TTS skipped: nothing to report"
      }
   }
   else {
      logDebug "Notification/TTS skipped: outside of specified mode(s)"
   }
   notificationText = null
   speechText = null
}

void scheduleHandler() {
   logDebug("At scheduled time; doing notification/TTS if needed")
   doNotificationOrAnnouncement()
}

void presenceAwayHandler(evt) {
   if (evt.value == 'not present') {
      logDebug("${evt.getDisplayName()} went away; doing notificatoin/TTS if needed")
      doNotificationOrAnnouncement()
   }
}

void switchHandler(evt) {
   if (evt.value == 'on') {
      logDebug("${evt.getDisplayName()} turned on; doing notificatoin/TTS if needed")
      doNotificationOrAnnouncement()
   }
}

String styleSection(String sectionHeadingText) {
   return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

Boolean isModeOK() {
   Boolean isOK = !settings["modes"] || settings["modes"].contains(location.mode)
   logDebug "Checking if mode is OK; returning: ${isOK}"
   return isOK
}

private void removeContactLockGroup(String groupNum) {
   logDebug "removeContactLockGroup($groupNum)"
   def settingNamesToRemove = settings?.keySet()?.findAll{ it.startsWith("contactLockGroup_${groupNum}_") }
   logDebug "  Settings to remove: $settingNamesToRemove"
   settingNamesToRemove.each { settingName ->
      app.removeSetting(settingName)
   }
   state.contactLockGroups.removeElement(groupNum as Integer)
   state.remove('removeGroup')
   state.removedGroup = true
   logDebug "Finished removing contact lock group $groupNum"
}


private void removeCustomDeviceGroup(String groupNum) {
   logDebug "removeCustomDeviceGroup($groupNum)"
   def settingNamesToRemove = settings?.keySet()?.findAll{ it.startsWith("customDeviceGroup_${groupNum}_") }
   logDebug "  Settings to remove: $settingNamesToRemove"
   settingNamesToRemove.each { settingName ->
      app.removeSetting(settingName)
   }
   state.customDeviceGroups.removeElement(groupNum as Integer)
   state.remove('removeGroup')
   state.removedGroup = true
   logDebug "Finished removing custom device group $groupNum"
}

void appButtonHandler(btn) {
   switch (btn) {
      case "btnApply":
         break
      case "btnTestNotification":
         doNotificationOrAnnouncement()
         break
      case "btnNewContactLockGroup":
         if (state.contactLockGroups == null) state.contactLockGroups = []
         Integer newMaxGroup = (state.contactLockGroups?.size() > 0) ? ((state.contactLockGroups[-1] as Integer) + 1) : 0
         state.contactLockGroups << newMaxGroup
         break
      case "btnNewCustomDeviceGroup":
         if (state.customDeviceGroups == null) state.customDeviceGroups = []
         Integer newMaxGroup = (state.customDeviceGroups?.size() > 0) ? ((state.customDeviceGroups[-1] as Integer) + 1) : 0
         state.customDeviceGroups << newMaxGroup
         break
      case { it.startsWith("btnRemoveCLGroup.") }:
         String grpNum = btn - "btnRemoveCLGroup."
         removeContactLockGroup(grpNum)
         break
      case { it.startsWith("btnRemoveCustomDeviceGroup.") }:
         String grpNum = btn - "btnRemoveCustomDeviceGroup."
         removeCustomDeviceGroup(grpNum)
         break
      default:
         log.debug "Unhandled button press: $btn"
   }
}

//=========================================================================
// App Methods
//=========================================================================

void installed() {
   log.trace "Installed"
   initialize()
}

void updated() {
   log.trace "Updated"
   unschedule()
   initialize()
}

void initialize() {
   log.trace "Initialized"
   if (settings["debugLogging"]) {
      log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
   }
   unsubscribe()
   removeAllInUseGlobalVar()
   registerHubVariables()
   if (settings["notificationTime"]) schedule(settings["notificationTime"], scheduleHandler) 
   if (settings["announcementSwitch"]) subscribe(settings["announcementSwitch"], "switch", switchHandler)
   if (settings["sensorAway"]) subscribe(settings["sensorAway"], "presence", presenceAwayHandler)
}

/** Writes to log.debug if debug logging setting enabled
  */
void logDebug(string) {
   if (settings["debugLogging"] != false) {
      log.debug string
   }
}