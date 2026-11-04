package africa.finishers.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

public final class RecoveryCodes {
    private RecoveryCodes(){}
    public static List<String> generate(){
        SecureRandom random=new SecureRandom();
        return IntStream.range(0,10).mapToObj(i->{byte[] bytes=new byte[20];random.nextBytes(bytes);return Totp.base32(bytes).replaceAll("(.{8})(?!$)","$1-");}).toList();
    }
    public static String hash(String value){
        if(value==null)return null;
        String normalized=value.replace("-","").strip().toUpperCase(java.util.Locale.ROOT);
        if(!normalized.matches("[A-Z2-7]{32}"))return null;
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
