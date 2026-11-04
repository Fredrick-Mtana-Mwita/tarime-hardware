CREATE TABLE app.stock_receipts (
 id UUID PRIMARY KEY,
 actor_id UUID NOT NULL REFERENCES app.staff_accounts(id),
 variant_id UUID NOT NULL,
 branch_code VARCHAR(10) NOT NULL,
 product_name VARCHAR(200) NOT NULL,
 variant_label VARCHAR(120) NOT NULL,
 sku VARCHAR(100) NOT NULL,
 unit_label VARCHAR(80) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0),
 source_reference VARCHAR(160) NOT NULL CHECK(length(trim(source_reference))>=2),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>=5),
 on_hand_after NUMERIC(19,3) NOT NULL CHECK(on_hand_after>=quantity),
 reserved_after NUMERIC(19,3) NOT NULL CHECK(reserved_after>=0 AND reserved_after<=on_hand_after),
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(variant_id,branch_code) REFERENCES app.stock_balances(variant_id,branch_code)
);
CREATE INDEX stock_receipts_branch_time_idx ON app.stock_receipts(branch_code,received_at DESC,id);
CREATE TRIGGER stock_receipts_append_only BEFORE UPDATE OR DELETE ON app.stock_receipts
 FOR EACH ROW EXECUTE FUNCTION app.reject_audit_mutation();
