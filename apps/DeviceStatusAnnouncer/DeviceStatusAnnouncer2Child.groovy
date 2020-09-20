/**
 * ==========================  Device Status Announcer ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2020 Robert Morris
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
 * == App version: 2.5.0 ==
 *
 * Changelog:
 * 2.5   (2020-09-20) - Added thermostats and contact/lock "name grouping"
 * 2.0.1 (2020-08-02) - Made easier to remove "all OK" notification/TTS if desired
 * 2.0   (2020-08-02) - New parent/child strucutre, additional notification options
 * 1.0   (2020-07-25) - First public release
 *
 */

definition(
   name: "Device Status Announcer Child 2.x",
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
   page(name: "pageMain")
   page(name: "pageRemoveGroup")
   page(name: "pageViewReport")
}

def pageMain() {
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
         input name: "motionSensors", type: "capability.motionSensor", title: "Choose motion sensors:", multiple: true
         input name: "boolMotionActive", type: "bool", title: "Announce only if sensor(s) active", defaultValue: true
         input name: "thermostats", type: "capability.thermostat", title: "Choose thermostats:", submitOnChange: true, multiple: true
         if (settings["thermostats"]) {
            input name: "thermostatCoolThreshold", type: "number", title: "if cooling setpoint below:", width: 6        
            input name: "thermostatHeatThreshold", type: "number", title: "if heating setpoint above:", width: 6
            paragraph "(heating and cooling setpoints will be evaluated only when thermostat in heating or cooling mode, respectively)"
         }
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
               input name: "contactLockGroup.${it}.devs", type: "enum", title: "Devices to group into single name:", multiple: true, submitOnChange: true,
                  options: allContactsAndLocks, width: 5
               if (settings["contactLockGroup.${it}.devs"]) {
                  input name: "contactLockGroup.${it}.name", type: "text", title: "Name for this group:", width: 4
                  href name: "contactLockGroup.${it}.hrefRemove", page: "pageRemoveGroup", title: "Remove", description: "", params: [groupNumber: it], width: 3
               }
            }
            if (state.contactLockGroups?.size() > 0 && settings["contactLockGroup.${state.contactLockGroups[-1]}.name"] && settings["contactLockGroup.${state.contactLockGroups[-1]}.devs"]) {
               input name: "btnNewContactLockGroup", type: "button", title: "New Group", submitOnChange: true
            }
         }
      }

      section(styleSection("Notification Options")) {
         input name: "speechDevice", type: "capability.speechSynthesis", title: "Announce this device:",  multiple: true
         input name: "notificationDevice", type: "capability.notification", title: "Send notification to this device:", multiple: true
         input name: "notificationTime", type: "time", title: "Daily at this time (optional):"
         input name: "sensorAway", type: "capability.presenceSensor", title: "Or any time this presence sensor becomes not present", multiple: true
         paragraph "Or any time this switch is turned on:"
         input name: "announcementSwitch", type: "capability.switch", title: "Switch"
         input name: "allGoodSpeech", type: "text", title: "Text to speak if all devices are OK (blank for no speech if all devices OK):",
            defaultValue: (app.getInstallationState() == "INCOMPLETE" ? "All devices are OK" : ""), required: false
         input name: "allGoodNotification", type: "text", title: "Notification text to send if all devices are OK (blank for no notification if all devices OK):",
            defaultValue: "", required: false
         input name: "prependText", type: "text", title: "Text to prepend to announcements/notifications (optional)",
            defaultValue: ""
         input name: "appendText", type: "text", title: "Text to append to announcements/notifications (optional)",
            defaultValue: ""
      }

      section(styleSection("View/Test Report")) {
         href(name: "pageViewReportHref",
               page: "pageViewReport",
               title: "View current report",
               description: "Evaluate all devices now according to the criteria above, and display a report of devices in undesired state (the same information that would be spoken or sent in a real notification/announcement).")
         paragraph "The \"Test Announcement/Notification Now\" button will send a TTS announcement and/or notification to your selected device(s) if any current device states and options would cause an annoucement or notification. (Note: if you have changed options since you last loaded this page, press \"Done\" to save settings and re-enter the app before testing.) This a manual method to trigger the same actions the above options can automate:"
         input name: "btnTestNotification", type: "button", title: "Test Announcement/Notification Now", submitOnChange: true
      }
      
      section("Advanced Options", hideable: true, hidden: true) {
         input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
         input "modes", "mode", title: "Only make announcements/notifications when mode is", multiple: true, required: false
         input name: "debugLogging", type: "bool", title: "Enable debug logging" 
      }
   }
}

def pageRemoveGroup(params) {
   dynamicPage(name: "pageRemoveGroup", title: "Remove Group", uninstall: false, install: false, nextPage: "pageMain") {
      if (params?.groupNumber != null) state.removeGroup = params.groupNumber
      section("") {
         if (state.removeGroup != null && state.removedGroup != true) {
            paragraph "Press the button below to confirm removal of this group. Press \"Next\" to continue without removing."
            input name: "contactLockGroup.${state.removeGroup}.btnRemove", type: "button", title: "Remove Group", submitOnChange: true
         }
         else {
            if (state.removedGroup) paragraph "Group removed. Press \"Next\" to continue."
            else paragraph "Unknown group removal status. Try again if it did not suceed. Press \"Next\" to continue."
         }
      }
   }
}

def pageViewReport() {
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

String getDeviceStatusReport() {
   logDebug "Generating device status report..."
   List<String> statusReportList = []
   String statusReport = ""
   com.hubitat.app.DeviceWrapperList contacts = settings["contactSensors"]
   com.hubitat.app.DeviceWrapperList locks = settings["doorLocks"]
   com.hubitat.app.DeviceWrapperList motions = settings["motionSensors"]
   if (!(settings["boolIncludeDisabled"])) {
      contacts = contacts?.findAll { it.isDisabled != true }
      locks = locks?.findAll { it.isDisabled != true }
      motions = motions?.findAll { it.isDisabled != true }
   }
   state.contactLockGroups?.each { groupId ->
      if (settings["contactLockGroup.${groupId}.devs"] && settings["contactLockGroup.${groupId}.name"]) {
         List<String> groupState = []
         if (boolContactOpen != false && boolDoorUnlocked != false) {
            contacts.find { it.id in settings["contactLockGroup.${groupId}.devs"] && it.currentValue("contact") == "open" }?.each {
               groupState << "open"
               contacts.remove(it)
            }
            locks.find { it.id in settings["contactLockGroup.${groupId}.devs"] && it.currentValue("lock") == "unlocked" }?.each {
               groupState << "unlocked"
               locks.remove(it)
            }
         }
         else {
            contacts.find { it.id in settings["contactLockGroup.${groupId}.devs"]}?.each {
               groupState << it.currentValue("contact")
               contacts.remove(it)
            }
            locks.find { it.id in settings["contactLockGroup.${groupId}.devs"] }?.each {
               groupState << it.currentValue("lock")
               locks.remove(it)
            }
         }
         if (groupState.size() > 0) {
            statusReportList << """${settings["contactLockGroup.${groupId}.name"]} is ${groupState.join(" and ")}"""
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
   motions?.each {
      if (boolMotionActive != false) {
         if (it.currentValue("motion") == "active")  statusReportList << "${it.displayName} is active"
      }
      else {
         statusReportList << "${it.displayName} is ${it.currentValue("motion")}"
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

   if (statusReportList.size() >= 2) {
      statusReport = statusReportList[0..-2].join(", ") + ", and " + statusReportList[-1]
   }
   else {
      statusReport = statusReportList.join(", ")
   }
   if (statusReport) {
      if (settings["prependText"]) statusReport = settings["prependText"] + statusReport
      if (settings["appendText"]) statusReport = statusReport + settings["appendText"]
      statusReport += "."
   }
   logDebug "Device status list: $statusReportList"
   return statusReport
}

// Sends notification and/or TTS announcement with list of devices in undesired state unless none and not configured to send/speak if none
void doNotificationOrAnnouncement() {
   logDebug "doNotificationOrAnnouncement() called...preparing report."
   String notificationText = getDeviceStatusReport()
   String speechText = "$notificationText"
   if (!notificationText && allGoodNotification) notificationText = allGoodNotification
   if (!speechText && allGoodSpeech) speechText = allGoodSpeech
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
         speechDevice?.speak(speechText)
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
	def settingNamesToRemove = settings?.keySet()?.findAll{ it.startsWith("contactLockGroup.${groupNum}") }
	logDebug "  Settings to remove: $settingNamesToRemove"
	settingNamesToRemove.each { settingName ->
		app.removeSetting(settingName)
	}
   state.contactLockGroups.removeElement(groupNum as Integer)
   state.remove('removeGroup')
   state.removedGroup = true
   logDebug "Finished removing contact lock group $groupNum"
}

def appButtonHandler(btn) {
   switch (btn) {
      case "btnTestNotification":
         doNotificationOrAnnouncement()
         break
      case "btnNewContactLockGroup":
			if (state.contactLockGroups == null) state.contactLockGroups = [0]
			Integer newMaxGroup = (state.contactLockGroups?.size() > 0) ? ((state.contactLockGroups[-1] as Integer) + 1) : 0
         state.contactLockGroups << newMaxGroup
         break
      case { it.startsWith("contactLockGroup.") }:
         String clGrpNum = btn - "contactLockGroup." - ".btnRemove"
         removeContactLockGroup(clGrpNum)
         break
      default:
         log.debug "Unhandled button press: $btn"
   }
}

//=========================================================================
// App Methods
//=========================================================================

def installed() {
    log.trace "Installed"
    initialize()
}

def updated() {
    log.trace "Updated"
    unschedule()
    initialize()
}

def initialize() {
   log.trace "Initialized"
   if (settings["debugLogging"]) {
      log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
   }

   unsubscribe()
   if (settings["notificationTime"]) schedule(settings["notificationTime"], scheduleHandler) 
   if (settings["announcementSwitch"]) subscribe(settings["announcementSwitch"], "switch", switchHandler)
   if (settings["sensorAway"]) subscribe(settings["sensorAway"], "presence", presenceAwayHandler)
}

/** Writes to log.debug if debug logging setting enabled
  */
void logDebug(string) {
   if (settings["debugLogging"] == true) {
        log.debug string
    }
}