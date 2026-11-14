-- C2 / C4 / FR-19 / FR-20 / BR-08: manual payments, receipts, corrections and refunds.
-- Approved 13 November 2026: cash and mobile money only; payments only after confirmation;
-- no overpayment; a sale is recognised on delivery or collection.
-- Money moves are append-only. A wrong payment is corrected by an Owner-approved reversal, never by an edit.
CREATE SEQUENCE app.receipt_number_seq;
CREATE TABLE app.payments(
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES app.customer_orders(id),
 receipt_number varchar(20) NOT NULL UNIQUE,
 method varchar(16) NOT NULL CHECK(method IN ('CASH','MOBILE_MONEY')),
 provider varchar(16) CHECK(provider IN ('MPESA','MIXX','AIRTEL_MONEY','HALOPESA')),
 reference varchar(30) CHECK(reference ~ '^[A-Z0-9]{6,30}$'),
 amount numeric(19,2) NOT NULL CHECK(amount>0),
 currency char(3) NOT NULL,
 note varchar(300),
 -- Only staff-recorded payments exist today. A future provider integration adds its own verified source.
 source varchar(20) NOT NULL DEFAULT 'STAFF_RECORDED' CHECK(source IN ('STAFF_RECORDED')),
 recorded_by uuid NOT NULL REFERENCES app.staff_accounts(id),
 recorded_at timestamptz NOT NULL DEFAULT now(),
 CHECK((method='MOBILE_MONEY' AND provider IS NOT NULL AND reference IS NOT NULL)
    OR (method='CASH' AND provider IS NULL AND reference IS NULL))
);
-- The same mobile-money transaction can never be recorded twice.
CREATE UNIQUE INDEX payments_mobile_reference ON app.payments(provider,reference) WHERE reference IS NOT NULL;
CREATE INDEX payments_order_idx ON app.payments(order_id,recorded_at);
CREATE INDEX payments_recorded_idx ON app.payments(recorded_at);
CREATE TRIGGER payments_history BEFORE UPDATE OR DELETE ON app.payments
 FOR EACH ROW EXECUTE FUNCTION app.append_only();

CREATE TABLE app.payment_adjustments(
 id uuid PRIMARY KEY,
 order_id uuid NOT NULL REFERENCES app.customer_orders(id),
 kind varchar(10) NOT NULL CHECK(kind IN ('REVERSAL','REFUND')),
 payment_id uuid REFERENCES app.payments(id),
 amount numeric(19,2) NOT NULL CHECK(amount>0),
 reason varchar(500) NOT NULL CHECK(length(btrim(reason))>=10),
 refund_method varchar(16) CHECK(refund_method IN ('CASH','MOBILE_MONEY')),
 refund_provider varchar(16) CHECK(refund_provider IN ('MPESA','MIXX','AIRTEL_MONEY','HALOPESA')),
 refund_reference varchar(30) CHECK(refund_reference ~ '^[A-Z0-9]{6,30}$'),
 status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 requested_by uuid NOT NULL REFERENCES app.staff_accounts(id),
 requested_at timestamptz NOT NULL DEFAULT now(),
 decided_by uuid REFERENCES app.staff_accounts(id),
 decided_at timestamptz,
 decision_note varchar(300),
 -- A reversal cancels one wrongly recorded payment in full. A refund gives money back to the customer.
 CHECK((kind='REVERSAL' AND payment_id IS NOT NULL AND refund_method IS NULL)
    OR (kind='REFUND' AND payment_id IS NULL AND refund_method IS NOT NULL)),
 CHECK(refund_reference IS NULL OR (refund_method='MOBILE_MONEY' AND status='APPROVED')),
 CHECK((refund_provider IS NULL)=(refund_method IS DISTINCT FROM 'MOBILE_MONEY')),
 CHECK(NOT(kind='REFUND' AND refund_method='MOBILE_MONEY' AND status='APPROVED') OR refund_reference IS NOT NULL),
 CHECK((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL) OR (status<>'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
CREATE UNIQUE INDEX payment_adjustments_one_reversal ON app.payment_adjustments(payment_id) WHERE kind='REVERSAL' AND status<>'REJECTED';
CREATE UNIQUE INDEX payment_adjustments_refund_reference ON app.payment_adjustments(refund_provider,refund_reference) WHERE refund_reference IS NOT NULL;
CREATE INDEX payment_adjustments_order_idx ON app.payment_adjustments(order_id,requested_at);
CREATE INDEX payment_adjustments_pending_idx ON app.payment_adjustments(requested_at) WHERE status='PENDING';
CREATE INDEX payment_adjustments_decided_idx ON app.payment_adjustments(decided_at) WHERE status='APPROVED';
-- Decided requests are history; a pending request may be decided once.
CREATE FUNCTION app.payment_adjustments_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' OR OLD.status<>'PENDING' THEN RAISE EXCEPTION 'payment_adjustments is append-only' USING ERRCODE='23000'; END IF;
  IF (to_jsonb(NEW)-ARRAY['status','decided_by','decided_at','decision_note','refund_reference'])
     <>(to_jsonb(OLD)-ARRAY['status','decided_by','decided_at','decision_note','refund_reference']) THEN
    RAISE EXCEPTION 'Only the decision may change on a payment adjustment' USING ERRCODE='23000';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER payment_adjustments_history BEFORE UPDATE OR DELETE ON app.payment_adjustments
 FOR EACH ROW EXECUTE FUNCTION app.payment_adjustments_immutable();

-- Effective money per order. Pending adjustments never change a balance (C4).
CREATE VIEW app.order_balances AS
SELECT o.id AS order_id, o.status, o.currency,
 CASE WHEN o.status IN ('PENDING','REJECTED','CANCELLED') THEN 0::numeric(19,2) ELSE COALESCE(o.total,0) END AS amount_due_total,
 COALESCE(p.paid,0)::numeric(19,2) AS paid,
 COALESCE(a.reversed,0)::numeric(19,2) AS reversed,
 COALESCE(a.refunded,0)::numeric(19,2) AS refunded,
 (COALESCE(p.paid,0)-COALESCE(a.reversed,0)-COALESCE(a.refunded,0))::numeric(19,2) AS net_paid
FROM app.customer_orders o
LEFT JOIN (SELECT order_id,sum(amount) AS paid FROM app.payments GROUP BY order_id) p ON p.order_id=o.id
LEFT JOIN (SELECT order_id,
             sum(amount) FILTER (WHERE kind='REVERSAL') AS reversed,
             sum(amount) FILTER (WHERE kind='REFUND') AS refunded
           FROM app.payment_adjustments WHERE status='APPROVED' GROUP BY order_id) a ON a.order_id=o.id;

-- An order can have several payments, cancelled receipts and refunds, each with its own update.
-- Other update titles (received, confirmed, cancelled...) stay one per order.
ALTER TABLE app.customer_notifications DROP CONSTRAINT customer_notifications_order_id_title_key;
CREATE UNIQUE INDEX customer_notifications_once ON app.customer_notifications(order_id,title)
 WHERE title NOT IN ('Payment received','Receipt cancelled','Refund sent');
