# Alerts

Spec for the alert redesign (`feature/alert-redesign`). It replaces the single alert slot in the
persistent scanning notification with separate notifications per sensor and class, three alert
levels, snoozing and spoken alerts.

## Terms

- **Class**: what an alert is about: pressure, temperature, battery, pressure loss or sensor
  alarm (see [Classes and levels](#classes-and-levels)).
- **Level**: how urgent an alert is: amber, red or crimson.
- **Reading**: one stored sensor record. Sensors send each reading several times in a burst, and
  `ListenTyreWithDatabaseUseCase` stores only the first copy, so a burst counts as one reading.
- **Qualifying reading**: a reading whose value puts its class at some alert level.

## Levels

| Level | Meaning | Notification channel |
|---|---|---|
| Amber (warning) | Doesn't need immediate action, can be addressed when convenient | Its own channel |
| Red (alert) | Riding can go on with caution, address at the earliest possibility | Its own channel |
| Crimson (critical) | Must be fixed before riding on | Its own channel |

Each level has its own notification channel, so the user can set its sound, vibration and Do Not
Disturb behaviour separately. The existing alert channels (`MONITOR_SERVICE_FOR_ALERT`,
`MONITOR_SERVICE_FOR_LOW_BATTERY`, `MONITOR_SERVICE_FOR_PRESSURE_LOSS`) are deleted.

## Classes and levels

Pressures are compared after the vehicle's calibration, as today. Temperature margins are in
absolute degrees Celsius, whatever the display unit. All margins are fixed defaults for now, not
settings.

**A value exactly on a boundary belongs to the more severe side**, for every class: a tyre
exactly at its minimum pressure is red, a battery exactly at its low voltage alarm is red.

| Class | Amber | Red | Crimson |
|---|---|---|---|
| Pressure, low side | At or below 3% above the minimum | At or below the minimum | At or below 75% of the minimum |
| Pressure, high side | At or above 3% below the maximum | At or above the maximum | At or above the maximum + 20% |
| Temperature | At or above the hot threshold − 10 °C | At or above the hot threshold | At or above the hot threshold + 20 °C |
| Battery | At or below the low voltage alarm + 0.1 V (today's `LOW_SOON`) | At or below the low voltage alarm, **staying so 10 minutes** | — |

The low voltage alarm is set for 20°C and comes down 5 mV per degree below it, by 0.1 V at most (`Voltage.alarmAt`). Sysgration sensors report a percentage instead: amber at or below the low battery alarm + 10 points, red at or below it (10 % by default), not adjusted to the temperature.

Each column applies when the more severe one doesn't.
| Pressure loss | A leak detected by the pressure loss tracker | — | — |
| Sensor alarm (Sysgration's own alarm) | Raised | — | — |

- **Every level notifies from the first qualifying reading**, except battery red. A value
  hovering on a boundary is handled by the [hold](#holding-a-notification-at-its-level) instead.
- **Battery red takes two readings at red at least 10 minutes apart**, amber meanwhile: the
  voltage dips in the cold and while the sensor transmits, and recovers afterwards. A time rather
  than a count of readings, since a sensor can report once an hour.
- **Battery reaches red** because a sensor below its low voltage may stop transmitting entirely,
  leaving that tyre unmonitored with no way to alert about it later.
- **The thresholds change from today's behaviour** where they didn't already trigger at the
  boundary:
  - Pressure: today a tyre exactly at its minimum or maximum is in range. It becomes red. The
    pressure settings text ("Below the minimum or above the maximum, it blinks red") changes to
    match.
  - Temperature: the background and stats checks alert strictly above the hot threshold today, the
    tyre icon at it. All of them alert at it, as the temperature settings text already says ("From
    the hot temperature on").
  - Battery is unchanged: the default stays 2.6 V, and red comes at 2.6 V.
- **Critical low pressure is 25% below the minimum**, the point where FMVSS 138 has a car's own
  tyre pressure warning light turn on (25% below the recommended cold pressure): a tyre run that
  low heats up and flexes enough to be damaged. The app has no recommended pressure, only the
  user's minimum, so the pressure settings suggest setting the minimum to the recommended pressure,
  which makes the two match. A minimum set lower makes critical a little more lenient than the
  standard. The standard only covers cars and light trucks, a motorcycle handles worse with less.
- **The maximum pressure is the tyre's maximum inflation pressure**, the one marked on its
  sidewall. The pressure settings suggest setting it so. Red above it, crimson above it + 20%: past
  what the tyre is rated for, not something a tyre warming up reaches. An overfill mistake (a pump
  set to the wrong unit) or a fault.
- **The default maximum becomes 50 psi (345 kPa)** for new vehicles, at the high end of what
  tyres are rated for, against 300 kPa today. Existing vehicles keep theirs. The sidewall maximum
  is a cold pressure: a tyre inflated right up to it can go above it when hot, but the recommended
  pressures are well below it, so a tyre warming up from its recommended pressure stays under it.
  SQLite can't change a column's default without rebuilding the table, so the new default is set
  where vehicles are created rather than in the schema.
- **Pressure loss is a class of its own**: a leak is a different kind of problem from a pressure
  out of range. A leaking tyre reaching its minimum gets a separate red pressure notification.
  The tracker is experimental and off by default, this class only exists while it's on.
- **Sensor alarm stays amber**: the sensor still reports pressure and temperature, which raise
  their own alerts. It's still shown as "Leaking?" in the app.
- Fast leaks don't escalate to crimson. That's left out on purpose, to avoid false positives.

### No sensor removed detection

A sensor taken off the valve to pump the tyre up reads the open air, which is crimson pressure.
Telling that apart from a tyre that burst was tried (a reading near zero right after a normal one
was taken for a removal and only raised an amber "Sensor removed?" alert), and dropped: a burst
between two readings looks the same, and missing it costs far more than a crimson alert while
pumping up, which the silence button or a dismiss quiets.

## Notifications

### One notification per sensor and class

- Every alert is a notification of its own, keyed by **vehicle, sensor and class**. A tyre with a
  pressure alert that then also gets hot shows a second notification, so the new alert is seen as
  new rather than as an update of the first.
- **Except a leak while the pressure is red or crimson**: a pressure loss or the sensor's own
  alarm isn't notified on its own then, its notification going at once if it was shown. It's told
  in the pressure's notification instead ("Rear left wheel: 21.0 psi, minimum 30.0 psi, down
  3.0 psi in 5 min"), and the main screen still shows "Leaking?". The pressure already says more
  than the leak's amber: another notification would only be one more sound and one more thing to
  dismiss. While the pressure is fine or amber, a leak is the earliest warning, notified on its
  own.
- Alert notifications are separate from the persistent scanning notification, which only shows the
  scanning status (active, suspended, idle) on its low importance channel.
- They're posted whatever the app's state, including while the main screen is open: the user may
  not be looking at it.
- Each notification names the vehicle (as its subtext) and the tyre position, and gives the
  reading and the threshold it crossed. The title is the condition alone ("Pressure critically
  low"), short enough not to be cut off beside the vehicle's name, the tyre position starting the
  text ("Rear left wheel: 5.8 psi, minimum 14.5 psi").
- `setWhen` is the reading's own timestamp, so a notification left over from earlier looks its
  age.
- Tapping it opens the app on its vehicle, as today.

### When a notification sounds

| Event | Effect |
|---|---|
| A class goes to a higher level | Notification updated on the new level's channel, **sounds** |
| A new red or crimson qualifying reading at the same level | **Sounds again**, at every reading, with no minimum gap |
| A new amber qualifying reading at the same level | Silent update |
| A class goes down to a lower level, still alerting | Silent update on the lower level's channel, once the [hold](#holding-a-notification-at-its-level) ends |
| A reading below every level | Notification **cancelled**, once the hold ends |

Re-sounding on every red or crimson reading is intended: a tyre losing pressure fast enough to
transmit every few seconds is worth hearing about that often. Bursts are already collapsed into
one reading, so a single reading sounds once.

### Holding a notification at its level

Every reading which posts a notification holds it at its level for **3 minutes**. A reading which
would lower or clear it meanwhile is queued, the latest one replacing the previous: it only goes
through once the hold ends, unless a reading at that level or above comes first, which discards it
and starts the hold over.

While riding, the sensors report about once a minute, in steps of 0.4 to 0.5 psi: a value
hovering on a boundary would otherwise clear and post its notification again, with a sound, every
minute or two. A sensor reporting once an hour isn't affected, its lowering going through right
away. The hold is kept in memory: after the process died, a notification keeps its level until its
next reading.

The hold only concerns the notification. The speech follows each reading right away: a reading
lowering a crimson alert stops its loop at once, and a red one is said as red.

### Changing a threshold

Once a vehicle's thresholds settle for a second, each tyre's latest reading is evaluated again. A
notification it showed which that lowers or clears is updated at once, silently, its hold and any
reading held back dropped: the threshold was changed on purpose, it isn't hovering. The speech
follows, a crimson loop stops, said as red if it's red now. A notification at the same level is
updated silently, telling the new threshold. Nothing is posted nor raised: a reading made worse by
the new thresholds alerts with the tyre's next reading.

### Only new readings alert

The evaluator only alerts on readings stored **after it started listening**. The latest stored
reading, replayed when listening starts, never alerts. Background scanning becoming active doesn't
re-raise alerts from readings taken days ago.

What this costs:

- A tyre left below its minimum at the end of a ride doesn't alert at the next ride's start, only
  at its first reading during it. That's usually within minutes: a tyre starting to move or warm
  up transmits.
- Changing a threshold doesn't alert about readings already stored. The main screen shows the new
  level at once. A notification already shown is brought down to it, see
  [Changing a threshold](#changing-a-threshold).
- Readings stored while nothing was evaluating them never alert.
- Moving sensors between tyres moves their readings along, and a tyre whose sensor changes starts
  its history over from them: a moved reading never alerts at its new tyre, even if it's newer than
  the readings that tyre had.
- Force-stopping the app clears its notifications. An ongoing alert then stays silent until the
  next reading. The process being killed also forgets the loops, the same way.

## Snoozing

### Actions

| Level | Actions |
|---|---|
| Amber | "Dismiss 1 day", "Dismiss 1 week" |
| Red, crimson | "Dismiss 10 min", "Dismiss 1 day" |

Swiping a notification away counts as its **shorter** period. "Clear all" does the same to every
alert notification.

### Rules

- **Key: sensor and class.** Binding a replacement sensor escapes the snooze: the cause of the
  alert has most likely been dealt with at the same time.
- A snooze on a level silences that class's readings **at that level or lower** for that sensor,
  until it ends. A **higher** level gets through, and notifies.
- Snoozing a level also snoozes **the lower levels** of the same sensor and class for the same
  period, unless they're already snoozed until later.
- **A snooze survives the condition clearing.** A reading going back and forth across a threshold
  doesn't get around it.
- **A snooze ending doesn't alert by itself.** Only the next qualifying reading does.
- **Snoozes are stored on disk**: the service gets killed and restarted, and an in-memory snooze
  would re-raise everything at each restart.
- A snooze only silences notifications and speech. The main screen keeps showing every reading's
  level.

## Spoken alerts

| Level | What's said | When | Audio usage |
|---|---|---|---|
| Amber | Nothing | — | — |
| Red | The class twice: "TYRE PRESSURE", "TYRE HOT", "SENSOR BATTERY" | Each time its notification sounds, and every 10 min, see below | `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` |
| Crimson | "TYRE PRESSURE CRITICAL" or "TYRE HOT CRITICAL", twice | Every 20 s, then every 10 min, see below | `USAGE_ALARM` |

- The phrases stay this short on purpose: they say what to look for, the screen says the rest.
  They don't name the tyre position.
- Navigation guidance reaches helmet intercoms and Android Auto like navigation prompts do, and
  ducks the music. The alarm usage plays even with the media volume muted, and Android plays it
  through the phone's speaker as well as a connected headset.
- **A phrase that started is always said to the end**, whatever happens meanwhile: the alert
  going up, down or being dismissed, another alert coming. Only turning spoken alerts off cuts it.
- **One speech queue, two loops for the whole app**: the crimson loop and the reminders. Each says
  all its alerts in one phrase ("TYRE PRESSURE CRITICAL, TYRE HOT CRITICAL"), whatever tyres or
  vehicles they come from, and red announcements wait their turn instead of talking over them.
  Red announcements of the same class waiting together are said once.
- Speech waits for a notification's sound to play out rather than speaking over it: Android plays
  it about half a second after it's posted, so the speech waits up to 1.5 s for one to start, then
  until no notification sound plays (10 s at most). A silent or vibrating channel doesn't delay
  it. It takes the audio focus (ducking what plays) only while it speaks.
- **"Alert sound"**, in a new "Alerts" group of the app settings, chooses between:
  - **Speech**, the default: the phrases above.
  - **Tones**: a tone pattern in place of each phrase, with the same queue, loops, silence and
    stop rules. Shaped after the medical alarms (IEC 60601-1-8), which people already read as
    "attention" against "urgent": 3 pulses at 523 Hz twice for red (navigation guidance audio), a
    burst of 3 and 2 pulses at 880 Hz twice for crimson (alarm audio). Generated in the app, a tone
    with a few harmonics carrying through road noise and a helmet better than a pure one. They
    also work whatever the phone's language, the phrases being English only.
  - **None**: only the notifications sound. The silence button is then hidden.

  Upgrading keeps the previous choice: the former "Spoken alerts" switch off becomes None.

### Silencing the speech

The main screen shows a big button over the vehicle, between its axles or in the middle of the
screen for a single axle trailer, while the speech has a red or critical alert left to say: waiting
for its announcement, or repeated by the loops. A notification still shown isn't enough: once the
loops dropped its alert (silenced, scanning stopped...), only a new reading would speak again,
there's nothing to silence. It's there to be hit
while riding: finding a notification's buttons in the drawer is too much then. Reaching the screen
is left to the usual ways (the notification, the launcher): opening it by itself would take over
the navigation.

| Alerts to say | Button | Silences for 10 minutes |
|---|---|---|
| Red only | "Silence alerts for 10 min" | Every red alert's speech, including new ones. A new critical alert is still said, and the button turns into the critical one |
| A critical one | "Silence critical alerts for 10 min" | All the speech, new critical alerts included |

- Only the speech: notifications still post and sound as usual, the dismiss buttons are unchanged.
- The silenced alerts leave the loops, a phrase being said is said to the end. Once the silence
  ends, only a new qualifying reading speaks again.
- While silenced, the button tells the time left and unmutes on a tap.
- Stored on disk, as the snoozes are.
- Android Auto shows it as an "Alert speech" grid item next to the tyres, always there while the
  alerts are spoken: the host takes a change in the items for a new screen, which it only allows
  a few times in a row, while a change in their text is a refresh.

### Crimson loop

- Says the crimson alerts twice every 20 s, counted from the start of each phrase. A new one is
  said right away, then joins the loop.
- An alert stays in it for **10 minutes**. Each new qualifying reading of the alert restarts the
  10 minutes. Then it moves to the reminders.

### Reminders

- Say the red alerts, and the crimson alerts done with their loop, twice every **10 minutes**
  ("TYRE PRESSURE CRITICAL, SENSOR BATTERY"), counted from the start of each phrase. They use the
  alarm usage when a crimson alert is among them.
- A red alert is left out while a crimson alert of the same class is said, in either loop: the
  phrases don't name the tyre, "TYRE PRESSURE" would only repeat "TYRE PRESSURE CRITICAL".
- The 10 minutes start when the first alert enters the reminders, and start over when a red
  announcement said everything they would say. With a single red alert, it's said 10 minutes
  after its latest announcement. Red announcements come at every red reading, so the reminders
  mostly matter when the readings are further apart: sensors reporting rarely, or a parked
  vehicle while scanning goes on.
- They have no end of their own: "Dismiss 1 day" is the way to silence an alert that's known
  about.

### When the loops stop

- An alert leaves both loops when it's snoozed or dismissed, or when a reading lowers it to amber
  or clears it. A reading lowering a crimson alert to red moves it to the reminders, announced as
  red.
- Every alert leaves them when scanning is suspended or stopped: no reading could clear them any
  more. After scanning resumes, only a new qualifying reading starts them again.
- **Both loops only go on while monitoring is meant to**:
  - while the app is open: its screen scans, and whoever looks at it is watching the tyres;
  - in the background, with persistent scanning, while it's active (not idle or suspended);
  - in the background, without it, while monitoring from the app's button. Leaving the app any
    other way (home, switching apps) ends them, even though it may still be scanning until closed.

  Once that ends, every alert leaves them, as when scanning stops. Meanwhile, a new crimson reading
  is still said, twice, once, as a red one is.

## In the app

- **One evaluator** decides every tyre's level, for the notifications, the speech, the main screen
  and Android Auto. Today `TyreIconStateFlow`, `TyreStatsStateFlow` and `VehicleAlertUseCase` each
  have their own rules, and already disagree (`>` vs `≥` on the temperature).
- Every reading updates the main screen, whether or not it notifies.
- A new **"Alerts" group in the app settings** holds the spoken alerts switch.

### Visuals

Red and crimson are told apart by blink speed and a label rather than by colour alone, since the
two look alike in sunlight and to colour-blind users. The tyre icon follows the tyre's own
condition, its pressure and temperature: a low battery blinks its voltage, not the tyre.

| Level | Tyre icon | Tyre stats |
|---|---|---|
| Amber | Unchanged (no blinking) | The offending value in the theme's warning orange, as the battery's "getting low" is today |
| Red | Blinks red, in 400 ms phases | The offending value blinks red, in 400 ms phases |
| Crimson | Blinks red, in 200 ms phases | The offending value in red, alternating with "CRITICAL" in 800 ms phases |

The tyre blinks in 300 ms phases today. Halving that would make about 3.3 flashes a second, above
the usual photosensitivity limit of 3 (WCAG): red slows down to 400 ms instead, and crimson stays
under the limit at 2.5 flashes a second.

Everything blinking on every screen, tyres, values and the battery settings' samples, follows one
clock: each phase starts on a multiple of its length since the epoch, so the 200, 400 and 800 ms
phases switch together and two tyres at the same level are never out of step.

The normal tyre colour already fades from green to red as the temperature nears the hot threshold,
so a tyre at amber temperature is already reddish: the orange value in the stats is what marks it
as amber. Android Auto follows the same levels with its own icons.

## Architecture

- **The evaluator runs at app scope**, not in `MonitorService`, so alerts work the same while the
  app is open without background monitoring.
- **It observes the stored readings rather than the scan.** Subscribing to the tyre flows is what
  starts the BLE scan (they're shared `WhileSubscribed`), so an app-scope subscriber would scan
  forever. Every reading, from the main screen's scan or the service's, is stored by
  `ListenTyreWithDatabaseUseCase`. Watching the stored rows:
  - never starts a scan of its own: it sees whatever is scanning,
  - makes "readings stored after it started listening" the definition of a new reading,
  - gets bursts already collapsed,
  - matches `TyrePressureLossStateFlow`, which already rebuilds from stored readings.
- It covers every vehicle, not only the current one, as the service does today.
- Starting the service while the app is open was considered and rejected: in non-persistent mode it
  would show the monitoring notification and turn the bell and the manual button to "running" just
  because the app is open.
- The notification ids are stable per vehicle, sensor and class.
- `ServiceNotifier` keeps only the scanning status. A scan failure moves off the alert channel, onto
  the scanning status notification.

## Out of scope

- **Non-persistent ("legacy") mode as a special case of persistent mode**: activate conditions set
  to always, suspended on phone idle only unless turned off. It's a scan policy change, alerts
  already behave the same in both modes. Later, on its own branch.
- **Scan failure recovery.** A scan starting while Bluetooth is off fails with
  `Failure.ScannerIsNull`, and the service's pipeline ends for good on any failure: it says "TPMS
  Advanced must be restarted" and doesn't resume when Bluetooth comes back. Bluetooth being off
  should be a scanning status that resumes on its own, and transient `onScanFailed` errors
  retried. On its own branch.
- **Full-screen intent for crimson alerts.** Later. Since Android 14, Play only allows
  `USE_FULL_SCREEN_INTENT` for calling and alarm apps, so it may not be possible.
- **`FLAG_INSISTENT`**: repeating sounds are handled by the speech loops instead.
- **Alerting about a sensor going silent**: tyres on the non-driven axle can stay silent for days,
  even during rides.
- **Crimson for fast leaks**: left out to avoid false positives.

## Open questions

None at the moment.
