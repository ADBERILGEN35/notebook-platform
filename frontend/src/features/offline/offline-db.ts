import { openDB } from 'idb'

export const OFFLINE_DB_NAME = 'notebook-offline'
export const OFFLINE_DB_VERSION = 3
export const OFFLINE_NOTES_STORE = 'notes'
export const OFFLINE_DRAFTS_STORE = 'offline_note_drafts'

export function openOfflineDb() {
  return openDB(OFFLINE_DB_NAME, OFFLINE_DB_VERSION, {
    upgrade(db, oldVersion) {
      if (oldVersion < 1) {
        const store = db.createObjectStore(OFFLINE_NOTES_STORE, { keyPath: 'noteId' })
        store.createIndex('cachedAt', 'cachedAt')
      }
      if (oldVersion < 2) {
        if (!db.objectStoreNames.contains(OFFLINE_DRAFTS_STORE)) {
          const drafts = db.createObjectStore(OFFLINE_DRAFTS_STORE, { keyPath: 'noteId' })
          drafts.createIndex('lastEditedAt', 'lastEditedAt')
          drafts.createIndex('status', 'status')
        }
      }
      if (oldVersion < 3) {
        // V3 introduces optional encrypted payload fields in existing stores.
      }
    },
  })
}
