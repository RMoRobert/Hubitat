# Lights on Motion Plus

## Description
Lights on Motion Plus 5 (LoMP 5) is a complete rewrite that seeks to provide features available in similar motion-ligthiing apps,
such as:
* choose motion sensor(s) to turn on lights
* choose motion sensor(s) to keep on lights (but not turn on if not already on)
* specify delay after motion inactivity before lights turn off
* restrict turning on or off based on mode or switch
* choose additional lights to turn off (that are not part of the "on" half of the automation)

...while adding a number of new (optional) features, including:
* dim lights to "warn" before turning off (or new in v5, dim instead of turning off)
* restrict or modify automation ("on" or "off" actionns or both) based on current mode
* adjust lights on mode change

## Suggested Use
One possible use case: consdier a room with multiple bulbs. You do not want the app to (necessarily) turn on all
bulbs, but you do want the app to turn off all the bulbs and turn these specific bulbs (but not ones that were off)
on again the next time you enter the room. LoMP will capture and restore individual bulb states.

Another possible use case: consider a room where you would like the room to turn on to different settings
(e.g., color temperatures) in different modes. LoMP can do this, and it can also optionally change to
the new mode settings if the lights are on when the mode changes.

Another possibility: consider a room where you always (or at least in some modes) want to turn the lights
on manually, but you want them to turn off automatically "just in case." LoMP 5 can be configured to only turn
lights off without turning them on (either all the time or only in specific modes).

## To Install

Install the parent app code: https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/LightsOnMotionPlus/LightsOnMotionPlusParent.groovy

Install the child app code: https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/LightsOnMotionPlus/LightsOnMotionPlus5.groovy

(Note: the old 4.x child can be found in the `deprecated` folder if you prefer.)

Create a new instance of the app by going to Apps > Add User App, and choosing "Lights on Motion Plus."

### Note for v4 users:
IMPORTANT: If upgrading from v4 or earlier version, do not replace the child app code with v5. Instead,
upgrade the parent app to the new/current version, then add the v5 child app as a new app. Keep the
version 4 child app installed as long as you have v4 app instances in use.
(There is no direct upgrade path from v4 to v5 child apps, as v5 is a complete
rewrite, but you can continue to use both child app versions at the same time indefinitely.) Again,
DO upgrade the parent app; do NOT upgrade the child app (add as new).

### Note for v5.x users upgrading to 5.5 from 5.4.x or newer:
Open all 5.x child apps and hit "Done" if using the former "Switch(es) to disable turning on lights" or
"Switch(es) to disable turning off (or dimming) lights" to update them to the new "Disable turning on lights when..."
or "Disable turning off (or dimming) lights when..." options.

## Support
See Hubitat Community thread for more details or support: https://community.hubitat.com/t/release-lights-on-motion-plus-dim-before-off-remember-individual-bulb-states-etc/7178 
