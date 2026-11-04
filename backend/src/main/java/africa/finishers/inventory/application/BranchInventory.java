package africa.finishers.inventory.application;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BranchInventory {
    public record Item(UUID variantId,String productName,String variantLabel,String sku,String unit,boolean fractional,
                       String category,String image,BigDecimal onHand,BigDecimal reserved,BigDecimal available,boolean lowStock) {}
    public record Page(List<Item> items,long total,long lowStockCount,int page,int size) {}
    public record Receive(UUID id,UUID variantId,String branch,BigDecimal quantity,String sourceReference,String reason) {}
    public record Receipt(UUID id,UUID variantId,String branch,String productName,String variantLabel,String sku,String unit,
                          BigDecimal quantity,String sourceReference,String reason,BigDecimal onHandAfter,BigDecimal reservedAfter,
                          UUID actorId,String actorName,Instant receivedAt) {}
    public record History(List<Receipt> items,long total,int page,int size) {}
    public interface Store {
        Page list(String branch,String query,int page,int size);
        History history(String branch,int page,int size);
        Receipt receive(UUID actor,Receive request);
    }
    private final StaffAccounts staff;
    private final Store store;
    public BranchInventory(StaffAccounts staff,Store store){this.staff=staff;this.store=store;}
    private String branch(String branch){if(!List.of("TARIME","MWANZA").contains(branch==null?"":branch))throw new AuthProblem(400,"Choose Tarime or Mwanza.");return branch;}
    private void access(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER,StaffRole.INVENTORY);}
    private void paging(int page,int size){if(page<0||page>100000||size<1||size>48)throw new AuthProblem(400,"Invalid page size or page number.");}
    @Transactional(readOnly=true)
    public Page list(StaffPrincipal actor,String branch,String query,int page,int size){
        access(actor);paging(page,size);if(query==null||query.length()>100)throw new AuthProblem(400,"Search must be at most 100 characters.");
        return store.list(branch(branch),query.strip(),page,size);
    }
    @Transactional(readOnly=true)
    public History history(StaffPrincipal actor,String branch,int page,int size){access(actor);paging(page,size);return store.history(branch(branch),page,size);}
    @Transactional(timeout=20)
    public Receipt receive(StaffPrincipal actor,Receive request){
        access(actor);
        if(request==null||request.id()==null||request.variantId()==null||request.quantity()==null||request.quantity().signum()<=0
            ||request.quantity().stripTrailingZeros().scale()>3||request.quantity().compareTo(new BigDecimal("999999999.999"))>0)
            throw new AuthProblem(400,"Enter a positive quantity with at most three decimal places.");
        String source=text(request.sourceReference(),2,160,"Enter a source or delivery reference of 2–160 characters.");
        String reason=text(request.reason(),5,500,"Record a receipt reason of 5–500 characters.");
        return store.receive(actor.id(),new Receive(request.id(),request.variantId(),branch(request.branch()),request.quantity(),source,reason));
    }
    private String text(String value,int min,int max,String message){
        if(value==null||value.strip().length()<min||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw new AuthProblem(400,message);
        return value.strip();
    }
}
