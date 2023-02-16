/**
 * ==========================  Device Activity Check ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2023 Robert Morris
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
 * 2.1.2 (2023-02-15) - Fix snooze time calcuations
 * 2.1.1 (2023-02-11) - Remove accidental extra logging
 * 2.1   (2023-02-05) - Add healthStatus attribute option
 *                    - Add buttons as trigger for running report; add battery notes and snooze options for devices
 * 2.0.1 (2022-06-19) - Fix for error on platform 2.3.2 (thanks to @jtp10181 for spotting the issue)
 * 2.0   (2022-01-02) - Improved reports for non-activity-based methods (shows attribute value);
 *                      Filter device-selection list based on detection method
 *                      New date format options for report vs. notifications
 *                      Improved refresh behavior (if option selected)
 * 1.5.1 (2021-09-21) - Filter device list from detection method
 * 1.5   (2021-09-19) - Added battery as notification/report type; log if device checking with unsupported attribute
 * 1.4.7 (2021-08-20) - Fixed for missing dates on some reports
 * 1.4.6 (2021-08-18) - Eliminated spurious warning message in logs when using presence-based detection and refresh
 * 1.4.5 (2021-08-13) - Improvements to refresh behavior (runIn instead of pauseExecution); fix for inactivity thresholds >24 days
 * 1.4.4 (2021-06-07) - Fix for hours/minutes display when calculating total time for threshold (cosmetic issue only)
 * 1.4.3 (2021-06-06) - Fix for possible NPE when checking "presence"-based devices
 * 1.4.2 (2021-05-28) - Fix for device refresh; minor code cleanup
 * 1.4.1 (2021-04-06) - Fixed error when running report notification
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
import com.hubitat.app.DeviceWrapper

@Field static final String defaultDateTimeFormat = 'MMM d, yyyy, h:mm a'
@Field static final List<String> dateFormatOptions = ['MMM d, yyyy, h:mm a', 'E, MMM d, yyyy, h:mm a', 'E dd MMM yyyy, h:mm a', 'dd MMM yyyy, h:mm a',
                                        'dd MMM yyyy HH:mm', 'E MMM dd HH:mm', 'yyyy-MM-dd HH:mm z']
@Field static final Integer formatListIfMoreItemsThan = 4
@Field static final Integer defaultSnoozeDuration = 48

// Activity detection types
@Field static final String sACTIVITY = "activity"
@Field static final String sHEALTH_STATUS = "healthStatus"
@Field static final String sPRESENCE = "presence"
@Field static final String sBATTERY = "battery"

// Other
@Field static final String sSNOOZE_EMOJI = "&#x1F532;"    // black square box
@Field static final String sUNSNOOZE_EMOJI = "&#x2611;&#xFE0F;"  // ballot box w/ check

definition(
   name: "Device Activity Check",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Identify devices without recent activity that may have stopped working or \"fallen off\" your network",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-activity-check-get-notifications-for-inactive-devices/42176"
)

preferences {
   page name: "pageMain"
   page name: "pageDeviceGroup"
   page name: "pageRemoveGroup"
   page name: "pageViewReport"
}

Map pageMain() {
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
      
      section(styleSection("Devices to Monitor")) {
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
         input name: "notificationSwitch", type: "capability.switch", title: "Switch turned on:", description: "Optional - Click to set"
         //paragraph " ", width: 6
         paragraph "Or any time this button is pushed:"
         input name: "notificationButton", type: "capability.pushableButton", title: "Button device:", description: "Optional - Click to set",
            width: (settings["notificationButton"] != null) ? 6 : 12, submitOnChange: true
         if (settings["notificationButton"]) {
            input name: "notificationButtonNumber", type: "number", title: "button number", width: 3
            input name: "notificationButtonEvent", type: "enum", options: ["pushed", "held", "released", "doubleTapped"], title: "event", width: 3
         }
         // v2 always shows:
         //input name: "includeTime", type: "bool", title: "Include last acitivty time in notifications ", defaultValue: true, submitOnChange: true
         // Would be nice to consider for future:
         //if (includeTime) input name: "inclueNotPresentTime", type: "bool", title: "Use date of last \"not present\" event if device(s) configured for presence monitoring", defaultValue: true, submitOnChange: true
         List<Map<String,String>> timeFormatOptions = []
         Date currDate = new Date()
         dateFormatOptions.each { 
            timeFormatOptions << ["$it": "${currDate.format(it, location.timeZone)}"]
         }
         input name: "timeFormat", type: "enum", options: timeFormatOptions, title: "Date/time format for notifications:",
            defaultValue: defaultDateTimeFormat, submitOnChange: true, width: 6
         input name: "timeFormatForReports", type: "enum",  options: timeFormatOptions,
            title: 'Date/time format for "View current report" page',
            defaultValue: defaultDateTimeFormat, width: 6
      }

      section(styleSection("View/Test Report")) {
         href(name: "pageViewReportHref",
               page: "pageViewReport",
               title: "View current report",
               description: "Evaluate all devices now according to the criteria above, and display a report of \"inactive\" devices.")
         paragraph "The \"Test Notification Now\" button will send a notification to your selected device(s) if there is inactivity to report. This a manual method to trigger the same report the above options would also create:"
         input name: "btnTestNotification", type: "button", title: "Test Notification Now", width: 11
         input name: "btnSave", type: "button", title: "Update", width: 1, submitOnChange: true
      }
      
      section("Advanced Options", hideable: true, hidden: true) {
         label title: "Customize installed app name:", required: true
         input name: "includeHubName", type: "bool", title: "Include hub name in notifications (${location.name})"
         input name: "modes", type: "mode", title: "Only send notifications if mode is", multiple: true, required: false
         input name: "snoozeDuration", type: "number", title: 'Number of hours to remove deivce freom report with "snooze"', defaultValue: defaultSnoozeDuration
         input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
         input name: "debugLevel", type: "enum", title: "Debug logging level:", options: [[0: "Logs off"], [1: "Debug logging"], [2: "Verbose logging"]],
            defaultValue: 0
      }
   }
}

Map pageDeviceGroup(params) {
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
         String capabilityFilter
         switch (settings["group${groupNum}.inactivityMethod"]) {
            case sHEALTH_STATUS:
               capabilityFilter = "capability.*"
               break
            case sPRESENCE:
               capabilityFilter = "capability.presenceSensor"
               break
            case sBATTERY:
               capabilityFilter = "capability.battery"
               break
            default: // will catch default/inactivity method:
               capabilityFilter = "capability.*"
         }
         input name: "group${groupNum}.devices", type: capabilityFilter, multiple: true, title: "Select devices to monitor", submitOnChange: true
      }
      section(styleSection("Inactivity Threshold")) {
         input name: "group${groupNum}.inactivityMethod", title: "Inactivity detection method:", type: "enum",
            options: [[(sACTIVITY): "\"Last Activity At\" timestamp"], [(sBATTERY): "Battery level"], [(sHEALTH_STATUS): "\"healthStatus\" attribute"], [(sPRESENCE): "\"presence\" attribute (deprecated)"]],
            defaultValue: sACTIVITY, required: true, submitOnChange: true
         if (settings["group${groupNum}.inactivityMethod"] == sACTIVITY || settings["group${groupNum}.inactivityMethod"] == null) {
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
         else if (settings["group${groupNum}.inactivityMethod"] == sBATTERY) {
            input name: "group${groupNum}.batteryLevel", type: "number", title: "Include in report if battery level is less than:",
               range: "1..100"
         }
         else if (settings["group${groupNum}.inactivityMethod"] == sHEALTH_STATUS) {
            paragraph "Devices will be considered inactive if the value of the \"healthStatus\" attribute is \"offline\" at the time " +
               "of evaluation. Note that if your report is configured to display the \"Last Activity\" date, this date/time may not " +
               "necessarily correspond to actual device communication, depending on how the device driver works."
            if (settings["group${groupNum}.devices"]?.any { !(it.hasAttribute("healthStatus")) }) {
               paragraph "<strong>Warning: the following devices do not report a \"healthStatus\" attribute. De-select them or verify the correct driver or inactivity " +
                  """detection method:</strong> ${settings["group${groupNum}.devices"]?.findAll { !(it.hasAttribute("healthStatus")) }.join(", ")}"""
            }
         }
         else if (settings["group${groupNum}.inactivityMethod"] == sPRESENCE) {
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
      if (settings["group${groupNum}.devices"] && (settings["group${groupNum}.inactivityMethod"] == sACTIVITY)) {
         section(styleSection("Refresh Options")) {
            List<Map<Long, String>> rdevList = []
            settings["group${groupNum}.devices"].each {
               if (it.hasCommand("refresh")) rdevList << [(it.getIdAsLong()): it.getDisplayName()]
            }
            // Sort by device display name:
            rdevList.sort { a, b -> a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value }
            paragraph "Use the option below only if you have devices that do not regularly \"check in\" and respond to a refresh. (You must know your specific device behavior, but generally all powered devices and most battery-powered Zigbee devices will respond, while non-FLiRS Z-Wave battery devices will not.) Device Activity Check will send a \"Refresh\" command to the device before running a report, which may give a better indication of whether the device is still responsive. Be careful not to choose too many devices or devices that do not respond on-demand to refresh commands."
            input name: "group${groupNum}.refreshDevs", title: "Refresh before running report:", type: "enum", options: rdevList, multiple: true, submitOnChange: true
            if (settings["group${groupNum}.refreshDevs"]) {
               paragraph "<strong>Note:</strong> If any devices need to be refreshed (only devices deemed inactive will be), reports will be delayed for one-half second per device plus an additional five seconds in order to give devices time to respond (so the \"Test\" on the previous page may not be instant)."
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

Map pageRemoveGroup(params) {
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

Map pageViewReport() {
   logDebug "Loading \"View current report\" page..."
   // Format is [DeviceWrapper: ["device status (last activity, battery, etc.)", "optional other device status,..."]]
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = getInactiveDeviceMap(true)
   if (inactiveDeviceMap) inactiveDeviceMap = inactiveDeviceMap.sort { it.key.displayName }
   dynamicPage(name: "pageViewReport", title: "Device Activity Check", uninstall: false, install: false, nextPage: "pageMain") {
      if (inactiveDeviceMap) {
         section(styleSection("Inactive Device Report")) {
            paragraph "<strong>Device</strong>", width: 5
            paragraph "<strong>Status/Last Activity</strong>", width: 6
            paragraph "<strong>Snooze?</strong>", width: 1
            Boolean doFormatting = inactiveDeviceMap.size() > formatListIfMoreItemsThan
            inactiveDeviceMap.eachWithIndex { DeviceWrapper dev, List<String> states, index ->
               paragraph(doFormatting ? """<a href="/device/edit/${dev.id}">${styleListItem(dev.displayName, index)}</a>""" : """<a href="/device/edit/${dev.id}">${dev.displayName}</a>""", width: 5)
               paragraph(doFormatting ? "${styleListItem(states.join(', '), index)}" : states.join(', '), width: 6)
               if (checkIfSnoozed(dev.id)) {
                  //input name: "btnUnsnooze_${dev.id}", type: "button", title: "Y", width: 1, submitOnChange: true
                  paragraph emojiButtonLink("btnUnsnooze_${dev.id}", sUNSNOOZE_EMOJI, "click/tap to un-snooze"), width: 1
               }
               else {
                  //input name: "btnSnooze_${dev.id}", type: "button", title: "N", width: 1, submitOnChange: true
                  paragraph emojiButtonLink("btnSnooze_${dev.id}", sSNOOZE_EMOJI, "snooze for ${snoozeDuration ?: defaultSnoozeDuration} hr"), width: 1
               }
            }
         }
         List<DeviceWrapper> toRefreshDevices = []
         state.groups.each {
            toRefreshDevices.addAll(getInactiveDevices(it,true)) // catches devices that are inactive AND configured to be refreshed
         }
         if (toRefreshDevices) {
            section(styleSection("Refresh")) {
               Integer waitSeconds = performRefreshes(true) // gets estimated refresh time only; does NOT refresh
               String waitTime
               if (waitSeconds < 60) waitTime = "about ${waitSeconds} seconds"
               else if (waitSeconds < 75) waitTime = "about 1 minute"
               else if (waitSeconds < 145) waitTime = "about 2 minutes"
               else waitTime = "several minutes"
               paragraph "You have configured some inactive devices to be refreshed if inactive when a report is run (this happens automatically only for notifications, not when viewing this page). To perform this refresh now, press the \"Perform Refreshes\" button below, then reload this page in ${waitTime}."
               input type: "button", name: "btnPerformRefreshes", title: "Perform Refreshes", submitOnChange: false
               StringBuilder refreshSummarySB = new StringBuilder()
               refreshSummarySB << "<details><summary style=\"cursor: pointer\">Devices to be refreshed</summary>"
               toRefreshDevices.each { DeviceWrapper dev ->
                  refreshSummarySB <<  "<p>${dev.displayName}</p>"
               }
               refreshSummarySB <<  "</details>"
               paragraph refreshSummarySB.toString()
            }
         }
         section("Help", hideable: true, hidden: true) {
            paragraph "<dl><dt>What is \"snooze\"?</dt><dd>Snoozing will remove the device from notifications for the specified time period (configurable on the main app page)</dd></dl>"
         }
      }
      else {
         section(styleSection("Inactive Device Report")) {
            paragraph "No inactive devices to report"
         }
      }
   }
}

// Gets list of "inactive" devices (iterates through each group, determines detection method, adds devices to list)
// groupNum: group number (underlying, not display number) of devices to check
// onlyDevicesToBeRefreshed: returns only the list of inactive devices where user has selected that they should be refreshed
//                           (ignores devices where this option is not seleceted or that are not "inactive")
List<DeviceWrapper> getInactiveDevices(Integer groupNum, Boolean onlyDevicesToBeRefreshed=false) {
   logDebug "getInactiveDevices($groupNum, $onlyDevicesToBeRefreshed)", "trace"
   List<DeviceWrapper> inactiveDevices = []
   List<DeviceWrapper> groupDevs = settings["group${groupNum}.devices"] ?: []
   String detectionMethod = settings["group${groupNum}.inactivityMethod"]
   if (onlyDevicesToBeRefreshed && detectionMethod != sACTIVITY) {
      // Refresh only works for "last activity at" detection
      return []
   }
   if (settings["boolIncludeDisabled"] != true) {
      groupDevs = groupDevs.findAll { it.isDisabled() != true }
   }
   switch (detectionMethod) {
      case sACTIVITY:
      // TODO
         if (onlyDevicesToBeRefreshed && !(settings["group${groupNum}.refreshDevs"])) {
            break
         }
         Long inactiveMinutes = daysHoursMinutesToMinutes(settings["group${groupNum}.intervalD"],
            settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
         Long cutoffEpochTime = now() - (inactiveMinutes * 60000)
         groupDevs.each { DeviceWrapper dev ->
            if (onlyDevicesToBeRefreshed && !(dev.getId() in settings["group${groupNum}.refreshDevs"])) {
               // ignore
            }
            else if (dev.getLastActivity()?.getTime() <= cutoffEpochTime) {
               inactiveDevices << dev
            }
         }
         break
      case sHEALTH_STATUS:
         groupDevs.each { DeviceWrapper dev ->
            if (dev.currentValue("healthStatus") == "offline") {
               inactiveDevices << dev
            }
         }
         break
      case sPRESENCE:
         groupDevs.each { DeviceWrapper dev ->
            if (dev.currentValue("presence") == "not present") {
               inactiveDevices << dev
            }
         }
         break
      case sBATTERY:
         Integer level = settings["group${groupNum}.batteryLevel"] ?: 0
         groupDevs.each { DeviceWrapper dev ->
            if (dev.currentValue("battery") <= level) {
               inactiveDevices << dev
            }
         }
         break
      default:
         log.debug "Ignoring device $device: unsupported detcetion method $detectionMethod"
   }
   return inactiveDevices
}

/**
 *  Returns inactive devies (only) in Map in format like:
 *  [DeviceWrapper: ["device status (last activity, battery, etc.)", "optional other device status,..."]]
 *  for all groups; intended to be used when viewing report or sending notification
 *  @param isReportPage: false if is notification (default), true if "View current report" page; affects Last Activity string format
 */
Map<DeviceWrapper,List<String>> getInactiveDeviceMap(Boolean isReportPage=false) {
   // Format is [DeviceWrapper: ["device status (last activity, battery, etc.)", "optional other device status,..."]]
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = [:]
   state.groups?.each { groupNum ->
      String detectionMethod = settings["group${groupNum}.inactivityMethod"]
      List<DeviceWrapper> inactiveDevicesInGroup = getInactiveDevices(groupNum, false)
      inactiveDevicesInGroup.each { DeviceWrapper inactiveDev ->
         // Get status (last activity, battery, presence)
         if (!checkIfSnoozed(inactiveDev.id) || isReportPage) {
            String strStatus
            switch (detectionMethod) {
               case sACTIVITY:
                  String timeFormat = isReportPage ? settings.timeFormatForReports : settings.timeFormat
                  if (!timeFormat) timeFormat = defaultDateTimeFormat
                  strStatus = inactiveDev.getLastActivity()?.format(timeFormat, location.timeZone)
                  if (!strStatus) strStatus = "No activity reported"
                  break
               case sHEALTH_STATUS:
                  strStatus = "offline"
                  break
               case sPRESENCE:
                  strStatus = "not present"
                  break
               case sBATTERY:
                  strStatus = "${inactiveDev.currentValue('battery')}% battery"
                  break
               default:
                  strStatus = "unknown"
            }
            // Now, either put in Map or add to list of already-existing status(es) for that device:
            if (inactiveDeviceMap[inactiveDev]) {
               inactiveDeviceMap[inactiveDev] << strStatus
            }
            else {
               inactiveDeviceMap[inactiveDev] = [strStatus]
            }
         }
         else {
            // nothing -- not report page and device snoozed
         }
      }
   }
   return inactiveDeviceMap
}

Boolean isAnyRefreshConfigured() {
   state.groups.each { groupNum ->
      if (settings["group${groupNum}.inactivityMethod"] == sACTIVITY && settings["group${groupNum}.devices"] && settings["group${groupNum}.refreshDevs"]) {
         return true
      }
   }
   return false
}

// Calls refresh() on each device if needed (only if inactive and configured);
// Returns expected wait time (in seconds) after which report is likely to be reliable
Integer performRefreshes(Boolean returnCountOnlyAndDoNotRefresh=false) {
   logDebug "performRefreshes()...", "trace"
   Integer waitTime = 0
   List<DeviceWrapper> toRefreshDevices = []
   state.groups.each {
      toRefreshDevices.addAll(getInactiveDevices(it, true)) // only includes devices needing refresh
   }
   if (toRefreshDevices) {
      logDebug "Devices to refresh: $toRefreshDevices"
      if (!returnCountOnlyAndDoNotRefresh) {
         toRefreshDevices.each {
            try {
               it.refresh()
               pauseExecution(200)
            }
            catch (Exception ex) {
               log.warn "Could not refresh $it:\n$ex"
            }
         }
      }
      waitTime = (toRefreshDevices.size() * 200 + 5000) / 1000
   }
   return waitTime
}

// Lists all devices in group, one per line
String getDeviceGroupDescription(groupNum) {
   logDebug "getDeviceGroupDescription($groupNum)...", "trace"
   String desc = ""
   if (settings["group${groupNum}.devices"]) {
      List<DeviceWrapper> groupDevs = settings["group${groupNum}.devices"].sort { DeviceWrapper it -> it.displayName }
      groupDevs.each { DeviceWrapper dev ->
         desc += "${dev.displayName}\n"
      }
   }
   return desc
}

// Human-friendly string for inactivity period (e.g., "1 hour, 15 minutes")
String getDeviceGroupInactivityThresholdString(groupNum) {
   logDebug "getDeviceGroupInactivityThresholdString($groupNum)...", "trace"
   String thresholdString = ""
   if (settings["group${groupNum}.inactivityMethod"] == sACTIVITY || !settings["group${groupNum}.inactivityMethod"]) {
      thresholdString = daysHoursMinutesToString(settings["group${groupNum}.intervalD"],
         settings["group${groupNum}.intervalH"], settings["group${groupNum}.intervalM"])
   }
   else if (settings["group${groupNum}.inactivityMethod"] == sBATTERY) {
      Integer level = settings["group${groupNum}.batteryLevel"] ?: 0
      thresholdString = "if battery < $level"
   }
   else if (settings["group${groupNum}.inactivityMethod"] == sHEALTH_STATUS) {
      thresholdString = "if offline"
   }
   else if (settings["group${groupNum}.inactivityMethod"] == sPRESENCE) {
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
   settingNamesToRemove.each { String settingName ->
      app.removeSetting(settingName)
   }
}

Long daysHoursMinutesToMinutes(Long days, Long hours, Long minutes) {
   Long totalMin = (minutes ?: 0) + (hours ? hours * 60 : 0) + (days ? days * 1440 : 0)
   return totalMin
}

String daysHoursMinutesToString(Long days, Long hours, Long minutes) {
   Long totalMin = daysHoursMinutesToMinutes(days, hours, minutes)
   Long d = totalMin / 1440
   Long h = (totalMin - d * 1440) / 60
   Long m = totalMin % 60
   String strD = "$d day${d != 1 ? 's' : ''}"
   String strH = "$h hour${h != 1 ? 's' : ''}"
   String strM = "$m minute${m != 1 ? 's' : ''}"
   return "${d ? strD : ''}${d && (h || m) ? ', ' : ''}${h ? strH : ''}${(h && m) ? ', ' : ''}${m || !(h || d) ? strM : ''}"
}



void switchHandler(evt) {
   logDebug("Switch turned on; running report")
   sendInactiveNotification()
}

void buttonHandler(evt) {
   logDebug("Button ${evt.value}; running report")
   sendInactiveNotification()
}

// Sends notification with list of inactive devices to selected notification device(s)
void sendInactiveNotification(Boolean doRefreshIfConfigured=true) {
   logDebug "sendInactiveNotification($doRefreshIfConfigured)", "trace"
   if (doRefreshIfConfigured == true) {
      logDebug "doRefreshIfConfigured == true"
      Integer waitTime = performRefreshes(true) // only gets time, does not refresh
      runIn(waitTime, "postRefreshNotificationHandler")
      performRefreshes()
      return
   }
   logDebug "Preparing list of inactive devices..."

   Map<DeviceWrapper,List<String>> inactiveDeviceMap = getInactiveDeviceMap()
   StringBuilder sbNotificationText = new StringBuilder() 
   if (inactiveDeviceMap && isModeOK()) {
      inactiveDeviceMap = inactiveDeviceMap.sort { it.key.displayName }
      sbNotificationText << (settings["includeHubName"] ? "${app.label} - ${location.name}:" : "${app.label}:")
      inactiveDeviceMap.each { DeviceWrapper dev, List<String> status ->
         sbNotificationText << "\n${dev.displayName}"
         sbNotificationText << " - ${status.join(', ')}"
         
      }
      String notificationText = sbNotificationText.toString()
      logDebug "Sending notification for inactive devices: \"$notificationText\""
      notificationDevice?.each {
         it.deviceNotification(notificationText)
      }
   }
   else {
      String reason = "Notification skipped:"
      if (!inactiveDeviceMap) reason += " No inactive devices."
      if (!isModeOK()) reason += " Outside of specified mode(s)."
      logDebug reason
   }
}

void postRefreshNotificationHandler() {
   pauseExecution(100) // probably not necessary, but just in case (give a bit more time)
   sendInactiveNotification(false) // skips refresh
}

// For list items in report page
String styleListItem(String text, Long index=0) {
   return """<div style="color: ${index %2 == 0 ? "darkslategray" : "black"}; background-color: ${index %2 == 0 ? 'white' : 'ghostwhite'}">$text</div>"""
}


// Check and log if any detection methods using unspported attributes:
void verifyAndLogMissingCapabilities() {
   List groups = state.groups ?: [1]
   groups.each { groupNum ->
      List<DeviceWrapper> allDevices = settings["group${groupNum}.devices"] ?: []
      // ignoring "Last Activity" at devices since works with any
      // For battery level devices:
      if (settings["group${groupNum}.inactivityMethod"] == "battery") {
         allDevices.each {
            if (!(it.hasAttribute("battery"))) log.warn "Device $it detection method is battery but does not support battery attribute"
         }
      }
      // For presence-based devices:
      else if (settings["group${groupNum}.inactivityMethod"] == "presence") {
         allDevices.each {
            if (!(it.hasAttribute("presence"))) log.warn "Device $it detection method is presence but does not support presence attribute"
         }
      }
   }
}

// Returns true if snoozed, false if not
Boolean checkIfSnoozed(String deviceId) {
   logDebug "checkIfSnoozed($deviceId)"
   Long snoozedUntil = state.snoozedDevices?.get(deviceId)
   if (snoozedUntil) {
      logDebug "Found snooze date..."
      Boolean stillSnoozed = snoozedUntil >= now()
      if (stillSnoozed) {
         logDebug "Still snoozed."
         return true
      }
      else {
         logDebug "No longer snoozed."
         state.snoozedDevices?.remove(deviceId)
         return false
      }
   }
   else {
      logDebug "Not snoozed", "trace"
      return false
   }
}

void snoozeDevice(String deviceId) {
   logDebug "snoozeDevice($deviceId)"
   Long snoozeUntil = now() + (snoozeDuration ?: defaultSnoozeDuration)*3_600_000
   if (!(state.snoozedDevices)) {
      Map<String,Long> snoozedDevs = [(deviceId) : snoozeUntil]
      state.snoozedDevices = snoozedDevs
   }
   else {
      state.snoozedDevices[deviceId] = snoozeUntil
   }
}

void unsnoozeDevice(String deviceId) {
   logDebug "unsnoozeDevice($deviceId)"
   state.snoozedDevices?.remove(deviceId)
}

void scheduleHandler() {
   logDebug("At scheduled; running report")
   sendInactiveNotification()
}

//=========================================================================
// Styling Methods
//=========================================================================

String styleSection(String sectionHeadingText) {
   return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = 15) {
   "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:${font}px'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

String emojiButtonLink(String btnName, String linkText, String titleText, color = "#1A77C9", font = 17) {
   "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:${font}px' title='$titleText'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}


//=========================================================================
// App Methods
//=========================================================================

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated()"
   unschedule()
   initialize()
   verifyAndLogMissingCapabilities()
}

void initialize() {
   log.debug "initialize()"
   if (settings["debugLevel"] && settings["debugLevel"].toInteger() != 0) {
      log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
   }
   unsubscribe()
   if (settings["notificationTime"]) schedule(settings["notificationTime"], scheduleHandler)
   if (settings["notificationSwitch"]) subscribe(settings["notificationSwitch"], "switch.on", "switchHandler")
   if (settings["notificationButton"]) subscribe(settings["notificationButton"], "${settings.notificationButtonEvent}.${settings.notificationButtonNumber}", "buttonHandler")
}

Boolean isModeOK() {
   Boolean isOK = !settings["modes"] || settings["modes"].contains(location.mode)
   logDebug "Checking if mode is OK; reutrning: ${isOK}", "trace"
   return isOK
}

void appButtonHandler(String btn) {
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
      case "btnPerformRefreshes":
         performRefreshes()
         break
      case { it.startsWith("btnSnooze_") }:
         snoozeDevice(btn - "btnSnooze_")
         break
      case { it.startsWith("btnUnsnooze_") }:
         unsnoozeDevice(btn - "btnUnsnooze_")
         break
      case "btnSave":
         pauseExecution(100)
         initialize()
         break
      default:
         log.warn "Unhandled button press: $btn"
   }
}

/** Writes to log.debug by default if debug logging setting enabled; can specify
  * other log level (e.g., "info") if desired
  */
void logDebug(String str, String level="debug") {
   switch(level) {
      case "trace": 
         if (settings["debugLevel"] != null && (settings["debugLevel"].toInteger()) == 2) log.trace(str)
         break
      default:
        if (settings["debugLevel"] != null && (settings["debugLevel"].toInteger()) >= 1) log."$level"(str)
   }
}