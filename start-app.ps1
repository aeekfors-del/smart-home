$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "Launching SmartHome Monitor app..."
& $adbPath shell am start -n com.besenok.smarthome/.ui.MainActivity
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] App launched successfully!"
} else {
    Write-Host "[ERROR] Failed to launch app"
}
