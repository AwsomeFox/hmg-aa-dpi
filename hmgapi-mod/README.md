# aa-dpi as an HMG-API mod, with an app in the car

Android Auto's DPI and its tap/scroll limit while driving, as a mod for
[HMG-API](https://github.com/VysseCodes/HMG-API) (the successor of HMG Leafia Plus).
The car gets an **AA DPI** tile in its app menu to change them. Nothing on the head unit
is changed: HMG-API hooks the Android Auto app (GPM) while it runs, instead of the
NEMESIS package's patched GPM APK.

> [!WARNING]
> **Not tested on a car yet.** Checked off the car only (`test/run.sh`).

## In the car

1. Open **AA DPI** from the car's app menu (it closes while the car is moving, like the
   car's own video apps).
2. Set the DPI with the slider, the -/+ buttons or a preset (240 is the car's own;
   200 is a good first step), choose the real density, and turn the tap/scroll limit
   on or off.
3. **Save**, then reconnect the phone: unplug and plug in, or turn its Wi-Fi off and on.
   The phone asks for the DPI and the car's sensors only when it connects, so a change
   shows at the next connection. No head-unit restart, no reinstall.

The same three settings are also on HMG-API's settings page for the mod.

| Setting | What it does |
|---|---|
| `dpi` (60-480, default 200) | the DPI every video setting offers the phone (`gpm.video_sink.nearest`) |
| `real_dpi` (`same` / `keep`) | real density the same as the DPI (aa-proxy-rs's way, the default), or the car's own |
| `tap_limit` (on = stock) | off: the speed sensor isn't offered to the phone (`gpm.sensor_source.init`), so Android Auto doesn't lock taps and scrolling on the move; driving status still goes to the phone |

## How it's built

| Part | What it is |
|---|---|
| `mod.json` | id `aa-dpi`; hooks on `gpm.video_sink.nearest` and `gpm.sensor_source.init` (both `change`); the app and its menu tile (`provides.launcher`); `not_while_driving`; any car HMG-API has a map for |
| `hooks/` (`hooks.jar`) | `DpiHooks`: reads the settings each time Android Auto connects, so a saved change needs no restart; `Dpi`: the arithmetic |
| `app/` (`AaDpi.apk`) | the settings app: reads and saves the mod's settings through HMG-API (`hmgapi-client.jar`) |
| `settings.json` | the three settings, with their defaults |

Another mod that sets Android Auto's density through HMG-API itself (`projection.aa_density`) would change the same value; turn one of them off. egmp-aa-ev's HMG-API mod also changes the sensor list (it adds the EV sensor), and the two combine.

## Build and test

Needs a JDK, the Android SDK (build-tools and a platform `android.jar`; `ANDROID_HOME`)
and an HMG-API checkout (`HMGAPI`, default `../HMG-API`). No car files.

```bash
HMGAPI=../HMG-API sh hmgapi-mod/build.sh
```

```bash
HMGAPI=../HMG-API sh hmgapi-mod/test/run.sh
```

The first gives `build/hmgapi/mod/aa-dpi/`. The app is signed with a key made once in
`build/keys/` (or `AADPI_APP_KEY`); keep it, so updates install over the app. With your
HMG-API author key, the build also signs the package, `dist/aa-dpi-<version>.zip`:

```bash
java -cp ../HMG-API/out/engine-host hmgapi.engine.Signing keygen ~/hmg-keys/aa-dpi.key
```

```bash
HMGAPI=../HMG-API sh hmgapi-mod/build.sh ~/hmg-keys/aa-dpi.key
```

## Install

1. Uninstall the NEMESIS package `aa.dpi` first (NEMESIS Manager → `aa.dpi` → uninstall,
   reboot): it patches GPM's file, which HMG-API expects stock.
2. HMG-API 1.0.0-beta.6 or newer. Its hooks run on a car whose map is checked on a car,
   or with "testing maps" on in the HMG-API app (`hmgapid engine verify-map` checks it).
3. Put `aa-dpi-<version>.zip` in `HMGAPI/updates/` on a USB stick, then **Check USB
   updates** in the HMG-API app (or install it over adb).

In HMG-API's log, `[aa-dpi]` lines show each connection's DPI and tap limit.
