package africa.finishers;

import africa.finishers.identity.domain.Totp;
import africa.finishers.identity.domain.RecoveryCodes;
import africa.finishers.identity.infrastructure.StaffSecretVault;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StaffMfaTest {
    @Test void rfc6238Sha1VectorsAndReplayWindow(){
        byte[] secret="12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        long[] seconds={59,1111111109,1111111111,1234567890,2000000000,20000000000L};
        String[] expected={"94287082","07081804","14050471","89005924","69279037","65353130"};
        for(int i=0;i<seconds.length;i++)assertThat(Totp.code(secret,seconds[i]/30,8)).isEqualTo(expected[i]);
        assertThat(Totp.base32(secret)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.match(secret,"287082",59,-1)).isEqualTo(1);
        assertThat(Totp.match(secret,"287082",59,1)).isEqualTo(-1);
        assertThat(Totp.match(secret,"287082",180,-1)).isEqualTo(-1);
        assertThat(Totp.match(secret,"287082!",59,-1)).isEqualTo(-1);
    }
    @Test void encryptedSecretsAreRandomizedAndBoundToTheirAccount(){
        var vault=new StaffSecretVault(Base64.getEncoder().encodeToString(new byte[32]));UUID id=UUID.randomUUID();
        byte[] secret=Totp.newSecret();String a=vault.encrypt(id,secret),b=vault.encrypt(id,secret);
        assertThat(a).isNotEqualTo(b).doesNotContain(Totp.base32(secret));
        assertThat(vault.decrypt(id,a)).isEqualTo(secret);
        assertThatThrownBy(()->vault.decrypt(UUID.randomUUID(),a)).isInstanceOf(RuntimeException.class);
        byte[] tampered=Base64.getDecoder().decode(a);tampered[tampered.length-1]^=1;
        assertThatThrownBy(()->vault.decrypt(id,Base64.getEncoder().encodeToString(tampered))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->new StaffSecretVault("").decrypt(id,a)).isInstanceOf(RuntimeException.class);
    }
    @Test void recoveryCodesHaveHighEntropyAndStableHashesWithoutSavingPlainCodes(){
        var codes=RecoveryCodes.generate();assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        for(String code:codes){assertThat(code).matches("[A-Z2-7]{8}(-[A-Z2-7]{8}){3}");assertThat(RecoveryCodes.hash(code)).hasSize(64).isEqualTo(RecoveryCodes.hash(code.toLowerCase().replace("-",""))).isNotEqualTo(code);}
        assertThat(RecoveryCodes.hash("123456")).isNull();
    }
}
