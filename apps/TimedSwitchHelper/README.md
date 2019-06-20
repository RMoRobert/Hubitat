# Timed Switch Helper

This app is designed to be used to tie together a "real" switch and a virtual switch that you intend to use as a timer on the "real" switch. This app will watch for the virtual switch to beturned on, then turn off the real (and virtual) switch after the configured time.

 ## Example
If you have a switch called "Window Fan" and create a virtual switch called "Timed Window Fan," this app can turn on Window Fan ("real" switch) when Timed Window Fan ("timed/virtual" switch) turns on, then turn off the "real" device after the time configured in this app has expired *or* if the timed/virtual switch is turned off manually in the meantime. (You can also create a timed virtual switch with longer durations than the built-in virtual switch driver allows by simply selecting the same virtual switch as both the "real" and "timed/virtual" switch in the app.)
