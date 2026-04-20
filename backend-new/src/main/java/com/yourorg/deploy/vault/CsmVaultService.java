package com.yourorg.deploy.vault;

import com.yourorg.deploy.vault.dto.CsmAuthRequest;
import com.yourorg.deploy.vault.dto.CsmAuthResponse;
import com.yourorg.deploy.vault.dto.CsmSecretResponse;
import com.yourorg.deploy.vault.dto.DbCredential;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Authenticates to CSM Vault using a projected SA JWT and fetches DB credentials.
 *
 * Flow (mirrors the curl steps):
 *   1. Read JWT from /var/run/secrets/tokens/vault-token
 *   2. POST {base}/v1/CSM/auth/jwt/login  body={role, jwt}  -> auth.client_token
 *   3. GET  {base}{secretPath}            header x-vault-token: <client_token>
 *   4. Read data.username / data.password
 *
 * Vault token is cached in-memory until ~80% of lease_duration has elapsed,
 * then re-authenticated on next call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsmVaultService {

    private final CsmVaultProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String cachedVaultToken;
    private volatile Instant vaultTokenExpiresAt = Instant.EPOCH;

    /** logical name -> last fetched credential (so the admin UI can show what is loaded). */
    private final Map<String, DbCredential> lastFetched = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        if (!props.isEnabled()) {
            log.info("CSM Vault integration is disabled (csm-vault.enabled=false).");
            return;
        }
        log.info("CSM Vault enabled. baseUrl={}, role={}, jwtPath={}, secrets={}",
                props.getBaseUrl(), props.getRole(), props.getJwtPath(),
                props.getDbSecrets().keySet());
    }

    /**
     * Returns the DB credential for a logical name configured under csm-vault.db-secrets.
     * Caches last fetched copy but always re-reads from Vault (static-creds rotate).
     */
    public DbCredential getDbCredential(String logicalName) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("CSM Vault is disabled");
        }
        String secretPath = props.getDbSecrets().get(logicalName);
        if (secretPath == null) {
            throw new IllegalArgumentException("No vault path configured for '" + logicalName + "'");
        }

        String vaultToken = getVaultToken();
        CsmSecretResponse body = fetchSecret(secretPath, vaultToken);

        Map<String, Object> data = body.getData();
        if (data == null) {
            throw new IllegalStateException("Vault returned empty data for " + secretPath);
        }

        DbCredential cred = new DbCredential(
                String.valueOf(data.getOrDefault("username", "")),
                String.valueOf(data.getOrDefault("password", "")),
                Instant.now(),
                body.getLeaseDuration()
        );
        lastFetched.put(logicalName, cred);
        return cred;
    }

    public Map<String, DbCredential> getLoadedSecrets() {
        return Map.copyOf(lastFetched);
    }

    /** Forces a re-auth on the next call. Useful for the admin refresh button. */
    public void invalidateToken() {
        cachedVaultToken = null;
        vaultTokenExpiresAt = Instant.EPOCH;
    }

    // ---------- internals ----------

    private synchronized String getVaultToken() {
        if (cachedVaultToken != null && Instant.now().isBefore(vaultTokenExpiresAt)) {
            return cachedVaultToken;
        }
        String jwt = readJwt();
        CsmAuthRequest reqBody = new CsmAuthRequest(props.getRole(), jwt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // The curl example uses `authorization: Bearer ` (empty) - harmless; set to keep parity.
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer");

        String url = trimTrailingSlash(props.getBaseUrl()) + props.getLoginPath();
        log.info("CSM Vault login -> {} (role={})", url, props.getRole());

        ResponseEntity<CsmAuthResponse> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(reqBody, headers), CsmAuthResponse.class);

        if (resp.getBody() == null || resp.getBody().getAuth() == null
                || resp.getBody().getAuth().getClientToken() == null) {
            throw new IllegalStateException("CSM Vault login returned no client_token");
        }
        CsmAuthResponse.Auth auth = resp.getBody().getAuth();
        cachedVaultToken = auth.getClientToken();
        long lease = auth.getLeaseDuration() > 0 ? auth.getLeaseDuration() : 3600L;
        // Refresh slightly before expiry.
        vaultTokenExpiresAt = Instant.now().plusSeconds((long) (lease * 0.8));
        log.info("CSM Vault login OK. token cached for ~{}s", (long) (lease * 0.8));
        return cachedVaultToken;
    }

    private CsmSecretResponse fetchSecret(String secretPath, String vaultToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-vault-token", vaultToken);

        String url = trimTrailingSlash(props.getBaseUrl()) + secretPath;
        log.debug("CSM Vault GET {}", url);

        ResponseEntity<CsmSecretResponse> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), CsmSecretResponse.class);

        if (resp.getBody() == null) {
            throw new IllegalStateException("Vault returned empty body for " + secretPath);
        }
        return resp.getBody();
    }

    private String readJwt() {
        // Dev override (local run outside the pod).
        if (props.getDevJwt() != null && !props.getDevJwt().isBlank()) {
            return props.getDevJwt().trim();
        }
        try {
            return Files.readString(Path.of(props.getJwtPath())).trim();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JWT from " + props.getJwtPath()
                    + " - is the projected SA token volume mounted?", e);
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
