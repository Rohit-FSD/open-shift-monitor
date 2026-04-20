package com.yourorg.deploy.vault.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DbCredential {
    private String username;
    private String password;
    private Instant fetchedAt;
    private long leaseSeconds;
}
