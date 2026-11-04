package africa.finishers.identity.application;

import africa.finishers.identity.domain.PhoneNumber;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerAccounts {
    private final CustomerRepository repository;
    private final PasswordEncoder passwords;
    public CustomerAccounts(CustomerRepository repository,PasswordEncoder passwords){this.repository=repository;this.passwords=passwords;}

    @Transactional
    public CustomerProfile register(String name,String phone,String email,String password) {
        String normalized;
        try{normalized=PhoneNumber.normalize(phone);}catch(IllegalArgumentException exception){throw new AuthProblem(400,"Enter a valid phone number");}
        String displayName=name.strip();
        if(displayName.length()<2 || displayName.chars().anyMatch(Character::isISOControl))throw new AuthProblem(400,"Enter a valid name");
        String normalizedEmail=email==null || email.isBlank()?null:email.strip().toLowerCase(Locale.ROOT);
        UUID id=UUID.randomUUID();
        try{repository.insert(id,displayName,normalized,normalizedEmail,passwords.encode(password));}
        catch(DuplicateKeyException exception){throw new AuthProblem(409,"Unable to create an account with these details. If you already registered, sign in.");}
        return new CustomerProfile(id,displayName,normalized,normalizedEmail,false);
    }

    @Transactional(readOnly=true)
    public CustomerRepository.Credentials credentials(String phone){
        return repository.findByPhone(phone).orElse(null);
    }
    @Transactional(readOnly=true)
    public CustomerProfile profile(String phone){
        var stored=credentials(phone);
        if(stored==null || !stored.active())throw new AuthProblem(401,"Please sign in again");
        return stored.profile();
    }
    public boolean active(String phone){var stored=credentials(phone);return stored!=null && stored.active();}
}
