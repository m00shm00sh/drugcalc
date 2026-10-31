-- PostgreSQL trigger definitions.
-- These are kept separate from V001 because jOOQ's DDL parser cannot read procedural code.

-- Block insert of a compound with an empty variant if a compound with the same name and a
-- non-empty variant already exists.
CREATE OR REPLACE FUNCTION compounds_consistent_variants_insert_empty() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM compounds WHERE name = NEW.name AND variant <> '') THEN
        RAISE EXCEPTION 'compound has 1+ variants; specify new variant';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER compounds_consistent_variants_1
    BEFORE INSERT ON compounds
    FOR EACH ROW
    WHEN (NEW.variant = '')
    EXECUTE FUNCTION compounds_consistent_variants_insert_empty();

-- Block insert of a compound with a non-empty variant if a compound with the same name and an
-- empty variant already exists.
CREATE OR REPLACE FUNCTION compounds_consistent_variants_insert_nonempty() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM compounds WHERE name = NEW.name AND variant = '') THEN
        RAISE EXCEPTION 'compound has 0 variants; aborting insert';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER compounds_consistent_variants_2
    BEFORE INSERT ON compounds
    FOR EACH ROW
    WHEN (NEW.variant <> '')
    EXECUTE FUNCTION compounds_consistent_variants_insert_nonempty();

-- Block an update that removes the variant when 2+ variants already exist.
CREATE OR REPLACE FUNCTION compounds_consistent_variants_update() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM compounds
        WHERE name = NEW.name AND variant <> ''
        GROUP BY name
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'cannot remove variant from compound when 2+ variants exist';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER compounds_consistent_variants_3
    BEFORE UPDATE ON compounds
    FOR EACH ROW
    WHEN (NEW.variant = '' AND OLD.variant <> '')
    EXECUTE FUNCTION compounds_consistent_variants_update();

-- Set `updated` to the server timestamp when it is left NULL, so upserts that explicitly null the
-- column are still audited. A BEFORE trigger avoids the self-update recursion an AFTER trigger
-- would cause in PostgreSQL.
CREATE OR REPLACE FUNCTION set_updated_mtime() RETURNS trigger AS $$
BEGIN
    IF NEW.updated IS NULL THEN
        NEW.updated := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER compounds_set_mtime
    BEFORE UPDATE ON compounds
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_mtime();

CREATE TRIGGER blends_set_mtime
    BEFORE UPDATE ON blends
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_mtime();

CREATE TRIGGER frequencies_set_mtime
    BEFORE UPDATE ON frequencies
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_mtime();
