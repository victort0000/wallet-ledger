-- 1. Create Wallets Table
CREATE TABLE IF NOT EXISTS wallets (
                                       id UUID PRIMARY KEY,
                                       player_id VARCHAR(50) NOT NULL UNIQUE,
                                       balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                             CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
    );

-- 2. Create Wallet Transactions Table (Append-Only Ledger)
CREATE TABLE IF NOT EXISTS wallet_transactions (
                                                   id UUID PRIMARY KEY,
                                                   wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL,
    balance_after NUMERIC(18, 4) NOT NULL,
    type VARCHAR(30) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
                                                                                                      );

-- Index for fast paginated ledger lookups per wallet
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_created
    ON wallet_transactions(wallet_id, created_at DESC);

-- 3. Create Idempotency Records Table
CREATE TABLE IF NOT EXISTS idempotency_records (
                                                   key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL, -- IN_PROGRESS, SUCCESS, FAILED
    response_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
                             );
