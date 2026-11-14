package africa.finishers.assets.domain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reads the binary glTF 2.0 (GLB) container: a 12-byte header, one JSON chunk and at most one BIN chunk.
 * It checks lengths and chunk types only; the JSON content is inspected separately.
 * See https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#binary-gltf-layout
 */
public record GlbContainer(byte[] json,int binaryBytes) {
    public static final int MAX_FILE_BYTES=15*1024*1024;
    public static final int MAX_JSON_BYTES=4*1024*1024;
    private static final int MAGIC=0x46546C67, JSON=0x4E4F534A, BIN=0x004E4942;

    public static final class Invalid extends RuntimeException {
        public Invalid(String message){super(message);}
    }

    public static GlbContainer read(byte[] file){
        if(file==null||file.length<20)throw new Invalid("The file is too small to be a GLB model.");
        if(file.length>MAX_FILE_BYTES)throw new Invalid("The file is larger than 15 MB.");
        var in=ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if(in.getInt()!=MAGIC)throw new Invalid("This is not a GLB file. Export the model as binary glTF (.glb).");
        if(in.getInt()!=2)throw new Invalid("Only glTF version 2 GLB files are accepted.");
        if(Integer.toUnsignedLong(in.getInt())!=file.length)throw new Invalid("The GLB length header does not match the file size. The file may be damaged.");
        long jsonLength=Integer.toUnsignedLong(in.getInt());
        if(in.getInt()!=JSON)throw new Invalid("The first GLB chunk must be the JSON chunk.");
        if(jsonLength==0||jsonLength>MAX_JSON_BYTES||jsonLength%4!=0||jsonLength>in.remaining())throw new Invalid("The GLB JSON chunk has an invalid length.");
        byte[] json=Arrays.copyOfRange(file,in.position(),in.position()+(int)jsonLength);
        in.position(in.position()+(int)jsonLength);
        int binary=0;
        if(in.hasRemaining()){
            if(in.remaining()<8)throw new Invalid("The GLB file has trailing bytes after the JSON chunk.");
            long binLength=Integer.toUnsignedLong(in.getInt());
            if(in.getInt()!=BIN)throw new Invalid("The second GLB chunk must be the binary chunk.");
            if(binLength%4!=0||binLength!=in.remaining())throw new Invalid("The GLB binary chunk has an invalid length or is followed by extra data.");
            binary=(int)binLength;
        }
        try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json));}
        catch(CharacterCodingException e){throw new Invalid("The GLB JSON chunk is not valid UTF-8.");}
        return new GlbContainer(json,binary);
    }
}
