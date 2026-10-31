-- PostgreSQL schema.
-- Trigger definitions live in V003__Add_triggers.sql so that jOOQ's DDL parser can read this file.

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

CREATE TABLE blends (
    name VARCHAR(64) NOT NULL,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (name),
    CONSTRAINT b_name_nonempty CHECK(name <> '')
);

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

CREATE TABLE frequencies_items (
    freq_name VARCHAR(64) NOT NULL,
    interval_secs INTEGER NOT NULL,
    -- for ordering
    item_index INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (freq_name, interval_secs, item_index),
    CONSTRAINT fi_fname_fk FOREIGN KEY (freq_name)
        REFERENCES frequencies(name) ON DELETE CASCADE,
    CONSTRAINT fi_interval_range CHECK(interval_secs > 0),
    CONSTRAINT fi_index_range CHECK(item_index >= 0)
);
CREATE INDEX fi_name_idx ON frequencies_items(freq_name);
