package africa.finishers.notifications.api;

import africa.finishers.identity.application.CustomerAccounts;
import africa.finishers.notifications.application.OrderNotifications;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class CustomerNotificationController {
    private final CustomerAccounts accounts;
    private final OrderNotifications notifications;
    public CustomerNotificationController(CustomerAccounts accounts,OrderNotifications notifications){this.accounts=accounts;this.notifications=notifications;}
    @GetMapping
    public List<OrderNotifications.Notice> list(Authentication auth){return notifications.list(accounts.profile(auth.getName()).id());}
    @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Authentication auth,@PathVariable UUID id){
        if(!notifications.markRead(accounts.profile(auth.getName()).id(),id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
