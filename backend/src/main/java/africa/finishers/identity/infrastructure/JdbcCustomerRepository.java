package africa.finishers.identity.infrastructure;

import africa.finishers.identity.application.CustomerProfile;
import africa.finishers.identity.application.CustomerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCustomerRepository implements CustomerRepository {
    private final JdbcTemplate jdbc;
    public JdbcCustomerRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Credentials> findByPhone(String phone){
        return jdbc.query("select * from app.customer_accounts where phone=?",(r,n)->new Credentials(
                new CustomerProfile(r.getObject("id",UUID.class),r.getString("name"),r.getString("phone"),r.getString("email"),r.getBoolean("contact_verified")),
                r.getString("password_hash"),r.getBoolean("active")),phone).stream().findFirst();
    }
    public void insert(UUID id,String name,String phone,String email,String passwordHash){
        jdbc.update("insert into app.customer_accounts(id,name,phone,email,password_hash) values (?,?,?,?,?)",id,name,phone,email,passwordHash);
        jdbc.update("insert into app.audit_events(event_id,occurred_at,actor_id,actor_type,event_type,target_type,target_reference) values (?,now(),?,'CUSTOMER','CUSTOMER_REGISTERED','CUSTOMER',?)",UUID.randomUUID(),id,id.toString());
    }
}
