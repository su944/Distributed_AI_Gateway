package com.sunny.gateway.routing;

import com.sunny.gateway.gateway.GatewayException;
import com.sunny.gateway.provider.LLMProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.sunny.gateway.gateway.GatewayException.Reason.*;

@Component
public class ProviderSelector {
    private final List<LLMProvider> providers;
    public ProviderSelector(List<LLMProvider> providers) { this.providers = List.copyOf(providers); }
    public LLMProvider select(String model) {
        return providers.stream().filter(provider -> provider.supports(model)).findFirst()
                .orElseThrow(() -> new GatewayException("mock-chat".equals(model)
                        ? PROVIDER_UNAVAILABLE : UNSUPPORTED_MODEL));
    }
}
