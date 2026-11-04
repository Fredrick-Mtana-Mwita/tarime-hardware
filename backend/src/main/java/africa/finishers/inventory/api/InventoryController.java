package africa.finishers.inventory.api;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.inventory.application.BranchInventory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/inventory")
public class InventoryController {
    private final BranchInventory inventory;
    public InventoryController(BranchInventory inventory){this.inventory=inventory;}
    private StaffPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;}
    @GetMapping public BranchInventory.Page list(Authentication auth,@RequestParam String branch,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return inventory.list(actor(auth),branch,q,page,size);}
    @GetMapping("/receipts") public BranchInventory.History history(Authentication auth,@RequestParam String branch,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return inventory.history(actor(auth),branch,page,size);}
    @PostMapping("/receipts") public BranchInventory.Receipt receive(Authentication auth,@RequestBody BranchInventory.Receive request){return inventory.receive(actor(auth),request);}
}
