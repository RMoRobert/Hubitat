/**
 * =============================  Ambient Weather/Ecowitt PWS (Driver) ===============================
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
 * =======================================================================================
 *
 *  Last modified: 2020-12-26
 *
 *  Changelog:
 *  v1.0    - Initial Release. NOTE: Only handles Ambient Weather for now; Ecowitt coming soon...
 */

 import groovy.transform.Field

 @Field static Map<Long,Boolean> lastUpdateOK = [:]

metadata {
   definition (name: "Ambient Weather/EcoWitt PWS", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/ambientweather-ecowitt/ambient-ecowitt-pws-driver.groovy") {
      capability "Sensor"
      capability "TemperatureMeasurement"
      capability "RelativeHumidityMeasurement"
      capability "IlluminanceMeasurement"
      capability "PressureMeasurement"
      capability "UltravioletIndex"
      capability "Refresh"

      attribute "feelsLike", "NUMBER"
      attribute "windSpeed", "NUMBER"
      attribute "windGust", "NUMBER"
      attribute "windDirection", "STRING"

      attribute "lastUpdated", "NUMBER"
      attribute "status", "string"
   }
   
   preferences() {
      input name: "apiKey", type: "string", title: "API key (see: https://ambientweather.net/account)", required: true
      input name: "appKey", type: "string", title: "Application key (see: https://ambientweather.net/account)", required: true
      input name: "macAddress", type: "string", title: "MAC address (optional, required if more than one station on account; case-insensitive, colon-separated)", required: false
      input name: "runEvery", type: "enum", title: "Polling interval (refresh data every...)", required: true,
         options: ["5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "3 hours"], defaultValue: "15 minutes"
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
   Integer disableTime = 1800
   log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
   if (enableDebug) runIn(disableTime, debugOff)
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

// Probably won't happen with this LAN driver, but...
void parse(String description) {
   log.warn("Ignoring parse() for: '${description}'")
}

void refresh() {
   if (enableDebug) log.debug "refresh()"
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

void parseRefreshResponse(response, data) {
   if (response?.json && response?.status == 200) {
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
   String eventName
   def eventValue
   wxData.each { key, val ->
      switch (key) {
         case "tempf":
            eventName = "temperature"
            eventValue = convertTemperatureIfNeeded(val,"f",1)
            doSendEvent(eventName, eventValue, "°${location.temperatureScale}")
            break
         case "humidity":
            eventName = "humidity"
            eventValue = val
            doSendEvent(eventName, eventValue, "%")
            break
         case "baromrelin":
            eventName = "pressure"
            eventValue = val
            doSendEvent(eventName, eventValue, "inHg")
            break
         case "solarradiation":
            eventName = "illuminance"
            eventValue = val
            doSendEvent(eventName, eventValue, "lux")
            break
         case "feelsLike":
            eventName = "feelsLike"
            eventValue = convertTemperatureIfNeeded(val,"f",1)
            doSendEvent(eventName, eventValue, "°${location.temperatureScale}")
            break
         case "windspeedmph":
            eventName = "windSpeed"
            eventValue = val
            doSendEvent(eventName, eventValue, "mph")
            break
         case "windgustmph":
            eventName = "windGust"
            eventValue = val
            doSendEvent(eventName, eventValue, "mph")
            break
         case "winddir":
            eventName = "windDirection"
            switch (val as Integer) {
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
            doSendEvent(eventName, eventValue)
            break
         case "dateutc":
            eventName = "lastUpdated"
            eventValue = val
            doSendEvent(eventName, eventValue)
            runIn(2, "updateOnlineStatus")
            break
         default:
            if (enableDebug) log.debug "skipping key: $key"
      }
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
   String descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit != null ? eventUnit : ''}"
   if (enableDesc && (device.currentValue(eventName) != eventValue)) log.info descriptionText
   if (eventUnit != null) sendEvent(name: eventName, value: eventValue, eventUnit: eventUnit, descriptionText: descriptionText)
   else sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText)
}