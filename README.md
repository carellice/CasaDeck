<p align="center">
  <img src="art/icon.png" width="128" alt="CasaDeck icon">
</p>

<h1 align="center">CasaDeck</h1>

<p align="center">
  Native Android and Windows companion apps for your <a href="https://casaos.io">CasaOS</a> home server.<br>
  Monitor, manage files, launch apps and open a shell — all from your phone.
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/carellice/CasaDeck?label=download&style=flat-square" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin + Compose">
</p>

---

## Screenshots

<p align="center">
  <img src="art/screenshots/home-light.png" width="200" alt="Dashboard (light)">
  <img src="art/screenshots/home-dark.png" width="200" alt="Dashboard (dark)">
  <img src="art/screenshots/files.png" width="200" alt="File manager">
</p>
<p align="center">
  <img src="art/screenshots/terminal.png" width="200" alt="SSH terminal">
  <img src="art/screenshots/settings.png" width="200" alt="Settings">
</p>
<p align="center">
  <img src="art/screenshots/widget.png" width="400" alt="Home screen widget">
</p>

<p align="center">
  <img src="art/screenshots/windows-home.png" width="600" alt="Windows dashboard">
</p>
<p align="center">
  <img src="art/screenshots/windows-files.png" width="600" alt="Windows file manager">
</p>

## Features

- **Live dashboard** — CPU, RAM, disk, temperature, network speed and a 90-second CPU load chart.
- **Apps** — see and open the apps installed on your CasaOS, with their running status.
- **File manager** — browse your server, upload files and whole folders (uploads continue in the background).
- **SSH terminal** — connect to your server's shell; the password stays in memory only while the app is open.
- **Home screen widgets** — server resources, server status and a CasaOS apps grid.
- **Quick Settings tile** — jump to your apps from the notification shade.
- **Notifications** — background monitoring alerts you when something goes wrong.
- **Biometric lock** — fingerprint, face or PIN on launch.
- **Theming** — light / dark / system, pure-black AMOLED, Material You dynamic colors and accent picker.

## Install

1. Download `CasaDeck.apk` from the [latest release](../../releases/latest).
2. Open it on your phone and allow installing from unknown sources if asked.
3. Enter your CasaOS address (e.g. `http://192.168.1.10`) and your CasaOS credentials.

Requires Android 8.0 (API 26) or newer and a reachable CasaOS instance (usually on your home LAN).

## Windows

A native WinUI 3 app (Fluent design, Mica, light/dark theme) with dashboard, apps, file manager (upload by drag & drop), SSH terminal and settings.

1. Download `CasaDeck-Windows.zip` from the [latest release](../../releases/latest).
2. Unzip it anywhere you like (e.g. `Documents\CasaDeck`) and run `CasaDeck.exe`. Nothing else to install: .NET and the Windows App SDK are bundled.
3. Updates: the app checks GitHub releases at launch (or **Impostazioni → Cerca aggiornamenti**) and replaces itself in place.

Requires Windows 10 1809 or newer, x64. The terminal needs the Microsoft Edge WebView2 Runtime, already present on Windows 11.

Build: `dotnet publish windows/CasaDeck -c Release -o build/windows/CasaDeck` (.NET 8 SDK, no Visual Studio needed).

## Build from source

```bash
git clone https://github.com/carellice/CasaDeck.git
cd CasaDeck
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Release builds are signed with the debug key so they install directly; use your own keystore if you redistribute.

## Notes

- Cleartext HTTP is allowed because CasaOS on a home network is usually served over plain `http`.
- CasaDeck is an unofficial client and is not affiliated with the CasaOS / IceWhale team.
