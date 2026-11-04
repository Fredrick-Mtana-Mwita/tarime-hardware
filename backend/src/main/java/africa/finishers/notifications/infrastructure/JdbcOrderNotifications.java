package africa.finishers.notifications.infrastructure;

import africa.finishers.notifications.application.OrderNotifications;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOrderNotifications implements OrderNotifications {
    private final JdbcTemplate jdbc;
    public JdbcOrderNotifications(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void submitted(UUID customerId,UUID orderId,String reference){
        jdbc.update("INSERT INTO app.customer_notifications(id,customer_id,order_id,title,message) VALUES(?,?,?,?,?)",
                UUID.randomUUID(),customerId,orderId,"Order request received",
                "Your request "+reference+" is pending shop review. Stock is reserved only after confirmation.");
        jdbc.update("INSERT INTO app.staff_order_notices(id,order_id) VALUES(?,?)",UUID.randomUUID(),orderId);
    }
    @Transactional(readOnly=true)
    public List<Notice> list(UUID customerId){
        return jdbc.query("SELECT * FROM app.customer_notifications WHERE customer_id=? ORDER BY created_at DESC,id DESC LIMIT 50",
                (r,n)->new Notice(r.getObject("id",UUID.class),r.getObject("order_id",UUID.class),r.getString("title"),
                        r.getString("message"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("read_at")==null?null:r.getTimestamp("read_at").toInstant()),customerId);
    }
    @Transactional
    public boolean markRead(UUID customerId,UUID id){
        return jdbc.update("UPDATE app.customer_notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND customer_id=?",id,customerId)==1;
    }
}
