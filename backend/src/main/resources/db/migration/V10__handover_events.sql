ALTER TABLE app.customer_orders DROP CONSTRAINT customer_orders_status_check;
ALTER TABLE app.customer_orders ADD CHECK(status IN ('PENDING','CONFIRMED','PREPARING','READY_FOR_DELIVERY','READY_FOR_PICKUP','COLLECTED','OUT_FOR_DELIVERY','DELIVERED','REJECTED','CANCELLED'));
ALTER TABLE app.customer_orders ADD COLUMN driver_id uuid REFERENCES app.staff_accounts;
CREATE TABLE app.fulfilment_events (
 order_id uuid REFERENCES app.customer_orders, status varchar(32) NOT NULL,
 branch_code varchar(10) NOT NULL REFERENCES app.branches, actor_id uuid NOT NULL REFERENCES app.staff_accounts,
 recipient varchar(100), driver_id uuid REFERENCES app.staff_accounts, recorded_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(order_id,status),
 CHECK ((status IN ('DELIVERED','COLLECTED')) = (recipient IS NOT NULL))
);
