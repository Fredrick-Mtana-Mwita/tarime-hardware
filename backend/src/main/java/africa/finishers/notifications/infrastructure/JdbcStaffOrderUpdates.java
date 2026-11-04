package africa.finishers.notifications.infrastructure;

import africa.finishers.notifications.application.StaffOrderUpdates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcStaffOrderUpdates implements StaffOrderUpdates.Repository {
    private final JdbcTemplate jdbc;
    public JdbcStaffOrderUpdates(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<StaffOrderUpdates.Notice> list(UUID staffId){
        return jdbc.query("""
                SELECT n.*,o.reference,r.read_at FROM app.staff_order_notices n
                JOIN app.customer_orders o ON o.id=n.order_id
                LEFT JOIN app.staff_order_notice_reads r ON r.notice_id=n.id AND r.staff_id=?
                ORDER BY n.created_at DESC,n.id DESC LIMIT 50
                """,(r,n)->new StaffOrderUpdates.Notice(r.getObject("id",UUID.class),r.getObject("order_id",UUID.class),r.getString("reference"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("read_at")==null?null:r.getTimestamp("read_at").toInstant()),staffId);
    }
    public boolean read(UUID staffId,UUID id){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.staff_order_notices WHERE id=?)",Boolean.class,id)))return false;
        jdbc.update("INSERT INTO app.staff_order_notice_reads(staff_id,notice_id) VALUES(?,?) ON CONFLICT DO NOTHING",staffId,id);return true;
    }
}
