package africa.finishers.identity.infrastructure;

import africa.finishers.identity.application.CustomerAccounts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class CustomerAccountStatusFilter extends OncePerRequestFilter {
    private final CustomerAccounts accounts;
    public CustomerAccountStatusFilter(CustomerAccounts accounts){this.accounts=accounts;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER"))
                && !accounts.active(authentication.getName())){
            var session=request.getSession(false);if(session!=null)session.invalidate();
            SecurityContextHolder.clearContext();response.setStatus(401);response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Please sign in again\",\"status\":401}");return;
        }
        chain.doFilter(request,response);
    }
}
