package africa.finishers.identity.api;

import africa.finishers.identity.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffAuthController {
    public record Login(@NotBlank @Size(max=60) String username,@NotNull @Size(max=128) String password,@Size(max=80) String code){
        @Override public String toString(){return "StaffLogin[redacted]";}
    }
    public record PasswordChange(@NotNull @Size(max=128) String currentPassword,@NotNull @Size(min=12,max=128) String newPassword){
        @Override public String toString(){return "StaffPasswordChange[redacted]";}
    }
    private final StaffAccounts staff;
    private final AuthenticationThrottle throttle;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    public StaffAuthController(StaffAccounts staff,AuthenticationThrottle throttle,SessionAuthenticationStrategy sessions,SecurityContextRepository contexts){this.staff=staff;this.throttle=throttle;this.sessions=sessions;this.contexts=contexts;}
    public static StaffPrincipal principal(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;
    }
    private void signIn(StaffPrincipal principal,HttpServletRequest request,HttpServletResponse response){
        List<SimpleGrantedAuthority> roles=principal.passwordChangeRequired()
                ?List.of(new SimpleGrantedAuthority("ROLE_STAFF_PASSWORD_CHANGE"))
                :List.of(new SimpleGrantedAuthority("ROLE_STAFF"),new SimpleGrantedAuthority("ROLE_"+principal.role().name()));
        var authentication=UsernamePasswordAuthenticationToken.authenticated(principal,null,roles);
        sessions.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
    }
    @PostMapping("/auth/login")
    public StaffAccounts.Profile login(@Valid @RequestBody Login login,HttpServletRequest request,HttpServletResponse response){
        throttle.check(AuthenticationThrottle.Operation.STAFF_LOGIN,request.getRemoteAddr());
        throttle.check(AuthenticationThrottle.Operation.STAFF_ACCOUNT_LOGIN,StaffAccounts.username(login.username()));
        var principal=staff.login(login.username(),login.password(),login.code());signIn(principal,request,response);return StaffAccounts.profile(principal);
    }
    @GetMapping("/me")
    public StaffAccounts.Profile me(Authentication auth){return StaffAccounts.profile(principal(auth));}
    @PostMapping("/auth/password")
    public StaffAccounts.Profile password(Authentication auth,@Valid @RequestBody PasswordChange body,HttpServletRequest request,HttpServletResponse response){
        throttle.check(AuthenticationThrottle.Operation.STAFF_PASSWORD_CHANGE,principal(auth).id().toString());
        var updated=staff.changePassword(principal(auth),body.currentPassword(),body.newPassword());signIn(updated,request,response);return StaffAccounts.profile(updated);
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication auth,HttpServletRequest request,HttpServletResponse response){principal(auth);new SecurityContextLogoutHandler().logout(request,response,auth);}
}
