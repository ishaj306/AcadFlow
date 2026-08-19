# Smart Practical Batch & Timetable Generator

Practical batch allocation, laboratory timetable generation, conflict detection,
automatic rescheduling and faculty workload optimisation for a college.

Timetables are produced by a **deterministic constraint solver** (Google OR-Tools
CP-SAT), not by a language model. Hard constraints are structural and cannot be
traded away; soft constraints form a weighted objective.

---

## Architecture

```
frontend/   React 19 + TypeScript + Vite + Tailwind 4 + TanStack Query + Recharts   :5173
backend/    Spring Boot 3.5 (Java 21) + Spring Security/JWT + JPA + Flyway + H2     :8080
solver/     FastAPI + OR-Tools CP-SAT optimisation service                          :8090
```

The backend owns all persistence and business rules. The solver is stateless: it
receives a self-contained snapshot of the scheduling problem and returns
assignments. The frontend talks only to the backend.

**Database.** H2, file-backed at `backend/data/`, so the project runs with no
database install. Migrations and JPA mappings are written portably — switching to
PostgreSQL means activating the `postgres` profile and pointing it at a server
(`backend/src/main/resources/application-postgres.yml`); no schema or code changes.

---

## Running it

Prerequisites: **JDK 21**, **Python 3.11+**, **Node 20+**. Maven is not required —
the repo bootstraps its own copy into `tools/` (already present after first setup).

Start the three services in separate terminals.

**1 — Optimisation service**

```bash
cd solver && python -m venv .venv && .venv/Scripts/python.exe -m pip install -r requirements.txt
```

```bash
cd solver && .venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

**2 — Backend API** (seeds a full demo dataset on first run)

```bash
cd backend && ../tools/apache-maven-3.9.16/bin/mvn.cmd spring-boot:run
```

**3 — Frontend**

```bash
cd frontend && npm install && npm run dev
```

Open <http://localhost:5173>.

---

## First run: the database is empty

Nothing is invented by the system. On an empty database the backend creates only
what is needed to sign in:

- the four roles (ADMIN, HOD, FACULTY, STUDENT)
- one administrator account

The password is printed **once** to the startup log:

```
================================================================
  Empty database detected - created the administrator account.

     username : admin
     password : S8gaxTBf99KBZYpb
```

Set your own instead of having one generated:

```bash
set BATCHMAKER_BOOTSTRAP_ADMIN_PASSWORD=your-password-here
```

Everything else — departments, terms, working hours, subjects, laboratories,
faculty, students — is entered by you.

### Entering your college

Sign in and the dashboard shows a **setup checklist** in dependency order. Each
step links to the right screen and states exactly what is missing.

1. **Department** — everything else belongs to one · *Settings*
2. **Academic term** — scheduling runs against the current term · *Settings*
3. **Working hours** — the periods of your day and which days you run · *Settings*
4. **Subjects** — duration, sessions/week, batch size, required lab type; one at
   a time or import a CSV/Excel file · *Subjects*
5. **Laboratories** — capacity and type; one at a time or import a CSV/Excel
   file · *Laboratories*
6. **Faculty** — plus the subjects each is qualified for; one at a time or import
   a CSV/Excel file · *Faculty*
7. **Students** — one at a time, or import a CSV/Excel file · *Students*
8. **Practical batches** — generated from divisions and capacities · *Practical Batches*
9. **Timetable** — generate, validate, approve, publish · *Timetable*

Two things are worth knowing while entering data:

- A subject's **required lab type** must match a laboratory's **type** exactly
  (both are free text with autocomplete from what already exists).
- A subject with **no qualified faculty** can never be scheduled. The Subjects
  screen shows that count in red when it is zero.

Faculty and students get sign-in accounts only when you explicitly create one
(the **Login** action on their row), so importing a roll list never silently
creates hundreds of credentials.

### Optional sample data

If you would rather try the system before entering real data,
`scripts/sample-data.ps1` builds a two-department college by calling the same
public API you would use by hand. It is not part of the application and creates
nothing you could not type in yourself.

```bash
powershell -File scripts/sample-data.ps1 -AdminPassword <your-admin-password>
```

It creates 2 departments, 1 term, 8 periods, Mon–Fri, 10 practical subjects,
8 laboratories, 18 faculty with qualifications, and 520 students via CSV import.
Delete `backend/data/` to get back to empty.

### End-to-end verification

`scripts/demo-scenario.ps1` drives the whole workflow against a running backend
and asserts the outcome of each step. It needs data to exist first.

```bash
powershell -File scripts/demo-scenario.ps1 -AdminPassword <your-admin-password> -SolveSeconds 60
```

It exits non-zero if any check fails.

---

## The optimisation engine

### Hard constraints — never violated

| | Constraint | How it is enforced |
|---|---|---|
| H1 | Faculty cannot teach two practicals at once | per-period mutual exclusion |
| H2 | A laboratory hosts one practical at a time | per-period mutual exclusion |
| H3 | A student cohort attends one practical at a time | per-period mutual exclusion over practicals sharing students |
| H4 | Batch size ≤ laboratory capacity | undersized labs are never enumerated |
| H5 | Faculty availability | blocked periods are never enumerated |
| H6 | Laboratory availability | blocked periods are never enumerated |
| H7 | Within configured working hours | only teachable, contiguous periods are enumerated |
| H8 | No practical on a declared holiday | holidays expand into blocked cells |
| H9 | Faculty on approved leave get no assignment | leave expands into blocked cells |

The model enumerates one boolean per **complete legal placement** — day, run of
consecutive periods, faculty, laboratory. Most hard constraints therefore hold by
construction: an illegal combination simply has no variable. What remains are the
three mutual-exclusion families, expressed as "at most one of these may occupy
this period".

### Soft constraints — optimised

S1 faculty preferences · S2 workload balance · S3 student gaps · S4 laboratory
utilisation · S5 faculty idle time · S6 minimal disruption when rescheduling.

Weights live in the `optimization_weights` table and are tunable without a
redeploy.

### Two-phase solve

A large soft objective can consume the whole time budget without producing any
feasible schedule. Generation therefore runs feasibility first on the bare
constraint model, then feeds that solution to the optimisation phase as a hint.
The first phase guarantees a usable answer; the second improves it.

### Measured result

On a college of 520 students, 18 faculty, 8 laboratories, 10 practical subjects
and 69 practical batches, with a 60-second budget:

```
schedule score        93.9 / 100
sessions scheduled    69
hard violations       0        (faculty 0, lab 0, student 0, capacity 0)
workload imbalance    120 min  (the theoretical optimum for 69 sessions / 18 staff)
student gap periods   0
workload spread       2.0h     "Good"
solve time            ~43 s
```

If the solver service is unreachable, the backend falls back to a built-in greedy
scheduler. It honours every hard constraint but does not optimise, and results are
clearly labelled so a coordinator knows the real engine did not run.

---

## Rescheduling

Triggered by faculty leave, laboratory maintenance, holidays or an administrative
decision. The engine searches every legal alternative against the **current
published timetable**, scores each by disruption — distance from the original
slot, change of faculty or laboratory, resulting workload, student gaps — and
presents a ranked shortlist with the reasoning shown:

```
Rank  Day        Time         Faculty              Laboratory          Score
1     WEDNESDAY  09:00-11:00  Dr. Kavita Rane      Networking Lab 1     91.8
2     THURSDAY   09:00-11:00  Dr. Kavita Rane      Networking Lab 1     87.8
...
-12 slot distance, -7 workload (44%), +8 preferred slot, +4 no student gaps
```

Nothing moves until a coordinator approves. Because a leave cancels a *date*
rather than a weekday, keeping the existing slot with an available substitute is
a valid — and usually the least disruptive — resolution, so it is offered.

Every proposal, decision and applied change is written to `reschedules` and the
audit log, and affected students, faculty and coordinators are notified.

---

## Validation and approval

Generation always produces a **draft**. Before anything can be published, the
`ConflictDetectionService` re-reads the persisted entries and re-checks every hard
constraint against the database — it does not trust the solver's own report.
Approval re-validates a second time, because data may have changed since
generation. Publication is refused while any hard violation remains.

---

## Project layout

```
backend/src/main/java/edu/batchmaker/
├── config/          typed configuration
├── controller/      REST endpoints (thin; no business logic)
├── domain/          JPA entities and enums
├── dto/             request/response records — entities are never exposed
├── exception/       error codes and the uniform error envelope
├── repository/      Spring Data repositories
├── security/        JWT, roles, filter chain
├── seed/            bootstrap only - roles and the first administrator
└── service/
    ├── solver/      snapshot assembly, solver client, fallback scheduler
    ├── BatchGenerationService, TimetableService, ConflictDetectionService
    ├── ReschedulingService, WorkloadService, DashboardService
    └── NotificationService, AuditService

solver/app/
├── models.py        request/response contract (Pydantic)
├── engine.py        CP-SAT model
└── main.py          FastAPI surface

frontend/src/
├── components/      shell, timetable grid, UI primitives
├── lib/             API client, auth, types
└── pages/           admin, faculty and student screens
```

Layering is strict: controller → service → repository. Controllers hold no
business logic and DTOs are used throughout, so entities never reach the wire.

---

## Import, export and testing

- **Bulk import (CSV and `.xlsx`).** Students, faculty, subjects and laboratories
  each have a downloadable template and accept both CSV and Excel uploads through
  one shared reader (`CsvSupport`). Every row is validated independently and the
  screen shows a preview of what was added, updated or rejected — bad rows are
  reported, never silently imported. The faculty template's optional
  `subject_codes` column maps qualifications inline.
- **Server-side export.** The Reports screen still exports the on-screen CSV, and
  additionally generates real `.xlsx` (Apache POI) and PDF (OpenPDF) files on the
  server from the live published timetable and the faculty-workload report
  (`GET /api/reports/{timetable,workload}/export?format=xlsx|pdf`).
- **Automated tests.** A JUnit suite covers the shared importer (CSV/Excel
  parsing, header validation, quoting, BOM) and server-side authorization
  (each role against protected endpoints) — 13 tests, run with `mvn test`. These
  complement `scripts/demo-scenario.ps1` (24 assertions over the full workflow)
  and `solver/smoke_test.py` (asserts every hard constraint holds on a solved
  schedule).

## Known gaps

Honest list of what is **not** implemented:

- **Server-side export is limited to two reports** (timetable and workload). The
  per-division / per-faculty / per-lab grids and lab-utilisation report export as
  CSV client-side but not yet as server `.xlsx`/`.pdf`.
- **Service-level test coverage is still thin.** Batch generation, conflict
  detection and rescheduling are exercised by the end-to-end scripts but do not
  yet have dedicated unit/integration tests.
- **Optional AI assistant** (natural-language timetable queries) is not built.
  The core system is deliberately solver-driven.
- Notifications are in-app only; email and messaging integrations are not built.
