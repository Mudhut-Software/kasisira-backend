# Kasisira MVP — Design Specification

**Date:** 2026-04-17
**Status:** Draft for review
**Owner:** Phillip Seryazi

---

## 1. Overview

Kasisira is a web platform for the Uganda / East Africa real-estate market that serves two user sides under one product:

- **Tenants** search for long-term rentals, request viewings, and — once moved in — pay rent and report faults through the app.
- **Owners** (landlords) list properties, manage tenancies, collect rent, and dispatch service providers. Owners can invite **Managers** (caretakers, assistants) into their workspace with scoped permissions.

The MVP is **rent-first**: only long-term rental is shipped. Short-stay / B&B is a deliberate Phase 2 vertical, with the domain model scoped so it can slot in without restructuring.

**Monetization:** owners pay a tiered monthly subscription (14-day free trial). Tenants pay nothing beyond rent. Rent passes through to the owner with zero platform commission in MVP.

**Delivery:** responsive React web app for all user surfaces. Native / KMP mobile app is a later phase.

---

## 2. Goals

1. Let an owner in Uganda list a rental property in under 10 minutes.
2. Let a tenant find that listing, request a viewing, become a tenant, and pay rent via MTN MoMo / Airtel Money / card / bank — all in-app.
3. Give owners a reliable, one-place record of rent status, tenancy status, and fault tickets.
4. Replace caretaker phone-tag with structured requests (viewings, faults) while keeping caretaker-SP coordination offline.
5. Build trust: tiered owner verification, public listing reports, admin moderation.
6. Keep architecture simple enough for a single developer to operate: modular monolith, single Postgres, responsive SPA.

## 3. Non-Goals (MVP)

**Phase 2 (next after MVP):**
- Short-stay / B&B vertical (booking, calendar, guest flows).
- Service providers as real users of the app. They remain private roster records per owner org.
- E-signed tenancy agreements.
- Map-based search UI (lat/lng is stored; map view comes later).
- PWA / push notifications.
- Native / KMP mobile app.

**Phase 3+:**
- Rent collection commission / transaction fee.
- Escrow and deposit holding.
- Payouts from Kasisira to owners (money flows owner-direct via aggregator subaccount).
- Cost approval workflow on fault tickets.
- Fault scheduling (tenant time-window selection, SP confirmation).
- SP marketplace (public SP signup, ratings, cross-org dispatch).
- Favorites / saved listings / search alerts.
- Reviews & ratings.
- Referral / affiliate system.
- Multi-language (English only in MVP).
- Multi-currency (UGX only).
- Standalone Agent role (agents use Manager role under an owner's org).
- Legal document storage / vault.
- Tax / URA-compliant receipting beyond generic SMS receipts.

**Scope-discipline rules:**
- If a feature isn't listed in sections 5–12, it is out of MVP.
- If adding a feature requires changes to the core domain model, it is a Phase 2 discussion, not an MVP ticket.
- **Exception:** compliance obligations, trust-breaking issues, and security / safety defects ship regardless of phase.

---

## 4. Key Decisions (locked during brainstorming)

| # | Decision |
|---|---|
| 1 | Market: Uganda / East Africa. Mobile money is a first-class payment method. |
| 2 | Verticals: long-term rental in MVP. Short-stay deferred to Phase 2. |
| 3 | Inventories: a unit is either rent or stay, never both (when stay ships). |
| 4 | Roles: Tenant, Owner, Admin are global; Manager is an org-scoped role granted via invite with per-permission toggles. Service Provider is a roster record, not a user. |
| 5 | Payments: in-app via Flutterwave (MTN MoMo, Airtel Money, card, bank transfer). Pesapal documented as swap-out. |
| 6 | Service providers: private roster per owner org. No marketplace. |
| 7 | Verification: tiered — "Unverified" badge by default; "Verified" after admin review of ID + proof of ownership. |
| 8 | Tenancy lifecycle: viewings and contracts happen offline. App stores a digital tenancy record (dates, rent, deposit) and powers rent + faults from there. |
| 9 | Fault tickets: simple state machine `PENDING` → `IN_PROGRESS` → `DONE`. Tenant reopen flips back to `PENDING`. No cost approval, no scheduling. |
| 10 | Discovery: list view with filters. Lat/lng stored for future map view. |
| 11 | Communication: **in-app chat** (tenant ↔ owner/manager), text-only, 5-second polling. Phone / WhatsApp remain available off-platform. |
| 12 | Monetization: tiered subscription for owners, 14-day free trial. Tenants pay nothing. Zero commission on rent. |
| 13 | Stack: Kotlin / Spring Boot backend, Postgres, React / TypeScript web frontend. KMP mobile is a later phase. |

---

## 5. System Architecture

### Backend — `kasisira-backend`

- **Kotlin + Spring Boot** modular monolith. Bounded contexts as modules: `auth`, `owner_org`, `listings`, `tenancy`, `payments`, `subscriptions`, `faults`, `service_providers`, `verification`, `chat`, `notifications`, `admin`.
- Modules expose Kotlin service interfaces; no HTTP between modules. A module's internal types stay internal.
- **Postgres** as the single operational database. One schema per module. Migrations via **Flyway**.
- REST API at `/api/v1`, JSON, OpenAPI spec auto-generated.
- **Auth:** JWT access token (15 min) + refresh token (30 days, stored hashed). Login by phone + SMS OTP **or** email + password.
- **File storage:** S3-compatible (AWS S3 or Backblaze B2) for listing images, ID documents, fault photos. Presigned upload URLs; backend never proxies bytes.
- **Background work:** Spring `@Scheduled` for cron-style jobs (rent charge generation, reminders, subscription renewal). A DB-backed **outbox table** for side effects (notifications, webhooks). Upgrade to Redis / SQS only when throughput demands it.

### Frontend — `kasisira-frontend`

- **React + TypeScript** single SPA.
- **Responsive** for phone / tablet / desktop (table stakes). No PWA in MVP.
- **Tanstack Query** for server state; **React Router** for navigation; **shadcn/ui + Tailwind CSS** for components.
- Three logical surfaces inside one app, gated by role:
  - **Public** — landing, search, listing detail, sign up / log in.
  - **Tenant** — my tenancy, pay rent, fault tickets, viewing requests, chat threads.
  - **Owner / Manager** — org dashboard, listings, tenancies, faults, team, subscription, verification.
- Admin console lives at `/admin`, gated by `ADMIN` role, bundled in the same SPA.

### Third-Party Integrations (MVP)

| Service | Purpose |
|---|---|
| **Flutterwave** | Payment aggregator: MTN MoMo, Airtel Money, card, bank transfer; tokenization for recurring subscription charges; subaccounts for owner-direct rent collection. |
| **Africa's Talking** | SMS (OTP, notifications). |
| **Postmark** or **Amazon SES** | Transactional email. |
| **Google Maps Geocoding API** | One call at listing creation to convert neighbourhood + address text into lat/lng. No map UI in MVP. |

### Environments & Ops

- Dev (local Docker Compose: app, Postgres, LocalStack-style S3), staging, production.
- CI on GitHub Actions: build, test, migrate-check, deploy.
- Observability (MVP): Spring Boot Actuator + application logs; a simple admin "system health" page; Grafana / Prometheus deferred.

### Why modular monolith, not microservices

One developer, one deploy, cheap cross-module DB transactions, and clear module boundaries keep the door open for later extraction. Microservices at MVP scale is self-harm.

---

## 6. Domain Model

All tables carry `id`, `created_at`, `updated_at`. Foreign keys indexed. Amounts in UGX as `BIGINT` (no decimals used in practice).

### 6.1 `auth`

- **`user`** — id, phone (E.164, unique), email, password_hash, full_name, phone_verified, email_verified.
- **`user_role`** — user_id, role (`TENANT`, `OWNER`, `ADMIN`). A user may hold multiple.
- **`refresh_token`** — user_id, token_hash, expires_at, revoked_at.
- **`otp_challenge`** — phone, code_hash, purpose, expires_at, consumed_at.

### 6.2 `owner_org`

- **`owner_org`** — id, creator_user_id, name, verified_at (nullable), created_at.
- **`membership`** — owner_org_id, user_id, role (`OWNER`, `MANAGER`), permissions JSON (`manage_listings`, `manage_tenancies`, `manage_faults`, `manage_team`, `manage_billing`), invited_by_user_id, accepted_at.
- **`invite`** — owner_org_id, email_or_phone, role, permissions JSON, token_hash, expires_at, accepted_at.

### 6.3 `listings`

- **`listing`** — id, owner_org_id, title, description, type (`RENT`; enum leaves room for `STAY`), status (`DRAFT`, `ACTIVE`, `RENTED`, `ARCHIVED`), monthly_rent_ugx, deposit_months, bedrooms, bathrooms, furnished (bool), amenities JSON, address_text, neighbourhood, district, lat, lng, created_by_user_id, published_at, last_refreshed_at.
- **`listing_image`** — listing_id, url, position, is_cover.

### 6.4 `tenancy`

- **`viewing_request`** — id, listing_id, tenant_user_id, preferred_times JSON, status (`PENDING`, `ACCEPTED`, `DECLINED`, `COMPLETED`), caretaker_note.
- **`tenancy`** — id, listing_id, tenant_user_id, owner_org_id, start_date, end_date (nullable), monthly_rent_ugx, deposit_ugx, rent_due_day (1–28), status (`ACTIVE`, `ENDED`), ended_at.

### 6.5 `payments`

- **`payment_intent`** — id, owner_org_id (for subscription) OR tenancy_id (for rent) — exactly one set, purpose (`RENT`, `SUBSCRIPTION`, `DEPOSIT`), amount_ugx, method (`MOMO_MTN`, `MOMO_AIRTEL`, `CARD`, `BANK`), provider_ref, status (`PENDING`, `SUCCEEDED`, `FAILED`), initiated_by_user_id, idempotency_key, raw_webhook JSON.
- **`rent_charge`** — tenancy_id, period_month (e.g. `2026-05`), due_date, amount_ugx, status (`DUE`, `PAID`, `OVERDUE`, `WAIVED`), paid_payment_intent_id.
- Unique constraint: `(tenancy_id, period_month)`.

### 6.6 `subscriptions`

- **`plan`** — id, name, price_ugx_monthly, max_listings, max_managers.
- **`subscription`** — owner_org_id, plan_id, status (`TRIALING`, `ACTIVE`, `PAST_DUE`, `CANCELED`), current_period_end, trial_ends_at, cancel_at_period_end, payment_method_token.

### 6.7 `faults`

- **`fault_ticket`** — id, tenancy_id, opened_by_user_id, category (`PLUMBING`, `ELECTRICAL`, `APPLIANCE`, `STRUCTURAL`, `OTHER`), title, description, status (`PENDING`, `IN_PROGRESS`, `DONE`), priority (`LOW`, `NORMAL`, `URGENT`), acknowledged_at, resolved_at.
- **`fault_photo`** — fault_ticket_id, url, uploaded_by_user_id.
- **`fault_assignment`** — fault_ticket_id, service_provider_id, assigned_by_user_id, assigned_at, completed_at, completion_note. (Assignment is a record only — SP is not notified in-app.)
- **`fault_event`** — fault_ticket_id, actor_user_id, event_type, payload JSON. Append-only audit.

### 6.8 `service_providers`

- **`service_provider`** — id, owner_org_id, name, phone, categories JSON, notes. Private to the owning org.

### 6.9 `verification`

- **`verification_submission`** — owner_org_id, id_document_url, ownership_document_url, status (`PENDING`, `APPROVED`, `REJECTED`), reviewed_by_admin_id, review_note, submitted_at, reviewed_at.

### 6.10 `chat`

- **`chat_thread`** — id, listing_id (nullable), tenancy_id (nullable) — exactly one set, participant_tenant_user_id, participant_owner_org_id, last_message_at.
- **`chat_message`** — thread_id, sender_user_id, body, read_at_by_counterparty.
- On tenancy creation, the thread for that (tenant, listing) re-scopes from `listing_id` to `tenancy_id` — same thread id, anchored to the ongoing relationship.

### 6.11 `notifications`

- **`notification`** — user_id, channel (`SMS`, `EMAIL`, `INAPP`), template, payload JSON, status (`PENDING`, `SENT`, `FAILED`), sent_at, attempts.
- **`outbox`** — event_type, payload JSON, processed_at, attempts — used by the worker that drains side-effects.

### 6.12 `admin`

- **`abuse_report`** — listing_id, reporter_user_id, reason, note, status (`OPEN`, `DISMISSED`, `ACTIONED`), reviewed_by_admin_id.
- **`admin_action_log`** — admin_id, entity_type, entity_id, action, before JSON, after JSON. Append-only.

### 6.13 Multi-tenancy invariant

Every table scoped to an owner org carries `owner_org_id` directly or transitively through exactly one parent. Authorization on any org-scoped endpoint always resolves to "is this caller a member of this `owner_org_id`, and does their membership grant the required permission?"

---

## 7. Roles & Permissions

### 7.1 Global roles (on `user_role`)

- **`TENANT`** — default on signup. Can browse, request viewings, hold tenancies, pay rent, open fault tickets, chat with their landlord.
- **`OWNER`** — granted when a user creates an `owner_org`. Unlocks the Owner / Manager surface for that org.
- **`ADMIN`** — Kasisira staff only. Granted manually by a super-admin.

A single user may hold multiple roles. Login lands on the surface they used last.

### 7.2 Org-scoped roles (on `membership`)

- **`OWNER`** — org creator; full permissions, cannot be removed or downgraded.
- **`MANAGER`** — invited via `invite`; permissions toggled individually:
  - `manage_listings` — create / edit / publish / archive listings.
  - `manage_tenancies` — accept/decline viewings, create tenancies, end tenancies.
  - `manage_faults` — view tickets, acknowledge, assign SP, mark done.
  - `manage_team` — invite / remove other managers (owner-only by default).
  - `manage_billing` — change plan, update payment method (owner-only by default).

### 7.3 Authorization rule

Every org-scoped endpoint enforces:
1. **Membership check:** caller has a `membership` row on the target `owner_org`.
2. **Permission check:** the membership's permissions JSON includes the required flag.

Both are enforced at the service-layer boundary, not only at the controller.

### 7.4 Tenant authorization

Tenants see only:
- Listings with `status = ACTIVE` (public).
- Their own `viewing_request` rows.
- Their own `tenancy` rows and associated `rent_charge`, `fault_ticket`.
- `chat_thread` rows where they are the `participant_tenant_user_id`.

### 7.5 Service Providers

SPs are **not users** of the app in MVP. They exist only as `service_provider` roster records under an owner org. Assignment produces an in-app record; the manager coordinates the actual work by phone / WhatsApp outside the app.

### 7.6 Admin

Admins access `/admin` only. Every admin action writes `admin_action_log`.

---

## 8. Core Flows

### Flow A — Owner onboarding

1. User signs up (phone + OTP, or email + password). `user` created with `TENANT` role by default.
2. User clicks "Start listing properties" → creates `owner_org`, adds `OWNER` membership, and starts a 14-day `TRIALING` subscription on the Starter plan. `OWNER` global role added to `user_role`.
3. Non-blocking prompt to upload verification documents.
4. Owner can create and publish listings during trial. Listings carry an "Unverified" badge until verification is approved.

### Flow B — Invite a manager

1. Owner → Team → Invite → enters phone or email, selects permissions → `invite` row created + SMS / email link sent.
2. Invitee opens link → signs up or logs in → `membership` created with the granted permissions → lands on the org's dashboard with only their allowed menu items.
3. Invite token single-use, 7-day expiry, revocable from Team view.

### Flow C — Create listing

1. Owner / manager with `manage_listings` → New Listing.
2. Form fields: title, description, monthly_rent_ugx, deposit_months, bedrooms, bathrooms, furnished, amenities, neighbourhood, district, address_text, 1–10 images.
3. On save: backend geocodes `neighbourhood + address_text` via Google Maps Geocoding → writes lat, lng. Listing status = `DRAFT`.
4. Owner clicks Publish. Published only if subscription is `TRIALING` or `ACTIVE` **and** the org is under its plan's `max_listings` cap. Otherwise, blocked with an upsell prompt.
5. Listings with fewer than 3 images cannot be published.

### Flow D — Tenant discovery & viewing request

1. Anonymous or logged-in tenant searches by neighbourhood, price range, bedrooms, furnished → listing list → listing detail page.
2. Clicks **Request viewing** → must be logged in → submits preferred time windows.
3. Owner / manager with `manage_tenancies` receives SMS + in-app notification → accepts or declines with a short caretaker note.
4. Viewing happens offline. Caretaker later marks the request `COMPLETED`.

### Flow E — Start a tenancy

1. After a successful viewing, caretaker / owner clicks **Create tenancy** on the listing.
2. Picks a tenant from users who previously requested a viewing on this listing. Confirms start_date, monthly_rent_ugx (pre-filled), deposit_ugx, rent_due_day (1–28).
3. Listing status flips to `RENTED`. `tenancy` row created. First `rent_charge` generated for the current period.
4. Tenant's dashboard now shows "My tenancy at [address]" with a Pay rent button.

### Flow F — Rent collection cycle (recurring heartbeat)

1. Daily scheduled job at 06:00 Africa/Kampala: for every `ACTIVE` tenancy, ensure a `rent_charge` row exists for the current period.
2. 3 days before `due_date`: SMS + in-app reminder to tenant ("Rent of UGX X due on DD/MM").
3. Tenant opens app → Pay rent → Flutterwave checkout (picks MoMo / card / bank) → webhook on success flips the matching `rent_charge` to `PAID`, sends receipt SMS to tenant and owner.
4. If unpaid by `due_date + 1`: status → `OVERDUE`, nudge SMS to tenant + in-app notice to owner. No auto-penalty in MVP.

### Flow G — Fault ticket

1. Tenant → My tenancy → Report fault → category, title, description, 0–5 photos → ticket created with status `PENDING`.
2. Owner / manager with `manage_faults` receives SMS + in-app → opens ticket → **Acknowledge** → status `IN_PROGRESS`, `acknowledged_at` set.
3. Manager optionally picks an SP from the org's roster (dropdown) → `fault_assignment` written. This is a record, not a notification (SPs are not users).
4. Manager coordinates offline → clicks **Mark done** → status `DONE`, `resolved_at` set, SMS to tenant.
5. Tenant accepts or reopens. Reopen → status → `PENDING`, `fault_event` logged.

### Flow H — Chat

1. Tenant on a listing detail page clicks **Message host** → `chat_thread` created scoped to the listing.
2. Once a tenancy starts, the thread re-scopes to the tenancy (same id, `listing_id` cleared, `tenancy_id` set).
3. Lean implementation: text only, 5-second polling for new messages, unread count in header. No media upload in MVP.

### Flow I — Subscription lifecycle

1. On `trial_ends_at`: if no payment method or `cancel_at_period_end = true` → status `TRIALING` → `PAST_DUE`. Listings hidden from public search after a 7-day grace; still visible to the owner.
2. Owner adds MoMo / card → first charge succeeds → status → `ACTIVE`. Token stored for recurring charges.
3. Monthly scheduled job charges each `ACTIVE` subscription's token → extends `current_period_end`.
4. Failed charge → retry at T+1, T+3, T+7 days. After third failure → `CANCELED`, listings hidden.
5. Plan change: effective next billing period.

---

## 9. Payments

### 9.1 Aggregator

**Flutterwave** is the MVP payment aggregator for:
- MTN Mobile Money collection.
- Airtel Money collection.
- Card (Visa / Mastercard).
- Bank transfer / direct account redirect.
- Tokenization for recurring subscription charges.
- Subaccounts for owner-direct rent collection.

Pesapal is a documented swap-out if Flutterwave onboarding stalls; the internal payment service interface is provider-agnostic.

### 9.2 Two payment shapes

**Rent collection — passthrough to owner**
- At owner onboarding, the owner configures a Flutterwave subaccount (their own MoMo number or bank account). Kasisira never holds tenant funds.
- Tenant taps Pay rent → backend creates a `payment_intent` with idempotency key `tenancy_id:period_month` → calls Flutterwave referencing the owner's subaccount.
- Flutterwave aggregator fees are borne by the owner (deducted from the credited net).
- Zero platform commission on rent in MVP.
- Webhook `charge.completed` → mark `rent_charge = PAID`, issue SMS receipt to tenant and owner.
- Webhook `charge.failed` → mark `payment_intent = FAILED`, surface error to tenant, no state change on the `rent_charge`.

**Subscription — Kasisira collects**
- Owner enters MoMo / card at checkout → Flutterwave tokenizes → token stored on `subscription.payment_method_token`.
- Monthly job charges the token → updates `current_period_end`.
- Failed charge retries: T+1, T+3, T+7. Final failure → `CANCELED`, listings hidden.
- All subscription funds land in Kasisira's own Flutterwave account.

### 9.3 Reliability

- **Idempotency:** every `payment_intent` has an idempotency key; webhook handlers are idempotent (reprocessing never double-credits).
- **Webhook signing:** verify Flutterwave's signature on every webhook; reject unsigned or invalid.
- **Outbox for side effects:** marking a charge PAID writes the SMS/email intent to `outbox` in the same DB transaction. A worker drains the outbox and calls providers.
- **Nightly reconciliation:** job fetches Flutterwave's daily transaction list, compares with local `payment_intent` rows, flags mismatches to admin.

### 9.4 Currency & refunds

- UGX only. Amounts as `BIGINT`, no subunit.
- Refunds are admin-initiated via the admin console (rare for rent, more common for subscription within grace).

---

## 10. Verification & Trust

### 10.1 Owner verification

- One submission per `owner_org`, not per listing.
- Required uploads:
  - National ID (front + back) — photo or PDF.
  - Proof of ownership or authority — title deed, purchase agreement, land tenure letter, or signed authorization-to-let from the registered owner.
- Admin console queues `PENDING` submissions with a side-by-side viewer. Admin approves or rejects with a note. Target SLA: 48h.
- On approve: `owner_org.verified_at` set → all listings on that org display a green "Verified" badge and rank higher in search.
- On reject: owner receives SMS with the admin's note; can resubmit.

### 10.2 Listing-level trust signals (mechanical)

- Minimum 3 images to publish.
- Listings auto-dim to low-priority rank after 90 days without an owner action (log in + refresh listing) — prevents the stale-listing problem common on Ugandan classifieds.

### 10.3 Tenant verification

- Phone-only in MVP (OTP at signup).
- Full ID capture is a Phase 2 item tied to future escrow / deposit features.

### 10.4 Abuse reporting

- Every listing has a tenant-visible **Report** button with preset reasons (fake listing, already rented, wrong price, scam, other).
- Three or more reports within 14 days auto-archive the listing (status → `ARCHIVED`) and notify admin + owner.

---

## 11. Notifications

### 11.1 Channels

- **SMS** (Africa's Talking) — primary channel; assume tenants may not open the app daily.
- **Email** (Postmark or SES) — secondary; used when email is known.
- **In-app** — persistent bell, unread count, deep-link list.
- **Web push** — deferred (requires PWA, which is out of MVP).

### 11.2 Routing defaults

| Event | Recipient | SMS | Email | In-app |
|---|---|---|---|---|
| OTP login | user | ✓ | — | — |
| Viewing request submitted | owner / manager | ✓ | ✓ | ✓ |
| Viewing request accepted / declined | tenant | ✓ | — | ✓ |
| Tenancy created | tenant | ✓ | ✓ | ✓ |
| Rent due (T-3) | tenant | ✓ | — | ✓ |
| Rent paid — receipt | tenant + owner | ✓ | ✓ | ✓ |
| Rent overdue | tenant + owner | ✓ | — | ✓ |
| Fault ticket opened | owner / manager | ✓ | — | ✓ |
| Fault status changed | tenant | ✓ | — | ✓ |
| New chat message | counterparty | — | — | ✓ (email digest if unopened 24h) |
| Invitation received | invitee | ✓ | ✓ | — |
| Subscription trial ending (T-3) | owner | ✓ | ✓ | ✓ |
| Subscription payment failed | owner | ✓ | ✓ | ✓ |
| Verification approved / rejected | owner | ✓ | ✓ | ✓ |

### 11.3 Reliability

- All notifications written to `notification` in the same DB transaction as the originating domain event (outbox pattern).
- A worker drains the outbox, calls the provider, updates status. Exponential backoff retries up to 3 times; final failure flagged to admin.
- Per-user / per-channel / per-hour rate limit to prevent loop-driven spam.

### 11.4 Cost assumption

Africa's Talking SMS in Uganda is ~UGX 25–45 per message. A tenant receives ~6 SMS per month. At 1,000 active tenants that is ~UGX 200–250k per month — factor into subscription pricing.

---

## 12. Admin Console

Lives at `/admin` within the same SPA; gated by `ADMIN` role.

1. **Verification queue** — pending submissions, oldest first; approve / reject with note.
2. **Users** — search by phone / email / name; view roles, memberships, suspension status; suspend.
3. **Listings** — search, force-archive with reason, un-archive.
4. **Abuse reports** — queue, report counts, owner history, one-click archive or dismiss.
5. **Subscriptions & plans** — list orgs by status; manual extend trial, grant plan override, issue refund; edit plan catalogue.
6. **Payments** — search `payment_intent`; view raw webhook payload; mark-as-paid override (logged).
7. **Reconciliation dashboard** — today's Flutterwave totals vs local `payment_intent` totals; mismatches highlighted.
8. **System health** — outbox queue depth, SMS failure rate (24h), webhook latency, DB pool.

Every admin action writes an `admin_action_log` row (append-only). Staffing assumption: 1 admin for the first few hundred orgs — bias toward manual buttons over automated rules.

---

## 13. Error Handling & Edge Cases

- **Duplicate payment webhooks:** idempotent by `payment_intent.idempotency_key`. Reprocessing is a no-op.
- **Geocoding failure at listing creation:** listing saves as `DRAFT` with `lat/lng = null`; publish is allowed (search still works by neighbourhood text); owner is nudged to refine address.
- **Tenant opens fault ticket after tenancy ends:** blocked — tickets are only creatable on `ACTIVE` tenancies. Historical tickets remain readable.
- **Manager's permission removed mid-session:** next permission-guarded action is denied; cached menu on the frontend is stale until refresh (acceptable).
- **Invite accepted by a different phone/email than invited:** blocked — invitee must sign up with the invited identifier.
- **Subscription downgrade below current listing count:** allowed at end of period; active listings above the new cap are archived with notice at period rollover.
- **Flutterwave downtime during rent payment:** tenant sees an error; `payment_intent` stays `PENDING`; an owner can manually reconcile via admin if the charge eventually clears (rare).
- **Tenant ends lease mid-period:** manual action by owner → `tenancy.status = ENDED`, `end_date` set; the associated listing flips from `RENTED` back to `DRAFT` (owner can Publish again after refreshing details); open `rent_charge` for the period remains due unless waived by owner.
- **Creating a tenancy without a prior viewing request:** MVP requires the tenant to appear in the listing's viewing-request list. If the tenant came via an off-platform channel, the caretaker first creates a viewing request on their behalf (marked `COMPLETED`), then proceeds with tenancy creation. Clunky but preserves referential integrity; revisit in Phase 2.

---

## 14. Testing Strategy

- **Unit tests** for each module's service layer (JUnit 5 + Kotlin test fixtures). Heavy coverage on permission checks and payment state transitions.
- **Integration tests** with a real Postgres (Testcontainers). Exercise the outbox + notification worker end-to-end. No mocking of the DB.
- **Contract tests** for the Flutterwave integration against a recorded / replayed fixture set; at least one smoke test against Flutterwave sandbox in CI.
- **Frontend** — component tests with Vitest; end-to-end tests with Playwright on 2–3 golden paths (tenant signs up → requests viewing; owner signs up → creates listing → publishes; tenant pays rent).
- **Manual pre-launch checklist** — SMS delivery on MTN + Airtel test numbers; Flutterwave test transactions on MoMo / card / bank; verification approval flow on staging.

---

## 15. Open Questions

- Exact subscription plan pricing (UGX per tier). Requires cost modelling incl. SMS spend.
- Flutterwave subaccount vs simpler "pay the owner's MoMo number directly" — depends on what Flutterwave's onboarding requires for production collection accounts in Uganda.
- Admin console auth: reuse the main login flow with a role check, or a separate admin-only login surface. Default to the former; revisit if needed.
- SMS sender-ID registration with UCC — mandatory before production SMS traffic.
- Data retention policy for ID documents and fault photos (not yet defined).

---

## 16. Phase 2 Hooks (built-in for later)

Design decisions taken in MVP that keep Phase 2 cheap:

- `listing.type` is an enum already including `STAY`; adding short-stay listings is an enum value and additional columns, not a schema rewrite.
- `service_provider` is an org-scoped table; moving to a marketplace means adding a public profile table and widening the FK, not a migration of the roster model.
- `chat_thread` already accommodates different anchor entities via nullable `listing_id` / `tenancy_id`; new thread types (e.g., booking threads) slot in the same way.
- `payment_intent.purpose` is an enum; Phase 2 `STAY_BOOKING` is an additional value.
- `user_role` supports multiple roles per user, so adding a `SERVICE_PROVIDER` or `AGENT` role later does not break existing accounts.
