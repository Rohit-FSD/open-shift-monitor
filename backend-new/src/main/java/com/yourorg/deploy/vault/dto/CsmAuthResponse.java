package com.yourorg.deploy.vault.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsmAuthResponse {

    private Auth auth;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Auth {

        @JsonProperty("client_token")
        private String clientToken;

        @JsonProperty("lease_duration")
        private long leaseDuration;

        private boolean renewable;
    }
}
