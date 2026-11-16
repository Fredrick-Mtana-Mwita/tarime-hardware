package africa.finishers.catalogue.infrastructure;

import africa.finishers.catalogue.application.CatalogueAdmin;
import africa.finishers.catalogue.application.CatalogueAdmin.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCatalogueAdmin implements CatalogueAdmin.Store {
    private final JdbcTemplate jdbc;
    public JdbcCatalogueAdmin(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static Instant time(ResultSet r,String c) throws SQLException {Timestamp t=r.getTimestamp(c);return t==null?null:t.toInstant();}
    private static Integer number(ResultSet r,String c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:v;}

    public Options options(){
        return new Options(
            jdbc.query("SELECT id,name,published FROM app.categories WHERE NOT archived ORDER BY sort_order,name,id",(r,n)->new CategoryOption(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3))),
            jdbc.query("SELECT code,label,fractional FROM app.units ORDER BY label,code",(r,n)->new UnitOption(r.getString(1),r.getString(2),r.getBoolean(3))));
    }

    private static final String STATUS="CASE WHEN p.archived THEN 'ARCHIVED' WHEN p.published THEN 'PUBLISHED' ELSE 'DRAFT' END";
    private static final String PENDING="(SELECT count(*) FROM app.price_changes pc JOIN app.product_variants pv ON pv.id=pc.variant_id WHERE pv.product_id=p.id AND pc.status='PENDING')";
    public Page list(String filter,String query,int page,int size){
        String where=" WHERE position(lower(?) in lower(p.name||' '||p.brand||' '||p.sku))>0 "+switch(filter){
            case "draft"->"AND NOT p.archived AND NOT p.published ";
            case "published"->"AND p.published ";
            case "archived"->"AND p.archived ";
            case "sample"->"AND p.sample_data AND NOT p.archived ";
            case "price-waiting"->"AND "+PENDING+">0 ";
            default->"";
        };
        var items=jdbc.query("SELECT p.id,p.sku,p.name,p.brand,c.name category,"+STATUS+" status,p.sample_data,p.revision,p.updated_at,"
            +"(SELECT count(*) FROM app.product_variants v WHERE v.product_id=p.id) variants,"
            +"COALESCE((SELECT i.path FROM app.product_images i WHERE i.product_id=p.id ORDER BY i.sort_order,i.id LIMIT 1),'') image,"
            +"(SELECT min(v.unit_price) FROM app.product_variants v WHERE v.product_id=p.id AND v.published) lowest,"+PENDING+" pending"
            +" FROM app.products p JOIN app.categories c ON c.id=p.category_id"+where+" ORDER BY "+PENDING+">0 DESC,p.archived,p.updated_at DESC,p.id LIMIT ? OFFSET ?",
            (r,n)->new Summary(r.getObject("id",UUID.class),r.getString("sku"),r.getString("name"),r.getString("brand"),r.getString("category"),r.getString("status"),
                r.getBoolean("sample_data"),r.getInt("variants"),r.getString("image"),r.getBigDecimal("lowest"),r.getInt("pending"),r.getLong("revision"),time(r,"updated_at")),
            query,size,page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM app.products p"+where,Long.class,query);
        long pending=jdbc.queryForObject("SELECT count(*) FROM app.price_changes WHERE status='PENDING'",Long.class);
        long samples=jdbc.queryForObject("SELECT count(*) FROM app.products WHERE sample_data AND NOT archived",Long.class);
        return new Page(items,total,pending,samples,page,size);
    }

    public Optional<ProductView> find(UUID id){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.products WHERE id=?)",Boolean.class,id)))return Optional.empty();
        var variants=jdbc.query("""
            SELECT v.*,u.label unit_label,u.fractional,
              (EXISTS(SELECT 1 FROM app.stock_balances s WHERE s.variant_id=v.id) OR EXISTS(SELECT 1 FROM app.customer_order_items i WHERE i.variant_id=v.id)) unit_locked,
              pc.id pending_id,pc.requested_mode,pc.requested_price,pc.reason,pc.requested_at,a.name requested_by
            FROM app.product_variants v JOIN app.units u ON u.code=v.unit_code
            LEFT JOIN app.price_changes pc ON pc.variant_id=v.id AND pc.status='PENDING' LEFT JOIN app.staff_accounts a ON a.id=pc.requested_by
            WHERE v.product_id=? ORDER BY v.sort_order,v.id
            """,(r,n)->new VariantView(r.getObject("id",UUID.class),r.getString("sku"),r.getString("label"),r.getString("unit_code"),r.getString("unit_label"),r.getBoolean("fractional"),
                r.getString("pricing_mode"),r.getBigDecimal("unit_price"),r.getString("currency"),r.getBoolean("published"),r.getInt("sort_order"),r.getBoolean("unit_locked"),
                r.getObject("pending_id")==null?null:new PendingPrice(r.getObject("pending_id",UUID.class),r.getString("requested_mode"),r.getBigDecimal("requested_price"),r.getString("reason"),r.getString("requested_by"),time(r,"requested_at"))),id);
        var images=jdbc.query("SELECT id,path,alt_text,sort_order,width_px,height_px,file_sha256 FROM app.product_images WHERE product_id=? ORDER BY sort_order,id",
            (r,n)->new ImageView(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),number(r,"width_px"),number(r,"height_px"),r.getString(7)!=null),id);
        var specs=jdbc.query("SELECT label,value FROM app.product_specifications WHERE product_id=? ORDER BY sort_order,label",(r,n)->new Spec(r.getString(1),r.getString(2)),id);
        return jdbc.query("SELECT p.*,c.name category_name FROM app.products p JOIN app.categories c ON c.id=p.category_id WHERE p.id=?",(r,n)->new ProductView(r.getObject("id",UUID.class),
            r.getString("sku"),r.getString("slug"),r.getString("name"),r.getString("brand"),r.getObject("category_id",UUID.class),r.getString("category_name"),r.getString("description"),
            r.getBoolean("published"),r.getBoolean("archived"),r.getBoolean("featured"),r.getBoolean("sample_data"),r.getLong("revision"),time(r,"updated_at"),variants,images,specs,r.getString("name_sw")),id).stream().findFirst();
    }

    public Optional<Lock> lock(UUID id){
        return jdbc.query("""
            SELECT p.id,p.revision,p.published,p.archived,(c.published AND NOT c.archived) category_live,p.name,p.description,
              (SELECT count(*) FROM app.product_variants v WHERE v.product_id=p.id AND v.published) live_variants,
              (SELECT count(*) FROM app.product_images i WHERE i.product_id=p.id) images
            FROM app.products p JOIN app.categories c ON c.id=p.category_id WHERE p.id=? FOR UPDATE OF p
            """,(r,n)->new Lock(r.getObject(1,UUID.class),r.getLong(2),r.getBoolean(3),r.getBoolean(4),r.getBoolean(5),r.getString(6),r.getString(7),r.getInt(8),r.getInt(9)),id).stream().findFirst();
    }
    public boolean productSkuTaken(String sku,UUID except){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.products WHERE upper(sku)=? AND id<>?)",Boolean.class,sku,except));}
    public boolean variantSkuTaken(String sku,UUID except){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.product_variants WHERE upper(sku)=? AND id<>?)",Boolean.class,sku,except));}
    public boolean categoryUsable(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.categories WHERE id=? AND NOT archived)",Boolean.class,id));}
    public Optional<UnitOption> unit(String code){return jdbc.query("SELECT code,label,fractional FROM app.units WHERE code=?",(r,n)->new UnitOption(r.getString(1),r.getString(2),r.getBoolean(3)),code).stream().findFirst();}

    private String slug(String name,UUID id){
        String base=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");
        if(base.isEmpty())base="product";
        if(base.length()>140)base=base.substring(0,140).replaceAll("-$","");
        String slug=base;int n=2;
        while(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.products WHERE slug=? AND id<>?)",Boolean.class,slug,id)))slug=base+"-"+n++;
        return slug;
    }
    private void specifications(UUID productId,List<Spec> specs){
        jdbc.update("DELETE FROM app.product_specifications WHERE product_id=?",productId);
        for(int i=0;i<specs.size();i++)jdbc.update("INSERT INTO app.product_specifications(product_id,label,value,sort_order) VALUES(?,?,?,?)",productId,specs.get(i).label(),specs.get(i).value(),i);
    }
    public void createProduct(UUID actor,CleanProduct p){
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,brand,description,featured,published,sample_data,name_sw) VALUES(?,?,?,?,?,?,?,?,false,false,?)",
            p.id(),p.sku(),slug(p.name(),p.id()),p.categoryId(),p.name(),p.brand(),p.description(),p.featured(),p.nameSw());
        specifications(p.id(),p.specifications());
        audit(actor,"CATALOGUE_PRODUCT_CREATED","PRODUCT",p.id().toString());
    }
    public void updateProduct(UUID actor,CleanProduct p){
        jdbc.update("UPDATE app.products SET sku=?,name=?,brand=?,category_id=?,description=?,featured=?,name_sw=?,revision=revision+1,updated_at=now() WHERE id=?",
            p.sku(),p.name(),p.brand(),p.categoryId(),p.description(),p.featured(),p.nameSw(),p.id());
        specifications(p.id(),p.specifications());
        audit(actor,"CATALOGUE_PRODUCT_UPDATED","PRODUCT",p.id().toString());
    }
    public void addVariant(UUID actor,UUID productId,CleanVariant v){
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,pricing_mode,unit_price,currency,published,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)",
            v.id(),productId,v.sku(),v.label(),v.unitCode(),v.pricingMode(),v.price(),CatalogueAdmin.CURRENCY,v.published(),v.sortOrder());
        touch(productId);audit(actor,"CATALOGUE_OPTION_ADDED","PRODUCT_VARIANT",v.id().toString());
    }
    public void updateVariant(UUID actor,UUID productId,CleanVariant v){
        jdbc.update("UPDATE app.product_variants SET sku=?,label=?,unit_code=?,published=?,sort_order=? WHERE id=? AND product_id=?",v.sku(),v.label(),v.unitCode(),v.published(),v.sortOrder(),v.id(),productId);
        touch(productId);audit(actor,"CATALOGUE_OPTION_UPDATED","PRODUCT_VARIANT",v.id().toString());
    }
    public Optional<VariantState> variant(UUID id,boolean lock){
        return jdbc.query("SELECT v.id,v.product_id,v.pricing_mode,v.unit_price,v.unit_code,(EXISTS(SELECT 1 FROM app.stock_balances s WHERE s.variant_id=v.id) OR EXISTS(SELECT 1 FROM app.customer_order_items i WHERE i.variant_id=v.id)) FROM app.product_variants v WHERE v.id=?"+(lock?" FOR UPDATE OF v":""),
            (r,n)->new VariantState(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getBoolean(6)),id).stream().findFirst();
    }
    public void setPrice(UUID variantId,String mode,BigDecimal price){jdbc.update("UPDATE app.product_variants SET pricing_mode=?,unit_price=? WHERE id=?",mode,price,variantId);}
    public Optional<UUID> pendingFor(UUID variantId){return jdbc.queryForList("SELECT id FROM app.price_changes WHERE variant_id=? AND status='PENDING' FOR UPDATE",UUID.class,variantId).stream().findFirst();}
    public void recordPrice(UUID id,UUID variantId,String previousMode,BigDecimal previousPrice,String mode,BigDecimal price,String reason,String status,UUID actor){
        boolean decided=!"PENDING".equals(status);
        jdbc.update("INSERT INTO app.price_changes(id,variant_id,previous_mode,previous_price,requested_mode,requested_price,currency,reason,status,requested_by,decided_by,decided_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,"+(decided?"now()":"NULL")+")",
            id,variantId,previousMode,previousPrice,mode,price,CatalogueAdmin.CURRENCY,reason,status,actor,decided?actor:null);
    }
    public Optional<PendingChange> lockChange(UUID id){
        return jdbc.query("SELECT pc.id,pc.variant_id,v.product_id,pc.previous_mode,pc.previous_price,pc.requested_mode,pc.requested_price,pc.status FROM app.price_changes pc JOIN app.product_variants v ON v.id=pc.variant_id WHERE pc.id=? FOR UPDATE OF pc",
            (r,n)->new PendingChange(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getBigDecimal(5),r.getString(6),r.getBigDecimal(7),r.getString(8)),id).stream().findFirst();
    }
    public void decide(UUID id,String status,UUID actor,String note){jdbc.update("UPDATE app.price_changes SET status=?,decided_by=?,decided_at=now(),decision_note=? WHERE id=? AND status='PENDING'",status,actor,note,id);}
    private static final String CHANGES="""
        SELECT pc.*,v.product_id,p.name product_name,v.label variant_label,v.sku,ra.name requested_by_name,da.name decided_by_name
        FROM app.price_changes pc JOIN app.product_variants v ON v.id=pc.variant_id JOIN app.products p ON p.id=v.product_id
        JOIN app.staff_accounts ra ON ra.id=pc.requested_by LEFT JOIN app.staff_accounts da ON da.id=pc.decided_by
        """;
    private static final RowMapper<PriceChange> CHANGE=(r,n)->new PriceChange(r.getObject("id",UUID.class),r.getObject("variant_id",UUID.class),r.getObject("product_id",UUID.class),
        r.getString("product_name"),r.getString("variant_label"),r.getString("sku"),r.getString("previous_mode"),r.getBigDecimal("previous_price"),r.getString("requested_mode"),
        r.getBigDecimal("requested_price"),r.getString("currency"),r.getString("reason"),r.getString("status"),r.getString("requested_by_name"),time(r,"requested_at"),
        r.getString("decided_by_name"),time(r,"decided_at"),r.getString("decision_note"));
    public Optional<PriceChange> change(UUID id){return jdbc.query(CHANGES+" WHERE pc.id=?",CHANGE,id).stream().findFirst();}
    public PriceChanges changes(String status,int page,int size){
        return new PriceChanges(jdbc.query(CHANGES+" WHERE (?::text IS NULL OR pc.status=?) ORDER BY pc.status='PENDING' DESC,pc.requested_at DESC,pc.id LIMIT ? OFFSET ?",CHANGE,status,status,size,page*size),
            jdbc.queryForObject("SELECT count(*) FROM app.price_changes WHERE (?::text IS NULL OR status=?)",Long.class,status,status),page,size);
    }
    public void setStatus(UUID actor,UUID productId,boolean published,boolean archived,String event){
        jdbc.update("UPDATE app.products SET published=?,archived=?,revision=revision+1,updated_at=now() WHERE id=?",published,archived,productId);
        audit(actor,event,"PRODUCT",productId.toString());
    }
    public int archiveSamples(UUID actor){
        var ids=jdbc.queryForList("UPDATE app.products SET published=false,archived=true,revision=revision+1,updated_at=now() WHERE sample_data AND NOT archived RETURNING id",UUID.class);
        for(UUID id:ids)audit(actor,"CATALOGUE_PRODUCT_ARCHIVED","PRODUCT",id.toString());
        audit(actor,"SAMPLE_PRODUCTS_ARCHIVED","CATALOGUE",String.valueOf(ids.size()));
        return ids.size();
    }
    public int imageCount(UUID productId){return jdbc.queryForObject("SELECT count(*) FROM app.product_images WHERE product_id=?",Integer.class,productId);}
    public void addImage(UUID actor,UUID productId,UUID imageId,String sha,String alt,int width,int height){
        jdbc.update("INSERT INTO app.product_images(id,product_id,path,alt_text,sort_order,file_sha256,width_px,height_px) VALUES(?,?,?,?,(SELECT COALESCE(max(sort_order),-1)+1 FROM app.product_images WHERE product_id=?),?,?,?)",
            imageId,productId,"/api/product-images/"+sha+".jpg",alt,productId,sha,width,height);
        touch(productId);audit(actor,"PRODUCT_PHOTO_ADDED","PRODUCT",productId.toString());
    }
    public List<UUID> imageIds(UUID productId){return jdbc.queryForList("SELECT id FROM app.product_images WHERE product_id=?",UUID.class,productId);}
    public void arrangeImages(UUID actor,UUID productId,List<ImageOrder> images){
        var keep=images.stream().map(ImageOrder::id).toList();
        for(UUID id:imageIds(productId))if(!keep.contains(id))jdbc.update("DELETE FROM app.product_images WHERE id=? AND product_id=?",id,productId);
        for(int i=0;i<images.size();i++)jdbc.update("UPDATE app.product_images SET sort_order=?,alt_text=? WHERE id=? AND product_id=?",i,images.get(i).alt(),images.get(i).id(),productId);
        touch(productId);audit(actor,"PRODUCT_PHOTOS_ARRANGED","PRODUCT",productId.toString());
    }
    public void touch(UUID productId){jdbc.update("UPDATE app.products SET revision=revision+1,updated_at=now() WHERE id=?",productId);}
    public boolean publicImage(String sha){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.product_images i JOIN app.public_catalogue_products p ON p.id=i.product_id WHERE i.file_sha256=?)",Boolean.class,sha));}
    public boolean anyImage(String sha){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.product_images WHERE file_sha256=?)",Boolean.class,sha));}
    public void audit(UUID actor,String event,String targetType,String target){
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF',?,?,?)",UUID.randomUUID(),actor,event,targetType,target);
    }
}
