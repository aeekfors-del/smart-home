# Check Tuya adapter structure
$url = "https://10.10.100.14:8087/objects?pattern=tuya.0.*"
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

try {
    $request = [System.Net.WebRequest]::Create($url)
    $request.Method = "GET"
    $request.Headers["Authorization"] = "Basic $cred"
    $request.ContentType = "application/json"
    $request.Timeout = 10000
    
    $response = $request.GetResponse()
    $reader = New-Object System.IO.StreamReader $response.GetResponseStream()
    $content = $reader.ReadToEnd()
    $reader.Close()
    
    $json = $content | ConvertFrom-Json
    
    Write-Host "Total objects for tuya.0.*:" $json.Keys.Count
    Write-Host "`nFirst 30 object IDs:"
    $json.Keys | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" }
    
    Write-Host "`nLooking for device patterns..."
    $deviceKeys = $json.Keys | Where-Object { $_ -match 'tuya.0\.devices' -or $_ -match 'tuya\.0\.devices' -or $_ -match 'tuya\.0\.channels' }
    Write-Host "Device/channels objects:" $deviceKeys.Count
    if ($deviceKeys.Count -gt 0) {
        $deviceKeys | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
    }
    
    Write-Host "`nAll tuya.0 top-level keys:"
    $json.Keys | Where-Object { $_ -match '^tuya\.0\.[^\.]+$' } | ForEach-Object { Write-Host "  $_" }
    
} catch {
    Write-Host "Error:" $_.Exception.Message
    if ($_.Exception.InnerException) {
        Write-Host "Inner:" $_.Exception.InnerException.Message
    }
}
