# One Switch at a Time

This app allows you to select multiple switches and effectively ensure only one switch is on at a time: when one of the selected switches is turned on, the others are sent an "Off" command.

## Installation Instructions

1. Add the parent app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/OneSwitchAtATime/OneSwitchAtATime-Parent.groovy


2. Add the child app code to "Apps Code" in the Hubitat admin UI:
https://raw.githubusercontent.com/RMoRobert/Hubitat/master/apps/OneSwitchAtATime/OneSwitchAtATime-1-Child.groovy

2. Create a new instance of the app under **Apps > Add User App**. Select **One Switch at a Time**, and follow the prompts.

If you do not know how to add custom app code to Hubitat, consult <a  href="https://docs2.hubitat.com/en/how-to/install-custom-apps">the
first five steps of the Hubitat support document on custom app installation</a> and consider whether using custom code
is a good choice for you.