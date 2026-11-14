package africa.finishers.orders.infrastructure;

import africa.finishers.orders.application.OrderDecisions;
import africa.finishers.orders.application.OrderDecisions.Kind;
import africa.finishers.orders.application.OrderProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcOrderDecisions implements OrderDecisions.Writer {
    /** Orders whose goods have not left the shop. After dispatch or collection the returns process applies. */
    private static final Set<String> CANCELLABLE=Set.of("PENDING","CONFIRMED","PREPARING","READY_FOR_PICKUP","READY_FOR_DELIVERY");
    private final JdbcTemplate jdbc;
    public JdbcOrderDecisions(JdbcTemplate jdbc){this.jdbc=jdbc;}
    record Header(String status,UUID customer,String reference){}
    record Previous(String decision,String reason){}
    record Held(UUID variant,String branch,BigDecimal remaining){}
    public void decide(UUID id,UUID actor,Kind kind,String reason){
        // Locking the order serialises this with confirmation and handover, which lock the same row first.
        var rows=jdbc.query("SELECT status,customer_id,reference FROM app.customer_orders WHERE id=? FOR UPDATE",
                (r,n)->new Header(r.getString(1),r.getObject(2,UUID.class),r.getString(3)),id);
        if(rows.isEmpty())throw new OrderProblem(404,"Order not found.");
        var order=rows.getFirst();
        var previous=jdbc.query("SELECT decision,reason FROM app.order_decisions WHERE order_id=?",(r,n)->new Previous(r.getString(1),r.getString(2)),id);
        if(!previous.isEmpty()){
            // A retried request with the same decision and reason returns the recorded result.
            if(previous.getFirst().decision().equals(kind.name())&&previous.getFirst().reason().equals(reason))return;
            throw new OrderProblem(409,"This order was already "+previous.getFirst().decision().toLowerCase()+". Refresh the order.");
        }
        String status=order.status();
        if(kind==Kind.REJECTED&&!status.equals("PENDING"))
            throw new OrderProblem(409,"Only pending requests can be rejected. Cancel a confirmed order instead.");
        if(kind==Kind.CANCELLED&&!CANCELLABLE.contains(status))
            throw new OrderProblem(409,"This order has left the shop or is already closed, so it cannot be cancelled. Handle it as a return.");
        var held=jdbc.query("SELECT variant_id,branch_code,remaining FROM app.stock_reservations WHERE order_id=? AND remaining>0 ORDER BY variant_id FOR UPDATE",
                (r,n)->new Held(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3)),id);
        for(var line:held){
            int changed=jdbc.update("UPDATE app.stock_balances SET reserved=reserved-?,version=version+1 WHERE variant_id=? AND branch_code=? AND reserved>=?",
                    line.remaining(),line.variant(),line.branch(),line.remaining());
            if(changed!=1)throw new OrderProblem(409,"Reserved stock does not match this order. Nothing was changed. Ask the Owner to check the stock record.");
            jdbc.update("INSERT INTO app.reservation_releases(id,order_id,variant_id,branch_code,quantity,reason,actor_id) VALUES(?,?,?,?,?,'ORDER_CANCELLED',?)",
                    UUID.randomUUID(),id,line.variant(),line.branch(),line.remaining(),actor);
            jdbc.update("UPDATE app.stock_reservations SET remaining=0 WHERE order_id=? AND variant_id=?",id,line.variant());
        }
        jdbc.update("INSERT INTO app.order_decisions(order_id,decision,previous_status,reason,actor_id) VALUES(?,?,?,?,?)",id,kind.name(),status,reason,actor);
        jdbc.update("UPDATE app.customer_orders SET status=? WHERE id=?",kind.name(),id);
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference,metadata) VALUES(?,now(),?,'STAFF',?,'ORDER',?,jsonb_build_object('previousStatus',?::text,'releasedLines',?::int))",
                UUID.randomUUID(),actor,"ORDER_"+kind.name(),id.toString(),status,held.size());
        String title=kind==Kind.REJECTED?"Order request not accepted":"Order cancelled";
        String message=kind==Kind.REJECTED
                ?"The shop could not accept your request "+order.reference()+". Reason: "+reason+" You can contact the shop or send a new request."
                :"Your order "+order.reference()+" was cancelled. Reason: "+reason+(held.isEmpty()?"":" The stock held for you has been released.")+" If you paid anything for this order, contact the shop about a refund.";
        jdbc.update("INSERT INTO app.customer_notifications(id,customer_id,order_id,title,message) VALUES(?,?,?,?,?)",UUID.randomUUID(),order.customer(),id,title,message);
        jdbc.update("INSERT INTO app.staff_order_notices(id,order_id,kind) VALUES(?,?,?)",UUID.randomUUID(),id,kind.name());
    }
}
