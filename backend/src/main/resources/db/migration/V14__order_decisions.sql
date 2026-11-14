-- FR-14 / BR-04 / C1: staff rejection and cancellation with reasons; cancellation releases unfulfilled reservations.
CREATE TABLE app.order_decisions(
 order_id uuid PRIMARY KEY REFERENCES app.customer_orders(id),
 decision varchar(10) NOT NULL CHECK(decision IN ('REJECTED','CANCELLED')),
 previous_status varchar(32) NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(btrim(reason))>=10),
 actor_id uuid NOT NULL REFERENCES app.staff_accounts(id),
 decided_at timestamptz NOT NULL DEFAULT now(),
 CHECK(decision='CANCELLED' OR previous_status='PENDING')
);
COMMENT ON TABLE app.order_decisions IS 'One final staff decision per order. The reason is shown to the customer.';
CREATE TABLE app.reservation_releases(
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL,
 variant_id uuid NOT NULL,
 branch_code varchar(10) NOT NULL REFERENCES app.branches(code),
 quantity numeric(12,3) NOT NULL CHECK(quantity>0),
 reason varchar(20) NOT NULL CHECK(reason IN ('ORDER_CANCELLED')),
 actor_id uuid NOT NULL REFERENCES app.staff_accounts(id),
 released_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(order_id,variant_id) REFERENCES app.stock_reservations(order_id,variant_id)
);
CREATE INDEX reservation_releases_order_idx ON app.reservation_releases(order_id);
CREATE INDEX reservation_releases_variant_idx ON app.reservation_releases(variant_id,branch_code,released_at DESC);
CREATE FUNCTION app.append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE='23000';
END;
$$;
CREATE TRIGGER order_decisions_history BEFORE UPDATE OR DELETE ON app.order_decisions
 FOR EACH ROW EXECUTE FUNCTION app.append_only();
CREATE TRIGGER reservation_releases_history BEFORE UPDATE OR DELETE ON app.reservation_releases
 FOR EACH ROW EXECUTE FUNCTION app.append_only();
-- Staff updates now distinguish new requests from decisions.
ALTER TABLE app.staff_order_notices ADD COLUMN kind varchar(12) NOT NULL DEFAULT 'SUBMITTED'
 CHECK(kind IN ('SUBMITTED','REJECTED','CANCELLED'));
-- A closed branch still lets staff close out its old orders: rejecting or cancelling an order,
-- and giving back reserved stock, are allowed. Every other change stays blocked.
CREATE OR REPLACE FUNCTION app.require_active_branch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.branch_code IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM app.branches WHERE code=NEW.branch_code AND active
  ) THEN
    -- Compared as JSON: PL/pgSQL resolves every field named in an IF, even for the other table.
    IF TG_OP='UPDATE' AND NEW.branch_code IS NOT DISTINCT FROM OLD.branch_code THEN
      IF TG_TABLE_NAME='customer_orders' THEN
        IF to_jsonb(NEW)->>'status' IN ('REJECTED','CANCELLED')
           AND (to_jsonb(NEW)-'status')=(to_jsonb(OLD)-'status') THEN RETURN NEW; END IF;
      ELSIF TG_TABLE_NAME='stock_balances' THEN
        IF (to_jsonb(NEW)->>'on_hand')::numeric=(to_jsonb(OLD)->>'on_hand')::numeric
           AND (to_jsonb(NEW)->>'reserved')::numeric<(to_jsonb(OLD)->>'reserved')::numeric THEN RETURN NEW; END IF;
      END IF;
    END IF;
    RAISE EXCEPTION 'This branch is closed. Use Tarime for new operations.';
  END IF;
  RETURN NEW;
END;
$$;
-- Decision notices carry the staff reason (up to 500 characters) plus the next steps.
ALTER TABLE app.customer_notifications ALTER COLUMN message TYPE varchar(1000);
