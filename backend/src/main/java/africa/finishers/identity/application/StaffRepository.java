package africa.finishers.identity.application;

import africa.finishers.identity.domain.StaffRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository {
    record Stored(StaffPrincipal profile,String passwordHash,boolean active,String encryptedSecret,long lastStep) {
        @Override public String toString(){return "StoredStaff[redacted]";}
    }
    Optional<Stored> findUsername(String username,boolean lock);
    Optional<Stored> findId(UUID id,boolean lock);
    void recordLogin(Stored staff,long step);
    boolean consumeRecovery(UUID id,String hash);
    record Account(StaffPrincipal profile,boolean active) {}
    List<Account> accounts();
    void create(UUID actor,UUID id,String username,String name,StaffRole role,String passwordHash);
    void changeAccess(UUID actor,UUID id,String name,StaffRole role,boolean active);
    void changePassword(UUID id,String hash);
    void audit(UUID actor,String event,UUID target);
}
