package com.sunny.gateway.provider.openai;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAIProperties.class)
public class OpenAIConfiguration {
    @Bean
    @ConditionalOnProperty(name = "gateway.openai.enabled", havingValue = "true")
    WebClient openAIWebClient(OpenAIProperties properties) {
        // Reactor Netty can replay a request after a reset by default; explicitly disable it.
        var httpClient = HttpClient.create().disableRetry(true).followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout());
        return WebClient.builder().baseUrl("https://api.openai.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.openai.enabled", havingValue = "true")
    OpenAIProvider openAIProvider(@Qualifier("openAIWebClient") WebClient client,
            OpenAIProperties properties, @Value("${OPENAI_API_KEY:}") String apiKey) {
        return new OpenAIProvider(client, properties, apiKey, new OpenAIMapper());
    }
}
