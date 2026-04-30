-- Opt-in only. Validate with integration tests and a non-owner runtime role before production use.
ALTER TABLE notes FORCE ROW LEVEL SECURITY;
ALTER TABLE note_versions FORCE ROW LEVEL SECURITY;
ALTER TABLE note_links FORCE ROW LEVEL SECURITY;
ALTER TABLE comments FORCE ROW LEVEL SECURITY;
ALTER TABLE note_tags FORCE ROW LEVEL SECURITY;
