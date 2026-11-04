package africa.finishers.orders.application;

import java.util.Optional;
import java.util.UUID;
import static africa.finishers.orders.application.OrderModels.*;

public interface OrderRepository {
    void lockCustomer(UUID customerId);
    record Existing(UUID id,String hash) {}
    Optional<Existing> existing(UUID customerId,UUID key);
    boolean submissionLimitReached(UUID customerId);
    void save(UUID id,String reference,UUID customerId,UUID key,String hash,Submission request,Quote quote);
    Optional<Order> find(UUID customerId,UUID orderId);
    OrderPage list(UUID customerId,int page,int size);
}
