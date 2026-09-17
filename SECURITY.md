# PrivateVault — Security Model & Cryptographic Architecture

## 1. Executive Summary

PrivateVault is engineered as a **local-first, zero-knowledge personal digital fortress**. It provides hardware-isolated encryption and confidential storage for documents, photographs, videos, audio recordings, source code, archives, and private notes on Android.

- **Zero Cloud / Zero Telemetry**: Operates strictly offline. No network requests, analytics SDKs, trackers, or AI engines.
- **No Plaintext on Disk**: All imported files are encrypted with authenticated ciphers before writing to permanent storage.
- **Hardware-Backed Cryptography**: Master keys leverage Android KeyStore TEE (Trusted Execution Environment) or StrongBox Keymaster.
- **DoD-Standard Sanitization**: Ephemeral files and deleted files are sanitized using multi-pass zeroization and overwrite.

---

## 2. Threat Model & Security Boundaries

### 2.1 Protected Assets
1. **Payload Contents**: Sensitive documents (PDF, DOCX, etc.), high-resolution images, video/audio recordings, source code, and private notes.
2. **Metadata Confidentiality**: Original filenames, file extensions, creation timestamps, and folder structures are hidden from disk-level inspection.
3. **Master Authentication Credentials**: Master PIN and Biometric keys.

### 2.2 Security Boundary
The security boundary is strictly enforced at the **Authentication + Session Control + Hardware Keystore + Cryptographic Storage Engine** layer. UI screens never handle raw cryptographic keys.

### 2.3 Non-Goals & Limitations
- **Compromised Root / Malicious Kernel**: If the underlying Android OS is rooted with a kernel-level debugger or memory scraper, volatile RAM cannot be completely protected against an attacker with root privileges while the vault is actively unsealed.
- **Camera Capture**: Software protections (`FLAG_SECURE`) prevent on-device screenshots and system task-switcher previews, but cannot prevent physical optical capture via an external camera.

---

## 3. Cryptographic Implementation Details

### 3.1 Ciphers & Parameters
- **Data Encryption**: `AES/GCM/NoPadding` (AES-256 with Galois/Counter Mode).
- **Authentication Tag**: 128-bit authentication tag, strictly verified during every decryption operation.
- **Initialization Vector (IV)**: 12-byte cryptographically secure random nonce generated via `java.security.SecureRandom` per vault object. IVs are never reused.
- **Key Derivation Function (KDF)**: `PBKDF2WithHmacSHA256` with 100,000 iterations and a 256-bit cryptographically random salt.
- **Integrity Checksums**: SHA-256 digest calculated on original plaintext during import and verified on export.

### 3.2 Key Hierarchy
```
User Master PIN / Biometric Hardware
             │
             ▼
    [PBKDF2-HMAC-SHA256]
             │ (100k Iterations, 256-bit Salt)
             ▼
      Derived Key
             │
             ▼
[Android KeyStore Hardware Enclave (AES-256)]
             │
             ▼
      Master Vault Key
             │
             ▼
  Per-File Unique AES-256 Data Key
             │
             ▼
  Encrypted Vault Payload (.vaultobj)
```

---

## 4. Session State Machine

The session security engine (`SessionSecurityManager`) enforces a single authoritative state machine:

```
    ┌──────────────────────┐
    │    UNINITIALIZED     │
    └──────────┬───────────┘
               │ (First-time setup)
               ▼
    ┌──────────────────────┐
    │    SETUP_REQUIRED    │
    └──────────┬───────────┘
               │ (PIN set + salt generated)
               ▼
    ┌──────────────────────┐   Timeout / Manual   ┌──────────────────────┐
    │        LOCKED        │ ◄─────────────────── │       UNLOCKED       │
    └──────────┬───────────┘                      └──────────▲───────────┘
               │                                             │
               │ (PIN / Biometric submit)                    │ Success
               ▼                                             │
    ┌──────────────────────┐                                 │
    │    AUTHENTICATING    │ ────────────────────────────────┘
    └──────────────────────┘
```

- **Fail-Safe Principle**: Any uncaught exception, unexpected state transition, or background process termination automatically defaults to `LOCKED`. It never defaults to `UNLOCKED`.
- **Lock Interruption**: When an auto-lock or manual lock triggers, open viewers, text editors, and media decoders are immediately disposed, and volatile in-memory buffers are zeroed (`Arrays.fill(bytes, 0.toByte())`).

---

## 5. Storage & Privacy Architecture

### 5.1 Physical Storage
- Encrypted payloads are stored in the app's internal storage (`context.filesDir/vault_encrypted/`).
- File names are randomly generated UUIDs (e.g., `8f7b2c1a-4e5d-4a1b-9c3e-2f8a1d5e7b9c.vaultobj`). Original filenames, extensions, and directory paths are never written to the filesystem.
- `android:allowBackup="false"` is set in `AndroidManifest.xml` to prevent unauthorized ADB backup extraction or Google Cloud auto-backup leakage.

### 5.2 Metadata Layer
- Stored in a local Room database (`vault_database.db`).
- Contains user-assigned display names, categories, virtual folder IDs, tags, favorite states, and encrypted relative paths.
- Contains **no plaintext content** and **no encryption keys**.

### 5.3 Temporary File Lifecycle
- Ephemeral streams and previews (e.g., video rendering) are managed through `VaultTempFileManager`.
- Every temporary file is tracked in an active registry.
- Cleanup occurs immediately upon screen exit, activity pause, backgrounding, session lock, or application shutdown.
- Prior to file deletion, data blocks are wiped using `FileShredder` (zeroization pass followed by pseudo-random overwrite).

### 5.4 Screen Privacy
- `WindowManager.LayoutParams.FLAG_SECURE` is active on sensitive activities.
- Prevents screen recordings, screenshots, and exposures in Android's recent applications switcher.

---

## 6. Audit & Accountability

- **Local Security Audit Log**: Records security-relevant events (`AUTH_SUCCESS`, `AUTH_FAILED`, `AUTO_LOCK`, `MASTER_PIN_SETUP`, `FILE_IMPORT`, `FILE_EXPORT`, `ITEM_SHREDDED`, `EMERGENCY_WIPE`, `CONSISTENCY_CHECK`).
- Logs are strictly local and contain zero sensitive payloads or passwords.

---

## 7. Future Security Extensions (Part 2+)

1. **Passphrase-Protected Encrypted Export**: Full vault container export (`.pvault`) encrypted with user-supplied PBKDF2 + AES-GCM for off-device secure cold storage.
2. **FIDO2 / Hardware Security Key**: Support for USB-C / NFC hardware tokens (YubiKey) for physical hardware multi-factor authentication.
3. **Decoy Vault / Duress PIN**: Secondary PIN triggering an isolated, decoy vault partition in coercion scenarios.
