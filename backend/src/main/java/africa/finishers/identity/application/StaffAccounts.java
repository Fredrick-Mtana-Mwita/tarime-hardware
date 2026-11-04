package africa.finishers.identity.application;

import africa.finishers.identity.domain.StaffRole;
import africa.finishers.identity.domain.Totp;
import africa.finishers.identity.domain.RecoveryCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class StaffAccounts {
    public record Profile(UUID id,String username,String name,StaffRole role,boolean passwordChangeRequired) {}
    public record ManagedAccount(Profile profile,boolean active,long revision) {}
    private final StaffRepository repository;
    private final PasswordEncoder passwords;
    private final StaffSecrets secrets;
    private final Clock clock;
    private final String dummyHash;
    private final boolean recoveryEnabled;
    public StaffAccounts(StaffRepository repository,PasswordEncoder passwords,StaffSecrets secrets,Clock clock,@Value("${FPA_STAFF_RECOVERY_ENABLED:false}") boolean recoveryEnabled){
        this.repository=repository;this.passwords=passwords;this.secrets=secrets;this.clock=clock;
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
        this.recoveryEnabled=recoveryEnabled;
    }
    public static String username(String value){return value==null?"":value.strip().toLowerCase(Locale.ROOT);}
    public static Profile profile(StaffPrincipal p){return new Profile(p.id(),p.username(),p.name(),p.role(),p.passwordChangeRequired());}
    private AuthProblem invalid(){return new AuthProblem(401,"Staff sign-in details or verification code are incorrect.");}
    @Transactional(timeout=20)
    public StaffPrincipal login(String login,String password,String code){
        var stored=repository.findUsername(username(login),true).orElse(null);
        boolean matches=passwords.matches(password,stored==null?dummyHash:stored.passwordHash());
        if(stored==null||!stored.active()||!matches)throw invalid();
        long step=-1;
        if(stored.profile().role()==StaffRole.OWNER){
            byte[] secret=secrets.decrypt(stored.profile().id(),stored.encryptedSecret());
            try{step=Totp.match(secret,code,clock.instant().getEpochSecond(),stored.lastStep());}
            finally{Arrays.fill(secret,(byte)0);}
            if(step<0){String hash=RecoveryCodes.hash(code);if(!recoveryEnabled||hash==null||!repository.consumeRecovery(stored.profile().id(),hash))throw invalid();}
        }
        repository.recordLogin(stored,step);return stored.profile();
    }
    @Transactional(readOnly=true)
    public boolean current(StaffPrincipal principal){
        var stored=repository.findId(principal.id(),false).orElse(null);
        return stored!=null&&stored.active()&&stored.profile().version()==principal.version()
                &&stored.profile().role()==principal.role()&&stored.profile().passwordChangeRequired()==principal.passwordChangeRequired();
    }
    public StaffPrincipal require(StaffPrincipal principal,StaffRole... allowed){
        if(principal==null||!current(principal))throw new AuthProblem(401,"Please sign in again.");
        if(principal.passwordChangeRequired()||Arrays.stream(allowed).noneMatch(role->role==principal.role()))throw new AuthProblem(403,"Your staff role does not allow this action.");
        return principal;
    }
    @Transactional(timeout=20)
    public StaffPrincipal changePassword(StaffPrincipal principal,String current,String replacement){
        var stored=repository.findId(principal.id(),true).orElseThrow(this::invalid);
        if(!stored.active()||stored.profile().version()!=principal.version())throw invalid();
        if(!passwords.matches(current,stored.passwordHash()))throw invalid();
        validatePassword(replacement);
        if(passwords.matches(replacement,stored.passwordHash()))throw new AuthProblem(400,"Choose a different password.");
        repository.changePassword(principal.id(),passwords.encode(replacement));
        return repository.findId(principal.id(),false).orElseThrow().profile();
    }
    @Transactional(readOnly=true)
    public List<ManagedAccount> list(StaffPrincipal actor){
        require(actor,StaffRole.OWNER);
        return repository.accounts().stream().map(a->new ManagedAccount(profile(a.profile()),a.active(),a.profile().version())).toList();
    }
    @Transactional
    public ManagedAccount create(StaffPrincipal actor,String rawUsername,String rawName,StaffRole role,String temporaryPassword){
        require(actor,StaffRole.OWNER);
        String login=username(rawUsername),name=validName(rawName);
        if(!login.matches("[a-z0-9][a-z0-9._-]{2,59}"))throw new AuthProblem(400,"Use a staff username of 3–60 lowercase letters, digits, dots, underscores or hyphens.");
        if(role==null||role==StaffRole.OWNER)throw new AuthProblem(400,"Choose Sales, Inventory or Delivery. Owner enrollment requires the protected setup process.");
        validatePassword(temporaryPassword);UUID id=UUID.randomUUID();
        try{repository.create(actor.id(),id,login,name,role,passwords.encode(temporaryPassword));}
        catch(DuplicateKeyException e){throw new AuthProblem(409,"This staff username is already in use.");}
        var stored=repository.findId(id,false).orElseThrow();return new ManagedAccount(profile(stored.profile()),true,stored.profile().version());
    }
    @Transactional
    public ManagedAccount change(StaffPrincipal actor,UUID id,long revision,String rawName,StaffRole role,boolean active){
        require(actor,StaffRole.OWNER);
        var stored=repository.findId(id,true).orElseThrow(()->new AuthProblem(404,"Staff account not found."));
        if(stored.profile().role()==StaffRole.OWNER||role==StaffRole.OWNER)throw new AuthProblem(409,"Owner enrollment and recovery cannot be changed through staff role editing.");
        if(role==null||revision!=stored.profile().version())throw new AuthProblem(409,"This staff account changed. Reload it before editing.");
        repository.changeAccess(actor.id(),id,validName(rawName),role,active);
        var changed=repository.findId(id,false).orElseThrow();return new ManagedAccount(profile(changed.profile()),changed.active(),changed.profile().version());
    }
    private String validName(String value){
        String name=value==null?"":value.strip();
        if(name.length()<2||name.length()>100||name.chars().anyMatch(Character::isISOControl))throw new AuthProblem(400,"Enter a staff name of 2–100 characters.");return name;
    }
    private void validatePassword(String value){if(value==null||value.length()<12||value.length()>128)throw new AuthProblem(400,"Use a password of 12–128 characters.");}
}
