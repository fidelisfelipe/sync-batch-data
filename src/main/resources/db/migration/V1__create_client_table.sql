-- CLIENT table: core entity for bidirectional sync
-- Compatible with H2, MySQL, MSSQL, Oracle, DB2

CREATE TABLE IF NOT EXISTS CLIENT (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    document     VARCHAR(50),
    last_updated TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source       VARCHAR(100) NOT NULL DEFAULT 'local',
    CONSTRAINT pk_client PRIMARY KEY (id),
    CONSTRAINT uq_client_email UNIQUE (email)
);

-- Index to speed up incremental sync queries
CREATE INDEX IF NOT EXISTS idx_client_last_updated ON CLIENT (last_updated);
CREATE INDEX IF NOT EXISTS idx_client_source ON CLIENT (source);
CREATE INDEX IF NOT EXISTS idx_client_source_updated ON CLIENT (source, last_updated);

-- Seed data for development / smoke tests
INSERT INTO CLIENT (name, email, document, last_updated, source)
VALUES
    ('Alice Souza',  'alice@example.com',  '111.111.111-11', DATEADD('DAY', -10, CURRENT_TIMESTAMP), 'local'),
    ('Bruno Lima',   'bruno@example.com',  '222.222.222-22', DATEADD('DAY', -5,  CURRENT_TIMESTAMP), 'local'),
    ('Carla Mendes', 'carla@example.com',  '333.333.333-33', DATEADD('DAY', -1,  CURRENT_TIMESTAMP), 'source1');
