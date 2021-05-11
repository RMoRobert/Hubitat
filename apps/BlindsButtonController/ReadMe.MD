# Blinds Button Controller

**Current Version:** 1.0.0

This app is designed to be used to used with button devices and makes it easy to configure each button to adjust blinds/shades
with button presses as specified. This app is particuarly useful for blinds/shades devices that take a few seconds or more to
report new level/position states back to the hub: when adjusting (e.g., "open by 10% more"), the app will remember the last
command it sent to the device and assume that this represents the curent state; most other apps (and rules by default) will
read the attribute from the device, which may not yet be accurate.

For more discussion see, the <a  href="https://community.hubitat.com/TODO">thread in the Hubitat Community</a>.

## Installation Instructions

1. Add the parent app code:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/BlindsButtonController/BlindsButtonControllerParent.groovy

2. Add the child app code:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/BlindsButtonController/BlindsButtonController1.groovy

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps">the
first five steps of the Hubitat support document on custom app installation</a> and consider whether using custom code
is a good choice for you.