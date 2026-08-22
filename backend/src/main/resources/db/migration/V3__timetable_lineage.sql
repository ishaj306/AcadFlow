-- =====================================================================
-- Timetable version lineage. When a draft is approved it supersedes the
-- previously-published timetable (which is archived); recording that link and a
-- version number keeps the published history inspectable as an explicit chain
-- rather than a set of independent rows (spec section 20).
-- =====================================================================
ALTER TABLE timetables ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE timetables ADD COLUMN supersedes_id BIGINT;
ALTER TABLE timetables
    ADD CONSTRAINT fk_tt_supersedes FOREIGN KEY (supersedes_id) REFERENCES timetables (id);
