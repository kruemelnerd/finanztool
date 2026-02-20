package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__NormalizeCategoryBooleanColumns extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = OFF");

      statement.execute("""
          CREATE TABLE categories_new (
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
            CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories_new(id)
          )
          """);

      statement.execute("""
          CREATE TABLE rules_new (
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
            CONSTRAINT fk_rules_category FOREIGN KEY (category_id) REFERENCES categories_new(id)
          )
          """);

      statement.execute("""
          CREATE TABLE transactions_new (
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
            payer_name TEXT,
            booking_text TEXT,
            card_number TEXT,
            card_payment_text TEXT,
            reference_text TEXT,
            category_id INTEGER,
            category_assigned_by TEXT CHECK (category_assigned_by IN ('DEFAULT', 'RULE', 'MANUAL')),
            category_locked BOOLEAN NOT NULL DEFAULT 0,
            rule_conflicts TEXT,
            CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id),
            CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories_new(id)
          )
          """);

      statement.execute("""
          INSERT INTO categories_new (
            id,
            user_id,
            parent_id,
            name,
            sort_order,
            is_default,
            is_system,
            deleted_at,
            created_at
          )
          SELECT
            id,
            user_id,
            parent_id,
            name,
            sort_order,
            CASE WHEN is_default IS NULL THEN 0 ELSE is_default END,
            CASE WHEN is_system IS NULL THEN 0 ELSE is_system END,
            deleted_at,
            created_at
          FROM categories
          """);

      statement.execute("""
          INSERT INTO rules_new (
            id,
            user_id,
            name,
            match_text,
            match_field,
            category_id,
            is_active,
            sort_order,
            last_run_at,
            last_match_count,
            deleted_at,
            created_at
          )
          SELECT
            id,
            user_id,
            name,
            match_text,
            match_field,
            category_id,
            CASE WHEN is_active IS NULL THEN 1 ELSE is_active END,
            sort_order,
            last_run_at,
            last_match_count,
            deleted_at,
            created_at
          FROM rules
          """);

      statement.execute("""
          INSERT INTO transactions_new (
            id,
            user_id,
            booking_datetime,
            value_date,
            transaction_type,
            partner_name,
            purpose_text,
            raw_booking_text,
            amount_cents,
            currency,
            status,
            deleted_at,
            created_at,
            payer_name,
            booking_text,
            card_number,
            card_payment_text,
            reference_text,
            category_id,
            category_assigned_by,
            category_locked,
            rule_conflicts
          )
          SELECT
            id,
            user_id,
            booking_datetime,
            value_date,
            transaction_type,
            partner_name,
            purpose_text,
            raw_booking_text,
            amount_cents,
            currency,
            status,
            deleted_at,
            created_at,
            payer_name,
            booking_text,
            card_number,
            card_payment_text,
            reference_text,
            category_id,
            category_assigned_by,
            CASE WHEN category_locked IS NULL THEN 0 ELSE category_locked END,
            rule_conflicts
          FROM transactions
          """);

      statement.execute("DROP TABLE transactions");
      statement.execute("DROP TABLE rules");
      statement.execute("DROP TABLE categories");

      statement.execute("ALTER TABLE categories_new RENAME TO categories");
      statement.execute("ALTER TABLE rules_new RENAME TO rules");
      statement.execute("ALTER TABLE transactions_new RENAME TO transactions");

      statement.execute("CREATE INDEX idx_categories_user_parent_sort ON categories(user_id, parent_id, sort_order, id)");
      statement.execute("CREATE INDEX idx_categories_user_active ON categories(user_id, deleted_at)");
      statement.execute(
          "CREATE UNIQUE INDEX idx_categories_unique_active_name_per_parent ON categories(user_id, COALESCE(parent_id, 0), lower(name)) WHERE deleted_at IS NULL");

      statement.execute("CREATE INDEX idx_rules_user_sort ON rules(user_id, sort_order, id)");
      statement.execute("CREATE INDEX idx_rules_user_active ON rules(user_id, is_active, deleted_at)");
      statement.execute(
          "CREATE UNIQUE INDEX idx_rules_unique_active_name ON rules(user_id, lower(name)) WHERE deleted_at IS NULL");

      statement.execute("CREATE INDEX idx_transactions_user_booking ON transactions(user_id, booking_datetime DESC)");
      statement.execute("CREATE INDEX idx_transactions_user_amount ON transactions(user_id, amount_cents)");
      statement.execute("CREATE INDEX idx_transactions_user_category ON transactions(user_id, category_id)");
      statement.execute("CREATE INDEX idx_transactions_user_assigned_by ON transactions(user_id, category_assigned_by)");

      statement.execute("PRAGMA foreign_keys = ON");
    }
  }
}
