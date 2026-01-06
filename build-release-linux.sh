#!/bin/bash
# CI/CD Build Script for VcServer Android Project
# This script ensures the correct Java version is used and builds the release APK

set -e  # Exit on any error

echo "=== VcServer CI/CD Build Script ==="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Error: Java 17 or higher is required. Current version: $JAVA_VERSION"
    echo "Please install Java 17:"
    echo "  Ubuntu/Debian: sudo apt-get install openjdk-17-jdk"
    echo "  Or use SDKMAN: sdk install java 17.0.2-tem"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"

# Set JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    JAVA_PATH=$(which java)
    JAVA_HOME=$(dirname $(dirname $(readlink -f "$JAVA_PATH")))
    export JAVA_HOME
    echo "JAVA_HOME set to: $JAVA_HOME"
fi

# Stop any existing Gradle daemons to avoid version conflicts
echo "Stopping existing Gradle daemons..."
./gradlew --stop || true

# Clean previous build (optional, uncomment if needed)
# echo "Cleaning previous build..."
# ./gradlew clean

# Build release APK
echo "Building release APK..."
./gradlew assembleRelease

# Check if APK was created
APK_PATH="app/build/outputs/apk/release"
if [ -f "$APK_PATH"/*.apk ]; then
    echo "✓ Build successful! APK location: $APK_PATH"
    ls -lh "$APK_PATH"/*.apk
else
    echo "✗ Build failed: APK not found in $APK_PATH"
    exit 1
fi

echo "=== Build Complete ==="

