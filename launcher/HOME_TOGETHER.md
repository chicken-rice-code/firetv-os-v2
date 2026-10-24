# Home Together

Native Java dashboard for Android-based Fire OS and Android TV. The design uses a near-black ambient background, two aligned header baselines, one favourites row with installed TV artwork, and an unboxed current-weather strip with twelve hourly forecasts in two rows of six. The clock, greeting and main weather temperatures use the same size and regular font weight. Layout proportions adapt to 720p, 1080p and 4K. No Play Services or external runtime libraries are used.

## Build and install

Requires JDK 17 and Android SDK platform 25/build-tools 34. Set `ANDROID_HOME` and `JAVA_HOME` to the local SDK and JDK. On Windows, run these from Git Bash in `launcher/`:

```sh
BUILD_MODE=debug ./build.sh
adb devices
adb install -r build/dumbtv-home-debug.apk
adb shell am start -n tv.dumbtv.home/.Home
```

If several devices are attached, set `ADB_SERIAL` to the intended development device first. `./build.sh` builds the release APK at `build/dumbtv-home.apk`; debug builds use the same local signing key so updates keep existing settings. Debug mode permits inspection of this app's own data with `run-as` and must not be used for a production release.

Installing the APK does not change the system's default launcher. Fire TV Home-button restrictions depend on model and Fire OS version. The repository's existing opt-in launcher setup and restore scripts remain separate; none are required to preview this APK. Android APKs do not run on Vega OS. No Fire TV hardware has been tested in this session.

## Remote controls

- Left/right: browse favourites; Select: launch; hold Select or Menu: move, rename, hide or app information.
- Up: Settings; Down from Settings: return to the selected app. Back closes dialogs and Settings, then focuses an app on the dashboard.
- Settings → Choose and reorder favourites: select actual installed apps and change their order. An empty row shows Add apps.
- Settings → Weather location: enter a town/city and country. Coordinates and timezone are saved after a successful lookup. Temperature units can be Celsius or Fahrenheit. Unconfigured weather shows Set weather location.
- TV settings and inputs are available within Settings, preserving one app row on the dashboard.

The clock follows the device's local timezone and preferred format and updates once a minute while visible. Greeting periods are 05:00–11:59, 12:00–17:59 and 18:00–04:59. Weather uses the selected location's timezone, including day/night icons. All twelve forecast times, icons and temperatures align within two rows of six.

Weather comes from [Open-Meteo](https://open-meteo.com/en/docs), with approximately 30-minute refresh, HTTPS timeouts, cancellation while hidden and exponential retry backoff. Last successful data survives process restarts. Old/offline readings are explicitly labelled; unavailable readings are never fabricated. This app sends the chosen place/coordinates and network address to Open-Meteo and does not request GPS access. The free endpoint is intended here for personal, non-commercial use. Provider and icon attribution are in Settings.

## Implementation and checks

`Home` coordinates lifecycle and remote actions. `HomeScreen`, `ClockGreeting`, `AppsRow`, `WeatherRow` and `Options` own the UI. `Catalog` discovers installed apps; `WeatherRepository` caches responses; `Config` persists preferences. `DashboardLogic` isolates clock, cache, focus and scroll rules for host tests.

```sh
mkdir -p build/tests
"$JAVA_HOME/bin/javac" --release 8 -classpath "$ANDROID_HOME/platforms/android-25/android.jar" -d build/tests \
  src/tv/dumbtv/home/DashboardLogic.java src/tv/dumbtv/home/Weather.java tests/DashboardLogicTest.java
"$JAVA_HOME/bin/java" -cp build/tests tv.dumbtv.home.DashboardLogicTest
```

The suite covers greeting boundaries, midnight, timezone and daylight-saving changes, forecast hour ordering, cache expiry, retry limits, removed-app focus, empty favourites and horizontal scroll bounds. Emulator screenshots and session verification notes are kept in git-ignored `report/`. Installed apps provide their own banners/icons, so their artwork and the number of visible favourites depend on the device. The development emulator has only five discoverable apps, which limits a real-device test of scrolling beyond six favourites; scroll bounds are covered by the host tests.

For the same test suite, run `./test-dashboard.sh` with `JAVA_HOME` and `ANDROID_HOME` set. The weather model compiles against the SDK; host tests do not execute Android APIs. Visual and remote checks passed at 720p and 1080p. A requested 4K override remained at 1080p on this emulator, so a native 4K display remains unverified.

Google Material Symbols Outlined assets are distributed under Apache License 2.0. The full licence is included in the APK and [third_party/material-icons-LICENSE.txt](third_party/material-icons-LICENSE.txt).
