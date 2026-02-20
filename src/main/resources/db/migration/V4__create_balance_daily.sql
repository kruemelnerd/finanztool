CREATE TABLE balance_daily (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  date DATE NOT NULL,
  balance_cents_end_of_day BIGINT NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EUR',
  computed_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_balance_daily_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT uq_balance_daily UNIQUE (user_id, date)
);
