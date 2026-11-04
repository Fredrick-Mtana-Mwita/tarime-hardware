package africa.finishers.identity.application;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    record Credentials(CustomerProfile profile,String passwordHash,boolean active) {
        @Override public String toString(){return "Credentials[redacted]";}
    }
    Optional<Credentials> findByPhone(String phone);
    void insert(UUID id,String name,String phone,String email,String passwordHash);
}
