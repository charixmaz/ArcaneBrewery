-- Optional MySQL/MariaDB provisioning shape. Run through an operator-managed migration tool.
-- MySQL DDL implicitly commits; do not execute with the SQLite transactional MigrationRunner.
-- Use InnoDB and utf8mb4. No JDBC MySQL driver is bundled or required by the plugin.
CREATE TABLE schema_migrations (
  version INTEGER PRIMARY KEY,
  checksum VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
) ENGINE=InnoDB;

CREATE TABLE discoveries (
  fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  process_payload MEDIUMBLOB NOT NULL,
  discovered_at VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  temporary_name VARCHAR(80) COLLATE utf8mb4_bin NOT NULL,
  canonical_name VARCHAR(80) COLLATE utf8mb4_bin,
  visibility VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  revision BIGINT NOT NULL CHECK(revision >= 0),
  CHECK(OCTET_LENGTH(process_payload) <= 1048576),
  CHECK(visibility IN ('PRIVATE','SHARED','PUBLIC')),
  INDEX discoveries_owner_idx(owner_id, fingerprint),
  INDEX discoveries_visibility_idx(visibility, fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recipe_grants (
  fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  recipient_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  selection_payload VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin,
  PRIMARY KEY(fingerprint, recipient_id),
  FOREIGN KEY(fingerprint) REFERENCES discoveries(fingerprint) ON DELETE CASCADE,
  CHECK((kind = 'FULL' AND selection_payload IS NULL)
     OR (kind = 'PARTIAL' AND selection_payload IS NOT NULL)),
  INDEX grants_recipient_idx(recipient_id, fingerprint)
) ENGINE=InnoDB;

INSERT INTO schema_migrations(version, checksum) VALUES (1, 'mysql-v1-externally-managed');
