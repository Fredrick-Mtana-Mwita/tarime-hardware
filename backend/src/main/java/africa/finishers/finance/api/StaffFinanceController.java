package africa.finishers.finance.api;

import africa.finishers.finance.application.FinanceModels.*;
import africa.finishers.finance.application.Payments;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/finance")
public class StaffFinanceController {
    private final Payments payments;
    public StaffFinanceController(Payments payments){this.payments=payments;}
    private static StaffPrincipal actor(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;
    }
    @GetMapping("/orders")
    public Page list(Authentication auth,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="OUTSTANDING") String filter,
                     @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return payments.list(actor(auth),q,filter,page,size);}
    @GetMapping("/orders/{id}")
    public Ledger ledger(Authentication auth,@PathVariable UUID id){return payments.ledger(actor(auth),id);}
    @PostMapping("/orders/{id}/payments")
    public Ledger record(Authentication auth,@PathVariable UUID id,@RequestBody PaymentRequest request){return payments.record(actor(auth),id,request);}
    @PostMapping("/orders/{id}/adjustments")
    public Ledger request(Authentication auth,@PathVariable UUID id,@RequestBody AdjustmentRequest request){return payments.request(actor(auth),id,request);}
    @GetMapping("/adjustments")
    public List<Adjustment> pending(Authentication auth){return payments.pending(actor(auth));}
    @PostMapping("/adjustments/{id}/approve")
    public ResponseEntity<Void> approve(Authentication auth,@PathVariable UUID id,@RequestBody(required=false) Decision decision){payments.approve(actor(auth),id,decision);return ResponseEntity.noContent().build();}
    @PostMapping("/adjustments/{id}/reject")
    public ResponseEntity<Void> reject(Authentication auth,@PathVariable UUID id,@RequestBody(required=false) Decision decision){payments.reject(actor(auth),id,decision);return ResponseEntity.noContent().build();}
    @GetMapping("/summary")
    public DaySummary summary(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return payments.summary(actor(auth),date);}
}
