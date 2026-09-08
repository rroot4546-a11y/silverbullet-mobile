# SilverBullet Mobile (Android)

Android client for [silverbulletmd/silverbullet](https://github.com/silverbulletmd/silverbullet)
2.10.0 — the self-hosted, markdown-based note taking app.

This app runs the **real upstream SilverBullet client** (the SPA the server
serves) inside a WebView, with Android chrome around it. It targets the
server API contract from the 2.10.0 tag of the upstream source
(`server/src/router.rs`, `client/spaces/http_space_primitives.ts`):

- `GET {base}/.ping` — health/version check (reads `X-Server-Version`)
- `/.fs/{path}` — space file operations (list/read/write/delete)
- `/.client/*` — client bundle assets
- SPA fallback — the SilverBullet UI itself

## Features

- Connect to any self-hosted SilverBullet server (`https://host` or mounted
  under a path like `https://host/wiki`)
- First-run connect screen that verifies the server through `/.ping` before
  loading the UI
- Shared-mode bearer token support, injected exactly the way the upstream
  client expects from an embedder:
  `globalThis.silverbullet.bearerToken` (see `client/client.ts:371`, the
  same seam the Tauri app uses). Cookie-based login also works — WebView
  cookie jar persists the session.
- Configured-host scoping: links to other hosts open in the external browser
- Toolbar with back/forward/reload, progress bar, error page with retry
- Settings screen (server URL + token) with live connection test

## Build

```
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Requires JDK 17 and the Android SDK (compileSdk 34). Set `ANDROID_HOME`.

## Usage

1. Run a SilverBullet 2.x server (`silverbullet --user <user> --space <space>`)
2. Open the app, enter the server URL (and a shared token if the space uses one)
3. Connect — you land in your space

## Notes

- `android:usesCleartextTraffic="true"` is on so LAN / self-signed setups work;
  tighten it in `network_security_config.xml` for HTTPS-only deployments.
- The service worker / offline cache lives in the server-served client, so
  offline behavior matches a browser install of the PWA.