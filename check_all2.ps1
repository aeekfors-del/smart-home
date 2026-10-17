# Check using POST to /objects/list
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

# Try GET /objects without pattern to see what we get
$url = "https://10.10.100.14:8087/objects"
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
    
    Write-Host "GET /objects response length:" $content.Length
    
    if ($content.Length -gt 100) {
        $json = $content | ConvertFrom-Json
        Write-Host "Total objects:" $json.Keys.Count
        
        # Count by prefix
        $prefixes = @{}
        foreach ($key in $json.Keys) {
            $parts = $key.Split('.')
            $prefix = $parts[0] + '.' + $parts[1]
            if (-not $prefixes.ContainsKey($prefix)) {
                $prefixes[$prefix] = 0
            }
            $prefixes[$prefix]++
        }
        
        Write-Host "`nObjects by adapter:"
        $prefixes.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
            Write-Host "  $($_.Key) : $($_.Value)"
        }
    }
} catch {
    Write-Host "Error:" $_.Exception.Message
}
