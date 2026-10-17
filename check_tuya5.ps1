# Check different Tuya patterns
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

$patterns = @(
    "tuya.0",
    "tuya.0.*", 
    "channel.tuya.0",
    "channel.tuya.0.*",
    "device.tuya.0",
    "device.tuya.0.*"
)

foreach ($pattern in $patterns) {
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
        
        if ($json.Keys.Count -gt 0 -and $json.Keys.Count -lt 50) {
            Write-Host "  First 5:"
            $json.Keys | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" }
        }
    } catch {
        Write-Host "$pattern : Error - $($_.Exception.Message)"
    }
}
