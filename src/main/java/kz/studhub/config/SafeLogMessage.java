package kz.studhub.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;

public class SafeLogMessage extends ClassicConverter {
    private static final List<String> SECRET_PROPERTIES = List.of(
        "redactDbUrl", "redactDbPassword", "redactAdminPassword", "redactTwoFactorKey", "redactGeminiKey"
    );

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return "";
        for (String property : SECRET_PROPERTIES) {
            String secret = getContext().getProperty(property);
            if (secret != null && !secret.isBlank()) {
                message = message.replace(secret, "[REDACTED]");
            }
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
