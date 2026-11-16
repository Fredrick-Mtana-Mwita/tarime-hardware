package africa.finishers.reporting.infrastructure;

import africa.finishers.reporting.application.ReportModels.*;
import africa.finishers.reporting.application.Reports;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Repository
public class JdbcReports implements Reports.Store {
    private static final String BRANCH="TARIME";
    private final JdbcTemplate jdbc;
    public JdbcReports(JdbcTemplate jdbc){this.jdbc=jdbc;}
    /** Calendar day in shop time (EAT) of a timestamp column. */
    private static String day(String column){return "("+column+" AT TIME ZONE 'Africa/Dar_es_Salaam')::date";}
    private static BigDecimal dec(Map<String,Object> row,String key){Object v=row.get(key);return v==null?BigDecimal.ZERO:(BigDecimal)v;}
    private static long num(Map<String,Object> row,String key){Object v=row.get(key);return v==null?0:((Number)v).longValue();}

    public SalesReport sales(LocalDate from,LocalDate to){
        var orders=jdbc.queryForMap("SELECT count(*) n,COALESCE(sum(subtotal),0) v FROM app.customer_orders WHERE "+day("created_at")+" BETWEEN ? AND ?",from,to);
        long confirmed=jdbc.queryForObject("SELECT count(*) FROM app.order_confirmations WHERE "+day("confirmed_at")+" BETWEEN ? AND ?",Long.class,from,to);
        var decisions=jdbc.queryForMap("SELECT count(*) FILTER (WHERE decision='REJECTED') r,count(*) FILTER (WHERE decision='CANCELLED') c FROM app.order_decisions WHERE "+day("decided_at")+" BETWEEN ? AND ?",from,to);
        var completed=jdbc.queryForMap("SELECT count(*) n,COALESCE(sum(o.total),0) v FROM app.fulfilment_events e JOIN app.customer_orders o ON o.id=e.order_id WHERE e.status IN ('DELIVERED','COLLECTED') AND "+day("e.recorded_at")+" BETWEEN ? AND ?",from,to);
        var received=jdbc.queryForMap("SELECT COALESCE(sum(amount) FILTER (WHERE method='CASH'),0) cash,COALESCE(sum(amount) FILTER (WHERE method='MOBILE_MONEY'),0) mobile,count(*) n FROM app.payments WHERE "+day("recorded_at")+" BETWEEN ? AND ?",from,to);
        var adjusted=jdbc.queryForMap("SELECT COALESCE(sum(amount) FILTER (WHERE kind='REVERSAL'),0) reversed,COALESCE(sum(amount) FILTER (WHERE kind='REFUND'),0) refunded FROM app.payment_adjustments WHERE status='APPROVED' AND "+day("decided_at")+" BETWEEN ? AND ?",from,to);
        BigDecimal gross=dec(completed,"v"),refunds=dec(adjusted,"refunded"),cash=dec(received,"cash"),mobile=dec(received,"mobile"),reversed=dec(adjusted,"reversed");
        BigDecimal in=cash.add(mobile);
        var days=jdbc.query("""
                SELECT d::date AS day,
                 (SELECT COALESCE(sum(subtotal),0) FROM app.customer_orders WHERE %s=d::date) requested,
                 (SELECT COALESCE(sum(o.total),0) FROM app.fulfilment_events e JOIN app.customer_orders o ON o.id=e.order_id WHERE e.status IN ('DELIVERED','COLLECTED') AND %s=d::date) completed,
                 (SELECT COALESCE(sum(amount),0) FROM app.payments WHERE %s=d::date) received,
                 (SELECT COALESCE(sum(amount),0) FROM app.payment_adjustments WHERE kind='REFUND' AND status='APPROVED' AND %s=d::date) refunded
                FROM generate_series(?::date,?::date,interval '1 day') d ORDER BY d
                """.formatted(day("created_at"),day("e.recorded_at"),day("recorded_at"),day("decided_at")),
                (r,n)->new Day(r.getObject("day",LocalDate.class),r.getBigDecimal("requested"),r.getBigDecimal("completed"),r.getBigDecimal("received"),r.getBigDecimal("refunded")),from,to);
        var top=jdbc.query("""
                SELECT i.product_name,i.variant_label,i.unit,sum(i.quantity) qty,sum(i.line_total) value
                FROM app.fulfilment_events e JOIN app.customer_order_items i ON i.order_id=e.order_id
                WHERE e.status IN ('DELIVERED','COLLECTED') AND %s BETWEEN ? AND ?
                GROUP BY i.product_name,i.variant_label,i.unit ORDER BY value DESC,i.product_name LIMIT 10
                """.formatted(day("e.recorded_at")),
                (r,n)->new TopProduct(r.getString(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5)),from,to);
        return new SalesReport(from,to,"TZS",
                new OrderFigures(num(orders,"n"),dec(orders,"v"),confirmed,num(decisions,"r"),num(decisions,"c")),
                new SalesFigures(num(completed,"n"),gross,refunds,gross.subtract(refunds)),
                new MoneyFigures(cash,mobile,in,num(received,"n"),reversed,refunds,in.subtract(reversed).subtract(refunds)),days,top);
    }

    public BalancesReport balances(){
        var rows=jdbc.query("""
                SELECT o.id,o.reference,o.contact_name,o.contact_phone,o.status,b.amount_due_total total,b.net_paid,
                 GREATEST(b.amount_due_total-b.net_paid,0) due,GREATEST(b.net_paid-b.amount_due_total,0) refund_due,
                 (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date-%s age
                FROM app.customer_orders o JOIN app.order_balances b ON b.order_id=o.id LEFT JOIN app.order_confirmations c ON c.order_id=o.id
                WHERE b.net_paid<>b.amount_due_total AND (o.status NOT IN ('PENDING','REJECTED') OR b.paid>0)
                ORDER BY age DESC,o.reference LIMIT 500
                """.formatted(day("COALESCE(c.confirmed_at,o.created_at)")),
                (r,n)->new BalanceRow(r.getObject("id",java.util.UUID.class),r.getString("reference"),r.getString("contact_name"),r.getString("contact_phone"),r.getString("status"),
                        r.getBigDecimal("total"),r.getBigDecimal("net_paid"),r.getBigDecimal("due"),r.getBigDecimal("refund_due"),r.getLong("age")));
        BigDecimal owed=rows.stream().map(BalanceRow::due).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal back=rows.stream().map(BalanceRow::refundDue).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new BalancesReport("TZS",owed,back,rows.size(),rows);
    }

    public StockReport stock(LocalDate from,LocalDate to){
        var rows=jdbc.query("""
                SELECT v.id,p.name,v.label,v.sku,u.label unit,s.on_hand,s.reserved,s.on_hand-s.reserved available,s.low_stock_threshold,
                 s.on_hand-s.reserved<=s.low_stock_threshold low,v.unit_price,s.on_hand*v.unit_price value,
                 (SELECT COALESCE(sum(quantity),0) FROM app.stock_receipts sr WHERE sr.variant_id=v.id AND sr.branch_code=s.branch_code AND %s BETWEEN ? AND ?) received,
                 (SELECT COALESCE(sum(sv.quantity),0) FROM app.stock_reservations sv JOIN app.fulfilment_events e ON e.order_id=sv.order_id AND e.status IN ('OUT_FOR_DELIVERY','COLLECTED')
                    WHERE sv.variant_id=v.id AND sv.branch_code=s.branch_code AND %s BETWEEN ? AND ?) dispatched,
                 (SELECT COALESCE(sum(rr.quantity),0) FROM app.reservation_releases rr WHERE rr.variant_id=v.id AND rr.branch_code=s.branch_code AND %s BETWEEN ? AND ?) released
                FROM app.stock_balances s JOIN app.product_variants v ON v.id=s.variant_id JOIN app.products p ON p.id=v.product_id JOIN app.units u ON u.code=v.unit_code
                WHERE s.branch_code=? AND NOT p.archived
                ORDER BY low DESC,p.name,v.sort_order,v.id LIMIT 2000
                """.formatted(day("sr.received_at"),day("e.recorded_at"),day("rr.released_at")),
                (r,n)->new StockRow(r.getObject("id",java.util.UUID.class),r.getString("name"),r.getString("label"),r.getString("sku"),r.getString("unit"),
                        r.getBigDecimal("on_hand"),r.getBigDecimal("reserved"),r.getBigDecimal("available"),r.getBigDecimal("low_stock_threshold"),r.getBoolean("low"),
                        r.getBigDecimal("unit_price"),r.getBigDecimal("value"),r.getBigDecimal("received"),r.getBigDecimal("dispatched"),r.getBigDecimal("released")),
                from,to,from,to,from,to,BRANCH);
        long low=rows.stream().filter(StockRow::lowStock).count();
        BigDecimal value=rows.stream().map(StockRow::valueAtPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new StockReport(from,to,BRANCH,"TZS",rows.size(),low,value,rows);
    }
}
