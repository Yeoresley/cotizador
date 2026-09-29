#!/usr/bin/env python3
import argparse
import base64
import json
import time
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

REQ_PREFIX = "ASSI-REQ-1."
LIC_PREFIX = "ASSI-LIC-1."

def b64u_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")

def b64u_decode(value: str) -> bytes:
    value += "=" * ((4 - len(value) % 4) % 4)
    return base64.urlsafe_b64decode(value)

def decode_request(code: str) -> dict:
    code = code.strip()
    if not code.startswith(REQ_PREFIX):
        raise ValueError("Formato de solicitud no reconocido.")
    return json.loads(b64u_decode(code[len(REQ_PREFIX):]).decode("utf-8"))

def main():
    ap = argparse.ArgumentParser(description="Generador administrativo de licencias ASSI")
    ap.add_argument("--request", required=True, help="Código ASSI-REQ-1...")
    ap.add_argument("--private-key", required=True, help="Clave privada EC PKCS#8 PEM")
    ap.add_argument("--license-id", required=True, help="ID único de licencia")
    ap.add_argument("--edition", default="COMERCIAL")
    ap.add_argument("--expires-at", type=int, default=0, help="Epoch seconds; 0 = sin vencimiento")
    ap.add_argument("--updates-until", type=int, default=0, help="Epoch seconds; 0 = sin límite")
    ap.add_argument("--out", default="license.lic")
    args = ap.parse_args()

    req = decode_request(args.request)
    payload = {
        "version": 1,
        "licenseId": args.license_id,
        "customer": req.get("customer", ""),
        "phone": req.get("phone", ""),
        "email": req.get("email", ""),
        "deviceId": req.get("deviceId", ""),
        "edition": args.edition,
        "issuedAtEpochSec": int(time.time()),
        "expiresAtEpochSec": args.expires_at,
        "updatesUntilEpochSec": args.updates_until,
    }
    payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

    key = serialization.load_pem_private_key(
        Path(args.private_key).read_bytes(),
        password=None,
    )
    if not isinstance(key, ec.EllipticCurvePrivateKey):
        raise TypeError("La clave privada debe ser EC P-256.")
    signature = key.sign(payload_bytes, ec.ECDSA(hashes.SHA256()))
    token = LIC_PREFIX + b64u_encode(payload_bytes) + "." + b64u_encode(signature)

    Path(args.out).write_text(token, encoding="utf-8")
    print(token)
    print(f"\nLicencia guardada en: {args.out}")

if __name__ == "__main__":
    main()
