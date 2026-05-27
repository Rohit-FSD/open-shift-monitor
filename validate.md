You are a strict spec reviewer for a corporate 
banking application.

Review the AOP logging spec.md I will paste below.
Validate ONLY against these criteria:

STRUCTURE CHECK:
- [ ] Feature header present
- [ ] Problem statement clear
- [ ] Actors table complete
- [ ] Exactly 4 user stories present
- [ ] Every story has Given/When/Then scenarios
- [ ] FR-001 to FR-013 all present
- [ ] PII section lists all sensitive fields
- [ ] SC-001 to SC-006 present
- [ ] Assumptions listed
- [ ] Error scenarios table present
- [ ] Config examples (3 modes) present

QUALITY CHECK:
- [ ] All FRs use "must/will" not "should"
- [ ] Production defaults are all SAFE
- [ ] PII masking rule clearly stated
- [ ] identityToken called out as never loggable
- [ ] correlationId confirmed safe to log
- [ ] debug-mode has production safety warning

OUTPUT:
1. PASS / FAIL per checklist item
2. CRITICAL FIXES (blocks implementation)
3. MINOR FIXES (causes confusion)
4. FINAL VERDICT: APPROVED / NEEDS REVISION

Paste spec below:
[PASTE SPEC HERE]
