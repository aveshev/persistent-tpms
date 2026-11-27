---
title: Persistent TPMS – Sensor models
permalink: /sensors/
---

# Sensor models

_Last updated: 27 November 2026 · [Русский](../ru/sensors/)_

Sensors from different manufacturers differ in how often they send an update, in the steps they
read the pressure in, and in whether they can tell that the wheel is rolling. What follows was
measured on real sensors with Persistent TPMS. Something different on yours? Tell us in an
[issue](https://github.com/aveshev/persistent-tpms/issues).

## WiCarLink, older sensors

- **Updates:** every 5.5 minutes, whether the vehicle moves or not, and at once whenever the
  pressure changes significantly.
- **Pressure steps:** 0.1 bar (about 1.5 psi). A tyre between two steps can read one step lower
  when cold, and the next one up once warm.
- **Wheel rotation:** not detected.

## WiCarLink, newer sensors

- **Updates:** every 10 minutes while the vehicle stands still. As soon as the wheel starts turning
  it sends an update at once, then about every 2 minutes: for at least 8 minutes, and for as long as
  the ride goes on. Back to every 10 minutes a while after it stops. Also at once when inflated,
  when taken off the valve, and when the pressure falls by about 2.7 psi (0.19 bar).
- **Pressure steps:** about 0.45 psi (3.1 kPa). A steady tyre can go back and forth by one step as
  its temperature changes.
- **Wheel rotation:** detected when the wheel starts turning.
- **Telling them from the older ones:** the pressure moves in small steps of about 0.45 psi rather
  than 1.5 psi, and updates come every 2 minutes while riding.

## Pecham

- **Updates:** every minute while the wheel rotates. While stationary, only when the pressure is
  below about 22 psi. In either case, whenever the pressure changes significantly.
- **Pressure steps:** about 0.45 psi (3.1 kPa), shown rounded to 0.1 psi: a steady tyre can go
  back and forth by one step.
- **Wheel rotation:** detected.

## TC.TPMS

- **Updates:** every 5 minutes, whether the vehicle moves or not, and at once when the pressure
  changes by about 1.5 psi.
- **Pressure steps:** 0.1 psi.
- **Wheel rotation:** not detected.

[Back to Persistent TPMS](../)
