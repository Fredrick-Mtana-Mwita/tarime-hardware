package africa.finishers;

import africa.finishers.assets.application.GlbInspector;
import africa.finishers.assets.application.ModelAssets;
import africa.finishers.assets.domain.GlbContainer;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModelAssetsIT {
    @LocalServerPort int port;
    @Autowired ModelAssets assets;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    static Path folder;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        Files.createDirectories(Path.of("target"));
        folder=Files.createTempDirectory(Path.of("target"),"model-files-").toAbsolutePath();
        registry.add("fpa.models.dir",()->folder.toString());
    }

    /** Other suites count the public catalogue, so nothing this suite publishes may stay visible. */
    @org.junit.jupiter.api.AfterEach void hideTestCatalogue(){
        jdbc.update("UPDATE app.products SET published=false WHERE sku LIKE 'model-%'");
        jdbc.update("UPDATE app.categories SET published=false WHERE slug LIKE 'model-%'");
    }
    StaffPrincipal actor(StaffRole role){
        UUID id=UUID.randomUUID();String name="model-"+id;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,false,?)",id,name,"Model tester",role.name(),passwords.encode("Model integration password 2026!"),role==StaffRole.OWNER?"test-encrypted-placeholder":null);
        return new StaffPrincipal(id,name,"Model tester",role,0,false);
    }
    record Item(UUID product,UUID variant,String sku){}
    Item product(){
        UUID category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();String unit="m-"+variant.toString().substring(0,15),sku="model-"+product;
        jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,?,true)",category,"model-"+category,"Model test");
        jdbc.update("INSERT INTO app.units(code,label,fractional) VALUES(?,?,false)",unit,"Test units");
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description,published) VALUES(?,?,?,?,?,?,true)",product,sku,sku,category,"Model material "+product,"Test-only material");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency,published) VALUES(?,?,?,?,?,10,'TZS',true)",variant,product,"model-"+variant,"Standard",unit);
        return new Item(product,variant,sku);
    }
    ModelAssets.Draft draft(UUID id,Item item,String source,String kind,boolean rights,Long revision){
        return new ModelAssets.Draft(id,item.product(),source,kind,"Drawn by the project team","Project-owned","",rights,null,null,null,"finishes",null,null,null,Map.of(item.variant(),new BigDecimal("0.8")),revision);
    }
    ModelAssets.Decision at(ModelAssets.Asset asset,String note){return new ModelAssets.Decision(asset.revision(),note,false);}
    ModelAssets.ManifestEntry live(Item item){return assets.manifest().entries().stream().filter(entry->entry.sku().equals(item.sku())).findFirst().orElse(null);}

    static byte[] glb(String json,int binary){
        byte[] text=json.getBytes(StandardCharsets.UTF_8);int padded=(text.length+3)/4*4,total=12+8+padded+(binary>0?8+binary:0);
        var out=ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546C67).putInt(2).putInt(total).putInt(padded).putInt(0x4E4F534A).put(text);
        for(int i=text.length;i<padded;i++)out.put((byte)' ');
        if(binary>0){out.putInt(binary).putInt(0x004E4942).put(new byte[binary]);}
        return out.array();
    }
    static final String VALID="{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":8}],\"meshes\":[{\"primitives\":[]}],\"nodes\":[{\"mesh\":0}]}";

    @Test void inventoryPreparesOwnerApprovesAndOnlyPublishedModelsArePublic(){
        var inventory=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);var item=product();
        var created=assets.create(inventory,draft(UUID.randomUUID(),item,"BUILT_IN","paint-tin",false,null));
        assertThat(created.version()).isEqualTo(1);assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(assets.create(inventory,draft(created.id(),item,"BUILT_IN","paint-tin",false,null)).revision()).isEqualTo(created.revision());
        assertThatThrownBy(()->assets.submit(inventory,created.id(),at(created,null))).isInstanceOf(AuthProblem.class).hasMessageContaining("Confirm");
        var ready=assets.update(inventory,created.id(),draft(null,item,"BUILT_IN","paint-tin",true,created.revision()));
        assertThatThrownBy(()->assets.update(inventory,created.id(),draft(null,item,"BUILT_IN","paint-tin",true,created.revision()))).isInstanceOf(AuthProblem.class).hasMessageContaining("changed by someone else");
        var review=assets.submit(inventory,created.id(),at(ready,null));
        assertThat(review.status()).isEqualTo("IN_REVIEW");assertThat(live(item)).isNull();
        // Inventory may prepare but never approve; Sales and Delivery have no access at all.
        assertThatThrownBy(()->assets.publish(inventory,created.id(),at(review,""))).isInstanceOf(AuthProblem.class);
        for(var role:List.of(StaffRole.SALES,StaffRole.DELIVERY)){var denied=actor(role);
            assertThatThrownBy(()->assets.list(denied,"","",0,12)).isInstanceOf(AuthProblem.class);
            assertThatThrownBy(()->assets.create(denied,draft(UUID.randomUUID(),item,"BUILT_IN","paint-tin",true,null))).isInstanceOf(AuthProblem.class);}
        assertThatThrownBy(()->assets.update(inventory,created.id(),draft(null,item,"BUILT_IN","pipe",true,review.revision()))).isInstanceOf(AuthProblem.class).hasMessageContaining("Only drafts");
        assertThatThrownBy(()->assets.publish(owner,created.id(),new ModelAssets.Decision(review.revision(),"",true))).isInstanceOf(AuthProblem.class).hasMessageContaining("built-in");
        var published=assets.publish(owner,created.id(),at(review,"Looks right"));
        assertThat(published.status()).isEqualTo("PUBLISHED");assertThat(published.verifiedExact()).isFalse();
        var entry=live(item);
        assertThat(entry.modelKind()).isEqualTo("paint-tin");assertThat(entry.modelUrl()).isNull();assertThat(entry.verified()).isFalse();
        assertThat(entry.variantScales().get(item.variant())).isEqualByComparingTo("0.8");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=?",Long.class,created.id().toString())).isEqualTo(3);
        // Unpublishing the product removes its model from the public manifest.
        jdbc.update("UPDATE app.products SET published=false WHERE id=?",item.product());assertThat(live(item)).isNull();
        jdbc.update("UPDATE app.products SET published=true WHERE id=?",item.product());
        var retired=assets.retire(owner,created.id(),at(published,"No longer stocked in this form"));
        assertThat(retired.status()).isEqualTo("RETIRED");assertThat(live(item)).isNull();
    }

    @Test void uploadedGlbIsCheckedStoredAndReplacesTheLiveVersionWithoutBreakingOldReferences() throws Exception {
        var inventory=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);var item=product();
        var first=assets.create(inventory,draft(UUID.randomUUID(),item,"BUILT_IN","pipe",true,null));
        var firstLive=assets.publish(owner,first.id(),at(assets.submit(inventory,first.id(),at(first,null)),""));
        var second=assets.create(inventory,draft(UUID.randomUUID(),item,"GLB",null,true,null));
        assertThat(second.version()).isEqualTo(2);
        assertThatThrownBy(()->assets.submit(inventory,second.id(),at(second,null))).isInstanceOf(AuthProblem.class).hasMessageContaining("Upload");
        byte[] file=glb(VALID,8);
        var uploaded=assets.upload(inventory,second.id(),second.revision(),"../../evil name?.glb",file);
        assertThat(uploaded.fileBytes()).isEqualTo(file.length);assertThat(uploaded.fileName()).isEqualTo(".._.._evil name_.glb");
        assertThat(uploaded.validation().meshes()).isEqualTo(1);
        assertThat(Files.list(folder).map(path->path.getFileName().toString()).toList()).contains(uploaded.fileSha256()+".glb");
        HttpClient http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
        // A draft file is never public.
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/models/"+second.id()+"/file")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        var review=assets.submit(inventory,second.id(),at(uploaded,null));
        assertThatThrownBy(()->assets.publish(owner,second.id(),new ModelAssets.Decision(review.revision(),"",true))).isInstanceOf(AuthProblem.class).hasMessageContaining("measured");
        var secondLive=assets.publish(owner,second.id(),at(review,""));
        assertThat(assets.get(owner,firstLive.id()).status()).isEqualTo("RETIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.model_assets WHERE product_id=? AND status='PUBLISHED'",Long.class,item.product())).isEqualTo(1);
        var entry=live(item);
        assertThat(entry.version()).isEqualTo(2);assertThat(entry.modelUrl()).isEqualTo("/api/models/"+secondLive.id()+"/file");
        var served=http.send(HttpRequest.newBuilder(URI.create(base+entry.modelUrl())).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);assertThat(served.body()).isEqualTo(file);
        assertThat(served.headers().firstValue("Content-Type")).contains("model/gltf-binary");
        assertThat(served.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        // Rows are never deleted, so an order that recorded version 1 still points at a real record.
        assertThatThrownBy(()->jdbc.update("DELETE FROM app.products WHERE id=?",item.product())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/models/manifest")).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/models")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/models/"+second.id()+"/file")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/models")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isIn(401,403);
    }

    @Test void unsafeOrUnsupportedGlbFilesAreRefusedBeforeAnythingIsStored() throws Exception {
        var inventory=actor(StaffRole.INVENTORY);var item=product();
        var asset=assets.create(inventory,draft(UUID.randomUUID(),item,"GLB",null,true,null));
        byte[] valid=glb(VALID,8);
        byte[] wrongMagic=valid.clone();wrongMagic[0]='X';
        byte[] wrongLength=valid.clone();wrongLength[8]++;
        var trailing=new ByteArrayOutputStream();trailing.write(valid);trailing.write(new byte[4]);
        var bad=Map.of(
            "not a GLB",wrongMagic,"length header",wrongLength,"length header ",trailing.toByteArray(),"too small",new byte[10],
            "another file",glb("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"https://example.com/a.bin\",\"byteLength\":8}],\"meshes\":[{}]}",8),
            "Embed textures",glb("{\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"file:///etc/passwd\"}],\"meshes\":[{}]}",0),
            "unsupported extensions",glb("{\"asset\":{\"version\":\"2.0\"},\"extensionsRequired\":[\"KHR_draco_mesh_compression\"],\"meshes\":[{}]}",0),
            "no meshes",glb("{\"asset\":{\"version\":\"2.0\"},\"meshes\":[]}",0),
            "version 2",glb("{\"asset\":{\"version\":\"1.0\"},\"meshes\":[{}]}",0),
            "larger than the GLB binary chunk",glb("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":4096}],\"meshes\":[{}]}",8));
        for(var entry:bad.entrySet())assertThatThrownBy(()->assets.upload(inventory,asset.id(),asset.revision(),"model.glb",entry.getValue())).as(entry.getKey())
            .isInstanceOf(AuthProblem.class).hasMessageContaining(entry.getKey().strip()).extracting(problem->((AuthProblem)problem).status()).isEqualTo(422);
        assertThatThrownBy(()->GlbContainer.read(new byte[GlbContainer.MAX_FILE_BYTES+1])).isInstanceOf(GlbContainer.Invalid.class).hasMessageContaining("15 MB");
        assertThat(assets.get(inventory,asset.id()).fileSha256()).isNull();
        var report=GlbInspector.inspect(glb("{\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"KHR_materials_unlit\",\"EXT_unknown\"],\"animations\":[{}],\"meshes\":[{}]}",0));
        assertThat(report.warnings()).hasSize(2);
        // Validation of draft fields.
        for(var wrong:List.of(
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"BUILT_IN","spaceship","","","",true,null,null,null,"finishes",null,null,null,Map.of(),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,null,null,null,"garden",null,null,null,Map.of(),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,null,null,null,"finishes","floor","pendant",null,Map.of(),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,null,null,null,"finishes","light","bulb",600,Map.of(),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,0,null,null,"finishes",null,null,null,Map.of(),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,null,null,null,"finishes",null,null,null,Map.of(UUID.randomUUID(),BigDecimal.ONE),null),
            new ModelAssets.Draft(UUID.randomUUID(),item.product(),"GLB",null,"","","",true,null,null,null,"finishes",null,null,null,Map.of(item.variant(),new BigDecimal("9")),null)))
            assertThatThrownBy(()->assets.create(inventory,wrong)).isInstanceOf(AuthProblem.class).extracting(problem->((AuthProblem)problem).status()).isEqualTo(400);
    }

    @Test void orderLinesRecordTheModelVersionThatWasLiveWhenTheOrderWasPlaced(){
        var inventory=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);var item=product();
        UUID customer=UUID.randomUUID();
        jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,?,?,?)",customer,"Model customer","+2557"+String.format("%08d",Math.floorMod(customer.getLeastSignificantBits(),100000000L)),"x");
        java.util.function.Supplier<UUID> order=()->{UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,fulfilment,contact_name,contact_phone,currency,subtotal,branch_code) VALUES(?,?,?,?,?,'PICKUP','Model customer','+255700000000','TZS',10,'TARIME')",id,"M-"+id.toString().substring(0,30),customer,UUID.randomUUID(),"0".repeat(64));
            jdbc.update("INSERT INTO app.customer_order_items(order_id,variant_id,product_id,product_name,variant_label,sku,unit,quantity,unit_price,line_total) VALUES(?,?,?,?,?,?,?,1,10,10)",id,item.variant(),item.product(),"Model material","Standard",item.sku(),"Test units");
            return id;};
        UUID before=order.get();
        assertThat(jdbc.queryForMap("SELECT model_asset_id,model_asset_version FROM app.customer_order_items WHERE order_id=?",before).get("model_asset_id")).isNull();
        var one=assets.create(inventory,draft(UUID.randomUUID(),item,"BUILT_IN","hammer",true,null));
        assets.publish(owner,one.id(),at(assets.submit(inventory,one.id(),at(one,null)),""));
        UUID during=order.get();
        var two=assets.create(inventory,draft(UUID.randomUUID(),item,"BUILT_IN","pipe",true,null));
        assets.publish(owner,two.id(),at(assets.submit(inventory,two.id(),at(two,null)),""));
        UUID after=order.get();
        assertThat(jdbc.queryForMap("SELECT model_asset_id,model_asset_version FROM app.customer_order_items WHERE order_id=?",during)).containsEntry("model_asset_id",one.id()).containsEntry("model_asset_version",1);
        assertThat(jdbc.queryForMap("SELECT model_asset_id,model_asset_version FROM app.customer_order_items WHERE order_id=?",after)).containsEntry("model_asset_id",two.id()).containsEntry("model_asset_version",2);
    }

    @Test void ownerCanReturnAModelWithAReasonAndTheListPutsReviewsFirst(){
        var inventory=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);var item=product();
        var created=assets.create(inventory,draft(UUID.randomUUID(),item,"BUILT_IN","roller",true,null));
        var review=assets.submit(inventory,created.id(),at(created,null));
        assertThat(assets.list(owner,"IN_REVIEW",item.sku(),0,12).items()).extracting(ModelAssets.Asset::id).containsExactly(created.id());
        assertThat(assets.list(owner,"","",0,12).inReview()).isGreaterThanOrEqualTo(1);
        assertThatThrownBy(()->assets.returnToDraft(owner,created.id(),at(review,"no"))).isInstanceOf(AuthProblem.class);
        assertThatThrownBy(()->assets.returnToDraft(inventory,created.id(),at(review,"Please add the licence"))).isInstanceOf(AuthProblem.class);
        var returned=assets.returnToDraft(owner,created.id(),at(review,"Please add the licence"));
        assertThat(returned.status()).isEqualTo("DRAFT");assertThat(returned.reviewNote()).isEqualTo("Please add the licence");
        assertThatThrownBy(()->assets.retire(owner,created.id(),at(returned,"Not needed any more"))).isInstanceOf(AuthProblem.class).hasMessageContaining("published");
        assertThat(assets.products(inventory,item.sku())).singleElement().satisfies(found->{assertThat(found.variants()).hasSize(1);assertThat(found.publishedVersion()).isNull();});
    }
}
