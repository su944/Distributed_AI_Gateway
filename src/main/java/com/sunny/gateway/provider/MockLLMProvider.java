package com.sunny.gateway.provider;

import com.sunny.gateway.gateway.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "gateway.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockLLMProvider implements LLMProvider {
    @Override public String id() { return "mock"; }
    @Override public boolean supportsUpstream(String model) { return "mock-chat".equals(model); }
    @Override public ChatCompletionResponse complete(ChatCompletionRequest request, String upstreamModel) {
        if (!supportsUpstream(upstreamModel)) throw new ProviderException(ProviderException.Reason.ERROR);
        return complete(request);
    }
    @Override public boolean supports(String model) { return "mock-chat".equals(model); }

    @Override public ChatCompletionResponse complete(ChatCompletionRequest request) {
        String lastUser = request.messages().stream().filter(m -> "user".equals(m.role()))
                .map(ChatMessage::content).reduce((first, last) -> last).orElse("No user message.");
        String output = "Mock response: " + lastUser.strip();
        String[] words = output.split("\\s+");
        int limit = request.maxTokens() == null ? 4096 : request.maxTokens();
        boolean truncated = words.length > limit;
        if (truncated) output = String.join(" ", Arrays.copyOf(words, limit));
        int promptTokens = request.messages().stream().mapToInt(m -> m.content().strip().split("\\s+").length).sum();
        int completionTokens = Math.min(words.length, limit);
        String id = "mock-" + UUID.nameUUIDFromBytes(request.toString().getBytes(StandardCharsets.UTF_8));
        return new ChatCompletionResponse(id, request.model(),
                List.of(new ChatCompletionResponse.Choice(0, new ChatMessage("assistant", output), truncated ? "length" : "stop")),
                new ChatCompletionResponse.Usage(promptTokens, completionTokens, promptTokens + completionTokens));
    }
}
