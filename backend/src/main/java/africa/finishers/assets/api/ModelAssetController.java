package africa.finishers.assets.api;

import africa.finishers.assets.application.ModelAssets;
import africa.finishers.assets.domain.GlbContainer;
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
import java.util.List;
import java.util.UUID;

@RestController
public class ModelAssetController {
    private static final MediaType GLB=MediaType.parseMediaType("model/gltf-binary");
    private final ModelAssets assets;
    public ModelAssetController(ModelAssets assets){this.assets=assets;}
    private StaffPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;}

    // Public: what the storefront may show, and the published files themselves.
    @GetMapping("/api/models/manifest") public ModelAssets.Manifest manifest(){return assets.manifest();}
    @GetMapping("/api/models/{id}/file") public ResponseEntity<byte[]> publicFile(@PathVariable UUID id){
        var file=assets.publicFile(id);
        return ResponseEntity.ok().contentType(GLB).eTag('"'+file.sha256()+'"').cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable()).body(file.bytes());
    }

    // Staff workspace.
    @GetMapping("/api/staff/models") public ModelAssets.Page list(Authentication auth,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return assets.list(actor(auth),status,q,page,size);}
    @GetMapping("/api/staff/models/products") public List<ModelAssets.Product> products(Authentication auth,@RequestParam(defaultValue="") String q){return assets.products(actor(auth),q);}
    @GetMapping("/api/staff/models/{id}") public ModelAssets.Asset get(Authentication auth,@PathVariable UUID id){return assets.get(actor(auth),id);}
    @PostMapping("/api/staff/models") public ModelAssets.Asset create(Authentication auth,@RequestBody ModelAssets.Draft draft){return assets.create(actor(auth),draft);}
    @PutMapping("/api/staff/models/{id}") public ModelAssets.Asset update(Authentication auth,@PathVariable UUID id,@RequestBody ModelAssets.Draft draft){return assets.update(actor(auth),id,draft);}
    @PostMapping("/api/staff/models/{id}/submit") public ModelAssets.Asset submit(Authentication auth,@PathVariable UUID id,@RequestBody ModelAssets.Decision decision){return assets.submit(actor(auth),id,decision);}
    @PostMapping("/api/staff/models/{id}/return") public ModelAssets.Asset back(Authentication auth,@PathVariable UUID id,@RequestBody ModelAssets.Decision decision){return assets.returnToDraft(actor(auth),id,decision);}
    @PostMapping("/api/staff/models/{id}/publish") public ModelAssets.Asset publish(Authentication auth,@PathVariable UUID id,@RequestBody ModelAssets.Decision decision){return assets.publish(actor(auth),id,decision);}
    @PostMapping("/api/staff/models/{id}/retire") public ModelAssets.Asset retire(Authentication auth,@PathVariable UUID id,@RequestBody ModelAssets.Decision decision){return assets.retire(actor(auth),id,decision);}
    @GetMapping("/api/staff/models/{id}/file") public ResponseEntity<byte[]> staffFile(Authentication auth,@PathVariable UUID id){
        var file=assets.staffFile(actor(auth),id);
        return ResponseEntity.ok().contentType(GLB).cacheControl(CacheControl.noStore()).body(file.bytes());
    }

    /** The GLB is sent as the raw request body. It is read with a hard limit so an oversized upload cannot exhaust memory. */
    @PostMapping(path="/api/staff/models/{id}/file",consumes={"model/gltf-binary",MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ModelAssets.Asset upload(Authentication auth,@PathVariable UUID id,@RequestParam long revision,@RequestParam(defaultValue="model.glb") String name,HttpServletRequest request) throws IOException {
        var staff=actor(auth);
        if(name.length()>200)throw new AuthProblem(400,"The file name is too long.");
        if(request.getContentLengthLong()>GlbContainer.MAX_FILE_BYTES)throw new AuthProblem(413,"The file is larger than 15 MB.");
        byte[] bytes=request.getInputStream().readNBytes(GlbContainer.MAX_FILE_BYTES+1);
        if(bytes.length>GlbContainer.MAX_FILE_BYTES)throw new AuthProblem(413,"The file is larger than 15 MB.");
        return assets.upload(staff,id,revision,name,bytes);
    }
}
