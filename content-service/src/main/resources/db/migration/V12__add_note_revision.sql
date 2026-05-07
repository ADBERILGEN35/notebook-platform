ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS note_revision bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_notes_note_revision ON notes (note_revision);
