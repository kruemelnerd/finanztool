CREATE TABLE rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  match_text TEXT NOT NULL,
  match_field TEXT NOT NULL CHECK (match_field IN ('BOOKING_TEXT', 'PARTNER_NAME', 'BOTH')),
  category_id INTEGER NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT 1,
  sort_order INTEGER NOT NULL DEFAULT 0,
  last_run_at TIMESTAMP,
  last_match_count INTEGER NOT NULL DEFAULT 0,
  deleted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_rules_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_rules_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_rules_user_sort ON rules(user_id, sort_order, id);
CREATE INDEX idx_rules_user_active ON rules(user_id, is_active, deleted_at);
CREATE UNIQUE INDEX idx_rules_unique_active_name
  ON rules(user_id, lower(name))
  WHERE deleted_at IS NULL;
