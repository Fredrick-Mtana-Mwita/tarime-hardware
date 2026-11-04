package africa.finishers.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import africa.finishers.identity.application.AuthProblem;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class StaffSecretVault implements africa.finishers.identity.application.StaffSecrets {
    private final String key;
    public StaffSecretVault(@Value("${FPA_STAFF_MFA_KEY:}") String key){this.key=key;}
    public String encrypt(UUID staff,byte[] secret){
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,staff,nonce,secret);
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array());
    }
    public byte[] decrypt(UUID staff,String stored){
        try{
            byte[] bytes=Base64.getDecoder().decode(stored);
            if(bytes.length<29)throw new IllegalArgumentException();
            byte[] nonce=java.util.Arrays.copyOfRange(bytes,0,12);
            return crypt(Cipher.DECRYPT_MODE,staff,nonce,java.util.Arrays.copyOfRange(bytes,12,bytes.length));
        }catch(IllegalArgumentException e){throw unavailable();}
    }
    private byte[] crypt(int mode,UUID staff,byte[] nonce,byte[] value){
        try{
            byte[] decoded=Base64.getDecoder().decode(key);
            if(decoded.length!=32)throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(decoded,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(staff.toString().getBytes(StandardCharsets.US_ASCII));return cipher.doFinal(value);
        }catch(java.security.GeneralSecurityException|IllegalArgumentException e){throw unavailable();}
    }
    private AuthProblem unavailable(){return new AuthProblem(503,"Owner security setup is unavailable. Contact the system administrator.");}
}
