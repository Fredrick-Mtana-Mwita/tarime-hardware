package africa.finishers.catalogue.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Published, authoritative commerce values, optionally locked until the caller commits. */
public interface PurchasingCatalogue {
    record Item(UUID variantId,UUID productId,String name,String label,String sku,String unit,
                boolean fractional,BigDecimal price,String currency,BigDecimal available) {}
    default List<Item> items(List<UUID> ids,boolean lock){ return items(ids,lock,null); }
    List<Item> items(List<UUID> ids,boolean lock,String branch);
}
