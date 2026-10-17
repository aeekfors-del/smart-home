# Check Tuya using /getPlainValue endpoint like app does
$url = "https://10.10.100.14:8087/getPlainValue?state=true&enum=true&dev&noneditable&withoutsecurity"
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

try {
    $request = [System.Net.WebRequest]::Create($url)
    $request.Method = "GET"
    $request.Headers["Authorization"] = "Basic $cred"
    $request.ContentType = "application/json"
    $request.Timeout = 15000
    
    $response = $request.GetResponse()
    $reader = New-Object System.IO.StreamReader $response.GetResponseStream()
    $content = $reader.ReadToEnd()
    $reader.Close()
    
    Write-Host "Response length:" $content.Length
    Write-Host "`nFirst 2000 chars:"
    Write-Host $content.Substring(0, [Math]::Min(2000, $content.Length))
    
} catch {
    Write-Host "Error:" $_.Exception.Message
    if ($_.Exception.InnerException) {
        Write-Host "Inner:" $_.Exception.InnerException.Message
    }
}
