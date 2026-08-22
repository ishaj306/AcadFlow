# AcadFlow — Commands & Demo Guide

Everything you need to set up, run, reset, and demo AcadFlow. Commands are shown
for **Windows PowerShell** (the project's primary shell); Git Bash equivalents
are noted where they differ.

Three services run together:

| Service | Folder | Port | URL |
|---|---|---|---|
| Frontend (React + Vite) | `frontend/` | 5173 | http://localhost:5173 |
| Backend API (Spring Boot) | `backend/` | 8080 | http://localhost:8080/api |
| Solver (FastAPI + OR-Tools) | `solver/` | 8090 | http://127.0.0.1:8090 |

---

## 1. Prerequisites (install once)

- **JDK 21** — `java -version`
- **Python 3.11+** — `python --version`
- **Node.js 20+** — `node -v`

Maven is **not** required — the backend ships the `.\mvnw` wrapper.

---

## 2. First-time setup

```powershell
# Solver: create the virtual environment and install dependencies
cd solver
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt

# Frontend: install packages
cd ..\frontend
npm install
```

The backend needs no setup step — `.\mvnw` fetches Maven and dependencies on the
first run.

---

## 3. Run — three terminals, one per service

**Terminal 1 — Solver (8090)**
```powershell
cd solver
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

**Terminal 2 — Backend (8080)**
```powershell
cd backend
.\mvnw spring-boot:run
```
> Git Bash: `./mvnw spring-boot:run`

**Terminal 3 — Frontend (5173)**
```powershell
cd frontend
npm run dev
```

Then open **http://localhost:5173**.

---

## 4. The admin account

On an **empty** database the backend creates the four roles (ADMIN, HOD, FACULTY,
STUDENT) and **one administrator**. The username defaults to `admin`.

### Choose your own admin password (recommended for a demo)

Set the environment variable **before** starting the backend, then run it:

```powershell
cd backend
$env:BATCHMAKER_BOOTSTRAP_ADMIN_PASSWORD = "Demo12345"
.\mvnw spring-boot:run
```
> Git Bash: `BATCHMAKER_BOOTSTRAP_ADMIN_PASSWORD=Demo12345 ./mvnw spring-boot:run`

Then sign in with **admin / Demo12345**.

### Or let it generate one

If you don't set the variable, a strong password is generated and printed
**once** in the backend startup log:

```
================================================================
  Empty database detected - created the administrator account.
     username : admin
     password : (shown only this once)
================================================================
```

### Change the admin username (optional)

```powershell
$env:BATCHMAKER_BOOTSTRAP_ADMIN_USERNAME = "coordinator"
```

### Faculty / student logins

These are **not** created on import. Add a login per person from their row in
the **Faculty** or **Students** screen (the *Login* / *Create account* action),
so importing a roster never creates hundreds of credentials silently.

---

## 5. Reset / clean the database

The database is a single folder, `backend/data/`. To wipe everything and start
fresh (you'll get a new admin account on the next start):

```powershell
# Stop the backend first, then:
Remove-Item -Recurse -Force backend\data
```
> Git Bash: `rm -rf backend/data`

Restart the backend to get a clean, empty install.

---

## 6. Demo: import the sample college

The repo ships ready-to-import CSVs in **`demo-data/`**. Because departments,
term and working hours are not CSV-imported, create those in the UI first, then
import in dependency order.

1. **Settings** → create departments **CSE** and **IT**.
2. **Settings** → create the **academic term** (mark it current).
3. **Settings** → set **working hours** (periods + active days, e.g. Mon–Fri).
4. Import CSVs **in this order** (each references the previous):
   1. **Subjects** ← `demo-data/subjects.csv`
   2. **Laboratories** ← `demo-data/laboratories.csv`
   3. **Faculty** ← `demo-data/faculty.csv`  *(the `subject_codes` column maps qualifications, so subjects must exist first)*
   4. **Students** ← `demo-data/students.csv`
5. **Practical Batches** → *Generate*.
6. **Timetable** → *Generate* → *Approve & publish*.

Then demonstrate: **Fixed Lectures**, **batch swap** (Batches → *Swap two
students*), **Excel/PDF export** (Timetable), and **version lineage** (regenerate
+ approve a second time).

> Prefer to skip the manual clicks? `scripts/sample-data.ps1` builds the same
> college via the API (see §8).

---

## 7. Build, test & type-check

```powershell
# Backend: compile / run tests
cd backend
.\mvnw compile
.\mvnw test

# Frontend: type-check / production build
cd ..\frontend
npx tsc --noEmit
npm run build

# Solver: quick engine sanity check
cd ..\solver
.\.venv\Scripts\python.exe smoke_test.py
```

---

## 8. Helper scripts (need the backend + solver running)

```powershell
# Populate a full sample college (2 depts, 520 students, 18 faculty, 8 labs, ...)
powershell -File scripts\sample-data.ps1 -AdminPassword "Demo12345"

# Drive the whole workflow end to end and assert every step
powershell -File scripts\demo-scenario.ps1 -AdminPassword "Demo12345" -SolveSeconds 60
```

---

## 9. Health checks

```powershell
curl http://127.0.0.1:8090/health          # solver
curl http://localhost:8080/api/health      # backend
```

---

## 10. Deploying beyond localhost (security)

Two dev conveniences are gated to the default `dev` profile and must be handled
for a real deployment:

- The **H2 web console** is only exposed under `dev`.
- The committed **default JWT secret** is rejected at startup outside `dev`. Set
  your own (≥ 32 bytes) before running under another profile:

```powershell
$env:BATCHMAKER_SECURITY_JWT_SECRET = "a-long-random-private-signing-key-change-me"
```

---

## 11. Git

```powershell
git status
git add -A
git commit -m "message"
git push origin main
```
