package kz.studhub.config;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointsTest {
    private static final String HEALTH = "/actuator/health";
    private static final String LIVENESS = HEALTH + "/liveness";
    private static final String READINESS = HEALTH + "/readiness";

    @Autowired
    private MockMvc mvc;
    @MockitoSpyBean
    private DataSource dataSource;

    @Test
    void monitoringCanReadHealthWithoutAuthenticationOrDiagnosticDetails() throws Exception {
        for (String path : new String[]{HEALTH, LIVENESS, READINESS}) {
            expectStatusOnly(mvc.perform(get(path)).andExpect(status().isOk()), "UP");
        }
    }

    @Test
    void databaseFailureOnlyStopsReadinessAndOverallHealth() throws Exception {
        doThrow(new SQLException("test database connection failure")).when(dataSource).getConnection();

        expectStatusOnly(mvc.perform(get(READINESS)).andExpect(status().isServiceUnavailable()), "DOWN");
        expectStatusOnly(mvc.perform(get(HEALTH)).andExpect(status().isServiceUnavailable()), "DOWN");
        expectStatusOnly(mvc.perform(get(LIVENESS)).andExpect(status().isOk()), "UP");
    }

    @Test
    void adminCannotAccessManagementDiscoverySettingsOrIndividualChecks() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/env", HEALTH + "/db"}) {
            mvc.perform(get(path).with(user("operator").roles("ADMIN")))
                .andExpect(status().isForbidden());
        }
    }

    private static void expectStatusOnly(ResultActions response, String healthStatus) throws Exception {
        response.andExpect(jsonPath("$.status").value(healthStatus))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());
    }
}
