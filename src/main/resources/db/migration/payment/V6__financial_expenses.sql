CREATE TABLE IF NOT EXISTS financial_expenses (
  expense_id VARCHAR(80) PRIMARY KEY,
  expense_date DATE NOT NULL,
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  currency CHAR(3) NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
  category VARCHAR(80) NOT NULL,
  description VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_financial_expenses_date_currency ON financial_expenses(expense_date, currency);
