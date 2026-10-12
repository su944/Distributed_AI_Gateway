package com.sunny.gateway.gateway;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/chat/completions")
public class ChatCompletionController {
    private final GatewayService service;
    public ChatCompletionController(GatewayService service) { this.service = service; }
    @PostMapping
    public ChatCompletionResponse complete(@Valid @RequestBody ChatCompletionRequest request) {
        return service.complete(request);
    }
}
