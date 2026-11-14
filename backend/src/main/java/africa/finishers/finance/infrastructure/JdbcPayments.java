package africa.finishers.finance.infrastructure;

import africa.finishers.finance.application.FinanceModels.*;
import africa.finishers.finance.application.Payments;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JdbcPayments implements Payments.Store {
    /** Payments are accepted once the agreed total is fixed and until the order is closed without a sale. */
    private static final Set<String> PAYABLE=Set.of("CONFIRMED","PREPARING","READY_FOR_PICKUP","READY_FOR_DELIVERY","OUT_FOR_DELIVERY","DELIVERED","COLLECTED");
    private static final Map<String,String> PROVIDER_NAMES=Map.of("MPESA","M-Pesa","MIXX","Mixx by Yas","AIRTEL_MONEY","Airtel Money","HALOPESA","HaloPesa");
    private final JdbcTemplate jdbc;
    public JdbcPayments(JdbcTemplate jdbc){this.jdbc=jdbc;}

    record OrderRow(String status,String currency,UUID customer,String reference){}
    /** Locks the order row. Every money change for an order runs under this lock, so balances cannot race. */
    private OrderRow lockOrder(UUID orderId){
        var rows=jdbc.query("SELECT status,currency,customer_id,reference FROM app.customer_orders WHERE id=? FOR UPDATE",
                (r,n)->new OrderRow(r.getString(1),r.getString(2),r.getObject(3,UUID.class),r.getString(4)),orderId);
        if(rows.isEmpty())throw new AuthProblem(404,"Order not found.");
        return rows.getFirst();
    }
    private Balance balance(UUID orderId){
        return jdbc.queryForObject("SELECT * FROM app.order_balances WHERE order_id=?",(r,n)->balance(r),orderId);
    }
    private static Balance balance(ResultSet r) throws SQLException {
        return Balance.of(r.getString("status"),r.getString("currency"),r.getBigDecimal("amount_due_total"),r.getBigDecimal("paid"),r.getBigDecimal("reversed"),r.getBigDecimal("refunded"));
    }
    private BigDecimal pendingTotal(UUID orderId){
        return jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM app.payment_adjustments WHERE order_id=? AND status='PENDING'",BigDecimal.class,orderId);
    }

    public void record(UUID orderId,UUID actor,PaymentRequest request){
        var order=lockOrder(orderId);
        var existing=jdbc.query("SELECT order_id,method,provider,reference,amount FROM app.payments WHERE id=?",
                (r,n)->Objects.equals(r.getObject(1,UUID.class),orderId)&&r.getString(2).equals(request.method().name())
                        &&Objects.equals(r.getString(3),request.provider()==null?null:request.provider().name())&&Objects.equals(r.getString(4),request.reference())
                        &&r.getBigDecimal(5).compareTo(request.amount())==0,request.id());
        if(!existing.isEmpty()){
            if(existing.getFirst())return;
            throw new AuthProblem(409,"This payment was already saved with different details. Refresh the order.");
        }
        if(!PAYABLE.contains(order.status()))throw new AuthProblem(409,switch(order.status()){
            case "PENDING"->"Confirm the order first. Payments are recorded once the total is agreed.";
            default->"This order is "+order.status().toLowerCase(Locale.ROOT)+". No more payments can be recorded.";
        });
        var balance=balance(orderId);
        if(balance.due().signum()==0)throw new AuthProblem(409,"This order is already fully paid.");
        if(request.amount().compareTo(balance.due())>0)throw new AuthProblem(409,"That is more than the "+money(order.currency(),balance.due())+" still owed. Record at most the balance and hand back the rest.");
        if(request.reference()!=null){
            var used=jdbc.queryForList("SELECT receipt_number FROM app.payments WHERE provider=? AND reference=?",String.class,request.provider().name(),request.reference());
            if(!used.isEmpty())throw new AuthProblem(409,"This mobile money transaction is already recorded on receipt "+used.getFirst()+".");
        }
        String receipt=jdbc.queryForObject("""
                INSERT INTO app.payments(id,order_id,receipt_number,method,provider,reference,amount,currency,note,recorded_by)
                VALUES(?,?,'RCT-'||to_char(now() AT TIME ZONE 'Africa/Dar_es_Salaam','YYYY')||'-'||lpad(nextval('app.receipt_number_seq')::text,6,'0'),?,?,?,?,?,?,?)
                RETURNING receipt_number
                """,String.class,request.id(),orderId,request.method().name(),request.provider()==null?null:request.provider().name(),
                request.reference(),request.amount(),order.currency(),request.note(),actor);
        audit(actor,"PAYMENT_RECORDED",orderId,receipt);
        var after=balance(orderId);
        String how=request.method()==Method.CASH?"in cash":"by "+PROVIDER_NAMES.get(request.provider().name())+" (transaction "+request.reference()+")";
        notify(order.customer(),orderId,"Payment received",
                "We received "+money(order.currency(),request.amount())+" "+how+" for order "+order.reference()+". Receipt "+receipt+". "
                +(after.due().signum()==0?"Your order is fully paid.":"Balance still due: "+money(order.currency(),after.due())+".")
                +" This is a payment receipt from the shop, not a TRA fiscal (EFD) receipt.");
    }

    public void request(UUID orderId,UUID actor,AdjustmentRequest request){
        var order=lockOrder(orderId);
        var existing=jdbc.query("SELECT order_id,kind,payment_id,amount,reason FROM app.payment_adjustments WHERE id=?",
                (r,n)->Objects.equals(r.getObject(1,UUID.class),orderId)&&r.getString(2).equals(request.kind().name())&&Objects.equals(r.getObject(3,UUID.class),request.paymentId())
                        &&(request.amount()==null||r.getBigDecimal(4).compareTo(request.amount())==0)&&r.getString(5).equals(request.reason()),request.id());
        if(!existing.isEmpty()){
            if(existing.getFirst())return;
            throw new AuthProblem(409,"This request was already saved with different details. Refresh the order.");
        }
        BigDecimal amount=request.amount();
        if(request.kind()==Kind.REVERSAL){
            var payment=jdbc.query("SELECT amount,receipt_number FROM app.payments WHERE id=? AND order_id=?",(r,n)->r.getBigDecimal(1),request.paymentId(),orderId);
            if(payment.isEmpty())throw new AuthProblem(404,"That receipt is not on this order.");
            amount=payment.getFirst();
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.payment_adjustments WHERE payment_id=? AND kind='REVERSAL' AND status<>'REJECTED')",Boolean.class,request.paymentId())))
                throw new AuthProblem(409,"This receipt already has a cancellation request or is already cancelled.");
        }
        var balance=balance(orderId);
        BigDecimal available=balance.netPaid().subtract(pendingTotal(orderId));
        if(amount.compareTo(available)>0)throw new AuthProblem(409,available.signum()<=0
                ?"There is no paid money left to "+(request.kind()==Kind.REFUND?"refund":"cancel")+" on this order, counting requests still waiting for the Owner."
                :"At most "+money(order.currency(),available)+" can be "+(request.kind()==Kind.REFUND?"refunded":"cancelled")+", counting requests still waiting for the Owner.");
        jdbc.update("""
                INSERT INTO app.payment_adjustments(id,order_id,kind,payment_id,amount,reason,refund_method,refund_provider,requested_by)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,request.id(),orderId,request.kind().name(),request.paymentId(),amount,request.reason(),
                request.refundMethod()==null?null:request.refundMethod().name(),request.refundProvider()==null?null:request.refundProvider().name(),actor);
        audit(actor,request.kind()==Kind.REFUND?"REFUND_REQUESTED":"PAYMENT_REVERSAL_REQUESTED",orderId,request.id().toString());
    }

    record Pending(UUID order,String kind,String status,BigDecimal amount,String refundMethod,String refundProvider,String refundReference,UUID payment){}
    private Pending pendingRow(UUID adjustmentId,boolean lock){
        var rows=jdbc.query("SELECT order_id,kind,status,amount,refund_method,refund_provider,refund_reference,payment_id FROM app.payment_adjustments WHERE id=?"+(lock?" FOR UPDATE":""),
                (r,n)->new Pending(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getString(6),r.getString(7),r.getObject(8,UUID.class)),adjustmentId);
        if(rows.isEmpty())throw new AuthProblem(404,"Request not found.");
        return rows.getFirst();
    }
    public void approve(UUID adjustmentId,UUID actor,String reference,String note){
        var order=lockOrder(pendingRow(adjustmentId,false).order());
        var row=pendingRow(adjustmentId,true);
        boolean mobile="MOBILE_MONEY".equals(row.refundMethod());
        if(!row.status().equals("PENDING")){
            if(row.status().equals("APPROVED")&&Objects.equals(row.refundReference(),mobile?reference:null))return;
            throw new AuthProblem(409,"This request was already "+row.status().toLowerCase(Locale.ROOT)+". Refresh the list.");
        }
        if(mobile&&reference==null)throw new AuthProblem(400,"Enter the transaction ID of the mobile money refund you sent.");
        if(!mobile&&reference!=null)throw new AuthProblem(400,"Only a mobile money refund has a transaction ID.");
        var balance=balance(row.order());
        if(row.amount().compareTo(balance.netPaid())>0)
            throw new AuthProblem(409,"Only "+money(order.currency(),balance.netPaid())+" is paid on this order now. Reject this request and ask for a new one.");
        if(mobile&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.payment_adjustments WHERE refund_provider=? AND refund_reference=?)",Boolean.class,row.refundProvider(),reference)))
            throw new AuthProblem(409,"This mobile money transaction is already recorded on another refund.");
        jdbc.update("UPDATE app.payment_adjustments SET status='APPROVED',decided_by=?,decided_at=now(),decision_note=?,refund_reference=? WHERE id=?",actor,note,reference,adjustmentId);
        boolean refund=row.kind().equals("REFUND");
        audit(actor,refund?"REFUND_APPROVED":"PAYMENT_REVERSAL_APPROVED",row.order(),adjustmentId.toString());
        var after=balance(row.order());
        if(refund){
            String how=mobile?"by "+PROVIDER_NAMES.get(row.refundProvider())+" (transaction "+reference+")":"in cash";
            notify(order.customer(),row.order(),"Refund sent","The shop refunded "+money(order.currency(),row.amount())+" "+how+" for order "+order.reference()+".");
        }else{
            String receipt=jdbc.queryForObject("SELECT receipt_number FROM app.payments WHERE id=?",String.class,row.payment());
            notify(order.customer(),row.order(),"Receipt cancelled","Receipt "+receipt+" for order "+order.reference()+" was cancelled because it was recorded in error. "
                    +(after.due().signum()>0?"Balance due: "+money(order.currency(),after.due())+".":"Contact the shop if you have questions."));
        }
    }
    public void reject(UUID adjustmentId,UUID actor,String note){
        lockOrder(pendingRow(adjustmentId,false).order());
        var row=pendingRow(adjustmentId,true);
        if(!row.status().equals("PENDING")){
            if(row.status().equals("REJECTED"))return;
            throw new AuthProblem(409,"This request was already approved. Refresh the list.");
        }
        jdbc.update("UPDATE app.payment_adjustments SET status='REJECTED',decided_by=?,decided_at=now(),decision_note=? WHERE id=?",actor,note,adjustmentId);
        audit(actor,row.kind().equals("REFUND")?"REFUND_REJECTED":"PAYMENT_REVERSAL_REJECTED",row.order(),adjustmentId.toString());
    }

    private static final String RECEIPT_SQL="""
            SELECT p.*,s.name AS recorder,EXISTS(SELECT 1 FROM app.payment_adjustments a WHERE a.payment_id=p.id AND a.kind='REVERSAL' AND a.status='APPROVED') AS reversed
            FROM app.payments p JOIN app.staff_accounts s ON s.id=p.recorded_by WHERE p.order_id=? ORDER BY p.recorded_at,p.receipt_number
            """;
    private static Receipt receipt(ResultSet r,boolean staffView) throws SQLException {
        return new Receipt(r.getObject("id",UUID.class),r.getString("receipt_number"),r.getString("method"),r.getString("provider"),r.getString("reference"),
                r.getBigDecimal("amount"),r.getString("currency"),staffView?r.getString("note"):null,staffView?r.getString("recorder"):null,
                r.getTimestamp("recorded_at").toInstant(),r.getBoolean("reversed"));
    }
    private static final String ADJUSTMENT_SQL="""
            SELECT a.*,o.reference AS order_reference,p.receipt_number,rq.name AS requester,dc.name AS decider
            FROM app.payment_adjustments a JOIN app.customer_orders o ON o.id=a.order_id
            JOIN app.staff_accounts rq ON rq.id=a.requested_by LEFT JOIN app.staff_accounts dc ON dc.id=a.decided_by
            LEFT JOIN app.payments p ON p.id=a.payment_id
            """;
    private static Adjustment adjustment(ResultSet r) throws SQLException {
        Timestamp decided=r.getTimestamp("decided_at");
        return new Adjustment(r.getObject("id",UUID.class),r.getObject("order_id",UUID.class),r.getString("order_reference"),r.getString("kind"),
                r.getObject("payment_id",UUID.class),r.getString("receipt_number"),r.getBigDecimal("amount"),r.getString("reason"),r.getString("refund_method"),
                r.getString("refund_provider"),r.getString("refund_reference"),r.getString("status"),r.getString("requester"),r.getTimestamp("requested_at").toInstant(),
                r.getString("decider"),decided==null?null:decided.toInstant(),r.getString("decision_note"));
    }
    public Optional<Ledger> ledger(UUID orderId){
        var rows=jdbc.query("SELECT o.reference,o.status AS order_status,o.fulfilment,o.contact_name,o.contact_phone,b.* FROM app.customer_orders o JOIN app.order_balances b ON b.order_id=o.id WHERE o.id=?",
                (r,n)->new Ledger(orderId,r.getString("reference"),r.getString("order_status"),r.getString("fulfilment"),r.getString("contact_name"),r.getString("contact_phone"),balance(r),List.of(),List.of()),orderId);
        if(rows.isEmpty())return Optional.empty();
        var head=rows.getFirst();
        var receipts=jdbc.query(RECEIPT_SQL,(r,n)->receipt(r,true),orderId);
        var adjustments=jdbc.query(ADJUSTMENT_SQL+" WHERE a.order_id=? ORDER BY a.requested_at,a.id",(r,n)->adjustment(r),orderId);
        return Optional.of(new Ledger(orderId,head.reference(),head.orderStatus(),head.fulfilment(),head.customerName(),head.customerPhone(),head.balance(),receipts,adjustments));
    }
    public Page list(String query,boolean outstanding,int page,int size){
        String where=" WHERE (o.status NOT IN ('PENDING','REJECTED') OR b.paid>0)";
        List<Object> params=new ArrayList<>();
        // Outstanding: the customer still owes money, or the shop owes money back.
        if(outstanding)where+=" AND b.net_paid<>b.amount_due_total";
        if(!query.isEmpty()){where+=" AND strpos(lower(o.reference||' '||o.contact_name),?)>0";params.add(query);}
        String from=" FROM app.customer_orders o JOIN app.order_balances b ON b.order_id=o.id";
        Long total=jdbc.queryForObject("SELECT count(*)"+from+where,Long.class,params.toArray());
        params.add(size);params.add((long)page*size);
        var rows=jdbc.query("SELECT o.id,o.reference,o.status AS order_status,o.contact_name,o.created_at,b.*"+from+where+" ORDER BY o.created_at DESC,o.id LIMIT ? OFFSET ?",
                (r,n)->new LedgerRow(r.getObject("id",UUID.class),r.getString("reference"),r.getString("order_status"),r.getString("contact_name"),r.getTimestamp("created_at").toInstant(),balance(r)),params.toArray());
        return new Page(rows,total==null?0:total,page,size);
    }
    public List<Adjustment> pending(){
        return jdbc.query(ADJUSTMENT_SQL+" WHERE a.status='PENDING' ORDER BY a.requested_at,a.id LIMIT 100",(r,n)->adjustment(r));
    }
    public DaySummary summary(LocalDate date){
        // Days follow shop time (EAT). Sales are recognised when goods are delivered or collected (OD-10, approved).
        var received=jdbc.queryForMap("SELECT COALESCE(sum(amount) FILTER (WHERE method='CASH'),0) AS cash,COALESCE(sum(amount) FILTER (WHERE method='MOBILE_MONEY'),0) AS mobile,count(*) AS n FROM app.payments WHERE (recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date=?",date);
        var adjusted=jdbc.queryForMap("SELECT COALESCE(sum(amount) FILTER (WHERE kind='REVERSAL'),0) AS reversed,COALESCE(sum(amount) FILTER (WHERE kind='REFUND'),0) AS refunded FROM app.payment_adjustments WHERE status='APPROVED' AND (decided_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date=?",date);
        var sales=jdbc.queryForMap("SELECT COALESCE(sum(o.total),0) AS total,count(*) AS n FROM app.fulfilment_events e JOIN app.customer_orders o ON o.id=e.order_id WHERE e.status IN ('DELIVERED','COLLECTED') AND (e.recorded_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date=?",date);
        BigDecimal cash=(BigDecimal)received.get("cash"),mobile=(BigDecimal)received.get("mobile"),reversed=(BigDecimal)adjusted.get("reversed"),refunded=(BigDecimal)adjusted.get("refunded");
        BigDecimal in=cash.add(mobile);
        return new DaySummary(date,"TZS",cash,mobile,in,((Number)received.get("n")).intValue(),reversed,refunded,in.subtract(reversed).subtract(refunded),
                (BigDecimal)sales.get("total"),((Number)sales.get("n")).intValue());
    }
    public Optional<CustomerPayments> forCustomer(UUID customerId,UUID orderId){
        var owned=jdbc.query("SELECT b.* FROM app.customer_orders o JOIN app.order_balances b ON b.order_id=o.id WHERE o.id=? AND o.customer_id=?",(r,n)->balance(r),orderId,customerId);
        if(owned.isEmpty())return Optional.empty();
        return Optional.of(new CustomerPayments(owned.getFirst(),jdbc.query(RECEIPT_SQL,(r,n)->receipt(r,false),orderId)));
    }

    private void audit(UUID actor,String event,UUID orderId,String detail){
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference,metadata) VALUES(?,now(),?,'STAFF',?,'ORDER',?,jsonb_build_object('detail',?::text))",
                UUID.randomUUID(),actor,event,orderId.toString(),detail);
    }
    private void notify(UUID customer,UUID orderId,String title,String message){
        jdbc.update("INSERT INTO app.customer_notifications(id,customer_id,order_id,title,message) VALUES(?,?,?,?,?)",UUID.randomUUID(),customer,orderId,title,message);
    }
    static String money(String currency,BigDecimal amount){
        var format=new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.ROOT));
        return currency+" "+format.format(amount);
    }
}
