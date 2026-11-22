package kz.studhub.config;

import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ProductionConfiguration.class);

    @Bean
    static BeanFactoryPostProcessor validateProductionSettings(Environment environment) {
        return factory -> {
            if (environment.matchesProfiles("dev | test")) {
                throw reject("Activate prod separately from dev and test");
            }
            String url = require(environment, "spring.datasource.url", "DB_URL");
            if (!url.startsWith("jdbc:postgresql://")) {
                throw reject("DB_URL must use jdbc:postgresql:// in prod");
            }
            require(environment, "spring.datasource.username", "DB_USERNAME");
            require(environment, "spring.datasource.password", "DB_PASSWORD");
            validateKey(require(environment, "studhub.security.key", "STUDHUB_2FA_KEY"));
            if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw reject("Hibernate schema validation is required in prod");
            }
            if (environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false)) {
                throw reject("Automatic Flyway baselining is disabled in prod");
            }
        };
    }

    private static String require(Environment environment, String property, String variable) {
        String value = environment.getProperty(property, "");
        if (value.isBlank()) throw reject("Set " + variable + " before starting prod");
        return value;
    }

    private static void validateKey(String key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException ex) {
            throw invalidKey();
        }
        int length = decoded.length;
        Arrays.fill(decoded, (byte) 0);
        if (length != 32) throw invalidKey();
    }

    private static IllegalStateException invalidKey() {
        return reject("STUDHUB_2FA_KEY must contain 32 bytes encoded in Base64");
    }

    private static IllegalStateException reject(String message) {
        LOG.error("Invalid production configuration. {}", message);
        return new IllegalStateException(message);
    }
}
