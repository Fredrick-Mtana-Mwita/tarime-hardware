-- Approved 04 November 2026: existing balances belong to Tarime; Mwanza starts at zero.
CREATE TABLE app.branches(code VARCHAR(10) PRIMARY KEY, name VARCHAR(60) NOT NULL);
INSERT INTO app.branches VALUES ('TARIME','Tarime (main branch)'),('MWANZA','Mwanza');
ALTER TABLE app.stock_balances ADD COLUMN branch_code VARCHAR(10) NOT NULL DEFAULT 'TARIME' REFERENCES app.branches(code);
ALTER TABLE app.stock_balances DROP CONSTRAINT stock_balances_pkey;
ALTER TABLE app.stock_balances ADD PRIMARY KEY(variant_id,branch_code);
-- A missing branch balance means zero stock; never clone quantities into another branch.
ALTER TABLE app.customer_orders ADD COLUMN branch_code VARCHAR(10) REFERENCES app.branches(code);
CREATE TABLE app.order_confirmations(
 order_id UUID PRIMARY KEY REFERENCES app.customer_orders(id),
 actor_id UUID NOT NULL REFERENCES app.staff_accounts(id),
 branch_code VARCHAR(10) NOT NULL REFERENCES app.branches(code),
 original_delivery_fee NUMERIC(19,2), original_total NUMERIC(19,2),
 delivery_fee NUMERIC(19,2) NOT NULL CHECK(delivery_fee>=0),
 agreed_total NUMERIC(19,2) NOT NULL CHECK(agreed_total>=0),
 agreement_note VARCHAR(500) NOT NULL CHECK(length(trim(agreement_note))>=10),
 confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE app.stock_reservations(
 order_id UUID NOT NULL REFERENCES app.order_confirmations(order_id),
 variant_id UUID NOT NULL, branch_code VARCHAR(10) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0),
 remaining NUMERIC(12,3) NOT NULL CHECK(remaining>=0 AND remaining<=quantity),
 PRIMARY KEY(order_id,variant_id),
 FOREIGN KEY(variant_id,branch_code) REFERENCES app.stock_balances(variant_id,branch_code)
);
COMMENT ON TABLE app.order_confirmations IS 'Immutable confirmation revision and staff-recorded customer agreement. Original submission totals retained.';
