package com.sunny.gateway.provider.openai;

import com.sunny.gateway.gateway.*;
import com.sunny.gateway.provider.ProviderException;
import java.util.List;
import java.util.Set;

final class OpenAIMapper {
    OpenAIRequest toRequest(ChatCompletionRequest request, String providerModel) {
        var messages = request.messages().stream()
                .map(message -> new OpenAIRequest.Message(message.role(), message.content())).toList();
        return new OpenAIRequest(providerModel, messages, request.temperature(), request.maxTokens(), false, 1);
    }

    ChatCompletionResponse toResponse(OpenAIResponse response, String alias) {
        if (response == null || response.id() == null || response.id().isBlank()
                || response.model() == null || response.model().isBlank()
                || response.choices() == null || response.choices().size() != 1 || response.usage() == null) {
            throw malformed();
        }
        var choice = response.choices().getFirst();
        var usage = response.usage();
        if (choice == null || choice.index() == null || choice.index() != 0 || choice.message() == null
                || !"assistant".equals(choice.message().role()) || choice.message().content() == null
                || choice.finishReason() == null || !validCount(usage.promptTokens())
                || !validCount(usage.completionTokens()) || !validCount(usage.totalTokens())
                || (long) usage.promptTokens() + usage.completionTokens() != usage.totalTokens()) {
            throw malformed();
        }
        String finishReason = Set.of("stop", "length", "content_filter").contains(choice.finishReason())
                ? choice.finishReason() : "unknown";
        return new ChatCompletionResponse(response.id(), alias,
                List.of(new ChatCompletionResponse.Choice(0,
                        new ChatMessage("assistant", choice.message().content()), finishReason)),
                new ChatCompletionResponse.Usage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()));
    }
    private boolean validCount(Integer count) { return count != null && count >= 0; }
    private ProviderException malformed() { return new ProviderException(ProviderException.Reason.ERROR); }
}
