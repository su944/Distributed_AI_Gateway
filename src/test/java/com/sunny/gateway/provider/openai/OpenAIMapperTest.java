package com.sunny.gateway.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.gateway.gateway.*;
import com.sunny.gateway.provider.ProviderException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OpenAIMapperTest {
    private final OpenAIMapper mapper = new OpenAIMapper();
    private final ObjectMapper json = new ObjectMapper();
    static final String SUCCESS = """
        {"id":"chatcmpl-test","model":"vendor-model-version","object":"chat.completion","created":123,
         "choices":[{"index":0,"message":{"role":"assistant","content":"Hello back","refusal":null},"finish_reason":"stop","logprobs":null}],
         "usage":{"prompt_tokens":8,"completion_tokens":2,"total_tokens":10,"completion_tokens_details":{"reasoning_tokens":0}}}
        """;

    @Test void requestMapsAliasMessagesAndOptionsToOpenAIFields() throws Exception {
        var messages = List.of(new ChatMessage("system", "Be brief"), new ChatMessage("user", "Hello"), new ChatMessage("assistant", "Earlier reply"));
        var request = new ChatCompletionRequest("openai-chat", messages, new BigDecimal("0.2"), 128, null);
        var body = json.valueToTree(mapper.toRequest(request, "vendor-model"));
        assertThat(body.path("model").asText()).isEqualTo("vendor-model");
        assertThat(body.path("messages")).isEqualTo(json.valueToTree(messages));
        assertThat(body.path("temperature").decimalValue()).isEqualByComparingTo("0.2");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(128);
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("n").asInt()).isEqualTo(1);
        assertThat(body.has("max_tokens")).isFalse();
    }

    @Test void absentOptionsAreOmitted() {
        var body = json.valueToTree(mapper.toRequest(new ChatCompletionRequest("openai-chat",
                List.of(new ChatMessage("user", "Hello")), null, null, null), "vendor-model"));
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("max_completion_tokens")).isFalse();
    }

    @Test void responseUsesAliasAndReportedUsageIgnoringVendorExtensions() throws Exception {
        var response = mapper.toResponse(json.readValue(SUCCESS, OpenAIResponse.class), "openai-chat");
        assertThat(response.id()).isEqualTo("chatcmpl-test");
        assertThat(response.model()).isEqualTo("openai-chat");
        assertThat(response.choices().getFirst().message()).isEqualTo(new ChatMessage("assistant", "Hello back"));
        assertThat(response.usage()).isEqualTo(new ChatCompletionResponse.Usage(8, 2, 10));
    }

    @Test void unfamiliarFinishReasonIsNormalized() throws Exception {
        var response = mapper.toResponse(json.readValue(SUCCESS.replace("\"stop\"", "\"future_reason\""), OpenAIResponse.class), "openai-chat");
        assertThat(response.choices().getFirst().finishReason()).isEqualTo("unknown");
    }

    @Test void missingUsageOrTextIsRejectedWithoutInventingTokens() throws Exception {
        for (String body : List.of(SUCCESS.replace("\"content\":\"Hello back\"", "\"content\":null"),
                SUCCESS.replace("\"total_tokens\":10", "\"total_tokens\":-1"), "{}")) {
            var wire = json.readValue(body, OpenAIResponse.class);
            assertThatThrownBy(() -> mapper.toResponse(wire, "openai-chat")).isInstanceOf(ProviderException.class);
        }
    }
}
