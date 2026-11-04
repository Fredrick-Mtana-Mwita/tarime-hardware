package africa.finishers.inventory.infrastructure;

import africa.finishers.inventory.application.BranchInventory;
import africa.finishers.inventory.application.BranchInventory.*;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class JdbcBranchInventory implements BranchInventory.Store {
    private final JdbcTemplate jdbc;
    public JdbcBranchInventory(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String JOINS=" FROM app.product_variants v JOIN app.products p ON p.id=v.product_id JOIN app.units u ON u.code=v.unit_code JOIN app.categories c ON c.id=p.category_id LEFT JOIN app.stock_balances s ON s.variant_id=v.id AND s.branch_code=? ";
    private static final String FILTER=" WHERE NOT p.archived AND NOT c.archived AND (position(lower(?) in lower(p.name||' '||v.label||' '||v.sku))>0) ";
    public Page list(String branch,String query,int page,int size){
        var items=jdbc.query("SELECT v.id,p.name,v.label,v.sku,u.label unit,u.fractional,c.name category,COALESCE((SELECT i.path FROM app.product_images i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1),'') image,COALESCE(s.on_hand,0) on_hand,COALESCE(s.reserved,0) reserved,COALESCE(s.on_hand-s.reserved,0) available,COALESCE(s.on_hand-s.reserved,0)<=COALESCE(s.low_stock_threshold,0) low_stock"+JOINS+FILTER+" ORDER BY p.name,v.sort_order,v.id LIMIT ? OFFSET ?",
            (r,n)->new Item(r.getObject("id",UUID.class),r.getString("name"),r.getString("label"),r.getString("sku"),r.getString("unit"),r.getBoolean("fractional"),r.getString("category"),r.getString("image"),r.getBigDecimal("on_hand"),r.getBigDecimal("reserved"),r.getBigDecimal("available"),r.getBoolean("low_stock")),branch,query,size,page*size);
        long total=jdbc.queryForObject("SELECT count(*)"+JOINS+FILTER,Long.class,branch,query);
        long low=jdbc.queryForObject("SELECT count(*)"+JOINS+FILTER+" AND COALESCE(s.on_hand-s.reserved,0)<=COALESCE(s.low_stock_threshold,0)",Long.class,branch,query);
        return new Page(items,total,low,page,size);
    }
    private static final String RECEIPTS="SELECT r.*,a.name actor_name FROM app.stock_receipts r JOIN app.staff_accounts a ON a.id=r.actor_id ";
    private static final RowMapper<Receipt> RECEIPT=(r,n)->new Receipt(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),r.getString("branch_code"),r.getString("product_name"),r.getString("variant_label"),r.getString("sku"),r.getString("unit_label"),r.getBigDecimal("quantity"),r.getString("source_reference"),r.getString("reason"),r.getBigDecimal("on_hand_after"),r.getBigDecimal("reserved_after"),r.getObject("actor_id",UUID.class),r.getString("actor_name"),r.getTimestamp("received_at").toInstant());
    public History history(String branch,int page,int size){
        return new History(jdbc.query(RECEIPTS+"WHERE r.branch_code=? ORDER BY r.received_at DESC,r.id LIMIT ? OFFSET ?",RECEIPT,branch,size,page*size),jdbc.queryForObject("SELECT count(*) FROM app.stock_receipts WHERE branch_code=?",Long.class,branch),page,size);
    }
    public Receipt receive(UUID actor,Receive request){
        // Serialize identical request identities before looking up a previous result. Hash collisions only serialize unrelated receipts.
        jdbc.query("SELECT pg_advisory_xact_lock(?)",r->{},request.id().getMostSignificantBits()^request.id().getLeastSignificantBits());
        var existing=jdbc.query(RECEIPTS+"WHERE r.id=?",RECEIPT,request.id());
        if(!existing.isEmpty()){
            var old=existing.getFirst();
            if(!old.actorId().equals(actor)||!old.variantId().equals(request.variantId())||!old.branch().equals(request.branch())||old.quantity().compareTo(request.quantity())!=0||!old.sourceReference().equals(request.sourceReference())||!old.reason().equals(request.reason()))
                throw new AuthProblem(409,"This receipt request was already used with different details. Refresh before starting a new receipt.");
            return old;
        }
        record Variant(String name,String label,String sku,String unit,boolean fractional){}
        var variants=jdbc.query("SELECT p.name,v.label,v.sku,u.label unit,u.fractional FROM app.product_variants v JOIN app.products p ON p.id=v.product_id JOIN app.units u ON u.code=v.unit_code JOIN app.categories c ON c.id=p.category_id WHERE v.id=? AND NOT p.archived AND NOT c.archived FOR SHARE OF v,p,u,c",(r,n)->new Variant(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5)),request.variantId());
        if(variants.isEmpty())throw new AuthProblem(404,"This product option is no longer available for receiving.");
        var v=variants.getFirst();
        if(!v.fractional()&&request.quantity().stripTrailingZeros().scale()>0)throw new AuthProblem(400,"This product option requires a whole quantity.");
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code) VALUES(?,?) ON CONFLICT DO NOTHING",request.variantId(),request.branch());
        // Add atomically; never overwrite a concurrent reservation or receipt.
        int updated=jdbc.update("UPDATE app.stock_balances SET on_hand=on_hand+?,version=version+1 WHERE variant_id=? AND branch_code=? AND on_hand<=9999999999999999.999-?",request.quantity(),request.variantId(),request.branch(),request.quantity());
        if(updated!=1)throw new AuthProblem(409,"The resulting stock balance is too large.");
        var balance=jdbc.queryForObject("SELECT on_hand,reserved FROM app.stock_balances WHERE variant_id=? AND branch_code=?",(r,n)->new BigDecimal[]{r.getBigDecimal(1),r.getBigDecimal(2)},request.variantId(),request.branch());
        jdbc.update("INSERT INTO app.stock_receipts(id,actor_id,variant_id,branch_code,product_name,variant_label,sku,unit_label,quantity,source_reference,reason,on_hand_after,reserved_after) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",request.id(),actor,request.variantId(),request.branch(),v.name(),v.label(),v.sku(),v.unit(),request.quantity(),request.sourceReference(),request.reason(),balance[0],balance[1]);
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF','STOCK_RECEIVED','STOCK_RECEIPT',?)",UUID.randomUUID(),actor,request.id().toString());
        return jdbc.queryForObject(RECEIPTS+"WHERE r.id=?",RECEIPT,request.id());
    }
}
