<p align="center">
  <img src="media/ic_launcher.webp" width="128" alt="VcServer" />
</p>

<h1 align="center">VcServer</h1>

<p align="center"><strong>SSH in your pocket.</strong></p>

<p align="center"><a href="./README.md">简体中文</a> | ENGLISH</p>

An Android SSH server manager: connect to remote hosts, watch status, transfer files, and bind a server to a provider’s control panel.

## 📱 Overview

VcServer connects over SSH to run commands, monitor status, transfer files, and handle day-to-day ops. You can bind a server to a provider and use their panel API for power, info, relocate, and usage. Data stays on-device; secrets use Android Keystore.

## 📸 Screenshots

| Server List                          | Add Server                        |
|-------------------------------------|-----------------------------------|
| ![Server List](media/server_list.png) | ![Add Server](media/add_server.png) |
| **Status Page**                     | **Settings Page**                 |
| ![Status Page](media/server_status.png) | ![Settings Page](media/settings.png) |
| **Operations**                      | **Details**                       |
| ![Operations](media/server_operation.jpg) | ![Details](media/server_usage_detail.jpg) |

## ✨ Core Features

### 🖥️ Servers and SSH
- Add by IP/hostname and port; password or key auth
- List, edit, delete, pin, swipe-delete, copy
- Connection test before save
- HTTP / SOCKS proxy and SSH jump host

### 📊 Monitoring
- CPU, memory, disk, OS, uptime
- NIC IPs and logged-in users
- Configurable auto-refresh

### 🏭 Provider binding
- Each SSH server can bind to **one** vendor (BandwagonHost, CloudCone, Virtualizor, and others)
- Unbind keeps the saved SSH configuration

### 💻 Terminal
- Interactive SSH; command history and local common-command completion
- ANSI / xterm 256-color; multiple shells on the same host
- Full-screen apps such as `htop` and `vim`
- Ctrl / Esc / Tab / arrows; adjustable font size

### 📁 Files
- SFTP upload, download, create, delete

### 🔒 Security and local data
- Keystore-encrypted passwords and private keys
- Local import/export; no collection or upload of user data
- Light / dark theme; Chinese and English

## 🚀 Quick Start

### Requirements
- Android Studio Narwhal Feature Drop | 2025.1.2 or higher
- JDK 17
- Android SDK 29+
- Gradle 8.7 (Kotlin 1.9.10, compile / target SDK 34, min Android 10)

### Build Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/FranzKafkaYu/VcServer.git
   cd VcServer
   ```

2. **Open the Project**
   - Open the project using Android Studio
   - Wait for Gradle sync to complete

3. **Run the Application**
   - Connect an Android device or start an emulator (Android 10+)
   - Click the Run button or use the shortcut `Shift+F10`

### Configuration

The application will automatically create a local database and settings storage on first run. All sensitive information (passwords, private keys) will be encrypted and stored using Android Keystore.

## 🔐 Security Notice

- All sensitive information (passwords, SSH private keys) is encrypted and stored using Android Keystore
- SSH connections use standard encryption protocols
- The application does not collect or upload any user data
- All data is stored locally on the device

## 👥 Contributing

Issues and Pull Requests are welcome!

⭐[![Stargazers over time](https://starchart.cc/FranzKafkaYu/VcServer.svg)](https://starchart.cc/FranzKafkaYu/VcServer)
