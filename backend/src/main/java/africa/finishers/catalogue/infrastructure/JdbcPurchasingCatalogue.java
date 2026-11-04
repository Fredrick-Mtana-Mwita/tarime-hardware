package africa.finishers.catalogue.infrastructure;

import africa.finishers.catalogue.application.PurchasingCatalogue;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcPurchasingCatalogue implements PurchasingCatalogue {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcPurchasingCatalogue(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Item> items(List<UUID> ids,boolean lock,String branch){
        // Shared row locks prevent price/publication/stock changes during submission. No stock mutation.
        // All callers lock variants in the same order. The order service owns the transaction.
        return jdbc.query("""
                SELECT v.id,v.product_id,p.name,v.label,v.sku,u.label AS unit,u.fractional,
                       v.unit_price,v.currency,COALESCE((SELECT sum(s.on_hand-s.reserved) FROM app.stock_balances s WHERE s.variant_id=v.id AND (:branch='' OR s.branch_code=:branch)),0) AS available
                FROM app.product_variants v JOIN app.products p ON p.id=v.product_id
                JOIN app.categories c ON c.id=p.category_id JOIN app.units u ON u.code=v.unit_code
                WHERE v.id IN (:ids) AND v.published AND p.published AND NOT p.archived
                AND c.published AND NOT c.archived AND v.pricing_mode='FIXED'
                ORDER BY v.id
                """+(lock?" FOR SHARE OF v,p,c,u":""),Map.of("ids",ids,"branch",branch==null?"":branch),
                (r,n)->new Item(r.getObject("id",UUID.class),r.getObject("product_id",UUID.class),r.getString("name"),
                        r.getString("label"),r.getString("sku"),r.getString("unit"),r.getBoolean("fractional"),
                        r.getBigDecimal("unit_price"),r.getString("currency"),r.getBigDecimal("available")));
    }
}
