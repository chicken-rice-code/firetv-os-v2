# Home Together / dumbtv home

A native Fire TV dashboard with your apps, a shared greeting and real local weather. See [HOME_TOGETHER.md](HOME_TOGETHER.md) for the current v3 UI, local build/install commands and verification details. The existing Fire TV launcher setup is documented below.

- **Small native APK.** Plain Java and Android views. No AndroidX, Kotlin, Compose, Play
  Services or any other library.
- **Weather beneath your apps.** Current conditions, feels-like temperature, high/low and twelve hourly forecasts come from Open-Meteo for a city chosen in Settings. All twelve hours stay visible in two rows of six. Successful readings are cached and labelled when stale or offline. The city/coordinates and network address go to Open-Meteo; device location is not read.
- **No runtime permissions.** Fire OS 6 uses three permissions capped at Android 8.1. Weather uses the normal INTERNET permission. Check: `aapt2 dump permissions dumbtv-home.apk`.
- **No ads, no recommended rows, no account, no analytics, no update checks.**
- **One consistent UI kit.** A near-black ambient canvas, charcoal surfaces, white focus outlines and a gentle 160 ms focus animation. Six installed-app banners fit across one row with labels beneath. The clock, greeting and main weather temperatures share the same font size and weight.
- Resource use is checked on the development emulator; current-session results are recorded in git-ignored `report/`. Fire TV hardware measurements for this UI are outstanding.

## Install

You need `adb` and a TV with ADB debugging on.

```sh
adb connect <tv-ip>:5555         # accept "Allow USB debugging?" on the TV
./launcher/install.sh            # download, check the SHA-256, install, take over HOME
```

The APK comes from this repository's
[releases](https://github.com/humblemuzzu/dumbtv/releases/latest), and `install.sh`
refuses it if the checksum differs from the one in `version.env`. Installing the
APK by hand works, but HOME only switches over once `install.sh` (or
`scripts/launcher.sh`) has set up the HOME hook described below.

Undo: `./launcher/install.sh --undo` gives HOME back to Amazon's launcher.

## Using it

| Remote | Does |
|---|---|
| OK | open the app, or switch to the input |
| Hold OK, or ☰ Menu | Move, Rename, Hide, App info |
| Move mode | ◀ ▶ moves the tile, any other key drops it |
| Up from the app row | Settings |
| Back on the home screen | return focus to an app |
| On an input: Back | back to the home screen |
| On an input: ☰ Menu | show the input's name again |
| Hold HOME | Fire OS quick menu (Amazon's, unchanged) |

**Settings** changes each choice with OK:

| Setting | Choices |
|---|---|
| Clock | system, 24-hour, 12-hour |
| Temperature | Celsius or Fahrenheit |
| Weather location | town/city and country; blank turns weather off |
| Open inputs with | Automatic, the TV's app, dumbtv home (see below) |

Also available: choose/reorder favourites, open a TV input, TV settings, reset order,
names and hidden tiles, and **Open Amazon's home screen**. Apps
without a TV launcher entry, like the Appstore, start hidden; tick them to add them.

Weather source: Open-Meteo. Until a place is configured, choose **Set weather location**. Static weather readings do not add unnecessary focus stops.

## HDMI inputs

**Options › Open inputs with** decides what an input tile does:

- **Automatic** (default): the TV's own app, when exactly one app other than
  Amazon's Live TV claims the input; otherwise the launcher's own screen. Amazon's
  Live TV claims every input and then opens its settings or a channel scan.
- **The TV's app**: always hand the input to the TV's app, as releases up to 1.1
  did.
- **dumbtv home**: always use the launcher's own screen. It uses Android's public
  `TvView`: the picture, the input's name for a moment, and "No signal" when nothing
  is connected. Back returns to the home screen, and HOME releases the input at
  once. Nothing new is requested to do this.

## The settings file

Everything lives in one text file on the TV:

```
/sdcard/Android/data/tv.dumbtv.home/files/home.conf
```

```ini
# white blue cyan green amber red pink violet, or #RRGGBB
accent=blue
# small medium large
tiles=medium
# rows grid
layout=rows
# focus always never
labels=focus
# auto 24h 12h
clock=auto
# on off
inputs=on
# auto tv home
switch=auto
apps.order=com.amazon.firetv.youtube,org.videolan.vlc
apps.hide=org.videolan.vlc
apps.show=com.amazon.venezia
inputs.hide=com.droidlogic.tvinput/.services.AV1InputService/HW1
name.com.droidlogic.tvinput/.services.Hdmi2InputService/HW6=PS5
```

Comments go on their own line. Back it up with `adb pull`, restore it with
`adb push`; changes apply the next time the home screen comes back.

## How HOME works on Fire OS

Fire OS has no working "default launcher" setting, and Amazon's launcher is a
protected package. Measured on a Fire OS 7 television:

1. **The HOME key cannot be intercepted.** Fire OS handles it before any
   accessibility key filter runs. So `Guard`, a small accessibility service, waits
   for Amazon's home window instead. It sees window changes only and reads no
   screen content.
2. **Android delays app launches for 5 s after HOME** (`APP_SWITCH_DELAY_TIME`),
   which is why other launchers flash Amazon's home. A launch fired by
   AlarmManager comes from `system_server` and is exempt.
   `install.sh` sets `settings global alarm_manager_constants min_futurity=0` so
   the alarm fires at once; `--undo` removes it.
3. **Amazon's home is covered before it draws.** `Guard` keeps an overlay ready,
   with no surface while idle so it costs nothing during video, and removes it once
   dumbtv home is on screen.
4. **Fire OS 8 (Android 11) only lets an app with a visible window start an
   activity.** The cover is that window, so `Guard` launches once it has drawn.

HOME from an app shows about 0.3 s of black, then this home screen.

### Fire OS 6

Fire OS 6.7 refuses third-party accessibility services, so it uses `Keeper`, a
small foreground service:

1. UsageStats (`appops set tv.dumbtv.home GET_USAGE_STATS allow`, granted by
   `install.sh`) tells it when Amazon's launcher comes to the front.
2. `ActivityManager.moveTaskToFront` brings dumbtv home back. That is not an
   activity launch, so the 5 s delay does not apply: under 0.25 s with a real
   remote.
3. At boot there is no dumbtv task yet, so the first start uses an alarm.

**Options › Open Amazon's home screen** pauses the takeover until another app is
opened.

## Build it yourself

Needs JDK 17 and the Android SDK (`platforms;android-25`, `build-tools`). There is
no Gradle: `build.sh` runs aapt2, javac, d8, zipalign and apksigner directly, and
compiles against Android 7.1 so one APK runs on Fire OS 6, 7 and 8.

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-25" "build-tools;34.0.0"
./launcher/build.sh              # launcher/build/dumbtv-home.apk
./launcher/install.sh --build    # build, install, take over HOME
```

On Linux, set `ANDROID_HOME`; `build.sh` finds a JDK 17 in
`/usr/lib/jvm/java-17-openjdk` (Arch: `pacman -S jdk17-openjdk`). It prefers JDK 17
because build-tools 34's `d8` has crashed on classes from newer JDKs.

Your build is signed with a key `build.sh` creates on first run, and Android will
not update the release with it or the other way round. `install.sh` says so; back
up `home.conf`, then `adb uninstall tv.dumbtv.home`.

## Diagnostics

Logcat has everything under the tag `dumbtvtest`. For problems at boot, when the
log has rotated by the time you can read it, the Keeper can also write a file that
survives reboots:

```sh
adb shell touch /sdcard/Android/data/tv.dumbtv.home/files/debug
```

While that file exists, log lines go to `files/keeper.log` in the same folder.
Delete both to stop.

## Files

| File | What it is |
|---|---|
| `src/.../Home.java` | dashboard lifecycle, remote actions, app launches and focus restoration |
| `src/.../HomeScreen.java` | responsive header, app row and weather layout |
| `src/.../ClockGreeting.java` | aligned clock/greeting baselines and injectable time source |
| `src/.../Tile.java` | installed app banners/icons with labels beneath |
| `src/.../WeatherRow.java` | unboxed current conditions and twelve hourly forecasts |
| `src/.../WeatherRepository.java` | HTTPS requests, cache, cancellation and retry backoff |
| `src/.../WeatherIconView.java` | consistent Material outline vectors with day/night variants |
| `src/.../Favorites.java` | remote selection and ordering of favourites |
| `src/.../Catalog.java` | which apps and inputs exist, and their order |
| `src/.../Item.java` | one app or input |
| `src/.../Input.java` | the built-in input screen (`TvView`) |
| `src/.../Config.java` | reads and writes `home.conf` |
| `src/.../Options.java` | the settings screen |
| `src/.../Ui.java` | shared colours, text and sizes |
| `src/.../Guard.java` | the Fire OS 7 and 8 HOME takeover |
| `src/.../Keeper.java` | the Fire OS 6 HOME takeover |
| `src/.../BootReceiver.java` | restarts the Keeper after a boot or an update |
| `build.sh` | aapt2, javac, d8, zipalign, apksigner |
| `install.sh` | download and checksum (or `--build`), install, HOME hook, `--undo` |
| `version.env` | the version and the release's SHA-256, read by the three scripts |
| `release-check.sh` | checks that the version, the built APK and the GitHub release agree |
| `tests/FocusColorTest.java` | host-side test for `FocusColor`, run by CI |

## Releasing

1. Bump `VERSION`, `VERSION_CODE` and `VERSION_NAME` in `version.env`.
2. `./build.sh`, copy `build/dumbtv-home.apk` to `build/dumbtv-home-<VERSION>.apk`,
   and put its SHA-256 in `version.env`.
3. Upload it as `dumbtv-home.apk` to a release tagged `launcher-v<VERSION>`.
4. `./launcher/release-check.sh` fails if the tag, the APK and the hash disagree.
   CI runs `--manifest` on every push.
