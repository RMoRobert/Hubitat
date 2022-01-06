/**
 * =========================  HASS Alexa TTS Proxy (Driver) ==========================
 *
 *  DESCRIPTION:
 *  Basic driver to sent TTS to Alexa through Home Assistant's Alexa Media Player integeration
 *  from Hubitat
 *
 *  Copyright 2022 Robert Morris
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  For documentation and discussion, please see:
 *  https://community.hubitat.com/t/release-home-assistant-alexa-tts-proxy-another-alexa-tts-option/86834
 *
 * =======================================================================================
  * 
 *  Changelog:
 *  v1.0 (2022-01-05) - Initial Release
 *
 */ 


metadata {
   definition (name: "HASS Alexa TTS Proxy", namespace: "RMoRobert", author: "Robert Morirs") {
      capability "Actuator"
      capability "SpeechSynthesis"
      attribute "lastText", "STRING"
   }

   preferences() {
      input "hassIP", "text", title: "Home Assistant URI (include protocol and port if necessary, e.g, http://192.168.1.23:8123)", required: true
      input "hassToken", "password", title: "Home Assistant long-lived access token", required: true
      input "hassService", "text", title: "Home Assistant domain/service (e.g., notify.alexa_media_everywhere)", defaultValue: "notify.alexa_media_YOUR_DEVICE_NAME"
      input "enableDebug", "bool", title: "Enable debug logging"
   }
}

void installed() {
   initialize()
}

void updated() {
   initialize()
}

void initialize() {
   log.debug "initialize()"
}

void speak(String text, Number volume=null, String voice=null) {
   if (enableDebug) log.debug "speak(${text})"
   if (volume != null && enableDebug) log.warn "volume not currently implemented; ignoring volume"
   sendEvent(name: "lastText", value: text, isStateChange: true)
   callNotificationService(text)
}

/** Sends request for username creation to Bridge API. Intended to be called after user
 *  presses link button on Bridge
 */
void callNotificationService(String message) {
   if (enableDebug) "callNotificationService(...)"
   def (String domain, String service) = hassService.split("\\.", 2)
   Map params = [
      uri: "${hassIP}",
      requestContentType: "application/json",
      contentType: "application/json",
      headers: [Authorization: "Bearer ${hassToken}"],
      path: "/api/services/${domain}/${service}",
      body: [data: [type: "announce"], message: message],
      timeout: 15
   ]
   if (enableDebug) log.debug "Doing POST with params: $params"
   asynchttpPost("callbackForCallNotificationService", params, null)
}

void callbackForCallNotificationService(hubitat.scheduling.AsyncResponse resp, Map data=null) {
   if (enableDebug) log.debug "callbackForCallNotificationService(...)"
   if (resp.status == 200 || resp.status == 201) {
      if (enableDebug) log.debug "HTTP ${resp.status}"
   }
   else {
      log.error "HTTP ${resp.status}"
      log.error "${resp.data}"
   }
}