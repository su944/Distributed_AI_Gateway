package com.sunny.gateway.routing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ThreadLocalRandom;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfiguration {
    @Bean
    @ConditionalOnMissingBean(RandomSource.class)
    RandomSource routingRandomSource() { return bound -> ThreadLocalRandom.current().nextLong(bound); }
}
