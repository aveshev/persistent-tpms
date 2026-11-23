---
title: Persistent TPMS – Privacy policy
permalink: /privacy/
---

# Persistent TPMS – Privacy policy

_Last updated: 7 October 2026_

Persistent TPMS is an Android app that reads Bluetooth tyre pressure sensors. It is developed by
Alexander Veshev as an open-source fork of
[TPMS Advanced](https://github.com/VincentMasselis/TPMS-advanced) by Vincent Masselis. This policy
explains what data the app handles and where it goes.

## Summary

The app has no account, no advertising and no analytics. Everything it records stays on your
device. The developer does not receive or store any of your data.

## Data the app stores on your device

- **Vehicles and sensors**: the vehicles you create, the sensors you bind to their tyres, and your
  alert thresholds and settings, including any Wi-Fi network names you add as exceptions.
- **Tyre readings**: the Bluetooth advertisements received from your sensors (pressure,
  temperature, battery and alarm), with the time, the sensor ID and the signal strength.

You can delete this data at any time by deleting vehicles in the app, clearing the app's data in
Android's settings, or uninstalling the app.

## Permissions and why they are used

- **Nearby devices / Bluetooth**: to receive the signals broadcast by your tyre sensors.
- **Location**: Android requires the location permission to scan for Bluetooth devices on older
  versions, and to read the name of the Wi-Fi network you are connected to. On Android 10 and 11,
  the background monitor's notification keeps that permission in effect while the app is closed,
  so that the scan keeps receiving the sensors. The app does not record or use your position.
- **Background location**: lets the background monitor read the connected Wi-Fi network's name
  while the app is closed. Monitoring pauses while the phone is on Wi-Fi, except on the networks
  you list as exceptions (such as a car's hotspot). The names you add to that list are stored on
  the device.
- **Physical activity**: lets the background monitor tell when the phone has been left somewhere
  rather than carried in a vehicle, so it can pause scanning. Activity results are only used on
  the device.
- **Camera**: to scan the QR codes that come with some sensors. Images are processed on the device
  and are not stored.
- **Notifications**: to show tyre alerts and the background monitor's status.

## Data shared with third parties

The app does not send your data to the developer or to any third party. However:

- **Android backup**: if Android's backup is turned on, Android may include the app's data in
  your device backup in your Google account. This is controlled by your device settings and
  governed by [Google's privacy policy](https://policies.google.com/privacy).
- **QR code scanning** uses Google's ML Kit through Google Play services. Images are analysed on
  the device, but Google Play services may send Google diagnostic and usage information about the
  feature, as described in [ML Kit's terms](https://developers.google.com/ml-kit/terms).

## Children

The app is not directed at children and does not knowingly collect data from anyone.

## Changes to this policy

If the app's data handling changes, this page will be updated and the date above changed.

## Contact

Questions about this policy can be asked by opening an issue on the app's
[GitHub repository](https://github.com/aveshev/persistent-tpms/issues).
