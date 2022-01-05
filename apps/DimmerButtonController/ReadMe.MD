# Dimmer Button Controller

This app is designed to be used to used with button devices and makes it easy to configure each button to turn on/off and/or set level of certain bulbs or to dim/brighten bulbs with button presses as specified. It was specifically created with the goal of emulating a Hue Dimmer (as it behaves when paired to Hue) with a Pico 5-button remote paired to Hubitat with the "fast" (press/release, not press/hold) driver, though it should work for any compatible button device.

For more discussion, to ask questions, or report issues, see the <a  href="https://community.hubitat.com/t/release-dimmer-button-controller-configure-pico-to-emulate-hue-dimmer-or-any-button-device-to-easily-control-lights/7726">thread in the Hubitat Community</a>. (Please use the Community instead of GitHub for this.)

## Installation Instructions

1. Add the parent app code:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DimmerButtonController/DimmerButtonControllerParent.groovy

2. Add the child app code:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DimmerButtonController/DimmerButtonController3.groovy

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps">the first five steps of the Hubitat support document on custom app installation</a>.

## Instructions for Upgrading from 1.x or 2.x

**IMPORTANT**: If you are upgrading from Dimmer Button Controller 1.x or 2.x, **do not overwrite the 1.x or 2.x child app code with the 3.x child app**. They are not compatible, and your old child apps will break. Instead, to upgrade:
1. Update the parent app code to the current version (above)
2. Add the child app code for version 3.x, above, as a **new** app (i.e., do not overwrite the existing 1.x or 2.x child app code).

Notes:
* Your new child apps should show up in your app code list afterwards as "Dimmer Button Controller (Child App) 3". Older versions will appear as "Dimmer Button Controller (Child App)" or "Dimmer Button Controller (Child App) 2".
* You may keep one or both old app code sets installed (and must leave the approprate version if you have 1.x or 2.x child apps isntalled). The parent app will show and let you create both if you have both installed, but its recommended that you create only 3.x instances going forward.
* If you forgot the above steps and overwrote the 1.x or 2.x code with 3.x (or want to install 1.x or 2.x new for some reason), you can find the old child app versions in the <a  href="https://github.com/RMoRobert/Hubitat/tree/master/apps/DimmerButtonController">/deprecated</a> folder of the DBC repository folder.  

## Instructions for Upgrading (within 3.x)
There are no special considerations if you are upgrading one version to the 3.x child app to a newer version in the 3.x series (just import the new code over the old as usual), but pay attention to notes in the <a  href="https://community.hubitat.com/t/release-dimmer-button-controller-configure-pico-to-emulate-hue-dimmer-or-any-button-device-to-easily-control-lights/7726">forum thread</a> for any special issues that may arise.
