package com.salesmap.backend.gmail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gmail.oauth")
public class GmailOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "openid email profile https://www.googleapis.com/auth/gmail.readonly";
    private int collectPageSize = 500;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public int getCollectPageSize() {
        return collectPageSize;
    }

    public void setCollectPageSize(int collectPageSize) {
        this.collectPageSize = collectPageSize;
    }
}
