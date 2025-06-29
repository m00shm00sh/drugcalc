-- FIXME: triggers will need to update when migrated from SQLite

CREATE TABLE compounds (
    name VARCHAR(64) NOT NULL,
    variant VARCHAR(64) NOT NULL DEFAULT '',
    half_life_secs INTEGER NOT NULL,
    pct_active DOUBLE PRECISION NOT NULL DEFAULT 1,
    note TEXT NOT NULL DEFAULT '',
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (name, variant),
    CONSTRAINT c_name_nonempty CHECK(name <> ''),
    CONSTRAINT c_halflife_range CHECK(half_life_secs > 0),
    CONSTRAINT c_pctactive_range CHECK(pct_active > 0 AND pct_active <= 1)
);
CREATE INDEX compounds_name ON compounds(name);
-- block insert of compound with empty variant if there exists a compound with the same name and a non-empty variant
CREATE TRIGGER compounds_consistent_variants_1 BEFORE INSERT ON compounds
FOR EACH ROW WHEN NEW.variant = ''
BEGIN
    SELECT RAISE(ABORT, 'compound has 1+ variants; specify new variant')
        WHERE EXISTS (SELECT 1 FROM compounds WHERE name=NEW.name AND variant <> '');
END;
-- block insert of compound with non-empty variant if there exists a compound with the same name and an empty variant
CREATE TRIGGER compounds_consistent_variants_2 BEFORE INSERT ON compounds
FOR EACH ROW WHEN NEW.variant <> ''
BEGIN
    SELECT RAISE(ABORT, 'compound has 0 variants; aborting insert')
        WHERE EXISTS (SELECT 1 FROM compounds WHERE name=NEW.name AND variant = '');
END;
-- block update that removes variant if there are already multiple
CREATE TRIGGER compounds_consistent_variants_3 BEFORE UPDATE ON compounds
FOR EACH ROW WHEN NEW.variant = '' AND OLD.variant <> ''
BEGIN
    SELECT RAISE(ABORT, 'cannot remove variant from compound when 2+ variants exist')
        WHERE EXISTS (SELECT 1 FROM compounds WHERE name=NEW.name AND variant <> '' GROUP BY name HAVING COUNT(*) > 1);
END;

-- In upsert or update on `compounds`, set `updated` to NULL so server timestamp replaces value
CREATE TRIGGER compounds_set_mtime AFTER UPDATE ON compounds
FOR EACH ROW WHEN NEW.updated IS NULL
BEGIN
    UPDATE compounds SET updated = CURRENT_TIMESTAMP WHERE updated IS NULL;
END;

CREATE TABLE blends (
    name VARCHAR(64) NOT NULL,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (name),
    CONSTRAINT b_name_nonempty CHECK(name <> '')
);
-- In upsert or update on `blends`, set `updated` to NULL so server timestamp replaces value
CREATE TRIGGER blends_set_mtime AFTER UPDATE ON blends
FOR EACH ROW WHEN NEW.updated IS NULL
BEGIN
    UPDATE blends SET updated = CURRENT_TIMESTAMP WHERE updated IS NULL;
END;

CREATE TABLE blend_components (
    blend_name VARCHAR(64) NOT NULL,
    component_compound VARCHAR(64) NOT NULL,
    component_variant VARCHAR(64) NOT NULL DEFAULT '',
    component_dose DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (blend_name, component_compound, component_variant),
    CONSTRAINT bc_bname_fk FOREIGN KEY (blend_name)
        REFERENCES blends(name) ON DELETE CASCADE,
    CONSTRAINT bc_compname_fk FOREIGN KEY (component_compound, component_variant)
        REFERENCES compounds(name, variant) ON DELETE RESTRICT,
    CONSTRAINT bc_compdose_range CHECK (component_dose > 0)
);
CREATE INDEX bc_name_idx ON blend_components(blend_name);

CREATE TABLE frequencies (
    name VARCHAR(64) NOT NULL,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (name),
    CONSTRAINT f_name_nonempty CHECK(name <> '')
);
-- In upsert or update on `frequencies`, set `updated` to NULL so server timestamp replaces value
CREATE TRIGGER frequencies_set_mtime AFTER UPDATE ON frequencies
FOR EACH ROW WHEN NEW.updated IS NULL
BEGIN
    UPDATE frequencies SET updated = CURRENT_TIMESTAMP WHERE updated IS NULL;
END;

CREATE TABLE frequencies_items (
    freq_name VARCHAR(64) NOT NULL,
    interval_secs INTEGER NOT NULL,
    -- for ordering
    item_index INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (freq_name, interval_secs, item_index)
    CONSTRAINT fi_fname_fk FOREIGN KEY (freq_name)
        REFERENCES frequencies(name) ON DELETE CASCADE,
    CONSTRAINT fi_interval_range CHECK(interval_secs > 0),
    CONSTRAINT fi_index_range CHECK(item_index >= 0)
);
CREATE INDEX fi_name_idx ON frequencies_items(freq_name);
