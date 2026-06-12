---
trigger: always_on
---

# Production Debug Protocol (Strict)

## Investigation-first
1. Never modify code before identifying the root cause.
2. Trace the issue end-to-end before suggesting changes.
3. Identify exact failing layer:
   - UI / interaction
   - local storage / Room DB
   - background service
   - sync queue
   - API request
   - backend processing
   - database persistence
4. Explain WHY the issue happens, not only WHAT fails.
5. Rank top 3 probable causes by likelihood.

## Approval gate
6. Present all findings (A-G) to the user before modifying any code.
7. Wait for the user to say "proceed", "ok", "go ahead", or equivalent before editing.
8. Do not assume silence or vague acknowledgements count as approval.

## Scope control
9. Prefer smallest possible fix.
10. Never rewrite unrelated files.
11. Never refactor while debugging unless explicitly requested.
12. Preserve all existing working features.
13. List all files that will be touched before editing.

## Evidence
14. Verify assumptions using:
    - existing logs
    - stack traces
    - actual code path
15. Do not guess missing behavior.
16. If logs are missing, add temporary logs before changing logic.
17. Show which function is failing and why.
18. Show exact event sequence that leads to failure.

## Safety
19. Do not remove working code unless root cause proves it is incorrect.
20. Do not change DB schema for a bug fix unless strictly required.
21. Do not change permissions, manifest, or service declarations unless directly relevant.
22. Do not introduce new libraries for debugging.
23. Keep backward compatibility with existing user data.

## Android-specific
24. Check OEM restrictions first (Xiaomi/Realme/Oppo/Samsung) before changing telephony logic.
25. Verify runtime permissions before assuming feature bug.
26. Verify app lifecycle state:
    - foreground
    - background
    - process death
27. Check release build separately from debug build.
28. Validate battery optimization / background restrictions.

## Sync-specific (important for your backend rollout)
29. For sync issues, trace:
    local event → payload → request → response → server write.
30. Identify if failure is:
    - trigger missing
    - request blocked
    - auth failed
    - server rejected
    - DB write failed
31. Always show payload sample before changing sync code.
32. Add retry-safe fix if network dependent.
33. Never duplicate sync records while fixing.

## Response format
Always answer in this order:

A. Root cause  
B. Affected layer  
C. Evidence  
D. Smallest fix  
E. Side effects  
F. Files to change  
G. Test steps



For state transition bugs:
- verify status change trigger
- verify UI state derives only from persisted source of truth
- verify sync happens only once per unique log_id
- detect duplicate event listeners before modifying code 