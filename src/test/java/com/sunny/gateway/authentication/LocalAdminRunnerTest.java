package com.sunny.gateway.authentication;

import com.sunny.gateway.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAdminRunnerTest {
    @TempDir Path directory;
    @Test void noFileOverwriteAndNoCommandWhileServingHttp() throws Exception {
        var keys = mock(ApiKeyService.class); var tenants = mock(TenantService.class);
        var env = new MockEnvironment().withProperty("spring.main.web-application-type", "servlet");
        var runner = new LocalAdminRunner(tenants, keys, env, mock(ConfigurableApplicationContext.class));
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(keys, tenants);
        var output = directory.resolve("already-exists.key"); Files.writeString(output, "existing");
        env.withProperty("spring.main.web-application-type", "none").withProperty("gateway.admin.command", "key-issue")
                .withProperty("gateway.admin.output-file", output.toString());
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(output)).isEqualTo("existing");
        verifyNoInteractions(keys);
    }
    @Test void commandBeanIsUnavailableInProductionEvenIfLocalIsAlsoSelected() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", "local");
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("command", Map.of("gateway.admin.command", "tenant-list")));
            context.register(LocalAdminRunner.class); context.refresh();
            assertThat(context.getBeansOfType(LocalAdminRunner.class)).isEmpty();
        }
    }
}
