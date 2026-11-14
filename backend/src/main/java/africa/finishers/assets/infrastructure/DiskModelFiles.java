package africa.finishers.assets.infrastructure;

import africa.finishers.assets.application.ModelAssets;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps uploaded GLB files in one private folder outside the web root, named by their SHA-256.
 * A name is never built from user input, so no path can escape the folder. Files are only ever
 * returned through the controller, which sends them as inert binary data.
 */
@Component
public class DiskModelFiles implements ModelAssets.Files {
    private final Path root;
    public DiskModelFiles(@Value("${fpa.models.dir:.local/model-assets}") String directory){this.root=Path.of(directory).toAbsolutePath().normalize();}

    private Path path(String sha256){
        if(sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid model file identity");
        return root.resolve(sha256+".glb");
    }
    @Override public void store(String sha256,byte[] bytes){
        try{
            Files.createDirectories(root);
            Path target=path(sha256);
            if(Files.exists(target)&&Files.size(target)==bytes.length)return;
            Path temporary=Files.createTempFile(root,"upload-",".part");
            try{Files.write(temporary,bytes);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            finally{Files.deleteIfExists(temporary);}
        }catch(IOException e){throw new UncheckedIOException("Unable to store the model file",e);}
    }
    @Override public byte[] read(String sha256){
        try{return Files.readAllBytes(path(sha256));}
        catch(java.nio.file.NoSuchFileException e){throw new AuthProblem(404,"The stored model file is missing. Upload it again.");}
        catch(IOException e){throw new UncheckedIOException("Unable to read the model file",e);}
    }
}
