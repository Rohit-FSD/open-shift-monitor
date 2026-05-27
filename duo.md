You are a senior Java backend engineer working on a 
Spring Boot corporate banking application.

Generate a spec.md for AOP Centralized Logging feature.

Follow EXACTLY this structure — nothing more, 
nothing less:

---

## REQUIRED STRUCTURE

### 1. Feature Specification Header
- Feature name: AOP Centralized Logging
- Feature Branch: feature/aop-centralized-logging
- Status: In Progress
- Input: POC analysis findings

### 2. Problem Statement
- No centralized entry/exit logging exists today
- Log statements are scattered across 
  REST, BEM, CloudIT layers
- PII data is at risk of appearing in logs
- No consistent correlation ID tracking 
  across layers
- IMPORTANT: Existing business logic log 
  statements are NOT part of this problem — 
  they are working correctly and must 
  remain untouched

### 3. Scope Boundary
State clearly:

IN SCOPE:
- AOP aspect for method entry logging
- AOP aspect for method exit logging  
- PII masking on entry/exit logs only
- CorrelationId/PartyId/TrackingId 
  extraction for entry/exit logs
- Payment journey entry/exit logging
- Error logging on exceptions

OUT OF SCOPE:
- Existing business logic log statements
- Existing Kibana filter log statements
- Any modification to current log format
- Toggling AOP on/off 
  (AOP is always enabled as infrastructure)

### 4. Actors Table
| Actor | Role | How They Interact |
- bcp-css-service → caller across all layers
- AOP Logging Aspect → permanently intercepts 
  all method entry and exit points
- Existing Business Loggers → unchanged, 
  continue to log business events as today
- Kibana/ELK → consumes both AOP logs and 
  existing business logs
- Spring Cloud Config → controls payload 
  and PII flags only (not AOP on/off)
- Upstream consumers → indirectly affected, 
  must see no change in behaviour

### 5. User Scenarios & Journeys

Write EXACTLY these 4 stories:

#### User Story 1 — Request/Response 
#### Payload Logging Control (P1)

Narrative:
AOP logging is always active as infrastructure.
However, whether full request and response 
payloads are logged is controlled by config flags.
This allows payload visibility in non-PROD 
without exposing data in PROD.

Acceptance Scenarios (Given/When/Then):

1. Given AOP is deployed (always active),
   when any method in REST/BEM/CloudIT 
   is called,
   then entry and exit logs are ALWAYS written
   regardless of any config flag

2. Given logging.level.request=true,
   when a request comes in,
   then full request payload is logged 
   at INFO level along with method signature

3. Given logging.level.request=false,
   when a request comes in,
   then only method signature and correlationId 
   are logged — no payload

4. Given logging.level.response=true,
   when response returns,
   then full response payload logged 
   at INFO level with execution time

5. Given logging.level.response=false,
   when response returns,
   then only execution time is logged — 
   no payload

6. Given existing business logic log 
   statements are present in the class,
   when AOP intercepts the method,
   then existing log statements are 
   NOT affected or duplicated —
   AOP only adds entry and exit logs

#### User Story 2 — Entry/Exit Logging 
#### Across All Layers (P1)

Narrative:
AOP aspect permanently intercepts all methods 
across REST, BEM and CloudIT layers and logs 
entry and exit consistently.
This is infrastructure behaviour — 
it cannot be switched off.

Acceptance Scenarios:

1. Given AOP is deployed,
   when a REST endpoint is called,
   then entry log contains: method name,
   correlationId, partyId, trackingId,
   serviceId, environment tag

2. Given AOP is deployed,
   when method completes successfully,
   then exit log contains: method name,
   execution time in ms, response status

3. Given AOP is deployed on BEM layer,
   when BEM service method is invoked,
   then same entry/exit pattern applies 
   as REST layer

4. Given AOP is deployed on CloudIT layer,
   when CloudIT method is invoked,
   then same entry/exit pattern applies

5. Given a method throws an exception,
   when AOP intercepts it,
   then error log contains: error message,
   exception details, errorKey, correlationId

6. Given existing business logs exist 
   in the same method,
   when AOP logs entry and exit,
   then existing business logs appear 
   between AOP entry and exit logs —
   order is: AOP entry → business logs 
   → AOP exit

#### User Story 3 — PII Masking (P1)

Narrative:
All sensitive customer data must be masked 
before any AOP log statement is written.
This is a compliance requirement.
Existing business logs handle their own 
masking separately — AOP masking applies 
only to AOP generated logs.

Acceptance Scenarios:

1. Given a response contains accountNumber,
   when AOP logs it,
   then only last 4 characters are visible,
   rest replaced with X

2. Given a payload contains partyId,
   when AOP logs at any level,
   then partyId is masked as XXXXXXXX1234

3. Given identityToken is present in request,
   when AOP logs entry,
   then token must NOT appear in any 
   AOP log output

4. Given correlationId is present,
   when AOP logs entry or exit,
   then correlationId is NOT masked
   (safe to log in full)

5. Given logging.pii.mask.debug-mode=false 
   (production default),
   when any payload is logged by AOP,
   then masking is enforced before 
   log is written

6. Given logging.pii.mask.debug-mode=true,
   when payload contains PII,
   then PII fields are unmasked in log
   (NON-PROD debug use only)

7. Given existing business log statements
   are present in the class,
   when AOP masking runs,
   then AOP masking does NOT affect or 
   modify existing business log output

#### User Story 4 — Payment Journey 
#### Logging (P2)

Narrative:
Payment journey requires additional AOP logging 
of transaction reference and encoded payload 
for audit and debugging purposes.
Existing payment business logs remain intact.

Acceptance Scenarios:

1. Given a payment request is processed,
   when AOP intercepts it,
   then transactionUniqueRefNo is logged 
   at entry point

2. Given payment payload exists,
   when AOP logs it,
   then payload is base64 encoded 
   before logging

3. Given BUILDER_END case is reached,
   when AOP logs it,
   then both exit log of current step and 
   entry log of next step are written

4. Given existing payment business logs 
   are present,
   when AOP logs payment entry/exit,
   then existing payment logs are 
   NOT modified or removed

---

### 6. Functional Requirements

FR-001: AOP logging is always active — 
        there is no enable/disable flag 
        for AOP itself.
        It is permanent infrastructure.

FR-002: Provide logging.level.request config
        (default: false) to toggle request 
        payload logging only

FR-003: Provide logging.level.response config
        (default: false) to toggle response 
        payload logging only

FR-004: Provide logging.pii.mask.debug-mode 
        config (default: false) to control 
        PII masking behaviour

FR-005: All payload and PII flags must be 
        hot-reloadable via Spring Cloud Config 
        or @RefreshScope

FR-006: Invalid config values must fall back 
        to secure defaults
        (masking ON, payload logging OFF)

FR-007: AOP must intercept entry and exit for
        REST, BEM and CloudIT layers always

FR-008: Execution time must be logged for 
        every intercepted method exit

FR-009: PII fields must be masked before any 
        AOP log statement is written —
        mask all but last 4 characters with X

FR-010: CorrelationId, PartyId, TrackingId 
        must be extracted and included in 
        every AOP log line

FR-011: Payment journey must log 
        transactionUniqueRefNo and 
        base64 encoded payload

FR-012: Error logs must include error message,
        exception details and errorKey

FR-013: All AOP logs must be in strict JSON 
        format consumable by Kibana

FR-014: Existing business logic log statements
        must remain completely unchanged —
        AOP must NOT modify, remove or 
        duplicate any existing log statement

FR-015: Existing Kibana filter log statements
        must remain intact and continue to 
        function as before AOP introduction

---

### 7. PII & Sensitive Data

Confirm: YES — service handles customer PII

Fields that MUST NOT appear in AOP logs:
- accountNumber
- cardholderName / cardholderIdentifier
- partyId (must be masked)
- trackingId (must be masked)
- paymentReference
- identityToken (must never appear)

Fields SAFE to log in AOP:
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

Note: AOP masking applies to AOP generated 
logs only. Existing business log masking 
is handled separately and remains unchanged.

---

### 8. Success Criteria

SC-001: AOP entry and exit logs are written 
        for every method call across all 
        configured layers — always, 
        without any toggle

SC-002: No PII field appears in any AOP 
        log output when debug-mode=false

SC-003: Payload flags toggle without 
        application restart

SC-004: Execution time is present in every 
        AOP exit log line

SC-005: Payment journey logs 
        transactionUniqueRefNo correctly

SC-006: All AOP log output is valid JSON 
        parseable by Kibana

SC-007: Existing business logic logs 
        continue to appear in Kibana 
        exactly as before AOP introduction —
        no missing, modified or duplicated 
        business log entries

SC-008: Existing Kibana filters continue 
        to work correctly after 
        AOP is introduced

---

### 9. Assumptions

- Spring AOP / AspectJ is available in project
- SLF4J + Logback is existing logging framework
- Kibana/ELK is already set up and consuming logs
- correlationId is already available in 
  MDC context
- Spring Cloud Config is available for hot-reload
- Existing business log statements are correct 
  and must not be touched
- Existing Kibana filters are working and 
  must remain working

---

### 10. Error & Edge Case Scenarios

| Scenario | Expected Behaviour |
|---|---|
| Method called (any env) | AOP entry/exit always logged — no toggle |
| PII field in payload | Masked before AOP log written, never raw |
| Null response from downstream | AOP exit log written with null noted |
| Error thrown in method | Error message + exception + errorKey logged |
| correlationId missing | AOP log written with UNKNOWN placeholder |
| Existing business log in same method | Business log unaffected, appears between AOP entry and exit |
| Kibana filter log present in class | Filter log untouched, continues to work |
| Invalid config value supplied | Falls back to secure default silently |

---

## CONFIG EXAMPLES TO INCLUDE

# Production Default (safe)
logging:
  level:
    request: false
    response: false
  pii:
    mask:
      debug-mode: false

# Debug Incoming Requests Only (non-PROD)
logging:
  level:
    request: true
    response: false
  pii:
    mask:
      debug-mode: false

# Full Debug (NON-PROD ONLY)
logging:
  level:
    request: true
    response: true
  pii:
    mask:
      debug-mode: true

NOTE: There is no logging.aop.enabled flag.
AOP is always active. Only payload visibility 
and PII masking are configurable.

---

## RULES FOR GENERATION
- AOP is ALWAYS ON — never add an 
  enable/disable flag for AOP itself
- Use "must" and "will" — never "should"
- Every acceptance scenario must be 
  independently testable
- Do not add any section not listed above
- Config defaults must always be 
  production-safe
- Existing logs must be called out as 
  untouched in every relevant section

Generate the complete spec.md now.
