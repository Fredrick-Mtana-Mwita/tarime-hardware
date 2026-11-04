CREATE TABLE app.staff_accounts (
 id UUID PRIMARY KEY,
 username VARCHAR(60) NOT NULL UNIQUE CHECK(username ~ '^[a-z0-9][a-z0-9._-]{2,59}$'),
 name VARCHAR(100) NOT NULL,
 role VARCHAR(16) NOT NULL CHECK(role IN ('OWNER','SALES','INVENTORY','DELIVERY')),
 password_hash VARCHAR(512) NOT NULL,
 password_change_required BOOLEAN NOT NULL DEFAULT TRUE,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 session_version BIGINT NOT NULL DEFAULT 0 CHECK(session_version>=0),
 mfa_secret_encrypted TEXT,
 mfa_last_step BIGINT NOT NULL DEFAULT -1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(role<>'OWNER' OR mfa_secret_encrypted IS NOT NULL)
);
-- One-time recovery codes are never stored in clear text; activation requires an approved policy.
CREATE TABLE app.staff_recovery_codes (
 staff_id UUID NOT NULL REFERENCES app.staff_accounts(id),
 code_hash CHAR(64) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 used_at TIMESTAMPTZ,
 PRIMARY KEY(staff_id,code_hash)
);
CREATE TABLE app.staff_order_notices (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL UNIQUE REFERENCES app.customer_orders(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE app.staff_order_notice_reads (
 staff_id UUID NOT NULL REFERENCES app.staff_accounts(id),
 notice_id UUID NOT NULL REFERENCES app.staff_order_notices(id),
 read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(staff_id,notice_id)
);
-- Existing requests must also be visible to the first named reviewers.
INSERT INTO app.staff_order_notices(id,order_id,created_at)
 SELECT id,id,created_at FROM app.customer_orders WHERE status='PENDING';
CREATE INDEX staff_order_notices_time_idx ON app.staff_order_notices(created_at DESC,id DESC);
CREATE INDEX orders_pending_review_idx ON app.customer_orders(created_at,id) WHERE status='PENDING';
COMMENT ON TABLE app.staff_accounts IS 'Named staff identity, separate from customer registration. No seeded or default Owner credentials.';
