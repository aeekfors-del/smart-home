$emulatorPath = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"

if (Test-Path $emulatorPath) {
    Write-Host "Emulator found: $emulatorPath"
    Write-Host "`nAvailable AVDs:"
    & $emulatorPath -list-avds
} else {
    Write-Host "Emulator not found at: $emulatorPath"
    Write-Host "`nSearching for emulator..."
    Get-ChildItem -Path "$env:LOCALAPPDATA\Android" -Recurse -Filter "emulator.exe" -ErrorAction SilentlyContinue | Select-Object FullName
}
