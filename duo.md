You are a senior Java backend engineer working on a Spring Boot 
corporate banking application.

I need you to follow a SPEC DRIVEN approach to implement 
AOP-based centralized logging. This means:
- First write the SPEC (what it should do)
- Then write the PLAN (how to do it)
- Then write TASKS (step by step execution)
- Then write a CHECKLIST (for verification)

## PROJECT CONTEXT
- Spring Boot application
- Layers: REST, BEM, CloudIT
- Existing Kibana/ELK logging setup
- Payment Journey module exists
- PII compliance is mandatory

## IMPLEMENTATION SCOPE (in order)
1. Feature flag setup (enable/disable logging via config)
2. AOP Aspect class (entry + exit logging for all layers)
3. PII masking utility 
   (mask all but last 4 chars, keep errorKey visible)
4. CorrelationId + PartyId + TrackingId extraction
5. REST layer integration + testing
6. Extend to BEM and CloudIT layers
7. Payment journey logging 
   (transactionUniqueRefNo + base64 payload)

## ADDITIONAL FEATURES TO INCLUDE
- Execution time per method
- Downstream API call logging
- Strict JSON log format for Kibana
- Audit trail for state-changing operations
- Log sanitization (prevent log injection)
- Environment tag in every log line (DEV/UAT/PROD)

## OUTPUT FORMAT
Generate 4 files:

### spec.md
- What each feature MUST do
- Acceptance criteria for each point
- Edge cases to handle

### plan.md
- Architecture decisions
- Class structure
- Dependencies needed
- Sequence of implementation

### tasks.md
- Granular step by step tasks
- Each task should be independently testable
- Estimated effort per task (S/M/L)

### checklist.md
- Verification points after each task
- Test scenarios
- PII masking validation cases
- Performance benchmarks

## CONSTRAINTS
- Do NOT break existing logging
- Feature flag must default to FALSE in PROD
- PII masking must work before any log is written
- All logs must be in JSON format
- Stack trace printing must be toggleable

## TECH STACK
- Java 11+
- Spring Boot 2.x
- Spring AOP / AspectJ
- SLF4J + Logback
- Jackson for JSON

Start with spec.md first and wait for my 
approval before moving to plan.md
