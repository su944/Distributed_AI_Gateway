package com.sunny.gateway.authentication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class ApiKeyCrypto {
    private static final Pattern FORMAT = Pattern.compile("dag_([a-f0-9]{32})_([A-Za-z0-9_-]{43})");
    private final byte[] pepper;
    private final SecureRandom random = new SecureRandom();
    public ApiKeyCrypto(@Value("${GATEWAY_API_KEY_PEPPER:}") String pepper) {
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("GATEWAY_API_KEY_PEPPER must contain at least 32 bytes");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }
    Generated generate() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String publicId = UUID.randomUUID().toString().replace("-", "");
        String raw = "dag_" + publicId + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Generated(publicId, raw, digest(raw));
    }
    Parsed parse(String raw) {
        if (raw == null || raw.length() != 80) throw invalid();
        var matcher = FORMAT.matcher(raw);
        if (!matcher.matches()) throw invalid();
        return new Parsed(matcher.group(1));
    }
    boolean matches(String raw, String expected) {
        return MessageDigest.isEqual(HexFormat.of().parseHex(digest(raw)), HexFormat.of().parseHex(expected));
    }
    private String digest(String raw) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("API key digest unavailable"); }
    }
    private BadCredentialsException invalid() { return new BadCredentialsException("Invalid API key"); }
    record Parsed(String publicId) {}
    record Generated(String publicId, String raw, String digest) {
        @Override public String toString() { return "Generated[REDACTED]"; }
    }
}
