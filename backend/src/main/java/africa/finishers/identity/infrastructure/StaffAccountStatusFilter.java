package africa.finishers.identity.infrastructure;

import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class StaffAccountStatusFilter extends OncePerRequestFilter {
    private final StaffAccounts staff;
    public StaffAccountStatusFilter(StaffAccounts staff){this.staff=staff;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.getPrincipal() instanceof StaffPrincipal principal&&!staff.current(principal)){
            var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
            response.setStatus(401);response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Staff access changed. Please sign in again.\",\"status\":401}");return;
        }
        chain.doFilter(request,response);
    }
}
