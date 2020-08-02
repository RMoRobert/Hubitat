// NOTE: This app version is deprecated. New installs are NOT recommended.
// Install parent/child 2.x or greater version instead.

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
 * == App version: 1.0.0 ==
 *
 * Changelog:
 * 1.0   (2020-07-25) - First public release
 *
 */

definition(
   name: "Device Status Announcer",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Speak or notify status of locks, contact sensors, and other devices on demand",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-status-announcer-tts-or-notification-if-lock-unlocked-door-window-open-etc/45723"
)

preferences {
	page(name: "pageMain")
	page(name: "pageViewReport")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Device Status Announcer", uninstall: true, install: true) {
		section(styleSection("Choose Devices")) {
			input name: "contactSensors", type: "capability.contactSensor", title: "Choose contact sensors:", multiple: true
			input name: "boolContactOpen", type: "bool", title: "Announce only if sensor(s) open", defaultValue: true, required: true
			input name: "doorLocks", type: "capability.lock", title: "Choose locks:", multiple: true
			input name: "boolDoorUnlocked", type: "bool", title: "Announce only if lock(s) unlocked", defaultValue: true, required: true
			input name: "motionSensors", type: "capability.motionSensor", title: "Choose motion sensors:", multiple: true
			input name: "boolMotionActive", type: "bool", title: "Announce only if sensor(s) active", defaultValue: true, required: true
		}

		section(styleSection("Notification Options")) {
			input name: "speechDevice", type: "capability.speechSynthesis", title: "Announce this device:",  multiple: true
			input name: "notificationDevice", type: "capability.notification", title: "Send notification to this device:", multiple: true
			input name: "notificationTime", type: "time", title: "Daily at this time (optional):"
			paragraph "Or any time this switch is turned on:"
			input name: "announcementSwitch", type: "capability.switch", title: "Switch"
			input name: "allGoodSpeech", type: "text", title: "Text to speak if all devices are OK (blank for no speech if all devices OK):",
				defaultValue: "All devices are OK", required: false			
			input name: "allGoodNotification", type: "text", title: "Notification text to send if all devices are OK (blank for no notification if all devices OK):",
				defaultValue: "", required: false
		}

		section(styleSection("View/Test Report")) {
			href(name: "pageViewReportHref",
					page: "pageViewReport",
					title: "View current report",
					description: "Evaluate all devices now according to the criteria above, and display a report of devices in undesired state (the same information that would be spoken or sent in a real notification/announcement).")
			paragraph "The \"Test Announcement/Notification Now\" button will send a TTS announcement and/or notification to your selected device(s) if any current device states and options would cause an annoucement or notification. This a manual method to trigger the same actions the above options can automate:"
			input name: "btnTestNotification", type: "button", title: "Test Announcement/Notification Now"
		}
		
		section("Advanced Options", hideable: true, hidden: true) {
			label title: "Customize installed app name:", required: true
			input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
			input "modes", "mode", title: "Only make announcements/notifications when mode is", multiple: true, required: false
			input name: "debugLogging", type: "bool", title: "Enable debug logging" 
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
	String statusReport = ""
	contactSensors?.each {
		if (boolContactOpen != false) {
			log.warn "${it.displayName} is ${it.currentValue("contact")}. "
			if (it.currentValue("contact") == "open" && (it.isDisabled() == false || settings["boolIncludeDisabled"])) statusReport += "${it.displayName} is open. "
		}
		else {
			 if (it.isDisabled() == false || settings["boolIncludeDisabled"]) statusReport += "${it.displayName} is ${it.currentValue("contact")}. "
		}
	}
	doorLocks?.each {
		if (boolDoorUnlocked != false) {
			if (it.currentValue("lock") == "unlocked" && (it.isDisabled() == false || settings["boolIncludeDisabled"])) statusReport += "${it.displayName} is unlocked. "
		}
		else {
			 if (it.isDisabled() == false || settings["boolIncludeDisabled"]) statusReport += "${it.displayName} is ${it.currentValue("lock")}. "
		}
	}
	motionSensors?.each {
		if (boolMotionActive != false) {
			if (it.currentValue("motion") == "active" && (it.isDisabled() == false || settings["boolIncludeDisabled"])) statusReport += "${it.displayName} is active. "
		}
		else {
			 if (it.isDisabled() == false || settings["boolIncludeDisabled"]) statusReport += "${it.displayName} is ${it.currentValue("motion")}. "
		}
	}
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

void switchHandler(evt) {
	if (evt.value == 'on') {
		logDebug("Switch turned on; doing notificatoin/TTS if needed")
		doNotificationOrAnnouncement()
	}
}

String styleSection(String sectionHeadingText) {
	return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
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
	if (settings['debugLogging']) {
		log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
	}

	unsubscribe()
	if (settings['notificationTime']) schedule(settings['notificationTime'], scheduleHandler)	
	if (settings['announcementSwitch']) subscribe(settings['announcementSwitch'], 'switch', switchHandler)
}

Boolean isModeOK() {
    Boolean isOK = !settings['modes'] || settings['modes'].contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${isOK}"
    return isOK
}

def appButtonHandler(btn) {
	switch (btn) {
		case "btnTestNotification":
			sendInactiveNotification()
			break
		default:
			log.debug "Unhandled button press: $btn"
	}
}

/** Writes to log.debug if debug logging setting enabled
  */
void logDebug(string) {
	if (settings['debugLogging'] == true) {
        log.debug string
    }
}