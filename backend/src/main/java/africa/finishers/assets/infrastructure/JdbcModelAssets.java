package africa.finishers.assets.infrastructure;

import africa.finishers.assets.application.GlbInspector;
import africa.finishers.assets.application.ModelAssets;
import africa.finishers.assets.application.ModelAssets.*;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Repository
public class JdbcModelAssets implements ModelAssets.Store {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String SELECT="""
        SELECT a.*,p.name product_name,p.sku product_sku,p.archived product_archived,
               cb.name created_by_name,sb.name submitted_by_name,rb.name reviewed_by_name
        FROM app.model_assets a JOIN app.products p ON p.id=a.product_id
        LEFT JOIN app.staff_accounts cb ON cb.id=a.created_by
        LEFT JOIN app.staff_accounts sb ON sb.id=a.submitted_by
        LEFT JOIN app.staff_accounts rb ON rb.id=a.reviewed_by
        """;
    private static final String FILTER=" WHERE (?::text IS NULL OR a.status=?) AND position(lower(?) in lower(p.name||' '||p.sku))>0 ";
    private final JdbcTemplate jdbc;
    public JdbcModelAssets(JdbcTemplate jdbc){this.jdbc=jdbc;}

    private static Instant time(ResultSet r,String column) throws SQLException {Timestamp t=r.getTimestamp(column);return t==null?null:t.toInstant();}
    private static Integer number(ResultSet r,String column) throws SQLException {int v=r.getInt(column);return r.wasNull()?null:v;}
    private static Map<UUID,BigDecimal> scales(String json){
        Map<String,BigDecimal> raw=JSON.readValue(json,new TypeReference<Map<String,BigDecimal>>(){});
        var result=new TreeMap<UUID,BigDecimal>();raw.forEach((key,value)->result.put(UUID.fromString(key),value));return result;
    }
    private static String scalesJson(Map<UUID,BigDecimal> scales){
        var raw=new TreeMap<String,BigDecimal>();scales.forEach((key,value)->raw.put(key.toString(),value));return JSON.writeValueAsString(raw);
    }
    private static final RowMapper<Asset> ASSET=(r,n)->{
        String report=r.getString("validation_report");
        return new Asset(r.getObject("id",UUID.class),r.getObject("product_id",UUID.class),r.getString("product_name"),r.getString("product_sku"),r.getBoolean("product_archived"),
            r.getInt("version"),r.getString("status"),r.getString("source_kind"),r.getString("model_kind"),r.getString("file_sha256"),number(r,"file_bytes"),r.getString("file_name"),
            report==null||report.equals("{}")?null:JSON.readValue(report,GlbInspector.Report.class),r.getString("source_description"),r.getString("license_terms"),r.getString("attribution"),
            r.getBoolean("rights_confirmed"),r.getBoolean("verified_exact"),number(r,"width_mm"),number(r,"height_mm"),number(r,"depth_mm"),r.getString("showroom_zone"),
            r.getString("room_surface"),r.getString("room_finish"),number(r,"tile_mm"),scales(r.getString("variant_scales")),r.getLong("revision"),r.getString("created_by_name"),
            time(r,"created_at"),time(r,"updated_at"),r.getString("submitted_by_name"),time(r,"submitted_at"),r.getString("reviewed_by_name"),time(r,"reviewed_at"),
            r.getString("review_note"),time(r,"published_at"),time(r,"retired_at"));
    };

    public Page list(String status,String query,int page,int size){
        var items=jdbc.query(SELECT+FILTER+" ORDER BY (a.status='IN_REVIEW') DESC,a.updated_at DESC,a.id LIMIT ? OFFSET ?",ASSET,status,status,query,size,page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM app.model_assets a JOIN app.products p ON p.id=a.product_id"+FILTER,Long.class,status,status,query);
        long review=jdbc.queryForObject("SELECT count(*) FROM app.model_assets WHERE status='IN_REVIEW'",Long.class);
        return new Page(items,total,review,page,size);
    }
    public Optional<Asset> find(UUID id){return jdbc.query(SELECT+" WHERE a.id=?",ASSET,id).stream().findFirst();}
    public Optional<Asset> lock(UUID id){return jdbc.query(SELECT+" WHERE a.id=? FOR UPDATE OF a",ASSET,id).stream().findFirst();}

    private Product product(ResultSet r) throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        var variants=jdbc.query("SELECT id,sku,label FROM app.product_variants WHERE product_id=? ORDER BY sort_order,id",(v,n)->new Variant(v.getObject(1,UUID.class),v.getString(2),v.getString(3)),id);
        return new Product(id,r.getString("sku"),r.getString("name"),r.getBoolean("published"),variants,number(r,"published_version"));
    }
    private static final String PRODUCT="SELECT p.id,p.sku,p.name,p.published,(SELECT a.version FROM app.model_assets a WHERE a.product_id=p.id AND a.status='PUBLISHED') published_version FROM app.products p WHERE NOT p.archived ";
    public List<Product> products(String query){
        return jdbc.query(PRODUCT+"AND position(lower(?) in lower(p.name||' '||p.sku))>0 ORDER BY p.name,p.id LIMIT 20",(r,n)->product(r),query);
    }
    public Optional<Product> product(UUID id){return jdbc.query(PRODUCT+"AND p.id=?",(r,n)->product(r),id).stream().findFirst();}

    private void audit(UUID actor,String event,UUID id){
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF',?,'MODEL_ASSET',?)",UUID.randomUUID(),actor,event,id.toString());
    }
    private Asset reload(UUID id){return find(id).orElseThrow();}

    public Asset create(UUID actor,Draft d){
        // One version counter per product; serialize creators so two drafts never claim the same number.
        jdbc.query("SELECT pg_advisory_xact_lock(?)",r->{},d.productId().getMostSignificantBits()^d.productId().getLeastSignificantBits());
        var again=find(d.id());
        if(again.isPresent()){
            if(!again.get().productId().equals(d.productId()))throw new AuthProblem(409,"This request identity was already used for another product.");
            return again.get();
        }
        int version=jdbc.queryForObject("SELECT COALESCE(max(version),0)+1 FROM app.model_assets WHERE product_id=?",Integer.class,d.productId());
        jdbc.update("""
            INSERT INTO app.model_assets(id,product_id,version,source_kind,model_kind,source_description,license_terms,attribution,rights_confirmed,
              width_mm,height_mm,depth_mm,showroom_zone,room_surface,room_finish,tile_mm,variant_scales,created_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)
            """,d.id(),d.productId(),version,d.sourceKind(),d.modelKind(),d.sourceDescription(),d.licenseTerms(),d.attribution(),d.rightsConfirmed(),
            d.widthMm(),d.heightMm(),d.depthMm(),d.showroomZone(),d.roomSurface(),d.roomFinish(),d.tileMm(),scalesJson(d.variantScales()),actor);
        audit(actor,"MODEL_DRAFT_CREATED",d.id());
        return reload(d.id());
    }
    public Asset update(UUID id,Draft d){
        // Changing between a built-in shape and an upload discards any file that no longer applies.
        jdbc.update("""
            UPDATE app.model_assets SET source_kind=?,model_kind=?,source_description=?,license_terms=?,attribution=?,rights_confirmed=?,
              width_mm=?,height_mm=?,depth_mm=?,showroom_zone=?,room_surface=?,room_finish=?,tile_mm=?,variant_scales=?::jsonb,
              file_sha256=CASE WHEN ?='GLB' THEN file_sha256 END,file_bytes=CASE WHEN ?='GLB' THEN file_bytes END,
              file_name=CASE WHEN ?='GLB' THEN file_name END,validation_report=CASE WHEN ?='GLB' THEN validation_report ELSE '{}'::jsonb END,
              revision=revision+1,updated_at=now() WHERE id=?
            """,d.sourceKind(),d.modelKind(),d.sourceDescription(),d.licenseTerms(),d.attribution(),d.rightsConfirmed(),d.widthMm(),d.heightMm(),d.depthMm(),
            d.showroomZone(),d.roomSurface(),d.roomFinish(),d.tileMm(),scalesJson(d.variantScales()),d.sourceKind(),d.sourceKind(),d.sourceKind(),d.sourceKind(),id);
        return reload(id);
    }
    public Asset attachFile(UUID id,String sha256,int bytes,String name,GlbInspector.Report report){
        jdbc.update("UPDATE app.model_assets SET file_sha256=?,file_bytes=?,file_name=?,validation_report=?::jsonb,revision=revision+1,updated_at=now() WHERE id=?",
            sha256,bytes,name,JSON.writeValueAsString(report),id);
        return reload(id);
    }
    public Asset submit(UUID id,UUID actor){
        jdbc.update("UPDATE app.model_assets SET status='IN_REVIEW',submitted_by=?,submitted_at=now(),revision=revision+1,updated_at=now() WHERE id=?",actor,id);
        audit(actor,"MODEL_SUBMITTED",id);return reload(id);
    }
    public Asset returnToDraft(UUID id,UUID actor,String note){
        jdbc.update("UPDATE app.model_assets SET status='DRAFT',reviewed_by=?,reviewed_at=now(),review_note=?,revision=revision+1,updated_at=now() WHERE id=?",actor,note,id);
        audit(actor,"MODEL_RETURNED",id);return reload(id);
    }
    public Asset publish(UUID id,UUID actor,String note,boolean verified){
        UUID product=jdbc.queryForObject("SELECT product_id FROM app.model_assets WHERE id=?",UUID.class,id);
        // Replace the live version in the same transaction; the old row stays so past orders still resolve.
        var replaced=jdbc.queryForList("UPDATE app.model_assets SET status='RETIRED',retired_at=now(),revision=revision+1,updated_at=now() WHERE product_id=? AND status='PUBLISHED' RETURNING id",UUID.class,product);
        jdbc.update("UPDATE app.model_assets SET status='PUBLISHED',verified_exact=?,reviewed_by=?,reviewed_at=now(),review_note=?,published_at=now(),revision=revision+1,updated_at=now() WHERE id=?",verified,actor,note,id);
        for(UUID old:replaced)audit(actor,"MODEL_REPLACED",old);
        audit(actor,"MODEL_PUBLISHED",id);return reload(id);
    }
    public Asset retire(UUID id,UUID actor,String note){
        jdbc.update("UPDATE app.model_assets SET status='RETIRED',retired_at=now(),review_note=?,revision=revision+1,updated_at=now() WHERE id=?",note,id);
        audit(actor,"MODEL_RETIRED",id);return reload(id);
    }

    public Manifest manifest(){
        // Only models of products the public catalogue currently shows.
        var entries=jdbc.query("""
            SELECT a.id,a.version,a.product_id,p.sku,a.source_kind,a.model_kind,a.file_sha256,a.verified_exact,a.showroom_zone,a.room_surface,a.room_finish,
                   a.tile_mm,a.variant_scales,a.width_mm,a.height_mm,a.depth_mm
            FROM app.model_assets a JOIN app.public_catalogue_products p ON p.id=a.product_id WHERE a.status='PUBLISHED' ORDER BY p.sku,a.id
            """,(r,n)->{
                UUID id=r.getObject("id",UUID.class);boolean glb="GLB".equals(r.getString("source_kind"));
                return new ManifestEntry(id,r.getInt("version"),r.getObject("product_id",UUID.class),r.getString("sku"),r.getString("source_kind"),r.getString("model_kind"),
                    glb?"/api/models/"+id+"/file":null,r.getString("file_sha256"),r.getBoolean("verified_exact"),r.getString("showroom_zone"),r.getString("room_surface"),
                    r.getString("room_finish"),number(r,"tile_mm"),scales(r.getString("variant_scales")),number(r,"width_mm"),number(r,"height_mm"),number(r,"depth_mm"));
            });
        var identity=new ArrayList<String>();
        for(var entry:entries)identity.add(entry.assetId()+":"+entry.version());
        try{
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join(",",identity).getBytes(StandardCharsets.UTF_8)));
            return new Manifest(entries.size()+"-"+digest.substring(0,12),entries);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public Optional<String> publicFile(UUID id){
        return jdbc.queryForList("SELECT file_sha256 FROM app.model_assets WHERE id=? AND status IN ('PUBLISHED','RETIRED') AND file_sha256 IS NOT NULL",String.class,id).stream().findFirst();
    }
}
