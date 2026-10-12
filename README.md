# Toast Catcher

Android app that records every toast message shown on the phone (from any app) while you
tell it to, and lets you read them back over adb. Built for PCTS / Android Auto automation,
where the test result only shows up as a toast.

The recording happens **on the phone**, with the phone's own timestamps, so lag between you
and the phone doesn't matter: nothing is missed, and `time` / `epoch_ms` are exact.

## 1. Get the APK

**Option A: GitHub (no installs)**
1. Create a new GitHub repo and upload this whole folder (keep `.github/` in it).
2. Open the repo's *Actions* tab, wait for "Build APK" to go green.
3. Open the run, download the artifact `ToastCatcher-debug-apk`, unzip it. That's `app-debug.apk`.

**Option B: Android Studio**
1. Install Android Studio, *Open* this folder, let it sync.
2. *Build > Build APK(s)*. The file ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Install and switch on (once per phone)

```
adb install -r app-debug.apk
adb shell settings put secure enabled_accessibility_services com.toastcatcher/com.toastcatcher.ToastService
adb shell settings put secure accessibility_enabled 1
```

If the phone already uses other accessibility services, don't run the first `settings put`
as is, because it would replace them. Use the Robot keyword `Enable Toast Service`
instead, which appends to the existing list.

Check it worked:

```
adb shell content call --uri content://com.toastcatcher.provider --method status
```

You want `service_enabled=true`.

## 3. Commands

| What | Command |
|---|---|
| start (wipes old captures, begins listening) | `adb shell content call --uri content://com.toastcatcher.provider --method start` |
| stop (captures are kept) | `adb shell content call --uri content://com.toastcatcher.provider --method stop` |
| retrieve | `adb shell content query --uri content://com.toastcatcher.provider/toasts` |
| retrieve and delete | `adb shell content query --uri content://com.toastcatcher.provider/toasts/drain` |
| delete only | `adb shell content delete --uri content://com.toastcatcher.provider/toasts` |
| status | `adb shell content call --uri content://com.toastcatcher.provider --method status` |

Retrieve prints one line per toast:

```
Row: 0 json={"id":1,"time":"2026-10-12 13:40:02.512","epoch_ms":1760269202512,"package":"com.google.android.projection.gearhead","text":"Test Passed!"}
```

All toasts from all apps are captured, so "Vehicle disconnected" next to "Test Passed!"
both show up, in order, with the app that sent each one.

## 4. Robot Framework

Put `robot/ToastCatcher.py` next to your suites and see `robot/example.robot`:

```
Library    ToastCatcher.py    serial=${SERIAL}

Start Toast Capture
...your PCTS steps...
${toast}=    Wait For Toast    Test (Passed|Failed)    timeout=300
Stop Toast Capture
${all}=      Get Toasts And Clear
```

## Notes

- There's also a small screen in the app (status, buttons, list of captured toasts) for
  manual checks.
- Start while already started just wipes and begins again.
- The provider is open to any app on the phone. Fine for locked-down test phones.
- If toasts don't show up: confirm `service_enabled=true`, and that the toast really is a
  normal Android toast (a custom-drawn popup inside the app isn't a toast).
