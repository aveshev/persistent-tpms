[![Develop](https://github.com/aveshev/persistent-tpms/actions/workflows/develop.yml/badge.svg)](https://github.com/aveshev/persistent-tpms/actions/workflows/develop.yml)

# Persistent TPMS

> A fork of [TPMS Advanced](https://github.com/VincentMasselis/TPMS-advanced) by Vincent Masselis, released under the [Apache License 2.0](LICENSE), with features that won't necessarily make it into the main TPMS Advanced. Formerly TPMS-advanced-NE (Nerd Edition). [Privacy policy](https://aveshev.com/persistent-tpms/privacy/)

Android app for Bluetooth Low Energy TPMS sensors made by the manufacturers Sysgration and Pecham 

<img src="https://user-images.githubusercontent.com/6769250/192485450-354d941b-47e7-4078-bede-5c28ace85b30.png" width="200"> <img src="https://user-images.githubusercontent.com/6769250/192485472-8c2c60fd-da54-4703-99db-3113e1339676.png" width="200"> <img src="https://user-images.githubusercontent.com/6769250/192485477-24ef7b35-8b37-4c40-b98e-e7025980d3f6.png" width="200"> <img src="https://user-images.githubusercontent.com/6769250/192485485-493ee137-6d88-43fc-b6fa-e50a31c96696.png" width="200">

The original TPMS Advanced is available on the [Play Store](https://play.google.com/store/apps/details?id=com.masselis.tpmsadvanced)

I will put a compiled release of the NE edition here once it's deemed polished enough.

## What's new in the Nerd Edition

### Persistent scanning

An optional always-on mode for background monitoring. Instead of starting it by hand before
every ride, a foreground service keeps running, comes back after a reboot or an app update, and
decides by itself when to actually listen to the sensors, so battery is only spent when it is
useful.

* **Activate scan conditions**: charging with a cable, charging wirelessly, Android Auto
  connected. Optionally **stay active** for 1 to 30 minutes after the last one ends. Turning
  the conditions off means always scanning.
* **Suspend scan conditions**: they override the activate conditions. The phone being idle
  (screen off, not charging, no movement: Deep Doze), or connected to any WiFi, except the
  ones you list (car hotspot, garage WiFi, etc.)
* A **status bell** replaces the Start/Stop button: green when scanning, orange when suspended,
  neutral when idle, red when a permission is missing. Tap it to see why.
* Scanning while the app is open is never affected. With persistent scanning off, the manual
  Start/Stop button works as before.

Reading the WiFi name in the background needs location access "Allow all the time"; the app
walks you through it only if you use WiFi exceptions.

### Everything else

* Separate front and rear pressure targets
* Pressure and temperature thresholds typed in rather than set with sliders
* Sensor ID and time since the last update shown on each tyre, with an explanation of why some
  sensors stay silent for a long time
* Only the value that triggered an alert blinks
* Background monitoring covers all vehicles at once
* App settings styled like the Android settings app, each option explained on its own page
* Unrestricted battery usage requested before background monitoring starts, so the system
  doesn't kill it

## Features

* Pressures alerts
* Temperature alerts
* Support one or multiple cars
* QR Code scanning
* Dark mode
* Pressure and temperature units selection
* Show the latest recorded value even after a restart
* Filter with favourites sensors
* Supports cars, motorcycles, trailers and 3 wheelers
* Run in background
* Shortcut to access directly to the vehicle

## What's next ?

* Battery alerts
* Last time update
* Temperature history
* Automatic startup in background when connected to the car's radio in bluetooth
* Add support for other BLE Sensors

## Which sensors are compatibles ?

You can buy them on Ali*xpress, it looks like this:

<img width="95" alt="Capture d’écran 2024-01-16 à 16 18 59" src="https://user-images.githubusercontent.com/6769250/192489323-00d1f481-635e-459b-9a43-f2ff75299fa5.png"> or <img width="95" alt="Capture d’écran 2024-01-16 à 16 18 59" src="https://github.com/VincentMasselis/TPMS-advanced/assets/6769250/7534982b-5a44-435a-a489-0877d61adc97"> or <img width="94" alt="Capture d’écran 2026-09-04 à 17 09 05" src="https://github.com/user-attachments/assets/d00ce071-7987-454a-9a66-caaa6551a679" />




## Contributing

Take a look at the [contributing file](CONTRIBUTING.md).
