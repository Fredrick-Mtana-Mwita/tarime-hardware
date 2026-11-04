package africa.finishers.identity.application;
import java.util.UUID;

/** Safe account response: never return credentials, active flags or staff authority. */
public record CustomerProfile(UUID id,String name,String phone,String email,boolean contactVerified) {}
