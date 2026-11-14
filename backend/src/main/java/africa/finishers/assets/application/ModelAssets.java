package africa.finishers.assets.application;

import africa.finishers.assets.domain.GlbContainer;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Staff workflow for 3D models (FR-3D-08, FR-3D-09): Owner and Inventory prepare drafts and submit them;
 * only the Owner publishes, returns or retires. Publication replaces the product's previous live version.
 */
@Service
public class ModelAssets {
    public static final Set<String> BUILT_IN_KINDS=Set.of("cement-bag","paint-tin","tile-stack","wall-tile","filament-bulb","cable-roll",
        "pendant","hammer","roller","breaker-board","roof-sheet","pipe");
    public static final Set<String> STATUSES=Set.of("DRAFT","IN_REVIEW","PUBLISHED","RETIRED");
    private static final Set<String> ZONES=Set.of("building","finishes","electrical","tools");
    private static final Map<String,Set<String>> FINISHES=Map.of("floor",Set.of("marble"),"wall",Set.of("gloss-white","glazed"),"light",Set.of("pendant","bulb"));

    public record Draft(UUID id,UUID productId,String sourceKind,String modelKind,String sourceDescription,String licenseTerms,String attribution,
                        Boolean rightsConfirmed,Integer widthMm,Integer heightMm,Integer depthMm,String showroomZone,String roomSurface,
                        String roomFinish,Integer tileMm,Map<UUID,BigDecimal> variantScales,Long revision) {}
    public record Decision(Long revision,String note,Boolean verifiedExact) {}
    public record Asset(UUID id,UUID productId,String productName,String productSku,boolean productArchived,int version,String status,String sourceKind,String modelKind,
                        String fileSha256,Integer fileBytes,String fileName,GlbInspector.Report validation,String sourceDescription,String licenseTerms,
                        String attribution,boolean rightsConfirmed,boolean verifiedExact,Integer widthMm,Integer heightMm,Integer depthMm,String showroomZone,
                        String roomSurface,String roomFinish,Integer tileMm,Map<UUID,BigDecimal> variantScales,long revision,String createdByName,
                        Instant createdAt,Instant updatedAt,String submittedByName,Instant submittedAt,String reviewedByName,Instant reviewedAt,
                        String reviewNote,Instant publishedAt,Instant retiredAt) {}
    public record Page(List<Asset> items,long total,long inReview,int page,int size) {}
    public record Variant(UUID id,String sku,String label) {}
    public record Product(UUID id,String sku,String name,boolean published,List<Variant> variants,Integer publishedVersion) {}
    public record ManifestEntry(UUID assetId,int version,UUID productId,String sku,String sourceKind,String modelKind,String modelUrl,String fileSha256,
                                boolean verified,String zone,String roomSurface,String roomFinish,Integer tileMm,Map<UUID,BigDecimal> variantScales,
                                Integer widthMm,Integer heightMm,Integer depthMm) {}
    public record Manifest(String version,List<ManifestEntry> entries) {}
    public record StoredFile(String sha256,byte[] bytes) {}

    public interface Store {
        Page list(String status,String query,int page,int size);
        Optional<Asset> find(UUID id);
        Optional<Asset> lock(UUID id);
        List<Product> products(String query);
        Optional<Product> product(UUID id);
        Asset create(UUID actor,Draft draft);
        Asset update(UUID id,Draft draft);
        Asset attachFile(UUID id,String sha256,int bytes,String name,GlbInspector.Report report);
        Asset submit(UUID id,UUID actor);
        Asset returnToDraft(UUID id,UUID actor,String note);
        Asset publish(UUID id,UUID actor,String note,boolean verified);
        Asset retire(UUID id,UUID actor,String note);
        Manifest manifest();
        Optional<String> publicFile(UUID id);
    }
    public interface Files {
        void store(String sha256,byte[] bytes);
        byte[] read(String sha256);
    }

    private final StaffAccounts staff;
    private final Store store;
    private final Files files;
    public ModelAssets(StaffAccounts staff,Store store,Files files){this.staff=staff;this.store=store;this.files=files;}

    private void preparer(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER,StaffRole.INVENTORY);}
    private void owner(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER);}

    @Transactional(readOnly=true)
    public Page list(StaffPrincipal actor,String status,String query,int page,int size){
        preparer(actor);
        if(page<0||page>100000||size<1||size>48)throw new AuthProblem(400,"Invalid page size or page number.");
        if(status!=null&&!status.isEmpty()&&!STATUSES.contains(status))throw new AuthProblem(400,"Unknown model status.");
        if(query==null||query.length()>100)throw new AuthProblem(400,"Search must be at most 100 characters.");
        return store.list(status==null||status.isEmpty()?null:status,query.strip(),page,size);
    }
    @Transactional(readOnly=true)
    public Asset get(StaffPrincipal actor,UUID id){preparer(actor);return store.find(id).orElseThrow(()->new AuthProblem(404,"This 3D model record does not exist."));}
    @Transactional(readOnly=true)
    public List<Product> products(StaffPrincipal actor,String query){
        preparer(actor);
        if(query==null||query.length()>100)throw new AuthProblem(400,"Search must be at most 100 characters.");
        return store.products(query.strip());
    }

    @Transactional(timeout=20)
    public Asset create(StaffPrincipal actor,Draft draft){
        preparer(actor);
        if(draft==null||draft.id()==null||draft.productId()==null)throw new AuthProblem(400,"Choose a product for the new 3D model.");
        var existing=store.find(draft.id());
        if(existing.isPresent()){
            if(!existing.get().productId().equals(draft.productId()))throw new AuthProblem(409,"This request identity was already used for another product.");
            return existing.get();
        }
        var product=store.product(draft.productId()).orElseThrow(()->new AuthProblem(404,"This product is not available."));
        return store.create(actor.id(),clean(draft,product));
    }

    @Transactional(timeout=20)
    public Asset update(StaffPrincipal actor,UUID id,Draft draft){
        preparer(actor);
        var current=editable(id,draft==null?null:draft.revision());
        var product=store.product(current.productId()).orElseThrow(()->new AuthProblem(409,"This product has been archived. Start again from an active product."));
        return store.update(id,clean(draft,product));
    }

    /** Checks and stores an uploaded GLB. The stored name is the file's SHA-256, never the uploaded name. */
    @Transactional(timeout=30)
    public Asset upload(StaffPrincipal actor,UUID id,long revision,String name,byte[] bytes){
        preparer(actor);
        var current=editable(id,revision);
        if(!"GLB".equals(current.sourceKind()))throw new AuthProblem(409,"Switch this draft to an uploaded GLB file before uploading.");
        GlbInspector.Report report;
        try{report=GlbInspector.inspect(bytes);}catch(GlbContainer.Invalid e){throw new AuthProblem(422,e.getMessage());}
        String sha=sha256(bytes);
        files.store(sha,bytes);
        String safeName=name==null?"":name.replaceAll("[^A-Za-z0-9._ -]","_").strip();
        if(safeName.length()>120)safeName=safeName.substring(safeName.length()-120);
        return store.attachFile(id,sha,bytes.length,safeName.isEmpty()?"model.glb":safeName,report);
    }

    @Transactional(timeout=20)
    public Asset submit(StaffPrincipal actor,UUID id,Decision decision){
        preparer(actor);
        var current=editable(id,decision==null?null:decision.revision());
        if(!current.rightsConfirmed())throw new AuthProblem(409,"Confirm that the shop may show this model before sending it for review.");
        if(current.sourceDescription().length()<5||current.licenseTerms().length()<3)throw new AuthProblem(409,"Record where the model came from and its licence terms before sending it for review.");
        if("GLB".equals(current.sourceKind())&&current.fileSha256()==null)throw new AuthProblem(409,"Upload the GLB file before sending it for review.");
        return store.submit(id,actor.id());
    }

    @Transactional(timeout=20)
    public Asset returnToDraft(StaffPrincipal actor,UUID id,Decision decision){
        owner(actor);
        var current=locked(id,decision==null?null:decision.revision(),"IN_REVIEW","Only a model waiting for review can be returned.");
        return store.returnToDraft(current.id(),actor.id(),note(decision,5,"Explain what needs to change (5–500 characters)."));
    }

    @Transactional(timeout=20)
    public Asset publish(StaffPrincipal actor,UUID id,Decision decision){
        owner(actor);
        var current=locked(id,decision==null?null:decision.revision(),"IN_REVIEW","Only a model waiting for review can be published.");
        if(current.productArchived())throw new AuthProblem(409,"This product has been archived and cannot receive a new 3D model.");
        boolean verified=decision!=null&&Boolean.TRUE.equals(decision.verifiedExact());
        if(verified&&!"GLB".equals(current.sourceKind()))throw new AuthProblem(400,"A built-in illustrative shape cannot be approved as the exact product.");
        if(verified&&(current.widthMm()==null||current.heightMm()==null||current.depthMm()==null))throw new AuthProblem(400,"Record the measured width, height and depth before approving a model as the exact product.");
        return store.publish(current.id(),actor.id(),note(decision,0,"Keep the approval note under 500 characters."),verified);
    }

    @Transactional(timeout=20)
    public Asset retire(StaffPrincipal actor,UUID id,Decision decision){
        owner(actor);
        var current=locked(id,decision==null?null:decision.revision(),"PUBLISHED","Only a published model can be retired.");
        return store.retire(current.id(),actor.id(),note(decision,5,"Give a reason for retiring this model (5–500 characters)."));
    }

    @Transactional(readOnly=true)
    public StoredFile staffFile(StaffPrincipal actor,UUID id){
        preparer(actor);
        var asset=store.find(id).orElseThrow(()->new AuthProblem(404,"This 3D model record does not exist."));
        if(asset.fileSha256()==null)throw new AuthProblem(404,"No file has been uploaded for this model.");
        return new StoredFile(asset.fileSha256(),files.read(asset.fileSha256()));
    }

    @Transactional(readOnly=true)
    public Manifest manifest(){return store.manifest();}

    /** Files of published models, and of retired ones so that older order references still resolve. Drafts are never public. */
    @Transactional(readOnly=true)
    public StoredFile publicFile(UUID id){
        String sha=store.publicFile(id).orElseThrow(()->new AuthProblem(404,"This model is not available."));
        return new StoredFile(sha,files.read(sha));
    }

    private Asset editable(UUID id,Long revision){return locked(id,revision,"DRAFT","Only drafts can be changed. Ask the Owner to return the model to draft first.");}
    private Asset locked(UUID id,Long revision,String status,String wrongStatus){
        var current=store.lock(id).orElseThrow(()->new AuthProblem(404,"This 3D model record does not exist."));
        if(revision==null||revision!=current.revision())throw new AuthProblem(409,"This model was changed by someone else. Refresh and try again.");
        if(!status.equals(current.status()))throw new AuthProblem(409,wrongStatus);
        return current;
    }

    private Draft clean(Draft draft,Product product){
        if(draft==null)throw new AuthProblem(400,"Enter the model details.");
        String kind=draft.sourceKind();
        if(!"BUILT_IN".equals(kind)&&!"GLB".equals(kind))throw new AuthProblem(400,"Choose a built-in shape or an uploaded GLB file.");
        String model=null;
        if("BUILT_IN".equals(kind)){
            if(draft.modelKind()==null||!BUILT_IN_KINDS.contains(draft.modelKind()))throw new AuthProblem(400,"Choose one of the built-in shapes.");
            model=draft.modelKind();
        }
        if(draft.showroomZone()==null||!ZONES.contains(draft.showroomZone()))throw new AuthProblem(400,"Choose the showroom area for this product.");
        String surface=blank(draft.roomSurface()),finish=blank(draft.roomFinish());
        if((surface==null)!=(finish==null)||(surface!=null&&(!FINISHES.containsKey(surface)||!FINISHES.get(surface).contains(finish))))
            throw new AuthProblem(400,"Choose a room surface together with a finish that matches it, or neither.");
        Integer tile=draft.tileMm();
        if(tile!=null&&(surface==null||"light".equals(surface)||tile<10||tile>3000))throw new AuthProblem(400,"A tile size (10–3000 mm) applies only to floor or wall finishes.");
        var allowed=product.variants().stream().map(Variant::id).toList();
        var scales=draft.variantScales()==null?Map.<UUID,BigDecimal>of():draft.variantScales();
        if(scales.size()>50)throw new AuthProblem(400,"Too many option sizes.");
        for(var entry:scales.entrySet()){
            if(entry.getKey()==null||!allowed.contains(entry.getKey()))throw new AuthProblem(400,"An option size refers to an option this product does not have.");
            if(entry.getValue()==null||entry.getValue().compareTo(new BigDecimal("0.2"))<0||entry.getValue().compareTo(new BigDecimal("3"))>0||entry.getValue().scale()>3)
                throw new AuthProblem(400,"Option sizes must be between 0.2 and 3.");
        }
        return new Draft(draft.id(),product.id(),kind,model,text(draft.sourceDescription(),"Where the model came from"),text(draft.licenseTerms(),"Licence terms"),
            text(draft.attribution(),"Attribution"),Boolean.TRUE.equals(draft.rightsConfirmed()),size(draft.widthMm()),size(draft.heightMm()),size(draft.depthMm()),
            draft.showroomZone(),surface,finish,tile,Map.copyOf(scales),draft.revision());
    }
    private static String blank(String value){return value==null||value.isBlank()?null:value;}
    private static String text(String value,String field){
        String clean=value==null?"":value.strip();
        if(clean.length()>300||clean.chars().anyMatch(Character::isISOControl))throw new AuthProblem(400,field+" must be at most 300 characters on one line.");
        return clean;
    }
    private static Integer size(Integer mm){
        if(mm!=null&&(mm<1||mm>100000))throw new AuthProblem(400,"Dimensions must be between 1 and 100000 mm.");
        return mm;
    }
    private static String note(Decision decision,int min,String message){
        String value=decision==null||decision.note()==null?"":decision.note().strip();
        if(value.length()<min||value.length()>500||value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new AuthProblem(400,message);
        return value;
    }
    static String sha256(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
