package africa.finishers;

import africa.finishers.catalogue.application.CatalogueAdmin;
import africa.finishers.catalogue.application.CatalogueAdmin.*;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogueAdminIT {
    @LocalServerPort int port;
    @Autowired CatalogueAdmin catalogue;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    static Path photos;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        Files.createDirectories(Path.of("target"));
        photos=Files.createTempDirectory(Path.of("target"),"product-photos-").toAbsolutePath();
        registry.add("fpa.photos.dir",()->photos.toString());
    }
    /** Other suites count the public catalogue, so nothing published here may stay visible. */
    @AfterEach void hide(){jdbc.update("UPDATE app.products SET published=false WHERE sku LIKE 'CAT-%'");jdbc.update("UPDATE app.categories SET published=false WHERE slug LIKE 'cat-%'");}

    StaffPrincipal actor(StaffRole role){
        UUID id=UUID.randomUUID();String name="catalogue-"+id;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,false,?)",id,name,role+" tester",role.name(),passwords.encode("Catalogue integration password 2026!"),role==StaffRole.OWNER?"test-encrypted-placeholder":null);
        return new StaffPrincipal(id,name,role+" tester",role,0,false);
    }
    UUID category(boolean published){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,?,?)",id,"cat-"+id,"Catalogue test",published);return id;}
    static String code(){return "CAT-"+UUID.randomUUID().toString().substring(0,13).toUpperCase();}
    ProductInput input(UUID category,String price){
        return new ProductInput(UUID.randomUUID(),code(),"Real Shop Cement 42.5N","Simba",category,"Cement from the shop's own supplier, checked on delivery.",false,
            List.of(new Spec("Strength","42.5N"),new Spec("Bag","50 kg")),
            List.of(new VariantInput(UUID.randomUUID(),code(),"50 kg bag","piece",price==null?"QUOTE":"FIXED",price==null?null:new BigDecimal(price),true,0,null)),null);
    }
    BigDecimal effective(UUID variant){return jdbc.queryForObject("SELECT unit_price FROM app.product_variants WHERE id=?",BigDecimal.class,variant);}

    @Test void inventoryPriceWaitsForTheOwnerAndNeverTakesEffectByItself(){
        var stock=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);
        var created=catalogue.create(stock,input(category(true),"18500"));
        var variant=created.variants().getFirst();
        assertThat(created.published()).isFalse();assertThat(created.sample()).isFalse();
        assertThat(variant.pricingMode()).isEqualTo("QUOTE");assertThat(variant.price()).isNull();
        assertThat(variant.pending().requestedPrice()).isEqualByComparingTo("18500");
        assertThat(catalogue.create(stock,new ProductInput(created.id(),null,null,null,null,null,null,null,null,null)).revision()).isEqualTo(created.revision());
        // A second request for the same option is refused until the Owner decides.
        assertThatThrownBy(()->catalogue.changePrice(stock,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal("17000"),"Supplier discount",created.revision())))
            .isInstanceOf(AuthProblem.class).hasMessageContaining("already waiting");
        assertThatThrownBy(()->catalogue.approvePrice(stock,variant.pending().id(),new Decision(null,"",null))).isInstanceOf(AuthProblem.class);
        assertThat(effective(variant.id())).isNull();
        var approved=catalogue.approvePrice(owner,variant.pending().id(),new Decision(null,"Matches the supplier list",null));
        assertThat(approved.status()).isEqualTo("APPROVED");assertThat(effective(variant.id())).isEqualByComparingTo("18500");
        var after=catalogue.get(stock,created.id());
        assertThat(after.variants().getFirst().pricingMode()).isEqualTo("FIXED");assertThat(after.variants().getFirst().pending()).isNull();
        // Inventory requests a change; the old price stays in force; the Owner rejects it with a reason.
        var waiting=catalogue.changePrice(stock,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal("16000"),"Competitor price",after.revision()));
        assertThat(effective(variant.id())).isEqualByComparingTo("18500");
        UUID request=waiting.variants().getFirst().pending().id();
        assertThatThrownBy(()->catalogue.rejectPrice(owner,request,new Decision(null,"",null))).isInstanceOf(AuthProblem.class);
        assertThat(catalogue.rejectPrice(owner,request,new Decision(null,"Keep current price",null)).status()).isEqualTo("REJECTED");
        assertThat(effective(variant.id())).isEqualByComparingTo("18500");
        assertThatThrownBy(()->catalogue.approvePrice(owner,request,new Decision(null,"",null))).isInstanceOf(AuthProblem.class).hasMessageContaining("already been decided");
        // Decided history cannot be edited or deleted.
        assertThatThrownBy(()->jdbc.update("UPDATE app.price_changes SET requested_price=1 WHERE id=?",request)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM app.price_changes WHERE id=?",request)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(catalogue.priceChanges(owner,"",0,48).items()).filteredOn(c->c.variantId().equals(variant.id())).hasSize(2);
    }

    @Test void ownerPricesApplyAtOnceAndAStaleRequestCannotUndoThem(){
        var stock=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);
        var created=catalogue.create(owner,input(category(true),"9000"));var variant=created.variants().getFirst();
        assertThat(variant.price()).isEqualByComparingTo("9000");assertThat(variant.pending()).isNull();
        var waiting=catalogue.changePrice(stock,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal("8000"),"Old supplier price",created.revision()));
        UUID stale=waiting.variants().getFirst().pending().id();
        // The Owner sets a price directly: the waiting request is closed, not left to overwrite it later.
        var direct=catalogue.changePrice(owner,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal("9500"),"New season price",waiting.revision()));
        assertThat(direct.variants().getFirst().price()).isEqualByComparingTo("9500");assertThat(direct.variants().getFirst().pending()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM app.price_changes WHERE id=?",String.class,stale)).isEqualTo("REJECTED");
        assertThatThrownBy(()->catalogue.changePrice(owner,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal("9500.00"),"Same",direct.revision()))).hasMessageContaining("already the current price");
        // A request made against an older price cannot be approved after the price moved underneath it.
        var request=catalogue.changePrice(stock,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"QUOTE",null,"Price varies by load",direct.revision()));
        UUID pending=request.variants().getFirst().pending().id();
        jdbc.update("UPDATE app.product_variants SET unit_price=9600 WHERE id=?",variant.id());
        assertThatThrownBy(()->catalogue.approvePrice(owner,pending,new Decision(null,"",null))).isInstanceOf(AuthProblem.class).hasMessageContaining("changed after this request");
        for(var bad:List.of("-1","1.234","1000000000000"))assertThatThrownBy(()->catalogue.changePrice(owner,created.id(),variant.id(),new PriceInput(UUID.randomUUID(),"FIXED",new BigDecimal(bad),"Bad",request.revision()))).isInstanceOf(AuthProblem.class);
    }

    @Test void publishingNeedsCompleteDetailsAndArchivingKeepsHistory() throws Exception {
        var stock=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);
        var hidden=category(false);var created=catalogue.create(owner,input(hidden,"12000"));
        assertThatThrownBy(()->catalogue.publish(stock,created.id(),new Decision(created.revision(),null,null))).hasMessageContaining("category");
        var shown=category(true);
        var moved=catalogue.update(stock,created.id(),new ProductInput(null,created.sku(),created.name()+" (moved)",created.brand(),shown,"Short",false,List.of(),null,created.revision()));
        assertThatThrownBy(()->catalogue.publish(stock,created.id(),new Decision(moved.revision(),null,null))).hasMessageContaining("description");
        assertThatThrownBy(()->catalogue.update(stock,created.id(),new ProductInput(null,created.sku(),"Name",created.brand(),shown,"A long enough description",false,List.of(),null,created.revision()))).hasMessageContaining("changed by someone else");
        var described=catalogue.update(stock,created.id(),new ProductInput(null,created.sku(),"Real Shop Cement",created.brand(),shown,"A long enough description",false,List.of(new Spec("Strength","42.5N")),null,moved.revision()));
        var variant=described.variants().getFirst();
        var off=catalogue.updateVariant(stock,created.id(),variant.id(),new VariantInput(null,variant.sku(),variant.label(),variant.unitCode(),null,null,false,0,described.revision()));
        assertThatThrownBy(()->catalogue.publish(stock,created.id(),new Decision(off.revision(),null,null))).hasMessageContaining("selling option");
        var on=catalogue.updateVariant(stock,created.id(),variant.id(),new VariantInput(null,variant.sku(),variant.label(),variant.unitCode(),null,null,true,0,off.revision()));
        var live=catalogue.publish(stock,created.id(),new Decision(on.revision(),null,null));
        assertThat(live.published()).isTrue();
        var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
        var page=http.send(HttpRequest.newBuilder(URI.create(base+"/api/products/"+created.id())).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);assertThat(page.body()).contains("Real Shop Cement").contains("12000").contains("42.5N");
        // Stock exists, so the unit is locked.
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code,on_hand) VALUES(?,'TARIME',5)",variant.id());
        assertThatThrownBy(()->catalogue.updateVariant(stock,created.id(),variant.id(),new VariantInput(null,variant.sku(),variant.label(),"bag",null,null,true,0,live.revision()))).hasMessageContaining("selling unit cannot change");
        var archived=catalogue.archive(stock,created.id(),new Decision(live.revision(),null,null));
        assertThat(archived.archived()).isTrue();assertThat(archived.published()).isFalse();
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/products/"+created.id())).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        assertThatThrownBy(()->catalogue.publish(stock,created.id(),new Decision(archived.revision(),null,null))).hasMessageContaining("Restore");
        assertThat(catalogue.restore(stock,created.id(),new Decision(archived.revision(),null,null)).archived()).isFalse();
        assertThatThrownBy(()->jdbc.update("UPDATE app.products SET archived=true,published=true WHERE id=?",created.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type LIKE 'CATALOGUE_PRODUCT_%'",Long.class,created.id().toString())).isGreaterThanOrEqualTo(6);
    }

    static byte[] png(int width,int height,String secret) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(new Color(242,169,0));g.fillRect(0,0,width,height);g.dispose();
        var writer=ImageIO.getImageWritersByFormatName("png").next();var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),null);
        var text=new IIOMetadataNode("tEXtEntry");text.setAttribute("keyword","Location");text.setAttribute("value",secret);
        var root=new IIOMetadataNode("javax_imageio_png_1.0");var chunk=new IIOMetadataNode("tEXt");chunk.appendChild(text);root.appendChild(chunk);
        metadata.mergeTree("javax_imageio_png_1.0",root);
        var out=new ByteArrayOutputStream();try(var stream=ImageIO.createImageOutputStream(out)){writer.setOutput(stream);writer.write(new IIOImage(image,null,metadata));}
        return out.toByteArray();
    }

    @Test void photosAreReencodedWithoutMetadataAndOnlyPublicOnceTheProductIsLive() throws Exception {
        var stock=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);
        var created=catalogue.create(owner,input(category(true),"500"));
        byte[] upload=png(2400,1800,"GPS -1.2921 36.8219 private home");
        assertThat(new String(upload,StandardCharsets.ISO_8859_1)).contains("private home");
        UUID photo=UUID.randomUUID();
        var withPhoto=catalogue.uploadImage(stock,created.id(),created.revision(),photo,"Front of the bag",upload);
        assertThat(catalogue.uploadImage(stock,created.id(),created.revision(),photo,"Front of the bag",upload).images()).hasSize(1);
        var image=withPhoto.images().getFirst();
        assertThat(image.uploaded()).isTrue();assertThat(image.width()).isEqualTo(1600);assertThat(image.height()).isEqualTo(1200);
        String sha=image.path().replaceAll(".*/([0-9a-f]{64})\\.jpg$","$1");
        byte[] stored=Files.readAllBytes(photos.resolve(sha+".jpg"));
        assertThat(stored[0]&0xff).isEqualTo(0xFF);assertThat(stored[1]&0xff).isEqualTo(0xD8);
        assertThat(new String(stored,StandardCharsets.ISO_8859_1)).doesNotContain("private home").doesNotContain("GPS");
        var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+image.path())).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        var live=catalogue.publish(owner,created.id(),new Decision(withPhoto.revision(),null,null));
        var served=http.send(HttpRequest.newBuilder(URI.create(base+image.path())).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);assertThat(served.body()).isEqualTo(stored);
        assertThat(served.headers().firstValue("Content-Type")).contains("image/jpeg");
        // Refused files: wrong type pretending to be a photo, too small, damaged.
        byte[] fake="<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'></svg>".repeat(4).getBytes(StandardCharsets.UTF_8);
        byte[] damaged=java.util.Arrays.copyOf(png(800,800,"x"),300);
        for(var bad:List.of(fake,png(100,100,"tiny"),damaged))
            assertThatThrownBy(()->catalogue.uploadImage(stock,created.id(),live.revision(),UUID.randomUUID(),"",bad)).isInstanceOf(AuthProblem.class).extracting(e->((AuthProblem)e).status()).isEqualTo(422);
        var second=catalogue.uploadImage(stock,created.id(),live.revision(),UUID.randomUUID(),"Side",png(900,900,"x"));
        var reordered=catalogue.arrangeImages(stock,created.id(),new ImagesInput(second.revision(),List.of(new ImageOrder(second.images().get(1).id(),"Side view"))));
        assertThat(reordered.images()).singleElement().satisfies(kept->assertThat(kept.alt()).isEqualTo("Side view"));
        assertThatThrownBy(()->catalogue.arrangeImages(stock,created.id(),new ImagesInput(reordered.revision(),List.of(new ImageOrder(UUID.randomUUID(),""))))).hasMessageContaining("out of date");
    }

    @Test void onlyOwnerAndInventoryManageTheCatalogueAndOnlyTheOwnerArchivesAllSamples() throws Exception {
        var stock=actor(StaffRole.INVENTORY);var owner=actor(StaffRole.OWNER);var shown=category(true);
        for(var role:List.of(StaffRole.SALES,StaffRole.DELIVERY)){var denied=actor(role);
            assertThatThrownBy(()->catalogue.list(denied,"","",0,12)).isInstanceOf(AuthProblem.class);
            assertThatThrownBy(()->catalogue.create(denied,input(shown,"1"))).isInstanceOf(AuthProblem.class);}
        UUID sample=UUID.randomUUID();
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description,published,sample_data) VALUES(?,?,?,?,?,?,true,true)",sample,code(),"cat-sample-"+sample,shown,"Sample thing","Sample description here");
        assertThat(catalogue.list(owner,"sample","",0,48).items()).extracting(Summary::id).contains(sample);
        assertThatThrownBy(()->catalogue.archiveSamples(stock,new Decision(null,null,"ARCHIVE SAMPLES"))).isInstanceOf(AuthProblem.class);
        assertThatThrownBy(()->catalogue.archiveSamples(owner,new Decision(null,null,"yes"))).hasMessageContaining("ARCHIVE SAMPLES");
        assertThat(catalogue.archiveSamples(owner,new Decision(null,null,"ARCHIVE SAMPLES")).archived()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT archived FROM app.products WHERE id=?",Boolean.class,sample)).isTrue();
        assertThat(catalogue.list(owner,"sample","",0,48).items()).isEmpty();
        // Duplicate SKUs and bad input are refused.
        var first=catalogue.create(stock,input(shown,null));
        var copy=input(shown,null);
        assertThatThrownBy(()->catalogue.create(stock,new ProductInput(copy.id(),first.sku(),copy.name(),copy.brand(),shown,copy.description(),false,List.of(),copy.variants(),null))).hasMessageContaining("already uses the SKU");
        assertThatThrownBy(()->catalogue.create(stock,new ProductInput(UUID.randomUUID(),code(),"X",null,shown,"",false,List.of(),copy.variants(),null))).isInstanceOf(AuthProblem.class);
        assertThatThrownBy(()->catalogue.create(stock,new ProductInput(UUID.randomUUID(),code(),"Name",null,shown,"",false,List.of(new Spec("A","1"),new Spec("a","2")),List.of(new VariantInput(UUID.randomUUID(),code(),"One","piece",null,null,true,0,null)),null))).hasMessageContaining("must be different");
        assertThatThrownBy(()->catalogue.create(stock,new ProductInput(UUID.randomUUID(),code(),"Name",null,shown,"",false,List.of(),List.of(new VariantInput(UUID.randomUUID(),code(),"One","no-such-unit",null,null,true,0,null)),null))).hasMessageContaining("selling unit");
        assertThatThrownBy(()->catalogue.create(stock,new ProductInput(UUID.randomUUID(),"bad sku!","Name",null,shown,"",false,List.of(),copy.variants(),null))).hasMessageContaining("SKU");
        // Real HTTP: sign-in and CSRF are required.
        var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/catalogue/products")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/catalogue/products")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isIn(401,403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.units WHERE code IN ('piece','bag','litre','metre','kilogram','square-metre')",Long.class)).isEqualTo(6);
    }
}
