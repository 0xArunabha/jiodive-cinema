# 🥽 JioDive VR Cinema

A modern, high-performance Virtual Reality Theater player designed specifically for the **JioDive VR Headset** and mobile browsers (Android Chrome, Brave, Samsung Internet, and iOS Safari).

---

## ✨ Features

- **Stereoscopic Dual-Eye Rendering:** Software side-by-side stereoscopic rendering with customizable Interpupillary Distance (IPD: 58mm, 64mm, 68mm, 72mm) and Field of View (FOV: 70°–85°), adjustable both in the launcher and live inside VR.
- **Dynamic Reticle Depth Matching:** The gaze reticle dynamically projects to the exact 3D distance of whatever you're looking at (floating buttons at ~4.5m or movie screen at ~6.5m) with proportional scaling, completely eliminating stereo diplopia (double vision).
- **Physical Click Trigger Integration:** Uses JioDive's physical capacitive conductive button:
  - Single click on a button to activate.
  - Single click in empty space to toggle menu visibility.
  - Fast double click to instantly recenter gyro drift without side effects.
- **Multiple Virtual Environments:**
  - 🏛 **Grand Cinema Hall:** Multi-tier theater with curved screen, aisle runner lights, and stage clearance.
  - 🌌 **Void Mode (OLED):** Pure pitch black space with floating screen (conserves mobile battery, maximum contrast).
  - 🛏 **Bed Mode:** Screen and control menu reoriented together to the ceiling so you can watch movies comfortably while lying down flat.
- **Comprehensive 3D Format Support:**
  - Standard 2D movies (using a shared single texture for optimal VRAM/GPU efficiency).
  - 3D Half Side-by-Side (Half-SBS) with 2:1 squeeze aspect ratio correction.
  - 3D Full Side-by-Side (Full-SBS).
  - 3D Half Over-Under (Half-OU) with 2:1 squeeze aspect ratio correction.
  - 3D Full Over-Under (Full-OU).
- **Local Files & Online URL Streaming:**
  - Load local video files (MP4 H.264/AAC, WebM) directly from your device with zero upload latency.
  - Stream CORS-enabled online video URLs (`https://...`).
  - One-click load for the built-in Oceans HD sample stream.
- **Clean In-VR Navigation:** Switch rooms, toggle formats, adjust IPD, or tap `🚪 Exit VR` anytime to return to the movie selection launcher without reloading the page.
- **Screen Wake-Lock:** Automatically keeps the mobile display awake and automatically re-acquires wake-lock on returning from background tabs.
- **Mobile-Optimized Performance:** Lightweight Lambert shading, baked additive sconce lighting, zero per-frame CPU-to-GPU texture re-uploads in Three.js, and automatic render suspension while in the 2D launcher.

---

## 📱 Mobile Setup & Platform Notes

### Android (Chrome / Brave / Samsung Internet)
1. Open the app URL over HTTPS.
2. Allow orientation and sensor access when prompted.
3. Tap **"Enter VR & Insert into JioDive"** — the app will request fullscreen and lock into landscape mode automatically.

### iOS Safari (iPhone)
Apple Safari does not support the HTML5 Fullscreen API for standard web pages. To experience edge-to-edge VR without Safari's top and bottom browser toolbars:
1. Open the app in Safari.
2. Tap the **Share** button (box with upward arrow).
3. Select **"Add to Home Screen"** and tap **Add**.
4. Launch **JioDive VR** directly from your iPhone home screen as a standalone Progressive Web App (PWA).
5. When prompted, allow Motion & Orientation permissions (or enable in *iOS Settings > Safari > Motion & Orientation Access*).

---

## 🚀 How to Run & Test

### 1. Start Dev Server
```bash
npm run dev
```

Vite will serve the app with local SSL over HTTPS:
```
  ➜  Local:   https://localhost:5173/
  ➜  Network: https://192.168.1.x:5173/
```

### 2. Open on Your Phone
1. Connect your phone to the same Wi-Fi network as your computer.
2. Open Chrome/Brave/Safari on your phone and navigate to the HTTPS network address.
3. *(If using Vite's local development certificate, tap "Advanced" -> "Proceed to site")*.

### 3. Insert Phone into JioDive
1. Choose a local video file, enter an online streaming URL, or click the Oceans HD sample.
2. Choose your initial format and environment.
3. Tap **"Enter VR & Insert into JioDive"**.
4. Slide your phone into the headset tray and lock the front latch.

---

## 🎮 JioDive Controls Guide

| Action | Control |
| :--- | :--- |
| **Select Button / Action** | Aim the center reticle at any floating button and **click the JioDive physical button**. |
| **Toggle Menu (Hide/Show)** | **Click the physical button** while aiming at empty space / movie screen. |
| **Recenter Screen (Fix Drift)** | **Double-click** the physical button quickly (< 350ms). |
| **Cycle IPD** | Click **👁 IPD** in the VR menu (58mm ➔ 64mm ➔ 68mm ➔ 72mm). |
| **Cycle Format** | Click **🕶 Format** in the VR menu (2D ➔ Half-SBS ➔ Full-SBS ➔ Half-OU ➔ Full-OU). |
| **Bed / Lay-Down Mode** | Click the **🛏 Bed Mode** button to tilt both the screen and menu to the ceiling. |
| **Exit VR** | Click **🚪 Exit VR** or exit fullscreen to return to the movie selection launcher. |

