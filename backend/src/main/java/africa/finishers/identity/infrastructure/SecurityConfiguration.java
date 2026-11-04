package africa.finishers.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import africa.finishers.identity.application.CustomerAccounts;
import africa.finishers.identity.application.StaffAccounts;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import java.util.List;
import java.util.Map;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,CustomerAccounts accounts,StaffAccounts staff,
            SecurityContextRepository contexts,CsrfTokenRepository csrf) throws Exception {
        http.securityContext(context -> context.securityContextRepository(contexts));
        http.csrf(config -> config.csrfTokenRepository(csrf));
        http.addFilterAfter(new CustomerAccountStatusFilter(accounts),SecurityContextHolderFilter.class);
        http.addFilterAfter(new StaffAccountStatusFilter(staff),CustomerAccountStatusFilter.class);
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET,"/api/categories","/api/products","/api/products/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/system/info", "/api/security/csrf",
                        "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/staff/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/staff/me").hasAnyRole("STAFF","STAFF_PASSWORD_CHANGE")
                .requestMatchers(HttpMethod.POST,"/api/staff/auth/password","/api/staff/auth/logout").hasAnyRole("STAFF","STAFF_PASSWORD_CHANGE")
                .requestMatchers(HttpMethod.GET,"/api/staff/inventory","/api/staff/inventory/receipts").hasAnyRole("OWNER","INVENTORY")
                .requestMatchers(HttpMethod.POST,"/api/staff/inventory/receipts").hasAnyRole("OWNER","INVENTORY")
                .requestMatchers(HttpMethod.GET,"/api/staff/accounts").hasRole("OWNER")
                .requestMatchers(HttpMethod.POST,"/api/staff/accounts").hasRole("OWNER")
                .requestMatchers(HttpMethod.PUT,"/api/staff/accounts/*").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET,"/api/staff/orders","/api/staff/orders/*","/api/staff/notifications").hasAnyRole("OWNER","SALES")
                .requestMatchers(HttpMethod.POST,"/api/staff/orders/*/confirm").hasAnyRole("OWNER","SALES")
                  .requestMatchers(HttpMethod.POST,"/api/staff/notifications/*/read").hasAnyRole("OWNER","SALES")
                .requestMatchers(HttpMethod.POST,"/api/cart/quote").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/orders","/api/orders/*","/api/notifications").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST,"/api/orders","/api/notifications/*/read").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,"/api/auth/me").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST,"/api/auth/logout").hasRole("CUSTOMER")
                .anyRequest().denyAll());
        // Keep CSRF protection for registration, login, logout and all future writes.
        http.httpBasic(basic -> basic.disable());
        http.formLogin(form -> form.disable());
        http.logout(logout -> logout.disable());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Authentication required\",\"status\":401}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Access denied\",\"status\":403}");
                }));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2",Map.of("pbkdf2",Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
    @Bean
    UserDetailsService userDetailsService(CustomerAccounts accounts) {
        return phone -> {
            var stored=accounts.credentials(phone);
            if(stored==null)throw new UsernameNotFoundException("Invalid credentials");
            return User.withUsername(phone).password(stored.passwordHash()).roles("CUSTOMER").disabled(!stored.active()).build();
        };
    }
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users,PasswordEncoder passwords) {
        var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }
    @Bean
    SecurityContextRepository securityContextRepository() {
        var repository=new HttpSessionSecurityContextRepository();repository.setDisableUrlRewriting(true);return repository;
    }
    @Bean
    CsrfTokenRepository csrfTokenRepository(){return new HttpSessionCsrfTokenRepository();}
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
}
