package africa.finishers.orders.api;

import africa.finishers.orders.application.CustomerOrders;
import africa.finishers.orders.application.OrderModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CustomerOrderController {
    private final CustomerOrders orders;
    public CustomerOrderController(CustomerOrders orders){this.orders=orders;}
    public record Cart(@NotNull @Size(min=1,max=50) List<@NotNull Selection> items,Branch branch) {}
    @PostMapping("/cart/quote")
    public Quote quote(@Valid @RequestBody Cart cart){return orders.quote(cart.items(),cart.branch());}
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED)
    public Order submit(Authentication auth,@RequestHeader("Idempotency-Key") UUID key,@RequestBody Submission request){
        return orders.submit(auth.getName(),key,request);
    }
    @GetMapping("/orders")
    public OrderPage list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return orders.list(auth.getName(),page,size);}
    @GetMapping("/orders/{id}")
    public Order find(Authentication auth,@PathVariable UUID id){return orders.find(auth.getName(),id);}
}
