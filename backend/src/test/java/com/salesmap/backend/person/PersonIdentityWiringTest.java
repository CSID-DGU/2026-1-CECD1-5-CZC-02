package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.ai.config.AiModuleProperties;
import com.salesmap.backend.minutes.client.FakeIdentityResolutionClient;
import com.salesmap.backend.minutes.client.IdentityResolutionClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonIdentityWiringTest {
    final ApplicationContextRunner context=new ApplicationContextRunner()
            .withBean(PersonService.class,() -> mock(PersonService.class))
            .withBean(PersonIdentityService.class,() -> mock(PersonIdentityService.class))
            .withBean(ObjectMapper.class,ObjectMapper::new)
            .withBean(AiModuleProperties.class,() -> {
                var properties=new AiModuleProperties(); properties.setBaseUrl("http://identity.test"); return properties;
            })
            .withUserConfiguration(PersonIdentityResolutionClient.class,FakeIdentityResolutionClient.class);

    @Test void httpModeSelectsRealClientWithoutModifyingOrDeletingBComponents() {
        context.withPropertyValues("ai.module.mode=http").run(app -> {
            assertThat(app).hasNotFailed();
            assertThat(app.getBean(IdentityResolutionClient.class)).isInstanceOf(PersonIdentityResolutionClient.class);
            assertThat(app.getBeansOfType(IdentityResolutionClient.class)).hasSize(2);
        });
    }

    @Test void defaultMockModeRetainsExistingOfflineBehavior() {
        context.run(app -> {
            assertThat(app).hasNotFailed();
            assertThat(app.getBean(IdentityResolutionClient.class)).isInstanceOf(FakeIdentityResolutionClient.class);
            assertThat(app).doesNotHaveBean(PersonIdentityResolutionClient.class);
        });
    }
}
