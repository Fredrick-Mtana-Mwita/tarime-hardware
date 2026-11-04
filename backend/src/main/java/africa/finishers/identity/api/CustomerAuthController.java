package africa.finishers.identity.api;

import africa.finishers.identity.application.*;
import africa.finishers.identity.domain.PhoneNumber;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class CustomerAuthController {
    public record Registration(@NotBlank @Size(min=2,max=100) String name,
                               @NotBlank @Size(max=40) String phone,
                               @Email @Size(max=254) String email,
                               @NotNull @Size(min=12,max=128) String password){
        @Override public String toString(){return "Registration[redacted]";}
    }
    public record Login(@NotBlank @Size(max=40) String phone,@NotNull @Size(max=128) String password){
        @Override public String toString(){return "Login[redacted]";}
    }
    private final CustomerAccounts accounts;
    private final AuthenticationThrottle throttle;
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    public CustomerAuthController(CustomerAccounts accounts,AuthenticationThrottle throttle,AuthenticationManager manager,
                                  SecurityContextRepository contexts,SessionAuthenticationStrategy sessions){
        this.accounts=accounts;this.throttle=throttle;this.manager=manager;this.contexts=contexts;this.sessions=sessions;
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public CustomerProfile register(@Valid @RequestBody Registration body,HttpServletRequest request,HttpServletResponse response){
        throttle.check(AuthenticationThrottle.Operation.REGISTRATION,request.getRemoteAddr());
        var profile=accounts.register(body.name(),body.phone(),body.email(),body.password());
        authenticate(profile.phone(),body.password(),request,response);
        return profile;
    }
    @PostMapping("/login")
    public CustomerProfile login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response){
        throttle.check(AuthenticationThrottle.Operation.LOGIN,request.getRemoteAddr());
        String phone;
        try{phone=PhoneNumber.normalize(body.phone());}catch(IllegalArgumentException exception){throw new AuthProblem(401,"Phone number or password is incorrect");}
        authenticate(phone,body.password(),request,response);
        return accounts.profile(phone);
    }
    private void authenticate(String phone,String password,HttpServletRequest request,HttpServletResponse response){
        Authentication authentication;
        try{authentication=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(phone,password));}
        catch(AuthenticationException exception){throw new AuthProblem(401,"Phone number or password is incorrect");}
        sessions.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
    }
    @GetMapping("/me")
    public CustomerProfile me(Authentication authentication){return accounts.profile(authentication.getName());}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request,HttpServletResponse response,Authentication authentication){
        new SecurityContextLogoutHandler().logout(request,response,authentication);
    }
}
