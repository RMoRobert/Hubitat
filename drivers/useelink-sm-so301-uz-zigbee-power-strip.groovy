/*
 * ==================  UseeLink SM-SO301-UZ Zigbee Power Strip Driver ====================
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
 *  Last modified: 2020-04-22
 * 
 *  Changelog:
 *  v1.0.2  - Update on() and off() to use endpint 0xFF instead of iterating overall
 *  v1.0.1  - Ensure parent switch attribute gets set; child command fixes
 *  v1.0    - Initial Release
 */

import groovy.transform.Field

@Field static Map <String, String> childEndpoints = ['01': 'Outlet 1', '02': 'Outlet 2', '03': 'Outlet 3',
                                                       '04': 'Outlet 4', '07': 'USB Ports']

metadata {
    definition(name: "UseeLink SM-SO301-UZ Zigbee Power Strip", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/useelink-sm-so301-uz-zigbee-power-strip.groovy") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"

        command "componentOn", [[name: "channel", type: "NUMBER"]]
        command "componentOff", [[name: "channel", type: "NUMBER"]]

        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,000A,0004,0005,0006", outClusters: "0019", model: "TS0115", manufacturer: "_TYZB01_vkwryfdr"
    }

    preferences {
        input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true)
        input(name: "createChildDevices", type: "bool", title: "Create child devices for each outlet", defaultValue: true)
    }
}

def installed() {
    log.debug "Installed..."
    initialize()
}

def updated() {
    log.debug "Updated..."
    initialize()
}

def initialize() {
    log.debug "Initializing"
    int disableTime = 1800
    if (enableDebug) {
        log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
        runIn(disableTime, debugOff)
    }
    if (settings['createChildDevices'] || settings['createChildDevices'] == null) {
        createChildDevices()
    }
}

def debugOff() {
    log.warn("Disabling debug logging")
    device.updateSetting("enableDebug", [value: "false", type: "bool"])
}

def parse(String description) {
    logDebug("parse($description)")
    def eventMap = zigbee.getEvent(description)
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (eventMap && !description.startsWith('catchall')) {
        if (descMap?.sourceEndpoint in childEndpoints.keySet || descMap?.endpoint in childEndpoints.keySet) {
            def childDevice = childDevices.find {
                it.deviceNetworkId == "${device.id}-CH${descMap.sourceEndpoint}" ||
                it.deviceNetworkId == "${device.id}-CH${descMap.endpoint}"
            }
            if (childDevice) {
                logDebug("Child device found; sending event map for parsing to child ${childDevice.deviceNetworkId}")
                childDevice.parse([[name: eventMap.name, value: eventMap.value,
                    descriptionText: "${childDevice.displayName} ${eventMap.name} is ${eventMap.value}${eventMap.unit ?: ''}"]])
                if (enableDesc) log.info "${childDevice.displayName} ${eventMap.name} is ${eventMap.value}${eventMap.unit ?: ''}"
                updateSwitchFromChildStates()
            } else {
                logDebug("Not parsing because child device endpoint ${descMap.sourceEndpoint} or ${descMap.endpoint} not found")
            }
        } else {
            log.warn "Not parsed: $description"
        }
    } else {
        logDebug "Not parsed: $description"
    }
}

// Updates parent "switch" to be "on" if any child is on, otherewise off
private void updateSwitchFromChildStates() {
    def attribute = "switch"
    def value = "off"
    def descText = ""
    if (childDevices.any { it.currentValue('switch') == 'on' }) {
        value = "on"
    }
    descText = "${device.displayName} ${attribute} is ${value}"
    if (device.currentValue(attribute) != value) {
        logDesc(descText)
        sendEvent(name: attribute, value: value, descriptionText: descText)
    }
}

private void createChildDevices() {
    log.debug("createChildDevices(); numberOfChildDevice = ${numberOfChildDevices}")
    childEndpoints.each { epId, epName ->
        try {
            logDebug "Finding or creating child device for endpoint: ${epId}"
            if (childDevices.find { it.deviceNetworkId == "${device.id}-CH${epId}" }) {
                logDebug "Skipping child device creation because already exists (endpoint $epId)"
            } else {
                logDebug "Creating child device for endpoint $epId"
                addChildDevice("hubitat", "Generic Component Switch", "${device.id}-CH${epId}", [label: "${device.displayName} ${epName}"])
            }
        } catch (Exception e) {
            log.error "Error creating child devices: ${e}"
        }
    }
}

def on() {
    logDebug("on()")
    return "he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x01 {}"
}

def off() {
    logDebug("off()")
    return "he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x00 {}"
}

void componentOn(Integer channel) {
    logDebug("componentOn(Integer $channel)")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x0${channel} 0x0006 0x01 {}",
        hubitat.device.Protocol.ZIGBEE))
}

void componentOff(Integer channel) {
    logDebug("componentOff(Integer $channel)")
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x0${channel} 0x0006 0x00 {}",
        hubitat.device.Protocol.ZIGBEE))
}

void componentOn(childDevice) {
    logDebug("componentOn(DeviceWrapper $childDevice)")
    def childEndpoint = getChildEndpoint(childDevice.deviceNetworkId)
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x${childEndpoint} 0x0006 0x01 {}",
        hubitat.device.Protocol.ZIGBEE))
}

void componentOff(childDevice) {
    logDebug("componentOff($childDevice)")
    def childEndpoint = getChildEndpoint(childDevice.deviceNetworkId)
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x${childEndpoint} 0x0006 0x00 {}",
        hubitat.device.Protocol.ZIGBEE))
}

def componentRefresh(childDevice) {
    logDebug("componentRefresh($childDevice)")
    def childEndpoint = getChildEndpoint(childDevice.deviceNetworkId)
    return sendHubCommand(new hubitat.device.HubAction("he rattr 0x${device.deviceNetworkId} 0x${childEndpoint} 0x0006 0 {}",
        hubitat.device.Protocol.ZIGBEE))
}

def refresh() {
    logDebug("refresh()")
    cmds = []
    childEndpoints.each { epId, epName ->
        cmds << "he rattr 0x${device.deviceNetworkId} 0x${epId} 0x0006 0 {}"
    }
    return delayBetween(cmds, 200)
}

def configure() {
    log.warn "configure..."
    def cmds = zigbee.onOffConfig() + refresh()
    return cmds
}

private String getChildEndpoint(String dni) {
    String ep = dni.split("-CH")[-1]
    return ep
}

void logDebug(str) {
    if (settings.enableDebug) log.debug(str)
}

void logDesc(str) {
    if (settings.enableDesc) log.info(str)
}