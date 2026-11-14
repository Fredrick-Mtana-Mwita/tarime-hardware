-- Called inside the authorised dispatch or collection transaction, before the status change.
CREATE FUNCTION app.consume_order_reservation(p_order uuid)
RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE
    order_branch text;
    reservation record;
    consumed boolean := false;
BEGIN
    SELECT branch_code INTO STRICT order_branch
    FROM app.customer_orders WHERE id = p_order FOR UPDATE;

    FOR reservation IN
        SELECT variant_id, branch_code, remaining
        FROM app.stock_reservations
        WHERE order_id = p_order AND remaining > 0
        ORDER BY variant_id FOR UPDATE
    LOOP
        IF reservation.branch_code IS DISTINCT FROM order_branch THEN
            RAISE EXCEPTION 'Reservation branch differs from order branch';
        END IF;
        UPDATE app.stock_balances
        SET on_hand = on_hand - reservation.remaining,
            reserved = reserved - reservation.remaining,
            version = version + 1
        WHERE branch_code = order_branch AND variant_id = reservation.variant_id
          AND reserved >= reservation.remaining AND on_hand >= reservation.remaining;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Insufficient reserved stock for handover';
        END IF;
        UPDATE app.stock_reservations SET remaining = 0
        WHERE order_id = p_order AND variant_id = reservation.variant_id;
        consumed := true;
    END LOOP;
    RETURN consumed;
END;
$$;
