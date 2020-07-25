# Device Status Announcer

**Current Version:** 1.0.0

This app is designed to check various device states (contact sensors, locks, motion sensors) when "triggered" via a specific
time or switch and either speak, notify, or both if any devices are in an undesired state.
For example, you can select door locks and receive an annoucement like "Front door lock is open" over TTS and/or via
a device notification.

## Installation Instructions

1. Add the app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DeviceActivityCheck/DeviceStatusAnnouncer1.groovy

2. Create a new instance of the app under "Apps" > "Add User App." Choose "Device Status Announcer," and follow the instructions.

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps">the
first five steps of the Hubitat support document on custom app installation</a> and consider whether using custom code
is a good choice for you.