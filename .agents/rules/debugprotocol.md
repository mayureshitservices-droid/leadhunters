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

## Scope control
6. Prefer smallest possible fix.
7. Never rewrite unrelated files.
8. Never refactor while debugging unless explicitly requested.
9. Preserve all existing working features.
10. List all files that will be touched before editing.

## Evidence
11. Verify assumptions using:
   - existing logs
   - stack traces
   - actual code path
12. Do not guess missing behavior.
13. If logs are missing, add temporary logs before changing logic.
14. Show which function is failing and why.
15. Show exact event sequence that leads to failure.

## Safety
16. Do not remove working code unless root cause proves it is incorrect.
17. Do not change DB schema for a bug fix unless strictly required.
18. Do not change permissions, manifest, or service declarations unless directly relevant.
19. Do not introduce new libraries for debugging.
20. Keep backward compatibility with existing user data.

## Android-specific
21. Check OEM restrictions first (Xiaomi/Realme/Oppo/Samsung) before changing telephony logic.
22. Verify runtime permissions before assuming feature bug.
23. Verify app lifecycle state:
   - foreground
   - background
   - process death
24. Check release build separately from debug build.
25. Validate battery optimization / background restrictions.

## Sync-specific (important for your backend rollout)
26. For sync issues, trace:
   local event → payload → request → response → server write.
27. Identify if failure is:
   - trigger missing
   - request blocked
   - auth failed
   - server rejected
   - DB write failed
28. Always show payload sample before changing sync code.
29. Add retry-safe fix if network dependent.
30. Never duplicate sync records while fixing.

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