ALTER TABLE transactions ADD COLUMN category_id INTEGER REFERENCES categories(id);
ALTER TABLE transactions ADD COLUMN category_assigned_by TEXT CHECK (category_assigned_by IN ('DEFAULT', 'RULE', 'MANUAL'));
ALTER TABLE transactions ADD COLUMN category_locked BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN rule_conflicts TEXT;

CREATE INDEX idx_transactions_user_category ON transactions(user_id, category_id);
CREATE INDEX idx_transactions_user_assigned_by ON transactions(user_id, category_assigned_by);
