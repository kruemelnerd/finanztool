CREATE TABLE categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  parent_id INTEGER,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_default BOOLEAN NOT NULL DEFAULT 0,
  is_system BOOLEAN NOT NULL DEFAULT 0,
  deleted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE INDEX idx_categories_user_parent_sort ON categories(user_id, parent_id, sort_order, id);
CREATE INDEX idx_categories_user_active ON categories(user_id, deleted_at);
CREATE UNIQUE INDEX idx_categories_unique_active_name_per_parent
  ON categories(user_id, COALESCE(parent_id, 0), lower(name))
  WHERE deleted_at IS NULL;
