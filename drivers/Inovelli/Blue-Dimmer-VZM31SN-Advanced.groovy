/**
* Inovelli VZM31-SN Blue Series Zigbee 2-in-1 Dimmer (Advanced)
*
* "Advanced" Fork by RMoRobert (Robert Morris) - includes new buttom mapping and dedicated commands for LED
* color, leve, and notifications -- like my "Advanced" Red Series drivers
*
* Based on Inovelli VZM31-SN driver:
* Author: Eric Maycock (erocm123)
* Contributor: Mark Amber (marka75160)
*
* Original Copyright 2022 Eric Maycock / Inovelli
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at:
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* 
* 2022-11-06 (RM) - Update for new FW (2.05) parameters
* 2022-11-03 (RM) - Remove QuickStart emulation, change button events to match other Advanced drivers
* 2022-08-14 (MA) - Original base:  emulate QuickStart for dimmer (can be disabled); add presetLevel command
*
**/

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.helper.ColorUtils
//import hubitat.helper.HexUtils
import java.security.MessageDigest

@Field static final Integer disableDebugLogging = 30 // minutes before auto-disabling debug logging

@Field static final Map<String,Integer> colorNameMap = [
   "red": 0,
   "red-orange": 2,
   "orange": 9,
   "yellow": 30,
   "chartreuse": 60,
   "green": 86,
   "spring": 100,
   "cyan": 127,
   "azure": 155,
   "blue": 170,
   "violet": 212,
   "magenta": 234,
   "rose": 254,
   "white": 255
]

@Field static final Map<String,Short> effectNameAllMap = ["off": 0, "solid": 1, "chase": 5, "fast blink": 2, "slow blink": 3, "pulse": 4, "open/close": 6,
                                                       "small to big": 7, "aurora": 8, "slow falling": 9, "medium falling": 10, "fast falling": 11, "slow rising": 12, "medium rising": 13, "fast rising": 14, "medium blink": 15,
                                                       "slow chase": 16, "fast chase": 17, "fast siren": 18, "slow siren": 19, "clear": 255]

@Field static final Map<String,Short> effectNameOneMap = ["off": 0, "solid": 1, "chase": 5, "fast blink": 2, "slow blink": 3, "pulse": 4, "clear": 255]

metadata {
    definition(name: "Advanced Inovelli Blue Series 2-in-1 (VZM31-SN) Advanced", namespace: "RMoRobert", author: "E.Maycock/M.Amber/R.Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Blue-Dimmer-VZM31SN-Advanced.groovy") { 
        
        capability "Actuator"
        capability "ChangeLevel"
        capability "Configuration"
        capability "EnergyMeter"
        capability "HoldableButton"
        //capability "LevelPreset" //V-Mark firmware incorrectly uses Remote Default (p14) instead of Local Default (p13) for levelPreset. Users want to preset level of local buttons not remote commands
        capability "Light"
        capability "PowerMeter"
        capability "PushableButton"
        capability "Refresh"
        capability "ReleasableButton"
        capability "Switch"
        capability "SwitchLevel"

        attribute "auxType", "String"          //type of Aux switch
        attribute "ledEffect", "String"        //LED effect that was requested
        attribute "numberOfBindings", "String" //(read only)
        attribute "smartBulb", "String"        //Smart Bulb mode enabled or disabled
        attribute "switchMode", "String"       //Dimmer or On/Off only

        command "bind",                ["string"]
        command "bindInitiator"
        command "bindTarget"

        command "configure",           [[name: "Option", type: "ENUM", description: "User=user changed settings only, All=configure all settings, Default=set all settings to default", constraints: [" ","User","All","Default"]]]

        command "initialize"

/*   // From Red Series -- figure out how to match here! 
      // prob not this one though: command "setIndicator", [[name: "Notification Value*", type: "NUMBER", description: "See https://nathanfiscus.github.io/inovelli-notification-calc to calculate"]] .
      command "setIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]
      command "setLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli format, 0-10"]]
      command "setLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0]]]
      command "setOnLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
      command "setOffLEDLevel", [[name:"Level*", type: "ENUM", description: "Brightess (0-10, 0=off)", constraints: 0..10]]
*/

      command "setIndicator", [[name:"Color", type: "ENUM", constraints: ["red", "red-orange", "orange", "yellow", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                               [name:"Level", type: "ENUM", description: "Level, 0-100", constraints: [100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 1, 0]],
                               [name:"Effect", type: "ENUM", description: "Effect name from list", constraints: ["off", "solid", "chase", "fast blink", "slow blink", "pulse", "open/close",
                                                       "small to big", "aurora", "clear"]],
                               [name: "Duration", type: "NUMBER", description: "Duration in seconds, 1-254 or 255 for indefinite"]]

      command "setLEDColor", [[name: "Color*", type: "ENUM", description: "Color name (from list)", constraints: ["red", "red-orange", "orange", "yellow", "chartreuse", "green", "spring", "cyan", "azure", "blue", "violet", "magenta", "rose", "white"]],
                              [name:"Level", type: "NUMBER", description: "Level, 0-100", range:0..100], [name: "OnOrOff", type: "ENUM", constraints: ["on","off","both"], description: "Apply to LED when on, off, or both (deafult: both)"]]
      
      command "setLEDColor", [[name: "Color*", type: "NUMBER", description: "Inovelli Red/Blue format, 0-255"], [name: "Level", type: "NUMBER", description: "Inovelli Red format, 0-10, by default, or 0-100 if not"],
                              [name: "OnOrOff", type: "ENUM", constraints: ["on","off","both"], description: "Apply to LED when on, off, or both (deafult: both)"],[name:"UseRedCompatibility", type: "ENUM", constraints: ["false", "true"],descrription:"Use Red Series-compatible level (0-10); default true"]]

      command "setOnLEDLevel", [[name:"Level*", type: "NUMBER", description: "Brightess (0-10, or 0-100 if not in Red compatibility mode; 0=off)", range: 0..100],[name:"UseRedCompatibility", type: "ENUM", constraints: ["false", "true"],descrription:"Use Red Series-compatible level (0-10); default true"]]

      command "setOffLEDLevel", [[name:"Level*", type: "NUMBER", description: "Brightess (0-10, or 0-100 if not in Red compatibility mode; 0=off)", range: 0..100],[name:"UseRedCompatibility", type: "ENUM", constraints: ["false", "true"],descrription:"Use Red Series-compatible level (0-10); default true"]]

      // Leaving in for compatibiltiy with Inovelli, but probably don't need:
        command "ledEffectAll",        [[name: "Type*",type:"ENUM", description: "1=Solid, 2=Fast Blink, 3=Slow Blink, 4=Pulse, 5=Chase, 6=Open/Close, 7=Small-to-Big, 8=Aurora, 9=Slow Falling, 10=Medium Falling, 11=Fast Falling, 12=Slow Rising, 13=Medium Rusing, 14=Fast Rising, 15=Medium Blink, 16=Slow Chase, 17=Fast Chase, 18=Fast Siren, 19=Slow Siren, 0=LEDs off, 255=Clear Notification", constraints: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,0,255]],
                                        [name: "Color",type:"NUMBER", description: "0-254=Hue Color, 255=White, default=Red"], 
                                        [name: "Level", type:"NUMBER", description: "0-100=LED Intensity, default=100"], 
                                        [name: "Duration", type:"NUMBER", description: "1-60=seconds, 61-120=1-120 minutes, 121-254=1-134 hours, 255=Indefinitely, default=255"]]


      // Keep for now; no Red equivalent:     
        command "ledEffectOne",        [[name: "LEDnum*",type:"ENUM", description: "LED 1-7", constraints: ["7","6","5","4","3","2","1","123","567","12","345","67","147","1357","246"]],
                                        [name: "Type*",type:"ENUM", description: "1=Solid, 2=Fast Blink, 3=Slow Blink, 4=Pulse, 5=Chase, 6=Falling, 7=Rising, 8=Aurora, 0=LED off, 255=Clear Notification", constraints: [1,2,3,4,5,6,7,8,0,255]], 
                                        [name: "Color",type:"NUMBER", description: "0-254=Hue Color, 255=White, default=Red"], 
                                        [name: "Level", type:"NUMBER", description: "0-100=LED Intensity, default=100"], 
                                        [name: "Duration", type:"NUMBER", description: "1-60=seconds, 61-120=1-120 minutes, 121-254=1-134 hours, 255=Indefinitely, default=255"]]
        
        //uncomment the next line if you want a "presetLevel" command to use in Rule Manager.  Can also be done with setPrivateCluster(13, level, 8) instead
        //command "presetLevel",          [[name: "Level", type: "NUMBER", description: "Level to preset (1 to 101)"]]           
        
        command "refresh",             [[name: "Option", type: "ENUM", description: "blank=current states only, User=user changed settings only, All=refresh all settings",constraints: [" ","User","All"]]]

        command "resetEnergyMeter"

        command "setPrivateCluster",   [[name: "Attribute*",type:"NUMBER", description: "Attribute (in decimal) ex. 0x000F input 15"], 
                                        [name: "Value", type:"NUMBER", description: "Enter the value (in decimal) Leave blank to get current value without changing it"], 
                                        [name: "Size*", type:"ENUM", description: "8=uint8, 16=uint16, 1=bool",constraints: ["8", "16","1"]]]
     
        command "setZigbeeAttribute",  [[name: "Cluster*",type:"NUMBER", description: "Cluster (in decimal) ex. Inovelli Private Cluster=0xFC31 input 64561"], 
                                        [name: "Attribute*",type:"NUMBER", description: "Attribute (in decimal) ex. 0x000F input 15"], 
                                        [name: "Value", type:"NUMBER", description: "Enter the value (in decimal) Leave blank to get current value without changing it"], 
                                        [name: "Size*", type:"ENUM", description: "8=uint8, 16=uint16, 32=unint32, 1=bool",constraints: ["8", "16","32","1"]]]
        
        command "startLevelChange",    [[name: "Direction*",type:"ENUM", description: "Direction for level change", constraints: ["up","down"]], 
                                        [name: "Duration",type:"NUMBER", description: "Transition duration in seconds"]]
               
        command "updateFirmware"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0008,0702,0B04,0B05,FC57,FC31", outClusters:"0003,0019",           model:"VZM31-SN", manufacturer:"Inovelli"
        fingerprint profileId:"0104", endpointId:"02", inClusters:"0000,0003",                                              outClusters:"0003,0019,0006,0008", model:"VZM31-SN", manufacturer:"Inovelli"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0008,0702,0B04,FC31",           outClusters:"0003,0019",           model:"VZM31-SN", manufacturer:"Inovelli"
    }
	
    preferences {
        getParameterNumbers().each{ i ->
            switch(configParams["parameter${i.toString().padLeft(3,"0")}"].type){
                case "number":
                    switch(i){
                        case 51:    //Device Bind Number
                            input "parameter${i}", "number",
                                title: "${i}. " + green(bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name)), 
                                description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description + 
                                     "<br>Range=" + configParams["parameter${i.toString().padLeft(3,"0")}"].range),
                                //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                                range: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                            break
                        default:
                            input "parameter${i}", "number",
								title: "${i}. " + bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name), 
                                description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description + 
                                    "<br>Range=" + configParams["parameter${i.toString().padLeft(3,"0")}"].range + 
				    	    	    " Default=" +  configParams["parameter${i.toString().padLeft(3,"0")}"].default),
                                //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                                range: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                            break
                    }    
                    break
                case "enum":
                    switch(i){
                        case 21:    //Power Source
                            input "parameter${i}", "enum",
                                title: "${i}. " + green(bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name)), 
                                description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description),
                                //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                                options: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                            break
                        case 22:    //Aux Type
                        case 52:    //Smart Bulb Mode
                        case 258:   //Switch Mode
                            input "parameter${i}", "enum",
                                title: "${i}. " + red(bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name)), 
                                description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description),
                                //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                                options: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                            break
                        case 95:
                        case 96:
							//special case for custom color is below
                            break
                        default:
                            input "parameter${i}", "enum",
                                title: "${i}. " + bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name), 
                                description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description),
                                //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                                options: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                            break
					}
                    break
            }        

            if (i==95 || i==96) {
                if ((i==95 && parameter95custom==null)||(i==96 && parameter96custom==null)){
                    input "parameter${i}", "enum",
                        title: "${i}. " + hue((settings?."parameter${i}"!=null?settings?."parameter${i}":configParams["parameter${i.toString().padLeft(3,"0")}"].default)?.toInteger(), 
                            bold(configParams["parameter${i.toString().padLeft(3,"0")}"].name)), 
                        description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description),
                        //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                        options: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                }
                else {
                    input "parameter${i}", "enum",
                        title: "${i}. " + hue((settings?."parameter${i}"!=null?settings?."parameter${i}":configParams["parameter${i.toString().padLeft(3,"0")}"].default)?.toInteger(), 
                            strike(configParams["parameter${i.toString().padLeft(3,"0")}"].name)) + 
                            hue((settings?."parameter${i}custom"!=null?(settings."parameter${i}custom"/360*255):configParams["parameter${i.toString().padLeft(3,"0")}"].default)?.toInteger(),
                                italic(bold(" Overridden by Custom Hue Value"))), 
                        description: italic(configParams["parameter${i.toString().padLeft(3,"0")}"].description),
                        //defaultValue: configParams["parameter${i.toString().padLeft(3,"0")}"].default,
                        options: configParams["parameter${i.toString().padLeft(3,"0")}"].range
                }
                input "parameter${i}custom", "number", 
                    title: settings?."parameter${i}custom"!=null?
                        (hue((settings."parameter${i}custom"/360*255)?.toInteger(), 
                            bold("Custom " + configParams["parameter${i.toString().padLeft(3,"0")}"].name))):
                        (hue((settings?."parameter${i}"!=null?settings?."parameter${i}":configParams["parameter${i.toString().padLeft(3,"0")}"].default)?.toInteger(), 
                            bold("Custom " + configParams["parameter${i.toString().padLeft(3,"0")}"].name))),
                    description: italic("Hue value to override " + configParams["parameter${i.toString().padLeft(3,"0")}"].name+".<br>Range: 0-360 chosen from a"+
                        underline(''' <a href="https://community-assets.home-assistant.io/original/3X/6/c/6c0d1ea7c96b382087b6a34dee6578ac4324edeb.png" target="_blank">'''+
                        fireBrick(" h")+crimson("u")+red("e")+orangeRed(" c")+darkOrange("o")+orange("l")+limeGreen("o")+green("r")+teal(" w")+blue("h")+steelBlue("e")+blueViolet("e")+magenta("l")+"</a>")),
                    required: false,
                    range: "0..360"
            }
        }
        input name: "txtEnable", type: "bool",   title: bold("Enable descriptionText Logging"),   defaultValue: true
        input name: "logEnable", type: "bool",   title: bold("Enable debug Logging"),  defaultValue: true
    }
}

List getParameterNumbers() {   //controls which options are available depending on whether the device is configured as a switch or a dimmer.
    if (parameter258 == "1")  //on/off mode
        return [258,22,52,10,11,12,17,18,19,20,21,50,51,95,96,97,98,256,257,259,260,261,262]
    else                      //dimmer mode
        return [258,22,52,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,17,18,19,20,21,50,51,53,95,96,97,98,256,257,260,262]
}

@Field static final Map configParams = [
    parameter001 : [
        number: 1,
        name: "Dimming Speed - Up (Remote)",
        description: "This changes the speed that the light dims up when controlled from the hub. A setting of 'instant' turns the light immediately on. Increasing the value slows down the transition speed.<br>Default=2.5s",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s (default)", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s"],
        default: 25,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter002 : [
        number: 2,
        name: "Dimming Speed - Up (Local)",
        description: "This changes the speed that the light dims up when controlled at the switch. A setting of 'instant' turns the light immediately on. Increasing the value slows down the transition speed.<br>Default=Sync with parameter1",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter1"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter003 : [
        number: 3,
        name: "Ramp Rate - Off to On (Remote)",
        description: "This changes the speed that the light turns on when controlled from the hub. A setting of 'instant' turns the light immediately on. Increasing the value slows down the transition speed.<br>Default=Sync with parameter1",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter1"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter004 : [
        number: 4,
        name: "Ramp Rate - Off to On (Local)",
        description: "This changes the speed that the light turns on when controlled at the switch. A setting of 'instant' turns the light immediately on. Increasing the value slows down the transition speed.<br>Default=Sync with parameter3",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter3"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter005 : [
        number: 5,
        name: "Dimming Speed - Down (Remote)",
        description: "This changes the speed that the light dims down when controlled from the hub. A setting of 'instant' turns the light immediately off. Increasing the value slows down the transition speed.<br>Default=Sync with parameter1",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter1"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter006 : [
        number: 6,
        name: "Dimming Speed - Down (Local)",
        description: "This changes the speed that the light dims down when controlled at the switch. A setting of 'instant' turns the light immediately off. Increasing the value slows down the transition speed.<br>Default=Sync with parameter2",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter2"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter007 : [
        number: 7,
        name: "Ramp Rate - On to Off (Remote)",
        description: "This changes the speed that the light turns off when controlled from the hub. A setting of 'instant' turns the light immediately off. Increasing the value slows down the transition speed.<br>Default=Sync with parameter3",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter3"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter008 : [
        number: 8,
        name: "Ramp Rate - On to Off (Local)",
        description: "This changes the speed that the light turns off when controlled at the switch. A setting of 'instant' turns the light immediately off. Increasing the value slows down the transition speed.<br>Default=Sync with parameter4",
        range: ["0":"instant", "5":"500ms", "6":"600ms", "7":"700ms", "8":"800ms", "9":"900ms", "10":"1.0s", "11":"1.1s", "12":"1.2s", "13":"1.3s", "14":"1.4s", "15":"1.5s", "16":"1.6s", "17":"1.7s", "18":"1.8s", "19":"1.9s", "20":"2.0s", "21":"2.1s", "22":"2.2s", "23":"2.3s", "24":"2.4s", "25":"2.5s", "26":"2.6s", "27":"2.7s", "28":"2.8s", "29":"2.9s", "30":"3.0s", "31":"3.1s", "32":"3.2s", "33":"3.3s", "34":"3.4s", "35":"3.5s", "36":"3.6s", "37":"3.7s", "38":"3.8s", "39":"3.9s", "40":"4.0s", "41":"4.1s", "42":"4.2s", "43":"4.3s", "44":"4.4s", "45":"4.5s", "46":"4.6s", "47":"4.7s", "48":"4.8s", "49":"4.9s", "50":"5.0s", "51":"5.1s", "52":"5.2s", "53":"5.3s", "54":"5.4s", "55":"5.5s", "56":"5.6s", "57":"5.7s", "58":"5.8s", "59":"5.9s", "60":"6.0s", "61":"6.1s", "62":"6.2s", "63":"6.3s", "64":"6.4s", "65":"6.5s", "66":"6.6s", "67":"6.7s", "68":"6.8s", "69":"6.9s", "70":"7.0s", "71":"7.1s", "72":"7.2s", "73":"7.3s", "74":"7.4s", "75":"7.5s", "76":"7.6s", "77":"7.7s", "78":"7.8s", "79":"7.9s", "80":"8.0s", "81":"8.1s", "82":"8.2s", "83":"8.3s", "84":"8.4s", "85":"8.5s", "86":"8.6s", "87":"8.7s", "88":"8.8s", "89":"8.9s", "90":"9.0s", "91":"9.1s", "92":"9.2s", "93":"9.3s", "94":"9.4s", "95":"9.5s", "96":"9.6s", "97":"9.7s", "98":"9.8s", "99":"9.9s", "100":"10.0s", "101":"10.1s", "102":"10.2s", "103":"10.3s", "104":"10.4s", "105":"10.5s", "106":"10.6s", "107":"10.7s", "108":"10.8s", "109":"10.9s", "110":"11.0s", "111":"11.1s", "112":"11.2s", "113":"11.3s", "114":"11.4s", "115":"11.5s", "116":"11.6s", "117":"11.7s", "118":"11.8s", "119":"11.9s", "120":"12.0s", "121":"12.1s", "122":"12.2s", "123":"12.3s", "124":"12.4s", "125":"12.5s", "126":"12.6s", "127":"Sync with parameter4"],
        default: 127,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter009 : [
        number: 9,
        name: "Minimum Level",
        description: "The minimum level that the light can be dimmed. Useful when the user has a light that does not turn on or flickers at a lower level.",
        range: "1..99",
        default: 1,
        size: 8,
        type: "number",
        value: null
        ],
    parameter010 : [
        number: 10,
        name: "Maximum Level",
        description: "The maximum level that the light can be dimmed. Useful when the user wants to limit the maximum brighness.",
        range: "2..100",
        default: 100,
        size: 8,
        type: "number",
        value: null
        ],
    parameter011 : [
        number: 11,
        name: "Invert Switch",
        description: "Inverts the orientation of the switch. Useful when the switch is installed upside down. Essentially up becomes down and down becomes up.",
        range: ["0":"No (default)", "1":"Yes"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter012 : [
        number: 12,
        name: "Auto Off Timer",
        description: "Automatically turns the switch off after this many seconds. When the switch is turned on a timer is started. When the timer expires the switch turns off.<br>0=Auto Off Disabled.",
        range: "0..32767",
        default: 0,
        size: 16,
        type: "number",
        value: null
        ],
    parameter013 : [
        number: 13,
        name: "Default Level (Local)",
        description: "Default level for the dimmer when turned on at the switch.<br>1-100=Set Level<br>101=Use previous level.",
        range: "1..101",
        default: 101,
        size: 8,
        type: "number",
        value: null
        ],
    parameter014 : [
        number: 14,
        name: "Default Level (Remote)",
        description: "Default level for the dimmer when turned on from the hub.<br>1-100=Set Level<br>101=Use previous level.",
        range: "1..101",
        default: 101,
        size: 8,
        type: "number",
        value: null
        ],
    parameter015 : [
        number: 15,
        name: "Level After Power Restored",
        description: "The level the switch will return to when power is restored after power failure.<br>0=Off<br>1-100=Set Level<br>101=Use previous level.",
        range: "0..101",
        default: 101,
        size: 8,
        type: "number",
        value: null
        ],
    parameter017 : [
        number: 17,
        name: "Load Level Indicator Timeout",
        description: "Shows the level that the load is at for x number of seconds after the load is adjusted and then returns to the Default LED state.",
        range: ["0":"Do not display Load Level","1":"1 Second","2":"2 Seconds","3":"3 Seconds","4":"4 Seconds","5":"5 Seconds","6":"6 Seconds","7":"7 Seconds","8":"8 Seconds","9":"9 Seconds","10":"10 Seconds","11":"Display Load Level with no timeout (default)"],
        default: 11,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter018 : [
        number: 18,
        name: "Active Power Reports",
        description: "Percent power level change that will result in a new power report being sent.<br>0 = Disabled",
        range: "0..100",
        default: 10,
        size: 8,
        type: "number",
        value: null
        ],
    parameter019 : [
        number: 19,
        name: "Periodic Power & Energy Reports",
        description: "Time period between consecutive power & energy reports being sent (in seconds). The timer is reset after each report is sent.",
        range: "0..32767",
        default: 3600,
        size: 16,
        type: "number",
        value: null
        ],
    parameter020 : [
        number: 20,
        name: "Active Energy Reports",
        description: "Energy level change that will result in a new energy report being sent.<br>0 = Disabled<br>1-32767 = 0.01kWh-327.67kWh.",
        range: "0..32767",
        default: 10,
        size: 16,
        type: "number",
        value: null
        ],
    parameter021 : [
        number: 21,
        name: "Power Source (read only)",
        description: "Neutral or Non-Neutral wiring is automatically sensed.",
        range: [0:"Non Neutral", 1:"Neutral"],
        default: 1,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter022 : [
        number: 22,
        name: "Aux Switch Type",
        description: "Set the Aux switch type.",
        range: ["0":"None (default)", "1":"3-Way Dumb Switch", "2":"3-Way Aux Switch"],
        default: 0,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter050 : [
        number: 50,
        name: "Button Press Delay",
        description: "Adjust the button delay used in scene control. 0=no delay (disables multi-tap scenes), Default=500ms",
        range: ["0":"0ms","3":"300ms","4":"400ms","5":"500ms (default)","6":"600ms","7":"700ms","8":"800ms","9":"900ms"],
        default: 5,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter051 : [
        number: 51,
        name: "Device Bind Number (read only)",
        description: "Number of devices currently bound and counts one group as two devices.",
        range: "0..255",
        default: 0,
        size: 8,
        type: "number",
        value: null
        ],
    parameter052 : [
        number: 52,
        name: "Smart Bulb Mode",
        description: "For use with Smart Bulbs that need constant power and are controlled via commands rather than power.",
        range: ["0":"Disabled (default)", "1":"Smart Bulb Mode"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter053 : [
        number: 53,
        name: "Double-Tap UP for full brightness",
        description: "Enable or Disable full brightness on double-tap up.",
        range: ["0":"Disabled (default)", "1":"Enabled"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter095 : [
        number: 95,
        name: "LED Indicator Color (when On)",
        description: "Set the color of the LED Indicator when the load is on.",
        range: ["0":"Red","7":"Orange","28":"Lemon","64":"Lime","85":"Green","106":"Teal","127":"Cyan","148":"Aqua","170":"Blue (default)","190":"Violet","212":"Magenta","234":"Pink","255":"White"],
        default: 170,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter096 : [
        number: 96,
        name: "LED Indicator Color (when Off)",
        description: "Set the color of the LED Indicator when the load is off.",
        range: ["0":"Red","7":"Orange","28":"Lemon","64":"Lime","85":"Green","106":"Teal","127":"Cyan","148":"Aqua","170":"Blue (default)","190":"Violet","212":"Magenta","234":"Pink","255":"White"],
        default: 170,
        size: 8,
        type: "enum",
        value: null
        ],
    parameter097 : [
        number: 97,
        name: "LED Indicator Intensity (when On)",
        description: "Set the intensity of the LED Indicator when the load is on.",
        range: "0..100",
        default: 33,
        size: 8,
        type: "number",
        value: null
        ],
    parameter098 : [
        number: 98,
        name: "LED Indicator Intensity (when Off)",
        description: "Set the intensity of the LED Indicator when the load is off.",
        range: "0..100",
        default: 3,
        size: 8,
        type: "number",
        value: null
        ],
    parameter256 : [
        number: 256,
        name: "Local Protection",
        description: "Ability to control switch from the wall.",
        range: ["0":"Local control enabled (default)", "1":"Local control disabled"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ] ,
    parameter257 : [
        number: 257,
        name: "Remote Protection",
        description: "Ability to control switch from the hub.",
        range: ["0":"Remote control enabled (default)", "1":"Remote control disabled"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter258 : [
        number: 258,
        name: "Switch Mode",
        description: "Use as a Dimmer or an On/Off switch",
        range: ["0":"Dimmer", "1":"On/Off (default)"],
        default: 1,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter259 : [
        number: 259,
        name: "On/Off LED Mode",
        description: "When the device is in On/Off mode, use full LED bar or just one LED",
        range: ["0":"All (default)", "1":"One"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter260 : [
        number: 260,
        name: "Firmware Update-In-Progess Indicator",
        description: "Display firmware update progress on LED Indicator",
        range: ["1":"Enabled (default)", "0":"Disabled"],
        default: 1,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter261 : [
        number: 261,
        name: "Relay Click",
        description: "Audible Click in On/Off mode",
        range: ["0":"Enabled (default)", "1":"Disabled"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ],
    parameter262 : [
        number: 262,
        name: "Double-Tap config to clear notification",
        description: "Double-Tap the Config button to clear notifications",
        range: ["0":"Enabled (default)", "1":"Disabled"],
        default: 0,
        size: 1,
        type: "enum",
        value: null
        ]
]

@Field static final Integer defaultDelay = 450    //default delay to use for zigbee commands (in milliseconds)
@Field static final Integer longerDelay = 2500    //longer delay to use for changing switch modes (in milliseconds)

def bind(cmds=[]) {
   if (logEnable) log.debug "bind($cmds)"
   return cmds
} 

def bindInitiator() {
    if (logEnable) log.debug "bindInitiator()"
    def cmds = zigbee.command(0xfc31,0x04,["mfgCode":"0x122F"],defaultDelay,"0") 
    if (logEnable) log.debug "bindInitiator cmds = $cmds"
    return cmds
}

def bindTarget() {
    if (logEnable) log.debug "bindTarget()"
    def cmds = zigbee.command(0x0003, 0x00, [:], defaultDelay, "20 00")
    if (logEnable) log.debug "bindTarget $cmds"
    return cmds
}

def calculateDuration(direction) {
	if (parameter258=="1") duration=0  //IF switch mode is on/off THEN dim/ramp rates are 0
    else {                             //ElSE we are in dimmer mode so calculate the dim/ramp rates
        switch (direction) {
            case "up":
                break
            case "down":
                break
            case "on":
                def rampRate = 0
                if (parameter258=="0") //if we are in dimmer mode,  use params 1-8 for rampRate
                    rampRate = (parameter3!=null?parameter3:(parameter1!=null?parameter1:configParams["parameter001"].default))?.toInteger()
                break
            case "off":
                def rampRate = 0
                if (parameter258=="0")  //if we are in dimmer mode, use params 1-8 for rampRate
                    rampRate = (parameter7!=null?parameter7:(parameter3!=null?parameter3:(parameter1!=null?parameter1:configParams["parameter001"].default)))?.toInteger()
                break
        }
    }	
}

def calculateParameter(number) {
    //if (logEnable) log.debug "${device.displayName}: calculateParameter(${number})"
    def value = (settings."parameter${number}"!=null?settings."parameter${number}":configParams["parameter${number.toString().padLeft(3,'0')}"].default).toInteger()
    switch (number){
        case 9:     //Min Level
        case 10:    //Max Level
        case 13:    //Default Level (local)
        case 14:    //Default Level (remote)
        case 15:    //Level after power restored
            value = convertPercentToByte(value)    //convert levels from percent to byte values before sending to the device
            break
        case 95:    //custom hue for LED Indicator (when On)
        case 96:    //custom hue for LED Indicator (when Off)
            //360-hue values need to be converted to byte values before sending to the device
            if (settings."parameter${number}custom" =~ /^([0-9]{1}|[0-9]{2}|[0-9]{3})$/) {
                value = Math.round(settings."parameter${number}custom"/360*255)
            }
            else {   //else custom hue is invalid format or not selected
                if(settings."parameter${number}custom"!=null) {
                    device.clearSetting("parameter${number}custom")
                    if (txtEnable) log.warn "${device.displayName}: "+fireBrick("Cleared invalid custom hue: ${settings."parameter${number}custom"}")
                }
            }
            break 
    }
    return value
}

def calculateSize(size=8) {
    //if (logEnable) log.debug "${device.displayName}: calculateSize(${size})"
    if      (size.toInteger() == 1)  return 0x10    //1-bit boolean
    else if (size.toInteger() == 8)  return 0x20    //1-byte unsigned integer
    else if (size.toInteger() == 16) return 0x21    //2-byte unsigned integer
    else if (size.toInteger() == 24) return 0x22    //3-byte unsigned integer
    else if (size.toInteger() == 32) return 0x23    //4-byte unsigned integer
    else if (size.toInteger() == 40) return 0x24    //5-byte unsigned integer
    else if (size.toInteger() == 48) return 0x25    //6-byte unsigned integer
    else if (size.toInteger() == 56) return 0x26    //7-byte unsigned integer
    else if (size.toInteger() == 64) return 0x27    //8-byte unsigned integer
    else                             return 0x20    //default to 1-byte unsigned if no other matches
}

def clusterLookup(cluster) {
    return zigbee.clusterLookup(cluster) ?: "PRIVATE_CLUSTER (${cluster})"
}

def configure(option) {
    option = (option==null||option==" ") ? "" : option
    if (logEnable) log.debug "configure($option)" 
    sendEvent(name: "numberOfButtons", value: 15)
    def cmds = []
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Basic Cluster
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0003 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Identify Cluster
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0003 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Identify Cluster ep2
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0004 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Group Cluster
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0005 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Scenes Cluster
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //On/Off Cluster
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //On/Off Cluster ep2
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Level Control Cluster
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x02 0x01 0x0008 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Level Control Cluster ep2
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0019 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //OTA Upgrade Cluster
    if (state.model?.substring(0,5)!="VZM35") {  //Fan does not support power/energy reports
        cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0702 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Simple Metering - to get energy reports
        cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0B04 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Electrical Measurement - to get power reports
    }
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x8021 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Binding Cluster - to get binding reports
//  cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x8022 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //UnBinding Cluster - to get Unbinding reports
    cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0xFC31 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Private Cluster
    cmds += ["zdo bind ${device.deviceNetworkId} 0x02 0x01 0xFC31 {${device.zigbeeId}} {}", "delay ${defaultDelay}"] //Private Cluster ep2
    //read back some key attributes
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0000 0x0004                    {}", "delay ${defaultDelay}"] //get manufacturer
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0000 0x0005                    {}", "delay ${defaultDelay}"] //get model
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0000 0x0006                    {}", "delay ${defaultDelay}"] //get firmware date
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0000 0x0007                    {}", "delay ${defaultDelay}"] //get power source
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0000 0x4000                    {}", "delay ${defaultDelay}"] //get firmware version
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0006 0x0000                    {}", "delay ${defaultDelay}"] //get on/off state
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0006 0x4003                    {}", "delay ${defaultDelay}"] //get Startup OnOff state
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0008 0x0000                    {}", "delay ${defaultDelay}"] //get current level
    if (state.model?.substring(0,5)!="VZM35")  //Fan does not support on_off transition time
        cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0008 0x0010                    {}", "delay ${defaultDelay}"] //get OnOff Transition Time
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0008 0x0011                    {}", "delay ${defaultDelay}"] //get Default Remote On Level
    cmds += ["he rattr ${device.deviceNetworkId} 0x01 0x0008 0x4000                    {}", "delay ${defaultDelay}"] //get Startup Level
    if (option!="All") { //if we didn't pick option "All" (so we don't read them twice) then preload the dimming/ramp rates and key parameters so they are not null in calculations
        for(int i = 1;i<=8;i++) cmds += getAttribute(0xfc31, i)
        cmds += getAttribute(0xfc31, 258)       //switch mode
        cmds += getAttribute(0xfc31, 22)        //aux switch type
        cmds += getAttribute(0xfc31, 52)        //smart bulb mode
        cmds += getAttribute(0xfc31, 21)        //power source (neutral/non-neutral)
        cmds += getAttribute(0xfc31, 51)        //number of bindings
    }
    if (option!="") cmds += updated(option) //if option was selected on Configure button, pass it on to update settings.
    return cmds
}

def convertByteToPercent(int value=0) {                  //Zigbee uses a 0-254 range where 254=100%.  255 is reserved for special meaning.
    //if (logEnable) log.debug "${device.displayName}: convertByteToPercent(${value})"
    value = value==null?0:value                          //default to 0 if null
    value = Math.min(Math.max(value.toInteger(),0),255)  //make sure input byte value is in the 0-255 range
    value = value>=255?256:value                         //this ensures that byte values of 255 get rounded up to 101%
    value = Math.ceil(value/255*100)                     //convert to 0-100 where 254=100% and 255 becomes 101 for special meaning
    return value
}

def convertPercentToByte(int value=0) {                  //Zigbee uses a 0-254 range where 254=100%.  255 is reserved for special meaning.
    //if (logEnable) log.debug "${device.displayName}: convertByteToPercent(${value})"
    value = value==null?0:value                          //default to 0 if null
    value = Math.min(Math.max(value.toInteger(),0),101)  //make sure input percent value is in the 0-101 range
    value = Math.floor(value/100*255)                    //convert to 0-255 where 100%=254 and 101 becomes 255 for special meaning
    value = value==255?254:value                         //this ensures that 100% rounds down to byte value 254
    value = value>255?255:value                          //this ensures that 101% rounds down to byte value 255
    return value
}

List<String> initialize() {
    if (logEnable) log.debug "initialize()"
    state.clear()
    List<String> cmds = refresh()
    return cmds
}

List<String> installed() {
    log.debug "installed()"
    initialize()
}

String intTo16bitUnsignedHex(value) {
    String hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2,4) + hexStr.substring(0,2))
}

String intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(),2)
}

List<String> setIndicator(String color, Object level, String effect, Number duration=255) {
   if (logEnable) log.debug "setIndicator($color, $level, $effect, $duration)"
   Integer intColor = colorNameMap[color?.toLowerCase()]
   if (intColor == null) intColor = 170
   Integer intEffect = (effectNameAllMap[effect?.toLowerCase()] != null) ? effectNameAllMap[effect.toLowerCase()] : 4
   Integer intLevel = level.toInteger()
   intLevel = Math.min(Math.max((level != null ? level : 100).toInteger(), 0), 100)
   Integer intDuration = duration.toInteger()
   intDuration = Math.min(Math.max((duration!=null ? duration : 255).toInteger(), 0), 255) 
   List<String> cmds = zigbee.command(0xfc31,0x01, ["mfgCode":"0x122F"], defaultDelay,
      "${intTo8bitUnsignedHex(intEffect)} ${intTo8bitUnsignedHex(intColor)} ${intTo8bitUnsignedHex(intLevel)} ${intTo8bitUnsignedHex(intDuration)}")
   if (logEnable) log.debug "setIndicator cmds = $cmds"
   return cmds
}

// Sets default on and/or off LED color parameter to value (0-255) and level (0-10, or 0-100 if redSeriesCompatibleScaling=false)
List<String> setLEDColor(value, level=null, String onOrOffOrBoth="both", redSeriesCompatibleScaling=true) {
   if (logEnable) log.debug "setLEDColor(Object $value, Object $level, onOrOffOrBoth=$onOrOffOrBoth redSeriesCompatibleScaling=$redSeriesCompatibleScaling)"
   Integer intColor = value.toInteger()
   List<String> cmds = []
   if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x005F, 0x20, intColor, ["mfgCode":"0x122F"], 250) // for on
   if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0060, 0x20, intColor, ["mfgCode":"0x122F"], 250) // for off
   if (level != null) {
      Integer intLevel = (redSeriesCompatibleScaling == true || redSeriesCompatibleScaling == "true") ? level*10 : level
      if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0061, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
      if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0062, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
   }
   // TODO: remove preference or overwrite?
   if (logEnable) "setLEDColor cmds = $cmds"
   return cmds
}

// Sets default on and/or off LED color parameter to named color (from map) and level (Hubitat 0-100 style)
List<String> setLEDColor(String color, level=null, String onOrOffOrBoth="both") {
   if (logEnable) log.debug "setLEDColor(String $color, Object $level, onOrOffOrBoth=$onOrOffOrBoth)"
   Integer intColor = colorNameMap[color?.toLowerCase()]
   if (intColor == null) intColor = 170
   List<String> cmds = []
   if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x005F, 0x20, intColor, ["mfgCode":"0x122F"], 250)
   if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0060, 0x20, intColor, ["mfgCode":"0x122F"], 250)
   if (level != null) {
      Integer intLevel = level.toInteger()
      if (onOrOffOrBoth != "off") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0061, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
      if (onOrOffOrBoth != "on") cmds += zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0062, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
   }
   // TODO: remove preference or overwrite?
   if (logEnable) "setLEDColor cmds = $cmds"
   return cmds
}

List<String> setOnLEDLevel(Number level, redSeriesCompatibleScaling=false) {
   if (logEnable) log.debug "setOnLEDLevel($level, $useRedCompatibility)"
   Integer intLevel = (redSeriesCompatibleScaling == true || redSeriesCompatibleScaling == "true") ? (level*10 as Integer) : (level as Integer)   
   return zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0061, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
}

List<String> setOffLEDLevel(Number level, redSeriesCompatibleScaling=false) {
   if (logEnable) log.debug "setOffLEDLevel($level, $useRedCompatibility)"
   Integer intLevel = (redSeriesCompatibleScaling == true || redSeriesCompatibleScaling == "true") ? (level*10 as Integer) : (level as Integer)   
   return zigbee.writeAttribute(CLUSTER_PRIVATE, 0x0062, 0x20, intLevel, ["mfgCode":"0x122F"], 250)
}


List<String> ledEffectAll(effect=1, color=0, level=100, duration=255) {
   if (logEnable) log.debug "ledEffectAll($effect, $color, $level, $duration)"
   effect   = Math.min(Math.max((effect!=null?effect:1).toInteger(),0),255) 
   color    = Math.min(Math.max((color!=null?color:0).toInteger(),0),255) 
   level    = Math.min(Math.max((level!=null?level:100).toInteger(),0),100) 
   duration = Math.min(Math.max((duration!=null?duration:255).toInteger(),0),255) 
   if (txtEnable) log.info "${device.displayName}: ledEffectALL(${effect},${color},${level},${duration})"
   sendEvent(name:"ledEffect", value: (effect==255?"Stop":"Start")+" All", displayed:false)
   def cmds =[]
   Integer cmdEffect = effect.toInteger()
   Integer cmdColor = color.toInteger()
   Integer cmdLevel = level.toInteger()
   Integer cmdDuration = duration.toInteger()
   cmds += zigbee.command(0xfc31,0x01,["mfgCode":"0x122F"],defaultDelay,"${intTo8bitUnsignedHex(cmdEffect)} ${intTo8bitUnsignedHex(cmdColor)} ${intTo8bitUnsignedHex(cmdLevel)} ${intTo8bitUnsignedHex(cmdDuration)}")
   if (logEnable) log.debug "ledEffectAll cmds = $cmds"
   return cmds
}

List<String> ledEffectOne(lednum, effect=1, color=0, level=100, duration=255) {
   if (logEnable) log.debug "ledEffectOne($lednum, $effect, $color, $level, $duration)"
    effect   = Math.min(Math.max((effect!=null?effect:1).toInteger(),0),255) 
    color    = Math.min(Math.max((color!=null?color:0).toInteger(),0),255) 
    level    = Math.min(Math.max((level!=null?level:100).toInteger(),0),100) 
    duration = Math.min(Math.max((duration!=null?duration:255).toInteger(),0),255)
    if (txtEnable) log.info "${device.displayName}: ledEffectOne(${lednum},${effect},${color},${level},${duration})"
	sendEvent(name:"ledEffect", value: (effect==255?"Stop":"Start")+" LED${lednum}", displayed:false)
    def cmds = []
    lednum.each {
        it= Math.min(Math.max((it!=null?it:1).toInteger(),1),7)
        Integer cmdLedNum = it.toInteger()-1    //lednum is 0-based in firmware 
        Integer cmdEffect = effect.toInteger()
        Integer cmdColor = color.toInteger()
        Integer cmdLevel = level.toInteger()
        Integer cmdDuration = duration.toInteger()
        cmds = zigbee.command(0xfc31,0x03,["mfgCode":"0x122F"],defaultDelay,"${intTo8bitUnsignedHex(cmdLedNum)} ${intTo8bitUnsignedHex(cmdEffect)} ${intTo8bitUnsignedHex(cmdColor)} ${intTo8bitUnsignedHex(cmdLevel)} ${intTo8bitUnsignedHex(cmdDuration)}")
    }
    if (logEnable) log.debug "ledEffectOne cmds = $cmds"
    return cmds
}

List<String> off() {
    //def rampRate = 0
    //if ((parameter258=="0")&&(state.model?.substring(0,5)!="VZM35"))  //if we are in dimmer mode and this is not the Fan Switch then use params 1-8 for rampRate
    //    rampRate = (parameter7!=null?parameter7:(parameter3!=null?parameter3:(parameter1!=null?parameter1:configParams["parameter001"].default)))?.toInteger()
    if (logEnable) log.debug "off()"
    //${device.currentValue('level')}%" + ", ${rampRate/10}s)"// (parameter258=="0"?", ${rampRate/10}s)":")")
    def cmds = []
    cmds += zigbee.off(defaultDelay)
    return cmds
}

List<String> on() {
    //def rampRate = 0
    //if ((parameter258=="0")&&(state.model?.substring(0,5)!="VZM35")) //if we are in dimmer mode and this is not the Fan Switch then use params 1-8 for rampRate
    //    rampRate = (parameter3!=null?parameter3:(parameter1!=null?parameter1:configParams["parameter001"].default))?.toInteger()
    if (logEnable) log.debug "on()"
    //${device.currentValue('level')}%" + ", ${rampRate/10}s)"// (parameter258=="0"?", ${rampRate/10}s)":")")
    def cmds = []
    cmds += zigbee.on(defaultDelay)
    if (traceEnable) log.trace "on $cmds"
    return cmds
}

def parse(String description) {
    if (logEnable) log.debug "parse($description)"
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "parsed descMap = $descMap"
    def attrHex =    descMap.attrInt==null?null:"0x${zigbee.convertToHexString(descMap.attrInt,4)}"
    def attrInt =    descMap.attrInt==null?null:descMap.attrInt.toInteger()
    def clusterHex = descMap.clusterInt==null?null:"0x${zigbee.convertToHexString(descMap.clusterInt,4)}"
    def clusterInt = descMap.clusterInt==null?null:descMap.clusterInt.toInteger()
    def valueStr =   descMap.value ?: "unknown"
    switch (clusterInt){
        case CLUSTER_BASIC:
            if (logEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       //(zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0004:
                    if (txtEnable) log.info "${device.displayName}: Report received Mfg:\t\t$valueStr"
                    state.manufacturer = valueStr
                    break
                case 0x0005:
                    if (txtEnable) log.info "${device.displayName}: Report received Model:\t$valueStr"
                    state.model = valueStr
                    break
                case 0x0006:
                    if (txtEnable) log.info "${device.displayName}: Report received FW Date:\t$valueStr"
                    state.fwDate = valueStr
                    break
                case 0x0007:
                    def valueInt = Integer.parseInt(descMap['value'],16)
                    valueStr = valueInt==0?"Non-Neutral":"Neutral"
                    if (txtEnable) log.info "${device.displayName}: " + green("Report received Power Source:\t$valueInt ($valueStr)")
                    state.powerSource = valueStr
                    state.parameter21value = valueInt
                    device.updateSetting("parameter21",[value:"${valueInt}",type:"enum"]) 
                    break
                case 0x4000:
                     if (txtEnable) log.info "${device.displayName}: Report received FW Version:\t$valueStr"
                     state.fwVersion = valueStr
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case CLUSTER_IDENTIFY:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (txtEnable) log.info "${device.displayName}: Report received IdentifyTime:\t$valueStr"
                    break
                default:
                    if ((txtEnable && attrInt!=null)||traceEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE ${attrInt}")
                    break
            }
            break
        case 0x0004:    //GROUP CLUSTER
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (txtEnable) log.info "${device.displayName}: Report received Group Name Support:\t$valueStr"
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case CLUSTER_SCENES:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (txtEnable) log.info "${device.displayName}: Report received Scene Count:\t$valueStr"
                    break
                case 0x0001:
                    if (txtEnable) log.info "${device.displayName}: Report received Current Scene:\t$valueStr"
                    break
                case 0x0002:
                    if (txtEnable) log.info "${device.displayName}: Report received Current Group:\t$valueStr"
                    break
                case 0x0003:
                    if (txtEnable) log.info "${device.displayName}: Report received Scene Valid:\t$valueStr"
                    break
                case 0x0004:
                    if (txtEnable) log.info "${device.displayName}: Report received Scene Name Support:\t$valueStr"
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case CLUSTER_ON_OFF:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        valueStr = valueInt == 0? "off": "on"
                        if (txtEnable) log.info "${device.displayName}: Report received Switch:\t$valueInt\t($valueStr)"
                        sendEvent(name:"switch", value: valueStr)
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                case 0x4003:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        valueStr = (valueInt==0?"Off":(valueInt==255?"Previous":"On")) 
                        if (txtEnable) log.info "${device.displayName}: Report received Power-On State:\t$valueInt\t($valueStr)"
                        state.powerOnState = valueStr
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")																										  
                    break
                default:
                    if (attrInt==null && descMap.command=="00" && descMap.direction=="00") {
                        if (txtEnable||logEnable) log.info "${device.displayName}: "+darkOrange("Cluster:$clusterHex Heartbeat")
                    }
                    else if (attrInt==null && descMap.command=="0B" && descMap.direction=="01") {
                        if (parameter$51>0) {    //not sure why the V-mark firmware sends these when there are no bindings
                            if (descMap.data[0]=="00" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tSwitch OFF"
                            if (descMap.data[0]=="01" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tSwitch ON"
                            if (descMap.data[0]=="02" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tToggle"
                        }
                    } 
                    else if (txtEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case CLUSTER_LEVEL_CONTROL:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        valueInt=Math.min(Math.max(valueInt.toInteger(),0),254)
                        def percentValue = convertByteToPercent(valueInt)
                        valueStr = percentValue.toString()+"%"
                        if (txtEnable) log.info "${device.displayName}: Report received Level:\t$valueInt\t($valueStr)"
                        sendEvent(name:"level", value: percentValue, unit: "%")
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                case 0x0010:
                    if(descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        if (txtEnable) log.info "${device.displayName}: Report received On/Off Transition:\t${valueInt/10}s"
                        state.parameter3value = valueInt
                        device.updateSetting("parameter3",[value:"${valueInt}",type:configParams["parameter003"].type.toString()])
                    }
                    else 
                        if (txtEnable || logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                case 0x0011:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        valueStr = (valueInt==255?"Previous":convertByteToPercent(valueInt).toString()+"%")
                        if (txtEnable) log.info "${device.displayName}: Report received Remote-On Level:\t$valueInt\t($valueStr)"
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                case 0x4000:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        valueStr = (valueInt==255?"Previous":convertByteToPercent(valueInt).toString()+"%")
                        if (txtEnable) log.info "${device.displayName}: Report received Power-On Level:\t$valueInt\t($valueStr)"
                        state.parameter15value = convertByteToPercent(valueInt)
                        device.updateSetting("parameter15",[value:"${convertByteToPercent(valueInt)}",type:configParams["parameter015"].type.toString()])
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                default:
                    if (attrInt==null && descMap.command=="0B" && descMap.direction=="01") {
                        if (parameter$51>0) {    //not sure why the V-mark firmware sends these when there are no bindings
                            if (descMap.data[0]=="00" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tMove To Level"
                            if (descMap.data[0]=="01" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tMove Up/Down"
                            if (descMap.data[0]=="02" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tStep"
                            if (descMap.data[0]=="03" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tStop Level Change"
                            if (descMap.data[0]=="04" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tMove To Level (with On/Off)"
                            if (descMap.data[0]=="05" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tMove Up/Down (with On/Off)"
                            if (descMap.data[0]=="06" && txtEnable) log.info "${device.displayName}: Bind Command Sent:\tStep (with On/Off)"
                        }
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case 0x0013:    //ALEXA CLUSTER
            if (txtEnable||logEnable) log.info "${device.displayName}: "+darkOrange("Alexa Heartbeat")
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            break
        case 0x0019:    //OTA CLUSTER
            if (txtEnable||logEnable) log.info "${device.displayName}: "+darkOrange("OTA CLUSTER")
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (txtEnable) log.info "${device.displayName}: Report received Server ID:\t$valueStr"
                    break
                case 0x0001:
                    if (txtEnable) log.info "${device.displayName}: Report received File Offset:\t$valueStr"
                    break
                case 0x0006:
                    if (txtEnable) log.info "${device.displayName}: Report received Upgrade Status:\t$valueStr"
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case 0x0702:    //SIMPLE METERING CLUSTER
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0000:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        float energy
                        energy = valueInt/100
                        if (txtEnable) log.info "${device.displayName}: Report received Energy:\t${energy}kWh"
                        sendEvent(name:"energy",value:energy ,unit: "kWh")
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")		  													  
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case CLUSTER_ELECTRICAL_MEASUREMENT:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            switch (attrInt) {
                case 0x0501:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        float amps
                        amps = valueInt/100
                        if (txtEnable) log.info "${device.displayName}: Report received Amps:\t${amps}A"
                        sendEvent(name:"amps",value:amps ,unit: "A")
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")
                    break
                case 0x050b:
                    if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                        def valueInt = Integer.parseInt(descMap['value'],16)
                        float power 
                        power = valueInt/10
                        if (txtEnable) log.info "${device.displayName}: Report received Power:\t${power}W"
                        sendEvent(name: "power", value: power, unit: "W")
                    }
                    else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND")				  																																	  
                    break
                default:
                    if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN ATTRIBUTE")
                    break
            }
            break
        case 0x8021:    //BINDING CLUSTER
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            break
        case 0x8022:    //UNBINDING CLUSTER
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            break
        case 0x8032:    //ROUTING TABLE CLUSTER
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            break
        case CLUSTER_PRIVATE:
            if (traceEnable) log.trace "${device.displayName}: ${clusterLookup(clusterHex)} (" +
                                       "clusterId:${descMap.cluster?:descMap.clusterId}" +
                                       (descMap.attrId==null?"":" attrId:${descMap.attrId}") +
                                       (descMap.value==null?"":" value:${descMap.value}") +
                                       (zigbee.getEvent(description)==[:]?(descMap.data==null?"":" data:${descMap.data}"):(" ${zigbee.getEvent(description)}")) + 
                                       ")"
            if (attrInt == null) {
                if (descMap.isClusterSpecific) {
                    if (descMap.command == "00") ZigbeePrivateCommandEvent(descMap.data)        //Button Events
                    if (descMap.command == "04") BindInitiator()                                //Start Binding
                    if (descMap.command == "24") ZigbeePrivateLEDeffectStopEvent(descMap.data)  //LED start/stop events
                }
            } 
            else if (descMap.command == "01" || descMap.command == "0A" || descMap.command == "0B"){
                def valueInt = Integer.parseInt(descMap['value'],16)
				def infoDev = "${device.displayName}: "
                def infoTxt = "Receive  attribute ${attrInt.toString().padLeft(3," ")} value ${valueInt.toString().padLeft(3," ")}"
				def infoMsg = infoDev + infoTxt
                switch (attrInt){
                    case 1:
                        infoMsg += "\t(Remote Dim Rate Up:\t\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"default)")
                        break
                    case 2:
                        infoMsg += "\t(Local Dim Rate Up:\t\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 1)")
                        break
                    case 3:
                        infoMsg += "\t(Remote Ramp Rate On:\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 1)")
                        break
                    case 4:
                        infoMsg += "\t(Local Ramp Rate On:\t\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 3)")
                        break
                    case 5:
                        infoMsg += "\t(Remote Dim Rate Down:\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 1)")
                        break
                    case 6:
                        infoMsg += "\t(Local Dim Rate Down:\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 2)")
                        break
                    case 7:
                        infoMsg += "\t(Remote Ramp Rate Off:\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 3)")
                        break
                    case 8:
                        infoMsg += "\t(Local Ramp Rate Off:\t\t" + (valueInt<127?((valueInt/10).toString()+"s)"):"sync with 4)")
                        break
                    case 9:     //Min Level
                        infoMsg += "\t(min level ${convertByteToPercent(valueInt)}%)"
                        break
                    case 10:    //Max Level
                        infoMsg += "\t(max level ${convertByteToPercent(valueInt)}%)" 
                        break
                    case 11:    //Invert Switch
                        infoMsg += valueInt==0?"\t(not Inverted)":"\t(Inverted)" 
                        break
                    case 12:    //Auto Off Timer
                        infoMsg += "\t(Auto Off Timer " + (valueInt==0?red("disabled"):"${valueInt}s") + ")"
                        break
                    case 13:    //Default Level (local)
                        infoMsg += "\t(default local level " + (valueInt==255?" = previous)":" ${convertByteToPercent(valueInt)}%)")
                        break
                    case 14:    //Default Level (remote)
                        infoMsg += "\t(default remote level " + (valueInt==255?" = previous)":"${convertByteToPercent(valueInt)}%)")
                        break
                    case 15:    //Level After Power Restored
                        infoMsg += "\t(power-on level " + (valueInt==255?" = previous)":"${convertByteToPercent(valueInt)}%)")
                        break
                    case 17:    //Load Level Timeout
                        infoMsg += (valueInt==0?"\t(do not display load level)":(valueInt==11?"\t(always display load level)":"s \tload level timeout"))
                        break
                    case 18: 
                        infoMsg += "\t(Active Power Report" + (valueInt==0?red(" disabled"):" ${valueInt}% change") + ")"
                        break
                    case 19:
                        infoMsg += "s\t(Periodic Power/Energy " + (valueInt==0?red(" disabled"):"") + ")"
                        break
                    case 20:
                        infoMsg += "\t(Active Energy Report " + (valueInt==0?red(" disabled"):" ${valueInt/100}kWh change") + ")"
                        break
                    case 21:    //Power Source
                        infoMsg = infoDev + green(infoTxt + (valueInt==0?"\t(Non-Neutral)":"\t(Neutral)"))
                        break
                    case 22:    //Aux Type
                        infoMsg = infoDev + red(infoTxt + (valueInt==0?"\t(No Aux)":valueInt==1?"\t(Dumb Aux)":"\t(Smart Aux)"))
                        sendEvent(name:"auxType", value:valueInt==0?"None":valueInt==1?"Dumb":"Smart", displayed:false )
                        break
                    case 50:    //Button Press Delay
                        infoMsg += "\t(${valueInt*100}ms Button Delay)"
                        break
                    case 51:    //Device Bind Number
                        infoMsg = infoDev + green(infoTxt + "\t(Bindings)")
                        sendEvent(name:"numberOfBindings", value:valueInt, displayed:false )
                        break
                    case 52:    //Smart Bulb Mode
                        infoMsg = infoDev + red(infoTxt) + (valueInt==0?red("\t(SBM disabled)"):green("\t(SBM enabled)"))
                        sendEvent(name:"smartBulb", value:valueInt==0?"Disabled":"Enabled", displayed:false )
                        break
                    case 53:  //Double-Tap UP for full brightness
                        infoMsg += "\t(Double-Tap Up " + (valueInt==0?red("disabled"):green("enabled")) + ")"
                        break
                    case 95:
                    case 96:
                        infoMsg = infoDev + hue(valueInt,infoTxt + "\t(${Math.round(valueInt/255*360)}°)")
                        break
                    case 97:  //LED bar intensity when on
                    case 98:  //LED bar intensity when off
                        infoMsg += "%\t(LED bar intensity when " + (attrInt==97?"On)":"Off)")
                        break
                    case 256:    //Local Protection
                        infoMsg += "\t(Local Control " + (valueInt==0?green("enabled"):red("disabled")) + ")"
                        break
                    case 257:    //Remote Protection
                        infoMsg += "\t(Remote Control " + (valueInt==0?green("enabled"):red("disabled")) + ")"
                        break
                    case 258:    //Switch Mode
                        infoMsg = infoDev + red(infoTxt + (valueInt==0?"\t(Dimmer mode)":"\t(On/Off mode)"))
                        sendEvent(name:"switchMode", value:valueInt==0?"Dimmer":"On/Off", displayed:false )
						break
                    case 259:    //On-Off LED
                        infoMsg += "\t(On-Off LED mode: " + (valueInt==0?"All)":"One)")
                        break
                    case 260:    //Firmware Update Indicator
                        infoMsg += "\t(Firmware Update Indicator " + (valueInt==0?red("disabled"):green("enabled")) + ")"
                        break
                    case 261:    //Relay Click
                        infoMsg += "\t(Relay Click " + (valueInt==0?green("enabled"):red("disabled")) + ")"
                        break
                    case 262:    //Double-Tap config button to clear notification
                        infoMsg += "\t(Double-Tap config button " + (valueInt==0?green("enabled"):red("disabled")) + ")"
                        break
                    default:
                        infoMsg += orangeRed(" *** Undefined Parameter $attrInt ***")
                        break
                }
                if (logEnable) log.debug infoMsg
                if ((attrInt==9)||(attrInt==10)||(attrInt==13)||(attrInt==14)||(attrInt==15)) valueInt = convertByteToPercent(valueInt)    //these attributes are stored as bytes but presented as percentages
                if (attrInt>0) device.updateSetting("parameter${attrInt}",[value:"${valueInt}",type:configParams["parameter${attrInt.toString().padLeft(3,"0")}"].type.toString()]) //update local setting with value received from device                   
                state."parameter${attrInt}value" = valueInt  //update state variable with value received from device
                if ((attrInt==95 && parameter95custom!=null)||(attrInt==96 && parameter96custom!=null)) {   //if custom hue was set, update the custom state variable also
                    device.updateSetting("parameter${attrInt}custom",[value:"${Math.round(valueInt/255*360)}",type:configParams["parameter${attrInt.toString().padLeft(3,"0")}"].type.toString()])
                    state."parameter${attrInt}custom" = Math.round(valueInt/255*360)
                }
                if ((valueInt==configParams["parameter${attrInt.toString()?.padLeft(3,"0")}"]?.default?.toInteger())  //IF  setting is the default
                && (attrInt!=21)&&(attrInt!=22)&&(attrInt!=51)&&(attrInt!=52)&&(attrInt!=258)) {                      //AND  not read-only or primary config params
                    if (logEnable) log.debug "${device.displayName}: parse() cleared parameter${attrInt}"
                    device.clearSetting("parameter${attrInt}")                                                        //THEN clear the setting (so only changed settings are displayed)
                }
            }
            else if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("${clusterLookup(clusterHex)}(${clusterHex}) UNKNOWN COMMAND" + (logEnable?"\t$descMap\t${zigbee.getEvent(description)}":""))
            break
        default:
            if (txtEnable||logEnable) log.warn "${device.displayName}: "+fireBrick("Cluster:$clusterHex UNKNOWN CLUSTER" + (logEnable?"\t$descMap\t${zigbee.getEvent(description)}":""))
            break
    }
}

List<String> presetLevel(value) {    //possible future command
    if (txtEnable) log.info "${device.displayName}: presetLevel(${value})"
    def cmds = []
    Integer scaledValue = value==null?null:Math.min(Math.max(convertPercentToByte(value.toInteger()),1),255)  //ZigBee levels range from 0x01-0xfe with 00 and ff = 'use previous'
    cmds += setPrivateCluster(13, scaledValue, 8)
    //if (logEnable) log.trace "presetLevel cmds = $cmds"
    return cmds
}
  
List<String> refresh(option) {
    option = (option == null || option == " ") ? "" : option
    if (txtEnable) log.info "${device.displayName}: refresh(${option})"
    def cmds = []
    //cmds += zigbee.readAttribute(0x0000, 0x0000, [:], defaultDelay)    //CLUSTER_BASIC ZCL Version
    //cmds += zigbee.readAttribute(0x0000, 0x0001, [:], defaultDelay)    //CLUSTER_BASIC Application Version
    //cmds += zigbee.readAttribute(0x0000, 0x0002, [:], defaultDelay)    //CLUSTER_BASIC 
    //cmds += zigbee.readAttribute(0x0000, 0x0003, [:], defaultDelay)    //CLUSTER_BASIC 
    cmds += zigbee.readAttribute(0x0000, 0x0004, [:], defaultDelay)    //CLUSTER_BASIC Mfg
    cmds += zigbee.readAttribute(0x0000, 0x0005, [:], defaultDelay)    //CLUSTER_BASIC Model
    cmds += zigbee.readAttribute(0x0000, 0x0006, [:], defaultDelay)    //CLUSTER_BASIC SW Date Code
    cmds += zigbee.readAttribute(0x0000, 0x0007, [:], defaultDelay)    //CLUSTER_BASIC Power Source
    //cmds += zigbee.readAttribute(0x0000, 0x0008, [:], defaultDelay)    //CLUSTER_BASIC dev class
    //cmds += zigbee.readAttribute(0x0000, 0x0009, [:], defaultDelay)    //CLUSTER_BASIC dev type
    //cmds += zigbee.readAttribute(0x0000, 0x000A, [:], defaultDelay)    //CLUSTER_BASIC prod code
    //cmds += zigbee.readAttribute(0x0000, 0x000B, [:], defaultDelay)    //CLUSTER_BASIC prod url
    cmds += zigbee.readAttribute(0x0000, 0x4000, [:], defaultDelay)    //CLUSTER_BASIC SW Build ID
    //cmds += zigbee.readAttribute(0x0003, 0x0000, [:], defaultDelay)    //CLUSTER_IDENTIFY Identify Time
    //cmds += zigbee.readAttribute(0x0004, 0x0000, [:], defaultDelay)    //CLUSTER_GROUP Name Support
    //cmds += zigbee.readAttribute(0x0005, 0x0000, [:], defaultDelay)    //CLUSTER_SCENES Scene Count
    //cmds += zigbee.readAttribute(0x0005, 0x0001, [:], defaultDelay)    //CLUSTER_SCENES Current Scene
    //cmds += zigbee.readAttribute(0x0005, 0x0002, [:], defaultDelay)    //CLUSTER_SCENES Current Group
    //cmds += zigbee.readAttribute(0x0005, 0x0003, [:], defaultDelay)    //CLUSTER_SCENES Scene Valid
    //cmds += zigbee.readAttribute(0x0005, 0x0004, [:], defaultDelay)    //CLUSTER_SCENES Name Support
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], defaultDelay)    //CLUSTER_ON_OFF Current OnOff state
    cmds += zigbee.readAttribute(0x0006, 0x4003, [:], defaultDelay)    //CLUSTER_ON_OFF Startup OnOff state
    cmds += zigbee.readAttribute(0x0008, 0x0000, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL Current Level
    //cmds += zigbee.readAttribute(0x0008, 0x0001, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL Remaining Time
    //cmds += zigbee.readAttribute(0x0008, 0x000F, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL Options
    if (state.model?.substring(0,5)!="VZM35")  //Fan does not support on_off transition time
      cmds += zigbee.readAttribute(0x0008, 0x0010, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL OnOff Transition Time
    cmds += zigbee.readAttribute(0x0008, 0x0011, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL Default Remote On Level
    cmds += zigbee.readAttribute(0x0008, 0x4000, [:], defaultDelay)    //CLUSTER_LEVEL_CONTROL Startup Level
    //cmds += zigbee.readAttribute(0x0019, 0x0000, [:], defaultDelay)    //CLUSTER_OTA Upgrade Server ID
    //cmds += zigbee.readAttribute(0x0019, 0x0001, [:], defaultDelay)    //CLUSTER_OTA File Offset
    //cmds += zigbee.readAttribute(0x0019, 0x0006, [:], defaultDelay)    //CLUSTER_OTA Image Upgrade Status
    if (state.model?.substring(0,5)!="VZM35")  //Fan does not support power/energy reports
      cmds += zigbee.readAttribute(0x0702, 0x0000, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING Energy Report
    //cmds += zigbee.readAttribute(0x0702, 0x0200, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING Status
    //cmds += zigbee.readAttribute(0x0702, 0x0300, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING Units
    //cmds += zigbee.readAttribute(0x0702, 0x0301, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING AC Multiplier
    //cmds += zigbee.readAttribute(0x0702, 0x0302, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING AC Divisor
    //cmds += zigbee.readAttribute(0x0702, 0x0303, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING Formatting
    //cmds += zigbee.readAttribute(0x0702, 0x0306, [:], defaultDelay)    //CLUSTER_SIMPLE_METERING Metering Device Type
    //cmds += zigbee.readAttribute(0x0B04, 0x0501, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Line Current
    //cmds += zigbee.readAttribute(0x0B04, 0x0502, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Active Current
    //cmds += zigbee.readAttribute(0x0B04, 0x0503, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Reactive Current
    //cmds += zigbee.readAttribute(0x0B04, 0x0505, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Voltage
    //cmds += zigbee.readAttribute(0x0B04, 0x0506, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Voltage min
    //cmds += zigbee.readAttribute(0x0B04, 0x0507, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Voltage max
    //cmds += zigbee.readAttribute(0x0B04, 0x0508, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Current
    //cmds += zigbee.readAttribute(0x0B04, 0x0509, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Current min
    //cmds += zigbee.readAttribute(0x0B04, 0x050A, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT RMS Current max
    if (state.model?.substring(0,5)!="VZM35")  //Fan does not support power/energy reports
      cmds += zigbee.readAttribute(0x0B04, 0x050B, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Active Power
    //cmds += zigbee.readAttribute(0x0B04, 0x050C, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Active Power min
    //cmds += zigbee.readAttribute(0x0B04, 0x050D, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Active Power max
    //cmds += zigbee.readAttribute(0x0B04, 0x050E, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Reactive Power
    //cmds += zigbee.readAttribute(0x0B04, 0x050F, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Apparent Power
    //cmds += zigbee.readAttribute(0x0B04, 0x0510, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Power Factor
    //cmds += zigbee.readAttribute(0x0B04, 0x0604, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Power Multiplier
    //cmds += zigbee.readAttribute(0x0B04, 0x0605, [:], defaultDelay)    //CLUSTER_ELECTRICAL_MEASUREMENT Power Divisor
    //cmds += zigbee.readAttribute(0x8021, 0x0000, [:], defaultDelay)    //Binding Cluster
    //cmds += zigbee.readAttribute(0x8022, 0x0000, [:], defaultDelay)    //UnBinding Cluster
    getParameterNumbers().each{ i -> 
        switch (option) {
            case "":
            case " ":
            case null:
                if (((i>=1)&&(i<=8))||(i==21)||(i==22)||(i==51)||(i==52)||(i==258)) cmds += getAttribute(0xfc31, i) //if option is blank or null then refresh primary and read-only settings
                break
            case "User":                
                if (settings."parameter${i}"!=null) cmds += getAttribute(0xfc31, i) //if option is User then refresh settings that are non-blank
                break
            case "All":
                cmds += getAttribute(0xfc31, i) //if option is All then refresh all settings
                break
            
        }
    }
    return cmds
}

def resetEnergyMeter() {
    if (logEnable) log.debug "${device.displayName}: resetEnergyMeter(" + device.currentValue("energy") + "kWh)"
    def cmds = []
    cmds += zigbee.command(0xfc31,0x02,["mfgCode":"0x122F"],defaultDelay,"0")
    cmds += zigbee.readAttribute(CLUSTER_SIMPLE_METERING, 0x0000)
    //if (logEnable) log.debug "resetEnergy $cmds"
    return cmds 
}

List<String> setAttribute(Integer cluster, Integer attrInt, Integer dataType, Integer value, Map additionalParams = [:], Integer delay=defaultDelay) {
    if (cluster==0xfc31) additionalParams = ["mfgCode":"0x122F"]
    if ((delay==null)||(delay==0)) delay = defaultDelay
    if (logEnable) log.debug "${device.displayName} setAttribute(" +
                             "0x${zigbee.convertToHexString(cluster,4)}, " +
                             "0x${zigbee.convertToHexString(attrInt,4)}, " +
                             "0x${zigbee.convertToHexString(dataType,2)}, " +
                               "${value}, ${additionalParams}, ${delay})"
    String infoMsg = "${device.displayName}: Sending "
    if (cluster==0xfc31) {
        infoMsg += " attribute ${attrInt.toString().padLeft(3," ")} value "
        switch (attrInt) {
            case 9:     //min level
            case 10:    //max level
            case 13:    //default local level
            case 14:    //default remote level
            case 15:    //level after power restored
                infoMsg += "${convertByteToPercent(value)}%\tconverted to ${value}\t(0..255 scale)"
                break
            case 95:
            case 96:
                infoMsg += "${value.toString().padLeft(3," ")} (" + Math.round(value/255*360) + "°)"
                break
            default:
                infoMsg += "${value.toString().padLeft(3," ")}"
                break 
        }
    } 
    else {
        infoMsg += "" + (cluster==0xfc31?"":clusterLookup(cluster)) + " attribute 0x${zigbee.convertToHexString(attrInt,4)} value ${value}"
    }
    if (logEnable) log.info infoMsg + (delay==defaultDelay?"":" [delay ${delay}]")
    def cmds = zigbee.writeAttribute(cluster, attrInt, dataType, value, additionalParams, delay)
    if (logEnable) log.debug "setAttr $cmds"
    return cmds
}

def getAttribute(Integer cluster, Integer attrInt, Map additionalParams = [:], Integer delay=defaultDelay) {
    if (cluster==0xfc31) additionalParams = ["mfgCode":"0x122F"]
    if (delay==null||delay==0) delay = defaultDelay
    if (logEnable) log.trace  "${device.displayName}: Getting "+(cluster==0xfc31?"":clusterLookup(cluster))+" attribute ${attrInt}"+(delay==defaultDelay?"":" [delay ${delay}]")
    if (logEnable) log.debug  "${device.displayName} getAttribute(0x${zigbee.convertToHexString(cluster,4)}, 0x${zigbee.convertToHexString(attrInt,4)}, ${additionalParams}, ${delay})"
    def cmds = []
    //String mfgCode = "{}"
    //if(additionalParams.containsKey("mfgCode")) mfgCode = "{${additionalParams.get("mfgCode")}}"
    //String rattrArgs = "0x${device.deviceNetworkId} 0x01 0x${zigbee.convertToHexString(cluster,4)} " + 
    //                   "0x${zigbee.convertToHexString(attrInt,4)} " + 
    //                   "$mfgCode"
    //cmds += ["he rattr $rattrArgs", "delay $delay"] 
    cmds += zigbee.readAttribute(cluster, attrInt, additionalParams, delay)
    if (logEnable) log.trace "getAttr $cmds"
    return cmds
}

List<String> setLevel(Number newLevel, Number duration=null) {
    if (logEnable) log.debug "setLevel($newLevel, $duration)"
    if (duration!=null) duration = duration.toInteger()*10  //firmware duration in 10ths
    def cmds = []
    cmds += duration==null?zigbee.setLevel(newLevel):zigbee.setLevel(newLevel,duration)
    if (logEnable) log.trace "setLevel $cmds"
    return cmds
}

List<String> setPrivateCluster(attributeId, value=null, size=8) {
    if (logEnable) log.debug "setPrivateCluster($attributeId, $value, $size)"
    def cmds = []
    Integer attId = attributeId.toInteger()
    Integer attValue = (value?:0).toInteger()
    Integer attSize = calculateSize(size).toInteger()
    if (value!=null) cmds += setAttribute(0xfc31,attId,attSize,attValue,[:],attId==258?longerDelay:defaultDelay)
    cmds += getAttribute(0xfc31, attId)
    //if (traceEnable) log.trace "setPrivate $cmds"
    return cmds
}

def setZigbeeAttribute(cluster, attributeId, value=null, size=8) {
    if (logEnable) log.debug "setZigbeeAttribute($cluster, $attributeId, $value, $size)"
    def cmds = []
    Integer setCluster = cluster.toInteger()
    Integer attId = attributeId.toInteger()
    Integer attValue = (value?:0).toInteger()
    Integer attSize = calculateSize(size).toInteger()
    if (value!=null) cmds += setAttribute(setCluster,attId,attSize,attValue,[:],attId==258?longerDelay:defaultDelay)
    cmds += getAttribute(setCluster, attId)
    //if (traceEnable) log.trace "setZigbee $cmds"
    return cmds
}

List<String> startLevelChange(direction, duration=null) {
   if (logEnable) log.debug "startLevelChange($direction, $duration)"
   Integer newLevel = direction=="up" ? 100 : (device.currentValue("switch") == "off" ? 0 : 1)
   if (parameter258 == "1") duration= 0  //if switch mode is on/off then ramping is 0
   //if (duration==null){               //if we didn't pass in the duration then get it from parameters
   //    if (direction=="up")           //if direction is up use parameter1 dimming duration
   //        duration = (parameter1!=null?parameter1:configParams["parameter001"].default)?.toInteger()    //dimming up, use parameter1, if null use default
   //    else                           //else direction is down so use parameter5 dim duration unless default then use parameter1 dim duration
   //        duration = (parameter5!=null?parameter5:(parameter1!=null?parameter1:configParams["parameter001"].default))?.toInteger()
   //}
   //else {
   //    duration = duration*10          //we passed in seconds but calculations are based on 10ths of seconds
   //}
   //if (duration==null) duration = configParams["parameter001"].default.toInteger()	//catch-all just in case we still have a null then use parameter001 default
   if (duration!=null) duration = duration.toInteger()*10  //firmware duration in 10ths
   List<String> cmds = duration==null?zigbee.setLevel(newLevel):zigbee.setLevel(newLevel, duration)
   return cmds
}

List<String> stopLevelChange() {
   if (logEnable) log.debug "stopLevelChange()"
   List<String> cmds = ["he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} ${CLUSTER_LEVEL_CONTROL} ${COMMAND_STOP} {}","delay $defaultDelay"]
   if (traceEnable) log.trace "stopLevel $cmds"
   return cmds
}

List<String> updated(option) {
   option = (option==null||option==" ")?"":option
   if (logEnable)  runIn(disableDebugLogging*60, "logsOff") 
   def changedParams = []
   def cmds = []
   def nothingChanged = true
   int setAttrDelay = defaultDelay 
   int defaultValue
   int newValue
   int oldValue
   getParameterNumbers().each{ i ->
      defaultValue=configParams["parameter${i.toString().padLeft(3,'0')}"].default.toInteger()
      oldValue=state."parameter${i}value"!=null?state."parameter${i}value".toInteger():defaultValue
      if ((i==9)||(i==10)||(i==13)||(i==14)||(i==15)) {    //convert the percent preferences back to byte values before testing for changes
         defaultValue=convertPercentToByte(defaultValue)
         oldValue=convertPercentToByte(oldValue)
      }
      if ((i==95 && parameter95custom!=null)||(i==96 && parameter96custom!=null)) {                                         //IF  a custom hue value is set
         if ((Math.round(settings?."parameter${i}custom"?.toInteger()/360*255)==settings?."parameter${i}"?.toInteger())) { //AND custom setting is same as normal setting
               device.clearSetting("parameter${i}custom")                                                                    //THEN clear custom hue and use normal color 
               if (txtEnable) log.info "${device.displayName}: Cleared Custom Hue setting since it equals standard color setting"
         }
         oldvalue=state."parameter${i}custom"!=null?state."parameter${i}custom".toInteger():oldValue
      }
      newValue = calculateParameter(i)
      if ((option == "Default")&&(i!=21)&&(i!=22)&&(i!=51)&&(i!=52)&&(i!=258)){    //if DEFAULT option was selected then use the default value (but don't change switch modes)
         newValue = defaultValue
         if (logEnable) log.debug "${device.displayName}: updated() has cleared parameter${attrInt}"
         device.clearSetting("parameter${i}")  //and clear the local settings so they go back to default values
         if ((i==95)||(i==96)) device.clearSetting("parameter${i}custom")    //clear the custom hue colors also
      }
      //If a setting changed OR we selected ALL then update parameters in the switch (but don't change switch modes when ALL is selected)
      //log.debug "Param:$i default:$defaultValue oldValue:$oldValue newValue:$newValue setting:${settings."parameter$i"} `$option`"
      if ((newValue!=oldValue) 
      || ((option=="User")&&(settings."parameter${i}"!=null)) 
      || ((option=="All")&&(i!=258))) {
         if ((i==52)||(i==258)) setAttrDelay = setAttrDelay!=longerDelay?longerDelay:defaultDelay  //IF   we're changing modes THEN delay longer
         else                   setAttrDelay = defaultDelay                                        //ELSE set back to default delay if we already delayed previously
         cmds += setAttribute(0xfc31, i, calculateSize(configParams["parameter${i.toString().padLeft(3,'0')}"].size), newValue.toInteger(), ["mfgCode":"0x122F"], setAttrDelay)
         changedParams += i
         nothingChanged = false
      }
   }
   changedParams.each{ i ->     //read back the parameters we've changed so the state variables are updated 
      cmds += getAttribute(0xfc31, i)
   }
   if (nothingChanged && (txtEnable||logEnable||traceEnable)) {
      log.debug "${device.displayName}: No device settings were changed"
      log.info  "${device.displayName}: Info logging    "  + (txtEnable?green("Enabled"):red("Disabled"))
      log.debug "${device.displayName}: Debug logging "    + (logEnable?green("Enabled"):red("Disabled"))
   }
   return cmds
}

List<String> updateFirmware() {
    if (logEnable) log.debug "updateFirmware()"
    /*  // remove and replace with this when no longer beta firmware:
    def cmds = []
    cmds += zigbee.updateFirmware()
    if (logEnable) log.trace "updateFirmware cmds = $cmds"
    return cmds
    */
    if (state.lastUpdateFw != null && now() - state.lastUpdateFw < 2000) {
    List<String> cmds = []
    cmds += zigbee.updateFirmware()
    if (logEnable) log.debug "updateFirmware cmds = $cmds"
    return cmds
    } else {
        log.warn "Firmware in this channel may be \"beta\" quality. Please check https://community.inovelli.com/c/switches/switch-firmware/42 before proceeding. Double-click \"Update Firmware\" to proceed"
    }
    state.lastUpdateFw = now()
    return []
}

void ZigbeePrivateCommandEvent(data) {
    if (logEnable) log.debug "${device.displayName}: ButtonNumber: ${data[0]} ButtonAttributes: ${data[1]}"
    Integer ButtonNumber = Integer.parseInt(data[0],16)
    Integer ButtonAttributes = Integer.parseInt(data[1],16)
    switch(zigbee.convertToHexString(ButtonNumber,2) + zigbee.convertToHexString(ButtonAttributes,2)) {
        case "0200":    //Tap Up 1x
            buttonEvent(1, "pushed", "physical")
            break
        case "0203":    //Tap Up 2x
            buttonEvent(3, "pushed", "physical")
            break
        case "0204":    //Tap Up 3x
            buttonEvent(5, "pushed", "physical")
            break
        case "0205":    //Tap Up 4x
            buttonEvent(7, "pushed", "physical")
            break
        case "0206":    //Tap Up 5x
            buttonEvent(9, "pushed", "physical")
            break
        case "0202":    //Hold Up
            buttonEvent(1, "held", "physical")
            break
        case "0201":    //Release Up
            buttonEvent(1, "released", "physical")
            break
        case "0100":    //Tap Down 1x
            buttonEvent(2, "pushed", "physical")
            break
        case "0103":    //Tap Down 2x
            buttonEvent(4, "pushed", "physical")
            break
        case "0104":    //Tap Down 3x
            buttonEvent(6, "pushed", "physical")
            break
        case "0105":    //Tap Down 4x
            buttonEvent(8, "pushed", "physical")
            break
        case "0106":    //Tap Down 5x
            buttonEvent(10, "pushed", "physical")
            break
        case "0102":    //Hold Down
            buttonEvent(2, "held", "physical")
            break
        case "0101":    //Release Down
            buttonEvent(2, "released", "physical")
            break
        case "0300":    //Tap Config 1x
            buttonEvent(11, "pushed", "physical")
            break
        case "0303":    //Tap Config 2x
            buttonEvent(12, "pushed", "physical")
            break
        case "0304":    //Tap Config 3x
            buttonEvent(13, "pushed", "physical")
            break
        case "0305":    //Tap Config 4x
            buttonEvent(14, "pushed", "physical")
            break
        case "0306":    //Tap Config 5x
            buttonEvent(15, "pushed", "physical")
            break
        case "0302":    //Hold Config
            buttonEvent(11, "held", "physical")
            break
        case "0301":    //Release Config
            buttonEvent(11, "released", "physical")
            break
        default:       //undefined button function
            log.warn "${device.displayName}: "+fireBrick("Undefined button function ButtonNumber: ${data[0]} ButtonAttributes: ${data[1]}")
            break
    }
}

void ZigbeePrivateLEDeffectStopEvent(data) {
    Integer ledNumber = Integer.parseInt(data[0],16)+1 //internal LED number is 0-based
    String  ledStatus = ledNumber==17?"Stop All":ledNumber==256?"User Cleared":"Stop LED${ledNumber}"
    if (txtEnable) log.info "${device.displayName}: ledEffect: ${ledStatus}"
	switch(ledNumber){
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 17:  //Full LED bar effects
        case 256: //user double-pressed the config button to clear the notification
			sendEvent(name:"ledEffect", value: "${ledStatus}", displayed:false)
            break
        default:  
			log.warn "${device.displayName}: "+fireBrick("Undefined LEDeffectStopEvent: ${data[0]}")
            break
    }
}

void buttonEvent(button, action, type = "digital") {
    if (txtEnable) log.info "${device.displayName}: ${type} Button ${button} was ${action}"
    sendEvent(name: action, value: button, isStateChange: true, type: type)
}

void hold(button) {
    buttonEvent(button, "held", "digital")
}

void push(button) {
    buttonEvent(button, "pushed", "digital")
}

void release(button) {
    buttonEvent(button, "released", "digital")
}

void logsOff() {
   log.warn "debug logging disabled..."
   device.updateSetting("logEnable", [value:"false", type:"bool"])
}


@Field static final Integer CLUSTER_BASIC = 0x0000
@Field static final Integer CLUSTER_POWER = 0x0001
@Field static final Integer CLUSTER_IDENTIFY = 0x0003
@Field static final Integer CLUSTER_GROUP = 0x0004
@Field static final Integer CLUSTER_SCENES = 0x0005
@Field static final Integer CLUSTER_ON_OFF = 0x0006
@Field static final Integer CLUSTER_LEVEL_CONTROL = 0x0008
@Field static final Integer CLUSTER_SIMPLE_METERING = 0x0702
@Field static final Integer CLUSTER_ELECTRICAL_MEASUREMENT = 0x0B04
@Field static final Integer CLUSTER_PRIVATE = 0xFC31


@Field static final Integer COMMAND_MOVE_LEVEL = 0x00
@Field static final Integer COMMAND_MOVE = 0x01
@Field static final Integer COMMAND_STEP = 0x02
@Field static final Integer COMMAND_STOP = 0x03
@Field static final Integer COMMAND_MOVE_LEVEL_ONOFF = 0x04
@Field static final Integer COMMAND_MOVE_ONOFF = 0x05
@Field static final Integer COMMAND_STEP_ONOFF = 0x06

@Field static final Integer BASIC_ATTR_POWER_SOURCE = 0x0007
@Field static final Integer POWER_ATTR_BATTERY_PERCENTAGE_REMAINING = 0x0021
@Field static final Integer POSITION_ATTR_VALUE = 0x0055

@Field static final Integer COMMAND_OFF = 0x00
@Field static final Integer COMMAND_ON = 0x01
@Field static final Integer COMMAND_TOGGLE = 0x02
@Field static final Integer COMMAND_PAUSE = 0x02
@Field static final Integer ENCODING_SIZE = 0x39


//Functions to enhance text appearance
String bold(s)      { return "<b>$s</b>" }
String italic(s)    { return "<i>$s</i>" }
String mark(s)      { return "<mark>$s</mark>" }  // yellow background
String strike(s)    { return "<s>$s</s>" }
String underline(s) { return "<u>$s</u>" }

String hue(h,s) { 
    h = Math.min(Math.max((h!=null?h:170),1),255)    //170 is Inovelli factory default blue
    if (h==255) s = '<font style="background-color:Gray;color:White;"> ' + s + ' </font>'
    else        s = '<font color="' + hubitat.helper.ColorUtils.rgbToHEX(hubitat.helper.ColorUtils.hsvToRGB([(h/255*100), 100, 100])) + '">' + s + '</font>'
    return s
}


//Reds
String indianRed(s) { return '<font color = "IndianRed">' + s + '</font>'}
String lightCoral(s) { return '<font color = "LightCoral">' + s + '</font>'}
String crimson(s) { return '<font color = "Crimson">' + s + '</font>'}
String red(s) { return '<font color = "Red">' + s + '</font>'}
String fireBrick(s) { return '<font color = "FireBrick">' + s + '</font>'}
String coral(s) { return '<font color = "Coral">' + s + '</font>'}

//Oranges
String orangeRed(s) { return '<font color = "OrangeRed">' + s + '</font>'}
String darkOrange(s) { return '<font color = "DarkOrange">' + s + '</font>'}
String orange(s) { return '<font color = "Orange">' + s + '</font>'}

//Yellows
String gold(s) { return '<font color = "Gold">' + s + '</font>'}
String yellow(s) { return '<font color = "yellow">' + s + '</font>'}
String paleGoldenRod(s) { return '<font color = "PaleGoldenRod">' + s + '</font>'}
String peachPuff(s) { return '<font color = "PeachPuff">' + s + '</font>'}
String darkKhaki(s) { return '<font color = "DarkKhaki">' + s + '</font>'}

//Greens
String limeGreen(s) { return '<font color = "LimeGreen">' + s + '</font>'}
String green(s) { return '<font color = "green">' + s + '</font>'}
String darkGreen(s) { return '<font color = "DarkGreen">' + s + '</font>'}
String olive(s) { return '<font color = "Olive">' + s + '</font>'}
String darkOliveGreen(s) { return '<font color = "DarkOliveGreen">' + s + '</font>'}
String lightSeaGreen(s) { return '<font color = "LightSeaGreen">' + s + '</font>'}
String darkCyan(s) { return '<font color = "DarkCyan">' + s + '</font>'}
String teal(s) { return '<font color = "Teal">' + s + '</font>'}

//Blues
String cyan(s) { return '<font color = "Cyan">' + s + '</font>'}
String lightSteelBlue(s) { return '<font color = "LightSteelBlue">' + s + '</font>'}
String steelBlue(s) { return '<font color = "SteelBlue">' + s + '</font>'}
String lightSkyBlue(s) { return '<font color = "LightSkyBlue">' + s + '</font>'}
String deepSkyBlue(s) { return '<font color = "DeepSkyBlue">' + s + '</font>'}
String dodgerBlue(s) { return '<font color = "DodgerBlue">' + s + '</font>'}
String blue(s) { return '<font color = "blue">' + s + '</font>'}
String midnightBlue(s) { return '<font color = "midnightBlue">' + s + '</font>'}

//Purples
String magenta(s) { return '<font color = "Magenta">' + s + '</font>'}
String rebeccaPurple(s) { return '<font color = "RebeccaPurple">' + s + '</font>'}
String blueViolet(s) { return '<font color = "BlueViolet">' + s + '</font>'}
String slateBlue(s) { return '<font color = "SlateBlue">' + s + '</font>'}
String darkSlateBlue(s) { return '<font color = "DarkSlateBlue">' + s + '</font>'}

//Browns
String burlywood(s) { return '<font color = "Burlywood">' + s + '</font>'}
String goldenrod(s) { return '<font color = "Goldenrod">' + s + '</font>'}
String darkGoldenrod(s) { return '<font color = "DarkGoldenrod">' + s + '</font>'}
String sienna(s) { return '<font color = "Sienna">' + s + '</font>'}

//Grays
String lightGray(s) { return '<font color = "LightGray">' + s + '</font>'}
String gray(s) { return '<font color = "Gray">' + s + '</font>'}
String dimGray(s) { return '<font color = "DimGray">' + s + '</font>'}
String slateGray(s) { return '<font color = "SlateGray">' + s + '</font>'}
String black(s) { return '<font color = "Black">' + s + '</font>'}

//**********************************************************************************
//****** End of HTML enhancement functions.
//**********************************************************************************
