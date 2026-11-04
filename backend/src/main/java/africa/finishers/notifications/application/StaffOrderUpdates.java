package africa.finishers.notifications.application;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StaffOrderUpdates {
    public record Notice(UUID id,UUID orderId,String reference,Instant createdAt,Instant readAt) {}
    public interface Repository { List<Notice> list(UUID staffId);boolean read(UUID staffId,UUID noticeId); }
    private final StaffAccounts staff;
    private final Repository repository;
    public StaffOrderUpdates(StaffAccounts staff,Repository repository){this.staff=staff;this.repository=repository;}
    @Transactional(readOnly=true)
    public List<Notice> list(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER,StaffRole.SALES);return repository.list(actor.id());}
    @Transactional
    public void read(StaffPrincipal actor,UUID id){staff.require(actor,StaffRole.OWNER,StaffRole.SALES);if(!repository.read(actor.id(),id))throw new AuthProblem(404,"Update not found.");}
}
