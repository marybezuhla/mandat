#!/usr/bin/env python3
"""Opens a Mandat backup on a computer, without the app (for emergencies and for audits).

    pip install "cryptography>=44"
    python3 decrypt_backup.py archive-2026-01-01.bin out.txt

The password is asked for and never shown. The result is the backup's data as plain text,
readable only by you (file mode 600). Keep it somewhere safe and delete it when done.

Key, for both layouts: Argon2id, 64 MiB, 3 passes, 1 lane, 32 bytes, from the password in
UTF-8 NFC and the 16-byte salt at the start of the file.

A backup made by Mandat 0.3.0 or newer is written in parts:

    salt (16) | Tink salt (32) | nonce prefix (7) | segments
    file key  = HKDF-SHA256(key, salt = Tink salt, info = salt + "mandat-backup-v2"), 32 bytes
    segment i = AES-256-GCM, nonce = prefix | i (4 bytes, big-endian) | 1 if it is the last else 0
    segments are 1 MiB of ciphertext each (tag included), the first one 40 bytes shorter

This is Google Tink's AES-GCM-HKDF streaming layout without its first byte (always 40). Inside
is gzip; unpacked, one JSON value per line: a first line about the copy, then for each table a
line {"t": name, "c": [columns]} followed by one line per row, and {"end": rows} as the last.

A backup made before 0.3.0 is one piece: salt (16) | nonce (12) | AES-256-GCM ciphertext with
tag, with salt and nonce as the associated data. Inside is one JSON document.
"""
import getpass
import gzip
import os
import sys
import unicodedata

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.argon2 import Argon2id
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

KDF = (64 * 1024, 3, 1)  # (memory KiB, passes, lanes); same as the app
LABEL = b"mandat-backup-v2"
SEGMENT = 1 << 20
TINK_HEADER = 40  # its length byte (left out of the file), the 32-byte salt, the 7-byte nonce prefix


def derive(password: str, salt: bytes, kdf=KDF) -> bytes:
    memory_kib, passes, lanes = kdf
    secret = unicodedata.normalize("NFC", password).encode("utf-8")
    return Argon2id(salt=salt, length=32, iterations=passes, lanes=lanes, memory_cost=memory_kib).derive(secret)


def decrypt_parts(data: bytes, key: bytes) -> bytes:
    """A backup written in parts (0.3.0 and newer). Raises if the key is wrong or the file is not whole."""
    salt, tink_salt, prefix = data[:16], data[16:48], data[48:55]
    file_key = HKDF(algorithm=hashes.SHA256(), length=32, salt=tink_salt, info=salt + LABEL).derive(key)
    aead = AESGCM(file_key)
    out = bytearray()
    pos, index = 55, 0
    size = SEGMENT - TINK_HEADER
    while True:
        segment = data[pos:pos + size]
        pos += len(segment)
        last = pos >= len(data)
        nonce = prefix + index.to_bytes(4, "big") + (b"\x01" if last else b"\x00")
        out += aead.decrypt(nonce, segment, None)
        if last:
            break
        index += 1
        size = SEGMENT
    return gzip.decompress(bytes(out))


def decrypt_one_piece(data: bytes, key: bytes) -> bytes:
    """A backup made before 0.3.0."""
    return AESGCM(key).decrypt(data[16:28], data[28:], data[:28])


def decrypt(data: bytes, password: str, kdf=KDF) -> bytes:
    if len(data) < 16 + 12 + 16 or len(data) > 1 << 30:
        raise SystemExit("Not a Mandat backup")
    key = derive(password, data[:16], kdf)
    for layout in (decrypt_parts, decrypt_one_piece):
        try:
            return layout(data, key)
        except Exception:
            continue
    raise SystemExit("Wrong password, or the file is damaged")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    with open(sys.argv[1], "rb") as f:
        data = f.read()
    plain = decrypt(data, getpass.getpass("Password: "))
    # A new file only: an existing one (or a link planted in its place) is never overwritten
    try:
        fd = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
    except FileExistsError:
        raise SystemExit("The output file already exists; choose a new name.")
    with os.fdopen(fd, "wb") as f:
        f.write(plain)
    print("Done:", sys.argv[2])


if __name__ == "__main__":
    main()
