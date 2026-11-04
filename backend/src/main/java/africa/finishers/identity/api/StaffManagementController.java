package africa.finishers.identity.api;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.domain.StaffRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/accounts")
public class StaffManagementController {
    public record Create(@NotBlank @Size(max=60) String username,@NotBlank @Size(max=100) String name,@NotNull StaffRole role,@NotNull @Size(min=12,max=128) String temporaryPassword){
        @Override public String toString(){return "CreateStaff[redacted]";}
    }
    public record Change(@Min(0) long revision,@NotBlank @Size(max=100) String name,@NotNull StaffRole role,@NotNull Boolean active) {}
    private final StaffAccounts staff;
    public StaffManagementController(StaffAccounts staff){this.staff=staff;}
    @GetMapping
    public List<StaffAccounts.ManagedAccount> list(Authentication auth){return staff.list(StaffAuthController.principal(auth));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StaffAccounts.ManagedAccount create(Authentication auth,@Valid @RequestBody Create body){return staff.create(StaffAuthController.principal(auth),body.username(),body.name(),body.role(),body.temporaryPassword());}
    @PutMapping("/{id}")
    public StaffAccounts.ManagedAccount change(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Change body){return staff.change(StaffAuthController.principal(auth),id,body.revision(),body.name(),body.role(),body.active());}
}
