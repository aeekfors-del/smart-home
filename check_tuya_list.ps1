# Check Tuya via /list endpoint
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

# Try GET /list with pattern
$url = "https://10.10.100.14:8087/list?tuya.0"
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
    
    Write-Host "Response length:" $content.Length
    Write-Host "First 1000 chars:"
    Write-Host $content.Substring(0, [Math]::Min(1000, $content.Length))
    
} catch {
    Write-Host "Error:" $_.Exception.Message
}
