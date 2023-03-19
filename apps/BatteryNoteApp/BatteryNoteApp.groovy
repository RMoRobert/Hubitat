/**
 *  Battery Note App for Hubitat
 *  Description: Tracks battery replacement and other information (e.g., battery size) for devices on your
 *               Hubitat Elevation system in the app.
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
 *  Changes:
 *   2023-03-19: Add device list sorting and filtering options
 *   2023-01-18: Initial release
 *
 */
 
import groovy.transform.Field
import com.hubitat.app.DeviceWrapper

@Field static final Boolean logEnable = true

// Device type filters:
@Field static final String strBattery = "capability.battery"
@Field static final String strDefaultFilter = "capability.battery"
@Field static final String strAll = "capability.*"

// Battery sizes
@Field static final List<String> defaultBatteryTypes = ["CR123A", "CR2", "CR2032", "CR2450", "CR2477", "CR1632", "A", "AA", "AAA", "AAAA", "1/2 AA", "9V"]

definition(
   name: "Battery Note App",
   namespace: "RMoRobert",
   author: "RMoRboert",
   description: "Track battery replacement dates and other information for devices",
   category: "Convenience",
   iconUrl: "",
   iconX2Url: "",
   iconX3Url: ""
)

preferences {
   page name: "mainPage"
   page name: "devicePage"
}

def mainPage() {
   Set<String> settingNames = settings.keySet()?.findAll { 
      it.startsWith("btnAddTodayDate_") || it.startsWith("btnAddSpecificDate_") || it.startsWith("lastReplaced_")
   }
   settingNames.each { String sName ->
      app.removeSetting(sName)
   }
   dynamicPage(name:"mainPage", title:"Battery Note App", install: true, uninstall: true) {
      List<Map> allRooms = getRooms()
      List<Map> roomList = allRooms.collect { [(it.id): it.name] }
      section(styleSection("Select devices")) {
         input "isFilter", "bool", title: "Use custom device list filter?", submitOnChange: true, width: 8
         if (isFilter) {
            input "filterBy", "enum", title: "Filter device list by:", options: [[(strBattery): "Battery capability only"], [(strAll): "List all devices"]],
               defaultValue: strDefaultFilter, submitOnChange: true, width: 4
            paragraph "NOTE: Changing any options above after selecting devices may result in needing to re-select devices."
         }
         String deviceListSelector = strDefaultFilter
         if (isFilter && filterBy) deviceListSelector = filterBy
         input "devices", deviceListSelector, title: "Select devices", multiple: true, submitOnChange: true
      }
      section(styleSection("Individual Devices")) {
         paragraph "To make a change on a single device at a time, select the device from the list below."
         input "showRoom", "enum", title: "Filter list by room", submitOnChange: true, width: 6,
            options: roomList.sort { m1, m2 -> m1.value <=> m2.value }, defaultValue: ""
         input "sortBy", "enum", title: "Sort by", submitOnChange: true, width: 6,
            options: [["dispName": "Display name"],["batt": "Battery level (low to high)"],["lastAct": "Last activity at (oldest first)"]],
            defaultValue: "dispName"
         // Filter list (or not) according to options:
         List<DeviceWrapper> filteredDevices
         if (showRoom != null && showRoom != "") {
            filteredDevices = devices.findAll { DeviceWrapper d -> d.roomId == (showRoom as Long) }
         }
         else {
            filteredDevices = devices
         }
         // Sort list according to options
         switch(settings["sortBy"]) {
            case "batt":
               filteredDevices?.sort { DeviceWrapper d -> d.currentValue("battery") }
               break
            case "lastAct":
               filteredDevices?.sort { DeviceWrapper d -> d.getLastActivity() }
               break
            default: // including display name
               filteredDevices?.sort { DeviceWrapper d -> d.displayName }
         }
         // Display list:
         filteredDevices.each { DeviceWrapper dev ->
            href name: "hrefDevicePage", page: "devicePage", title: dev.displayName, description: "", params: ["deviceId": dev.id]
         }
      }
      section(styleSection("History Size")) {
         input "historySize", "number", title: "Number of battery history entries to store for device (default: 10)", defaultValue: 10, required: true,
            range: "1..100"
      }
      section(styleSection("Name and Logging")) {
         label title: "Customize installed app name:", required: true
         input "logEnable", "bool", title: "Enable debug logging"
      }
      section() {
         paragraph "<small>NOTE: Do not open multiple pages from this app in different tabs/windows at the same time to avoid unexpected problems.<small>"
      }
   }
}

def devicePage(Map params) {
   if (params) state.devicePageParams = params
   DeviceWrapper dev = devices.find { DeviceWrapper dev -> dev.id == state.devicePageParams?.deviceId}
   dynamicPage(name:"devicePage", title: dev.displayName, nextPage: "mainPage") {
      section(styleSection("Battery Type")) {
         if (isCustomBattery) {
            input "batteryType_${dev.id}", "string", title: "Battery type:"
         }
         else {
            input "batteryType_${dev.id}", "enum", options: defaultBatteryTypes, title: "Battery type:"
         }
         input "isCustomBattery_${dev.id}", "bool", title: "Custom battery type?", submitOnChange: true
         input "isRechargeable_${dev.id}", "bool", title: "Rechargeable?", submitOnChange: true
      }
      section(styleSection("Add ${isRechargeable? 'Recharge' : 'Replacement'} Date")) {
         String dateText = isRechargeable ? "Add battery-recharged date" : "Add battery replacement date"
         //paragraph dateText
         input "replacementType", "enum", options: ["Today", "Specific Date"], defaultValue: "Today", submitOnChange: true
         if (replacementType == "Today" || replacementType == null) {
            app.clearSetting("lastReplaced_${dev.id}")
            input "btnAddTodayDate_${dev.id}", "button", title: "Add today as ${isRechargeable? 'recharge' : 'replacement'} date"
         }
         else {
            paragraph "To add a battery replacement date: 1) choose date, 2) select <b>Update</b>, then 3) select <b>Add as ${isRechargeable? 'recharge' : 'replacement'} date</b>."
            input "lastReplaced_${dev.id}", "date", title: dateText, width: 4
            input "btnSave", "button", title: "Update", width: 2
            paragraph "<-&nbsp;&nbsp;select <b>Update</b> to save selection <em>before</em> adding date!", width: 6
            String selDate = settings."lastReplaced_${dev.id}"
            if (selDate) {
               input "btnAddSpecificDate_${dev.id}", "button", title: "Add ${selDate} as ${isRechargeable? 'recharge' : 'replacement'} date"
            }
            //ladss lastReplaced
         }
      }
      section(styleSection("Battery History")) {
         StringBuilder sb = new StringBuilder()
         state.replacementDates?.get(dev.id)?.each { String isoDateStr ->
            sb << "<li>$isoDateStr</li>"
         }
         if (sb) {
            paragraph "<ul>${sb.toString()}</ul>"
         }
         else {
            paragraph "No battery history found for ${dev.displayName}"
         }
         input "btnRemoveHistory_${dev.id}", "button", title: "Remove All History for ${dev.displayName}"
      }
      section(styleSection("Save")) {
         input "btnSaveDevice", "button", title: "Save", submitOnChange: true
      }
   }
}

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   //unsubscribe()
   //unschedule()
   initialize()
}

void initialize() {
   log.debug "initialize()"
}

String styleSection(String sectionHeadingText) {
   return """<span style="font-weight:bold; font-size: 115%">$sectionHeadingText</span>"""
}

void appButtonHandler(String btn) {
   log.debug "appButtonHandler($btn)"
   switch (btn) {
      case "btnSave":
         // just commit settings
         break
      case { it.startsWith "btnAddTodayDate_" }:
         String devId = btn -  "btnAddTodayDate_"
         String strCurrDate = new Date().format("YYYY-MM-dd")
         addReplacementDateForDevice(devId, strCurrDate)
         break
      case { it.startsWith "btnAddSpecificDate_" }:
         String devId = btn -  "btnAddSpecificDate_"
         addReplacementDateForDevice(devId, settings."lastReplaced_${devId}")
         break
      case { it.startsWith "btnRemoveHistory_" }:
         String devId = btn -  "btnRemoveHistory_"
         removeAllReplacementDatesForDevice(devId)
         break
      default:
         log.warn "Unhandled button: $btn"
   }
}

void addReplacementDateForDevice(String deviceId, String isoDate) {
   if (logEnable) log.trace "addReplacementDateForDevice($deviceId, $isoDate)"
   List<String> replacementDates = state.replacementDates?.get(deviceId)
   if (!replacementDates) {
      if (!state.replacementDates) {
         state.replacementDates = new HashMap<Long,List<String>>()
      }
      replacementDates = new ArrayList<String>()
   }
   replacementDates << isoDate
   replacementDates = replacementDates.sort().reverse()
   if (replacementDates.size() > (historySize ?: 10)) {
      replacementDates = replacementDates.take(historySize as Integer)
   }
   state.replacementDates[deviceId] = replacementDates
}

void removeAllReplacementDatesForDevice(String deviceId) {
   if (logEnable) log.trace "removeAllReplacementDatesForDevice($deviceId, $isoDate)"
   state.replacementDates[deviceId] = new ArrayList<String>()
}
