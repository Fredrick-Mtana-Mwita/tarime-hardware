package africa.finishers.devtools;

import africa.finishers.identity.domain.Totp;
import africa.finishers.identity.domain.RecoveryCodes;
import africa.finishers.identity.infrastructure.StaffSecretVault;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.flywaydb.core.Flyway;
import java.io.Console;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Local-only, interactive first-Owner enrollment. No HTTP bootstrap, default password or loggable secret arguments. */
public final class OwnerSetup {
    private OwnerSetup(){}
    public static void main(String[] args) throws Exception {
        Console console=System.console();
        if(console==null)throw new IllegalStateException("Owner setup requires a private interactive terminal. Run scripts/setup-owner.ps1 in IntelliJ's Terminal.");
        Path root=Path.of("").toAbsolutePath().normalize(),file=root.resolve("backend/.env");
        if(!Files.isRegularFile(root.resolve("scripts/setup-local-db.py"))||!Files.isRegularFile(file))throw new IllegalStateException("Run from the finishers-app folder after local database setup.");
        Properties settings=new Properties();try(var reader=Files.newBufferedReader(file)){settings.load(reader);}
        String url=settings.getProperty("FPA_DB_URL",""),user=settings.getProperty("FPA_DB_USER","");
        if(!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/finishers_dev")||!user.equals("finishers_dev"))throw new IllegalStateException("This enrollment tool accepts only the project-owned local development database.");
        Flyway.configure().dataSource(url,user,settings.getProperty("FPA_DB_PASSWORD"))
                .defaultSchema("app").schemas("app").locations("classpath:db/migration","classpath:db/dev")
                .cleanDisabled(true).load().migrate();
        try(Connection connection=DriverManager.getConnection(url,user,settings.getProperty("FPA_DB_PASSWORD"))){
            connection.setAutoCommit(false);
            try{
                try(var lock=connection.createStatement()){lock.execute("SELECT pg_advisory_xact_lock(735492001)");}
                try(var query=connection.createStatement();var rows=query.executeQuery("SELECT count(*) FROM app.staff_accounts")){
                    rows.next();if(rows.getLong(1)!=0)throw new IllegalStateException("Staff accounts already exist. Enrollment stopped; existing accounts and credentials were preserved.");
                }
                console.printf("Create the first named Owner. Secrets are shown only in this terminal; do not record or share this session.%n");
                String name=required(console.readLine("Owner full name: ")),username=required(console.readLine("Staff username (3–60 lowercase letters/digits/dot/underscore/hyphen): ")).toLowerCase(Locale.ROOT);
                if(name.length()<2||name.length()>100||name.chars().anyMatch(Character::isISOControl)||!username.matches("[a-z0-9][a-z0-9._-]{2,59}"))throw new IllegalArgumentException("Invalid Owner name or username.");
                char[] entered=console.readPassword("Create a private password (12–128 characters): "),again=console.readPassword("Repeat password: ");
                if(entered==null||again==null)throw new IllegalStateException("Setup cancelled.");
                String hash;
                try{
                    if(entered.length<12||entered.length>128||!Arrays.equals(entered,again))throw new IllegalArgumentException("Passwords do not match or have invalid length.");
                    var passwords=new DelegatingPasswordEncoder("pbkdf2",Map.of("pbkdf2",Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
                    hash=passwords.encode(new String(entered));
                }finally{Arrays.fill(entered,'\0');Arrays.fill(again,'\0');}
                // Approved setup policy: authenticator plus ten single-use offline codes.
                boolean recovery=true;
                console.printf("Owner recovery uses ten offline codes, each usable once with your password, expiring after 365 days.%n");
                UUID id=UUID.randomUUID();byte[] secret=Totp.newSecret();
                String key=System.getenv("FPA_STAFF_MFA_KEY");
                if(key==null||key.isBlank())key=settings.getProperty("FPA_STAFF_MFA_KEY","");
                if(key.isBlank()){
                    byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);key=Base64.getEncoder().encodeToString(bytes);Arrays.fill(bytes,(byte)0);
                    Files.writeString(file,"\nFPA_STAFF_MFA_KEY="+key+"\n",StandardOpenOption.APPEND);
                }
                if(Base64.getDecoder().decode(key).length!=32)throw new IllegalStateException("The configured MFA encryption key is invalid; it was not overwritten.");
                String encoded=new StaffSecretVault(key).encrypt(id,secret);
                console.printf("%nAdd an authenticator entry: Tarime Hardware / %s%nManual setup key: %s%nTime based, SHA1, 6 digits, 30-second interval.%n",username,Totp.base32(secret));
                char[] typed=console.readPassword("Enter the current six-digit authenticator code to finish enrollment: ");
                long step;
                try{step=Totp.match(secret,typed==null?"":new String(typed),Instant.now().getEpochSecond(),-1);}
                finally{Arrays.fill(secret,(byte)0);if(typed!=null)Arrays.fill(typed,'\0');}
                if(step<0)throw new IllegalArgumentException("Authenticator verification failed. No Owner account was created.");
                try(var statement=connection.prepareStatement("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted,mfa_last_step) VALUES(?,?,?,'OWNER',?,false,?,?)")){
                    statement.setObject(1,id);statement.setString(2,username);statement.setString(3,name);statement.setString(4,hash);statement.setString(5,encoded);statement.setLong(6,step);statement.executeUpdate();
                }
                List<String> codes=recovery?RecoveryCodes.generate():List.of();
                for(String code:codes)try(var statement=connection.prepareStatement("INSERT INTO app.staff_recovery_codes(staff_id,code_hash,expires_at) VALUES(?,?,now()+interval '365 days')")){
                    statement.setObject(1,id);statement.setString(2,RecoveryCodes.hash(code));statement.executeUpdate();
                }
                try(var statement=connection.prepareStatement("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF','OWNER_ENROLLED','STAFF_ACCOUNT',?)")){
                    statement.setObject(1,UUID.randomUUID());statement.setObject(2,id);statement.setString(3,id.toString());statement.executeUpdate();
                }
                if(recovery){
                    console.printf("%nSave these recovery codes in a private offline location. Each code works once, together with your password:%n");
                    codes.forEach(code->console.printf("%s%n",code));
                    if(!"SAVED".equals(console.readLine("Type SAVED after securely saving the codes; any other response cancels enrollment: ")))throw new IllegalStateException("Enrollment cancelled. No Owner was created; discard the displayed codes.");
                }
                Files.writeString(file,"\nFPA_STAFF_RECOVERY_ENABLED="+recovery+"\n",StandardOpenOption.APPEND);
                connection.commit();console.printf("%nOwner enrolled. Restart Full Stack - local, then open /#/staff/login. Wait for the next authenticator code before signing in.%n");
            }catch(Exception exception){connection.rollback();throw exception;}
        }
    }
    private static String required(String value){if(value==null)throw new IllegalStateException("Setup cancelled.");return value.strip();}
}
