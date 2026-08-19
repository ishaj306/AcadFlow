# BatchMaker — Complete Project Guide

*Smart Practical Batch & Timetable Generator — a college scheduling ERP.*

This is the one document to read to understand the whole project: what it is, how
to run it, how it's built, every command, and every important endpoint. Written
for someone comfortable with basic programming but new to Spring Boot / backend
development.

---

## 1. What is BatchMaker?

BatchMaker automatically builds a **practical (lab) timetable** for a college.
You enter your subjects, laboratories, faculty, students and time slots, and it
works out **which batch does which practical, in which lab, with which teacher,
at what time** — without any clashes.

The hard part is the scheduling math (hundreds of ways to place classes, many
rules that must never be broken). BatchMaker solves it with a constraint-solving
engine, then lets an admin review, adjust and publish the result.

---

## 2. Architecture — three services

The system is split into three programs that run at the same time and talk over
HTTP. This separation is the single most important thing to understand.

```
┌─────────────────────────────────────────────────────────────┐
│  FRONTEND  — React + TypeScript (browser)          :5173     │
│  What the user sees and clicks.                              │
└───────────────┬─────────────────────────────────────────────┘
                │  calls /api/... (Vite proxies to :8080)
                ▼
┌─────────────────────────────────────────────────────────────┐
│  BACKEND  — Java + Spring Boot                     :8080     │
│  Login, permissions, database, business rules, REST API.    │
└───────┬──────────────────────────────────┬──────────────────┘
        │  reads/writes                     │  POSTs the problem
        ▼                                   ▼
┌──────────────────────┐     ┌──────────────────────────────────┐
│  DATABASE — H2 file  │     │  SOLVER — Python + OR-Tools :8090 │
│  ./backend/data/     │     │  The scheduling math (CP-SAT).    │
└──────────────────────┘     └──────────────────────────────────┘
```

**Why three services?** Each does one job well: the frontend handles what the
user sees, the Java backend handles the web app + database + security, and the
Python solver handles the heavy optimisation math. They're independent, so one
can change without breaking the others.

---

## 3. Tech stack (only what's actually used)

### Frontend
| Tech | What it is / why |
|---|---|
| **React 19** | UI library — builds the screens from components |
| **TypeScript** | JavaScript with types — catches mistakes before running |
| **Vite** | Dev server + build tool; also proxies `/api` to the backend |
| **Tailwind CSS 4** | Utility CSS classes for styling |
| **TanStack Query** | Fetches/caches API data (`useQuery`, `useMutation`) |
| **React Router** | Page navigation (`/timetable`, `/what-if`, …) |

### Backend
| Tech | What it is / why |
|---|---|
| **Java 21** | The language |
| **Spring Boot 3.5** | Framework that turns Java into a running web server with little setup |
| **Spring Web** | The REST API (`@RestController`, endpoints) |
| **Spring Security + JWT** | Login and role-based permissions |
| **Spring Data JPA** | Database access without writing SQL (the `...Repository` classes) |
| **Spring `RestClient`** | HTTP client used to call the Python solver |
| **Lombok** | Removes boilerplate (`@Getter`, `@Slf4j`, `@RequiredArgsConstructor`) |
| **Jackson** | Converts Java objects ↔ JSON (`@JsonNaming` for snake_case ↔ camelCase) |
| **Flyway** | Creates/updates database tables on startup (migrations) |
| **H2** | Lightweight file-based SQL database |
| **Maven** | Build tool; `pom.xml` lists dependencies. Run via the `./mvnw` wrapper |

### Solver
| Tech | What it is / why |
|---|---|
| **Python 3.11+** | The language |
| **FastAPI** | Web framework exposing the solver endpoints |
| **OR-Tools (CP-SAT)** | Google's constraint solver — does the actual scheduling |
| **Pydantic** | Validates the request/response JSON shapes |
| **Uvicorn** | The server that runs the FastAPI app |

---

## 4. Prerequisites

Install these once:

- **JDK 21** (Java) — check: `java -version`
- **Python 3.11+** — check: `python --version`
- **Node.js 20+** — check: `node -v`

**Maven is NOT required** — the project ships a wrapper (`./mvnw`) that downloads
the right Maven version automatically on first use.

---

## 5. Setup & run (all commands)

### First-time setup

```bash
# Solver: create the Python virtual environment and install dependencies
cd solver
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt

# Frontend: install packages
cd ../frontend
npm install
```

The backend needs no setup step — `./mvnw` fetches Maven and dependencies on first run.

### Run — three terminals, one per service

**Terminal 1 — Solver** (port 8090):
```bash
cd solver && .venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

**Terminal 2 — Backend** (port 8080):
```bash
cd backend && .\mvnw spring-boot:run
```
> Git Bash instead of PowerShell: `./mvnw spring-boot:run`

**Terminal 3 — Frontend** (port 5173):
```bash
cd frontend && npm run dev
```

Then open **http://localhost:5173**.

### Other useful commands

```bash
# Backend: compile only / run tests
cd backend && .\mvnw compile
cd backend && .\mvnw test

# Frontend: type-check without building / production build
cd frontend && npx tsc --noEmit
cd frontend && npm run build

# Solver: quick engine sanity check
cd solver && .venv\Scripts\python.exe smoke_test.py
```

---

## 6. First login (admin account)

On an **empty** database, the backend creates the four roles and **one admin
account**, and prints the password **once** in its startup log:

```
================================================================
  Empty database detected - created the administrator account.
     username : admin
     password : (a random one, shown only this once)
================================================================
```

To choose your own password instead, set an environment variable **before**
starting the backend:

```bash
set BATCHMAKER_BOOTSTRAP_ADMIN_PASSWORD=your-password-here
```

Everything else (departments, terms, subjects, labs, faculty, students) is
entered by you through the app — the system never invents institutional data.

---

## 7. Ports & URLs

| Service | URL | Port |
|---|---|---|
| Frontend | http://localhost:5173 | 5173 |
| Backend API | http://localhost:8080/api | 8080 |
| Solver | http://127.0.0.1:8090 | 8090 |
| Backend health check | http://localhost:8080/api/health | 8080 |
| Solver health check | http://127.0.0.1:8090/health | 8090 |

The frontend calls `/api/...`; Vite proxies those to the backend at `:8080`.

---

## 8. Configuration reference (`backend/src/main/resources/application.yml`)

| Setting | Value / meaning |
|---|---|
| `spring.datasource.url` | `jdbc:h2:file:./data/batchmaker` — the DB is a file in `backend/data/` |
| `server.port` | `8080` |
| `batchmaker.solver.base-url` | `http://127.0.0.1:8090` — where the backend finds the solver |
| `batchmaker.solver.fallback-enabled` | `true` — if the solver is down, a simple built-in scheduler is used |
| `batchmaker.cors.allowed-origins` | Which frontend URLs may call the API (includes `5173`, `5174`, `5175`) |
| `batchmaker.bootstrap.admin-username` | `admin` |
| `batchmaker.security.jwt.secret` | Signing key for login tokens (change for production) |

> **Your data lives in `backend/data/batchmaker.mv.db`.** It persists across
> restarts. Delete that file to start completely fresh (you'll get a new admin
> password in the log).

---

## 9. Project structure

```
batchmaker/
├── PROJECT_GUIDE.md          ← this file
├── README.md
│
├── backend/                  Java + Spring Boot
│   ├── mvnw, mvnw.cmd        Maven wrapper (run the build without installing Maven)
│   ├── pom.xml               Dependencies + build config
│   ├── data/                 The H2 database file lives here
│   └── src/main/
│       ├── java/edu/batchmaker/
│       │   ├── controller/   HTTP endpoints (the API surface)
│       │   ├── service/      Business logic (the "brains")
│       │   ├── repository/   Database access (interfaces, no SQL)
│       │   ├── domain/entity/ Java classes mapped to DB tables
│       │   ├── dto/          Data shapes sent over the wire
│       │   ├── security/     JWT auth, permissions
│       │   ├── config/       App configuration
│       │   └── seed/         Bootstrap admin + roles on first run
│       └── resources/
│           ├── application.yml        Configuration
│           └── db/migration/          Flyway SQL migrations (V1__baseline_schema.sql)
│
├── solver/                   Python + FastAPI + OR-Tools
│   ├── requirements.txt
│   └── app/
│       ├── main.py           FastAPI app + endpoints (/solve, /feasibility, /reschedule)
│       ├── models.py         Request/response shapes (Pydantic)
│       ├── engine.py         The CP-SAT solver
│       └── feasibility.py    The fast "is it possible?" pre-check
│
└── frontend/                 React + TypeScript
    ├── vite.config.ts        Dev server + /api proxy
    └── src/
        ├── pages/            One file per screen (Timetable, WhatIf, Students, …)
        ├── components/       Reusable UI (TimetableGrid, Modal, AssistantDrawer, …)
        └── lib/              api.ts (HTTP client), types.ts (TypeScript types), auth.ts
```

**The golden rule of the backend: layers.**
`Controller → Service → Repository → Database`. The controller knows *HTTP*, the
service knows *the rules*, the repository knows *the database*. Each layer has one job.

---

## 10. Data model (main entities)

```
Department ──< Faculty, Subject, Laboratory, StudentBatch

Faculty ──< FacultySubject >── Subject        (who can teach what)
Faculty ──< FacultyAvailability                (when a teacher is free/busy/prefers)
Faculty ──< FacultyLeave

Subject ──< StudentBatch                       (a batch studies one subject's practical)
StudentBatch ──< BatchStudent >── Student      (who is in the batch)
StudentBatch ──< Practical                     (the schedulable unit)

AcademicTerm ──< Practical, StudentBatch, Timetable

Timetable ──< TimetableEntry                   (one entry = one scheduled session)
Timetable ──< Conflict

WorkingDay, TimeSlot, Holiday, OptimizationWeight   (standalone configuration)
User, Role                                          (login + permissions)
```

Key ones:
- **Practical** — "batch X needs subject Y's practical N times/week for D minutes." This is what the solver schedules.
- **TimetableEntry** — one placed session: day, start/end period, faculty, lab, batch, subject.
- **Timetable** — a versioned schedule: `DRAFT → PUBLISHED → ARCHIVED/REJECTED`.
- **Conflict** — a rule violation detected by the independent checker.

---

## 11. How a request flows (example: "Check feasibility")

```
Browser (Timetable.tsx)
   │  POST /api/timetable/feasibility   (+ JWT token in the header)
   ▼
TimetableController.feasibility()        receives the HTTP request
   ▼
TimetableService.feasibilityAudit()      the business logic
   │  1. reads current term, practicals, labs, faculty via Repositories
   │  2. ScheduleDataAssembler packs it into a solver request
   │  3. SolverClient sends it to the Python service over HTTP
   ▼
Python /feasibility (feasibility.py)     does the arithmetic, returns JSON
   ▼
TimetableService                          adds subject/batch/division names
   ▼
Controller → Spring converts to JSON → Browser draws the FeasibilityPanel
```

---

## 12. Features guide

### Core workflow
1. **Enter master data** — departments, term, working days, time slots, then
   subjects, labs, faculty, students. (Settings + the Master data pages. The app
   has a **Setup checklist** that tracks what's still missing.)
2. **Create practical batches** — the *Practical Batches* page splits students
   into lab-sized batches.
3. **Generate the timetable** — *Timetable* page.
4. **Review and publish** — approve a draft (blocked if any hard rule is broken).

### The scheduling features

| Feature | Where | What it does |
|---|---|---|
| **Check feasibility** | Timetable → *Check feasibility* | Fast check *before* generating: is there enough lab/faculty/time? If not, shows exactly what to add. Fixes the old "no free slot" dead-end. |
| **Generate timetable** | Timetable → *Generate timetable* | Runs the full solver, produces a draft. |
| **Generate 3 options** | Timetable → *Generate 3 options* | Three drafts tuned differently (Balanced / Faculty-friendly / Lab-efficient); pick the best. |
| **Tune priorities** | Timetable → *Tune optimisation priorities* | Sliders to weight what the optimiser cares about. |
| **What-if simulator** | sidebar → *What-if Simulator* | "What if this lab closes / this teacher is away / 20% more students?" Compares baseline vs scenario. Saves nothing. |
| **Manual editing** | Timetable (draft) → *Edit sessions* | Click a session to move it, reassign faculty/lab, or delete it. Re-validated instantly. |
| **Assistant** | *Assistant* button (top bar) | Ask about the published timetable in plain English (a day, division, faculty, lab, or a count). |
| **Roster parser** | Students → *Paste a roster* | Paste raw text → extracts students + suggests a batch split (preview only). |

**Hard vs soft constraints:**
- **Hard** (never broken): faculty can't be in two places at once, a lab holds one class at a time, students in one batch can't have two classes at once, lab must be big enough and the right type, faculty must be qualified and available.
- **Soft** (optimised): balance faculty workload, respect preferences, minimise student gaps, fill labs efficiently, minimise idle time, minimise disruption when rescheduling.

---

## 13. API reference

All under `http://localhost:8080/api`. Most require a JWT token; staff endpoints
require the `ADMIN` or `HOD` role.

### Timetable (`/api/timetable`)
| Method + path | Purpose |
|---|---|
| `POST /feasibility` | Resource pre-check (can it be scheduled?) **[new]** |
| `POST /generate` | Generate one draft timetable |
| `POST /generate-options` | Generate 3 tuned draft options **[new]** |
| `POST /what-if` | Compare baseline vs a hypothetical scenario **[new]** |
| `POST /{id}/entries` | Add a session to a draft **[new]** |
| `PUT /{id}/entries/{entryId}` | Edit a session on a draft **[new]** |
| `DELETE /{id}/entries/{entryId}` | Delete a session from a draft **[new]** |
| `GET /` | List all timetable versions |
| `GET /{id}` | One timetable's full detail |
| `GET /current` | The published timetable |
| `POST /{id}/approve` | Publish a draft |
| `POST /{id}/reject` · `DELETE /{id}` | Reject / delete a draft |
| `GET /{id}/validate` | Re-check a timetable's conflicts |
| `GET /faculty/{id}` · `/student/{id}` · `/batch/{id}` · `/lab/{id}` · `/division` | Filtered views |

### Other controllers
| Base path | Purpose |
|---|---|
| `POST /api/auth/login` | Log in, returns a JWT token |
| `/api/students` | Student CRUD, CSV/Excel import, **`POST /parse`** (roster parser) **[new]** |
| `/api/assistant` | **`POST /query`** — natural-language timetable Q&A **[new]** |
| `/api/faculty` | Faculty CRUD, availability, leave |
| `/api/subjects` | Subject CRUD, lab types |
| `/api/labs` | Laboratory CRUD |
| `/api/batches` | Practical batch generation |
| `/api/conflicts` | List/resolve detected conflicts |
| `/api/rescheduling` | Auto-reschedule on faculty leave / lab maintenance |
| `/api/workload` | Faculty workload analysis |
| `/api/dashboard` | Dashboard data |
| `/api/config` | Departments, terms, working days, time slots, weights |
| `/api/reports` | PDF/Excel exports |
| `/api/notifications` | User notifications |
| `/api/setup` | Setup-checklist status |
| `/api/health` | Backend health check |

### Solver endpoints (`http://127.0.0.1:8090`, called only by the backend)
| Method + path | Purpose |
|---|---|
| `GET /health` | Is the solver up? |
| `POST /solve` | Full timetable generation |
| `POST /feasibility` | Resource pre-check (no solve) **[new]** |
| `POST /reschedule` | Re-solve with minimal disruption |

---

## 14. How the solver works (in brief)

- It receives a **self-contained snapshot** of the problem (days, slots, labs,
  faculty, practicals) — it never touches the database.
- **Candidate placements:** for each session it lists every legal (day, periods,
  faculty, lab) combination. Illegal ones (too-small lab, unqualified/unavailable
  faculty, wrong slot) are simply never listed — so those hard rules can't be broken.
- **Two-phase solve:** first it *proves a valid schedule exists* (hard rules only),
  then it *optimises* the soft rules within the time budget.
- **Score:** it returns a 0–100 quality score plus a breakdown.
- **Feasibility pre-check** (`feasibility.py`) is different: pure arithmetic, no
  solve — it counts sessions needed vs capacity and reports the binding shortage
  in milliseconds.

---

## 15. Database

- **Technology:** H2, a file-based SQL database. File: `backend/data/batchmaker.mv.db`.
- **Connection:** handled by Spring Data JPA — you never open a connection by hand.
- **Tables:** created/updated by **Flyway** on startup from
  `backend/src/main/resources/db/migration/` (currently `V1__baseline_schema.sql`).
- **Reads/writes:** through **repository interfaces**, e.g.
  `practicalRepository.findActiveForTerm(termId)` — no SQL written by hand.
- **Reset everything:** stop the backend, delete `backend/data/batchmaker.mv.db`,
  restart. You'll get a fresh admin password in the log.

---

## 16. Common problems & fixes

| Symptom | Cause | Fix |
|---|---|---|
| `mvn: command not found` | Maven not installed | Use the wrapper: `.\mvnw spring-boot:run` |
| **502** on login | Backend not running | Start the backend (`.\mvnw spring-boot:run`) |
| **"Invalid CORS request"** | Frontend port not in allowed-origins | Add the port to `batchmaker.cors.allowed-origins` in `application.yml`, restart backend |
| **"optimisation service unavailable"** | Solver not running | Start the solver on port 8090 |
| **"No free slot could be found"** | Genuinely not enough labs/faculty/time | Click **Check feasibility** — it names the shortage and the fix |
| Assistant says "no timetable published" | Nothing published yet | Generate → approve a timetable first |
| Feasibility/What-if button errors | Solver down | Start the solver |
| Port already in use | Another process on 8080/8090/5173 | Stop it, or change the port in config |

---

## 17. Important annotations (glossary)

| Annotation | What it does |
|---|---|
| `@RestController` | Marks a class whose methods answer web requests with JSON |
| `@RequestMapping("/api/x")` | Base URL for a controller |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | Maps a method to an HTTP verb + path |
| `@PathVariable` / `@RequestBody` / `@RequestParam` | Pull data from the URL path / JSON body / query string |
| `@PreAuthorize("hasAnyRole('ADMIN','HOD')")` | Permission check before a method runs |
| `@Service` / `@Component` | Marks a class Spring should create and manage |
| `@RequiredArgsConstructor` (Lombok) | Auto-writes the constructor — how Spring injects dependencies |
| `@Transactional` | Runs a method as one all-or-nothing database transaction |
| `@Entity` | Maps a class to a database table |
| `@Getter` / `@Setter` (Lombok) | Generate getters/setters |
| `@Slf4j` (Lombok) | Adds a `log` object |
| `@JsonNaming(SnakeCaseStrategy)` | Converts camelCase ↔ snake_case for the Python solver |
| `record` (Java, not an annotation) | A compact immutable data holder — used for all DTOs |

---

## 18. Glossary

| Term | Meaning |
|---|---|
| **Practical** | A lab session a batch must attend for a subject |
| **Batch** | A lab-sized group of students from a division |
| **Division** | A section of a class (A, B, …) |
| **Hard constraint** | A rule that must never be broken |
| **Soft constraint** | A preference the optimiser tries to satisfy |
| **CP-SAT** | The constraint-solving algorithm (OR-Tools) |
| **Feasibility** | Whether any valid schedule can exist at all |
| **JWT** | JSON Web Token — the login token proving who you are |
| **DTO** | Data Transfer Object — the shape of data sent over the API |
| **ORM** | Object-Relational Mapping — Java objects ↔ database rows |
| **Migration** | A versioned SQL script that sets up/updates the database (Flyway) |
| **Draft / Published** | A timetable under review vs the live one everyone uses |
```
