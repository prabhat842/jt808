package com.example.jt808.platform.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jt808.auth")
public class AuthProperties {
    private String apiToken = "admin-token";
    private String terminalToken = "server-token";

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getTerminalToken() {
        return terminalToken;
    }

    public void setTerminalToken(String terminalToken) {
        this.terminalToken = terminalToken;
    }
}
