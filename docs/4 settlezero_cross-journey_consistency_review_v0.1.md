# SettleZero — Cross-Journey Consistency Review v0.1

**Status:** Completed  
**Scope:** Journeys 1–6 and all product decisions confirmed during review

## 1. Purpose

This document consolidates the decisions made across SettleZero's product journeys and checks them for contradictions, missing states, ambiguous behavior, and edge cases.

The purpose is to establish a consistent behavioral model before freezing:

> **SettleZero — Product Specification v0.1**

This review does not define implementation details. Technical architecture, database schema, synchronization protocol, API design, and hosting remain separate.

## 2. Core Product Principles

SettleZero follows these principles:

1. Local-first
2. Offline-capable
3. Server-authoritative for synchronized shared state
4. No user accounts
5. No subscriptions
6. Free to use
7. One currency per group
8. Manual expense entry
9. No expense categories
10. Simplify ON by default
11. Ledger and presentation are separate
12. Financial/activity history remains permanently available
13. No silent loss of local user data
14. Plain text + emoji only
15. No rich text
16. Lightweight architecture
17. Boring monolith

## 3. Identity Model

SettleZero has four distinct identities.

### 3.1 Group ID
Every group has an immutable Group ID. The displayed group name is not an identifier.

### 3.2 Member ID
Every member has an immutable Member ID. A member's display name can change, but the Member ID never changes.

### 3.3 Device ID
Every installation receives a unique Device ID. A Device ID identifies a technical installation, not a person. There is no account/login identity.

### 3.4 Transaction ID
Every expense and settlement receives a globally unique immutable ID. This allows transactions to be created offline and synchronized safely.

## 4. Member Lifecycle

A member can exist in these states:

- Active · Claimed
- Active · Unclaimed
- Archived

Active unclaimed members can participate in the financial ledger.

When a member voluntarily leaves a group, their state transitions to **Archived** (rather than Active · Unclaimed). This ensures they are not mistakenly added to future expenses while preserving all historical debt relationships and allowing them to settle debts or reclaim their identity if they rejoin later.

Archived members retain historical financial relationships but cannot participate in normal new activity.

## 5. Member Name Rules

Member names must be unique within a group, including Active, Unclaimed, Claimed, and Archived members.

Two offline devices may independently attempt to create the same name. The server is authoritative. The first valid server-side creation wins. The conflicting local operation is rejected and followed by a rename option.

## 6. Archived Members

Archiving a member does not erase history.

| Action | Allowed |
|---|---|
| Historical expenses | Yes |
| Historical settlements | Yes |
| Existing outstanding debt | Yes |
| Settlement of existing debt | Yes |
| New expense participant | No |
| New expense payer | No |
| Reclaim/rejoin later | Yes |

## 7. Unclaimed Members

Active · Unclaimed members can be:

- Expense participants
- Expense payers
- Settlement parties

This allows accurate ledger maintenance before everyone installs SettleZero.

## 8. Payer and Participant Rules

Every expense has exactly one payer.

Any active member (Claimed or Active · Unclaimed) may be the payer even when not participating in the expense (e.g. fronting a cost or buying a shared item for other members).

An archived member cannot be the payer or participant of a new expense.

Multiple payers per expense are out of scope for v0.1.

## 9. Expense Rules

Every expense requires:

- What was it?
- Amount
- Date
- Paid by
- At least one participant
- Split method

Optional:

- Description
- Note

Amount must be greater than zero. Any valid date is allowed, including future dates.

The created-at timestamp is immutable. Editing the expense date never changes created-at.

## 10. Split Rules

### Equally
Default. The system calculates each participant's share using deterministic rounding.

### Unequally
Users enter exact participant amounts. The sum must equal the expense amount.

Equal splitting distributes remainder minor units deterministically using immutable Member ID ordering, ensuring every device calculates the same result.

## 11. Currency

Each group has one currency value.

There is no automatic conversion.

The currency can be changed freely until the first financial transaction (expense or settlement) is recorded. Once the first transaction exists, the group currency is permanently locked to prevent corruption of historical ledger balances.

Amounts are stored internally as integer minor units (`Long`) using standard ISO-4217 currency scale (e.g., 2 decimals for INR/USD, 0 for JPY, 3 for KWD). Floating-point arithmetic is strictly prohibited in financial calculations.

## 12. Text Rules

All supported text fields accept plain text and emoji.

No rich text is supported.

Pasted rich text is converted to plain text.

Only Note supports newline characters.

## 13. Created By vs. Paid By

SettleZero always distinguishes who performed an action from who the financial event concerns.

Example:

> Paid by Rahul  
> Recorded by Sayantan

For settlements:

> Settlement recorded Rahul → Sayantan ₹300  
> Recorded by Sayantan

This distinction applies throughout the product and audit history.

## 14. Expense Editing

Users can edit expenses according to their permissions.

The created-at timestamp remains unchanged.

Every edit appears in history.

The current ledger reflects the edited expense immediately.

If an equal-split expense changes, equal shares are recalculated.

## 15. Expense Deletion

Expense deletion is a soft-delete operation.

The expense is removed from the active financial ledger but remains in permanent history.

Deleted expenses no longer contribute to:

- Current balances
- Simplified repayment relationships
- Total spent together

## 16. Settlement Rules

Settlements are individual financial events and retain the actual relationship:

> Rahul → Sayantan ₹300

Simplification never rewrites that historical relationship.

### Partial settlement
Allowed.

### Settlement exceeding debt
Rejected. If someone actually paid extra, the appropriate new financial relationship is recorded separately.

### Self-settlement
Not allowed.

### Settlement editing
Not allowed. An incorrect settlement is deleted and a new settlement is recorded.

### Settlement deletion
Allowed for the person who recorded it or an Admin. Deletion remains in history.

## 17. Settlement After Departure

A member who is Active · Unclaimed or Archived can still settle an existing outstanding debt.

## 18. Settlement After Group Archive

An archived group stops normal activity but preserves financial relationships.

| Action | Archived Group |
|---|---|
| New expense | No |
| New member | No |
| New invite | No |
| Historical viewing | Yes |
| Export | Yes |
| Existing debt settlement | Yes |
| Restore group | Yes |

## 19. Simplification

Simplify is **ON by default**.

It minimizes the number of repayments while preserving every member's net balance.

To guarantee identical results across all distributed devices, the greedy min-cash-flow algorithm applies a strict deterministic tie-breaker:
1. Debtors sorted ascending by net balance; ties broken by `Member ID` (lexicographical UUID).
2. Creditors sorted descending by net balance; ties broken by `Member ID` (lexicographical UUID).
3. Greedy matching across sorted sets.

Simplification is a derived presentation, not an authoritative financial transaction.

The setting is **group-level**. Changing it changes the group's primary repayment presentation and never changes the ledger.

A small icon can expose:

> **How is this calculated?**

## 20. Immediate Balance Updates

The UI updates balances immediately after a local change.

There is no manual refresh action.

The calculation is derived from the local ledger.

## 21. Group Total Spending

The group displays:

> **Total how much we spent together**

This is derived from active expenses.

Deleted expenses do not contribute.

Settlements do not affect spending totals.

## 22. Group Lifecycle

Groups can be:

- Active
- Archived

Archive is the v0.1 terminal lifecycle state.

Permanent deletion is deferred to a future explicit export/delete flow.

## 23. Group Restoration

An archived group can be restored.

Restoration preserves Group ID, members, financial history, and activity history.

A new invite link is generated. Old invite links remain invalid.

## 24. Invite Links

Invite links are long-lived until explicitly invalidated or replaced.

Generating a new invite link invalidates the old link.

Archived groups do not accept invites.

## 25. Invite Preview

Before joining, the invite flow shows:

- Group name
- Group description
- Currency
- Inviter

It does not show member count.

## 26. Joining Flow

The joining flow uses a single decision screen:

### Existing Member
Enter the existing member name. An Admin maps the joining person to the existing member identity.

### New Member
Create a new Active · Claimed member.

Duplicate names are prevented.

## 27. Existing Member Claim

Claiming an existing member requires administrator approval.

To prevent spoofing or unauthorized takeovers via public invite links:
- The claimant device generates and displays a short 4-digit verification code (Short Authentication String / SAS), e.g., `7294`.
- The Admin's approval screen displays the matching claim along with the verification code (`Rahul - Code: 7294`).
- The Admin verifies the code out-of-band (or in person) with the claimant before tapping Approve.

Approval must be atomic, idempotent, and server-authoritative. Once approved, the existing Member ID is associated with the new device.

## 28. Super Admin and Admin

The original creator becomes Super Admin.

Super Admin and Admin have the same normal administrative permissions.

Super Admin additionally controls ownership.

The original creator is not permanently privileged after ownership transfer.

There can be multiple Admins.

Admins can rename any member, including another Admin, but cannot change Super Admin ownership directly.

## 29. Super Admin Recovery

The Super Admin cannot voluntarily leave without first transferring ownership to an active member.

If the Super Admin device is lost or permanently inactive:
1. An emergency recovery request can be initiated after **30 days of Super Admin inactivity**.
2. To avoid distributed voting deadlocks in small groups, ownership deterministically cascades to the **earliest-promoted active Admin**.
3. If no other Admins exist, the ownership cascades to the **earliest-joined active claimed member**.

The recovery transition is atomic, idempotent, and recorded permanently in group history.

## 30. Local-First Architecture

SettleZero always reads the local database first.

```text
Open App
   ↓
Local database
   ↓
Immediate UI
   ↓
Background synchronization
```

## 31. Offline Capabilities

The following work offline:

- View groups
- View history
- Add members
- Add expenses
- Edit permitted expenses
- Delete permitted expenses
- Record settlements
- Calculate balances
- Calculate simplified repayments
- Local text search
- Export JSON

Joining through a server-backed invite requires connectivity.

## 32. Pending Sync

Every unacknowledged local operation has a pending state.

Examples:

> Edited · Pending sync

> Syncing…

Normal sync status remains subtle. Counts appear only when attention is required.

## 33. Server Authority and Conflicts

Every synchronized operation is validated against current server state.

If permissions change while offline, synchronization can reject the operation:

> **This change could not be synchronized because your permissions changed.**

If the group was archived while offline:

> **This change could not be synchronized because the group was archived.**

Rejected operations remain locally recoverable.

## 34. Never Silently Lose Local Data

Every local operation ultimately becomes one of:

- Synced
- Pending
- Rejected with explanation
- Conflict requiring resolution
- Exportable

No operation is silently discarded.

## 35. Synchronization Model

v0.1 uses:

> **Snapshot + incremental operations**

Technical synchronization records can eventually be compacted.

Financial and activity history remains permanently available.

All synchronization operations are idempotent.

## 36. Offline Group Creation

Groups can be created completely offline.

A working invite link becomes available after the group first synchronizes with the backend.

## 37. Backend Outage

If the backend is unavailable, SettleZero continues working locally.

Server-dependent actions such as joining through an invite require connectivity.

## 38. JSON Export

JSON export works offline.

It includes the current local state and appropriate synchronization status.

Pending operations can appear in exports.

Rejected operations also appear, clearly marked as rejected/not part of the shared ledger.

## 39. JSON Import

JSON import is always:

> **Read-only**

Import never writes into an existing editable group.

## 40. JSON Clone

A read-only imported snapshot can be cloned into a new editable group.

The clone receives a new Group ID and independent future history.

Original invite links, administrative authority, device authorization, and Group ID are never inherited.

Rejected operations from the original snapshot never become accepted ledger transactions in the clone.

## 41. JSON Merge

JSON merge into an existing group is out of scope for v0.1.

## 42. Reinstallation

Reinstalling SettleZero destroys the old local device state.

Recovery is possible through existing-member reclaim, server synchronization, or JSON recovery.

Reinstallation creates a new Device ID.

The Member ID remains the same after successful reclaim.

## 43. Notifications

Notifications are limited to things requiring action or explicitly requested communication.

Examples:

- Membership approval
- Claim request
- Administrative approval
- Explicit settlement notification

SettleZero does not notify users about every expense or balance change.

Sending a settlement notification does not itself create a settlement.

## 44. Search

Search is simple local text search.

It can cover:

- Member names
- Expense names
- Descriptions
- Notes
- Relevant history text

No advanced search infrastructure is required.

## 45. History

Financial and activity history remains permanently available.

History records:

- Expense creation
- Expense edits
- Expense deletion
- Settlement creation
- Settlement deletion
- Member changes
- Administrative changes
- Relevant synchronization outcomes

History identifies the actor performing the action.

## 46. Derived Financial Values

The authoritative ledger consists of:

> **Expenses + Settlements**

Derived values include:

- Member balances
- Simplified repayment relationships
- Total spending
- Outstanding debt
- Individual financial position

Derived values are recalculated from the ledger.

## 47. Deletion and Recalculation

Deleting an expense immediately updates:

- Balances
- Simplified repayments
- Total spending

Deleting a settlement immediately updates:

- Balances
- Simplified repayments

The deleted event remains in history.

## 48. Product Complexity Principle

v0.1 intentionally avoids:

- Expense categories
- Multi-currency conversion
- Multi-payer expenses
- Rich text
- Automatic backups
- JSON merging
- Permanent deletion
- Advanced search
- Complex notification systems
- Microservices
- Heavy infrastructure

Guiding principle:

> **Do the useful thing simply.**

## 49. Lightweight Backend Philosophy

The backend should be:

> **A boring monolith.**

Conceptually:

```text
Android App
     ↕
Simple API
     ↕
Compact Database
```

Priority:

- Low hosting cost
- Reliability
- Simple deployment
- Simple maintenance
- Correct synchronization
- Minimal infrastructure

## 50. Final Consistency Verdict

The product model is internally consistent after the review.

| Area | Final Decision |
|---|---|
| Member names | Unique across active + unclaimed + archived |
| Member departure | Voluntary leave transitions to Archived (prevents new charges) |
| Simplify | Group-level, ON by default, deterministic tie-breaking |
| Rejected offline operations | Exportable and clearly marked |
| Currency | Locked after first transaction; integer minor units (Long) |
| Invite links | Long-lived until replaced/revoked |
| Claim verification | Short Authentication String (SAS 4-digit code) |
| Offline duplicate names | Server authority → rename resolution |
| Settlement deletion | Creator or Admin |
| Settlement editing | Not allowed (Delete + re-record only) |
| Settlement over debt | Rejected (strictly capped at outstanding debt) |
| Non-participant payer | Allowed for any active member (Claimed or Unclaimed) |
| Super Admin recovery | Cascades to earliest active Admin after 30d inactivity |
| Unclaimed participant | Allowed |
| Unclaimed payer | Allowed |
| Unclaimed settlement party | Allowed |
| Multiple payers | Out of scope |
| Group archive | Stops new activity, preserves history |
| Permanent group deletion | Deferred |
| JSON import | Read-only |
| JSON clone | New independent group |
| Sync | Local-first + server-authoritative (Room + WorkManager) |
| Conflict handling | Explicit and recoverable |
| Technical sync history | Compacted |
| Financial history | Permanent |

## 51. Product Specification Readiness

**Cross-Journey Consistency Review: COMPLETE**

The next document should consolidate the entire product into one authoritative specification:

> **SettleZero — Product Specification v0.1**

After that:

> **SettleZero — Technical Architecture v0.1**

Technical Architecture will define the Android architecture, local database, synchronization protocol, API, backend, security model, hosting, and deployment strategy.

## 52. Final Product Philosophy

> **Split bills, not subscriptions. Clear the tab. Keep the app free.**

SettleZero should remain:

> **Local-first. Offline-capable. Server-synchronized. History-preserving. Free. Boring. Reliable.**
