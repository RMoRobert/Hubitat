/**
 * ==========================  Device Activity Check ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2021 Robert Morris
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
 * 1.4   (2021-04-05) - Added more refresh options; added link to device pages on "manual" report page
 * 1.3   (2020-12-18) - Added ability to refresh selected devices before report, ability to select multiple notification
 *                      devices, and better default date/time formatting on "View current report" page
 * 1.2   (2020-07-28) - Added presence checking (in addition to activity)
 * 1.1   (2020-06-11) - Added ability to ignore disabled devices (on by default)
 * 1.0.1 (2020-06-04) - Minor bugfix (eliminates errors for empty groups or if notification device is not selected)
 * 1.0   (2020-05-27) - First public release
 *
 */

import groovy.transform.Field

@Field static List dateFormatOptions = ['MMM d, yyyy, h:mm a', 'E, MMM d, yyyy, h:mm a', 'E dd MMM yyyy, h:mm a', 'dd MMM yyyy, h:mm a',
                                        'dd MMM yyyy HH:mm', 'E MMM dd HH:mm', 'yyyy-MM-dd HH:mm z']
@Field static Integer formatListIfMoreItemsThan = 4

definition(
   name: "Device Activity Check",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Identify devices without recent activity that may have stopped working or \"fallen off\"  your network",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-activity-check-get-notifications-for-inactive-devices/42176"
)

preferences {
	page(name: "pageMain")
	page(name: "pageDeviceGroup")
	page(name: "pageRemoveGroup")
	page(name: "pageViewReport")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Device Activity Check", uninstall: true, install: true) {
		if (!(state.groups)) state.groups = [1]
		List groups = state.groups ?: [1]
		if (state.removeSettingsForGroupNumber) { 
			Integer groupNum = state.removeSettingsForGroupNumber
			state.remove("removeSettingsForGroupNumber")
			removeSettingsForGroupNumber(groupNum)
			state.groups?.removeElement(groupNum)
		}
		state.remove("cancelDelete")
		state.remove("currGroupNum")
      state.remove("currGroupDispNum")
      app.removeSetting("debugLogging") // from 1.1.0 and earlier; can probably remove in future
		
		String strSectionTitle = (state.groups.size() > 1) ? "Device Groups" : "Devices"
		section(styleSection(strSectionTitle)) {
			groups.eachWithIndex { realGroupNum, groupIndex ->
				String timeout = getDeviceGroupInactivityThresholdString(realGroupNum)
				String strTitle = (state.groups.size() > 1) ? "Group ${groupIndex+1} Devices (inactivity threshold: $timeout):" : "Devices (inactivity threshold: $timeout):"
				href(name: "pageDeviceGroup${realGroupNum}Href",
				     page: "pageDeviceGroup",
					 params: [groupNumber: realGroupNum, groupDispNumber: groupIndex+1],
					 title: strTitle,
					 description: getDeviceGroupDescription(realGroupNum) ?: "Click/tap to choose devices and inactivity threshold...",
					 state: getDeviceGroupDescription(realGroupNum) ? "complete" : null)
				}
			paragraph("To monitor another set of devices with a different inactiviy threshold or method, add a new group:")
			input name: "btnNewGroup", type: "button", title: "Add new group"
		}

		section(styleSection("Notification Options")) {
			input name: "notificationDevice", type: "capability.notification", title: "Send notification with list of inactive devices to this device:", multiple: true
			input name: "notificationTime", type: "time", title: "Daily at this time:"
			paragraph "Or any time this switch is turned on:"
			input name: "notificationSwitch", type: "capability.switch", title: "Switch", description: "Optional - Click to set"
			input name: "includeTime", type: "bool", title: "Include last acitivty time in notifications ", defaultValue: true, submitOnChange: true
         // Would be nice to consider for future:
         //if (includeTime) input name: "inclueNotPresentTime", type: "bool", title: "Use date of last \"not present\" event if device(s) configured for presence monitoring", defaultValue: true, submitOnChange: true
			if (!settings["includeTime"] == false) {
				List<Map<String,String>> timeFormatOptions = []
				Date currDate = new Date()
				dateFormatOptions.each { 
					timeFormatOptions << ["$it": "${currDate.format(it, location.timeZone)}"]
				}
				input name: "timeFormat", type: "enum", options: timeFormatOptions, title: "Date/time format for notifications:",
					defaultValue: timeFormatOptions[0]?.keySet()[0], required: (!settings["includeTime"] == false), submitOnChange: true
			}
		}

		section(styleSection("View/Test Report")) {
			href(name: "pageViewReportHref",
              page: "pageViewReport",
              title: "View current report",
              description: "Evaluate all devices now according to the criteria above, and display a report of \"inactive\" devices.")
			paragraph "The \"Text Notification Now\" button will send a notification to your selected device(s) if there is inactivity to report. This a manual method to trigger the same report the above options would also create:"
			input name: "btnTestNotification", type: "button", title: "Test Notification Now"
		}
		
		section("Advanced Options", hideable: true, hidden: true) {
         label title: "Customize installed app name:", required: true
         input name: "includeHubName", type: "bool", title: "Include hub name in reports (${location.name})"
         input name: "useNotificationTimeFormatForReport", type: "bool", title: 'Use "Date/time format for notifications" for "View current report" dates/times (default is YYYY-MM-dd hh:mm a z)'
         input name: "refreshBeforeViewReport", type: "bool", title: "If configured to refresh devices before sending inactivity notification, also refresh before \"View current report\" (default is no; enabling may cause delay loading that page)"
         input name: "modes", type: "mode", title: "Only send notifications when mode is", multiple: true, required: false
         input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
         input name: "debugLevel", type: "enum", title: "Debug logging level:", options: [[0: "Logs off"], [1: "Debug logging"], [2: "Verbose logging"]],
            defaultValue: 0
		}
	}
}

def pageDeviceGroup(params) {
	Integer groupNum
   Integer groupDispNum
   String strTitle
   if (params?.groupNumber) {
      state.currGroupNum = params.groupNumber
      groupNum = params.groupNumber
   }
   else {
      groupNum = state.currGroupNum
   }
   if (params?.groupDispNumber) {
      state.currGroupDispNum = params.groupDispNumber
      groupDispNum = params.groupDispNumber
   }
   else {
      groupNum = state.currGroupDispNum
   }
	strTitle = (state.groups?.size() > 1) ? "Device Group ${groupDispNum}:" : "Devices"   
	state.remove("cancelDelete")

   dynamicPage(name: "pageDeviceGroup", title: strTitle, uninstall: false, install: false, nextPage: "pageMain") {
      section(styleSection("Choose Devices")) {
         input name: "group${groupNum}.devices", type: "capability.*", multiple: true, title: "Select devices to monitor", submitOnChange: true
      }
      section(styleSection("Inactivity Threshold")) {
         input name: "group${groupNum}.inactivityMethod", title: "Inactivity detection method:", type: "enum",
            options: [["activity": "\"Last Activity\" timestamp"], ["presence": "\"Presence\" attribute"]],
            defaultValue: "activity", required: true, submitOnChange: true
         if (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null) {
            paragraph "Consider above devices inactive if they have not had activity within..."
            input name: "group${groupNum}.intervalD", type: "number", title: "days",
               description: "", submitOnChange: true, width: 2
            input name: "group${groupNum}.intervalH", type: "number", title: "hours",
               description: "", submitOnChange: true, width: 2
            input name: "group${groupNum}.intervalM", type: "number", title: "minutes*",
               description: "", submitOnChange: true, width: 2
            paragraph """${(settings["group${groupNum}.intervalD"] || settings["group${groupNum}.intervalH"] || settings["group${groupNum}.intervalM"]) ?
               '<strong>Total time:</strong>\n' + daysHoursMinutesToString(settings["group${groupNum}.intervalD"], settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"]) :
               ''}""", width: 6
            if (!(settings["group${groupNum}.intervalD"] || settings["group${groupNum}.intervalH"] || settings["group${groupNum}.intervalM"])) {
               paragraph "*At least one of: days, hours, or minutes is required"
            }
         }
         else if (settings["group${groupNum}.inactivityMethod"] == "presence") {
            paragraph "Devices will be considered inactive if the value of the \"presence\" attribute is \"not present\" at the time " +
               "of evaluation. Note that if your report is configured to display the \"Last Activity\" date, this date/time may not " +
               "necessarily correspond to actual device communication, depending on how the device driver works."
            if (settings["group${groupNum}.devices"]?.any { !(it.hasAttribute("presence")) }) {
               paragraph "<strong>Warning: the following devices do not report a \"presence\" attribute. De-select them or verify the correct driver or inactivity " +
                  """detection method:</strong> ${settings["group${groupNum}.devices"]?.findAll { !(it.hasAttribute("presence")) }.join(", ")}"""
            }
         }
         else {
            paragraph "Please select a valid \"inactivity detection method\" option above."
         }
      }
      if (settings["group${groupNum}.devices"] && (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null)) {
         section(styleSection("Refresh Options")) {
            List<Map<Long, String>> rdevList = []
            settings["group${groupNum}.devices"].each {
               if (it.hasCommand("refresh")) rdevList << [(it.getIdAsLong()): it.getDisplayName()]
            }
            // Sort by device display name:
            rdevList.sort { a, b -> a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value }
            paragraph "Use the option below only if you have devices that do not regularly \"check in\" and respond to a refresh. (You must know your specific device behavior, but generally all powered devices and battery-powered Zigbee devices will respond, while non-FLiRS Z-Wave battery devices will not.) Device Activity Check will send a \"Refresh\" command to the device before running a report, which may give a better indication of whether the device is still responsive. Be careful not to choose too many devices or devices that do not respond on-demand to refresh commands."
            input name: "group${groupNum}.refreshDevs", title: "Refresh before running report:", type: "enum", options: rdevList, multiple: true, submitOnChange: true
            if (settings["group${groupNum}.refreshDevs"]) {
               paragraph "<strong>Note:</strong> If any devices need to be refreshed, reports will be delayed for one-half second per device plus an additional five seconds in order to give devices time to respond (so the \"Test\" on the previous page may not be instant)."
            }
         }
      }
      section(styleSection("Remove Group")) {
         href(name: "pageRemoveGroupHref",
              page: "pageRemoveGroup",
              title: "Remove this group",
              params: [deleteGroupNumber: groupNum],
              description: "Warning: this will delete all selected devices and settings for this group.")
      }
	}
}

def pageRemoveGroup(params) {
   logDebug("pageRemoveGroup with parameters $params...")
   Integer groupNum = params?.deleteGroupNumber	
   if (groupNum && !(state.cancelDelete)) {
      state.remove("lastGroupNum")
      state.removeSettingsForGroupNumber = groupNum
   }
   dynamicPage(name: "pageRemoveGroup", title: "Remove Group", uninstall: false, install: false, nextPage: "pageMain") {
      section() {
         if (!(state.cancelDelete)) {
            paragraph("Press \"Next\" to complete the deletion of this group.")
            input name: "btnCancelGroupDelete", type: "button", title: "Cancel"
         }
         else {				
            paragraph("Deletion cancelled. Press \"Next\" to continue.")
         }
      }
   }
}

def pageViewReport() {
   logDebug "Loading \"View current report\" page... (if you have device refresh enabled, this may take several seconds)"
	dynamicPage(name: "pageViewReport", title: "Device Activity Check", uninstall: false, install: false, nextPage: "pageMain") {
		section(styleSection("Inactive Device Report")) {
			List<com.hubitat.app.DeviceWrapper> inactiveDevices = getInactiveDevices(settings["refreshBeforeViewReport"] ?: false)
			if (inactiveDevices) {
				paragraph "<strong>Device</strong>", width: 6
				paragraph "<strong>Last Activity</strong>", width: 6
				Boolean doFormatting = inactiveDevices.size() > formatListIfMoreItemsThan
				inactiveDevices.eachWithIndex { dev, index ->
					String lastActivity
					if (settings["useNotificationTimeFormatForReport"]) {
						lastActivity = dev.getLastActivity()?.format(settings["timeFormat"] ?: "MMM dd, yyyy h:mm a", location.timeZone)
					}
               else {                  
						lastActivity = dev.getLastActivity()?.format("yyyy-MM-dd hh:mm a z", location.timeZone)
               }
					if (!lastActivity) lastActivity = "No reported activity"
					paragraph(doFormatting ? """<a href="/device/edit/${dev.id}">${styleListItem(dev.displayName, index)}</a>""" : """<a href="/device/edit/${dev.id}">${dev.displayName}</a>""", width: 6)
					//paragraph(doFormatting ? "${styleListItem(dev.displayName, index)}" : "${dev.displayName}", width: 6)
					paragraph(doFormatting ? "${styleListItem(lastActivity, index)}" : lastActivity, width: 6)
				}
			}
			else {
				paragraph "No inactive devices to report"
			}
		}
	}
}

List<com.hubitat.app.DeviceWrapper> getInactiveDevices(Boolean sortByName=true) {
   logDebug "getInactiveDevices()...", "trace"
	List<Integer> groups = state.groups ?: [1]
	List<com.hubitat.app.DeviceWrapper> inactiveDevices = []
	List<com.hubitat.app.DeviceWrapper> toRefreshDevices = []
	Long currEpochTime = now()
   Closure inactivityDetectionClosure
   Closure disabledCheckClosure = { com.hubitat.app.DeviceWrapper dev -> !(dev.isDisabled()) || !(settings["boolIncludeDisabled"]) }
	groups.each { groupNum ->
		List allDevices = settings["group${groupNum}.devices"] ?: []
      // For "Last Activity at" devices:
      if (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null) {
         Integer inactiveMinutes = daysHoursMinutesToMinutes(settings["group${groupNum}.intervalD"],
            settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
         Long cutoffEpochTime = currEpochTime - (inactiveMinutes * 60000)
         inactivityDetectionClosure = { Long cutoffTime, com.hubitat.app.DeviceWrapper dev ->
            dev.getLastActivity()?.getTime() <= cutoffTime &&
            disabledCheckClosure(dev)
         }
         inactivityDetectionClosure = inactivityDetectionClosure.curry(cutoffEpochTime)
         if (settings["group${groupNum}.refreshDevs"] && (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null)) {
            toRefreshDevices += settings["group${groupNum}.devices"]?.findAll { settings["group${groupNum}.refreshDevs"].contains (it.getId()) }
         }
      }
      // For presence-based devices:
      else if (settings["group${groupNum}.inactivityMethod"] == "presence") {
         inactivityDetectionClosure = { com.hubitat.app.DeviceWrapper dev ->
            dev.currentValue("presence") != "present" &&
            disabledCheckClosure(dev)
         }
      }
      // Shouldn't happen, but warn if does:
      else {
         log.warn "Unsupported inactivity detection method for group ${groupNum}; skipping"
      }
      // Finally, add inactive devices to list:
      inactiveDevices.addAll(allDevices?.findAll(inactivityDetectionClosure))
	}
	if (sortByName) inactiveDevices = inactiveDevices.sort { it.displayName }
   inactivityDetectionClosure = null
   disabledCheckClosure = null
   logDebug "getInactiveDevices() returning: $inactiveDevices", "trace"
	return inactiveDevices
}

void performRefreshes() {
   logDebug "performRefreshes()...", "trace"
	List<Integer> groups = state.groups ?: [1]
	List<com.hubitat.app.DeviceWrapper> inactiveDevices = []
	List<com.hubitat.app.DeviceWrapper> toRefreshDevices = []
	Long currEpochTime = now()
   Closure inactivityDetectionClosure
   Closure disabledCheckClosure = { com.hubitat.app.DeviceWrapper dev -> !(dev.isDisabled()) || !(settings["boolIncludeDisabled"]) }
	groups.each { groupNum ->
		List allDevices = settings["group${groupNum}.devices"] ?: []
      // For "Last Activity at" devices:
      if (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null) {
         Integer inactiveMinutes = daysHoursMinutesToMinutes(settings["group${groupNum}.intervalD"],
            settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
         Long cutoffEpochTime = currEpochTime - (inactiveMinutes * 60000)
         inactivityDetectionClosure = { Long cutoffTime, com.hubitat.app.DeviceWrapper dev ->
            dev.getLastActivity()?.getTime() <= cutoffTime &&
            disabledCheckClosure(dev)
         }
         inactivityDetectionClosure = inactivityDetectionClosure.curry(cutoffEpochTime)
         if (settings["group${groupNum}.refreshDevs"] && (settings["group${groupNum}.inactivityMethod"] == "activity" || settings["group${groupNum}.inactivityMethod"] == null)) {
            toRefreshDevices += settings["group${groupNum}.devices"]?.findAll { settings["group${groupNum}.refreshDevs"].contains (it.getId()) }
         }
      }
      // Finally, add inactive devices to list:
      inactiveDevices.addAll(allDevices?.findAll(inactivityDetectionClosure))
	}
   if (refreshSelectedDevices && toRefreshDevices) {
      List<com.hubitat.app.DeviceWrapper> toActuallyRefresh = toRefreshDevices.intersect(inactiveDevices) ?: []
      logDebug "Devices to refresh: $toActuallyRefresh"
      toActuallyRefresh.each {
         try {
            it.refresh()
            pauseExecution(400)
         }
         catch (Exception ex) {
            log.warn "Could not refresh $it; $ex"
         }
      }
      pauseExecution(toActuallyRefresh.size() * 100 + 5000)
   }
   inactivityDetectionClosure = null
   disabledCheckClosure = null
}


// Lists all devices in group, one per line
String getDeviceGroupDescription(groupNum) {
   logDebug "getDeviceGroupDescription($groupNum)...", "trace"
	String desc = ""
	if (settings["group${groupNum}.devices"]) {
		settings["group${groupNum}.devices"].each { dev ->
			desc += "${dev.displayName}\n"
		}
	}
	return desc
}

// Human-friendly string for inactivity period (e.g., "1 hour, 15 minutes")
String getDeviceGroupInactivityThresholdString(groupNum) {
   logDebug "getDeviceGroupInactivityThresholdString($groupNum)...", "trace"
   String thresholdString = ""   
   if (settings["group${groupNum}.inactivityMethod"] == "activity" || !settings["group${groupNum}.inactivityMethod"]) {
      thresholdString = daysHoursMinutesToString(settings["group${groupNum}.intervalD"],
		   settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
   }
	else if (settings["group${groupNum}.inactivityMethod"] == "presence") {
      thresholdString = "if not present"
   }
   else {
      thresholdString = "(invalid configuration; please verify)"
   }
   return thresholdString
}

void removeSettingsForGroupNumber(Integer groupNumber) {
	logDebug "Removing settings for group $groupNumber..."
	def settingNamesToRemove = settings?.keySet()?.findAll{ it.startsWith("group${groupNumber}.") }
	logDebug "  Settings to remove: $settingNamesToRemove"
	settingNamesToRemove.each { settingName ->
		app.removeSetting(settingName)
	}
}

Integer daysHoursMinutesToMinutes(days, hours, minutes) {
	Integer totalMin = (minutes ? minutes : 0) + (hours ? hours * 60 : 0) + (days ? days * 1440 : 0)
	return totalMin
}

String daysHoursMinutesToString(days, hours, minutes) {
	Integer totalMin = daysHoursMinutesToMinutes(days, hours, minutes)
	Integer d = totalMin / 1440 as Integer
	Integer h = totalMin % 1440 / 60 as Integer
	Integer m = totalMin % 60
	String strD = "$d day${d != 1 ? 's' : ''}"
	String strH = "$h hour${h != 1 ? 's' : ''}"
	String strM = "$m minute${m != 1 ? 's' : ''}"
	return "${d ? strD : ''}${d && (h || m) ? ', ' : ''}${h ? strH : ''}${(h && m) ? ', ' : ''}${m || !(h || d) ? strM : ''}"
}

String styleSection(String sectionHeadingText) {
	return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

void switchHandler(evt) {
	if (evt.value == "on") {
		logDebug("Switch turned on; running report")
		sendInactiveNotification()
	}
}

// Sends notification with list of inactive devices to selected notification device(s)
void sendInactiveNotification(Boolean includeLastActivityTime=(settings["includeTime"] != false)) {
	logDebug "sendInactiveNotification($includeLastActivityTime) called..."
   if (doRefresh) {
      performRefreshes()
      pauseExecution(500)
   }
   logDebug "Preparing list of inactive devices..."
	List<com.hubitat.app.DeviceWrapper> inactiveDevices = getInactiveDevices(true, true) 
	String notificationText = ""
	if (inactiveDevices && isModeOK()) {
		notificationText += (settings["includeHubName"] ? "${app.label} - ${location.name}:" : "${app.label}:")		
		inactiveDevices.each { dev ->
			notificationText += "\n${dev.displayName}"
			if (includeLastActivityTime) {
				String dateString = dev.getLastActivity()?.format(settings["timeFormat"] ?: 'MMM dd, yyyy h:mm a', location.timeZone) ?: 'No activity reported'
				notificationText += " - $dateString"
			}
		}		
		logDebug "Sending notification for inactive devices: \"$notificationText\""
		notificationDevice?.each {
         it.deviceNotification(notificationText)
      }
	}
	else {
		String reason = "Notification skipped: "
		if (inactiveDevices) reason += "No inactive devices. "
		if (!isModeOK()) reason += "Outside of specified mode(s)."
		logDebug reason
	}
}

// For list items in report page
String styleListItem(String text, index=0) {
	return """<div style="color: ${index %2 == 0 ? "darkslategray" : "black"}; background-color: ${index %2 == 0 ? 'white' : 'ghostwhite'}">$text</div>"""
}

void scheduleHandler() {
	logDebug("At scheduled; running report")
	sendInactiveNotification()
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
	if (settings["debugLevel"] && settings["debugLevel"] as Integer != 0) {
		log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
	}

	unsubscribe()
	if (settings["notificationTime"]) schedule(settings["notificationTime"], scheduleHandler)	
	if (settings["notificationSwitch"]) subscribe(settings["notificationSwitch"], "switch", switchHandler)
}

Boolean isModeOK() {
    Boolean isOK = !settings["modes"] || settings["modes"].contains(location.mode)
    logDebug "Checking if mode is OK; reutrning: ${isOK}", "trace"
    return isOK
}

def appButtonHandler(btn) {
	switch (btn) {
		case "btnNewGroup":
			Integer newMaxGroup = (state.groups[-1]) ? ((state.groups[-1] as Integer) + 1) : 2
			state.groups << newMaxGroup
			break
		case "btnCancelGroupDelete":
			state.cancelDelete = true
			state.remove("removeSettingsForGroupNumber")
			break
		case "btnTestNotification":
			sendInactiveNotification()
			break
	}
}

/** Writes to log.debug by default if debug logging setting enabled; can specify
  * other log level (e.g., "info") if desired
  */
void logDebug(string, level="debug") {
   switch(level) {
      case "trace": 
         if (settings["debugLevel"] as Integer != null && (settings["debugLevel"] as Integer) == 2) log.trace(string)
         break
      default:         
        if (settings["debugLevel"] as Integer != null && (settings["debugLevel"] as Integer) >= 1) log."$level"(string)
   }
}