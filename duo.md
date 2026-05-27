You are a senior Java backend engineer working on a 
Spring Boot corporate banking application.

Generate a spec.md for AOP Centralized Logging feature.

Follow EXACTLY this structure — nothing more, nothing less:

---

## REQUIRED STRUCTURE

### 1. Feature Specification Header
- Feature name: AOP Centralized Logging
- Feature Branch: feature/aop-centralized-logging
- Status: In Progress
- Input: POC analysis findings

### 2. Problem Statement
- No centralized logging exists today
- Log statements are scattered across REST, BEM, CloudIT
- PII data is at risk of appearing in logs
- No consistent correlation ID tracking across layers

### 3. Actors Table
| Actor | Role | How They Interact |
- bcp-css-service → caller across all layers
- AOP Logging Aspect → intercepts all method calls
- Kibana/ELK → consumes structured log output
- Spring Cloud Config → controls feature flags at runtime
- Upstream consumers → indirectly affected, 
  should see no change in behaviour

### 4. User Scenarios & Journeys

Write EXACTLY these 4 stories:

#### User Story 1 — Feature Flag Configuration (P1)
Narrative: 
Logging behaviour must be fully controllable via 
config flags without redeployment.

Acceptance Scenarios (Given/When/Then):
1. Given logging.aop.enabled=false, 
   when any method is called, 
   then no AOP logging occurs at any layer

2. Given logging.aop.enabled=true, 
   when any method is called, 
   then entry/exit logged with method name, 
   correlationId, execution time at INFO level

3. Given logging.level.request=true, 
   when a request comes in, 
   then full request payload is logged at INFO level

4. Given logging.level.request=false, 
   when a request comes in, 
   then only method signature is logged, 
   no payload

5. Given logging.level.response=true, 
   when response returns, 
   then full response payload logged at INFO level

6. Given logging.level.response=false, 
   when response returns, 
   then only execution time logged, no payload

7. Given logging.pii.mask.debug-mode=true AND 
   logging.level.request=true, 
   when payload contains PII, 
   then PII fields are UNMASKED (debug only)

8. Given logging.pii.mask.debug-mode=false, 
   when payload contains PII, 
   then PII fields are always masked 
   regardless of request/response flags

#### User Story 2 — Entry/Exit Logging (P1)
Narrative:
AOP aspect must intercept all methods across 
REST, BEM and CloudIT layers and log entry 
and exit consistently.

Acceptance Scenarios:
1. Given AOP is enabled, 
   when a REST endpoint is called, 
   then entry log contains: method name, 
   correlationId, partyId, trackingId, 
   serviceId, environment tag

2. Given AOP is enabled, 
   when method completes, 
   then exit log contains: method name, 
   execution time in ms, response status

3. Given AOP is enabled on BEM layer, 
   when BEM service method is invoked, 
   then same entry/exit pattern applies 
   as REST layer

4. Given method throws an exception, 
   when AOP intercepts it, 
   then error log contains: error message, 
   errorKey, correlationId and stack trace 
   only if toggle is ON

#### User Story 3 — PII Masking (P1)
Narrative:
All sensitive customer data must be masked 
before any log statement is written. 
This is a compliance requirement.

Acceptance Scenarios:
1. Given a response contains accountNumber, 
   when logged, 
   then only last 4 characters are visible, 
   rest replaced with X

2. Given a payload contains partyId, 
   when logged at any level, 
   then partyId is masked as XXXXXXXX1234

3. Given identityToken is present in request, 
   when logged, 
   then token must NOT appear in any log output

4. Given correlationId is present, 
   when logged, 
   then correlationId is NOT masked 
   (safe to log in full)

5. Given debug-mode=false (production default), 
   when any payload is logged, 
   then masking is enforced before log is written

#### User Story 4 — Payment Journey Logging (P2)
Narrative:
Payment journey requires additional logging of 
transaction reference and encoded payload for 
audit and debugging purposes.

Acceptance Scenarios:
1. Given a payment request is processed, 
   when AOP intercepts it, 
   then transactionUniqueRefNo is logged

2. Given payment payload exists, 
   when logged, 
   then payload is base64 encoded before logging

3. Given BUILDER_END case is reached, 
   when logged, 
   then both exit log and entry log for 
   next step are written

---

### 5. Functional Requirements

FR-001: Provide logging.aop.enabled config 
        (default: true) to enable/disable globally

FR-002: Provide logging.level.request config 
        (default: false) to toggle request payload logging

FR-003: Provide logging.level.response config 
        (default: false) to toggle response payload logging

FR-004: Provide logging.pii.mask.debug-mode config 
        (default: false) to control PII masking behaviour

FR-005: All flags must be hot-reloadable via 
        Spring Cloud Config or @RefreshScope

FR-006: Invalid config values must fall back to 
        secure defaults 
        (masking ON, request/response logging OFF)

FR-007: AOP must intercept entry and exit for 
        REST, BEM and CloudIT layers

FR-008: Execution time must be logged for 
        every intercepted method

FR-009: PII fields must be masked before any 
        log statement is written — 
        mask all but last 4 characters with X

FR-010: CorrelationId, PartyId, TrackingId must 
        be extracted and included in every log line

FR-011: Payment journey must log 
        transactionUniqueRefNo and base64 payload

FR-012: Error logs must include errorKey and 
        optionally stack trace based on toggle

FR-013: All logs must be in strict JSON format 
        consumable by Kibana

---

### 6. PII & Sensitive Data

Confirm: YES — service handles customer PII

Fields that MUST NOT appear in logs:
- accountNumber
- cardholderName / cardholderIdentifier  
- partyId (must be masked)
- trackingId (must be masked)
- paymentReference
- identityToken (must never appear)

Fields SAFE to log:
- correlationId
- errorKey
- methodName
- executionTime
- serviceId
- environment

Masking Rule:
Show only last 4 characters, 
replace rest with X.
Example: XXXXXXXX6789

---

### 7. Success Criteria

SC-001: All configured layers log entry/exit 
        when logging.aop.enabled=true

SC-002: No PII field appears in any log output 
        when debug-mode=false

SC-003: Feature flags toggle without 
        application restart

SC-004: Execution time is present in every 
        exit log line

SC-005: Payment journey logs 
        transactionUniqueRefNo correctly

SC-006: All log output is valid JSON 
        parseable by Kibana

---

### 8. Assumptions

- Spring AOP / AspectJ is available in the project
- SLF4J + Logback is the existing logging framework
- Kibana/ELK is already set up and consuming logs
- correlationId is already available in MDC context
- Spring Cloud Config is available for hot-reload
- Existing logging must not be broken or removed

---

### 9. Error & Edge Case Scenarios

| Scenario | Expected Behaviour |
|---|---|
| logging.aop.enabled=false | No AOP logging, existing logs unaffected |
| PII field in payload | Masked before log written, never raw |
| Null response from downstream | Exit log written with null noted, no exception |
| Stack trace toggle=false | Error message logged, stack trace suppressed |
| correlationId missing | Log written with UNKNOWN placeholder |
| BEM layer throws exception | Error logged with errorKey and correlationId |
| Invalid config value supplied | Falls back to secure default silently |

---

## CONFIG EXAMPLES TO INCLUDE

# Production Default (safe)
logging:
  aop:
    enabled: true
  level:
    request: false
    response: false
  pii:
    mask:
      debug-mode: false

# Debug Incoming Requests Only
logging:
  aop:
    enabled: true
  level:
    request: true
    response: false
  pii:
    mask:
      debug-mode: false

# Full Debug (NON-PROD ONLY)
logging:
  aop:
    enabled: true
  level:
    request: true
    response: true
  pii:
    mask:
      debug-mode: true

---

## RULES FOR GENERATION
- Use "must" and "will" — never "should"
- Every acceptance scenario must be 
  independently testable
- Do not add any section not listed above
- Config defaults must always be 
  production-safe
- Keep language precise and technical

Generate the complete spec.md now.
