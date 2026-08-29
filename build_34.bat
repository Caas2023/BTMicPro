@echo off
set "JAVA_HOME=D:\aplicativo intercominicador\jdk-17.0.2"
set "ANDROID_HOME=D:\aplicativo intercominicador\android_sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"

echo Installing SDK 34...
echo y | sdkmanager.bat "platforms;android-34"

echo Building APK...
call gradlew.bat assembleDebug --no-daemon
