-- Customer requests are pending review: submission never reserves or deducts stock.
CREATE TABLE app.customer_orders (
 id UUID PRIMARY KEY,
 reference VARCHAR(40) NOT NULL UNIQUE,
 customer_id UUID NOT NULL REFERENCES app.customer_accounts(id),
 idempotency_key UUID NOT NULL,
 request_hash CHAR(64) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK(status IN
   ('PENDING','CONFIRMED','PREPARING','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','REJECTED','CANCELLED')),
 fulfilment VARCHAR(10) NOT NULL CHECK(fulfilment IN ('DELIVERY','PICKUP')),
 contact_name VARCHAR(100) NOT NULL,
 contact_phone VARCHAR(16) NOT NULL,
 address VARCHAR(500) NOT NULL DEFAULT '',
 instructions VARCHAR(500) NOT NULL DEFAULT '',
 currency CHAR(3) NOT NULL,
 subtotal NUMERIC(19,2) NOT NULL CHECK(subtotal>=0),
 delivery_fee NUMERIC(19,2) CHECK(delivery_fee>=0),
 total NUMERIC(19,2) CHECK(total>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(customer_id,idempotency_key),
 CHECK(fulfilment<>'DELIVERY' OR length(trim(address))>=5),
 CHECK((delivery_fee IS NULL AND total IS NULL) OR (delivery_fee IS NOT NULL AND total IS NOT NULL AND total=subtotal+delivery_fee))
);
CREATE INDEX customer_orders_owner_time_idx ON app.customer_orders(customer_id,created_at DESC,id DESC);
CREATE TABLE app.customer_order_items (
 order_id UUID NOT NULL REFERENCES app.customer_orders(id),
 variant_id UUID NOT NULL REFERENCES app.product_variants(id),
 product_id UUID NOT NULL REFERENCES app.products(id),
 product_name VARCHAR(200) NOT NULL,
 variant_label VARCHAR(120) NOT NULL,
 sku VARCHAR(100) NOT NULL,
 unit VARCHAR(80) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0 AND quantity<=1000000),
 unit_price NUMERIC(19,2) NOT NULL CHECK(unit_price>=0),
 line_total NUMERIC(19,2) NOT NULL CHECK(line_total>=0),
 PRIMARY KEY(order_id,variant_id)
);
CREATE TABLE app.customer_notifications (
 id UUID PRIMARY KEY,
 customer_id UUID NOT NULL REFERENCES app.customer_accounts(id),
 order_id UUID NOT NULL REFERENCES app.customer_orders(id),
 title VARCHAR(150) NOT NULL,
 message VARCHAR(500) NOT NULL,
 read_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(order_id,title)
);
CREATE INDEX customer_notifications_owner_time_idx ON app.customer_notifications(customer_id,created_at DESC,id DESC);
-- Separate from auth limits: a committed successful submission is counted under the customer lock.
-- Identical retries remain available even after this limit is reached.
COMMENT ON TABLE app.customer_orders IS 'Immutable submitted snapshots; delivery fees and final agreement are pending staff review. No stock effects at submission.';
