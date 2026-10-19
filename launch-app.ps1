$sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
$emulatorPath = "$sdkPath\emulator\emulator.exe"
$adbPath = "$sdkPath\platform-tools\adb.exe"
$apkPath = "C:\Users\Besenok\IdeaProjects\untitled\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "========================================="
Write-Host "SmartHome Monitor - Launch Script"
Write-Host "========================================="
Write-Host ""

# Check if APK exists
if (Test-Path $apkPath) {
    Write-Host "[OK] APK found: $apkPath"
    $apkSize = (Get-Item $apkPath).Length / 1MB
    Write-Host "     Size: $([math]::Round($apkSize, 2)) MB"
} else {
    Write-Host "[ERROR] APK not found at: $apkPath"
    Write-Host "Please build the project first with: .\build.ps1 assembleDebug"
    exit 1
}

Write-Host ""

# Launch emulator with home_monitor AVD in background
Write-Host "[1/3] Launching emulator 'home_monitor'..."
Start-Process -FilePath $emulatorPath -ArgumentList "-avd home_monitor -noaudio -no-boot-anim" -WindowStyle Minimized
Write-Host "      Emulator starting..."

# Wait for emulator to boot
Write-Host "[2/3] Waiting for emulator to boot..."
$maxWait = 120
$elapsed = 0
while ($elapsed -lt $maxWait) {
    $status = & $adbPath get-state 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "      Emulator is ready: $status"
        break
    }
    Start-Sleep -Seconds 5
    $elapsed += 5
    Write-Host "      Waiting... ($elapsed/$maxWait sec)"
}

if ($elapsed -ge $maxWait) {
    Write-Host "[WARNING] Emulator may not be fully booted yet"
}

Write-Host ""
Write-Host "[3/3] Installing APK..."
& $adbPath install -r "$apkPath"
if ($LASTEXITCODE -eq 0) {
    Write-Host "      Installation successful!"
} else {
    Write-Host "      Installation failed"
}

Write-Host ""
Write-Host "========================================="
Write-Host "Launch the app manually on the emulator:"
Write-Host "  App: SmartHome Monitor (com.besenok.smarthome)"
Write-Host "========================================="
Write-Host ""
Write-Host "To install and launch in one command, run:"
Write-Host "  & `$adbPath install -r `$apkPath"
Write-Host "  & `$adbPath shell am start -n com.besenok.smarthome/.ui.MainActivity"
