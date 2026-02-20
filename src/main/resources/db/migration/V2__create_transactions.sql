CREATE TABLE transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  booking_datetime TIMESTAMP NOT NULL,
  value_date DATE,
  transaction_type TEXT,
  partner_name TEXT NOT NULL,
  purpose_text TEXT NOT NULL,
  raw_booking_text TEXT,
  amount_cents BIGINT NOT NULL,
  currency TEXT NOT NULL DEFAULT 'EUR',
  status TEXT NOT NULL DEFAULT 'Completed',
  deleted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_transactions_user_booking ON transactions(user_id, booking_datetime DESC);
CREATE INDEX idx_transactions_user_amount ON transactions(user_id, amount_cents);
