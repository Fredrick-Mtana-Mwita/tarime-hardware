package africa.finishers.orders.application;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderConfirmation {
    public record Agreement(BigDecimal deliveryFee,String note) {}
    public interface Writer { void confirm(UUID orderId,UUID actorId,Agreement agreement); }
    private final StaffAccounts staff;
    private final Writer writer;
    private final StaffOrderReview.Reader reader;
    public OrderConfirmation(StaffAccounts staff,Writer writer,StaffOrderReview.Reader reader){this.staff=staff;this.writer=writer;this.reader=reader;}
    @Transactional(timeout=20)
    public OrderModels.Order confirm(StaffPrincipal actor,UUID id,Agreement request){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        if(request==null||request.deliveryFee()==null||request.deliveryFee().signum()<0||request.deliveryFee().scale()>2||request.deliveryFee().precision()>19
            ||request.note()==null||request.note().strip().length()<10||request.note().length()>500)
            throw new OrderProblem(400,"Enter a valid agreed fee and record how and when the customer agreed to the branch, materials and final total.");
        writer.confirm(id,actor.id(),new Agreement(request.deliveryFee().setScale(2),request.note().strip()));
        return reader.find(id).orElseThrow(()->new OrderProblem(404,"Order not found."));
    }
}
