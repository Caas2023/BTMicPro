@echo off
set "JAVA_HOME=D:\aplicativo intercominicador\jdk-17.0.2"
set "ANDROID_HOME=D:\aplicativo intercominicador\android_sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"

echo Building APK...
call gradlew.bat assembleDebug --no-daemon
