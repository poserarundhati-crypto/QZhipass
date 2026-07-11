package org.microsoft.qintelipass.repository;

import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.entity.UserAgentCallSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:agent-settings-repository;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserAgentCallSettingsRepositoryIntegrationTest {
    @Autowired
    private UserAgentCallSettingsRepository repository;

    @Test
    void savesAndUpdatesOneSettingsRowPerUser() {
        UserAgentCallSettings settings = new UserAgentCallSettings();
        settings.setUserId(901L);
        settings.setHotkey("!");
        settings.setMouseTriggerEnabled(true);
        repository.saveAndFlush(settings);

        UserAgentCallSettings saved = repository.findById(901L).orElseThrow();
        assertEquals("!", saved.getHotkey());
        assertNotNull(saved.getCreatedAt());

        saved.setHotkey("Alt+J");
        saved.setMouseTriggerEnabled(false);
        repository.saveAndFlush(saved);

        UserAgentCallSettings updated = repository.findById(901L).orElseThrow();
        assertEquals("Alt+J", updated.getHotkey());
        assertEquals(false, updated.isMouseTriggerEnabled());
        assertNotNull(updated.getUpdatedAt());
    }
}
