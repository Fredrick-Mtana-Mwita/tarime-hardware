package africa.finishers.identity.infrastructure;

import africa.finishers.identity.application.StaffRepository;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStaffRepository implements StaffRepository {
    private final JdbcTemplate jdbc;
    public JdbcStaffRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final RowMapper<Stored> STORED=(r,n)->new Stored(new StaffPrincipal(r.getObject("id",UUID.class),r.getString("username"),r.getString("name"),
            StaffRole.valueOf(r.getString("role")),r.getLong("session_version"),r.getBoolean("password_change_required")),r.getString("password_hash"),r.getBoolean("active"),r.getString("mfa_secret_encrypted"),r.getLong("mfa_last_step"));
    public Optional<Stored> findUsername(String username,boolean lock){return jdbc.query("SELECT * FROM app.staff_accounts WHERE username=?"+(lock?" FOR UPDATE":""),STORED,username).stream().findFirst();}
    public Optional<Stored> findId(UUID id,boolean lock){return jdbc.query("SELECT * FROM app.staff_accounts WHERE id=?"+(lock?" FOR UPDATE":""),STORED,id).stream().findFirst();}
    public void recordLogin(Stored staff,long step){
        if(step>=0)jdbc.update("UPDATE app.staff_accounts SET mfa_last_step=? WHERE id=?",step,staff.profile().id());
        audit(staff.profile().id(),"STAFF_SIGNED_IN",staff.profile().id());
    }
    public boolean consumeRecovery(UUID id,String hash){
        boolean consumed=jdbc.update("UPDATE app.staff_recovery_codes SET used_at=now() WHERE staff_id=? AND code_hash=? AND used_at IS NULL AND expires_at>now()",id,hash)==1;
        if(consumed)audit(id,"STAFF_RECOVERY_CODE_USED",id);return consumed;
    }
    public List<Account> accounts(){return jdbc.query("SELECT id,username,name,role,session_version,password_change_required,active FROM app.staff_accounts ORDER BY name,id",
            (r,n)->new Account(new StaffPrincipal(r.getObject("id",UUID.class),r.getString("username"),r.getString("name"),StaffRole.valueOf(r.getString("role")),r.getLong("session_version"),r.getBoolean("password_change_required")),r.getBoolean("active")));}
    public void create(UUID actor,UUID id,String username,String name,StaffRole role,String passwordHash){
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash) VALUES(?,?,?,?,?)",id,username,name,role.name(),passwordHash);audit(actor,"STAFF_CREATED",id);
    }
    public void changeAccess(UUID actor,UUID id,String name,StaffRole role,boolean active){
        jdbc.update("UPDATE app.staff_accounts SET name=?,role=?,active=?,session_version=session_version+1 WHERE id=?",name,role.name(),active,id);audit(actor,"STAFF_ACCESS_CHANGED",id);
    }
    public void changePassword(UUID id,String hash){
        jdbc.update("UPDATE app.staff_accounts SET password_hash=?,password_change_required=false,session_version=session_version+1 WHERE id=?",hash,id);audit(id,"STAFF_PASSWORD_CHANGED",id);
    }
    public void audit(UUID actor,String event,UUID target){
        jdbc.update("INSERT INTO app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) VALUES(?,now(),?,'STAFF',?,'STAFF_ACCOUNT',?)",UUID.randomUUID(),actor,event,target.toString());
    }
}
