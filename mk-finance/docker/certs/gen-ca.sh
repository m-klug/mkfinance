#!/usr/bin/env bash
#
# Generate an internal CA + leaf cert for MK Finance's Caddy (see ../Caddyfile).
#
# The root CA is created once and reused; only the leaf (mkfinance.crt/key) is
# mounted into Caddy. Trust mkfinance-ca.crt on each device (PC, phone, Proxmox
# host) and the same root validates the site on the PC and on the LXC.
#
#   ./gen-ca.sh
#
# The CA private key (mkfinance-ca.key) must NEVER leave your machine or reach
# git — keep it somewhere safe. Only the leaf key goes on the servers.
set -euo pipefail
cd "$(dirname "$0")"

CA_KEY=mkfinance-ca.key
CA_CRT=mkfinance-ca.crt
LEAF_KEY=mkfinance.key
LEAF_CRT=mkfinance.crt
CA_DAYS=3650   # root CA validity (~10 years)
LEAF_DAYS=398  # leaf validity — iOS/Safari REJECT server certs valid for more than 398 days
               # (Apple, certs issued after 2020-09-01). Rerun this script to renew (~yearly);
               # it reuses the CA, so devices stay trusted — only the leaf is replaced.
HOST=mkfinance.internal

# Names/addresses the leaf cert is valid for. The Proxmox LXC IP is included so
# the site works whether you reach it by hostname or by IP; localhost/127.0.0.1
# cover local testing on the PC.
SAN="DNS:${HOST},IP:192.168.1.247,DNS:localhost,IP:127.0.0.1"

# 1) Root CA — created once, then reused. Regenerating it would invalidate the
#    trust already installed on every device, so we never overwrite it.
if [[ -f "$CA_KEY" && -f "$CA_CRT" ]]; then
  echo "==> Reusing existing CA ($CA_CRT)."
  echo "    (Delete $CA_KEY and $CA_CRT to start over — you'd have to re-trust it everywhere.)"
else
  echo "==> Generating root CA (valid ${CA_DAYS} days)"
  openssl genrsa -out "$CA_KEY" 4096
  # Explicit CA extensions (not relying on the system openssl.cnf) so iOS accepts the root:
  # a critical CA:TRUE and keyCertSign are what strict validators require of a signing CA.
  openssl req -x509 -new -nodes -key "$CA_KEY" -sha256 -days "$CA_DAYS" \
    -subj "/CN=MK Finance Internal CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -addext "subjectKeyIdentifier=hash" \
    -out "$CA_CRT"
fi

# 2) Leaf cert for the site, signed by the CA.
echo "==> Generating leaf cert for ${HOST}"
echo "    SAN: ${SAN}"
openssl genrsa -out "$LEAF_KEY" 2048
openssl req -new -key "$LEAF_KEY" -subj "/CN=${HOST}" -out mkfinance.csr
openssl x509 -req -in mkfinance.csr -CA "$CA_CRT" -CAkey "$CA_KEY" -CAcreateserial \
  -days "$LEAF_DAYS" -sha256 \
  -extfile <(printf "subjectAltName=%s\nbasicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer" "$SAN") \
  -out "$LEAF_CRT"
rm -f mkfinance.csr

echo
echo "Done. Files in $(pwd):"
echo "  $CA_CRT              -> trust this on each device (PC / phone / Proxmox host)"
echo "  $LEAF_CRT / $LEAF_KEY -> served by Caddy (mounted read-only at /etc/caddy/certs)"
echo
echo "Trust the CA:"
echo "  macOS:  sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $CA_CRT"
echo "  Debian: sudo cp $CA_CRT /usr/local/share/ca-certificates/ && sudo update-ca-certificates"
echo "  (Firefox uses its own store — import $CA_CRT under Settings > Certificates too.)"
echo
echo "Then: (cd .. && docker compose up -d) and open https://${HOST} (or https://192.168.1.247)"
