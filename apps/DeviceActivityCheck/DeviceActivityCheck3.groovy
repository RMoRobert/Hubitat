/**
 * ==========================  Device Activity Check ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2026 Robert Morris
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
 *  Author: Robert Morris
 *
 * Changelog:
 * 3.0.1 (2026-06-04) - Add preference to hide names of group devices; add additional post-refresh interval times
 * 3.0   (2026-05-24) - Parent/child app structure: one child app per device group
 *                    - Optional Zigbee last message and Z-Wave last time for C-8/C-8 Pro
 *                    - Configurable post-refresh delay (parent-level)
 *                    - Remove deprecated "presence" option
 * 2.4.1 (2024-04-25) - Add line break before (optional) appended notification text
 * 2.4   (2024-04-24) - Add option to append text to notifications
 * 2.3   (2023-12-14) - Add search for device list
 * 2.2   (2023-05-27) - Add OAuth endpoints to view reports locally or via cloud
 * 2.1.2 (2023-02-15) - Fix snooze time calcuations
 * 2.1.1 (2023-02-11) - Remove accidental extra logging
 * 2.1   (2023-02-05) - Add healthStatus attribute option; buttons as trigger; snooze options
 * 2.0.1 (2022-06-19) - Fix for error on platform 2.3.2
 * 2.0   (2022-01-02) - Improved reports for non-activity-based methods
 * 1.0   (2020-05-27) - First public release
 *
 */

import groovy.transform.Field
import groovy.xml.MarkupBuilder
import com.hubitat.app.DeviceWrapper

@Field static final String defaultDateTimeFormat = 'MMM d, yyyy, h:mm a'
@Field static final List<String> dateFormatOptions = ['MMM d, yyyy, h:mm a', 'E, MMM d, yyyy, h:mm a', 'E dd MMM yyyy, h:mm a', 'dd MMM yyyy, h:mm a',
                                        'dd MMM yyyy HH:mm', 'E MMM dd HH:mm', 'yyyy-MM-dd HH:mm z']
@Field static final Integer formatListIfMoreItemsThan = 4
@Field static final Integer defaultSnoozeDuration = 48
@Field static final Integer defaultPostRefreshDelay = 5000
@Field static final Long radioCacheTtlMs = 120000
@Field static final Integer radioFetchWaitMs = 5000

@Field static final String sSNOOZE_EMOJI = "&#x1F532;"
@Field static final String sUNSNOOZE_EMOJI = "&#x2611;&#xFE0F;"

definition(
   name: "Device Activity Check 3",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Identify devices without recent activity (or select other methods) that may have stopped working",
   category: "Convenience",
   installOnOpen: true,
   //singleThreaded: true,
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-activity-check-get-notifications-for-inactive-devices/42176"
)

preferences {
   page name: "pageFirstPage"
   page name: "pageMain"
   page name: "pageViewReport"
}

Map pageFirstPage() {
   if (app.getInstallationState() == "INCOMPLETE") {
      dynamicPage(name: "pageFirstPage", title: "Device Activity Check", uninstall: true, install: true) {
         section() {
            paragraph "<b>Select \"Done\" to finish installing this app,</b> then re-open it to configure and use."
         }
      }
   }
   else {
      return pageMain()
   }
}

Map pageMain() {
   dynamicPage(name: "pageMain", title: "Device Activity Check", uninstall: true, install: true) {

      section(styleSection("Device Groups")) {
         List childList = getChildApps()?.sort { it.label } ?: []
         Boolean showDeviceNames = (settings.showDeviceNamesInDescription != false)
         childList.each { child ->
            String threshold = child.getInactivityThresholdString()
            String method = child.getInactivityMethodDisplayString()
            String deviceDesc = child.getDeviceGroupDescription()
            String strTitle = "${child.label} (detection method: ${method}; inactivity threshold: ${threshold}):"
            href(name: "childGroup${child.id}Href",
                 url: "/installedapp/configure/${child.id}",
                 title: strTitle,
                 description: showDeviceNames ? (deviceDesc ?: "Select to choose devices and inactivity threshold...") : "Click/tap to see details...",
                 state: deviceDesc ? "complete" : null)
         }
         if (childList.size() < 1) {
            paragraph "<b>Get started:</b> Begin by adding a new device group. You may add multiple groups with different detection methods or thresholds."
         }
         else {
            paragraph "Add additional device groups to monitor different sets of devices, optionally with different detection methods or thesholds."
         }
         input name: "showDeviceNamesInDescription", type: "bool", title: "Show device names in group descriptions", defaultValue: true, submitOnChange: true
         app(
            name: "childApps",
            appName: "Device Activity Check 3 Group Child",
            namespace: "RMoRobert",
            title: "Add new device group...", 
            multiple: true,
            displayChildApps: false
         )
      }
      

      section(styleSection("Notification Options")) {
         input name: "notificationDevice", type: "capability.notification", title: "Send notification with list of inactive devices to this device:", multiple: true
         input name: "notificationTime", type: "time", title: "Daily at this time:"
         paragraph "Or any time this switch is turned on:"
         input name: "notificationSwitch", type: "capability.switch", title: "Switch turned on:", description: "Optional - Click to set"
         paragraph "Or any time this button is pushed:"
         input name: "notificationButton", type: "capability.pushableButton", title: "Button device:", description: "Optional - Click to set",
            width: (settings["notificationButton"] != null) ? 6 : 12, submitOnChange: true
         if (settings["notificationButton"]) {
            input name: "notificationButtonNumber", type: "number", title: "button number", width: 3
            input name: "notificationButtonEvent", type: "enum", options: ["pushed", "held", "released", "doubleTapped"], title: "event", width: 3
         }
         List<Map<String,String>> timeFormatOptions = []
         Date currDate = new Date()
         dateFormatOptions.each {
            timeFormatOptions << ["$it": "${currDate.format(it, location.timeZone)}"]
         }
         input name: "timeFormat", type: "enum", options: timeFormatOptions, title: "Date/time format for notifications:",
            defaultValue: defaultDateTimeFormat, submitOnChange: true, width: 6
         input name: "timeFormatForReports", type: "enum", options: timeFormatOptions,
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

      if (state.accessToken) {
         section(styleSection("Report Pages")) {
            paragraph "To access reports from your LAN or the cloud (without needing to use the regular hub interface), use one of the links below:"
            paragraph """<ul><li><a href="${getLocalPathWithToken("/dac/report") + "&isLocal=true"}">LAN Link</a></li><li><a href="${getCloudPathWithToken("/dac/report")}">Cloud Link</a></li></ul>"""
         }
      }
      else {
         section(styleSection("Report Pages")) {
            paragraph 'OAuth is not enabled. Please enable OAuth for this app in "Apps Code" per the ' +
            '<a href="https://github.com/RMoRobert/Hubitat/tree/master/apps/DeviceActivityCheck">installation instructions</a> if you wish to use local or cloud report endpoints. Then, re-open the app and select "Done."'
         }
      }

      section("Advanced Options", hideable: true, hidden: (boolAppendNotificationText != true)) {
         label title: "Customize installed app name:", required: true
         input name: "includeHubName", type: "bool", title: "Include hub name in notifications (${location.name})"
         input name: "modes", type: "mode", title: "Only send notifications if mode is", multiple: true, required: false
         input name: "snoozeDuration", type: "number", title: 'Number of hours to remove device from report with "snooze"', defaultValue: defaultSnoozeDuration
         input name: "boolIncludeDisabled", type: "bool", title: "Include disabled devices in report"
         input name: "boolAppendNotificationText", type: "bool", title: "Append text to notification?", submitOnChange: true
         if (settings.boolAppendNotificationText) {
            input name: "textToAppendToNotification", type: "text", title: "Text to append to notification:", submitOnChange: true
         }
         input name: "postRefreshDelay", title: "Post-refresh delay before notification", type: "enum",
            options: [[4000:"4 seconds"],[5000:"5 seconds [DEFAULT]"],[7000:"7 seconds"],[10000:"10 seconds"],
            [15000:"15 seconds"],[20000:"20 seconds"],[30000:"30 seconds"],[45000:"45 seconds"],[60000:"1 minute"],
            [90000:"1.5 minutes"],[120000:"2 minutes"],[180000:"3 minutes"],[300000:"5 minutes"]],
            defaultValue: "5000"
         input name: "btnFetchRadioData", type: "button", title: "Fetch Zigbee/Z-Wave device data"
         input name: "debugLevel", type: "enum", title: "Debug logging level:", options: [[0: "Logs off"], [1: "Debug logging"], [2: "Verbose logging"]],
            defaultValue: 0
      }
   }
}

Map pageViewReport() {
   logDebug "Loading \"View current report\" page..."
   ensureRadioDetailsFetched()
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
                  paragraph emojiButtonLink("btnUnsnooze_${dev.id}", sUNSNOOZE_EMOJI, "click/tap to un-snooze"), width: 1
               }
               else {
                  paragraph emojiButtonLink("btnSnooze_${dev.id}", sSNOOZE_EMOJI, "snooze for ${snoozeDuration ?: defaultSnoozeDuration} hr"), width: 1
               }
            }
         }
         List<DeviceWrapper> toRefreshDevices = getAllInactiveDevices(true)
         if (toRefreshDevices) {
            section(styleSection("Refresh")) {
               Integer waitSeconds = performRefreshes(true)
               String waitTime
               if (waitSeconds < 60) waitTime = "about ${waitSeconds} seconds"
               else if (waitSeconds < 75) waitTime = "about 1 minute"
               else if (waitSeconds < 145) waitTime = "about 2 minutes"
               else waitTime = "several minutes"
               paragraph "You have configured some inactive devices to be refreshed if inactive when a report is run (this happens automatically before notifications, not when viewing this page). To perform this refresh now, press the \"Perform Refreshes\" button below, then reload this page in ${waitTime}."
               input type: "button", name: "btnPerformRefreshes", title: "Perform Refreshes", submitOnChange: false
               StringBuilder refreshSummarySB = new StringBuilder()
               refreshSummarySB << "<details><summary style=\"cursor: pointer\">Devices to be refreshed</summary>"
               toRefreshDevices.each { DeviceWrapper dev ->
                  refreshSummarySB << "<p>${dev.displayName}</p>"
               }
               refreshSummarySB << "</details>"
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

//=========================================================================
// Aggregation (delegates to child apps)
//=========================================================================

Map<DeviceWrapper,List<String>> getInactiveDeviceMap(Boolean isReportPage=false) {
   ensureRadioDetailsFetched()
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = [:]
   getChildApps()?.each { child ->
      child.getInactiveDeviceMap(isReportPage)?.each { DeviceWrapper dev, List<String> statuses ->
         if (inactiveDeviceMap[dev]) {
            inactiveDeviceMap[dev].addAll(statuses)
         }
         else {
            inactiveDeviceMap[dev] = statuses
         }
      }
   }
   return inactiveDeviceMap
}

List<DeviceWrapper> getAllInactiveDevices(Boolean onlyDevicesToBeRefreshed=false) {
   ensureRadioDetailsFetched()
   List<DeviceWrapper> inactiveDevices = []
   getChildApps()?.each { child ->
      inactiveDevices.addAll(child.getInactiveDevices(onlyDevicesToBeRefreshed))
   }
   return inactiveDevices
}

Integer performRefreshes(Boolean returnCountOnlyAndDoNotRefresh=false) {
   logDebug "performRefreshes($returnCountOnlyAndDoNotRefresh)", "trace"
   ensureRadioDetailsFetched()
   Integer waitTime = 0
   getChildApps()?.each { child ->
      Integer childWait = child.performRefreshes(returnCountOnlyAndDoNotRefresh)
      if (childWait > waitTime) waitTime = childWait
   }
   return waitTime
}

//=========================================================================
// Methods exposed to child apps
//=========================================================================

Boolean getBoolIncludeDisabled() {
   return settings.boolIncludeDisabled == true
}

String getTimeFormat(Boolean isReportPage=false) {
   String timeFormat = isReportPage ? settings.timeFormatForReports : settings.timeFormat
   return timeFormat ?: defaultDateTimeFormat
}

Integer getPostRefreshDelayMs() {
   if (settings.postRefreshDelay != null) {
      try {
         return Integer.parseInt(settings.postRefreshDelay ?: defaultPostRefreshDelay)
      }
      catch (Exception ex) {
         log.warn "Invalid postRefreshDelay: $ex"
      }
   }
   return defaultPostRefreshDelay
}

Date getLastActivityOrMessageDateForDevice(DeviceWrapper dev, Boolean useZigbeeInfo, Boolean useZwaveInfo) {
   if (dev.controllerType == "ZGB" && useZigbeeInfo) {
      Map zigbeeDevInfo = state.zigbeeDevices?.find { it.id == dev.idAsLong }
      if (zigbeeDevInfo?.lastMessage) {
         return toDate(zigbeeDevInfo.lastMessage)
      }
   }
   else if (dev.controllerType == "ZW" && useZwaveInfo) {
      Map zwaveDeviceInfo = state.zwaveNodes?.find { it.deviceId == dev.idAsLong }
      if (zwaveDeviceInfo?.lastTime) {
         return toDate(zwaveDeviceInfo.lastTime)
      }
   }
   return dev.getLastActivity()
}

//=========================================================================
// Zigbee and Z-Wave data (async fetch, state-backed cache)
//=========================================================================

Boolean isRadioCacheStale(Long lastFetch) {
   return !lastFetch || lastFetch < now() - radioCacheTtlMs
}

Boolean anyChildNeedsZigbeeInfo() {
   return getChildApps()?.any { it.usesZigbeeInfo() }
}

Boolean anyChildNeedsZwaveInfo() {
   return getChildApps()?.any { it.usesZwaveInfo() }
}

void ensureRadioDetailsFetched(Boolean forceRefresh=false) {
   Boolean needZigbee = anyChildNeedsZigbeeInfo()
   Boolean needZwave = anyChildNeedsZwaveInfo()
   if (!needZigbee && !needZwave) return

   if (needZigbee && (forceRefresh || isRadioCacheStale(state.lastZigbeeFetch))) {
      if (forceRefresh) state.zigbeeFetchInProgress = false
      if (!state.zigbeeFetchInProgress) {
         state.zigbeeFetchInProgress = true
         asynchttpGet("zigbeeDeviceGetCallback", radioHttpParams("/hub/zigbeeDetails/json"))
         logDebug "Zigbee info fetch sent", "trace"
      }
   }
   if (needZwave && (forceRefresh || isRadioCacheStale(state.lastZwaveFetch))) {
      if (forceRefresh) state.zwaveFetchInProgress = false
      if (!state.zwaveFetchInProgress) {
         state.zwaveFetchInProgress = true
         asynchttpGet("zwaveDeviceGetCallback", radioHttpParams("/hub/zwaveDetails/json"))
         logDebug "Z-Wave info fetch sent", "trace"
      }
   }

   Integer waited = 0
   while (waited < radioFetchWaitMs &&
          ((needZigbee && state.zigbeeFetchInProgress) || (needZwave && state.zwaveFetchInProgress))) {
      pauseExecution(250)
      waited += 250
   }
}

Map radioHttpParams(String path) {
   return [
      uri: "http://127.0.0.1:8080",
      path: path,
      requestContentType: "application/json",
      timeout: 15
   ]
}

void zigbeeDeviceGetCallback(hubitat.scheduling.AsyncResponse resp, Map data=null) {
   logDebug "zigbeeDeviceGetCallback()", "trace"
   state.zigbeeFetchInProgress = false
   if (resp.error) {
      log.error "Error fetching Zigbee device data: HTTP ${resp.status}: ${resp.errorMessage}."
   }
   else if (resp.json?.devices != null) {
      state.zigbeeDevices = resp.json.devices
      state.lastZigbeeFetch = now()
      logDebug "Zigbee device data cached (${state.zigbeeDevices?.size()} devices)", "trace"
   }
}

void zwaveDeviceGetCallback(hubitat.scheduling.AsyncResponse resp, Map data=null) {
   logDebug "zwaveDeviceGetCallback()", "trace"
   state.zwaveFetchInProgress = false
   if (resp.error) {
      log.error "Error fetching Z-Wave device data: HTTP ${resp.status}: ${resp.errorMessage}."
   }
   else if (resp.json?.nodes != null) {
      state.zwaveNodes = resp.json.nodes
      state.lastZwaveFetch = now()
      logDebug "Z-Wave device data cached (${state.zwaveNodes?.size()} nodes)", "trace"
   }
}

//=========================================================================
// Notifications and event handlers
//=========================================================================

void switchHandler(evt) {
   logDebug("Switch turned on; running report")
   sendInactiveNotification()
}

void buttonHandler(evt) {
   logDebug("Button ${evt.value}; running report")
   sendInactiveNotification()
}

void scheduleHandler() {
   logDebug("At scheduled time; running report")
   sendInactiveNotification()
}

void sendInactiveNotification(Boolean doRefreshIfConfigured=true) {
   logDebug "sendInactiveNotification($doRefreshIfConfigured)", "trace"
   if (doRefreshIfConfigured == true) {
      Integer waitTime = performRefreshes(true)
      runIn(waitTime, "postRefreshNotificationHandler")
      performRefreshes()
      return
   }
   ensureRadioDetailsFetched()
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = getInactiveDeviceMap()
   StringBuilder sbNotificationText = new StringBuilder()
   if (inactiveDeviceMap && isModeOK()) {
      inactiveDeviceMap = inactiveDeviceMap.sort { it.key.displayName }
      sbNotificationText << (settings["includeHubName"] ? "${app.label} - ${location.name}:" : "${app.label}:")
      inactiveDeviceMap.each { DeviceWrapper dev, List<String> status ->
         sbNotificationText << "\n${dev.displayName}"
         sbNotificationText << " - ${status.join(', ')}"
      }
      if (settings.boolAppendNotificationText == true && settings.textToAppendToNotification) {
         sbNotificationText << "\n${settings.textToAppendToNotification}"
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
   pauseExecution(100)
   sendInactiveNotification(false)
}

Boolean isModeOK() {
   return !settings["modes"] || settings["modes"].contains(location.mode)
}

//=========================================================================
// Snooze
//=========================================================================

Boolean checkIfSnoozed(String deviceId) {
   logDebug "checkIfSnoozed($deviceId)"
   Long snoozedUntil = state.snoozedDevices?.get(deviceId)
   if (snoozedUntil) {
      if (snoozedUntil >= now()) {
         return true
      }
      state.snoozedDevices?.remove(deviceId)
   }
   return false
}

void snoozeDevice(String deviceId) {
   logDebug "snoozeDevice($deviceId)"
   Long snoozeUntil = now() + (snoozeDuration ?: defaultSnoozeDuration) * 3_600_000
   if (!(state.snoozedDevices)) {
      state.snoozedDevices = [(deviceId): snoozeUntil]
   }
   else {
      state.snoozedDevices[deviceId] = snoozeUntil
   }
}

void unsnoozeDevice(String deviceId) {
   logDebug "unsnoozeDevice($deviceId)"
   state.snoozedDevices?.remove(deviceId)
}

//=========================================================================
// Styling
//=========================================================================

String styleSection(String sectionHeadingText) {
   return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

String styleListItem(String text, Long index=0) {
   return """<div style="color: ${index %2 == 0 ? "darkslategray" : "black"}; background-color: ${index %2 == 0 ? 'white' : 'ghostwhite'}">$text</div>"""
}

String emojiButtonLink(String btnName, String linkText, String titleText, color = "#1A77C9", font = 17) {
   "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:${font}px' title='$titleText'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

//=========================================================================
// App lifecycle
//=========================================================================

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated()"
   unschedule()
   initialize()
   getChildApps()?.each { it.verifyAndLogMissingCapabilities() }
}

void initialize() {
   log.debug "initialize()"
   if (settings["debugLevel"] && settings["debugLevel"].toInteger() != 0) {
      log.debug "Debug logging is enabled for ${app.label}. It will remain enabled until manually disabled."
   }
   initializeAppEndpoint()
   unsubscribe()
   if (settings["notificationTime"]) schedule(settings["notificationTime"], scheduleHandler)
   if (settings["notificationSwitch"]) subscribe(settings["notificationSwitch"], "switch.on", "switchHandler")
   if (settings["notificationButton"]) subscribe(settings["notificationButton"], "${settings.notificationButtonEvent}.${settings.notificationButtonNumber}", "buttonHandler")
}

void appButtonHandler(String btn) {
   switch (btn) {
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
      case "btnFetchRadioData":
         ensureRadioDetailsFetched(true)
         break
      case "btnSave":
         pauseExecution(100)
         initialize()
         break
      default:
         log.warn "Unhandled button press: $btn"
   }
}

private void initializeAppEndpoint(Boolean forceNewAccessToken=false) {
   logDebug "initializeAppEndpoint()"
   if (!state.accessToken || forceNewAccessToken) {
      try {
         log.debug "Creating access token..."
         createAccessToken()
      }
      catch (Exception ex) {
         log.warn "Failed to generate access token: $ex"
         state.remove("accessToken")
      }
   }
}

void logDebug(String str, String level="debug") {
   switch (level) {
      case "trace":
         if (settings["debugLevel"] != null && (settings["debugLevel"].toInteger()) == 2) log.trace(str)
         break
      default:
         if (settings["debugLevel"] != null && (settings["debugLevel"].toInteger()) >= 1) log."$level"(str)
   }
}

//=========================================================================
// HTTP endpoints
//=========================================================================

mappings {
   path("/dac/report") {action: [GET: "handleHttpReport"]}
   path("/dac/toggleSnooze") {action: [POST: "handleHttpToggleSnooze"]}
}

Map handleHttpReport() {
   logDebug "handleHttpReport", "trace"
   Boolean isLocal = params.isLocal == "true" || params.isLocal == true
   render contentType: "text/html", status: 200, data: createReportHTML(isLocal)
}

Map handleHttpToggleSnooze() {
   logDebug "handleHttpToggleSnooze(); params = $params", "trace"
   String deviceId = request?.JSON?.deviceId
   Map<String,String> returnJSON
   if (deviceId != null) {
      if (checkIfSnoozed(deviceId)) {
         unsnoozeDevice(deviceId)
         returnJSON = [status: "unsnoozed"]
      }
      else {
         snoozeDevice(deviceId)
         returnJSON = [status: "snoozed"]
      }
   }
   else {
      returnJSON = [status: "error"]
   }
   returnJSON
}

String getLocalPathWithToken(String forPath) {
   return getFullLocalApiServerUrl() + forPath + "?access_token=${state.accessToken}"
}

String getCloudPathWithToken(String forPath) {
   return getFullApiServerUrl() + forPath + "?access_token=${state.accessToken}"
}

String createReportHTML(Boolean isLocal=true) {
   ensureRadioDetailsFetched()
   StringWriter swriter = new StringWriter()
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = getInactiveDeviceMap(true)
   if (inactiveDeviceMap) inactiveDeviceMap = inactiveDeviceMap.sort { it.key.displayName }
   swriter.write "<!DOCTYPE html>\n<html><head>\n"
   new MarkupBuilder(swriter).title("Device Activity Check - ${location.name}")
   swriter.write "\n<style>$reportCSS</style>\n<script>$tableSortJS\n$toggleSnoozeJS</script>\n</head>\n"
   new MarkupBuilder(swriter).body {
      header {
         h1("Inactive Device Report")
         div(role: "doc-subtitle", app.name)
         p(location.name)
      }
      table(id: "reportTable") {
         tr(class: "header") {
            th(onclick: "sortTable(0);", "Device")
            th(onclick: "sortTable(1);", "Status/Last Activity")
            th(onclick: "sortTable(2);", "Snooze")
         }
         inactiveDeviceMap.each { DeviceWrapper dev, List<String> states ->
            tr {
               if (isLocal) {
                  td { a(href: "/device/edit/${dev.id}", dev.displayName) }
               }
               else {
                  td(dev.displayName)
               }
               td(states.join(', '))
               td {
                  if (checkIfSnoozed(dev.id)) {
                     INPUT(type: "checkbox", id: "snoozebox_dev_${dev.id}", value: "snoozebox_dev_${dev.id}",
                        onclick: "toggleSnooze(${dev.id})", checked: true)
                     LABEL(for: "snoozebox_dev_${dev.id}", hidden: true, "Is snoozed?")
                  }
                  else {
                     INPUT(type: "checkbox", id: "snoozebox_dev_${dev.id}", value: "snoozebox_dev_${dev.id}",
                        onclick: "toggleSnooze(${dev.id})")
                     LABEL(for: "snoozebox_dev_${dev.id}", hidden: true, "Is snoozed?")
                  }
               }
            }
         }
      }
   }
   swriter.write("</html>")
   return swriter.toString()
}

@Field static final String reportCSS =
"""
h1,header>p{padding-top:.25em}[role=doc-subtitle],h1,header>p{margin-top:0;margin-bottom:0}body{font-family:Helvetica,Arial,sans-serif}h1{padding-bottom:.1em}[role=doc-subtitle]{padding-top:.1em;padding-bottom:.5em}header>p{padding-bottom:1.5em;font-size:85%}#reportTable{border-collapse:collapse;width:100%}#reportTable td,#reportTable th{border:1px solid #ddd;padding:.5em}#reportTable th{cursor:pointer;padding-top:.66em;padding-bottom:.66em;text-align:left;background-color:#8cba00;color:#fff}#reportTable tr:nth-child(2n){background-color:#f2f2f2}#reportTable tr:hover{background-color:#ddd}
"""

String getToggleSnoozeJS() {
   """
   async function toggleSnooze(deviceId) {
      event.preventDefault();
      const req = new Request(`../dac/toggleSnooze?access_token=${state.accessToken}`, {
         method: 'POST',
         mode: 'cors',
         redirect: 'follow',
         headers: {
            "Content-Type": "application/json"
         },
         body: JSON.stringify({ deviceId: +`\${deviceId}` })
      });
      const response = await fetch(req);
      const jsonData = await response.json();
      const devChkbox = document.getElementById(`snoozebox_dev_\${deviceId}`);
      if (jsonData?.status == "snoozed") {
         devChkbox.checked = true;
      }
      else if (jsonData?.status == "unsnoozed") {
         devChkbox.checked = false;
      }
      else {
         console.log("error setting snooze status");
      }
   }
   """
}

@Field static final String tableSortJS = """
function sortTable(e){var r,a,n,o,t,s,T,i,L=0;for(r=document.getElementById("reportTable"),n=!0,i="asc";n;){for(o=1,n=!1,a=r.rows;o<a.length-1;o++)if(T=!1,t=a[o].getElementsByTagName("TD")[e],s=a[o+1].getElementsByTagName("TD")[e],"asc"==i){if(t.innerHTML.toLowerCase()>s.innerHTML.toLowerCase()){T=!0;break}}else if("desc"==i&&t.innerHTML.toLowerCase()<s.innerHTML.toLowerCase()){T=!0;break}T?(a[o].parentNode.insertBefore(a[o+1],a[o]),n=!0,L++):0==L&&"asc"==i&&(i="desc",n=!0)}};
"""
