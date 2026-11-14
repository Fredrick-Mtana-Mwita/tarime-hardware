package africa.finishers.fulfilment;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import africa.finishers.orders.application.OrderProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/fulfilment")
public class HandoverController {
    private final StaffAccounts staff;
    private final JdbcTemplate db;
    public HandoverController(StaffAccounts staff, JdbcTemplate db) {
        this.staff = staff;
        this.db = db;
    }
    @ExceptionHandler(OrderProblem.class)
    public org.springframework.http.ProblemDetail conflict(OrderProblem problem) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(problem.status()), problem.getMessage());
    }
    @ExceptionHandler(africa.finishers.identity.application.AuthProblem.class)
    public org.springframework.http.ProblemDetail denied(africa.finishers.identity.application.AuthProblem problem) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(problem.status()), problem.getMessage());
    }
    @GetMapping
    public Map<String,Object> queue(@AuthenticationPrincipal StaffPrincipal actor) {
        staff.require(actor, StaffRole.OWNER, StaffRole.SALES, StaffRole.DELIVERY);
        boolean driver = actor.role() == StaffRole.DELIVERY;
        String query = """
                SELECT id,reference,status,branch_code,fulfilment,contact_name,contact_phone,address,instructions
                FROM app.customer_orders WHERE branch_code IS NOT NULL AND status IN ('CONFIRMED','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY')
                """;
        var orders = driver ? db.queryForList(query+" AND driver_id=? ORDER BY created_at,id", actor.id())
                            : db.queryForList(query+" ORDER BY created_at,id");
        var drivers = driver ? List.of() : db.queryForList("SELECT id,name FROM app.staff_accounts WHERE role='DELIVERY' AND active ORDER BY name,id");
        return Map.of("orders", orders, "drivers", drivers);
    }
    public record Change(String target, UUID driver, String recipient, Boolean acknowledged) {}
    @PostMapping("/{id}")
    @Transactional
    public Map<String,String> update(@AuthenticationPrincipal StaffPrincipal actor,@PathVariable UUID id,@RequestBody Change change) {
        staff.require(actor, StaffRole.OWNER, StaffRole.SALES, StaffRole.DELIVERY);
        var rows = db.queryForList("SELECT status,fulfilment,branch_code,driver_id FROM app.customer_orders WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty() || rows.getFirst().get("branch_code")==null) throw new OrderProblem(404,"Job unavailable.");
        var order = rows.getFirst();
        boolean driver = actor.role()==StaffRole.DELIVERY;
        if (driver && !actor.id().equals(order.get("driver_id"))) throw new OrderProblem(404,"Job unavailable.");
        String current = (String)order.get("status");
        boolean pickup = "PICKUP".equals(order.get("fulfilment"));
        String next = switch(current) {
            case "CONFIRMED" -> pickup ? "READY_FOR_PICKUP" : "READY_FOR_DELIVERY";
            case "READY_FOR_PICKUP" -> "COLLECTED";
            case "READY_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
            case "OUT_FOR_DELIVERY" -> "DELIVERED";
            default -> "";
        };
        if (current.equals(change.target())) return Map.of("status",current);
        if (next.isEmpty() || !next.equals(change.target())) throw new OrderProblem(409,"Refresh the job before continuing.");
        boolean driving = next.equals("OUT_FOR_DELIVERY") || next.equals("DELIVERED");
        if ((driver && !driving) || (driving && actor.role()==StaffRole.SALES)) throw new OrderProblem(403,"This action requires the assigned driver or Owner.");
        UUID assigned = (UUID)order.get("driver_id");
        if (next.equals("READY_FOR_DELIVERY")) {
            assigned = change.driver();
            if (db.queryForList("SELECT id FROM app.staff_accounts WHERE id=? AND active AND role='DELIVERY' FOR SHARE",assigned).isEmpty())
                throw new OrderProblem(400,"Select an active delivery driver.");
        }
        String recipient = null;
        if (next.equals("COLLECTED") || next.equals("DELIVERED")) {
            recipient = change.recipient()==null ? "" : change.recipient().strip();
            if (!Boolean.TRUE.equals(change.acknowledged()) || recipient.isBlank() || recipient.length()>100)
                throw new OrderProblem(400,"Enter the recipient name and acknowledge the handover.");
        }
        if ((next.equals("OUT_FOR_DELIVERY") || next.equals("COLLECTED")) &&
            !Boolean.TRUE.equals(db.queryForObject("SELECT app.consume_order_reservation(?)", Boolean.class,id)))
            throw new OrderProblem(409,"No remaining reservation to hand over.");
        db.update("UPDATE app.customer_orders SET status=?,driver_id=? WHERE id=?",next,assigned,id);
        db.update("INSERT INTO app.fulfilment_events(order_id,status,branch_code,actor_id,recipient,driver_id) VALUES(?,?,?,?,?,?)",id,next,order.get("branch_code"),actor.id(),recipient,assigned);
        return Map.of("status",next);
    }
}
