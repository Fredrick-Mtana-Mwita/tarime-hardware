package africa.finishers.orders.infrastructure;

import africa.finishers.orders.application.OrderModels.Decision;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

final class OrderDecisionRows {
    private OrderDecisionRows(){}
    static Decision find(JdbcTemplate jdbc,UUID orderId){
        var rows=jdbc.query("SELECT decision,reason,decided_at FROM app.order_decisions WHERE order_id=?",
                (r,n)->new Decision(r.getString(1),r.getString(2),r.getTimestamp(3).toInstant()),orderId);
        return rows.isEmpty()?null:rows.getFirst();
    }
}
