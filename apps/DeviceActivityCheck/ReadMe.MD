# Device Activity Check

**Current Version:** 1.0.0

This app is designed to periodically check the "Last Acvtivity At" data on selected devices and notify you if
activity has not happened within your specified timeframe. For example, you could select a few motion sensors
and configure Device Activity Check to notify you if they have not generated activity within the last
so many hours as a way to tell if they may have stopped working due to network issues, battery health,
or other problems.

You will need to have a good feel for how long you are willing to wait before considering
any particular device to be of concern. For example, many door/window and motion sensors also report temperature
data and will often send reports to the hub at least every few hours, if not multiple times per hour, even if there
is no open/close or motion activity. On the other hand, many switches/dimmers and plugs will not send any
reports unless they are phyiscally or digitally used or refreshed. You can configure different intervals for different devices.

In most cases, "activity" will generate an event for the device (check the "Events" tab on the device page inthe admin UI),
but what Device Activity Check really looks at is the "Last Activity At" data you can find under "Device Details" on the
device page. Devices must report "Last Activity At" to be usable with Device Activity Check.


For more discussion see, the <a  href="https://community.hubitat.com/TODO">thread in the Hubitat Community</a>.

## Installation Instructions

1. Add the app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/DeviceActivityCheck/DeviceActivityCheck1.groovy

2. Create a new instance of the app under "Apps" > "Add User App." Choose "Device Activity Check," and follow the instructions.

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps">the
first five steps of the Hubitat support document on custom app installation</a> and consider whether using custom code
is a good choice for you.