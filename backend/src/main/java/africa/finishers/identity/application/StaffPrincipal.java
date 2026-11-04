package africa.finishers.identity.application;

import africa.finishers.identity.domain.StaffRole;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** Session snapshot only. Every protected staff operation validates its current version and role. */
public record StaffPrincipal(UUID id,String username,String name,StaffRole role,long version,boolean passwordChangeRequired)
        implements Principal,Serializable {
    @Override public String getName(){return "staff:"+id;}
}
