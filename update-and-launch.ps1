$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apkPath = "C:\Users\Besenok\IdeaProjects\untitled\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "Installing APK..."
& $adbPath install -r $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Installation successful!"
    Write-Host "Launching app..."
    & $adbPath shell am start -n com.besenok.smarthome/.ui.MainActivity
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] App launched!"
    } else {
        Write-Host "[ERROR] Failed to launch app"
    }
} else {
    Write-Host "[ERROR] Installation failed"
}
