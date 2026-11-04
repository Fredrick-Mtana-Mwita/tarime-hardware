package africa.finishers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinishersApplication {
    public static void main(String[] args) throws Exception {
        if(args.length==1&&args[0].equals("--setup-owner")){africa.finishers.devtools.OwnerSetup.main(new String[0]);return;}
        SpringApplication.run(FinishersApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
