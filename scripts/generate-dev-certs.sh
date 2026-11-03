#!/usr/bin/env bash
# Generate a development CA plus separate server and client certificates.
#
# Usage (from the repository root):
#   CERT_PASSWORD=choose-a-password ./scripts/generate-dev-certs.sh
#
# Outputs into ./certs (override with CERT_OUTPUT_DIR):
#   ca.p12 / ca.crt                 development certificate authority
#   server-keystore.p12             server key + certificate chain
#   client-keystore.p12             CLI key + certificate chain (mTLS)
#   truststore.p12                  CA certificate for both sides
#   server.crt / client.crt         exported leaf certificates (inspection only)

set -euo pipefail

OUTPUT_DIR="${CERT_OUTPUT_DIR:-certs}"
PASSWORD="${CERT_PASSWORD:-}"

if [ "${#PASSWORD}" -lt 6 ]; then
  echo "ERROR: set CERT_PASSWORD to at least 6 characters." >&2
  exit 1
fi

find_keytool() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    echo "$JAVA_HOME/bin/keytool"
    return
  fi
  if command -v keytool >/dev/null 2>&1; then
    command -v keytool
    return
  fi
  local java_home
  java_home="$(java -XshowSettings:properties -version 2>&1 \
    | sed -n 's/^[[:space:]]*java\.home = //p' | head -n 1)"
  if [ -n "$java_home" ] && [ -x "$java_home/bin/keytool" ]; then
    echo "$java_home/bin/keytool"
    return
  fi
  echo "ERROR: keytool was not found. Install a JDK 21+ or set JAVA_HOME." >&2
  exit 1
}

KEYTOOL="$(find_keytool)"
case "$OUTPUT_DIR" in
  /*) CERTS_DIR="$OUTPUT_DIR" ;;
  *)  CERTS_DIR="$(pwd)/$OUTPUT_DIR" ;;
esac
mkdir -p "$CERTS_DIR"

CA_STORE="$CERTS_DIR/ca.p12"
CA_CERT="$CERTS_DIR/ca.crt"
SERVER_STORE="$CERTS_DIR/server-keystore.p12"
CLIENT_STORE="$CERTS_DIR/client-keystore.p12"
TRUST_STORE="$CERTS_DIR/truststore.p12"
SERVER_CSR="$CERTS_DIR/server.csr"
CLIENT_CSR="$CERTS_DIR/client.csr"
SERVER_CERT="$CERTS_DIR/server.crt"
CLIENT_CERT="$CERTS_DIR/client.crt"

rm -f "$CA_STORE" "$SERVER_STORE" "$CLIENT_STORE" "$TRUST_STORE" \
      "$SERVER_CSR" "$CLIENT_CSR" "$SERVER_CERT" "$CLIENT_CERT"

echo "Generating development PKI in $CERTS_DIR"

# 1. Certificate authority
"$KEYTOOL" -genkeypair \
  -alias companion-ca \
  -keyalg RSA -keysize 4096 -validity 3650 \
  -dname "CN=Companion Dev CA, OU=Development, O=Companion, C=US" \
  -ext "bc:c" -ext "ku:c=keyCertSign,cRLSign" \
  -storetype PKCS12 -keystore "$CA_STORE" \
  -storepass "$PASSWORD" -keypass "$PASSWORD"

"$KEYTOOL" -exportcert -alias companion-ca -keystore "$CA_STORE" \
  -storepass "$PASSWORD" -rfc -file "$CA_CERT"

# 2. Server certificate signed by the CA (SAN matches localhost)
"$KEYTOOL" -genkeypair \
  -alias server \
  -keyalg RSA -keysize 2048 -validity 825 \
  -dname "CN=localhost, OU=Development, O=Companion, C=US" \
  -ext "san=dns:localhost,ip:127.0.0.1" \
  -storetype PKCS12 -keystore "$SERVER_STORE" \
  -storepass "$PASSWORD" -keypass "$PASSWORD"

"$KEYTOOL" -certreq -alias server -keystore "$SERVER_STORE" \
  -storepass "$PASSWORD" -file "$SERVER_CSR"

"$KEYTOOL" -gencert -alias companion-ca -keystore "$CA_STORE" \
  -storepass "$PASSWORD" -infile "$SERVER_CSR" -outfile "$SERVER_CERT" -rfc \
  -validity 825 -ext "san=dns:localhost,ip:127.0.0.1" -ext "eku=serverAuth"

"$KEYTOOL" -importcert -alias companion-ca -file "$CA_CERT" -keystore "$SERVER_STORE" \
  -storepass "$PASSWORD" -noprompt
"$KEYTOOL" -importcert -alias server -file "$SERVER_CERT" -keystore "$SERVER_STORE" \
  -storepass "$PASSWORD" -noprompt

# 3. Client certificate signed by the CA (mTLS)
"$KEYTOOL" -genkeypair \
  -alias client \
  -keyalg RSA -keysize 2048 -validity 825 \
  -dname "CN=companion-cli, OU=Development, O=Companion, C=US" \
  -ext "eku=clientAuth" \
  -storetype PKCS12 -keystore "$CLIENT_STORE" \
  -storepass "$PASSWORD" -keypass "$PASSWORD"

"$KEYTOOL" -certreq -alias client -keystore "$CLIENT_STORE" \
  -storepass "$PASSWORD" -file "$CLIENT_CSR"

"$KEYTOOL" -gencert -alias companion-ca -keystore "$CA_STORE" \
  -storepass "$PASSWORD" -infile "$CLIENT_CSR" -outfile "$CLIENT_CERT" -rfc \
  -validity 825 -ext "eku=clientAuth"

"$KEYTOOL" -importcert -alias companion-ca -file "$CA_CERT" -keystore "$CLIENT_STORE" \
  -storepass "$PASSWORD" -noprompt
"$KEYTOOL" -importcert -alias client -file "$CLIENT_CERT" -keystore "$CLIENT_STORE" \
  -storepass "$PASSWORD" -noprompt

# 4. Trust store contains only the development CA
"$KEYTOOL" -importcert -alias companion-ca -file "$CA_CERT" -keystore "$TRUST_STORE" \
  -storepass "$PASSWORD" -noprompt -storetype PKCS12

# Best-effort restriction of private key material on POSIX systems
chmod 600 "$CA_STORE" "$SERVER_STORE" "$CLIENT_STORE" 2>/dev/null || true
chmod 644 "$CA_CERT" "$SERVER_CERT" "$CLIENT_CERT" "$TRUST_STORE" 2>/dev/null || true

echo
echo "Development certificates generated."
echo "  CA:          $CA_CERT"
echo "  Server:      $SERVER_STORE (alias: server)"
echo "  Client:      $CLIENT_STORE (alias: client)"
echo "  Trust store: $TRUST_STORE"
echo
echo "Password supplied through CERT_PASSWORD (not printed)."
