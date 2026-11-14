package africa.finishers.finance.api;

import africa.finishers.finance.application.FinanceModels.CustomerPayments;
import africa.finishers.finance.application.Payments;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** A signed-in customer sees the balance and receipts of their own order only. */
@RestController
public class CustomerPaymentController {
    private final Payments payments;
    public CustomerPaymentController(Payments payments){this.payments=payments;}
    @GetMapping("/api/orders/{id}/payments")
    public CustomerPayments payments(Authentication auth,@PathVariable UUID id){return payments.forCustomer(auth.getName(),id);}
}
