$headers = @{'Accept'='application/json'}
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:TB56GdWl'))
$headers['Authorization'] = 'Basic ' + $cred

# Ignore SSL certificate errors
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls -bor [Net.SecurityProtocolType]::Tls11 -bor [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

try {
    $response = Invoke-WebRequest -Uri 'https://10.10.100.14:8087/objects?pattern=tuya.0' -Headers $headers -UseBasicParsing
    Write-Host "Response status:" $response.StatusCode
    Write-Host "Content length:" $response.Content.Length
    $json = $response.Content | ConvertFrom-Json
    Write-Host "Objects count:" $json.Keys.Count
    if ($json.Keys.Count -gt 0) {
        Write-Host "First few keys:"
        $json.Keys | Select-Object -First 10 | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host "No objects found for tuya.0"
    }
} catch {
    Write-Host "Error:" $_.Exception.Message
}
