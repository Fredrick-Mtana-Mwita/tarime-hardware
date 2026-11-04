package africa.finishers.platform.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {
    @GetMapping("/api/system/info")
    public SystemInfo info() {
        return new SystemInfo("Tarime Hardware", "foundation", true, false);
    }

    public record SystemInfo(String application, String stage,
                             boolean zeroBudget, boolean externalMessagingEnabled) {}
}
