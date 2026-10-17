# Check all adapters and xiaomi structure for comparison
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

Write-Host "=== All adapters ==="
$url = "https://10.10.100.14:8087/objects?pattern=adapter.*"
$request = [System.Net.WebRequest]::Create($url)
$request.Method = "GET"
$request.Headers["Authorization"] = "Basic $cred"
$request.ContentType = "application/json"
$response = $request.GetResponse()
$reader = New-Object System.IO.StreamReader $response.GetResponseStream()
$content = $reader.ReadToEnd()
$reader.Close()
$json = $content | ConvertFrom-Json
Write-Host "Adapters found:" $json.Keys.Count
$json.Keys | Where-Object { $_ -match '^adapter\.' } | ForEach-Object { Write-Host "  $_" }

Write-Host "`n=== Xiaomi structure ==="
$xiaomi_patterns = @("mihome.0", "mihome.0.*", "mihome.0.devices", "mihome.0.devices.*")
foreach ($pattern in $xiaomi_patterns) {
    $url = "https://10.10.100.14:8087/objects?pattern=$pattern"
    try {
        $request = [System.Net.WebRequest]::Create($url)
        $request.Method = "GET"
        $request.Headers["Authorization"] = "Basic $cred"
        $request.ContentType = "application/json"
        $request.Timeout = 5000
        $response = $request.GetResponse()
        $reader = New-Object System.IO.StreamReader $response.GetResponseStream()
        $content = $reader.ReadToEnd()
        $reader.Close()
        $json = $content | ConvertFrom-Json
        Write-Host "$pattern : $($json.Keys.Count) objects"
    } catch {
        Write-Host "$pattern : Error"
    }
}

Write-Host "`n=== Tuya structure ==="
$tuya_patterns = @("tuya.0", "tuya.0.*", "tuya.0.devices", "tuya.0.devices.*")
foreach ($pattern in $tuya_patterns) {
    $url = "https://10.10.100.14:8087/objects?pattern=$pattern"
    try {
        $request = [System.Net.WebRequest]::Create($url)
        $request.Method = "GET"
        $request.Headers["Authorization"] = "Basic $cred"
        $request.ContentType = "application/json"
        $request.Timeout = 5000
        $response = $request.GetResponse()
        $reader = New-Object System.IO.StreamReader $response.GetResponseStream()
        $content = $reader.ReadToEnd()
        $reader.Close()
        $json = $content | ConvertFrom-Json
        Write-Host "$pattern : $($json.Keys.Count) objects"
    } catch {
        Write-Host "$pattern : Error"
    }
}

Write-Host "`n=== Check if Tuya adapter object exists ==="
$url = "https://10.10.100.14:8087/objects/tuya.0"
try {
    $request = [System.Net.WebRequest]::Create($url)
    $request.Method = "GET"
    $request.Headers["Authorization"] = "Basic $cred"
    $request.ContentType = "application/json"
    $request.Timeout = 5000
    $response = $request.GetResponse()
    $reader = New-Object System.IO.StreamReader $response.GetResponseStream()
    $content = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "tuya.0 object EXISTS"
    $json = $content | ConvertFrom-Json
    Write-Host "Object type:" $json.type
    Write-Host "Object common.name:" $json.common.name
} catch {
    Write-Host "tuya.0 object NOT found: $($_.Exception.Message)"
}
