package africa.finishers.orders.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderModels {
    private OrderModels(){}
    public enum Branch { TARIME,MWANZA }
    public enum Fulfilment { DELIVERY,PICKUP }
    public record Selection(UUID variantId,BigDecimal quantity) {}
    public record ReviewedItem(UUID variantId,BigDecimal quantity,BigDecimal expectedPrice,String currency) {}
    public record Submission(List<ReviewedItem> items,Fulfilment fulfilment,String name,String phone,String address,String instructions,Branch branch) {}
    public record Line(UUID variantId,UUID productId,String productName,String variantLabel,String sku,String unit,
                       boolean fractional,BigDecimal quantity,BigDecimal unitPrice,String currency,BigDecimal lineTotal,BigDecimal available) {}
    public record Quote(List<Line> items,String currency,BigDecimal subtotal) {}
    public record OrderLine(UUID variantId,UUID productId,String productName,String variantLabel,String sku,String unit,
                            BigDecimal quantity,BigDecimal unitPrice,BigDecimal lineTotal) {}
    public record Summary(UUID id,String reference,String status,Fulfilment fulfilment,String currency,
                          BigDecimal subtotal,BigDecimal deliveryFee,BigDecimal total,Instant createdAt,Branch branch) {}
    public record Order(Summary summary,String name,String phone,String address,String instructions,List<OrderLine> items) {}
    public record OrderPage(List<Summary> items,long total,int page,int size) {}
}
