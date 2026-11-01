#!/bin/bash
# Generate development TLS certificates for orchestrator
# Run from project root: ./scripts/generate-dev-certs.sh

set -e

CERTS_DIR="orchestrator-server/src/main/resources/certs"
KEYSTORE="$CERTS_DIR/keystore.p12"
TRUSTSTORE="$CERTS_DIR/truststore.p12"
PASSWORD="changeit"
ALIAS="orchestrator"
DN="CN=orchestrator-dev, OU=Development, O=Orchestrator, L=Local, ST=Local, C=US"
VALIDITY=3650  # 10 years

echo "Generating development TLS certificates..."
echo "Output directory: $CERTS_DIR"

# Create keystore with key pair
keytool -genkeypair \
  -alias orchestrator \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "$DN" \
  -validity 3650 \
  -ext "SAN=DNS:localhost,IP:127.0.0.1,DNS:host.docker.internal" \
  -deststoretype PKCS12

# Export certificate
keytool -exportcert \
  -alias orchestrator \
  -keystore "$KEYSTORE" \
  -storepass "$PASSWORD" \
  -file "$CERTS_DIR/orchestrator.crt" \
  -rfc

# Create truststore with the certificate
keytool -importcert \
  -alias orchestrator \
  -file "$CERTS_DIR/orchestrator.crt" \
  -keystore "$TRUSTSTORE" \
  -storepass "$PASSWORD" \
  -noprompt \
  -storetype PKCS12

# Also add to system cacerts for Java (optional, for client trust)
# keytool -importcert -alias orchestrator -file "$CERTS_DIR/orchestrator.crt" -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit -noprompt

echo ""
echo "✅ Certificates generated successfully!"
echo "Keystore:  $KEYSTORE"
echo "Truststore: $TRUSTSTORE"
echo "Password:  $PASSWORD"
echo "Alias:     $ALIAS"
echo ""
echo "Certificate details:"
keytool -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS"