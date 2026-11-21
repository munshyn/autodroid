# Builds Autodroid and opens the phone's own installer for it, marked as installed from the Play Store.
# Android Auto only lists the app when the phone's system installer starts the install (a plain
# `adb install` is rejected), so you tap Update on the phone to finish.
#
# Usage (phone on the same Wi-Fi, Wireless debugging on):
#   .\install-to-phone.ps1 -Phone 192.168.1.20:41234
param(
    [Parameter(Mandatory = $true)][string]$Phone
)
$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
Set-Location $PSScriptRoot

Write-Host "Building release..."
& .\gradlew.bat assembleRelease --no-daemon -q
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

Write-Host "Connecting to $Phone..."
& $adb connect $Phone | Out-Host

Write-Host "Copying to the phone..."
& $adb -s $Phone push "app\build\outputs\apk\release\app-release.apk" /sdcard/Download/Autodroid.apk | Out-Host
& $adb -s $Phone shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/Autodroid.apk | Out-Null
Start-Sleep -Seconds 2
$row = & $adb -s $Phone shell "content query --uri content://media/external/file --projection _id --where `"_data LIKE '%/Download/Autodroid.apk'`""
if ($row -notmatch '_id=(\d+)') { throw "Couldn't find the copied APK on the phone: $row" }
$uri = "content://media/external/file/$($Matches[1])"

Write-Host "Opening the installer on the phone. Unlock it and tap Update."
& $adb -s $Phone shell am start -a android.intent.action.VIEW -t application/vnd.android.package-archive -d $uri `
    --grant-read-uri-permission `
    --es android.intent.extra.INSTALLER_PACKAGE_NAME com.android.vending `
    --ez android.intent.extra.NOT_UNKNOWN_SOURCE true | Out-Host

Read-Host "Press Enter after tapping Update on the phone"
& $adb -s $Phone shell "dumpsys package com.husny.autodroid | grep -E 'versionName|installerPackageName|initiatingPackageName'" | Out-Host
& $adb -s $Phone shell rm /sdcard/Download/Autodroid.apk
Write-Host "Android Auto lists Autodroid when it shows installerPackageName=com.android.vending and initiatingPackageName=com.google.android.packageinstaller."
