package africa.finishers.catalogue.infrastructure;

import africa.finishers.catalogue.application.CatalogueReader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static africa.finishers.catalogue.application.CatalogueModels.*;

@Repository
@Transactional(readOnly=true)
public class JdbcCatalogueReader implements CatalogueReader {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcCatalogueReader(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    private static final RowMapper<Product> PRODUCT=(r,n)->new Product(r.getObject("id",UUID.class),r.getString("sku"),
            r.getString("slug"),r.getString("name"),r.getString("brand"),r.getString("description"),
            r.getString("category_slug"),r.getString("category_name"),r.getBigDecimal("price"),r.getString("currency"),
            r.getString("unit"),r.getString("image_path"),r.getString("image_alt"),r.getBoolean("in_stock"),r.getBoolean("featured"),r.getBoolean("sample_data"));

    public List<Category> categories() {
        return jdbc.query("""
                SELECT c.*, (SELECT count(*) FROM app.public_catalogue_products p WHERE p.category_id=c.id) AS product_count
                FROM app.categories c WHERE c.published AND NOT c.archived ORDER BY c.sort_order,c.name,c.id
                """,Map.of(),(r,n)->new Category(r.getObject("id",UUID.class),r.getString("slug"),r.getString("name"),
                r.getString("description"),r.getString("image_path"),r.getLong("product_count")));
    }
    public ProductPage products(Filter filter) {
        StringBuilder where=new StringBuilder(" WHERE TRUE");
        var params=new MapSqlParameterSource();
        if(!filter.query().isBlank()) {where.append(" AND strpos(lower(name || ' ' || brand || ' ' || sku),:q)>0");params.addValue("q",filter.query().toLowerCase(java.util.Locale.ROOT));}
        if(!filter.category().isBlank()) {where.append(" AND category_slug=:category");params.addValue("category",filter.category());}
        if(filter.minPrice()!=null){where.append(" AND price>=:minPrice");params.addValue("minPrice",filter.minPrice());}
        if(filter.maxPrice()!=null){where.append(" AND price<=:maxPrice");params.addValue("maxPrice",filter.maxPrice());}
        if(filter.inStock())where.append(" AND in_stock");
        if(filter.featured())where.append(" AND featured");
        String order=switch(filter.sort()) {
            case "price-asc" -> "price ASC NULLS LAST,name,id";
            case "price-desc" -> "price DESC NULLS LAST,name,id";
            case "name" -> "name,id";
            default -> "featured DESC,name,id";
        };
        Long total=jdbc.queryForObject("SELECT count(*) FROM app.public_catalogue_products"+where,params,Long.class);
        params.addValue("limit",filter.size()).addValue("offset",(long)filter.page()*filter.size());
        var items=jdbc.query("SELECT * FROM app.public_catalogue_products"+where+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",params,PRODUCT);
        return new ProductPage(items,total==null?0:total,filter.page(),filter.size());
    }
    public Optional<Detail> product(UUID id) {
        var params=Map.of("id",id);
        var rows=jdbc.query("SELECT * FROM app.public_catalogue_products WHERE id=:id",params,PRODUCT);
        if(rows.isEmpty())return Optional.empty();
        var variants=jdbc.query("""
                SELECT v.*,u.label AS unit,u.fractional,COALESCE((SELECT sum(s.on_hand-s.reserved) FROM app.stock_balances s WHERE s.variant_id=v.id),0) AS available
                FROM app.product_variants v JOIN app.units u ON u.code=v.unit_code
                WHERE v.product_id=:id AND v.published ORDER BY v.sort_order,v.id
                """,params,(r,n)->new Variant(r.getObject("id",UUID.class),r.getString("sku"),r.getString("label"),r.getString("unit"),
                r.getBigDecimal("unit_price"),r.getString("currency"),r.getString("pricing_mode"),r.getBigDecimal("available"),r.getBigDecimal("available").signum()>0,r.getBoolean("fractional")));
        var images=jdbc.query("SELECT path,alt_text FROM app.product_images WHERE product_id=:id ORDER BY sort_order,id",params,(r,n)->new Image(r.getString(1),r.getString(2)));
        var specs=jdbc.query("SELECT label,value FROM app.product_specifications WHERE product_id=:id ORDER BY sort_order,label",params,(r,n)->new Specification(r.getString(1),r.getString(2)));
        return Optional.of(new Detail(rows.getFirst(),variants,images,specs));
    }
}
