# Android LAN preview

**LAN preview · unencrypted and unauthenticated.** No confidentiality, peer identity
or production-security claim. Gate E/W1 and authenticated transport are unchanged.
This branch starts at E2 dc19ab7 and retains its parser and interoperability fixtures.

Windows companion:
https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/tree/codex/lan-preview

Enable Windows LAN preview on one selected assigned private IPv4 interface, port
8765. Build on the existing Android SDK/JDK environment:

```powershell
.\gradlew.bat --no-daemon --max-workers=4 '-PlanPreviewHost=<PC_PRIVATE_IPV4>' testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open .lanpreview, Settings, enter that IP/port and connect. Ordinary launch does not
connect. This explicit debug launch connects to the build-selected host on 8765:

```text
adb shell am start -n dev.peripheral.battery.lanpreview/dev.peripheral.battery.MainActivity --ez lan_preview_connect true
```

Polling is STARTED/foreground-only, single-flight, approximately every two seconds
after completion, with 2/5/15-second failure delays. Leaving the Activity cancels I/O;
returning resumes the selected session. Restart requires explicit connection again.

Generated debug network configuration denies cleartext except the chosen numeric
host. Client independently enforces exact host/path, private IPv4, no redirects,
JSON type, 64-KiB size and three-second total timeout. Changing PC IP requires a
new preview build. Release has no INTERNET permission or cleartext exception and
keeps demo behavior. No custom trust manager or new runtime network dependency.

GET /v1/battery-snapshot reuses the existing strict v1 parser. HTTP success is an
unauthenticated receipt, not proof of source freshness. Precision, ownership and
unavailable reasons remain; null never becomes zero. Stale lastKnown is labelled
stale; failures never replace real/cache data with demo samples. An unchanged
publication becomes stale after 30 seconds using monotonic elapsed time.

No firewall/profile changes, ADB reverse/forward, discovery or device commands.
Check same-LAN routing/AP isolation and separately approved firewall policy when
unreachable. Do not broaden the listener or enable unrestricted cleartext traffic.

## Verification

E2 baseline: 223 JVM tests. Preview: 233 JVM tests passed, zero failures/errors/skips;
debug build and lint passed (zero errors, ten warnings). Tests cover validation,
cancellation/backoff, malformed data, zero/null/precision, disconnect/reconnect,
staleness, exact destinations, redirects, bounded responses and disposal.
Main APK installed on the API 36 phone. Xiaomi rejected the separate test APK and
denied shell touch injection; no device security settings changed. Physical results
are recorded below after verification.


### Physical LAN results — 2026-11-02

The installed Xiaomi/API 36 app fetched the real Windows endpoint over Wi-Fi and
visibly displayed G915 61%, G305 ~90% and HyperX estimate 29%, matching the source
publication. The HyperX source also returned 0% in this session; no filtering or
precision upgrade was applied. Neither value proves battery accuracy or ownership.
A transient G915 source outage appeared as stale 61%, as required.

Explicit debug disconnect and an unreachable-port interruption retained stale
readings. Reconnection restored Connected and live-preview labels, with no demo
substitution. This tested client interruption, not physically toggling Wi-Fi.
Successful fetch proves numeric-IP cleartext works on this phone. Other-host policy
assertions remain automated/static only because Xiaomi rejected test-APK installation.
No device security settings, Windows firewall/profile, providers or cadence changed.

Optional debug validation while the app is topmost:

```text
adb shell am start --activity-single-top -n dev.peripheral.battery.lanpreview/dev.peripheral.battery.MainActivity --ez lan_preview_disconnect true
adb shell am start --activity-single-top -n dev.peripheral.battery.lanpreview/dev.peripheral.battery.MainActivity --ez lan_preview_connect true --ei lan_preview_port 8766
adb shell am start --activity-single-top -n dev.peripheral.battery.lanpreview/dev.peripheral.battery.MainActivity --ez lan_preview_connect true --ei lan_preview_port 8765
```

These debug intents change only the preview session's connection. The approved host
cannot be overridden; ports must remain within 1024–65535. They do not alter Wi-Fi,
permissions, security gates or release behavior. Release APK build also passed and
merged-manifest inspection confirmed INTERNET exists only in debug, not release.

Foreground lifecycle check: zero new phone TCP connections over six seconds while
backgrounded after cancellation settled, then three over six seconds after resume.
This is bounded evidence, not a long-running network/battery claim.
