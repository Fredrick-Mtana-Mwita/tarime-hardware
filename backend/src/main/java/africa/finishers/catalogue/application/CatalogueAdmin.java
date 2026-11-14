package africa.finishers.catalogue.application;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Staff catalogue management. Owner and Inventory edit products, options, photos and specifications and
 * may publish or archive them (FR-01 to FR-06). Prices follow C4: the Owner changes them directly; an
 * Inventory price is stored as a pending request and the effective price does not move until the Owner approves.
 * Products are never deleted (BR-10): archiving hides them while past orders keep their own snapshot.
 */
@Service
public class CatalogueAdmin {
    public static final String CURRENCY="TZS";
    public static final Set<String> FILTERS=Set.of("","draft","published","archived","sample","price-waiting");
    private static final BigDecimal MAX_PRICE=new BigDecimal("999999999999.99");

    public record CategoryOption(UUID id,String name,boolean published) {}
    public record UnitOption(String code,String label,boolean fractional) {}
    public record Options(List<CategoryOption> categories,List<UnitOption> units) {}
    public record Summary(UUID id,String sku,String name,String brand,String categoryName,String status,boolean sample,int variants,String image,
                          BigDecimal lowestPrice,int pendingPrices,long revision,Instant updatedAt) {}
    public record Page(List<Summary> items,long total,long pendingPrices,long liveSamples,int page,int size) {}
    public record PendingPrice(UUID id,String requestedMode,BigDecimal requestedPrice,String reason,String requestedByName,Instant requestedAt) {}
    public record VariantView(UUID id,String sku,String label,String unitCode,String unitLabel,boolean fractional,String pricingMode,BigDecimal price,
                              String currency,boolean published,int sortOrder,boolean unitLocked,PendingPrice pending) {}
    public record ImageView(UUID id,String path,String alt,int sortOrder,Integer width,Integer height,boolean uploaded) {}
    public record Spec(String label,String value) {}
    public record ProductView(UUID id,String sku,String slug,String name,String brand,UUID categoryId,String categoryName,String description,
                              boolean published,boolean archived,boolean featured,boolean sample,long revision,Instant updatedAt,
                              List<VariantView> variants,List<ImageView> images,List<Spec> specifications) {}
    public record VariantInput(UUID id,String sku,String label,String unitCode,String pricingMode,BigDecimal price,Boolean published,Integer sortOrder,Long revision) {}
    public record ProductInput(UUID id,String sku,String name,String brand,UUID categoryId,String description,Boolean featured,List<Spec> specifications,
                               List<VariantInput> variants,Long revision) {}
    public record PriceInput(UUID id,String pricingMode,BigDecimal price,String reason,Long revision) {}
    public record ImageOrder(UUID id,String alt) {}
    public record ImagesInput(Long revision,List<ImageOrder> images) {}
    public record Decision(Long revision,String note,String confirm) {}
    public record PriceChange(UUID id,UUID variantId,UUID productId,String productName,String variantLabel,String sku,String previousMode,BigDecimal previousPrice,
                              String requestedMode,BigDecimal requestedPrice,String currency,String reason,String status,String requestedByName,Instant requestedAt,
                              String decidedByName,Instant decidedAt,String decisionNote) {}
    public record PriceChanges(List<PriceChange> items,long total,int page,int size) {}
    public record ArchivedSamples(int archived) {}
    public record StoredImage(String sha256,byte[] bytes) {}

    /** A variant as stored, after validation. Price fields are only applied by the Owner. */
    public record CleanVariant(UUID id,String sku,String label,String unitCode,String pricingMode,BigDecimal price,boolean published,int sortOrder) {}
    public record CleanProduct(UUID id,String sku,String name,String brand,UUID categoryId,String description,boolean featured,List<Spec> specifications) {}
    public record Lock(UUID id,long revision,boolean published,boolean archived,boolean categoryLive,String name,String description,int liveVariants,int images) {}
    public record VariantState(UUID id,UUID productId,String pricingMode,BigDecimal price,String unitCode,boolean unitLocked) {}
    public record PendingChange(UUID id,UUID variantId,UUID productId,String previousMode,BigDecimal previousPrice,String requestedMode,BigDecimal requestedPrice,String status) {}

    public interface Store {
        Options options();
        Page list(String filter,String query,int page,int size);
        Optional<ProductView> find(UUID id);
        Optional<Lock> lock(UUID id);
        boolean productSkuTaken(String sku,UUID except);
        boolean variantSkuTaken(String sku,UUID except);
        boolean categoryUsable(UUID id);
        Optional<UnitOption> unit(String code);
        void createProduct(UUID actor,CleanProduct product);
        void updateProduct(UUID actor,CleanProduct product);
        void addVariant(UUID actor,UUID productId,CleanVariant variant);
        void updateVariant(UUID actor,UUID productId,CleanVariant variant);
        Optional<VariantState> variant(UUID id,boolean lock);
        void setPrice(UUID variantId,String mode,BigDecimal price);
        Optional<UUID> pendingFor(UUID variantId);
        void recordPrice(UUID id,UUID variantId,String previousMode,BigDecimal previousPrice,String mode,BigDecimal price,String reason,String status,UUID actor);
        Optional<PendingChange> lockChange(UUID id);
        void decide(UUID id,String status,UUID actor,String note);
        Optional<PriceChange> change(UUID id);
        PriceChanges changes(String status,int page,int size);
        void setStatus(UUID actor,UUID productId,boolean published,boolean archived,String event);
        int archiveSamples(UUID actor);
        int imageCount(UUID productId);
        void addImage(UUID actor,UUID productId,UUID imageId,String sha256,String alt,int width,int height);
        List<UUID> imageIds(UUID productId);
        void arrangeImages(UUID actor,UUID productId,List<ImageOrder> images);
        void touch(UUID productId);
        boolean publicImage(String sha256);
        boolean anyImage(String sha256);
        void audit(UUID actor,String event,String targetType,String target);
    }
    public interface Images { void store(String sha256,byte[] jpeg); byte[] read(String sha256); }

    private final StaffAccounts staff;
    private final Store store;
    private final Images images;
    public CatalogueAdmin(StaffAccounts staff,Store store,Images images){this.staff=staff;this.store=store;this.images=images;}

    private StaffPrincipal editor(StaffPrincipal actor){return staff.require(actor,StaffRole.OWNER,StaffRole.INVENTORY);}
    private static boolean isOwner(StaffPrincipal actor){return actor.role()==StaffRole.OWNER;}

    @Transactional(readOnly=true)
    public Options options(StaffPrincipal actor){editor(actor);return store.options();}

    @Transactional(readOnly=true)
    public Page list(StaffPrincipal actor,String filter,String query,int page,int size){
        editor(actor);
        if(filter==null||!FILTERS.contains(filter))throw new AuthProblem(400,"Unknown product filter.");
        if(query==null||query.length()>100)throw new AuthProblem(400,"Search must be at most 100 characters.");
        if(page<0||page>100000||size<1||size>48)throw new AuthProblem(400,"Invalid page size or page number.");
        return store.list(filter,query.strip(),page,size);
    }

    @Transactional(readOnly=true)
    public ProductView get(StaffPrincipal actor,UUID id){editor(actor);return store.find(id).orElseThrow(()->new AuthProblem(404,"This product does not exist."));}

    @Transactional(timeout=20)
    public ProductView create(StaffPrincipal actor,ProductInput input){
        editor(actor);
        if(input==null||input.id()==null)throw new AuthProblem(400,"Enter the product details.");
        if(store.find(input.id()).isPresent())return store.find(input.id()).get();
        var product=clean(input,input.id());
        var variants=input.variants()==null?List.<VariantInput>of():input.variants();
        if(variants.isEmpty()||variants.size()>30)throw new AuthProblem(400,"Add between 1 and 30 selling options.");
        var cleanVariants=new java.util.ArrayList<CleanVariant>();var skus=new HashSet<String>();
        for(int i=0;i<variants.size();i++){
            var variant=cleanVariant(variants.get(i),i);
            if(!skus.add(variant.sku().toLowerCase(java.util.Locale.ROOT)))throw new AuthProblem(400,"Each option needs its own SKU.");
            cleanVariants.add(variant);
        }
        store.createProduct(actor.id(),product);
        for(var variant:cleanVariants)placeVariant(actor,product.id(),variant,"Starting price for a new product");
        return store.find(product.id()).orElseThrow();
    }

    @Transactional(timeout=20)
    public ProductView update(StaffPrincipal actor,UUID id,ProductInput input){
        editor(actor);
        locked(id,input==null?null:input.revision());
        store.updateProduct(actor.id(),clean(input,id));
        return store.find(id).orElseThrow();
    }

    @Transactional(timeout=20)
    public ProductView addVariant(StaffPrincipal actor,UUID productId,VariantInput input){
        editor(actor);
        var lock=locked(productId,input==null?null:input.revision());
        if(input!=null&&input.id()!=null&&store.variant(input.id(),false).isPresent())throw new AuthProblem(409,"This option was already added. Refresh to see it.");
        if(store.find(productId).orElseThrow().variants().size()>=30)throw new AuthProblem(409,"A product can have at most 30 options.");
        placeVariant(actor,lock.id(),cleanVariant(input,0),"Starting price for a new option");
        return store.find(productId).orElseThrow();
    }

    @Transactional(timeout=20)
    public ProductView updateVariant(StaffPrincipal actor,UUID productId,UUID variantId,VariantInput input){
        editor(actor);
        locked(productId,input==null?null:input.revision());
        var state=store.variant(variantId,true).filter(v->v.productId().equals(productId)).orElseThrow(()->new AuthProblem(404,"This option does not belong to the product."));
        var variant=cleanVariant(input==null?null:new VariantInput(variantId,input.sku(),input.label(),input.unitCode(),null,null,input.published(),input.sortOrder(),input.revision()),0);
        if(state.unitLocked()&&!state.unitCode().equals(variant.unitCode()))
            throw new AuthProblem(409,"The selling unit cannot change after stock or orders exist for this option. Add a new option instead.");
        store.updateVariant(actor.id(),productId,variant);
        return store.find(productId).orElseThrow();
    }

    /** Owner: applied at once and recorded. Inventory: recorded as a request; the effective price is unchanged. */
    @Transactional(timeout=20)
    public ProductView changePrice(StaffPrincipal actor,UUID productId,UUID variantId,PriceInput input){
        editor(actor);
        locked(productId,input==null?null:input.revision());
        var state=store.variant(variantId,true).filter(v->v.productId().equals(productId)).orElseThrow(()->new AuthProblem(404,"This option does not belong to the product."));
        if(input==null||input.id()==null)throw new AuthProblem(400,"Enter the new price.");
        String mode=mode(input.pricingMode());BigDecimal price=price(mode,input.price());
        String reason=text(input.reason(),3,500,"Give a reason for the price change (3–500 characters).");
        if(state.pricingMode().equals(mode)&&same(state.price(),price))throw new AuthProblem(400,"This is already the current price.");
        var pending=store.pendingFor(variantId);
        if(isOwner(actor)){
            pending.ifPresent(old->store.decide(old,"REJECTED",actor.id(),"Replaced by the Owner's direct price change."));
            store.recordPrice(input.id(),variantId,state.pricingMode(),state.price(),mode,price,reason,"APPROVED",actor.id());
            store.setPrice(variantId,mode,price);
            store.audit(actor.id(),"PRICE_CHANGED","PRICE_CHANGE",input.id().toString());
        }else{
            if(pending.isPresent())throw new AuthProblem(409,"A price change for this option is already waiting for the Owner.");
            store.recordPrice(input.id(),variantId,state.pricingMode(),state.price(),mode,price,reason,"PENDING",actor.id());
            store.audit(actor.id(),"PRICE_CHANGE_REQUESTED","PRICE_CHANGE",input.id().toString());
        }
        store.touch(productId);
        return store.find(productId).orElseThrow();
    }

    @Transactional(readOnly=true)
    public PriceChanges priceChanges(StaffPrincipal actor,String status,int page,int size){
        editor(actor);
        if(status==null||!Set.of("","PENDING","APPROVED","REJECTED").contains(status))throw new AuthProblem(400,"Unknown price change status.");
        if(page<0||page>100000||size<1||size>48)throw new AuthProblem(400,"Invalid page size or page number.");
        return store.changes(status.isEmpty()?null:status,page,size);
    }

    @Transactional(timeout=20)
    public PriceChange approvePrice(StaffPrincipal actor,UUID changeId,Decision decision){
        staff.require(actor,StaffRole.OWNER);
        var change=store.lockChange(changeId).orElseThrow(()->new AuthProblem(404,"This price change does not exist."));
        if(!"PENDING".equals(change.status()))throw new AuthProblem(409,"This price change has already been decided.");
        var state=store.variant(change.variantId(),true).orElseThrow();
        // The request was made against a particular price. If that price has since changed, approving it blindly could undo a newer decision.
        if(!state.pricingMode().equals(change.previousMode())||!same(state.price(),change.previousPrice()))
            throw new AuthProblem(409,"The price changed after this request was made. Reject it and ask for a new request.");
        store.setPrice(change.variantId(),change.requestedMode(),change.requestedPrice());
        store.decide(changeId,"APPROVED",actor.id(),text(decision==null?null:decision.note(),0,500,"Keep the note under 500 characters."));
        store.audit(actor.id(),"PRICE_CHANGE_APPROVED","PRICE_CHANGE",changeId.toString());
        store.touch(change.productId());
        return store.change(changeId).orElseThrow();
    }

    @Transactional(timeout=20)
    public PriceChange rejectPrice(StaffPrincipal actor,UUID changeId,Decision decision){
        staff.require(actor,StaffRole.OWNER);
        var change=store.lockChange(changeId).orElseThrow(()->new AuthProblem(404,"This price change does not exist."));
        if(!"PENDING".equals(change.status()))throw new AuthProblem(409,"This price change has already been decided.");
        store.decide(changeId,"REJECTED",actor.id(),text(decision==null?null:decision.note(),3,500,"Explain why the price was not approved (3–500 characters)."));
        store.audit(actor.id(),"PRICE_CHANGE_REJECTED","PRICE_CHANGE",changeId.toString());
        store.touch(change.productId());
        return store.change(changeId).orElseThrow();
    }

    @Transactional(timeout=20)
    public ProductView publish(StaffPrincipal actor,UUID id,Decision decision){
        editor(actor);
        var lock=locked(id,decision==null?null:decision.revision());
        if(lock.archived())throw new AuthProblem(409,"Restore this product before publishing it.");
        if(!lock.categoryLive())throw new AuthProblem(409,"Choose a category that is shown in the shop before publishing.");
        if(lock.description()==null||lock.description().strip().length()<10)throw new AuthProblem(409,"Write a description of at least 10 characters before publishing.");
        if(lock.liveVariants()==0)throw new AuthProblem(409,"Turn on at least one selling option before publishing.");
        store.setStatus(actor.id(),id,true,false,"CATALOGUE_PRODUCT_PUBLISHED");
        return store.find(id).orElseThrow();
    }
    @Transactional(timeout=20)
    public ProductView unpublish(StaffPrincipal actor,UUID id,Decision decision){
        editor(actor);var lock=locked(id,decision==null?null:decision.revision());
        if(!lock.published())throw new AuthProblem(409,"This product is not published.");
        store.setStatus(actor.id(),id,false,false,"CATALOGUE_PRODUCT_UNPUBLISHED");return store.find(id).orElseThrow();
    }
    @Transactional(timeout=20)
    public ProductView archive(StaffPrincipal actor,UUID id,Decision decision){
        editor(actor);var lock=locked(id,decision==null?null:decision.revision());
        if(lock.archived())throw new AuthProblem(409,"This product is already archived.");
        store.setStatus(actor.id(),id,false,true,"CATALOGUE_PRODUCT_ARCHIVED");return store.find(id).orElseThrow();
    }
    @Transactional(timeout=20)
    public ProductView restore(StaffPrincipal actor,UUID id,Decision decision){
        editor(actor);var lock=locked(id,decision==null?null:decision.revision());
        if(!lock.archived())throw new AuthProblem(409,"This product is not archived.");
        store.setStatus(actor.id(),id,false,false,"CATALOGUE_PRODUCT_RESTORED");return store.find(id).orElseThrow();
    }

    /** Owner only: hides every development sample product at once, so the shop shows only its real stock. */
    @Transactional(timeout=30)
    public ArchivedSamples archiveSamples(StaffPrincipal actor,Decision decision){
        staff.require(actor,StaffRole.OWNER);
        if(decision==null||!"ARCHIVE SAMPLES".equals(decision.confirm()))throw new AuthProblem(400,"Type ARCHIVE SAMPLES to confirm.");
        return new ArchivedSamples(store.archiveSamples(actor.id()));
    }

    @Transactional(timeout=30)
    public ProductView uploadImage(StaffPrincipal actor,UUID productId,long revision,UUID imageId,String alt,byte[] upload){
        editor(actor);
        if(imageId==null)throw new AuthProblem(400,"The upload is missing its identity. Refresh and try again.");
        // A retried upload that already succeeded returns the result instead of a revision conflict.
        if(store.lock(productId).isPresent()&&store.imageIds(productId).contains(imageId))return store.find(productId).orElseThrow();
        locked(productId,revision);
        if(store.imageCount(productId)>=8)throw new AuthProblem(409,"A product can have at most 8 photos. Remove one first.");
        String description=text(alt,0,200,"Keep the photo description under 200 characters.");
        ProductPhotos.Photo photo;
        try{photo=ProductPhotos.process(upload);}catch(ProductPhotos.Rejected e){throw new AuthProblem(422,e.getMessage());}
        String sha=sha256(photo.jpeg());
        images.store(sha,photo.jpeg());
        store.addImage(actor.id(),productId,imageId,sha,description,photo.width(),photo.height());
        return store.find(productId).orElseThrow();
    }

    /** Reorders photos, edits their descriptions and removes any left out. The first photo is the main one. */
    @Transactional(timeout=20)
    public ProductView arrangeImages(StaffPrincipal actor,UUID productId,ImagesInput input){
        editor(actor);
        locked(productId,input==null?null:input.revision());
        var wanted=input.images()==null?List.<ImageOrder>of():input.images();
        var existing=new HashSet<>(store.imageIds(productId));var seen=new HashSet<UUID>();
        for(var image:wanted){
            if(image==null||image.id()==null||!existing.contains(image.id())||!seen.add(image.id()))throw new AuthProblem(400,"The photo list is out of date. Refresh and try again.");
            text(image.alt(),0,200,"Keep each photo description under 200 characters.");
        }
        store.arrangeImages(actor.id(),productId,wanted.stream().map(image->new ImageOrder(image.id(),image.alt()==null?"":image.alt().strip())).toList());
        return store.find(productId).orElseThrow();
    }

    @Transactional(readOnly=true)
    public StoredImage publicImage(String sha256){
        if(sha256==null||!sha256.matches("[0-9a-f]{64}")||!store.publicImage(sha256))throw new AuthProblem(404,"This photo is not available.");
        return new StoredImage(sha256,images.read(sha256));
    }
    @Transactional(readOnly=true)
    public StoredImage staffImage(StaffPrincipal actor,String sha256){
        editor(actor);
        if(sha256==null||!sha256.matches("[0-9a-f]{64}")||!store.anyImage(sha256))throw new AuthProblem(404,"This photo is not available.");
        return new StoredImage(sha256,images.read(sha256));
    }

    private void placeVariant(StaffPrincipal actor,UUID productId,CleanVariant variant,String reason){
        boolean priced=!"QUOTE".equals(variant.pricingMode());
        // An Inventory price never takes effect on its own: the option starts as "price on request" with a pending change.
        var stored=isOwner(actor)?variant:new CleanVariant(variant.id(),variant.sku(),variant.label(),variant.unitCode(),"QUOTE",null,variant.published(),variant.sortOrder());
        store.addVariant(actor.id(),productId,stored);
        if(isOwner(actor))store.recordPrice(UUID.randomUUID(),variant.id(),"QUOTE",null,variant.pricingMode(),variant.price(),reason,"APPROVED",actor.id());
        else if(priced){
            UUID change=UUID.randomUUID();
            store.recordPrice(change,variant.id(),"QUOTE",null,variant.pricingMode(),variant.price(),reason,"PENDING",actor.id());
            store.audit(actor.id(),"PRICE_CHANGE_REQUESTED","PRICE_CHANGE",change.toString());
        }
    }

    private Lock locked(UUID id,Long revision){
        var lock=store.lock(id).orElseThrow(()->new AuthProblem(404,"This product does not exist."));
        if(revision==null||revision!=lock.revision())throw new AuthProblem(409,"This product was changed by someone else. Refresh and try again.");
        return lock;
    }

    private CleanProduct clean(ProductInput input,UUID id){
        if(input==null)throw new AuthProblem(400,"Enter the product details.");
        String sku=sku(input.sku(),"product");
        if(store.productSkuTaken(sku,id))throw new AuthProblem(409,"Another product already uses the SKU "+sku+".");
        String name=text(input.name(),2,200,"Enter a product name of 2–200 characters.");
        String brand=text(input.brand(),0,100,"Keep the brand under 100 characters.");
        if(input.categoryId()==null||!store.categoryUsable(input.categoryId()))throw new AuthProblem(400,"Choose a category.");
        String description=multiline(input.description(),0,4000,"Keep the description under 4000 characters.");
        var specs=input.specifications()==null?List.<Spec>of():input.specifications();
        if(specs.size()>30)throw new AuthProblem(400,"A product can have at most 30 specifications.");
        var labels=new HashSet<String>();var cleanSpecs=new java.util.ArrayList<Spec>();
        for(var spec:specs){
            if(spec==null)throw new AuthProblem(400,"A specification is empty.");
            String label=text(spec.label(),1,100,"Each specification needs a name of up to 100 characters.");
            String value=text(spec.value(),1,500,"Each specification needs a value of up to 500 characters.");
            if(!labels.add(label.toLowerCase(java.util.Locale.ROOT)))throw new AuthProblem(400,"Specification names must be different: "+label+".");
            cleanSpecs.add(new Spec(label,value));
        }
        return new CleanProduct(id,sku,name,brand,input.categoryId(),description,Boolean.TRUE.equals(input.featured()),List.copyOf(cleanSpecs));
    }

    private CleanVariant cleanVariant(VariantInput input,int position){
        if(input==null||input.id()==null)throw new AuthProblem(400,"Enter the option details.");
        String sku=sku(input.sku(),"option");
        if(store.variantSkuTaken(sku,input.id()))throw new AuthProblem(409,"Another option already uses the SKU "+sku+".");
        String label=text(input.label(),1,120,"Name each option, for example \"50 kg bag\" (up to 120 characters).");
        if(input.unitCode()==null||store.unit(input.unitCode()).isEmpty())throw new AuthProblem(400,"Choose a selling unit for each option.");
        String mode=input.pricingMode()==null?"QUOTE":mode(input.pricingMode());
        BigDecimal price=input.pricingMode()==null?null:price(mode,input.price());
        int sort=input.sortOrder()==null?position:input.sortOrder();
        if(sort<0||sort>1000)throw new AuthProblem(400,"Option order must be between 0 and 1000.");
        return new CleanVariant(input.id(),sku,label,input.unitCode(),mode,price,input.published()==null||input.published(),sort);
    }

    private static String mode(String value){
        if(!"FIXED".equals(value)&&!"QUOTE".equals(value))throw new AuthProblem(400,"Choose a fixed price or price on request.");
        return value;
    }
    private static BigDecimal price(String mode,BigDecimal price){
        if("QUOTE".equals(mode))return null;
        if(price==null||price.signum()<0||price.compareTo(MAX_PRICE)>0||price.stripTrailingZeros().scale()>2)throw new AuthProblem(400,"Enter a price of 0 or more with at most two decimal places.");
        return price.setScale(2,java.math.RoundingMode.UNNECESSARY);
    }
    private static boolean same(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    private static String sku(String value,String what){
        String clean=value==null?"":value.strip().toUpperCase(java.util.Locale.ROOT);
        if(!clean.matches("[A-Z0-9][A-Z0-9._-]{1,79}"))throw new AuthProblem(400,"Each "+what+" SKU must be 2–80 letters, numbers, dots, dashes or underscores.");
        return clean;
    }
    private static String text(String value,int min,int max,String message){
        String clean=value==null?"":value.strip();
        if(clean.length()<min||clean.length()>max||clean.chars().anyMatch(Character::isISOControl))throw new AuthProblem(400,message);
        return clean;
    }
    private static String multiline(String value,int min,int max,String message){
        String clean=value==null?"":value.strip().replace("\r\n","\n");
        if(clean.length()<min||clean.length()>max||clean.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new AuthProblem(400,message);
        return clean;
    }
    static String sha256(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
