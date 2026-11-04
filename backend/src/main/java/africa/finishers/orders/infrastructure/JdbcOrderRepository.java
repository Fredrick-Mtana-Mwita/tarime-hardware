package africa.finishers.orders.infrastructure;

import africa.finishers.orders.application.OrderRepository;
import africa.finishers.orders.application.OrderProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static africa.finishers.orders.application.OrderModels.*;

@Repository
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;
    public JdbcOrderRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static Summary summary(ResultSet r) throws SQLException {
        return new Summary(r.getObject("id",UUID.class),r.getString("reference"),r.getString("status"),
                Fulfilment.valueOf(r.getString("fulfilment")),r.getString("currency"),r.getBigDecimal("subtotal"),
                r.getBigDecimal("delivery_fee"),r.getBigDecimal("total"),r.getTimestamp("created_at").toInstant(),r.getString("branch_code")==null?null:Branch.valueOf(r.getString("branch_code")));
    }
    public void lockCustomer(UUID customerId){
        var ids=jdbc.query("SELECT id FROM app.customer_accounts WHERE id=? AND active FOR UPDATE",(r,n)->r.getObject(1,UUID.class),customerId);
        if(ids.isEmpty())throw new OrderProblem(401,"Sign in to place an order.");
    }
    public Optional<Existing> existing(UUID customerId,UUID key){
        return jdbc.query("SELECT id,request_hash FROM app.customer_orders WHERE customer_id=? AND idempotency_key=?",
                (r,n)->new Existing(r.getObject(1,UUID.class),r.getString(2)),customerId,key).stream().findFirst();
    }
    public boolean submissionLimitReached(UUID customerId){
        return jdbc.queryForObject("SELECT count(*)>=20 FROM app.customer_orders WHERE customer_id=? AND created_at>now()-interval '1 hour'",Boolean.class,customerId);
    }
    public void save(UUID id,String reference,UUID customerId,UUID key,String hash,Submission request,Quote quote){
        java.math.BigDecimal fee=request.fulfilment()==Fulfilment.PICKUP?java.math.BigDecimal.ZERO:null;
        jdbc.update("""
                INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,fulfilment,
                    contact_name,contact_phone,address,instructions,currency,subtotal,delivery_fee,total,branch_code)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,reference,customerId,key,hash,request.fulfilment().name(),request.name(),request.phone(),
                request.address(),request.instructions(),quote.currency(),quote.subtotal(),fee,fee==null?null:quote.subtotal(),request.branch().name());
        for(var line:quote.items())jdbc.update("""
                INSERT INTO app.customer_order_items(order_id,variant_id,product_id,product_name,variant_label,sku,unit,quantity,unit_price,line_total)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,id,line.variantId(),line.productId(),line.productName(),line.variantLabel(),line.sku(),line.unit(),line.quantity(),line.unitPrice(),line.lineTotal());
        jdbc.update("""
                INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference)
                VALUES(?,now(),?,'CUSTOMER','ORDER_SUBMITTED','ORDER',?)
                """,UUID.randomUUID(),customerId,id.toString());
    }
    public Optional<Order> find(UUID customerId,UUID id){
        RowMapper<Order> mapper=(r,n)->new Order(summary(r),r.getString("contact_name"),r.getString("contact_phone"),r.getString("address"),r.getString("instructions"),List.of());
        var rows=jdbc.query("SELECT * FROM app.customer_orders WHERE id=? AND customer_id=?",mapper,id,customerId);
        if(rows.isEmpty())return Optional.empty();
        var lines=jdbc.query("SELECT * FROM app.customer_order_items WHERE order_id=? ORDER BY product_name,variant_id",
                (r,n)->new OrderLine(r.getObject("variant_id",UUID.class),r.getObject("product_id",UUID.class),r.getString("product_name"),
                        r.getString("variant_label"),r.getString("sku"),r.getString("unit"),r.getBigDecimal("quantity"),r.getBigDecimal("unit_price"),r.getBigDecimal("line_total")),id);
        var row=rows.getFirst();return Optional.of(new Order(row.summary(),row.name(),row.phone(),row.address(),row.instructions(),lines));
    }
    public OrderPage list(UUID customerId,int page,int size){
        var rows=jdbc.query("SELECT * FROM app.customer_orders WHERE customer_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (r,n)->summary(r),customerId,size,(long)page*size);
        Long count=jdbc.queryForObject("SELECT count(*) FROM app.customer_orders WHERE customer_id=?",Long.class,customerId);
        return new OrderPage(rows,count==null?0:count,page,size);
    }
}
