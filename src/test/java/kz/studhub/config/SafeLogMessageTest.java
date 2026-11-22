package kz.studhub.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SafeLogMessageTest {
    @Test void redactsConfiguredCredentialsAndKeepsMessagesOnOneLine() {
        var context = new LoggerContext();
        try {
            context.putProperty("redactDbUrl", "jdbc:postgresql://db/test");
            context.putProperty("redactDbPassword", "db-review-canary");
            context.putProperty("redactAdminPassword", "admin-review-canary");
            context.putProperty("redactTwoFactorKey", "otp-review-canary");
            context.putProperty("redactGeminiKey", "gemini-review-canary");
            var converter = new SafeLogMessage();
            converter.setContext(context);
            var event = new LoggingEvent();
            event.setMessage("Error jdbc:postgresql://db/test\ndb-review-canary admin-review-canary otp-review-canary gemini-review-canary");
            assertThat(converter.convert(event)).isEqualTo("Error [REDACTED] [REDACTED] [REDACTED] [REDACTED] [REDACTED]");
        } finally {
            context.stop();
        }
    }
}
