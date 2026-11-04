package africa.finishers.orders.api;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.orders.application.StaffOrderReview;
import africa.finishers.orders.application.OrderModels;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/orders")
public class StaffOrderController {
    private final StaffOrderReview orders;
    private final africa.finishers.orders.application.OrderConfirmation confirmation;
    public StaffOrderController(StaffOrderReview orders,africa.finishers.orders.application.OrderConfirmation confirmation){this.orders=orders;this.confirmation=confirmation;}
    private StaffPrincipal actor(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;
    }
    @PostMapping("/{id}/confirm")
    public OrderModels.Order confirm(Authentication auth,@PathVariable UUID id,@RequestBody africa.finishers.orders.application.OrderConfirmation.Agreement agreement){return confirmation.confirm(actor(auth),id,agreement);}
    @GetMapping
    public StaffOrderReview.Page pending(Authentication auth,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="ALL") String fulfilment,
                                          @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return orders.pending(actor(auth),q,fulfilment,page,size);}
    @GetMapping("/{id}")
    public OrderModels.Order find(Authentication auth,@PathVariable UUID id){return orders.find(actor(auth),id);}
}
