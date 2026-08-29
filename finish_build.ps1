$ErrorActionPreference = "Stop"
$WorkingDir = "D:\aplicativo intercominicador"
Set-Location $WorkingDir

$env:JAVA_HOME = "$WorkingDir\jdk-17.0.2"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
$env:ANDROID_HOME = "$WorkingDir\android_sdk"
$env:PATH = "$env:ANDROID_HOME\cmdline-tools\latest\bin;" + $env:PATH
$env:PATH = "$WorkingDir\gradle-8.9\bin;" + $env:PATH

Write-Host "Accepting Licenses..."
$y = "y`n" * 50
$y | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses | Out-Null

Write-Host "Generating Gradle Wrapper..."
gradle wrapper

Write-Host "Building APK..."
.\gradlew.bat assembleDebug
