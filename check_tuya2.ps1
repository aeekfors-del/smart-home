# Check if tuya.0 adapter exists in ioBroker
$url = "https://10.10.100.14:8087/objects?pattern=tuya.0"
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))

Write-Host "Checking: $url"

try {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.ServerCertificateCustomValidationCallback = [System.Net.Http.HttpRequestMessage, System.Net.Security.SslPolicyErrors]::New({ $true })
    
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Basic", $cred)
    $client.DefaultRequestHeaders.Accept.Add(New-Object System.Net.Http.Headers.MediaTypeWithQualityHeaderValue("application/json"))
    
    $response = $client.GetAsync($url).Result
    Write-Host "Status:" $response.StatusCode
    $content = $response.Content.ReadAsStringAsync().Result
    Write-Host "Content length:" $content.Length
    
    if ($content.Length -gt 0) {
        $json = $content | ConvertFrom-Json
        Write-Host "Objects count:" $json.Keys.Count
        if ($json.Keys.Count -gt 0) {
            Write-Host "First few keys:"
            $json.Keys | Select-Object -First 10 | ForEach-Object { Write-Host "  $_" }
        }
    } else {
        Write-Host "Empty response"
    }
} catch {
    Write-Host "Error:" $_.Exception.Message
    if ($_.Exception.InnerException) {
        Write-Host "Inner:" $_.Exception.InnerException.Message
    }
}
