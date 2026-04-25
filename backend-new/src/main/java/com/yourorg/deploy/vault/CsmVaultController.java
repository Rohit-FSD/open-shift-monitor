package com.yourorg.deploy.vault;

import com.yourorg.deploy.vault.dto.DbCredential;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for inspecting / refreshing CSM Vault secrets.
 *
 * Passwords are NEVER returned in the list endpoint - only metadata.
 * The single-fetch endpoint returns the password because the caller
 * is expected to wire it into a DataSource (internal use only).
 *
 * Gate this controller with a PROJECT_MANAGER role check once the
 * backend @PreAuthorize layer is in place.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/vault")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CsmVaultController {

    private final CsmVaultService vaultService;
    private final CsmVaultProperties props;

    /** All configured secret names from application.yml (so the UI can render a picker). */
    @GetMapping("/configured")
    public ResponseEntity<Map<String, Object>> configured() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", props.isEnabled());
        out.put("baseUrl", props.getBaseUrl());
        out.put("role", props.getRole());
        out.put("names", props.getDbSecrets().keySet());
        out.put("paths", props.getDbSecrets());
        return ResponseEntity.ok(out);
    }

    /** List what secrets have been loaded so far (no passwords). */
    @GetMapping("/secrets")
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> out = new LinkedHashMap<>();
        vaultService.getLoadedSecrets().forEach((name, cred) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", cred.getUsername());
            row.put("passwordLoaded", cred.getPassword() != null && !cred.getPassword().isEmpty());
            row.put("fetchedAt", cred.getFetchedAt());
            row.put("leaseSeconds", cred.getLeaseSeconds());
            out.put(name, row);
        });
        return ResponseEntity.ok(out);
    }

    /** Force a fresh fetch of a single logical secret. */
    @PostMapping("/secrets/{name}/refresh")
    public ResponseEntity<DbCredential> refresh(@PathVariable String name) {
        return ResponseEntity.ok(vaultService.getDbCredential(name));
    }

    /** Drops the cached Vault token so the next fetch re-authenticates. */
    @PostMapping("/token/invalidate")
    public ResponseEntity<Void> invalidateToken() {
        vaultService.invalidateToken();
        return ResponseEntity.noContent().build();
    }
}
