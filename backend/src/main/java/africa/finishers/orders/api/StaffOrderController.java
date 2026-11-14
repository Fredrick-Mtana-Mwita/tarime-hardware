package africa.finishers.orders.api;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.orders.application.StaffOrderReview;
import africa.finishers.orders.application.OrderModels;
import africa.finishers.orders.application.OrderDecisions;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/orders")
public class StaffOrderController {
    private final StaffOrderReview orders;
    private final africa.finishers.orders.application.OrderConfirmation confirmation;
    private final OrderDecisions decisions;
    public StaffOrderController(StaffOrderReview orders,africa.finishers.orders.application.OrderConfirmation confirmation,OrderDecisions decisions){this.orders=orders;this.confirmation=confirmation;this.decisions=decisions;}
    private StaffPrincipal actor(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;
    }
    @PostMapping("/{id}/confirm")
    public OrderModels.Order confirm(Authentication auth,@PathVariable UUID id,@RequestBody africa.finishers.orders.application.OrderConfirmation.Agreement agreement){return confirmation.confirm(actor(auth),id,agreement);}
    @PostMapping("/{id}/reject")
    public OrderModels.Order reject(Authentication auth,@PathVariable UUID id,@RequestBody OrderDecisions.Reason reason){return decisions.reject(actor(auth),id,reason);}
    @PostMapping("/{id}/cancel")
    public OrderModels.Order cancel(Authentication auth,@PathVariable UUID id,@RequestBody OrderDecisions.Reason reason){return decisions.cancel(actor(auth),id,reason);}
    @GetMapping
    public StaffOrderReview.Page pending(Authentication auth,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="ALL") String fulfilment,
                                          @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return orders.pending(actor(auth),q,fulfilment,page,size);}
    @GetMapping("/{id}")
    public OrderModels.Order find(Authentication auth,@PathVariable UUID id){return orders.find(actor(auth),id);}
}
