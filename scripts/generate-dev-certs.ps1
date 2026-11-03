#!/usr/bin/env pwsh
# Generate a development CA plus separate server and client certificates.
#
# Usage (from the repository root):
#   $env:CERT_PASSWORD = "choose-a-password"
#   ./scripts/generate-dev-certs.ps1
#
# Outputs into ./certs:
#   ca.p12 / ca.crt                 development certificate authority
#   server-keystore.p12             server key + certificate chain
#   client-keystore.p12             CLI key + certificate chain (mTLS)
#   truststore.p12                  CA certificate for both sides
#   server.crt / client.crt         exported leaf certificates (inspection only)

param(
    [string]$OutputDir = "certs"
)

$ErrorActionPreference = "Stop"

$Password = $env:CERT_PASSWORD
if ([string]::IsNullOrWhiteSpace($Password) -or $Password.Length -lt 6) {
    throw "Set CERT_PASSWORD to at least 6 characters before running this script."
}

function Find-Keytool {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin/keytool.exe"
        if (Test-Path $candidate) { return $candidate }
        $candidate = Join-Path $env:JAVA_HOME "bin/keytool"
        if (Test-Path $candidate) { return $candidate }
    }

    # `java -XshowSettings` writes to stderr, which Windows PowerShell would
    # otherwise surface as a terminating error.
    $output = & cmd /c "java -XshowSettings:properties -version 2>&1"
    $javaHomeLine = $output | Select-String -Pattern 'java\.home\s*=\s*(.+)$' | Select-Object -First 1
    if ($javaHomeLine) {
        $javaHome = $javaHomeLine.Matches[0].Groups[1].Value.Trim()
        foreach ($name in @("keytool.exe", "keytool")) {
            $candidate = Join-Path $javaHome "bin/$name"
            if (Test-Path $candidate) { return $candidate }
        }
    }

    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    throw "keytool was not found. Install a JDK 21+ or set JAVA_HOME."
}

$Keytool = Find-Keytool
$CertsDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir
} else {
    Join-Path (Get-Location) $OutputDir
}
New-Item -ItemType Directory -Force -Path $CertsDir | Out-Null

$CaStore = Join-Path $CertsDir "ca.p12"
$CaCert = Join-Path $CertsDir "ca.crt"
$ServerStore = Join-Path $CertsDir "server-keystore.p12"
$ClientStore = Join-Path $CertsDir "client-keystore.p12"
$TrustStore = Join-Path $CertsDir "truststore.p12"
$ServerCsr = Join-Path $CertsDir "server.csr"
$ClientCsr = Join-Path $CertsDir "client.csr"
$ServerCert = Join-Path $CertsDir "server.crt"
$ClientCert = Join-Path $CertsDir "client.crt"

# keytool refuses to overwrite existing entries; start from a clean slate.
@($CaStore, $ServerStore, $ClientStore, $TrustStore, $ServerCsr, $ClientCsr, $ServerCert, $ClientCert) |
    ForEach-Object { if (Test-Path $_) { Remove-Item $_ -Force } }

function Invoke-Keytool {
    & $Keytool @args
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
}

Write-Host "Generating development PKI in $CertsDir" -ForegroundColor Green

# 1. Certificate authority
Invoke-Keytool -genkeypair `
    -alias companion-ca `
    -keyalg RSA -keysize 4096 -validity 3650 `
    -dname "CN=Companion Dev CA, OU=Development, O=Companion, C=US" `
    -ext "bc:c" -ext "ku:c=keyCertSign,cRLSign" `
    -storetype PKCS12 -keystore $CaStore `
    -storepass $Password -keypass $Password

Invoke-Keytool -exportcert -alias companion-ca -keystore $CaStore `
    -storepass $Password -rfc -file $CaCert

# 2. Server certificate signed by the CA (SAN matches localhost)
Invoke-Keytool -genkeypair `
    -alias server `
    -keyalg RSA -keysize 2048 -validity 825 `
    -dname "CN=localhost, OU=Development, O=Companion, C=US" `
    -ext "san=dns:localhost,ip:127.0.0.1" `
    -storetype PKCS12 -keystore $ServerStore `
    -storepass $Password -keypass $Password

Invoke-Keytool -certreq -alias server -keystore $ServerStore `
    -storepass $Password -file $ServerCsr

Invoke-Keytool -gencert -alias companion-ca -keystore $CaStore `
    -storepass $Password -infile $ServerCsr -outfile $ServerCert -rfc `
    -validity 825 -ext "san=dns:localhost,ip:127.0.0.1" -ext "eku=serverAuth"

Invoke-Keytool -importcert -alias companion-ca -file $CaCert -keystore $ServerStore `
    -storepass $Password -noprompt
Invoke-Keytool -importcert -alias server -file $ServerCert -keystore $ServerStore `
    -storepass $Password -noprompt

# 3. Client certificate signed by the CA (mTLS)
Invoke-Keytool -genkeypair `
    -alias client `
    -keyalg RSA -keysize 2048 -validity 825 `
    -dname "CN=companion-cli, OU=Development, O=Companion, C=US" `
    -ext "eku=clientAuth" `
    -storetype PKCS12 -keystore $ClientStore `
    -storepass $Password -keypass $Password

Invoke-Keytool -certreq -alias client -keystore $ClientStore `
    -storepass $Password -file $ClientCsr

Invoke-Keytool -gencert -alias companion-ca -keystore $CaStore `
    -storepass $Password -infile $ClientCsr -outfile $ClientCert -rfc `
    -validity 825 -ext "eku=clientAuth"

Invoke-Keytool -importcert -alias companion-ca -file $CaCert -keystore $ClientStore `
    -storepass $Password -noprompt
Invoke-Keytool -importcert -alias client -file $ClientCert -keystore $ClientStore `
    -storepass $Password -noprompt

# 4. Trust store contains only the development CA
Invoke-Keytool -importcert -alias companion-ca -file $CaCert -keystore $TrustStore `
    -storepass $Password -noprompt -storetype PKCS12

Write-Host ""
Write-Host "Development certificates generated." -ForegroundColor Green
Write-Host "  CA:          $CaCert"
Write-Host "  Server:      $ServerStore (alias: server)"
Write-Host "  Client:      $ClientStore (alias: client)"
Write-Host "  Trust store: $TrustStore"
Write-Host ""
Write-Host "Password supplied through CERT_PASSWORD (not printed)."
