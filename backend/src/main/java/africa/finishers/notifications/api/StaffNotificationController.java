package africa.finishers.notifications.api;

import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.notifications.application.StaffOrderUpdates;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/notifications")
public class StaffNotificationController {
    private final StaffOrderUpdates updates;
    public StaffNotificationController(StaffOrderUpdates updates){this.updates=updates;}
    private StaffPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;}
    @GetMapping public List<StaffOrderUpdates.Notice> list(Authentication auth){return updates.list(actor(auth));}
    @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Authentication auth,@PathVariable UUID id){updates.read(actor(auth),id);}
}
