package africa.finishers.catalogue.infrastructure;

import africa.finishers.catalogue.application.CatalogueAdmin;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Re-encoded product photos, kept outside the web root and named only by their SHA-256. */
@Component
public class DiskProductPhotos implements CatalogueAdmin.Images {
    private final Path root;
    public DiskProductPhotos(@Value("${fpa.photos.dir:.local/product-photos}") String directory){this.root=Path.of(directory).toAbsolutePath().normalize();}
    private Path path(String sha){if(sha==null||!sha.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid photo identity");return root.resolve(sha+".jpg");}
    @Override public void store(String sha,byte[] jpeg){
        try{
            Files.createDirectories(root);Path target=path(sha);
            if(Files.exists(target)&&Files.size(target)==jpeg.length)return;
            Path temporary=Files.createTempFile(root,"upload-",".part");
            try{Files.write(temporary,jpeg);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            finally{Files.deleteIfExists(temporary);}
        }catch(IOException e){throw new UncheckedIOException("Unable to store the photo",e);}
    }
    @Override public byte[] read(String sha){
        try{return Files.readAllBytes(path(sha));}
        catch(NoSuchFileException e){throw new AuthProblem(404,"The stored photo is missing. Upload it again.");}
        catch(IOException e){throw new UncheckedIOException("Unable to read the photo",e);}
    }
}
