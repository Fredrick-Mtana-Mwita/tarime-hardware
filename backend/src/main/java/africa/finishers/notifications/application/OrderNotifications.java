package africa.finishers.notifications.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderNotifications {
    record Notice(UUID id,UUID orderId,String title,String message,Instant createdAt,Instant readAt) {}
    void submitted(UUID customerId,UUID orderId,String reference);
    List<Notice> list(UUID customerId);
    boolean markRead(UUID customerId,UUID id);
}
