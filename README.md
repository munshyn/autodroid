# Autodroid

A minimal home screen for your Android Auto, with prayer times and synced lyrics.

Autodroid fills the car screen with the things you glance at while driving: the time and date (with the
Hijri date), how long until the next prayer, and the song playing in YT Music with its lyrics moving along
line by line. At prayer time it plays the azan and pauses your music, then picks the music up again
afterwards. Everything runs on free services; no accounts or paid APIs.

**Download:** [Autodroid 1.1.0 APK](releases/Autodroid-1.1.0.apk) (see [Installing](#installing), a plain
install won't show up in Android Auto).

## What's on the car screen

- **Clock** with day, date and Hijri date, 12- or 24-hour, optional greeting.
- **Next prayer** with a countdown and all five times for the day, from JAKIM e-solat for your zone. The
  zone is found from GPS (or picked by hand), and times are saved on the phone so they work with no signal.
- **Azan:** three chimes, the music pauses, the azan plays, and the music resumes a second after it ends.
  Tap the prayer card to stop early. "Chimes and pause only" or "do nothing" are also available.
- **Music** from YT Music: album art, title, progress, previous / play-pause / next.
- **Synced lyrics** from [Lyrica](https://github.com/Wilooper/Lyrica): the previous line, the current line
  large, and the next lines coming up.

Autodroid registers as a navigation app, which gives it the whole car screen. It has no map: switch to
Google Maps or Waze from the Android Auto taskbar for directions, and Autodroid keeps running (the azan still
plays).

## Settings on the phone

Open Autodroid on the phone to change things; the car screen updates straight away.

- Music access and location permissions
- Prayer zone (GPS or chosen), "azan soon" warning, what happens at prayer time, azan and Subuh audio,
  azan volume, resume music, test
- 24-hour clock, Hijri date, greeting, English or Bahasa Melayu
- Lyrics on / current line only / off, lyrics size, lyrics timing
- Car screen look: font style (Cluster, Rounded, Classic), text size, background (Graphite, Black, Navy,
  Forest), six accent colours, clock style, with a live preview of the car screen

Prayer times and zone names are for Malaysia (JAKIM zones).

## Installing

Android Auto only lists Car App Library apps that it believes came from the Play Store. Install the APK
through the phone's own installer with the Play Store recorded as installer:

1. Turn on **Wireless debugging** on the phone and connect with `adb connect <ip>:<port>`.
2. Copy the APK to the phone and open it with the installer, marked as from the Play Store. The script
   [install-to-phone.ps1](install-to-phone.ps1) does this (it builds first, so it needs the signing setup
   below):

   ```powershell
   .\install-to-phone.ps1 -Phone <ip>:<port>
   ```

3. Tap **Update** / **Install** on the phone.
4. In Android Auto settings › **Customise launcher**, tick Autodroid.

Allow **Music access** and **Location** in Autodroid on the phone. More detail and troubleshooting:
[CAR_SETUP.md](CAR_SETUP.md).

## Building

- JDK 17 (path set in `gradle.properties`), Android SDK 35.
- **Azan audio is not in this repo.** Put an MP3 of your choice at
  `app/src/main/res/raw/azan_default.mp3` before building; it's the built-in azan.
- Debug build: `gradlew.bat assembleDebug`
- Release build: create your own `autodroid-release.jks` and a `keystore.properties` next to it with
  `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, then `gradlew.bat assembleRelease`.

To try the car screen on a PC, use Android Studio's **Desktop Head Unit** with Android Auto's developer
mode and "Start head unit server".

## Sources

- Prayer times: [JAKIM e-solat](https://www.e-solat.gov.my), backup [waktusolat.app](https://waktusolat.app)
- Zones from GPS: waktusolat.app
- Lyrics: [Lyrica](https://github.com/Wilooper/Lyrica) (open source; searches LRCLIB, NetEase and others)
- Fonts: [Barlow](https://github.com/jpt/barlow) and [Outfit](https://github.com/Outfitio/Outfit-Fonts), SIL Open Font License

## Project layout

```
app/src/main/java/com/husny/autodroid/
  MainActivity.kt            phone settings screen
  car/AutodroidCarAppService entry point for Android Auto
  car/AutodroidSession       one car-screen session
  car/HomeScreen             NavigationTemplate (full-screen drawing surface + 12h/24h button)
  car/DashboardModel         prayer times, music and lyrics for one car session
  car/SurfaceRenderer        draws the Compose UI onto the car display
  data/PrayerTimes           JAKIM prayer times + offline cache
  data/Zone                  prayer zone from GPS
  data/NowPlaying            YT Music now-playing and controls (media sessions)
  data/Lyrics                Lyrica synced lyrics
  ui/CarDashboard            the car screen (Cluster layout)
  ui/CarLook                 fonts, backgrounds and sizes for the car screen
  ui/PhoneSettings           the phone settings screen
  settings/Settings          settings shared live between phone and car
  azan/Azan                  chimes, pause, azan, resume
```
