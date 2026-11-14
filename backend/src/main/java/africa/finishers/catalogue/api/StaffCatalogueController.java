package africa.finishers.catalogue.api;

import africa.finishers.catalogue.application.CatalogueAdmin;
import africa.finishers.catalogue.application.CatalogueAdmin.*;
import africa.finishers.catalogue.application.ProductPhotos;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
public class StaffCatalogueController {
    private final CatalogueAdmin catalogue;
    public StaffCatalogueController(CatalogueAdmin catalogue){this.catalogue=catalogue;}
    private StaffPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;}
    private static final String BASE="/api/staff/catalogue";

    @GetMapping(BASE+"/options") public Options options(Authentication auth){return catalogue.options(actor(auth));}
    @GetMapping(BASE+"/products") public Page list(Authentication auth,@RequestParam(defaultValue="") String filter,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return catalogue.list(actor(auth),filter,q,page,size);}
    @GetMapping(BASE+"/products/{id}") public ProductView get(Authentication auth,@PathVariable UUID id){return catalogue.get(actor(auth),id);}
    @PostMapping(BASE+"/products") public ProductView create(Authentication auth,@RequestBody ProductInput input){return catalogue.create(actor(auth),input);}
    @PutMapping(BASE+"/products/{id}") public ProductView update(Authentication auth,@PathVariable UUID id,@RequestBody ProductInput input){return catalogue.update(actor(auth),id,input);}
    @PostMapping(BASE+"/products/{id}/variants") public ProductView addVariant(Authentication auth,@PathVariable UUID id,@RequestBody VariantInput input){return catalogue.addVariant(actor(auth),id,input);}
    @PutMapping(BASE+"/products/{id}/variants/{variantId}") public ProductView updateVariant(Authentication auth,@PathVariable UUID id,@PathVariable UUID variantId,@RequestBody VariantInput input){return catalogue.updateVariant(actor(auth),id,variantId,input);}
    @PostMapping(BASE+"/products/{id}/variants/{variantId}/price") public ProductView price(Authentication auth,@PathVariable UUID id,@PathVariable UUID variantId,@RequestBody PriceInput input){return catalogue.changePrice(actor(auth),id,variantId,input);}
    @PostMapping(BASE+"/products/{id}/publish") public ProductView publish(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.publish(actor(auth),id,decision);}
    @PostMapping(BASE+"/products/{id}/unpublish") public ProductView unpublish(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.unpublish(actor(auth),id,decision);}
    @PostMapping(BASE+"/products/{id}/archive") public ProductView archive(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.archive(actor(auth),id,decision);}
    @PostMapping(BASE+"/products/{id}/restore") public ProductView restore(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.restore(actor(auth),id,decision);}
    @PutMapping(BASE+"/products/{id}/photos") public ProductView arrange(Authentication auth,@PathVariable UUID id,@RequestBody ImagesInput input){return catalogue.arrangeImages(actor(auth),id,input);}
    @PostMapping(BASE+"/samples/archive") public ArchivedSamples archiveSamples(Authentication auth,@RequestBody Decision decision){return catalogue.archiveSamples(actor(auth),decision);}
    @GetMapping(BASE+"/price-changes") public PriceChanges changes(Authentication auth,@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return catalogue.priceChanges(actor(auth),status,page,size);}
    @PostMapping(BASE+"/price-changes/{id}/approve") public PriceChange approve(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.approvePrice(actor(auth),id,decision);}
    @PostMapping(BASE+"/price-changes/{id}/reject") public PriceChange reject(Authentication auth,@PathVariable UUID id,@RequestBody Decision decision){return catalogue.rejectPrice(actor(auth),id,decision);}

    /** A photo is sent as the raw request body and read with a hard size limit. */
    @PostMapping(path=BASE+"/products/{id}/photos",consumes={MediaType.IMAGE_JPEG_VALUE,MediaType.IMAGE_PNG_VALUE,MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ProductView upload(Authentication auth,@PathVariable UUID id,@RequestParam long revision,@RequestParam UUID photo,@RequestParam(defaultValue="") String alt,HttpServletRequest request) throws IOException {
        var staff=actor(auth);
        if(request.getContentLengthLong()>ProductPhotos.MAX_UPLOAD_BYTES)throw new AuthProblem(413,"The photo is larger than 10 MB.");
        byte[] bytes=request.getInputStream().readNBytes(ProductPhotos.MAX_UPLOAD_BYTES+1);
        if(bytes.length>ProductPhotos.MAX_UPLOAD_BYTES)throw new AuthProblem(413,"The photo is larger than 10 MB.");
        return catalogue.uploadImage(staff,id,revision,photo,alt,bytes);
    }
    @GetMapping(BASE+"/photos/{sha}.jpg") public ResponseEntity<byte[]> staffPhoto(Authentication auth,@PathVariable String sha){
        var image=catalogue.staffImage(actor(auth),sha);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(image.bytes());
    }
    /** Public only while a published product uses the photo. */
    @GetMapping("/api/product-images/{sha}.jpg") public ResponseEntity<byte[]> publicPhoto(@PathVariable String sha){
        var image=catalogue.publicImage(sha);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag('"'+image.sha256()+'"').cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(image.bytes());
    }
}
