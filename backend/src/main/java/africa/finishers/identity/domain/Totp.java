package africa.finishers.identity.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** RFC 6238 HMAC-SHA1, 30-second steps, six decimal digits for authenticator interoperability. */
public final class Totp {
    private Totp(){}
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public static byte[] newSecret(){byte[] bytes=new byte[20];new SecureRandom().nextBytes(bytes);return bytes;}
    public static String code(byte[] secret,long step,int digits){
        if(step<0||digits<6||digits>8)throw new IllegalArgumentException("Invalid OTP parameters");
        try{
            Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret,"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset=hash[hash.length-1]&15;
            int binary=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);
            int divisor=digits==6?1000000:digits==7?10000000:100000000;
            return String.format(java.util.Locale.ROOT,"%0"+digits+"d",binary%divisor);
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("OTP unavailable",e);}
    }
    public static long match(byte[] secret,String value,long epochSeconds,long lastStep){
        if(value==null||!value.matches("[0-9]{6}"))return -1;
        long current=epochSeconds/30,matched=-1;
        for(long step=Math.max(0,current-1);step<=current+1;step++){
            boolean equal=MessageDigest.isEqual(code(secret,step,6).getBytes(StandardCharsets.US_ASCII),value.getBytes(StandardCharsets.US_ASCII));
            if(equal&&step>lastStep)matched=step;
        }
        return matched;
    }
    public static String base32(byte[] bytes){
        StringBuilder text=new StringBuilder();int buffer=0,bits=0;
        for(byte b:bytes){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;text.append(ALPHABET.charAt((buffer>>bits)&31));}}
        if(bits>0)text.append(ALPHABET.charAt((buffer<<(5-bits))&31));return text.toString();
    }
}
