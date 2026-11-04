package africa.finishers.identity.application;
import java.util.UUID;
public interface StaffSecrets { byte[] decrypt(UUID staff,String encrypted); }
