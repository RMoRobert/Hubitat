/**
 * ================  Ambient Weather/Ecowitt PWS (Driver) - Cloud or Local ===============
 *
 * Communicates to ambientweather.net (with your API and app keys) or local AmbientWeather console (on
 * firmware 1.6.9 or above, which added local server options) or local Ecowitt GW1000 gateway. Note that
 * AmbientWeather users with recent firmware no longer need the Ecowitt gateway to retrieve data locally.
 *
 * Communicates to ambientweather.net (with your API and app keys) or local AmbientWeather console (on
 * firmware 1.6.9 or above, which added local server options) or local Ecowitt GW1000 gateway. Note that
 * AmbientWeather users with recent firmware no longer need the Ecowitt gateway to retrieve data locally.
 *
 *  Copyright 2020-2021 Robert Morris
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
 * =======================================================================================
 *
 * ====== HOW TO INSTALL AND CONFIGURE DRIVER: ======
 *  - FOR LOCAL STATION DATA:
 *    1. Add this code to "Drivers Code" on Hubitat and save (if you haven't already added it)
 *    2. Ensure WS-2000 (or other compatible AmbientWeather console) is on at least firmware 1.6.9, which adds the "custom server"
*        configuration option. Ecowitt GW1000 and similar should be compatible out-of-box.
 *    3. On WS-2000 console, go to Settings > Weather Server > Customized, and configure with these settings:
 *       * State: Enable (to turn on this feature)
 *       * Protocol type: "Same as AMBWeather" (recommended; Wunderground or Ecowitt format should also work)
 *       * IP/Hostname: the IP address or hostname of your Hubitat hub
 *       * Port: 39501
 *       * Interval: your choice; however often you want the unit to send reports to Hubitat
 *       * Path: the default of "/data/report/" is fine, as the driver ignores this; the path must end with slash or be "/" (no quotes) itself
 *       * (if using Wunderground, recommend removing ".php" and ending path with slash also, e.g., "/weatherstation/"; station ID and key are ignored but
 *          likely must be included, possibly in a WU-valid format; again, AMBWeather format is recommended instead)
 *       As of this writing, this setup can only be done from the console itself and not the AmbientWeather mobile app. For Ecowitt devices,
 *       this configuration can be done from the WS View app; use similar configuration to the above for custom server.
 *    4. Create a new virtual device on Hubitat with the name of your choice. The Device Network ID should be set to the MAC address of
 *       your AmbientWeather console (or Ecowitt) in all caps with no separators, e.g., AABBCCDD1234
 *    5. Choose appropriate "Local" option for data source in device preferences, hit "Save Preferences," and verify information is received within
 *       configured reporting timeframe. Verify device configuration (e.g., hub IP and port) or check Hubitat logs if problems occur.
 *  - FOR CLOUD (AMBIENTWEATHER.NET) DATA:
 *    1. Add this code to "Drivers Code" on Hubitat and save (if you haven't already added it)
 *    2. Ensure WS-2000 console or other compatible device is configured to send data to your ambientweather.net account
 *    3. If you have not already, create an application key and an API key in your AmbientWeather.net account settings (currently: ambientweather.net/account)
 *    4. Create a new virtual device on Hubitat. The automatically generated Device Network ID is fine (but so is any unique DNI).
 *    6. Choose "Cloud" for data source in device preferences, hit "Save Preferences," and paste in both keys from above. Optionally, paste in AmbientWeather
 *       console MAC address (required if have more than one device on account; otherwise will read data from first device it finds). Save again.
 *       First data should be fetched within a few seconds and thereafter at scheduled interval per Hubitat preferences.
 *       Check Hubitat logs if problems occur.
 *
 * =======================================================================================
 *
 *  Last modified: 2021-08-08
 *
 *  Changelog:
 *  v2.1.1  - Added indoorTemperature attribute
 *  v2.1    - Additional attribute name matches
 *  v2.0    - Updated to handle local Ambient Weather "custom servers" (AMBWeather, Ecowitt, or WU format; AMBWeather or Ecowitt recommended)
 *          - Added separate handling for local vs. cloud connections and new local options for Ambient Weather
 *  v1.0    - Initial Release
 */

 import groovy.transform.Field

 @Field static Map<Long,Boolean> lastUpdateOK = [:]

metadata {
   definition (name: "Ambient Weather/Ecowitt PWS", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/ambientweather-ecowitt/ambient-ecowitt-pws-driver.groovy") {
      capability "Sensor"
      capability "TemperatureMeasurement"
      capability "RelativeHumidityMeasurement"
      capability "IlluminanceMeasurement"
      capability "PressureMeasurement"
      capability "UltravioletIndex"
      capability "Refresh"

      attribute "indoorTemperature", "NUMBER"
      attribute "feelsLike", "NUMBER"
      attribute "windSpeed", "NUMBER"
      attribute "windDirection", "STRING"
      attribute "windGust", "NUMBER"
      attribute "windGustDirection", "NUMBER"

      attribute "lastUpdated", "NUMBER"
      attribute "status", "string"
   }
   
   preferences() {
      input name: "source", type: "enum", title: "Station data source (do \"Save Preferences\" after changing this option)",
         options: [["local": "Local: Ambient Weather WS-2000 or compatible"],["localEW": "Local: Ecowitt GW1000 or compatible"],["cloud":"Cloud: AmbientWeather.net API"]], required: true
      if (source == "local" || source == "localEW") {
         // No preferences, but remove unneeded ones (can comment this out if you don't want that):
         device.removeSetting("legacyParsing")
         device.removeSetting("apiKey")
         device.removeSetting("appKey")
         device.removeSetting("macAddress")
         device.removeSetting("runEvery")
      }
      else if (source == "cloud") {
         input name: "apiKey", type: "string", title: "API key (required; see: https://ambientweather.net/account)"//, required: true
         input name: "appKey", type: "string", title: "Application key (required;  see: https://ambientweather.net/account)"//, required: true
         input name: "macAddress", type: "string", title: "MAC address (optional, required if more than one station on account; case-insensitive, colon-separated)", required: false
         input name: "runEvery", type: "enum", title: "Polling interval (refresh data every...)", required: true,
            options: ["5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "3 hours"], defaultValue: "15 minutes"
      }
      input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
      input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
   }   
}

void debugOff() {
   log.warn "Disabling debug logging"
   device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

void installed() {
   log.debug "Installed..."
   initialize()
}

void updated() {
   log.debug "Updated..."
   initialize()
}

void initialize() {
   log.debug "Initializing"
   unschedule()
   if (enableDebug) {
      Integer disableTime = 1800
      log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
      runIn(disableTime, debugOff)
   }
   if (source == "local" || source == "localEW") {
      if (enableDebug) log.debug "Data source is set to local. Should automatically receive reports from station if configured correctly. Check logs if not."
   }
   else if (source == "cloud") {
      if (enableDebug) log.debug "Data source is set to cloud."
      if (settings["apiKey"] && settings["appKey"]) {
         if (enableDebug) log.debug "Configuring polling every ${settings['runEvery']}..."
         switch (settings["runEvery"]) {
            case "5 minutes":
               runEvery5Minutes("refresh"); break
            case "10 minutes":
               runEvery10Minutes("refresh"); break
            case "15 minutes":
               runEvery15Minutes("refresh"); break
            case "30 minutes":
               runEvery30Minutes("refresh"); break
            case "1 hour":
               runEvery1Hour("refresh"); break
            case "3 hours":
               runEvery3Hours("refresh"); break
            default:
               runEvery30Minutes("refresh")
         }
         if (now() - (device.currentValue("lastUpdated") ?: 0) > 600000) {
            if (enableDebug) log.debug "Refreshing since has been longer than 10 minutes..."
            runIn(1, "refresh")
         }
      }
      else {
         log.warn "No app and/or API key configured. This driver will not function until properly configured."
      }
   }
}

void parse(String description) {
   if (enableDebug) log.debug "parse: ${description}"
   Map data = [:]
   if (source == "localEW") {
      // Only Ecowitt seems to handle this well so far. Ambient Weather puts all in headers and errors out on this
      // parse helper, so do that manually later instead:
      try {
         data = parseURLParameters(parseLanMessage(description).body)
      }
      catch (ex) {
         log.warn "Error in parse(): $ex"
      }
   }
   if (!data) {
      // So for Ambient Weather, instead do manual parsing in a few steps:
      // 1. Extract (encoded) "headers:" from LAN message: tokenize, find "headers:", return this portion
      // as (still-encoded) String:
      String headers = description.split(",")?.collect({ it.trim() })?.find({ it.startsWith ("headers:") }).substring(8)
      // 2. Extract full URI from GET (bit hacky but should work for this data)--take second whitespace-separated
      // part of raw string after decoding:
      String rawData = new String(URLDecoder.decode(headers).decodeBase64(), "UTF-8").split("\\s+")[1]
      // 3. Strip off path (must end with "/" as default does--device also seems to also let you configure without,
      // which won't work (here or anywhere):
      rawData = rawData.substring(rawData.lastIndexOf("/")+1)
      // 4. Should now be "&"-separated list of "key=value" items, so split into Map and we're done!
      data = parseURLParameters(rawData)
   }
   parseWeather(data)
   lastUpdateOK[device.deviceId] = true
}

Map parseURLParameters(String parameters) {
      List keysAndVals = parameters.contains("&") ? parameters.split("&") : []
      Map data = keysAndVals.collectEntries( { [(URLDecoder.decode(it.split("=")[0])):
                                             URLDecoder.decode(it.split("=")[1])] })
      return data
}

void refresh() {
   if (enableDebug) log.debug "refresh()"
   if (source == "cloud") {
      if (enableDebug) log.debug "Connection type is cloud. Preparing to query ambientweather.net API..."
      String mac = settings["macAddress"]?.toLowerCase()
      String uri
      if (mac) {
         uri = "https://api.ambientweather.net/v1/devices/${mac}?applicationKey=${settings['appKey']}&apiKey=${settings['apiKey']}&limit=1"
      }
      else {
         uri = "https://api.ambientweather.net/v1/devices?applicationKey=${settings['appKey']}&apiKey=${settings['apiKey']}"
      }
      Map params = [
         uri: uri,
         contentType: "application/json",
         timeout: 20
      ]
      try {
         asynchttpGet("parseRefreshResponse", params, [withMAC: mac ? true : false])
      }
      catch (Exception ex) {
         log.error "Error in refresh(): $ex"
      }
      runIn(25, "updateOnlineStatus")
   }
   else {
      log.warn "Local data connections do not support refresh(); information is pushed from device to Hubitat per device configuration."
   }
}

void parseRefreshResponse(response, data) {
   if (response?.status == 200 && response?.json) {
      Map wxData
      if (data?.withMAC == true) {
         wxData = response.json[0]
      }
      else {
         wxData = response.json[0].lastData
      }
      parseWeather(wxData)
      lastUpdateOK[device.deviceId] = true
   }
   else {
      lastUpdateOK[device.deviceId] = false
      log.warn "Unexpected response: ${response?.data}. HTTP ${response?.status}"
   }
}

void parseWeather(Map wxData) {
   if (enableDebug) log.debug "parseWeather(wxData = $wxData)"
   String eventName
   def eventValue
   wxData.each { key, val ->
      switch (key) {
         case "tempf":
            eventName = "temperature"
            eventValue = convertTemperatureIfNeeded(val as BigDecimal,"f",1)
            doSendEvent(eventName, eventValue, "°${location.temperatureScale}")
            break
         case "tempinf":
            eventName = "indoorTemperature"
            eventValue = convertTemperatureIfNeeded(val as BigDecimal,"f",1)
            doSendEvent(eventName, eventValue, "°${location.temperatureScale}")
            break
         case "humidity":
            eventName = "humidity"
            eventValue = val as BigDecimal
            doSendEvent(eventName, eventValue, "%")
            break
         case "baromrelin":
            eventName = "pressure"
            eventValue = (val as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)
            doSendEvent(eventName, eventValue, "inHg")
            break
         case "solarradiation":
            eventName = "illuminance"
            eventValue = Math.round(val as BigDecimal)
            doSendEvent(eventName, eventValue, "lux")
            break
         case "windchillf": // WU
         case "heatindexf": // WU
         case "feelsLike":  // AW
            eventName = "feelsLike"
            eventValue = convertTemperatureIfNeeded(val as BigDecimal,"f",1)
            doSendEvent(eventName, eventValue, "°${location.temperatureScale}")
            break
         case "windspeedmph":
            eventName = "windSpeed"
            eventValue = val as BigDecimal
            doSendEvent(eventName, eventValue, "mph")
            break
         case "winddir":
            eventName = "windDirection"
            eventValue = degreesToDirection(val as Integer)
            doSendEvent(eventName, eventValue)
            break
         case "windgustmph":
            eventName = "windGust"
            eventValue = val as BigDecimal
            doSendEvent(eventName, eventValue, "mph")
            break
         case "windgustdir":
            eventName = "windGustDirection"
            eventValue = degreesToDirection(val as Integer)
            doSendEvent(eventName, eventValue)
            break
         case "UV":  // WU
         case "uv":  // AW
            eventName = "ultravioletIndex"
            eventValue = val as Integer
            doSendEvent(eventName, eventValue)
            break
         case "dateutc":
            eventName = "lastUpdated"
            if (val instanceof String) {
               // Cloud API sends Long with Unix time, but local API sends ISO-ish date as String. Convert:
               if (val == "now") val = now() // Ecowitt with WU may do this
               else val = toDateTime(val.replace(" ","T") + "Z").getTime()
            }
            sendEvent(name: eventName, value: val)
            runIn(2, "updateOnlineStatus")
            break
         default:
            if (enableDebug) log.debug "skipping key: $key: $val"
      }
   }
}

/**
 *  Converts degrees (0-360) to cardinal direction string like "N" or "ESE" for wind
 */
String degreesToDirection(Integer degrees) {
   switch (degrees) {
      case 0..10:
         eventValue = "N"; break
      case 11..33:
         eventValue = "NNE"; break
      case 34..55:
         eventValue = "NE"; break
      case 56..78:
         eventValue = "ENE"; break
      case 79..100:
         eventValue = "E"; break
      case 101..123:
         eventValue = "ESE"; break
      case 124..145:
         eventValue = "SE"; break
      case 146..168:
         eventValue = "SSE"; break
      case 169..190:
         eventValue = "S"; break
      case 191..213:
         eventValue = "SSW"; break
      case 214..235:
         eventValue = "SW"; break
      case 236..258:
         eventValue = "WSW"; break
      case 259..280:
         eventValue = "W"; break
      case 281..303:
         eventValue = "WNW"; break
      case 304..325:
         eventValue = "NW"; break
      case 326..349:
         eventValue = "NNW"; break
      case 349..360:
         eventValue = "N"; break
      default:
         eventValue = "unknown"
   }
}

void updateOnlineStatus() {
   if (enableDebug) log.debug "updateOnlineStatus()"
   Integer inactivityThreshold = 300000
   switch (settings["runEvery"]) {
      case "5 minutes":
      case "10 minutes":
         inactivityThreshold = 15 * 60 * 1000; break
      case "15 minutes":
         inactivityThreshold = 30 * 60 * 1000; break
      case "30 minutes":
         inactivityThreshold = 45 * 60 * 1000; break
      case "1 hour":
         inactivityThreshold = 90 * 60 * 1000; break
      case "3 hours":
         inactivityThreshold = 270 * 60 * 1000; break
      default:
         inactivityThreshold = 45 * 60 * 1000
   }
   if (now() - (device.currentValue("lastUpdated") ?: 0) <= inactivityThreshold && lastUpdateOK[device.deviceId] != false) {
      doSendEvent("status", "online")
   }
   else {
      doSendEvent("status", "offline")
   }
}

private void doSendEvent(String eventName, eventValue, String eventUnit=null) {
   //if (enableDebug) log.debug "doSendEvent(eventName: $eventName, eventValue: $eventValue, eventUnit: $eventUnit)..."
   String descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit != null ? ' ' + eventUnit : ''}"
   if (enableDesc && ("${device.currentValue(eventName)}" != "$eventValue")) log.info descriptionText
   if (eventUnit != null) sendEvent(name: eventName, value: eventValue, eventUnit: eventUnit, descriptionText: descriptionText)
   else sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText)
}