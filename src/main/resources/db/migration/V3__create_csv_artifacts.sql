CREATE TABLE csv_artifacts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  original_file_name TEXT NOT NULL,
  content_type TEXT,
  bytes BLOB NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL,
  deleted_at TIMESTAMP,
  CONSTRAINT fk_csv_artifacts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_csv_artifacts_user ON csv_artifacts(user_id);
