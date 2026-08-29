$ErrorActionPreference = "Stop"
$ProgressPreference = 'SilentlyContinue'

$WorkingDir = "D:\aplicativo intercominicador"
Set-Location $WorkingDir

Write-Host "Downloading OpenJDK 17..."
if (-not (Test-Path "jdk-17.0.2")) {
    curl.exe -L -o jdk.zip "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip"
    Expand-Archive "jdk.zip" -DestinationPath "." -Force
}
$env:JAVA_HOME = "$WorkingDir\jdk-17.0.2"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

Write-Host "Downloading Android SDK Command-line Tools..."
if (-not (Test-Path "android_sdk")) {
    New-Item -ItemType Directory -Force -Path "android_sdk" | Out-Null
    curl.exe -L -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    Expand-Archive "cmdline-tools.zip" -DestinationPath "android_sdk" -Force
    Rename-Item -Path "android_sdk\cmdline-tools" -NewName "latest"
    New-Item -ItemType Directory -Force -Path "android_sdk\cmdline-tools" | Out-Null
    Move-Item -Path "android_sdk\latest" -Destination "android_sdk\cmdline-tools\latest"
}
$env:ANDROID_HOME = "$WorkingDir\android_sdk"
$env:PATH = "$env:ANDROID_HOME\cmdline-tools\latest\bin;" + $env:PATH

Write-Host "Accepting Licenses..."
$y = "y`n" * 50
$y | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses | Out-Null

Write-Host "Setting local.properties..."
"sdk.dir=D:\\aplicativo intercominicador\\android_sdk" | Out-File -FilePath "local.properties" -Encoding utf8

Write-Host "Downloading Gradle 8.9..."
if (-not (Test-Path "gradle-8.9")) {
    curl.exe -L -o gradle.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
    Expand-Archive "gradle.zip" -DestinationPath "." -Force
}
$env:PATH = "$WorkingDir\gradle-8.9\bin;" + $env:PATH

Write-Host "Generating Gradle Wrapper..."
gradle wrapper

Write-Host "Building APK..."
.\gradlew.bat assembleDebug

Write-Host "Done!"
