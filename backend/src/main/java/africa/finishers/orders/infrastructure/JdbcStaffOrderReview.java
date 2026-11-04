package africa.finishers.orders.infrastructure;

import africa.finishers.orders.application.StaffOrderReview;
import africa.finishers.orders.application.OrderModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStaffOrderReview implements StaffOrderReview.Reader {
    private final JdbcTemplate jdbc;
    public JdbcStaffOrderReview(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Summary summary(ResultSet r) throws SQLException {
        return new Summary(r.getObject("id",UUID.class),r.getString("reference"),r.getString("status"),Fulfilment.valueOf(r.getString("fulfilment")),
                r.getString("currency"),r.getBigDecimal("subtotal"),r.getBigDecimal("delivery_fee"),r.getBigDecimal("total"),r.getTimestamp("created_at").toInstant(),r.getString("branch_code")==null?null:Branch.valueOf(r.getString("branch_code")));
    }
    public StaffOrderReview.Page pending(String query,String fulfilment,int page,int size){
        String where=" WHERE status='PENDING'";
        List<Object> params=new ArrayList<>();
        if(!query.isEmpty()){where+=" AND strpos(lower(reference || ' ' || contact_name),?)>0";params.add(query.toLowerCase(Locale.ROOT));}
        if(!fulfilment.equals("ALL")){where+=" AND fulfilment=?";params.add(fulfilment);}
        Long total=jdbc.queryForObject("SELECT count(*) FROM app.customer_orders"+where,Long.class,params.toArray());
        params.add(size);params.add((long)page*size);
        var rows=jdbc.query("SELECT o.*,(SELECT count(*) FROM app.customer_order_items i WHERE i.order_id=o.id) AS item_count FROM app.customer_orders o"+where+" ORDER BY created_at,id LIMIT ? OFFSET ?",
                (r,n)->new StaffOrderReview.RequestRow(summary(r),r.getString("contact_name"),r.getInt("item_count")),params.toArray());
        Long pending=jdbc.queryForObject("SELECT count(*) FROM app.customer_orders WHERE status='PENDING'",Long.class);
        return new StaffOrderReview.Page(rows,total==null?0:total,pending==null?0:pending,page,size);
    }
    public Optional<Order> find(UUID id){
        var rows=jdbc.query("SELECT * FROM app.customer_orders WHERE id=?",(r,n)->new Order(summary(r),r.getString("contact_name"),r.getString("contact_phone"),r.getString("address"),r.getString("instructions"),List.of()),id);
        if(rows.isEmpty())return Optional.empty();
        var items=jdbc.query("SELECT * FROM app.customer_order_items WHERE order_id=? ORDER BY product_name,variant_id",
                (r,n)->new OrderLine(r.getObject("variant_id",UUID.class),r.getObject("product_id",UUID.class),r.getString("product_name"),r.getString("variant_label"),r.getString("sku"),r.getString("unit"),r.getBigDecimal("quantity"),r.getBigDecimal("unit_price"),r.getBigDecimal("line_total")),id);
        var row=rows.getFirst();return Optional.of(new Order(row.summary(),row.name(),row.phone(),row.address(),row.instructions(),items));
    }
}
