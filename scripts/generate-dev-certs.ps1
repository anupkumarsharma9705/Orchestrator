#!/usr/bin/env pwsh
# Generate development TLS certificates for orchestrator
# Run from project root: ./scripts/generate-dev-certs.ps1

$ErrorActionPreference = "Stop"

$CertsDir = "orchestrator-server/src/main/resources/certs"
$KeyStore = "$CertsDir/keystore.p12"
$TrustStore = "$CertsDir/truststore.p12"
$Password = "changeit"
$Alias = "orchestrator"
$Dn = "CN=orchestrator-dev, OU=Development, O=Orchestrator, L=Local, ST=Local, C=US"
$Validity = 3650  # 10 years

Write-Host "Generating development TLS certificates..." -ForegroundColor Green
Write-Host "Output directory: $CertsDir"

# Create keystore with key pair
& keytool -genkeypair `
  -alias orchestrator `
  -keyalg RSA `
  -keysize 2048 `
  -storetype PKCS12 `
  -keystore $KeyStore `
  -storepass $Password `
  -keypass $Password `
  -dname "CN=orchestrator-dev, OU=Development, O=Orchestrator, L=Local, ST=Local, C=US" `
  -validity 3650 `
  -ext "SAN=DNS:localhost,IP:127.0.0.1,DNS:host.docker.internal" `
  -deststoretype PKCS12

# Export certificate
& keytool -exportcert `
  -alias orchestrator `
  -keystore $KeyStore `
  -storepass $Password `
  -file "$CertsDir/orchestrator.crt" `
  -rfc

# Create truststore with the certificate
& keytool -importcert `
  -alias orchestrator `
  -file "$CertsDir/orchestrator.crt" `
  -keystore $TrustStore `
  -storepass $Password `
  -noprompt `
  -storetype PKCS12

Write-Host ""
Write-Host "✅ Certificates generated successfully!" -ForegroundColor Green
Write-Host "Keystore:  $KeyStore"
Write-Host "Truststore: $TrustStore"
Write-Host "Password:  $Password"
Write-Host "Alias:     $Alias"
Write-Host ""
Write-Host "Certificate details:"
& keytool -list -v -keystore $KeyStore -storepass $Password -alias $Alias