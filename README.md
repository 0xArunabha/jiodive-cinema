# 🥽 JioDive VR Cinema

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A static, browser-based Virtual Reality Theater player built with Three.js and Vite for the **JioDive VR Headset** and mobile browsers (Android Chrome, Brave, Samsung Internet, and iOS Safari).

> **Hosted Demo (HTTPS):** [`https://0xarunabha.github.io/jiodive-cinema/`](https://0xarunabha.github.io/jiodive-cinema/)  
> *(Serves the static `dist/` build via [GitHub Pages workflow](.github/workflows/deploy.yml) — enable **Settings ➔ Pages ➔ GitHub Actions** once on the repository so gyroscope, wake lock, and fullscreen run over trusted HTTPS without local Vite setup or self-signed SSL warnings.)*

![JioDive VR Cinema dual-eye theater preview](docs/vr-cinema-preview.png)

---

## ✨ Features

- **Calibrated JioDive Optics & Distortion Correction:** Defaults to the verified JioDive (`JIOCardboardHeadset`) inter-lens separation (`62 mm`, adjustable `58–72 mm`), selectable FOV (`70°–85°`), and a toggleable two-pass Brown-Conrady barrel lens distortion shader (`k1 = 0.101`, `k2 = 0.098` normalized in tangent space) with zero-overhead direct bypass when turned off.
- **Dynamic Reticle Depth Matching:** The gaze reticle projects from cyclopean head center (`rig.head`) to the exact 3D distance of whatever you're looking at (menu buttons at ~4.5 m or curved screen at 5.2–7.45 m) with proportional scaling to prevent stereo diplopia (double vision).
- **Physical Button, Hands-Free Gaze Dwell & Controller Support:**
  - **JioDive Capacitive Trigger:** Single click activates aimed button or toggles menu/playback; fast double click (< 350 ms) recenters yaw drift cleanly.
  - **Hands-Free Gaze Dwell:** Optional `1.2 s` look-to-click mode with an animated cyan progress ring around the reticle (`200 ms` anti-jitter grace period).
  - **Bluetooth Gamepad & Keyboard:** Works with Xbox/PS/VR Bluetooth controllers (Button `A` select, `B` menu, `X/Y` recenter, D-pad/bumpers seek, thumbstick desktop look) and desktop keys (`Space`/`Enter`, `ArrowLeft`/`ArrowRight`, `R`, `M`, `H`, `Esc`).
- **Comfort Brightness / Thermal Dimmer:** Cycles `100%` ➔ `80%` ➔ `60%` display dimming in VR (aligned with JioImmerse's `60%` headset brightness limit to reduce glare and OLED heat/battery draw).
- **Multiple Virtual Environments:**
  - 🏛 **Grand Cinema Hall:** Multi-tier theater with curved screen, amber aisle runner LED strips, stage clearance, and real-time screen light bounce.
  - 🌌 **Void Mode (OLED):** Pure pitch black space with floating screen (skips environment lighting updates and minimizes pixel draw for mobile battery life).
  - 🛏 **Bed Mode:** Screen and control menu reorient upward together onto the ceiling so you can watch lying flat.
- **Screen Distance / Seat Row Presets:** Switch live in VR or the launcher between **Front IMAX** (`5.2m`, immersive `82°` wrap), **Standard** (`6.5m`, `64°`), and **Balcony** (`7.45m`, compact `44°`), automatically scaling the screen, frame, subtitles, and dynamic reticle depth.
- **Comprehensive 3D Format Support & L/R Eye Swap:**
  - Standard 2D movies (shared single `VideoTexture` for minimal GPU upload overhead).
  - 3D Half Side-by-Side (Half-SBS) with 2:1 anamorphic aspect correction.
  - 3D Full Side-by-Side (Full-SBS).
  - 3D Half Over-Under (Half-OU) with 2:1 anamorphic aspect correction.
  - 3D Full Over-Under (Full-OU).
  - Live **3D Eye Swap** (`🔀 L/R` ➔ `🔀 R/L`) to fix inverted Right-Left / Bottom-Top 3D encodes.
- **Interactive Timeline & Scrubbing:** Floating gaze seek bar with hover time preview (`➔ MM:SS`) and current/total duration readout directly inside VR, plus `⏪ -10s` / `⏩ +10s` quick skips.
- **VR Subtitles (`.srt` / `.vtt`):** Load local `.srt` or `.vtt` subtitle files in the launcher to render stereo-converged captions on a curved layer just in front of the cinema screen (works in 2D, 3D SBS/OU, and Bed Mode, with live `💬 CC On/Off` toggling in VR).
- **Local Files & Online URL Streaming:**
  - Load local video files (MP4 H.264/AAC, WebM) directly from device storage.
  - Stream CORS-enabled online video URLs (`https://...`).
  - Built-in Oceans HD sample stream with automatic MP4/WebM codec selection.
- **Clean In-VR Navigation & Saved Fit:** Adjust rooms, screen size, 3D formats/eye swap, IPD, FOV, distortion, dwell mode, and brightness live inside VR (with IPD, FOV, seat preset, distortion, dwell, and brightness persisted in `localStorage`), or click `🚪 Exit` anytime without reloading.
- **Screen Wake-Lock & Non-Blocking Entry:** Keeps the display awake while watching, re-acquires wake lock on `visibilitychange`, and enters VR immediately without stalling on WebView fullscreen/wake-lock promises.
- **Mobile-Optimized Performance:** Lambert shading with baked sconce glow textures, no redundant per-frame `VideoTexture` uploads, paused 3D render loop behind the 2D launcher.

---

## 🌐 Hosting / Static Deployment (Recommended)

Gyroscope (`DeviceOrientationEvent`), Screen Wake Lock, and Fullscreen APIs require a **trusted HTTPS origin** on phones. Because this app is 100% static after `npm run build`, deploying `dist/` to any static HTTPS host removes the need for local dev servers or bypassing self-signed certificates:

- **GitHub Pages (included):** Enable **Settings ➔ Pages ➔ Source: GitHub Actions**; [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) automatically builds and publishes `dist/` on every push to `main`.
- **Cloudflare Pages / Netlify / Vercel:** Connect the repository with build command `npm run build` and publish directory `dist` (`vite.config.js` configures `base: './'` so assets resolve from any subpath or domain).

---

## 📱 Mobile Setup & Platform Notes

### Android (Chrome / Brave / Samsung Internet)
1. Open the hosted HTTPS URL (or local dev HTTPS URL).
2. Allow motion/orientation sensor access if prompted.
3. Tap **"Enter VR & Insert into JioDive"** — the app requests fullscreen and landscape orientation lock.

### iOS Safari (iPhone)
iOS Safari does not support the HTML5 Fullscreen API for regular web tabs. To watch edge-to-edge without Safari browser toolbars:
1. Open the hosted HTTPS URL in Safari.
2. Tap **Share** ➔ **"Add to Home Screen"** ➔ **Add**.
3. Open **JioDive VR** from your Home Screen (`display: standalone` via PWA manifest).
4. Allow **Motion & Orientation Access** when prompted (or under *iOS Settings > Safari*).

---

## 🚀 Local Development

Requires **Node.js 18+** (uses Vite `>= 6.1.0` with `--configLoader native`):

```bash
npm install
npm run dev
```

Vite serves the app over local HTTPS using `@vitejs/plugin-basic-ssl`:
```
  ➜  Local:   https://localhost:5173/
  ➜  Network: https://192.168.1.x:5173/
```

Open the Network URL on your phone over the same Wi-Fi network *(accept the local self-signed dev certificate if prompted)*.

---

## 🎮 JioDive Controls Guide

| Action | Control |
| :--- | :--- |
| **Select Button / Action** | Aim reticle & **click JioDive button** (or enable **⌛ Dwell 1.2s**, press **Space/Enter**, or Gamepad **A**). |
| **Toggle Menu (Hide/Show)** | **Click physical button** on empty space, press **H**, or Gamepad **B**. |
| **Recenter Screen (Fix Drift)** | **Double-click** quickly (< 350ms), click **🎯 Recenter**, press **R**, or Gamepad **X/Y/Start**. |
| **Seek Timeline** | Aim along top progress bar (`➔ MM:SS` preview) & click, use **⏪ -10s / ⏩ +10s**, **Arrow keys**, or **LB/RB / D-Pad**. |
| **Toggle Subtitles** | Click **💬 CC** in top bar (`On` / `Off`) after loading an `.srt` or `.vtt` file in the launcher. |
| **Screen Size / Distance** | Click **📐 Standard** to cycle **🏟 IMAX** (5.2m), **📐 Standard** (6.5m), and **🪑 Balcony** (7.45m). |
| **Swap 3D Eyes** | Click **🔀 L/R** to flip Left/Right or Top/Bottom halves (`🔀 R/L`) for inverted 3D files. |
| **Cycle IPD & FOV** | Click **👁 62mm** (58/62/64/68/72 mm) or **🔭 FOV 75°** (70°/75°/80°/85°) in VR. |
| **Lens Distortion & Dimmer** | Click **🔍 Lens On/Off** (`k1=0.101, k2=0.098`) or **☀ 100%** (100% / 80% / 60% dimmer). |
| **Hands-Free Gaze Dwell** | Click **⌛ Dwell** to toggle automatic `1.2s` look-to-click selection. |
| **Cycle Format** | Click **🕶 Format** (`2D` ➔ `Half-SBS` ➔ `Full-SBS` ➔ `Half-OU` ➔ `Full-OU`). |
| **Bed / Lay-Down Mode** | Click **🛏 Bed** to tilt both screen and menu upward onto the ceiling. |
| **Exit VR** | Click **🚪 Exit**, press **Esc**, or exit fullscreen to return to the launcher. |

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

