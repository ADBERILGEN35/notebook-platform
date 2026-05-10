import { isOfflineEncryptionEnabled } from '../../shared/config/offline-feature-flags'

type EncryptedJsonPayload = {
  version: 1
  algorithm: 'AES-GCM'
  iv: string
  ciphertext: string
}

let sessionOfflineKey: CryptoKey | null = null

const textEncoder = new TextEncoder()
const textDecoder = new TextDecoder()

function toBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

function fromBase64(raw: string): Uint8Array {
  const binary = atob(raw)
  const out = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i)
  }
  return out
}

export function isOfflineCryptoSupported(): boolean {
  return typeof window !== 'undefined' && Boolean(window.crypto?.subtle)
}

export function hasOfflineEncryptionKey(): boolean {
  return sessionOfflineKey !== null
}

export async function createOfflineEncryptionKey(): Promise<boolean> {
  if (!isOfflineCryptoSupported()) return false
  sessionOfflineKey = await window.crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
  return true
}

export async function ensureOfflineEncryptionKey(): Promise<boolean> {
  if (!isOfflineEncryptionEnabled()) return true
  if (sessionOfflineKey) return true
  return createOfflineEncryptionKey()
}

export function clearOfflineEncryptionKey(): void {
  sessionOfflineKey = null
}

export async function encryptJson(payload: unknown): Promise<EncryptedJsonPayload> {
  if (!sessionOfflineKey) throw new Error('OFFLINE_ENCRYPTION_KEY_UNAVAILABLE')
  const iv = window.crypto.getRandomValues(new Uint8Array(12))
  const plaintext = textEncoder.encode(JSON.stringify(payload))
  const encrypted = await window.crypto.subtle.encrypt({ name: 'AES-GCM', iv }, sessionOfflineKey, plaintext)
  return {
    version: 1,
    algorithm: 'AES-GCM',
    iv: toBase64(iv),
    ciphertext: toBase64(new Uint8Array(encrypted)),
  }
}

export async function decryptJson<T = unknown>(payload: EncryptedJsonPayload): Promise<T> {
  if (!sessionOfflineKey) throw new Error('OFFLINE_ENCRYPTION_KEY_UNAVAILABLE')
  const iv = fromBase64(payload.iv)
  const ciphertext = fromBase64(payload.ciphertext)
  const decrypted = await window.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource },
    sessionOfflineKey,
    ciphertext as BufferSource,
  )
  const raw = textDecoder.decode(new Uint8Array(decrypted))
  return JSON.parse(raw) as T
}

export type { EncryptedJsonPayload }
