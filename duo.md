You are a senior Java backend engineer working on a Spring Boot 
corporate banking application (bcp-css-service at Barclays).

I need you to generate a spec.md file for our AOP-based 
centralized logging feature, following EXACTLY this structure 
and quality standard:

## REQUIRED STRUCTURE (follow this order strictly)

### 1. Feature Specification Header
- Feature name: AOP Centralized Logging
- Feature Branch name
- Created date, Status, Input source

### 2. Problem Statement
- Why AOP logging is needed
- What risk exists without it
- What layers are affected (REST, BEM, CloudIT)

### 3. Clarifications (Q&A format)
Answer these as resolved questions:
- Q: What layers does AOP cover?
- Q: Does PII masking apply before or after logging?
- Q: Is feature flag hot-reloadable?
- Q: What happens if AOP is disabled?
- Q: What is default log level in PROD?

### 4. Actors Table
Generate a table with columns: Actor | Role | How They Interact
Actors to include:
- bcp-css-service (the calling service)
- AOP Logging Aspect (the interceptor)
- Kibana/ELK (log consumer)
- Downstream services (BEM, ODS, CloudIT)
- Upstream consumers (UI, other services)

### 5. User Scenarios & Journeys
Write 4 user stories with Priority (P1/P2):

Story 1 (P1): REST Layer Entry/Exit Logging
Story 2 (P1): PII Masking on All Log Output  
Story 3 (P1): Feature Flag Toggle Behavior
Story 4 (P2): Payment Journey Logging

For EACH story include:
- Narrative (what happens and why)
- Why this priority
- Independent Test (how to verify in isolation)
- Acceptance Scenarios (Given/When/Then format, 
  minimum 2 scenarios each)

### 6. Error & Edge Case Scenarios Table
Columns: Scenario | Expected Behaviour
Cover these cases:
- AOP disabled via flag
- PII field found in payload
- Null response from downstream
- Stack trace toggle off
- Correlation ID missing
- Payload exceeds size limit
- BEM layer throws exception

### 7. Requirements

#### Functional Requirements (FR-001 to FR-008)
- FR-001: Feature flag configuration (hot-reloadable)
- FR-002: Entry logging for all configured layers
- FR-003: Exit logging with execution time
- FR-004: PII masking (last 4 chars visible, rest X)
- FR-005: CorrelationId/PartyId/TrackingId extraction
- FR-006: Payment journey specific logging
- FR-007: JSON structured log format for Kibana
- FR-008: Error logging with toggleable stack trace

#### Data Requirements
- Config properties needed:
  logging.aop.enabled (default: true)
  logging.level.request (default: false)
  logging.level.response (default: false)
  logging.pii.mask.debug-mode (default: false)
- Fields that MUST be masked:
  accountNumber, cardholderName, partyId, 
  trackingId, paymentReference, identityToken
- Fields safe to log:
  correlationId, errorKey, methodName, 
  executionTime, serviceId, environment

#### External System Interactions Table
Columns: System | Direction | What Data | Purpose
Cover: BEM, REST endpoints, CloudIT, 
       Kibana/ELK, Spring Cloud Config

### 8. PII & Sensitive Data Section
- Confirm PII is present
- List every field that must NOT appear in logs
- State masking rule clearly

### 9. Success Criteria (SC-001 to SC-006)
- SC-001: All layers log entry/exit when flag=true
- SC-002: No PII appears in any log output
- SC-003: Feature flag toggle works without restart
- SC-004: Execution time logged for every method
- SC-005: Payment journey logs transactionRefNo
- SC-006: JSON format valid and parseable by Kibana

### 10. Assumptions
- Bullet list of what is assumed to be true
Include: existing Kibana setup, Spring AOP available,
         SLF4J+Logback in use, correlationId already 
         in MDC context

### 11. Open Questions
List 5 open questions in Q: / Answer: format
covering: log retention, Kibana index naming, 
          BEM layer pointcut scope, 
          payload size limits, 
          audit vs debug log separation

## TECH CONTEXT
- Java 11+
- Spring Boot 2.x
- Spring AOP / AspectJ
- SLF4J + Logback
- Jackson for JSON
- Spring Cloud Config for hot-reload
- Kibana/ELK for log consumption

## QUALITY RULES
- Write every FR and SC with enough detail that 
  a developer can code it without asking questions
- Every acceptance scenario must be testable 
  independently
- Do not use vague language like "should work" — 
  use "must", "will", "returns"
- Flag default must always be SAFE (masking ON, 
  payloads OFF in PROD)

Generate the complete spec.md now.

