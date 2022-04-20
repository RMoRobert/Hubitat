# Device Status Announcer

This app is designed to check various device states (contact sensors, locks, motion sensors) when "triggered" via a specific
time or switch and either speak, notify, or both if any devices are in an undesired state.
For example, you can select door locks and receive an annoucement like "Front door lock is open" over TTS and/or via
a device notification.

## Installation Instructions

1. Add the parent app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DeviceStatusAnnouncer/DeviceStatusAnnouncerParent.groovy

2. Add the child app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DeviceStatusAnnouncer/DeviceStatusAnnouncer3Child.groovy

2. Create a new instance of the app under "Apps" > "Add User App." Choose "Device Status Announcer," and follow the instructions.

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps">the
first five steps of the Hubitat support document on custom app installation</a> and consider whether using custom code
is a good choice for you.

## Note for 2.x Upgraders

If you are upgrading from v2.x, install the v3 child app as a <em>new</em> app, i.e., do not overwrite the 
existing 2.x child app code. Both versions can exist simultaneously, though you can delete the 2.x child app code
if/when you remove all v2.x  child apps (by deleting and/or recreating them in v3; there is no automatic upgrade path
but also no reason to change existing installations). install.

The parent app code can (and should) be overwritten with the updated version. Again, do NOT overwrite the v2.x
child code but rather add v3 as new app code.

## Note for 1.x Upgraders

If you are upgrading from v1.x, consider uninstalling and restarting with a fresh 3.x install. Alternatively,
modify the child app code by commenting out the "parent" line (as of this writing, line 49) as instructed in the
code (place two slashes in front of the line). You do not need to install the parent app and would continue
creating new instances of the "child" (only) app as before. You will need to re-comment or remove this line
any time you upgrade the (child) app code. It is recommended you start with a new parent/child 3.x install
if possible.