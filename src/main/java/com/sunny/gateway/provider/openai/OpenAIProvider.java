package com.sunny.gateway.provider.openai;

import com.sunny.gateway.gateway.ChatCompletionRequest;
import com.sunny.gateway.gateway.ChatCompletionResponse;
import com.sunny.gateway.provider.LLMProvider;
import com.sunny.gateway.provider.ProviderException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.concurrent.TimeoutException;
import static com.sunny.gateway.provider.ProviderException.Reason.*;

public class OpenAIProvider implements LLMProvider {
    private final WebClient client;
    private final OpenAIProperties properties;
    private final String apiKey;
    private final OpenAIMapper mapper;

    OpenAIProvider(WebClient client, OpenAIProperties properties, String apiKey, OpenAIMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    @Override public boolean supports(String model) { return properties.models().containsKey(model); }

    @Override public ChatCompletionResponse complete(ChatCompletionRequest request) {
        if (apiKey == null || apiKey.isBlank()) throw new ProviderException(MISSING_CREDENTIALS);
        String model = properties.models().get(request.model());
        if (model == null) throw new ProviderException(ERROR);
        try {
            return client.post().uri("/v1/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .bodyValue(mapper.toRequest(request, model))
                    .exchangeToMono(response -> {
                        if (!response.statusCode().is2xxSuccessful()) {
                            var reason = switch (response.statusCode().value()) {
                                case 401, 403 -> AUTHENTICATION;
                                case 429, 503 -> UNAVAILABLE;
                                default -> ERROR;
                            };
                            return response.releaseBody().then(Mono.<OpenAIResponse>error(new ProviderException(reason)));
                        }
                        return response.bodyToMono(OpenAIResponse.class)
                                .switchIfEmpty(Mono.error(new ProviderException(ERROR)));
                    })
                    .map(response -> mapper.toResponse(response, request.model()))
                    .timeout(properties.requestTimeout())
                    .onErrorMap(error -> !(error instanceof ProviderException),
                            error -> new ProviderException(isTimeout(error) ? TIMEOUT : ERROR))
                    .block();
        } catch (ProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Synchronous serialization/header errors must also be sanitized.
            throw new ProviderException(isTimeout(exception) ? TIMEOUT : ERROR);
        }
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException
                    || cause instanceof ConnectTimeoutException) return true;
        }
        return false;
    }
}
