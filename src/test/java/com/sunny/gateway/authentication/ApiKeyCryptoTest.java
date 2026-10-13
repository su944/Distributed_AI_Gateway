package com.sunny.gateway.authentication;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiKeyCryptoTest {
    private final ApiKeyCrypto crypto = new ApiKeyCrypto("test-pepper-at-least-32-characters-long");
    @Test void randomKeysAreDistinctAndDigestCannotBeUsedAsBearer() {
        var first = crypto.generate(); var second = crypto.generate();
        assertThat(first.raw()).matches("dag_[a-f0-9]{32}_[A-Za-z0-9_-]{43}").isNotEqualTo(second.raw());
        assertThat(crypto.parse(first.raw()).publicId()).isEqualTo(first.publicId());
        assertThat(crypto.matches(first.raw(), first.digest())).isTrue();
        assertThat(crypto.matches(second.raw(), first.digest())).isFalse();
        assertThat(first.toString()).doesNotContain(first.raw(), first.digest());
        assertThat(first.digest()).hasSize(64).doesNotContain(first.raw());
    }
    @Test void pepperChangesDigestAndMalformedKeysAreRejected() {
        var key = crypto.generate();
        assertThat(new ApiKeyCrypto("different-pepper-at-least-32-characters").matches(key.raw(), key.digest())).isFalse();
        for (String invalid : new String[] {"", "bad", "dag_short_secret", key.digest()}) {
            assertThatThrownBy(() -> crypto.parse(invalid)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        }
        assertThatThrownBy(() -> new ApiKeyCrypto("")).isInstanceOf(IllegalArgumentException.class);
    }
}
