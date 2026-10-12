package com.sunny.gateway.gateway;

import com.sunny.gateway.provider.*;
import com.sunny.gateway.routing.ProviderSelector;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

class GatewayServiceTest {
    private final ChatCompletionRequest request = new ChatCompletionRequest("mock-chat",
            List.of(new ChatMessage("user", "Hello world")), null, 2, null);

    @Test void mockHonorsOutputLimitAndUsage() {
        var service = new GatewayService(new ProviderSelector(List.of(new MockLLMProvider())));
        var response = service.complete(request);
        assertThat(response.choices().getFirst().message().content()).isEqualTo("Mock response:");
        assertThat(response.choices().getFirst().finishReason()).isEqualTo("length");
        assertThat(response.usage()).isEqualTo(new ChatCompletionResponse.Usage(2, 2, 4));
    }

    @Test void noUserMessageStillProducesDeterministicOutput() {
        var provider = new MockLLMProvider();
        var response = provider.complete(new ChatCompletionRequest("mock-chat",
                List.of(new ChatMessage("assistant", "Prior reply")), null, null, null));
        assertThat(response.choices().getFirst().message().content()).isEqualTo("Mock response: No user message.");
    }

    @Test void providerFailureIsMappedWithoutLeakingDetails() throws Exception {
        verifyError(new LLMProvider() {
            public boolean supports(String model) { return true; }
            public ChatCompletionResponse complete(ChatCompletionRequest request) {
                throw new ProviderException("upstream-secret-payload");
            }
        }, 502, "provider_error");
    }

    @Test void unexpectedFailureIsSanitized() throws Exception {
        verifyError(new LLMProvider() {
            public boolean supports(String model) { return true; }
            public ChatCompletionResponse complete(ChatCompletionRequest request) {
                throw new IllegalStateException("upstream-secret-payload");
            }
        }, 500, "internal_error");
    }

    private void verifyError(LLMProvider provider, int status, String code) throws Exception {
        var service = new GatewayService(new ProviderSelector(List.of(provider)));
        var mvc = MockMvcBuilders.standaloneSetup(new ChatCompletionController(service))
                .setControllerAdvice(new GatewayExceptionHandler()).build();
        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content("""
                {"model":"mock-chat","messages":[{"role":"user","content":"Hello"}]}
                """))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("upstream-secret-payload"))));
    }
}
