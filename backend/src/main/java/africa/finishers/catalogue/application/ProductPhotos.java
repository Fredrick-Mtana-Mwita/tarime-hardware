package africa.finishers.catalogue.application;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Turns an uploaded product photo into a fresh JPEG. The format is decided from the file's content, never
 * its name; the size is checked before decoding; and re-encoding drops all metadata, including any GPS
 * location a phone camera may have recorded, so only the picture itself is ever published.
 */
public final class ProductPhotos {
    public static final int MAX_UPLOAD_BYTES=10*1024*1024;
    static final int MAX_SOURCE_SIDE=12000, MAX_SOURCE_PIXELS=50_000_000, MAX_OUTPUT_SIDE=1600;
    private static final Set<String> ACCEPTED=Set.of("jpeg","png");

    public record Photo(byte[] jpeg,int width,int height) {}
    public static final class Rejected extends RuntimeException { public Rejected(String message){super(message);} }
    private ProductPhotos(){}

    public static Photo process(byte[] upload){
        if(upload==null||upload.length<64)throw new Rejected("The file is too small to be a photo.");
        if(upload.length>MAX_UPLOAD_BYTES)throw new Rejected("The photo is larger than 10 MB.");
        BufferedImage source;
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(upload))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new Rejected("This file is not a JPEG or PNG photo. iPhone HEIC photos must be saved as JPEG first.");
            ImageReader reader=readers.next();
            try{
                if(!ACCEPTED.contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))throw new Rejected("Only JPEG and PNG photos are accepted.");
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<200||height<200)throw new Rejected("The photo is too small. Use at least 200 × 200 pixels.");
                if(width>MAX_SOURCE_SIDE||height>MAX_SOURCE_SIDE||(long)width*height>MAX_SOURCE_PIXELS)throw new Rejected("The photo has too many pixels. Use at most 12000 pixels on a side.");
                source=reader.read(0);
            }finally{reader.dispose();}
        }catch(IOException|RuntimeException e){
            if(e instanceof Rejected rejected)throw rejected;
            throw new Rejected("The photo could not be read. It may be damaged; export it again as JPEG.");
        }
        if(source==null)throw new Rejected("The photo could not be read.");
        double scale=Math.min(1,(double)MAX_OUTPUT_SIDE/Math.max(source.getWidth(),source.getHeight()));
        int width=Math.max(1,(int)Math.round(source.getWidth()*scale)),height=Math.max(1,(int)Math.round(source.getHeight()*scale));
        var output=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=output.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);g.fillRect(0,0,width,height);
            g.drawImage(source,0,0,width,height,null);
        }finally{g.dispose();}
        var bytes=new ByteArrayOutputStream();
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try(var stream=new MemoryCacheImageOutputStream(bytes)){
            ImageWriteParam params=writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(0.86f);
            writer.setOutput(stream);writer.write(null,new IIOImage(output,null,null),params);
        }catch(IOException e){throw new IllegalStateException("Unable to encode the photo",e);}
        finally{writer.dispose();}
        return new Photo(bytes.toByteArray(),width,height);
    }
}
