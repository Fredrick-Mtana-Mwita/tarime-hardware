package africa.finishers.orders.application;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** FR-14: Owner and Sales reject pending requests or cancel orders before handover, always with a customer-facing reason. */
@Service
public class OrderDecisions {
    public enum Kind { REJECTED,CANCELLED }
    public record Reason(String reason) {}
    public interface Writer { void decide(UUID orderId,UUID actorId,Kind kind,String reason); }
    private final StaffAccounts staff;
    private final Writer writer;
    private final StaffOrderReview.Reader reader;
    public OrderDecisions(StaffAccounts staff,Writer writer,StaffOrderReview.Reader reader){this.staff=staff;this.writer=writer;this.reader=reader;}
    @Transactional(timeout=20)
    public OrderModels.Order reject(StaffPrincipal actor,UUID id,Reason request){return decide(actor,id,Kind.REJECTED,request);}
    @Transactional(timeout=20)
    public OrderModels.Order cancel(StaffPrincipal actor,UUID id,Reason request){return decide(actor,id,Kind.CANCELLED,request);}
    private OrderModels.Order decide(StaffPrincipal actor,UUID id,Kind kind,Reason request){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        String reason=request==null||request.reason()==null?"":request.reason().strip();
        if(reason.length()<10||reason.length()>500||reason.chars().anyMatch(c->c<32&&c!='\n'))
            throw new OrderProblem(400,"Enter a reason of 10 to 500 characters. The customer will see it.");
        writer.decide(id,actor.id(),kind,reason);
        return reader.find(id).orElseThrow(()->new OrderProblem(404,"Order not found."));
    }
}
