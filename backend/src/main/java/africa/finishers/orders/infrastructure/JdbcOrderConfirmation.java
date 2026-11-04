package africa.finishers.orders.infrastructure;

import africa.finishers.orders.application.OrderConfirmation;
import africa.finishers.orders.application.OrderProblem;
import africa.finishers.catalogue.application.PurchasingCatalogue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class JdbcOrderConfirmation implements OrderConfirmation.Writer {
    private final JdbcTemplate jdbc;
    private final PurchasingCatalogue catalogue;
    public JdbcOrderConfirmation(JdbcTemplate jdbc,PurchasingCatalogue catalogue){this.jdbc=jdbc;this.catalogue=catalogue;}
    record Header(String status,String branch,String fulfilment,String currency,BigDecimal subtotal,BigDecimal fee,BigDecimal total,UUID customer,String reference){}
    record Line(UUID variant,BigDecimal quantity,BigDecimal price){}
    public void confirm(UUID id,UUID actor,OrderConfirmation.Agreement agreement){
        var rows=jdbc.query("SELECT * FROM app.customer_orders WHERE id=? FOR UPDATE",(r,n)->new Header(r.getString("status"),r.getString("branch_code"),r.getString("fulfilment"),r.getString("currency"),r.getBigDecimal("subtotal"),r.getBigDecimal("delivery_fee"),r.getBigDecimal("total"),r.getObject("customer_id",UUID.class),r.getString("reference")),id);
        if(rows.isEmpty())throw new OrderProblem(404,"Order not found.");
        var order=rows.getFirst();
        // Order identity is the idempotency scope: identical retries return the committed result.
        var previous=jdbc.query("SELECT delivery_fee,agreement_note FROM app.order_confirmations WHERE order_id=?",(r,n)->new OrderConfirmation.Agreement(r.getBigDecimal(1),r.getString(2)),id);
        if(!previous.isEmpty()){
            if(previous.getFirst().deliveryFee().compareTo(agreement.deliveryFee())!=0||!previous.getFirst().note().equals(agreement.note()))throw new OrderProblem(409,"This order was already confirmed with different agreement details. Refresh the order.");
            return;
        }
        if(!order.status().equals("PENDING"))throw new OrderProblem(409,"Only pending orders can be confirmed.");
        if(order.branch()==null)throw new OrderProblem(409,"This older request has no customer-selected branch. Ask the customer to submit a new request with their branch choice.");
        if(order.fulfilment().equals("PICKUP")&&agreement.deliveryFee().signum()!=0)throw new OrderProblem(400,"Shop pickup has no delivery fee.");
        var total=order.subtotal().add(agreement.deliveryFee());
        if(total.precision()>19)throw new OrderProblem(400,"The agreed total is too large.");
        var lines=jdbc.query("SELECT variant_id,quantity,unit_price FROM app.customer_order_items WHERE order_id=? ORDER BY variant_id",(r,n)->new Line(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)),id);
        if(lines.isEmpty())throw new OrderProblem(409,"An empty order cannot be confirmed.");
        var current=catalogue.items(lines.stream().map(Line::variant).toList(),true,order.branch());
        if(current.size()!=lines.size())throw new OrderProblem(409,"An item is no longer available. Obtain a revised customer request before confirming.");
        for(var line:lines){
            var item=current.stream().filter(p->p.variantId().equals(line.variant())).findFirst().orElseThrow();
            if(item.price().compareTo(line.price())!=0||!item.currency().equals(order.currency()))throw new OrderProblem(409,"A price has changed. Ask the customer to review current prices and submit a new request.");
            if(!item.fractional()&&line.quantity().stripTrailingZeros().scale()>0)throw new OrderProblem(409,"A product unit has changed. Request a new customer review.");
        }
        jdbc.update("INSERT INTO app.order_confirmations(order_id,actor_id,branch_code,original_delivery_fee,original_total,delivery_fee,agreed_total,agreement_note) VALUES(?,?,?,?,?,?,?,?)",id,actor,order.branch(),order.fee(),order.total(),agreement.deliveryFee(),total,agreement.note());
        // Conditional writes lock balances in a stable variant order; all lines succeed or roll back.
        for(var line:lines){
            int changed=jdbc.update("UPDATE app.stock_balances SET reserved=reserved+?,version=version+1 WHERE variant_id=? AND branch_code=? AND on_hand-reserved>=?",line.quantity(),line.variant(),order.branch(),line.quantity());
            if(changed!=1)throw new OrderProblem(409,"Insufficient stock at the selected branch. Nothing was reserved; refresh before retrying.");
            jdbc.update("INSERT INTO app.stock_reservations(order_id,variant_id,branch_code,quantity,remaining) VALUES(?,?,?,?,?)",id,line.variant(),order.branch(),line.quantity(),line.quantity());
        }
        jdbc.update("UPDATE app.customer_orders SET status='CONFIRMED',delivery_fee=?,total=? WHERE id=?",agreement.deliveryFee(),total,id);
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF','ORDER_CONFIRMED','ORDER',?)",UUID.randomUUID(),actor,id.toString());
        jdbc.update("INSERT INTO app.customer_notifications(id,customer_id,order_id,title,message) VALUES(?,?,?,?,?)",UUID.randomUUID(),order.customer(),id,"Order confirmed","Your order "+order.reference()+" is confirmed at "+order.branch()+". Stock is reserved. Check your order for the agreed total; this is not a payment receipt.");
    }
}
