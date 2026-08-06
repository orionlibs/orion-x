CREATE TABLE things (
    id                   UUID PRIMARY KEY,
    name                 TEXT NOT NULL,
    description          TEXT NOT NULL,
    thing_type           TEXT NOT NULL CHECK (thing_type IN ('device', 'sensor', 'gateway', 'actuator', 'object')),
    parent_id            UUID REFERENCES things(id) ON DELETE CASCADE,
    uns_topic            TEXT NOT NULL UNIQUE, -- e.g. 'site1/area2/line3/thing4'
    status               TEXT NOT NULL DEFAULT 'inactive' CHECK (status IN ('active', 'inactive', 'offline', 'decommissioned')),
    registration_method  TEXT NOT NULL CHECK (registration_method IN ('manual', 'mqtt_auto')),
    mqtt_client_id       TEXT UNIQUE,
    metadata             JSONB NOT NULL DEFAULT '{}',
    last_seen_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_things_parent_id  ON things (parent_id);
CREATE INDEX idx_things_uns_topic  ON things (uns_topic);
CREATE INDEX idx_things_metadata   ON things USING GIN (metadata);
