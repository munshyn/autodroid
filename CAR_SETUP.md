# Autodroid in the real car

Autodroid 1.0.0 is installed on the phone as a release build. Its install was started by the phone's own
system installer with the Play Store recorded as installer, which is what Android Auto checks before listing
an app (a plain `adb install` is rejected). No Shizuku or UnknownAuto needed.

## Before the first drive

1. **Back up the signing key.** Copy `autodroid-release.jks` and `keystore.properties` (project folder) to a
   USB drive or cloud storage. Without them, future updates can't install over the current app.
2. On the phone, open **Autodroid** and check **Setup** shows OK for music access and location.
3. Optional: in Android Auto settings › ⋮, **Stop head unit server** (only needed for the PC simulator).

## In the car

1. Connect the phone as usual (cable or wireless Android Auto).
2. Open the app launcher on the car screen and tap **Autodroid**.
   - Not there? On the phone: Android Auto settings › **Customise launcher** and tick Autodroid.
3. Check, and note anything odd for Claude:
   - Does everything fit? (Screen size differs from the PC simulator; the layout scales to it.)
   - Is the text easy to read at a glance? Too big, too small?
   - Do the music buttons respond to touch?
   - Does the prayer card show your current zone?
   - Is the azan loud enough at prayer time? (Azan volume is in the phone settings.)
4. For directions, switch to Google Maps or Waze from the Android Auto taskbar; Autodroid keeps running and
   the azan still plays.

## If something goes wrong

| What you see | What to do |
|---|---|
| Autodroid missing from the car's app list | Customise launcher (above). Still missing: run the update script below and tap Update on the phone; that restores the install record Android Auto needs. |
| Black screen with only the round video button | Android Auto lost track of Autodroid. Disconnect the phone, force-stop Android Auto (phone Settings › Apps › Android Auto › Force stop), reconnect. |
| Music card says "Allow music access" | Phone: Autodroid › Setup › Music access › Allow. |
| Prayer zone wrong | Phone: Autodroid › Prayer zone › Detect zone now, or Choose zone. |
| No lyrics | The free Lyrica server may be asleep or down; it usually recovers. "No lyrics found" can also just mean no source has that song. |

## Updating Autodroid later (from the PC)

Phone and PC on the same Wi-Fi, Wireless debugging on (note the IP address & port it shows). In the
project folder:

```powershell
.\install-to-phone.ps1 -Phone 192.168.1.20:41234
```

It builds the app, copies it to the phone and opens the phone's installer; unlock the phone and tap **Update**.
Always update this way: an update installed with plain `adb install` makes Android Auto hide the app again.
Settings and permissions are kept (same signing key, no uninstall).
