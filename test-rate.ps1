[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}
$i = 1
while ($i -le 15) {
    $output = (curl.exe -k -s -w "`n%{http_code}" -H "X-Correlation-ID: rate-$i" https://localhost:8443/actuator/health)
    $status = $output[-1]
    Write-Host ("Request {0}: Status={1}" -f $i, $status)
    $i++
}