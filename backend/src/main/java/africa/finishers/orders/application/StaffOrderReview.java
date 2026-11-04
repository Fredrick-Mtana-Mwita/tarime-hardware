package africa.finishers.orders.application;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class StaffOrderReview {
    public record RequestRow(OrderModels.Summary summary,String contactName,int itemCount) {}
    public record Page(List<RequestRow> items,long total,long pendingCount,int page,int size) {}
    public interface Reader {
        Page pending(String query,String fulfilment,int page,int size);
        java.util.Optional<OrderModels.Order> find(UUID id);
    }
    private final StaffAccounts staff;
    private final Reader reader;
    public StaffOrderReview(StaffAccounts staff,Reader reader){this.staff=staff;this.reader=reader;}
    @Transactional(readOnly=true)
    public Page pending(StaffPrincipal actor,String query,String fulfilment,int page,int size){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        String q=query==null?"":query.strip();
        if(q.length()>100||page<0||page>10000||size<1||size>50||!List.of("ALL","DELIVERY","PICKUP").contains(fulfilment))throw new OrderProblem(400,"Check the search, fulfilment filter and page.");
        return reader.pending(q,fulfilment,page,size);
    }
    @Transactional(readOnly=true)
    public OrderModels.Order find(StaffPrincipal actor,UUID id){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        return reader.find(id).orElseThrow(()->new OrderProblem(404,"Order request not found."));
    }
}
