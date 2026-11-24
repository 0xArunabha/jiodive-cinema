# 🥽 JioDive VR Cinema

A modern, high-performance Virtual Reality Theater player designed specifically for the **JioDive VR Headset** and mobile browsers (Android Chrome, Brave, Samsung Internet, iOS Safari).

---

## ✨ Features

- **Split-Screen Stereoscopic Rendering:** Precise dual-eye rendering with optical IPD separation for JioDive lenses.
- **Physical Click Trigger Integration:** Uses JioDive's physical capacitive button for instant button selection, menu toggling, and double-click view recentering.
- **Real-Time Cinema Light Bounce:** Samples the video colors in real time and projects cinematic ambient bounce light across the theater seats, walls, and floor.
- **Multiple Virtual Environments:**
  - 🏛 **Grand Cinema Hall:** Tiered crimson theater seats, floor aisle lights, acoustic walls.
  - 🌌 **Void Mode (OLED):** Pure pitch black space with floating screen (conserves phone battery, maximum contrast).
  - 🛏 **Bed Mode:** Screen dynamically reoriented to the ceiling so you can watch movies comfortably while lying down flat.
- **Format Support:** Standard 2D movies, 3D Side-by-Side (SBS), and 3D Over-Under (OU).
- **Local & Streaming Playback:** Load any local movie file (MP4, WebM, MKV supported by browser) from phone storage or stream online URLs.
- **Screen Wake-Lock:** Automatically prevents phone screen from timing out while watching.

---

## 🚀 How to Run & Test on Your JioDive

### 1. Start the Dev Server
From the `jiodive-cinema` folder:
```bash
npm run dev
```

Vite will start an HTTPS server with a local network IP, for example:
```
  ➜  Network: https://192.168.1.5:5173/
```

### 2. Open on Your Phone
1. Connect your phone to the **same Wi-Fi network** as your computer.
2. Open Chrome/Brave on your phone and go to the network URL (e.g. `https://192.168.1.5:5173`).
3. *(If using basic SSL, tap "Advanced" -> "Proceed to site")*.

### 3. Insert Phone into JioDive
1. Tap **"Choose Local Movie File"** (or use the built-in Demo movie).
2. Tap **"Enter VR & Insert into JioDive"**.
3. Align phone in the center tray of your JioDive headset and lock it in.

---

## 🎮 JioDive Controls Guide

| Action | Control |
| :--- | :--- |
| **Select Button / Action** | Aim the center reticle at any floating button and **click the JioDive physical button**. |
| **Toggle Menu (Hide/Show)** | **Click the physical button** while aiming at empty space / movie screen. |
| **Recenter Screen (Fix Drift)** | **Double-click** the physical button quickly. |
| **Bed / Lay-Down Mode** | Click the **🛏 Bed Mode** button to move the screen to the ceiling. |
