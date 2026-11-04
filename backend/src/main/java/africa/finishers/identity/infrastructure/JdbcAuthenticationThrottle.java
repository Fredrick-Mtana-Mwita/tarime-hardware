package africa.finishers.identity.infrastructure;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.AuthenticationThrottle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class JdbcAuthenticationThrottle implements AuthenticationThrottle {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int loginLimit;
    private final int registrationLimit;
    public JdbcAuthenticationThrottle(JdbcTemplate jdbc,Clock clock,
            @Value("${fpa.auth.login-limit:30}") int loginLimit,@Value("${fpa.auth.registration-limit:10}") int registrationLimit){
        this.jdbc=jdbc;this.clock=clock;this.loginLimit=loginLimit;this.registrationLimit=registrationLimit;
    }
    public void check(Operation operation,String clientAddress){
        String key;
        try{key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(clientAddress.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
        Instant now=clock.instant();
        Timestamp window=Timestamp.from(Instant.ofEpochSecond(now.getEpochSecond()/900*900));
        jdbc.update("delete from app.auth_rate_limits where window_start < ?",Timestamp.from(now.minusSeconds(3600)));
        Integer attempts=jdbc.queryForObject("""
                insert into app.auth_rate_limits(operation,client_key,window_start,attempts) values (?,?,?,1)
                on conflict(operation,client_key,window_start) do update set attempts=app.auth_rate_limits.attempts+1 returning attempts
                """,Integer.class,operation.name(),key,window);
        int limit=switch(operation){case LOGIN,STAFF_LOGIN->loginLimit;case REGISTRATION->registrationLimit;case STAFF_ACCOUNT_LOGIN,STAFF_PASSWORD_CHANGE->10;};
        if(attempts!=null && attempts>limit)
            throw new AuthProblem(429,"Too many attempts. Please wait before trying again.");
    }
}
