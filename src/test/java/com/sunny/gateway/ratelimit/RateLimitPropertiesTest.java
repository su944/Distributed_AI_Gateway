package com.sunny.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RateLimitPropertiesTest {
    @Test void defaultsAreSafeAndTenantOverridesAreIsolated() {
        var id = UUID.randomUUID();
        var override = new RateLimitProperties.Policy(10, 0.5, 2);
        var properties = new RateLimitProperties(null, null, Map.of(id, override));
        assertThat(properties.outagePolicy()).isEqualTo(RateLimitProperties.OutagePolicy.FAIL_CLOSED);
        assertThat(properties.policy(id)).isEqualTo(override);
        assertThat(properties.policy(UUID.randomUUID())).isEqualTo(new RateLimitProperties.Policy(60, 1, 1));
    }
    @Test void springBindsUuidOverridesAndValidatesInvalidConfiguration() {
        var id = UUID.randomUUID();
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(Config.class);
        runner.withPropertyValues("gateway.rate-limit.tenants." + id + ".capacity=7",
                        "gateway.rate-limit.tenants." + id + ".refill-rate=0.5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimitProperties.class).policy(id))
                            .isEqualTo(new RateLimitProperties.Policy(7, 0.5, 1));
                });
        runner.withPropertyValues("gateway.rate-limit.defaults.refill-rate=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("gateway.rate-limit.tenants.not-a-uuid.capacity=7")
                .run(context -> assertThat(context).hasFailed());
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RateLimitProperties.class)
    static class Config {}

    @Test void rejectsInvalidAndUnboundedPolicies() {
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.000001}) {
            assertThatThrownBy(() -> new RateLimitProperties.Policy(60, rate, 1)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RateLimitProperties.Policy(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Policy(2, 1, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Policy(2, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
