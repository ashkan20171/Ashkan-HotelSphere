# Ashkan HotelSphere — Stage 12

Java 17 / Swing desktop hotel management demonstration with SQLite JDBC persistence, room and guest management, booking lifecycle, manual payment recording, invoices, financial and receivables reporting.

## New in Stage 12
- A separate, pure `AnalyticsService` for occupancy KPIs.
- Dashboard occupancy percentage alongside existing room, booking and occupied-room cards.
- Seven-day chart shares the same occupancy calculation to prevent discrepancies.
- Cancelled bookings, duplicated room reservations and nonexistent room IDs do not inflate occupancy.
- More complete FA/EN sidebar labels; this is **not** yet full application localization.
- Added `AnalyticsServiceSmokeTest` for occupancy edge cases.

## Run
Requires JDK 17 and Maven with access to the SQLite JDBC dependency:

```bash
mvn clean compile exec:java
```

## Run standalone smoke tests (compile only, does not exercise SQLite)
```bash
javac --release 17 -encoding UTF-8 -d out src/main/java/com/ashkan/hotel/*.java
javac --release 17 -encoding UTF-8 -cp out -d out src/test/java/com/ashkan/hotel/AnalyticsServiceSmokeTest.java
java -cp out com.ashkan.hotel.AnalyticsServiceSmokeTest
```

**Important:** Back up the existing database before updating. This version does not implement production-grade multi-user access, tax configuration, or full localization. The GUI has not been tested on Windows in this environment.

## Stage 13: Housekeeping
A new Housekeeping screen tracks CLEAN, DIRTY, IN_PROGRESS, INSPECTION and OUT_OF_SERVICE per room. Status changes are persisted separately in `~/.ashkan-hotelsphere/housekeeping.properties` and survive restarts. Occupancy and housekeeping are intentionally separate concepts. This version does not automatically block bookings for out-of-service rooms; implement this rule before production use.

Test with `javac --release 17 -encoding UTF-8 -cp out -d out src/test/java/com/ashkan/hotel/HousekeepingServiceSmokeTest.java` and `java -cp out com.ashkan.hotel.HousekeepingServiceSmokeTest` after compiling main sources.

## Stage 14
- Staff housekeeping tasks with assignee, priority and state transitions (OPEN -> IN_PROGRESS -> DONE).
- Prevent new bookings for OUT_OF_SERVICE rooms.
- Tasks persist in a local TSV file, not yet in SQLite; single-user only.

## Stage 15: Guest service requests
- Added a bilingual Guest requests screen with room validation, categories, priorities, assignees and workflow OPEN -> IN_PROGRESS -> RESOLVED.
- Records persist in `~/.ashkan-hotelsphere/guest-requests.tsv` using atomic file replacement where supported.
- Added `GuestRequestServiceSmokeTest` covering transitions and persistence.
- This stage does **not** migrate housekeeping, staff tasks or guest requests into SQLite; these remain local single-user files.


## Stage 16 — Operational SQLite integration
Housekeeping room states, staff tasks and guest service requests now use the `operational_records` table in the existing `hotel.db`. On first use, legacy `housekeeping.properties`, `tasks.tsv`, and `guest-requests.tsv` are imported if the corresponding SQLite category is empty; legacy files are left intact. Existing path-based constructors remain available for isolated smoke tests. SQLite operations are transactional; this remains a single-desktop-user application, not a networked PMS. Back up your entire `.ashkan-hotelsphere` directory before upgrading.


## Stage 17: Operational audit trail
- SQLite append-only `operational_audit` table, event timestamps in UTC.
- Records task and guest-request creation and status changes; Admin-only Audit screen, latest 250 events.
- Audit is best-effort relative to workflow persistence: a database audit error can be raised after the operation is saved; do not retry blindly.
- Audit records do not currently include authenticated actor identity and are not tamper-proof.
- Java 17+ / Maven with sqlite-jdbc required to run.

## Stage 18 — Operations Center
Admin-only operations center with live task/request KPIs and client-side request filtering by text and status. `OperationsService` keeps metric calculations independent of Swing. Run `OperationsServiceSmokeTest` to verify calculations. No schema migration is required.


## Stage 19 — Audit actor attribution
- Audit events now record the active application account role (`Admin` or `Receptionist`).
- SQLite schema migrates existing `operational_audit` rows safely, labeling previous entries `LEGACY`.
- Admin Audit table includes an Actor column.
- Background actions without a login context use `SYSTEM`.
- **Limitation:** roles are shared demo accounts, so Actor is not a unique staff identity. Audit writes still occur after business writes, not in the same transaction.


## Stage 20 — individual staff accounts
- Administrator-only staff account management dialog; create users and assign Admin/Receptionist roles.
- Unique usernames are now used as audit actors, rather than a shared role name.
- Existing account hashes are preserved; roles are stored separately in `roles.properties`.
- Prevents demoting the last administrator; new passwords require 12+ characters.
- **Limitations:** demo passwords remain on legacy installations, roles and hashes are in separate local files, and audit writes are not atomic with business changes. This is not production-ready.


## Stage 21 — Staff activity report

- Admin-only staff activity report based on the latest 1,000 audit events.
- Aggregates recorded events per individual actor, including created records and status changes.
- Excludes legacy/system events; no employee productivity ranking or HR evaluation should be inferred from counts.
- Added `StaffPerformanceServiceSmokeTest` for deterministic aggregation checks.
- Note: audit writes remain separate transactions; comprehensive SQLite integration tests and Windows GUI verification remain outstanding.


## Stage 22 — Atomic operational audit

Task and guest request create/status changes now commit their SQLite snapshot and audit event in one transaction using `OperationalSqliteStore.replaceWithAudit`. If audit insertion fails, the operational snapshot is rolled back too. Actor is sourced from the current `AuditService` session context. Legacy TSV migration remains supported; migration snapshots do not generate synthetic audit entries.

Limitations: this is still a single-user desktop application, account storage remains file-based, and full Windows GUI / SQLite JDBC integration testing is required before production use.

## Stage 23 — SQLite integration and rollback verification

Stage 23 adds `OperationalSqliteStoreIntegrationTest`, an executable integration test that uses a temporary SQLite database to verify:

- operational records and their audit event are committed together;
- the active actor is stored on the audit event;
- a forced audit-insert failure rolls back the operational snapshot and leaves no partial audit event;
- SQLite `PRAGMA integrity_check` reports `ok` after the tested operations.

Run the application as usual:

```bash
mvn clean compile exec:java
```

Compile the test sources with Maven:

```bash
mvn test-compile
```

Then run the integration test with the SQLite JDBC driver on the classpath. On Windows PowerShell, one option after Maven has downloaded dependencies is:

```powershell
$driver = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\xerial\sqlite-jdbc\3.46.1.3" -Filter "sqlite-jdbc-3.46.1.3.jar" | Select-Object -First 1).FullName
java -cp "target\classes;target\test-classes;$driver" com.ashkan.hotel.OperationalSqliteStoreIntegrationTest
```

The integration test creates and removes its own temporary database; it does not touch the hotel's real `hotel.db`.

## Stage 24 — Filterable audit reports and safe CSV export

- The Admin audit page can filter the latest 1,000 loaded events by UTC date range, actor, and free-text search across event fields.
- The visible table is sortable and shows the number of matching events.
- Export writes only matching events to UTF-8 CSV (with a BOM for spreadsheet compatibility), quotes fields correctly, and prefixes formula-like cell values to reduce CSV formula-injection risk.
- Filtering and CSV serialization live in `AuditReportService`, independently testable without a running GUI or database.
- `AuditReportServiceSmokeTest` checks date/actor/text filtering, case-insensitive actor matching, invalid date ranges, CSV quoting and formula-injection handling.
- The audit screen still loads a maximum of the latest 1,000 events; date filters do not query older events from SQLite. Expand the query/pagination before relying on it for long-retention compliance reports.

## Stage 25 — Date-range management reports

- Management reports now accept an inclusive start/end date and show daily occupancy, occupied room count, room-nights, overlapping non-cancelled bookings, payments recorded during the selected period, and current total outstanding balances.
- CSV export produces the daily report rows in UTF-8 with a BOM for spreadsheet compatibility.
- `PeriodReportService` keeps report calculations independent from Swing and persistence, validates date order, and limits reports to 366 days to avoid accidentally generating huge tables.
- Payment totals are based on payment creation dates; booking counts include bookings overlapping any part of the selected interval. Outstanding balance is the current total across eligible bookings, not a historical as-of balance.
- `PeriodReportServiceSmokeTest` verifies inclusive date boundaries, occupancy and room-night totals, payment date filtering, CSV output and validation.
- As with earlier stages, full Windows GUI and SQLite JDBC runtime integration tests still need to be run in an environment with Maven/dependencies installed.


## Stage 26 — Daily revenue and occupancy visualization

- Added a lightweight Java2D chart to the date-range management report: daily payment totals are shown as teal bars and occupancy percentage as an orange line.
- Added a payment-method breakdown for the selected inclusive date range, sorted by amount, including total amount and transaction count. Missing payment methods are grouped as `Unspecified`; null, zero and negative payments are excluded from this breakdown.
- Period report CSV now includes a second section with payment method totals and transaction counts.
- Expanded `PeriodReportServiceSmokeTest` to cover method aggregation, date filtering, ordering and CSV output.
- The chart is a custom Swing component and requires no additional charting dependency. Full interactive GUI verification on Windows and SQLite JDBC integration testing remain outstanding.


## Stage 27 — Payment-method filters in management reports

- Added a payment-method selector to the Admin management report. Choosing a method filters daily received totals, the chart, and the method breakdown; “All methods” preserves the previous behavior.
- Filtering is case-insensitive and trims surrounding whitespace. Blank/missing methods are represented as `Unspecified`.
- Booking overlap, room-night, occupancy and current outstanding figures remain hotel-wide, so a payment filter cannot misleadingly change operational KPIs.
- Added smoke-test coverage for case-insensitive filtering, zero results for unmatched methods, and unchanged occupancy/booking metrics.
- Verification: Java 17 compilation and standalone smoke tests; full Swing runtime and SQLite JDBC integration still require a desktop environment with the dependency available.


## Stage 28 — Inactivity session timeout

- Added a 15-minute inactivity timeout after successful sign-in, with a warning shown after 14 minutes.
- Mouse and keyboard input renew the inactivity deadline; a non-modal warning can be dismissed by activity or by choosing Continue session.
- On timeout the current window is disposed, the global input listener and Swing timer are removed, and the user is returned to sign-in.
- Extracted deterministic timeout logic into `SessionTimeoutService`, with configurable timeout/warning durations and a standalone smoke test.
- This is a local desktop-session safeguard, not a substitute for OS-level locking, database access control, or protection against another process accessing local files. Full Swing interaction testing on Windows remains outstanding.

Compile and run the unit-style smoke test:

```bash
javac --release 17 -encoding UTF-8 -d out src/main/java/com/ashkan/hotel/*.java
javac --release 17 -encoding UTF-8 -cp out -d out src/test/java/com/ashkan/hotel/SessionTimeoutServiceSmokeTest.java
java -cp out com.ashkan.hotel.SessionTimeoutServiceSmokeTest
```

## Stage 29 — Account security

- Added a five-minute lockout after five consecutive failed login attempts.
- Disabled accounts are rejected at login, and account activation state is stored locally without deleting account history.
- Administrators cannot disable their own account or disable/demote the last active administrator.
- Added a local `security-events.tsv` log for login outcomes, account status changes, and sign-outs. Passwords are never written to this log.
- Account activation, failed-attempt counters, and lockout expiry timestamps persist under `~/.ashkan-hotelsphere/`.
- Added `AuthServiceSecuritySmokeTest` for lockout, persistence, disabled-account handling, and last-admin protection.

Security logs are local application logs, not tamper-proof records. Change the demonstration credentials before storing real guest information.


## Stage 30 — Staff profiles, directory search, and activity review

- Added `StaffProfileService` to persist employee contact and organizational details separately from authentication credentials. Profiles include full name, email, phone, department, job title, and notes; profile records remain available when an account is disabled.
- Expanded Staff Management with live text search across username, name, email, phone, department, and job title, plus role and active-status filters.
- Added an employee profile editor with input validation and atomic local-file persistence. Credentials remain in the existing authentication store and are not copied into profile data.
- Added per-account security activity review using the existing local security event log.
- Added `StaffProfileServiceSmokeTest` for persistence/reload, searching, email validation, and field-length validation.
- Verification: Java 17 compilation succeeded and all 12 standalone `*SmokeTest` classes passed. Full interactive Swing testing on Windows and SQLite JDBC integration testing remain outstanding.

Staff profile data is stored locally in `~/.ashkan-hotelsphere/staff-profiles.properties`. This local properties file is not encrypted; restrict access to the operating-system user account and avoid placing highly sensitive personal data in the notes field.


## Stage 31 — Staff attendance and shift tracking

- Added `AttendanceService` with configurable named shifts, daily clock-in/clock-out records, duplicate check-in protection, and prevention of checkout before check-in.
- Calculates worked minutes, late minutes, and overtime minutes from each employee's selected shift.
- Attendance records persist independently in `~/.ashkan-hotelsphere/attendance.tsv` using atomic file replacement; malformed rows are skipped when loading.
- Added an Attendance page. Employees can record their own attendance; administrators can review the current month's team records. CSV export follows the same visibility rule.
- Added `AttendanceServiceSmokeTest` for time calculations, duplicate punches, monthly filtering, CSV output, and persistence/reload.
- Verification: Java 17 compilation and standalone smoke tests. Full interactive Swing testing and SQLite integration testing remain outstanding.

Attendance is a local operational register, not a legally certified time clock. The configured shifts are illustrative defaults; administrators should adjust them to the hotel's actual policies before operational use. Current implementation treats each attendance record as a same-calendar-day shift.

## Stage 32 — Payroll management
- Added local payroll compensation profiles (base salary, allowances, hourly rate, late-deduction rate, overtime multiplier).
- Monthly payslips aggregate attendance overtime and lateness, calculate deductions and net pay, and can be exported as CSV.
- Payroll screen is administrator-only. Amounts are currency-neutral units; configure rates using the hotel's chosen currency.
- Added `PayrollServiceSmokeTest` for calculations, validation, persistence, and CSV output.


## Stage 33 — Inventory and stock management

- Added an Admin-only Inventory screen with item/SKU catalog, category, unit, minimum stock, configured unit cost and supplier metadata.
- Stock receipts, consumption and signed adjustments are recorded in a dated movement ledger; consumption and adjustments cannot drive stock below zero.
- Low-stock alerts trigger when on-hand quantity is at or below the configured minimum. The screen shows item count, low-stock count and estimated inventory value.
- Added sortable stock and movement views plus UTF-8 CSV exports (with BOM).
- Inventory data is stored separately in `~/.ashkan-hotelsphere/inventory.tsv` using atomic replacement where supported; this is a single-workstation local ledger and is not yet synchronized to SQLite or multiple terminals.
- Inventory valuation uses each item's currently configured catalog unit cost, not weighted-average or lot-specific purchase cost. Supplier information is stored as item/receipt metadata; there is not yet a separate supplier master or purchase-order workflow.
- Added `InventoryServiceSmokeTest` for persistence, receipt/consumption balance, insufficient-stock rejection, low-stock threshold, adjustment, valuation and CSV serialization.

## Stage 34 — Restaurant & Café Management
- Added a restaurant/café menu with item code, category, price, availability, and optional inventory recipe linkage.
- Added table-based order entry, guest reference, order totals, and order lifecycle statuses (open, preparing, served, paid, cancelled).
- Orders can consume linked inventory quantities; cancellation restores the consumed quantities through inventory adjustment records.
- Added restaurant order/sales CSV export and persistent local restaurant data.
- Restaurant UI supports bilingual Persian/English labels and follows the app's RTL/LTR layout switching.
- Storage is local to the workstation. Recipe linkage currently supports one inventory SKU per menu item; multi-ingredient recipes and multi-terminal synchronization are future enhancements.

## Stage 35 — Conference & Event Management
- Added conference hall catalog with capacity, hourly rental rate and equipment details.
- Added event reservations with attendee-capacity validation and overlapping-reservation prevention per hall.
- Added event lifecycle statuses (Reserved, Confirmed, Completed, Cancelled); cancelled events release the hall time slot.
- Calculates hall rental, catering, additional and total estimated costs; provides CSV export.
- Event data is persisted locally with atomic file replacement under `~/.ashkan-hotelsphere/events.tsv`.
- Added an administrator-facing bilingual Swing page and dialogs for hall setup, reservations and status updates.

## Stage 36 — Spa & Leisure Management
- Added facility catalog for spa rooms, pools, fitness areas and other leisure facilities, with capacity limits.
- Added service catalog with category, duration, per-participant price and availability flag.
- Added appointment booking with calculated end times, participant-capacity checks, and conflict prevention for both facilities and assigned staff/therapists.
- Added appointment lifecycle (Booked, Confirmed, Completed, Cancelled), cancellation releases the time slot, estimated revenue totals and CSV export.
- Added an administrator-only bilingual Swing page and dialogs for facility/service setup, bookings and status changes.
- Data is stored locally in `~/.ashkan-hotelsphere/spa.tsv` using atomic file replacement. Charges are estimates; payment posting to the central finance ledger and multi-facility synchronization are future enhancements.
- Added `SpaServiceSmokeTest` for pricing, capacity, scheduling conflicts, cancellation/rebooking, persistence and CSV output.

## Stage 37 — Parking & Transportation Management

- Manage parking slots by code and zone, including enabled/disabled status and live availability.
- Register vehicle check-ins and check-outs, prevent double occupancy and duplicate active vehicle plates.
- Maintain hotel driver and vehicle records.
- Book airport/station/hotel transfers with passenger count, fare, pickup time, destination, optional driver assignment and status lifecycle.
- Prevent overlapping assignments for the same driver using a conservative one-hour scheduling window on either side of each pickup.
- View parking and completed-transfer revenue estimates and export combined parking/transport records to CSV.
- Local persistence: `~/.ashkan-hotelsphere/parking.tsv` with atomic file replacement.
- Run the standalone test with `java -cp out com.ashkan.hotel.ParkingServiceSmokeTest` after compiling sources.

## Stage 38 — Hotel CRM & Loyalty Management

- Added guest CRM profiles with unique guest IDs, name, email, phone, notes and VIP flag.
- Added a persistent loyalty-points ledger with positive earning and negative redemption entries; redemptions are rejected when the guest balance is insufficient.
- Loyalty tiers are calculated from current points (Standard, Silver, Gold, Platinum); VIP guests are promoted to Platinum.
- Added guest feedback with 1–5 rating validation and workflow statuses (Open, In Progress, Resolved, Closed), plus average-rating summary.
- Added administrator-only bilingual Swing CRM screen for profile creation, points adjustments, feedback capture/status updates and CSV export.
- Local data is stored in `~/.ashkan-hotelsphere/crm.tsv` using UTF-8, Base64-safe fields and atomic file replacement.
- Added `CrmServiceSmokeTest` for profile persistence, earning/redemption, insufficient balance, tier calculation, rating validation, feedback status and CSV export.
- Loyalty points and CRM profiles are currently maintained in the CRM ledger; automatic point accrual from paid bookings/invoices, campaign messaging and integration with external email/SMS providers are future enhancements.

## Stage 39 — Hotel Marketing & Campaign Management
- Added campaign and coupon administration with percentage or fixed discounts.
- Added validity dates, minimum spend, optional maximum discount, usage limits, active/inactive state, and minimum loyalty tier.
- Added coupon preview/redemption against a guest profile with tier and eligibility validation.
- Added redemption history, campaign usage counts, total discount reporting, and CSV export.
- Campaign and redemption data are stored locally in `~/.ashkan-hotelsphere/campaigns.tsv` using atomic file replacement.
- Added `CampaignServiceSmokeTest` for discount calculations, tier eligibility, usage limits, persistence, and CSV output.

## Stage 40 — Reservation Channel Manager
- Added channel catalog with active/inactive status and commission percentage validation (0–100%).
- Added room-type inventory definitions and channel-specific date-range rates, nightly prices and allotments.
- Added manual import of external/OTA reservations with channel + external-reference duplicate protection.
- Availability checks enforce both per-channel allotments and the physical room-type inventory for every night of a stay; cancelled reservations release inventory.
- Added booking lifecycle updates, gross revenue, channel commission and net revenue summaries, plus CSV export.
- Added administrator-only Swing page and dialogs for channel setup, room-type setup, rate/allotment setup, external booking import and status changes.
- Data is stored locally in `~/.ashkan-hotelsphere/channels.tsv` with atomic replacement where supported.
- Important integration boundary: this stage supports controlled manual import and local channel inventory/rates. It does not yet connect to live OTA APIs, push real-time availability/rates, or automatically create/update the core front-desk booking record. API integrations require provider credentials, contracts and provider-specific adapters.
- Added `ChannelManagerServiceSmokeTest` for allotment and physical inventory limits, duplicate references, cancellation release, commission/net revenue, persistence and CSV export.

## Stage 41 — Front Desk & Guest Journey

- Added a guest journey workspace linked to the existing booking, room and guest records.
- Added guest folios with accommodation totals, restaurant/other incidental charges, partial settlements and outstanding balance calculation.
- Added room moves for checked-in guests with room availability and out-of-service validation.
- Added stay extensions with overlapping-booking validation.
- Added final checkout that requires a checked-in stay and a zero folio balance.
- Added folio detail view and CSV export for charges and settlements.
- Folio ledger is stored locally in `~/.ashkan-hotelsphere/guest-folios.tsv` using atomic file replacement.
- Added `GuestJourneyServiceSmokeTest` for charge validation, totals, partial settlement, overpayment rejection, persistence and CSV output.

Build with Java 17:

```bash
javac --release 17 -encoding UTF-8 -d out $(find src/main/java src/test/java -name '*.java')
```

The guest folio ledger is a local operational ledger; it does not yet post automatically into the accounting module or synchronize across multiple terminals.

## Stage 42 — Revenue management and dynamic pricing

- Added `RevenueManagementService` with persistent seasonal and weekday rate rules, room-type-specific or hotel-wide (`*`) overrides, and explicit weekday precedence over general seasonal rates.
- Added date-range revenue analytics: rooms sold, occupancy percentage, room revenue, ADR and RevPAR. Confirmed and checked-in stays count; cancelled bookings are excluded and checkout dates are treated as exclusive.
- Added advisory rate recommendations (increase / hold / decrease) using occupancy thresholds. Suggested prices are informational and never overwrite configured room rates.
- Added an Admin-only Revenue Management page with KPI cards, a daily forecast/actual table, configured rate rules, channel revenue/commission/net comparison, and UTF-8 CSV export.
- Rate rules are stored separately at `~/.ashkan-hotelsphere/revenue-rules.tsv`; back up this file together with the hotel data folder.
- Channel comparison uses all non-cancelled imported channel bookings; it is an aggregate view, not filtered to the selected daily date range. The occupancy model assumes one room per internal booking and uses the current base room rate unless an override applies.
- Verification: Java 17 compilation and all 24 standalone `*SmokeTest` classes passed. The desktop UI was not manually exercised on Windows; SQLite JDBC runtime integration was not tested in this environment.

## Stage 43 — Housekeeping intelligence & predictive maintenance

- Added a persistent maintenance work-order register with room, asset, issue description, priority, reported/due dates, assignee, notes and lifecycle (Open, In Progress, Completed).
- Added preventive-maintenance plans by room and asset, with configurable service intervals and calculated next-due dates.
- Added a 30-day due/overdue queue with priority classification; completed work orders are excluded from alerts and cannot be reopened.
- Added operational readiness alerts by combining upcoming arrivals with room-cleaning state, and alerts for rooms marked Out of Service.
- Added bilingual Swing workspace with KPI cards, alert queue, work-order list, report-issue and preventive-plan dialogs, status actions and CSV export.
- Maintenance data is stored in `~/.ashkan-hotelsphere/maintenance-intelligence.tsv` using UTF-8, Base64-safe fields and atomic file replacement.
- Added `MaintenanceIntelligenceServiceSmokeTest` for due-date calculations, alert priority, work-order lifecycle, persistence, validation and CSV export.
- Predictive alerts are deterministic rule-based indicators, not machine-learning predictions. Preventive plans currently calculate due dates from the recorded last-service date; completing a work order does not automatically reset a plan's service interval.
- Verification: Java 17 compilation and all 25 standalone `*SmokeTest` classes passed. The Swing interface was not manually exercised on Windows; SQLite JDBC runtime integration was not tested in this environment.

## Stage 44 — Housekeeping workforce & staff scheduling

- Added a dedicated housekeeping workforce planner with date, shift (Morning / Evening / Night), employee, room, task type, priority and notes.
- Added assignment lifecycle controls (Assigned → In Progress → Completed) with start/completion timestamps and validation against invalid transitions or negative durations.
- Prevents duplicate active housekeeping assignments for the same room on the same date, including across shifts.
- Added one-click generation of today's checkout-cleaning and arrival-preparation tasks from confirmed / checked-in internal bookings. Checkout tasks are urgent; arrival preparation is high priority. Generated work is left Unassigned for a supervisor to allocate.
- Added per-shift assigned/completed/urgent summaries and employee productivity for a selected seven-day window (completion rate and average measured task duration).
- Added UTF-8 CSV export and atomic local persistence at `~/.ashkan-hotelsphere/housekeeping-workforce.tsv`.
- Added bilingual Swing workspace for assignment creation, automatic daily task generation, start/complete actions, productivity view and export.
- Added `HousekeepingWorkforceServiceSmokeTest` for duplicate protection, lifecycle, elapsed-time metrics, shift summary, persistence and CSV output.
- Automatic generation is a rule-based draft and does not auto-select staff or alter room-cleaning status. Assignments are stored separately from the legacy generic task list; synchronization with payroll/attendance and mobile staff notifications remain future integrations.
- Verification: Java 17 compilation and all 26 standalone `*SmokeTest` classes passed. The Swing interface was not manually exercised on Windows; SQLite JDBC runtime integration was not tested in this environment.

## Stage 45 — Housekeeping Quality Control & Inspection
- Added a room inspection checklist covering cleanliness, bathroom, amenities, linen and safety (each rated 1–5).
- Computes a 0–100 score; scores of 80+ are approved, lower scores require re-cleaning.
- Approved inspections set the room housekeeping state to CLEAN; failed inspections set it to DIRTY.
- Stores inspector, timestamp, notes and optional before/after photo file-path references in `~/.ashkan-hotelsphere/housekeeping-quality.tsv` using atomic replacement.
- Quality dashboard shows total inspections, approvals and re-clean requests; inspection history can be exported to CSV.
- Photo fields store references only; the application does not capture, copy, or upload image files in this stage. Inspections are local single-desktop records and do not yet enforce role-based inspector authorization.
- Added `HousekeepingQualityServiceSmokeTest` for scoring, approval/rework, persistence, CSV and input validation.

## Stage 46 — Guest Experience & Satisfaction

- Guest feedback surveys with 1–5 ratings for cleanliness, reception, restaurant, facilities and overall satisfaction.
- NPS collection (0–10), CSAT calculation, average overall score and basic sentiment classification.
- Guest complaint lifecycle: Open, In Progress, Resolved and Closed, with assignment and resolution notes.
- Category-level complaint counts and separate UTF-8 CSV exports for feedback and complaints.
- Persistent local data: `~/.ashkan-hotelsphere/customer-experience.tsv`.

### Service-oriented architecture / optional microservice

The customer-experience bounded context is isolated behind `CustomerExperienceService`. An optional independently hosted JDK HTTP adapter is included as `CustomerExperienceHttpService`; it exposes versioned endpoints and can run separately from the Swing desktop app:

```bash
java -cp out com.ashkan.hotel.CustomerExperienceHttpService 8088
```

Endpoints:
- `GET /health`
- `GET /api/v1/metrics`
- `POST /api/v1/feedback` (form URL-encoded: `bookingId`, `guestName`, `cleanliness`, `reception`, `restaurant`, `facilities`, `overall`, `nps`, optional `comment`)
- `POST /api/v1/complaints` (form URL-encoded: `bookingId`, `guestName`, `category`, `subject`, optional `description`, `assignedTo`)

The HTTP adapter binds to loopback (`127.0.0.1`) by default. The desktop application currently uses the same domain service locally for offline operation; network-based service discovery, authentication between services, TLS, centralized database, retries and event messaging are future production-hardening steps. This is an incremental microservice-ready boundary, not a claim that every existing hotel module has already been split into distributed services.

## Stage 47 — Notification & Communication microservice

- Added a durable notification outbox for EMAIL and SMS channels with recipient validation, queued/sent/failed states, attempt counters, timestamps and last-error tracking.
- Added bilingual message templates for booking confirmation, payment receipt, check-in reminders and complaint updates.
- Added bounded exponential retry scheduling (1, 2 and 4 minute backoff; four attempts maximum) and a pluggable `DeliveryGateway` contract.
- Added a safe development `PreviewGateway` that writes message previews to `~/.ashkan-hotelsphere/notification-preview.log`; it does **not** send real email or SMS. A production SMTP/SMS provider adapter and credentials are intentionally not bundled.
- Added independently hosted `NotificationHttpService` with `/health`, `GET /api/v1/metrics`, `POST /api/v1/notifications` (form URL-encoded `channel`, `recipient`, `subject`, `body`) and `POST /api/v1/notifications/process`.
- Added bilingual Swing notification center with queue, status/attempt visibility, manual queue processing and CSV export. Processing uses preview mode.
- Persistent outbox: `~/.ashkan-hotelsphere/notification-outbox.tsv`; preview delivery log: `~/.ashkan-hotelsphere/notification-preview.log`.
- Run standalone with `java -cp out com.ashkan.hotel.NotificationHttpService 8089`.
- The HTTP adapter binds to loopback only. This stage does not include RabbitMQ; the outbox and gateway interface establish a replaceable boundary before adding a broker and production provider credentials.

## Stage 48 — Event-driven foundation, API Gateway & RabbitMQ

- Added `HotelEventBus`, a durable local event journal with publish/dispatch, acknowledgement, bounded retries, dead-letter state, persistence and CSV export. This is the desktop/development fallback and provides at-least-once handler invocation; consumers should be idempotent.
- Added `HotelApiGateway`, an HTTP edge router with `/health` and configurable prefix routing. The default standalone configuration forwards `/notifications/*` to the notification service on port 8089 and `/customer-experience/*` to the customer-experience service on port 8088.
- Added `infra/docker-compose.yml` to run RabbitMQ 3.13 with its management UI and a persistent volume. Start it from the project root with `docker compose -f infra/docker-compose.yml up -d`; AMQP is on port 5672 and the management UI is on port 15672. The sample credentials are for local development only and must be changed before any shared or production deployment.
- Run the gateway with `java -cp out com.ashkan.hotel.HotelApiGateway 8080` after starting the upstream HTTP services.
- The current event journal is file-backed and is not yet an AMQP/RabbitMQ publisher or consumer. RabbitMQ is provisioned as infrastructure for the next integration stage; do not interpret the local journal as a distributed broker.
- Gateway routes are loopback-only by default. Production deployment still requires authentication/authorization, TLS, secrets management, rate limiting, observability and network policy.


## Stage 49 — RabbitMQ AMQP integration & distributed notification consumer

- Added the official RabbitMQ Java client dependency (`com.rabbitmq:amqp-client:5.22.0`). The transport uses AMQP 0-9-1, durable topic/direct exchanges, durable queues, persistent message properties, publisher confirms, manual consumer acknowledgements, QoS prefetch and automatic connection recovery.
- Added `RabbitMqAmqpTransport`, with environment-driven connection configuration (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `RABBITMQ_EXCHANGE`, `RABBITMQ_QUEUE`, `RABBITMQ_DLX`, `RABBITMQ_DLQ`). Reflection is used only to keep plain-JDK source compilation possible when the Maven client dependency is not present; runtime AMQP requires the Maven dependency.
- Topology: durable topic exchange `ashkan.hotel.events`, durable queue `ashkan.hotel.notifications` bound with `#`, dead-letter exchange `ashkan.hotel.events.dlx`, and dead-letter queue `ashkan.hotel.notifications.dead`. Handler failures are negatively acknowledged without requeue and routed to the DLQ.
- Added `EventIdempotencyStore`, a durable consumer inbox. Successfully handled message IDs are persisted before ACK; duplicate deliveries are ACKed without invoking the handler again. Notification outbox IDs are deterministic per source event, protecting against duplicate notification creation if a process stops between outbox persistence and inbox persistence.
- Added `NotificationEventConsumer` mappings for `booking.confirmed`, `payment.received`, `booking.checkin-reminder`, and `complaint.updated`. Event payload is URL-encoded key/value data with `channel`, `recipient`, `guestName`, and optional `bookingId` and `language`.
- Added standalone processes: `RabbitMqNotificationWorker` consumes events and places notifications into the existing outbox; `RabbitMqEventPublisher` publishes domain events. The existing preview delivery adapter remains safe development mode and does not send real email/SMS.
- Start broker: `docker compose -f infra/docker-compose.yml up -d`. Configure credentials through environment variables or copy `.env.example` to `.env` and change the sample password before use.
- Build with Maven: `mvn -q -DskipTests package`. Run worker: `mvn -q exec:java -Dexec.mainClass=com.ashkan.hotel.RabbitMqNotificationWorker`. Publish example: `mvn -q exec:java -Dexec.mainClass=com.ashkan.hotel.RabbitMqEventPublisher -Dexec.args='booking.confirmed BK-1 channel=EMAIL%26recipient=guest%40example.com%26guestName=Alex%26bookingId=BK-1%26language=en'`.
- Broker integration was not exercised against a live RabbitMQ container in this environment; no Docker daemon or Maven executable was available. The transport sources were compiled with Java 17 (the Rabbit client is loaded at runtime), and deterministic consumer/idempotency tests were run. Configure TLS, secrets management, authentication/authorization, metrics and network policies before production use.

## Stage 50 — Independently deployable domain microservices & Docker Compose

- Added standalone Booking, Payment, Guest and Inventory service entry points. Each service owns a separate HTTP port, resource collection, local durable data file and Docker named volume; the existing Swing application remains the desktop administration client.
- Each domain service exposes `GET /health`, `GET /metrics`, `GET /api/v1/{resource}`, `POST /api/v1/{resource}`, `GET /api/v1/{resource}/{id}` and `DELETE /api/v1/{resource}/{id}`. Request bodies are retained as opaque JSON strings; this is an integration scaffold, not yet a complete domain validation or accounting implementation.
- Added a reusable JDK `HttpServer` runtime with UTF-8 responses, bounded request bodies, atomic file replacement and restart persistence. The service binds to all container interfaces; production deployment must add authentication, authorization, TLS, request validation, rate limiting and audit logging.
- When `EVENTS_ENABLED=true`, successful create operations publish domain events through the Stage 49 RabbitMQ AMQP transport. Publication failures are logged; they do not roll back the persisted entity. A transactional outbox is a required future hardening step before relying on cross-service consistency.
- Added `infra/Dockerfile.service` and expanded `infra/docker-compose.yml` to build and run RabbitMQ, Booking, Payment, Guest, Inventory and Notification services with separate data volumes. Sample credentials are for local development only.
- Start from the project root: copy `.env.example` to `.env`, replace the RabbitMQ password, then run `docker compose -f infra/docker-compose.yml up --build -d`.
- Service ports: Booking 8091, Payment 8092, Guest 8093, Inventory 8094, Notification 8089; RabbitMQ AMQP 5672 and management UI 15672.
- The domain service stores are independent file-backed repositories for this stage, not separate production-grade database servers. The Swing desktop UI has not yet been migrated to call these remote APIs; the current stage establishes deployable service boundaries and endpoints.
- Verification: Java 17 source compilation and standalone smoke tests are run as part of this stage. Docker image builds and live broker integration require Docker/Maven and must be verified on the target machine.

## Stage 51 — Authenticated API gateway, service discovery & resilience

- Added `HotelEdgeGateway` as the authenticated entry point for Booking, Payment, Guest and Inventory APIs.
- Added HS256 JWT issuance and signature/expiry validation using JDK cryptography; JWT secrets must be supplied through `JWT_SECRET` and must contain at least 32 UTF-8 bytes.
- Added `/auth/login` and Bearer-token protected API routes: `/api/bookings`, `/api/payments`, `/api/guests`, and `/api/inventory`.
- Added role enforcement: Receptionist and Admin can access normal API routes; DELETE operations and `/admin/services` require Admin.
- Added `ServiceRegistry` with configurable service endpoints and live `/health` checks. The Docker deployment resolves services by their Compose DNS names.
- Added per-upstream circuit breaking after repeated failures, request timeouts, payload size limits, and forwarding of authenticated identity/role headers.
- Added a dedicated `api-gateway` Compose service. Domain service host ports are bound to loopback so normal clients use the gateway rather than bypassing it.
- Added JWT and end-to-end gateway smoke tests covering token tampering, anonymous access, successful login/proxy, and role restrictions.

### Stage 51 startup

1. Copy `.env.example` to `.env` and replace `JWT_SECRET` with a cryptographically random secret of at least 32 characters. Change the RabbitMQ credentials as well.
2. Start the stack with `docker compose -f infra/docker-compose.yml up --build -d`.
3. Check `http://localhost:8080/health`.
4. First-run demo accounts are `Admin` / `admin123` and `Receptionist` / `desk123`. They are only for local demonstration: change/remove them before real use and protect the persisted `/app/data/auth` volume. Passwords are hashed by the existing AuthService.
5. Login by POSTing JSON `{"username":"Admin","password":"..."}` to `/auth/login`; send the returned token as `Authorization: Bearer <token>` to `/api/bookings`, `/api/payments`, `/api/guests`, or `/api/inventory`.
6. `GET /admin/services` returns service health and requires an Admin token.

The current registry uses static Compose DNS registration plus active health checks; it is not a dynamic registry such as Consul. JWT revocation/refresh, TLS termination, distributed rate limiting, secrets management, and downstream service-to-service token validation are not yet implemented. The four domain services still use their Stage 50 isolated file stores; this stage does not claim production-grade distributed identity or persistence.

### Stage 51 verification

- Java 17 compilation of all main and test source files succeeded using `javac --release 17`.
- All 36 standalone `*SmokeTest` programs passed, including the new JWT and authenticated gateway end-to-end tests.
- Docker Compose deployment and live RabbitMQ/container networking were not executed in this build environment; validate them on a machine with Docker Desktop and Maven/network access.

## Stage 52 — PostgreSQL per microservice

The four bounded-context services can now run with isolated PostgreSQL databases:

- `ashkan_booking` — Booking Service
- `ashkan_payment` — Payment Service
- `ashkan_guest` — Guest Service
- `ashkan_inventory` — Inventory Service

For local development, the databases share one PostgreSQL 16 server/container but remain separate logical databases with separate JDBC URLs. This is a practical local topology; production can move each database to its own managed PostgreSQL instance.

### Persistence stack

- PostgreSQL JDBC driver
- HikariCP connection pooling (loaded when available on the runtime classpath)
- Flyway versioned migrations under `src/main/resources/db/migration`
- A bounded JDBC fallback pool for plain-JDK smoke-test environments
- A one-time import of existing Base64 TSV records when a service database is empty

When `JDBC_URL` is configured, the service uses PostgreSQL. If it is absent, the previous file-based persistence remains available for lightweight development and smoke tests. Database health is reflected by the service `/health` endpoint.

### Start locally

1. Copy `.env.example` to `.env` and set strong values for `DB_PASSWORD`, `RABBITMQ_PASSWORD`, and `JWT_SECRET`.
2. Run `docker compose -f infra/docker-compose.yml up --build -d`.
3. Check `http://localhost:8091/health` through `http://localhost:8094/health` and `http://localhost:8080/health`.

The PostgreSQL initialization script creates the four logical databases. Flyway applies `V1__create_service_records.sql` independently in each database when its service starts.

### Data and transaction boundaries

Each service owns its database and does not share tables with other services. The current generic `service_records` table preserves the existing API payload contract; richer domain-specific schemas and cross-service workflows will be introduced incrementally. There are no distributed ACID transactions: cross-service consistency should use events/Saga patterns.

### Security note

The `.env.example` credentials are placeholders for local development only. Rotate all secrets before deployment, restrict database network access, and configure TLS, backups, monitoring, and least-privilege database users for production.


## Stage 53 — Domain-driven data model and repositories

This stage introduces service-owned PostgreSQL schemas and JDBC repositories while preserving the generic v1 record API and file-backed fallback:

- **Booking:** validated date range, lifecycle status, currency/amount checks, soft deletion, guest/date/status indexes.
- **Payment:** positive amount, supported transaction states, unique provider reference, booking/status indexes.
- **Guest:** validated profile data, active-email uniqueness, guest history timeline, soft deletion.
- **Inventory:** SKU uniqueness, non-negative stock/reorder thresholds, stock movement ledger, atomic quantity adjustment that prevents negative stock.

Flyway now applies the common migration and the matching service migration directory (`db/migration/common` and `db/migration/<service>`). The repositories are available as `BookingRepository`, `PaymentRepository`, `GuestRepository`, and `InventoryRepository`. They use prepared statements and the shared bounded JDBC/Hikari connection pool. Domain repository operations are a Java persistence layer in this stage; the existing HTTP v1 endpoints remain backward-compatible and the dedicated v2 HTTP resource endpoints are a planned next increment.

Database migrations and JDBC repository runtime require PostgreSQL and the Maven runtime dependencies. Local validation covers Java compilation and domain validation smoke tests; it does not substitute for a live PostgreSQL/Flyway integration run.

## Stage 54 — Domain REST API v2

Each PostgreSQL-backed microservice now exposes a domain endpoint alongside the compatible `/api/v1` generic endpoint:

| Service | Collection endpoint | Domain fields |
|---|---|---|
| Booking | `/api/v2/bookings` | `guestId`, `roomId`, `checkIn`, `checkOut`, `status`, `totalAmount`, `currency` |
| Guest | `/api/v2/guests` | `firstName`, `lastName`, `email`, `phone`, `nationality` |
| Payment | `/api/v2/payments` | `bookingId`, `amount`, `currency`, `method`, `status`, `providerReference` |
| Inventory | `/api/v2/inventory` | `sku`, `name`, `unit`, `quantity`, `reorderLevel` |

Supported operations: `POST` collection, `GET` collection with `page` and `pageSize` (maximum 100), `GET` by ID, `PUT` by ID, and `DELETE` by ID. Collection filtering uses `status` for booking/payment and `search` for guest/inventory. Booking, guest and inventory deletion is soft-delete; payment deletion is physical because the Stage 53 payment schema has no deletion timestamp. Invalid JSON/domain values return HTTP 400; unknown records return 404. The API requires the service's PostgreSQL `JDBC_URL`; when not configured, `/api/v2` returns HTTP 503 while `/api/v1` continues to operate.

Example booking request:

```http
POST /api/v2/bookings
Content-Type: application/json

{"id":"booking-1001","guestId":"guest-100","roomId":"room-204","checkIn":"2026-11-10","checkOut":"2026-11-13","status":"CONFIRMED","totalAmount":420.00,"currency":"EUR"}
```

Example listing: `GET /api/v2/bookings?page=1&pageSize=20&status=CONFIRMED`.

## Stage 55 – API security and production hardening

- The edge gateway now exposes authenticated v2 aliases for Booking, Guest, Payment and Inventory domain APIs while retaining the existing v1 routes.
- Central role policy: Admin can perform supported CRUD operations; Receptionist can read resources and create bookings/guest profiles, but cannot mutate payments or inventory.
- Login throttling is limited to 5 attempts per client IP per 60 seconds; API traffic is limited to 120 requests per client IP per 60 seconds. Limits are in-memory per gateway instance and should be complemented by a shared reverse-proxy/WAF limiter in multi-replica deployments.
- Security response headers include `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Content-Security-Policy`, `Cache-Control`, and a validated/generated `X-Request-Id` correlation header.
- `GET /openapi.json` publishes a compact OpenAPI 3.0 description for the v2 routes and Bearer JWT scheme.
- Added focused smoke tests for rate limiting, role permissions, v2 gateway routing and response security headers.

Security note: this is an application-level baseline, not a substitute for TLS termination, secret rotation, persistent/distributed rate limiting, centralized audit logging, dependency scanning, and penetration testing before production use.

## Stage 56 – Observability & Monitoring

- Dependency-free HTTP telemetry is exposed in Prometheus text format at `/metrics` on the edge gateway and each domain microservice.
- Metrics include request counters by service/method/normalized path/status, failed requests, cumulative request duration, and in-flight requests.
- HTTP filters add/propagate a trace identifier (`X-Trace-Id`) and accept valid W3C `traceparent` headers.
- JSON-line structured logs are emitted to stdout with timestamp, level, service, event, trace ID, and request attributes.
- Docker Compose provisions Prometheus on `http://localhost:9090` and Grafana on `http://localhost:3000`; Grafana datasource and the HotelSphere overview dashboard are provisioned automatically.
- Prometheus scrapes the gateway and Booking, Payment, Guest, and Inventory services every 15 seconds; retention is configured to 15 days.

### Monitoring setup

1. Copy `.env.example` to `.env` and set strong values for `JWT_SECRET`, database, RabbitMQ, and Grafana credentials.
2. Start the stack from `infra` with `docker compose up -d --build`.
3. Open Grafana at `http://localhost:3000`, sign in using `GRAFANA_USER` / `GRAFANA_PASSWORD`, and select the provisioned **Ashkan HotelSphere - Microservices Overview** dashboard.
4. Check Prometheus targets at `http://localhost:9090/targets` and individual metrics at `http://localhost:8080/metrics` or the service ports.

Telemetry is intentionally lightweight and in-process. It is not a replacement for a full OpenTelemetry Collector/tracing backend; trace IDs are propagated, but distributed spans and histogram buckets are not yet implemented. Metrics labels normalize numeric/UUID path segments to reduce cardinality. Protect monitoring endpoints with network policy or an authenticated reverse proxy before exposing them outside a trusted network.


## Stage 57 — Distributed tracing with W3C Trace Context and OTLP

- HTTP server filters create server spans and preserve W3C `traceparent` trace IDs while generating a new span ID per service hop.
- The edge gateway injects its active span context into downstream service requests.
- Completed spans are exported asynchronously as OTLP/HTTP JSON when `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` is configured.
- Docker Compose includes an OpenTelemetry Collector on `127.0.0.1:4318` (HTTP) and `127.0.0.1:4317` (gRPC); its current debug exporter writes received spans to collector logs.
- Set `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces` for Java services. This is configured by Compose.

To inspect spans locally: `docker compose logs -f otel-collector`. Trace export is best-effort and does not block HTTP responses. A persistent trace backend (Jaeger/Tempo) can be added as a separate deployment stage.

## Stage 58 — Jaeger trace storage and visualization

- Docker Compose provisions Jaeger all-in-one (`jaegertracing/all-in-one:1.62.0`) with its UI bound to `127.0.0.1:16686`.
- The OpenTelemetry Collector now exports received OTLP traces to Jaeger over OTLP/gRPC (`jaeger:4317`) and retains the debug exporter for local diagnostics.
- Grafana automatically provisions a Jaeger datasource (`uid: jaeger`) alongside Prometheus.
- Open the Jaeger UI at `http://localhost:16686`, select a service such as `booking`, `payment`, `guest`, `inventory`, or `api-gateway`, and search for traces. Follow the trace ID from the gateway span to downstream service spans.
- Grafana Explore can use the provisioned Jaeger datasource for trace lookup and correlation with Prometheus metrics.

### Trace backend startup and verification

Run `docker compose up -d --build` from `infra`, then check `docker compose ps` and `docker compose logs -f otel-collector jaeger`. Generate traffic through the gateway and search in Jaeger. The Jaeger all-in-one deployment uses in-memory span storage for local development; traces are lost when its container restarts. Configure a supported persistent storage backend and authentication/TLS before production use. The Jaeger UI is bound to localhost and should not be exposed directly to the public internet.


## Stage 59 – Performance Engineering

- Added a bounded, thread-safe TTL read-through cache to the domain repositories for booking, guest, payment and inventory lookups. Cache entries are invalidated after writes, deletes and stock movements.
- Cache capacity and TTL are configurable with `HOTEL_CACHE_MAX_ENTRIES` (default `1000`) and `HOTEL_CACHE_TTL_SECONDS` (default `30`).
- Prometheus `/metrics` now exposes cache hits, misses, evictions and hit ratio.
- Added service-specific PostgreSQL V3 indexes for common booking, guest, payment and inventory query patterns.
- Added `infra/k6/hotel-smoke.js` for a repeatable gateway health load smoke test. Example: `k6 run -e BASE_URL=http://localhost:8080 -e VUS=10 -e DURATION=30s infra/k6/hotel-smoke.js`.

Cache is process-local; multi-instance deployments should use Redis or another shared cache and distributed invalidation. The k6 script is provided but requires a running Docker stack and an installed k6 binary to execute.

## Stage 60 – Redis Distributed Cache

- Added a dependency-free Redis RESP2 client with bounded socket connect/read timeouts, optional AUTH and logical database selection.
- Booking, Guest, Payment and Inventory repositories now use Redis as a shared second-level cache after their local TTL cache. Cache entries are namespaced by bounded context and use a null-safe Base64 field codec.
- Successful writes, soft deletes and inventory stock movements invalidate the corresponding Redis key. Redis failures are fail-open: repository reads continue to PostgreSQL rather than making Redis a hard dependency for correctness.
- Docker Compose provisions Redis 7 with AOF persistence, password protection, health checks and a named volume. Services wait for Redis health before startup.
- Added Prometheus counters for Redis hits, misses and fail-open errors.

Configure `REDIS_PASSWORD`, `REDIS_CACHE_ENABLED` and `REDIS_TIMEOUT_MS` in `.env`. Set `REDIS_CACHE_ENABLED=false` to disable distributed caching. Redis is an optimization only; PostgreSQL remains the source of truth.

## Stage 61 — Concurrency Safety & Idempotency

- Booking writes acquire a short Redis lease per room and then take a PostgreSQL transaction-scoped advisory lock. PostgreSQL also enforces a GiST exclusion constraint over half-open stay ranges, preventing overlapping active reservations even when Redis is unavailable.
- Inventory movements use an atomic conditional stock update, increment an optimistic version counter, and write the movement in the same database transaction. `recordMovementOnce(...)` accepts an idempotency key so retries cannot apply the same stock movement twice.
- Domain API v2 accepts an optional `Idempotency-Key` header on `POST`. The key is scoped to the microservice and stored with a SHA-256 request fingerprint and stable resource ID for 24 hours. Replays return the same resource; reusing a key with a different body returns HTTP 409.
- Redis leases use `SET NX PX` and token-checked Lua release. Redis is a coordination optimization, not the source of correctness: PostgreSQL locks, constraints and atomic updates remain authoritative.
- New Prometheus counters expose lock attempts/acquisitions, idempotency replays/conflicts and duplicate stock movement requests.

Example:

```http
POST /api/v2/bookings
Idempotency-Key: booking-request-2026-0001
Content-Type: application/json
```

The booking migration installs PostgreSQL `btree_gist` for the exclusion constraint. The configured Docker PostgreSQL bootstrap user is the database administrator for this local compose environment; managed database deployments must pre-install the extension or grant the required extension privilege before running migrations.

Validation in the build environment: Java 17 compilation and 52 dependency-free smoke tests passed. Live PostgreSQL/Redis contention tests and multi-instance load tests require Docker services and were not run here.

## Stage 62 — Commercial Booking Engine, Room Catalog & Dynamic Pricing

The Booking service now exposes commercial PMS endpoints in addition to the existing booking CRUD API. The booking database migration `booking/V5__room_catalog_and_rate_plans.sql` creates a room catalog (`hotel_rooms`) and configurable rate plans (`rate_plans`) with validation constraints and lookup indexes.

### New authenticated API routes (via API Gateway)

| Method | Route | Purpose |
|---|---|---|
| `GET` / `POST` | `/api/v2/rooms` | List rooms (optional `roomType` filter) / create or update a room |
| `GET` / `POST` | `/api/v2/rate-plans` | List rate plans / create or update a rate plan |
| `GET` | `/api/v2/availability?roomType=DELUXE&checkIn=2026-11-10&checkOut=2026-11-13&guests=2` | Check available room count for a date range |
| `POST` | `/api/v2/quotes` | Calculate a detailed multi-night price quote |

Room payload example:

```json
{"id":"room-204","roomType":"DELUXE","roomNumber":"204","floor":2,"capacity":2,"status":"ACTIVE","amenities":["WiFi","TV","Mini Bar"]}
```

Rate plan payload example:

```json
{"id":"deluxe-flex","name":"Deluxe Flexible","roomType":"DELUXE","currency":"EUR","baseRate":120.00,"weekendMultiplier":1.20,"highDemandMultiplier":1.30,"maxOccupancy":2,"active":true,"validFrom":"2026-01-01","validTo":"2027-12-31"}
```

Quote payload example:

```json
{"ratePlanId":"deluxe-flex","checkIn":"2026-11-10","checkOut":"2026-11-13","guests":2}
```

Pricing is deterministic and explainable: Friday/Saturday use the configured weekend multiplier; demand multiplier is applied when current room-type occupancy reaches 80%. The quote response itemizes each night and returns the availability snapshot. Currency is not converted and taxes/fees are not added in this stage. Availability is a snapshot, not a reservation hold; the existing PostgreSQL overlap constraint remains the final booking guard. Receptionists can read room/rate data and request quotes; room and rate-plan writes remain Admin-only.

Stage 62 validation: Java 17 compilation and the smoke suite are run separately; PostgreSQL migration execution and live HTTP/Docker testing require a local Docker/PostgreSQL environment.

## Stage 63 – Front Desk Workflow & Reservation Calendar

Stage 63 adds transactional front-desk operations to the booking service:

- `GET /api/v2/booking-operations?from=YYYY-MM-DD&to=YYYY-MM-DD&roomType=...` returns reservation calendar entries overlapping the requested interval.
- `POST /api/v2/booking-operations/{bookingId}/check-in` performs a row-locked `CONFIRMED -> CHECKED_IN` transition and records status history.
- `POST /api/v2/booking-operations/{bookingId}/check-out` performs a row-locked `CHECKED_IN -> CHECKED_OUT` transition and records status history.
- `POST /api/v2/booking-operations/{bookingId}/cancel` cancels eligible `PENDING` or `CONFIRMED` bookings and returns the configured cancellation fee. The default policy is free cancellation at least 24 hours before arrival and 50% thereafter; configure the `cancellation_policy` row in the booking database to change it.
- `PATCH /api/v2/booking-operations/{bookingId}/room` reassigns a pending/confirmed booking after validating target room status and availability.

Migration `booking/V6__front_desk_workflow.sql` creates cancellation policy and booking status history tables. Booking cache entries are invalidated after lifecycle and room-assignment changes. Receptionist role is allowed to perform front-desk actions and room reassignment; destructive deletion remains Admin-only.

The Swing desktop application also includes a Persian/English monthly reservation calendar with stay, arrival, and departure counts. Existing check-in/check-out/cancel actions remain available from the booking screen.

**Verification:** Java 17 compilation and 56 smoke tests pass. Live PostgreSQL/Redis/Docker integration and manual Swing GUI verification are not performed in this build environment.

## Stage 64 – Guest Self-Service Booking Portal

Stage 64 adds a bilingual Persian (default RTL) / English (LTR) guest-facing web portal as an independently deployable service.

### Run with Docker Compose

1. Configure `.env` with strong database, Redis, RabbitMQ and gateway secrets (the portal itself uses the PostgreSQL credentials).
2. From `infra/`, run `docker compose up -d --build`.
3. Open `http://localhost:8085`.

The portal service uses the existing booking and guest databases and exposes a same-origin web UI. Its public API is:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/portal/catalog` | Active rooms and rate plans |
| GET | `/api/portal/availability?roomType=...&checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD&guests=2` | Availability lookup |
| POST | `/api/portal/quote` | Calculate a date-specific quote |
| POST | `/api/portal/bookings` | Create a pending booking request (supports `Idempotency-Key`) |
| POST | `/api/portal/manage` | Retrieve a booking after reference + guest email verification |
| POST | `/api/portal/modify` | Modify dates for an eligible booking |
| POST | `/api/portal/cancel` | Apply the configured cancellation policy |

The portal creates or reuses a guest profile, assigns an available room using the transactional booking repository, and writes guest history events. If booking creation fails after a new guest profile is created, the portal attempts a compensating soft-delete. Booking requests are marked `PENDING`; no card details are collected and payment is not processed by this portal.

### Security and production notes

- Public requests are rate-limited per source IP (in-memory, 30 requests/minute) and response headers include a restrictive same-origin Content Security Policy.
- Booking lookup currently uses booking reference + email as a lightweight ownership check. This is not equivalent to a verified customer account or email OTP; enable an OTP/verified-account flow before public production use.
- Put the portal behind HTTPS and a trusted reverse proxy, configure stricter edge rate limits/WAF, and avoid exposing internal service ports publicly.
- The portal uses separate JDBC pools for booking and guest databases. Cross-database operations are a small saga with compensation, not a distributed ACID transaction.
- Live PostgreSQL/Docker deployment and browser-based end-to-end testing were not run in the build environment.

## Stage 65 — Guest identity and self-service sessions

The guest portal now supports email one-time-password authentication and an authenticated guest dashboard:

- `POST /api/portal/auth/request-otp` — request a six-digit code for an existing guest email (generic response prevents account enumeration).
- `POST /api/portal/auth/verify-otp` — verify the code and receive an opaque `HttpOnly`, `SameSite=Strict` session cookie.
- `GET /api/portal/me` — return the authenticated guest profile and up to 100 recent bookings.
- `POST /api/portal/auth/logout` — revoke the current node-local session.
- Existing booking lookup, cancellation and modification endpoints accept the authenticated session and enforce guest ownership; the legacy booking-reference/email flow remains supported for compatibility.

OTP codes expire after five minutes and are limited to five verification attempts. Session lifetime is eight hours. `GUEST_OTP_SECRET` is mandatory (minimum 32 characters). For local development, OTPs are logged to the guest-portal container logs; this is **not email delivery**. Configure `OTP_DELIVERY_WEBHOOK_URL` and `OTP_DELIVERY_WEBHOOK_TOKEN` to call a trusted HTTPS delivery adapter that accepts `{to, template, code, expiresInMinutes}`. Set `PORTAL_COOKIE_SECURE=true` behind HTTPS in production. The included Compose defaults are local-development-only and must be replaced before deployment.

Sessions and OTP challenges are currently node-local/in-memory; use a shared session/challenge store before running multiple portal replicas. This stage does not claim a live email provider, payment gateway, card processing, refund settlement, or PDF confirmation integration. No card data is collected. Payment provider integration remains a separate, explicitly configured step.

## Stage 66 — Payment workflow, refund ledger and booking PDF

- Introduces a provider-neutral `PaymentGateway` boundary and an explicitly simulated `DemoPaymentGateway`. Demo authorization returns `AUTHORIZED` and demo refunds return `SIMULATED`; no funds move and no card data is accepted or stored.
- Adds `PaymentWorkflowService` with booking-state checks, payment idempotency, refund amount validation, and refund replay protection.
- Adds the payment database migration `payment/V4__refund_ledger.sql` for an append-only refund ledger with unique idempotency keys.
- Guest portal endpoints (authenticated guest session required): `POST /api/portal/payments/demo` with `bookingId` and `Idempotency-Key`; `POST /api/portal/payments/{paymentId}/refund-demo` with `amount` and `Idempotency-Key`.
- Adds `GET /api/portal/bookings/{bookingId}/confirmation.pdf`, authorized against the guest session or the existing verified email flow. The PDF uses built-in Helvetica and English labels for broad compatibility; non-ASCII guest names are replaced in the PDF output.
- Configure `PAYMENT_JDBC_URL` for the isolated `ashkan_payment` database. Apply the payment Flyway migrations before using payment/refund endpoints.
- A real provider adapter (e.g. Adyen/Stripe) still requires merchant onboarding, signed webhook verification, and deployment secrets. The included demo adapter is not a production payment integration.


## Stage 67 — Signed payment webhooks & financial audit

- Added an HMAC-SHA256 payment webhook endpoint: `POST /api/portal/webhooks/payment`.
- Signature headers: `X-Payment-Timestamp` (Unix seconds) and `X-Payment-Signature` (hex HMAC). The signed message is `timestamp + "." + exactRawRequestBody`; maximum clock skew is five minutes.
- Configure `PAYMENT_WEBHOOK_SECRET` with a unique random value of at least 32 characters. Never use the sample value in production.
- The webhook validates event ID, payment ID, amount, currency and allowed state transitions. Provider events are stored in a unique inbox table to make duplicate deliveries idempotent.
- Added append-only `financial_audit_events` and payment webhook inbox tables in payment Flyway migration V5.
- Added payment status cache invalidation when a verified provider event updates a transaction.
- Webhook payload example: `{"eventId":"evt_123","paymentId":"PAY-...","provider":"provider-name","status":"CAPTURED","amount":120.00,"currency":"EUR","providerReference":"txn_123"}`.
- This is a provider-neutral callback contract, not a live bank/acquirer integration. A real provider adapter, provider-specific event mapping, secret rotation and reconciliation are still required. Webhook endpoint must be exposed only behind HTTPS and provider IP/network controls where available.
- Financial audit records are append-only by application convention; production deployments should also restrict DB UPDATE/DELETE permissions on these tables and protect audit exports.

## Stage 68 — Financial Management & Business Intelligence

- Added `FinancialAnalyticsService` for inclusive date-range reporting with strict per-currency aggregation. Captured payments are counted as income; authorizations and failed transactions are not treated as cash received.
- Successful refunds are deducted; pending/failed/simulated refunds are excluded. Expenses are deducted by currency, with a configurable **estimated** tax percentage and net-after-estimate output. Tax output is an estimate only, not tax/legal advice or a jurisdiction-specific tax calculation.
- Added daily totals, transaction counts, Excel-friendly UTF-8-BOM CSV export, and provider-settlement reconciliation (`MATCHED`, `LEDGER_ONLY`, `PROVIDER_ONLY`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`). Provider references are used when present; transaction ID is the fallback key.
- Added `FinancialReportingRepository` for date-bounded payment/refund/expense reads and idempotent expense creation.
- Added payment migration `V6__financial_expenses.sql` with positive-amount, ISO currency, and date indexes.
- Tests cover currency separation, date filtering, captured-only income, successful refunds, expenses, tax estimate, reconciliation and schema contracts.

**Operational notes:** This stage supplies the tested reporting and persistence layer; it does not yet claim a live dashboard against a running PostgreSQL instance or a live acquirer settlement feed. Expense entry and reconciliation import need to be wired to an authenticated operator workflow/provider-specific file adapter before production use. The neutral reconciliation model intentionally does not guess provider CSV formats.


## Stage 69 — Admin Financial Dashboard & Accounting Integration

The separate `finance-dashboard` service exposes an admin-only financial workspace on `http://localhost:8096/admin/finance` (Docker Compose binds it to loopback). Sign in by pasting a valid Admin JWT issued by the existing edge gateway; the dashboard does not issue credentials or persist the token. It requests date-bounded financial data from the payment PostgreSQL database and supports currency-separated KPI cards, daily captured-revenue bars, daily activity, configurable estimated-tax display, and Excel-friendly CSV export.

Admin API routes:
- `GET /admin/finance/api/report?from=YYYY-MM-DD&through=YYYY-MM-DD&taxRate=0..100` — JSON report.
- `GET /admin/finance/export.csv?from=YYYY-MM-DD&through=YYYY-MM-DD&taxRate=0..100` — UTF-8 BOM CSV.
- `POST /admin/finance/api/expenses?from=YYYY-MM-DD&through=YYYY-MM-DD` — register an expense using JSON fields `id`, `date`, `amount`, `currency`, and `category`; requires Admin JWT.
- `GET /health` — process health.

The dashboard requires `PAYMENT_JDBC_URL` and the same `JWT_SECRET` as the edge gateway. The payment service runs the payment Flyway migrations before the dashboard connects. Never use the example secrets in production; expose the dashboard only through a trusted network or HTTPS reverse proxy. Tax is an estimate, not tax advice. The dashboard intentionally does not combine different currencies or count authorized/failed payments as captured revenue. Room/service profitability and general-ledger posting are not claimed in this stage because those source accounting dimensions are not yet available in the payment schema.
"# Ashkan-HotelSphere" 
