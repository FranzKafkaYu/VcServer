<p align="center">
  <img src="media/ic_launcher.webp" width="96" />
</p>  

# VcServer

A powerful Android SSH server management application that supports connecting to remote servers via SSH for management.

## 📱 Application Overview

VcServer is a modern Android application that allows users to securely connect to remote servers via SSH protocol, execute commands, monitor server status, and perform daily operations management. The app follows Material Design 3 design guidelines, providing a smooth user experience.

## 📸 Screenshots

| Server List                          | Add Server                        |
|-------------------------------------|-----------------------------------|
| ![Server List](media/server_list.png) | ![Add Server](media/add_server.png) |
| **Status Page**                     | **Settings Page**                 |
| ![Status Page](media/server_status.png) | ![Settings Page](media/settings.png) |

## ✨ Core Features

### 🔐 Server Management
- **Add Servers**: Support adding servers via IP/domain name and SSH port
- **Multiple Authentication Methods**: Password authentication, key authentication  
- **Server List Management**: View, edit, and delete servers  
- **Connection Testing**: Test connection before adding a server

### 📊 Server Monitoring
- **Real-time Status Monitoring**: CPU, memory, disk, system information, and uptime  
- **Auto Refresh**: Configurable refresh interval for automatic server status updates  

### 💻 Interactive Terminal
- **SSH Terminal**: Execute commands via SSH connection
- **Command History**: Support viewing and executing historical commands
- **Command Completion**: Support command auto-completion
- **ANSI Support**: Support ANSI color code display
- **Terminal Buffer**: Manage terminal output content

### ⚙️ Application Settings
- **Theme Settings**: Dark and light theme adaptation  
- **Language Settings**: Support Chinese and English  
- **Connection Settings**: Default port, connection timeout settings  
- **Display Settings**: Refresh time interval settings  
- **Proxy Settings**: HTTP and SOCKS proxy settings  

### 🔒 Security Features
- **Encrypted Storage**: Use Android Keystore to encrypt and store sensitive information (passwords, SSH private keys)
- **Secure Transmission**: All SSH connections use encrypted protocols
- **Data Persistence**: Server configuration information is securely saved to local database

### 🛠️ Development Tools
- **Build Tool**: Gradle 8.7
- **Kotlin Version**: 1.9.10
- **Compile SDK**: 34
- **Min SDK**: Android 10 (API 29)
- **Target SDK**: 34

## 🚀 Quick Start

### Requirements
- Android Studio Narwhal Feature Drop | 2025.1.2 or higher
- JDK 17
- Android SDK 29+
- Gradle 8.7

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
