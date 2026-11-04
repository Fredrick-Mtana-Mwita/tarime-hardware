CREATE TABLE app.customer_accounts (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL,
 phone VARCHAR(16) NOT NULL UNIQUE CHECK(phone ~ '^\+[1-9][0-9]{7,14}$'),
 email VARCHAR(254), password_hash VARCHAR(512) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, contact_verified BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- This table has no staff-role column: public registration cannot grant staff authority.
CREATE TABLE app.auth_rate_limits (
 operation VARCHAR(30) NOT NULL, client_key CHAR(64) NOT NULL,
 window_start TIMESTAMPTZ NOT NULL, attempts INTEGER NOT NULL CHECK(attempts>0),
 PRIMARY KEY(operation,client_key,window_start)
);
CREATE INDEX auth_rate_limits_expiry_idx ON app.auth_rate_limits(window_start);
