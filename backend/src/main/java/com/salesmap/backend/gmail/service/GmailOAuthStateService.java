package com.salesmap.backend.gmail.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class GmailOAuthStateService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long STATE_TTL_SECONDS = 600;

    private final String secret;

    public GmailOAuthStateService(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    public String createState(Long userId) {
        long issuedAt = Instant.now().getEpochSecond();
        String payload = userId + ":" + issuedAt;
        String signature = sign(payload);

        return base64UrlEncode(payload + ":" + signature);
    }

    public Long parseUserId(String state) {
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Gmail OAuth state 형식이 올바르지 않습니다.");
        }

        String payload = parts[0] + ":" + parts[1];
        String expectedSignature = sign(payload);

        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new IllegalArgumentException("Gmail OAuth state 검증에 실패했습니다.");
        }

        long issuedAt = Long.parseLong(parts[1]);
        if (issuedAt + STATE_TTL_SECONDS < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Gmail OAuth state가 만료되었습니다.");
        }

        return Long.valueOf(parts[0]);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            return base64UrlEncode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Gmail OAuth state 서명에 실패했습니다.", exception);
        }
    }

    private String base64UrlEncode(String value) {
        return base64UrlEncode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }

        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);

        if (leftBytes.length != rightBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }

        return result == 0;
    }
}
