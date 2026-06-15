# W1 Decision Log Follow-Up (2026-06-13)

> Scope: follow-up user decisions for gates that were deferred in `w1-decision-log.md`.
> This document supersedes the earlier deferred state only for the explicitly approved items below.

## User Decisions

| Gate | User decision | Resulting scope |
|---|---|---|
| Track C / CMS board | notice 도메인만 먼저 승인 | Only notice-domain work is approved first. Other CMS/board domains remain deferred. |
| RF-TASK-005 attachment rollback | 승인 | `EditNoticePage` attachment rollback/storage cleanup may proceed. |
| RF-TASK-006 Contact/env | 승인 | Public env helper usage may be applied to Supabase client and Contact/EmailJS env reads. |
| Backend/session browser regression | 승인 | Playwright/backend-authenticated regression may run when runtime credentials/backend conditions are available. |
| Legacy simulation/admin/login | 유지 | Legacy surfaces are kept, not archived or removed. |
| Remaining lint debt | 전체 수정 | The old lint carry-forward bucket should be closed; global lint must be clean. |
| External image domain tightening | 스킵 | `next.config` `remotePatterns` narrowing remains deferred. |
| Global error/middleware policy | 스킵 | No global `error.tsx` / middleware fallback policy change in this pass. |

## Implementation Notes

- Notice-domain approval does not authorize broad CMS/board data model, RLS, route guard, or storage behavior changes outside notice.
- Contact/env approval covers public env reads only. Secret env values remain server-only.
- Legacy keep means lint/type cleanup is allowed only when behavior and route access are preserved.
- Image rendering cleanup may use local component boundaries, but remote domain policy must not be tightened without a new decision.
