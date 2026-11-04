CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    currency_code TEXT NOT NULL,
    currency_scale INTEGER NOT NULL DEFAULT 2,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    invite_token TEXT NOT NULL UNIQUE,
    is_currency_locked INTEGER NOT NULL DEFAULT 0,
    is_read_only INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS members (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'MEMBER',
    status TEXT NOT NULL DEFAULT 'ACTIVE_UNCLAIMED',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS expenses (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    payer_member_id TEXT NOT NULL REFERENCES members(id),
    created_by_member_id TEXT NOT NULL REFERENCES members(id),
    expense_date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    note TEXT,
    split_type TEXT NOT NULL,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    deleted_at INTEGER,
    deleted_by_member_id TEXT
);

CREATE TABLE IF NOT EXISTS expense_splits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    expense_id TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    member_id TEXT NOT NULL REFERENCES members(id),
    amount_minor INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS settlements (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    payer_member_id TEXT NOT NULL REFERENCES members(id),
    recipient_member_id TEXT NOT NULL REFERENCES members(id),
    amount_minor INTEGER NOT NULL,
    settlement_date INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    recorded_by_member_id TEXT NOT NULL,
    note TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    deleted_at INTEGER,
    deleted_by_member_id TEXT
);

CREATE TABLE IF NOT EXISTS activity_history (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    actor_member_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    summary TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS member_claims (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    member_id TEXT NOT NULL REFERENCES members(id),
    claimant_device_id TEXT NOT NULL,
    sas_code TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_operations (
    idempotency_key TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    error_code TEXT,
    error_message TEXT,
    created_at INTEGER NOT NULL
);

