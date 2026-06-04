/**
 * =================  Device Activity Check Group Child =================
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
 * 3.0   (2026-05-24) - Child app for device groups (install via parent app only)
 *
 */

import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

@Field static final String sACTIVITY = "activity"
@Field static final String sHEALTH_STATUS = "healthStatus"
@Field static final String sBATTERY = "battery"

definition(
   name: "Device Activity Check 3 Group Child",
   namespace: "RMoRobert",
   author: "Robert Morris",
   description: "Install only via the Device Activity Check parent app",
   category: "Convenience",
   parent: "RMoRobert:Device Activity Check 3",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: "",
   documentationLink: "https://community.hubitat.com/t/release-device-activity-check-get-notifications-for-inactive-devices/42176"
)

preferences {
   page name: "pageMain"
}

Map pageMain() {
   dynamicPage(name: "pageMain", title: "Device Activity Check Group", uninstall: true, install: true) {
      section(styleSection("Name")) {
         input name: "customLabel", type: "text", title: "Name this device group:", defaultValue: app.label ?: "Device Activity Check Group", submitOnChange: true, required: true
         if (customLabel) {
            app.updateLabel(customLabel)
         }
      }
      section(styleSection("Inactivity Threshold")) {
         input name: "inactivityMethod", title: "Inactivity detection method:", type: "enum",
            options: [[(sACTIVITY): "\"Last Activity At\" timestamp"], [(sBATTERY): "Battery level"], [(sHEALTH_STATUS): "\"healthStatus\" attribute"]],
            defaultValue: sACTIVITY, required: true, submitOnChange: true
         if (settings.inactivityMethod == sACTIVITY || settings.inactivityMethod == null) {
            paragraph "Consider selected devices inactive if they have not had activity within..."
            input name: "intervalD", type: "number", title: "days", description: "", submitOnChange: true, width: 2
            input name: "intervalH", type: "number", title: "hours", description: "", submitOnChange: true, width: 2
            input name: "intervalM", type: "number", title: "minutes*", description: "", submitOnChange: true, width: 2
            paragraph """${(settings.intervalD || settings.intervalH || settings.intervalM) ?
               '<strong>Total time:</strong>\n' + daysHoursMinutesToString(settings.intervalD, settings.intervalH, settings.intervalM) :
               ''}""", width: 6
            if (!(settings.intervalD || settings.intervalH || settings.intervalM)) {
               paragraph "*At least one of: days, hours, or minutes is required"
            }
         }
         else if (settings.inactivityMethod == sBATTERY) {
            input name: "batteryLevel", type: "number", title: "Include in report if battery level is less than:", range: "1..100"
         }
         else if (settings.inactivityMethod == sHEALTH_STATUS) {
            paragraph "Devices will be considered inactive if the value of the \"healthStatus\" attribute is \"offline\" at the time " +
               "of evaluation. Note that if your report is configured to display the \"Last Activity\" date, this date/time may not " +
               "necessarily correspond to actual device communication, depending on how the device driver works."
            if (settings.devices?.any { !(it.hasAttribute("healthStatus")) }) {
               paragraph "<strong>Warning: the following devices do not report a \"healthStatus\" attribute. De-select them or verify the correct driver or inactivity " +
                  """detection method:</strong> ${settings.devices?.findAll { !(it.hasAttribute("healthStatus")) }.join(", ")}"""
            }
         }
         else {
            paragraph "Please select a valid \"inactivity detection method\" option."
         }
      }

      section(styleSection("Choose Devices")) {
         String capabilityFilter
         switch (settings.inactivityMethod) {
            case sHEALTH_STATUS:
               capabilityFilter = "capability.*"
               break
            case sBATTERY:
               capabilityFilter = "capability.battery"
               break
            default:
               capabilityFilter = "capability.*"
         }
         input name: "devices", type: capabilityFilter, multiple: true, title: "Select devices to monitor", showFilter: true, submitOnChange: true
      }

      if (settings.devices && (settings.inactivityMethod == sACTIVITY || settings.inactivityMethod == null)) {
         section(styleSection("Last Activity Data")) {
            if (settings.devices.any { it.controllerType == "ZGB" }) {
               input name: "useZigbeeInfo", type: "bool", title: "Supplant \"Last Activity At\" data with Zigbee Details \"Last Message\" data for Zigbee devices if available"
            }
            if (settings.devices.any { it.controllerType == "ZW" }) {
               input name: "useZwaveInfo", type: "bool", title: "Supplant \"Last Activity At\" data with Z-Wave Details \"Last Message\" data for Z-Wave devices if available"
            }
         }
      }

      if (settings.devices && settings.inactivityMethod == sACTIVITY) {
         section(styleSection("Refresh Options")) {
            List<Map<Long, String>> rdevList = []
            settings.devices.each {
               if (it.hasCommand("refresh")) rdevList << [(it.getIdAsLong()): it.getDisplayName()]
            }
            rdevList.sort { a, b -> a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value }
            paragraph "Use the option below only if you have devices that do not regularly \"check in\" and respond to a refresh. (You must know your specific device behavior, but generally all powered devices and most battery-powered Zigbee devices will respond, while non-FLiRS Z-Wave battery devices will not.) Device Activity Check will send a \"Refresh\" command to the device before running a report, which may give a better indication of whether the device is still responsive. Be careful not to choose too many devices or devices that do not respond on-demand to refresh commands."
            input name: "refreshDevs", title: "Refresh before running report:", type: "enum", options: rdevList, multiple: true, submitOnChange: true
            if (settings.refreshDevs) {
               paragraph "<strong>Note:</strong> If any devices need to be refreshed (only devices deemed inactive will be), notifications may be delayed. The post-refresh delay is configured on the parent app."
            }
         }
      }
   }
}

// Called by parent app
Boolean usesZigbeeInfo() {
   return settings.useZigbeeInfo == true
}

// Called by parent app
Boolean usesZwaveInfo() {
   return settings.useZwaveInfo == true
}

// Called by parent app
List<DeviceWrapper> getInactiveDevices(Boolean onlyDevicesToBeRefreshed=false) {
   List<DeviceWrapper> inactiveDevices = []
   List<DeviceWrapper> groupDevs = settings.devices ?: []
   String detectionMethod = settings.inactivityMethod

   if (onlyDevicesToBeRefreshed && detectionMethod != sACTIVITY) {
      return []
   }
   if (parent?.getBoolIncludeDisabled() != true) {
      groupDevs = groupDevs.findAll { it.isDisabled() != true }
   }
   switch (detectionMethod) {
      case sACTIVITY:
         if (onlyDevicesToBeRefreshed && !settings.refreshDevs) {
            break
         }
         Long inactiveMinutes = daysHoursMinutesToMinutes(settings.intervalD, settings.intervalH, settings.intervalM)
         Long cutoffEpochTime = now() - (inactiveMinutes * 60000)
         groupDevs.each { DeviceWrapper dev ->
            if (onlyDevicesToBeRefreshed && !(dev.getId() in settings.refreshDevs)) {
               return
            }
            Date lastActivityOrMessageDate = parent.getLastActivityOrMessageDateForDevice(dev, settings.useZigbeeInfo == true, settings.useZwaveInfo == true)
            if (lastActivityOrMessageDate?.getTime() <= cutoffEpochTime) {
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
      case sBATTERY:
         Integer level = settings.batteryLevel ?: 0
         groupDevs.each { DeviceWrapper dev ->
            if (dev.currentValue("battery") <= level) {
               inactiveDevices << dev
            }
         }
         break
      default:
         log.debug "Ignoring unsupported detection method $detectionMethod in ${app.label}"
   }
   return inactiveDevices
}

// Called by parent app
Map<DeviceWrapper,List<String>> getInactiveDeviceMap(Boolean isReportPage=false) {
   Map<DeviceWrapper,List<String>> inactiveDeviceMap = [:]
   String detectionMethod = settings.inactivityMethod
   getInactiveDevices(false).each { DeviceWrapper inactiveDev ->
      if (!parent.checkIfSnoozed(inactiveDev.id) || isReportPage) {
         String strStatus
         switch (detectionMethod) {
            case sACTIVITY:
               String timeFormat = parent.getTimeFormat(isReportPage)
               Date lastActivityOrMessageDate = parent.getLastActivityOrMessageDateForDevice(inactiveDev, settings.useZigbeeInfo == true, settings.useZwaveInfo == true)
               strStatus = lastActivityOrMessageDate?.format(timeFormat, location.timeZone)
               if (!strStatus) strStatus = "No activity reported"
               break
            case sHEALTH_STATUS:
               strStatus = "offline"
               break
            case sBATTERY:
               strStatus = "${inactiveDev.currentValue('battery')}% battery"
               break
            default:
               strStatus = "unknown"
         }
         if (inactiveDeviceMap[inactiveDev]) {
            inactiveDeviceMap[inactiveDev] << strStatus
         }
         else {
            inactiveDeviceMap[inactiveDev] = [strStatus]
         }
      }
   }
   return inactiveDeviceMap
}

// Called by parent app
Integer performRefreshes(Boolean returnCountOnlyAndDoNotRefresh=false) {
   List<DeviceWrapper> toRefreshDevices = getInactiveDevices(true)
   if (!toRefreshDevices) {
      return 0
   }
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
   return (toRefreshDevices.size() * 200 + parent.getPostRefreshDelayMs()) / 1000
}

// Called by parent app
Boolean isRefreshConfigured() {
   return settings.inactivityMethod == sACTIVITY && settings.devices && settings.refreshDevs
}

// Called by parent app
String getDeviceGroupDescription() {
   String desc = ""
   settings.devices?.sort { it.displayName }?.each { DeviceWrapper dev ->
      desc += "${dev.displayName}\n"
   }
   return desc
}

// Called by parent app to show summary
String getInactivityMethodDisplayString() {
   switch (settings.inactivityMethod) {
      case sACTIVITY:
      case null:
         return "\"Last Activity At\" timestamp"
      case sBATTERY:
         return "Battery level"
      case sHEALTH_STATUS:
         return "\"healthStatus\" attribute"
      default:
         return "Unknown"
   }
}

// Called by parent app
String getInactivityThresholdString() {
   if (settings.inactivityMethod == sACTIVITY || !settings.inactivityMethod) {
      return daysHoursMinutesToString(settings.intervalD, settings.intervalH, settings.intervalM)
   }
   if (settings.inactivityMethod == sBATTERY) {
      Integer level = settings.batteryLevel ?: 0
      return "if battery &le; $level"
   }
   if (settings.inactivityMethod == sHEALTH_STATUS) {
      return "if offline"
   }
   return "(invalid configuration; please verify)"
}

void verifyAndLogMissingCapabilities() {
   if (settings.inactivityMethod == sBATTERY) {
      settings.devices?.each {
         if (!(it.hasAttribute("battery"))) log.warn "Device $it in ${app.label} uses battery detection but does not support battery attribute"
      }
   }
}

Long daysHoursMinutesToMinutes(Long days, Long hours, Long minutes) {
   return (minutes ?: 0) + (hours ? hours * 60 : 0) + (days ? days * 1440 : 0)
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

String styleSection(String sectionHeadingText) {
   return """<div style="font-weight:bold; font-size: 120%">$sectionHeadingText</div>"""
}

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated()"
   initialize()
   verifyAndLogMissingCapabilities()
}

void initialize() {
   log.debug "initialize()"
}
