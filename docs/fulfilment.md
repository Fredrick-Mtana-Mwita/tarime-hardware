# Pickup and delivery handover

Approved handover proof is the recipient name plus acknowledgement by the signed-in staff member. Each transition records the order, branch, actor, assigned driver and time.

Owner and Sales prepare confirmed orders. Delivery preparation requires an active named driver. The assigned driver and Owner can dispatch and complete delivery. Owner and Sales can complete collection. Stock is deducted on dispatch or collection, with reservations released in the same database transaction. Delivery completion does not deduct stock again.

The fulfilment queue is available at #/staff/fulfilment. Drivers see only their assigned jobs. GitHub Pages displays synthetic examples with disabled handover controls. Real operations require the local backend and PostgreSQL.

Restart IntelliJ Full Stack after verification to apply V9 and V10. Failed delivery, reassignment, partial fulfilment and returns remain later workflows.
