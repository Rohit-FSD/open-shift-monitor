package com.yourorg.deploy.vault.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CsmAuthRequest {
    private String role;
    private String jwt;
}
