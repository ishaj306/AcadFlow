"""HTTP surface of the timetable optimisation service.

Runs standalone on port 8090 and is called by the Spring Boot backend over
REST. It is deliberately stateless: everything needed for a solve arrives in
the request body.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from .engine import solve
from .feasibility import audit
from .models import FeasibilityReport, SolveRequest, SolveResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s [solver] %(message)s",
)
log = logging.getLogger(__name__)

app = FastAPI(
    title="Batchmaker Optimisation Service",
    description="Constraint-programming engine for practical timetable generation.",
    version="1.0.0",
)


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "batchmaker-solver", "engine": "ortools-cpsat"}


@app.post("/solve", response_model=SolveResponse)
def solve_timetable(request: SolveRequest) -> SolveResponse:
    log.info(
        "solve: %d practicals, %d faculty, %d labs, %d days, %d slots (limit %.0fs)",
        len(request.practicals), len(request.faculty), len(request.labs),
        len(request.days), len(request.time_slots), request.max_seconds,
    )
    response = solve(request)
    log.info(
        "solve finished: status=%s score=%.1f assignments=%d in %dms",
        response.status, response.schedule_score, len(response.assignments), response.runtime_ms,
    )
    return response


@app.post("/feasibility", response_model=FeasibilityReport)
def feasibility(request: SolveRequest) -> FeasibilityReport:
    """Explain, in resource terms, whether a schedule can exist - without solving.

    Fast necessary-condition arithmetic run before generation so the user learns
    up front whether they have enough labs, faculty hours and long-enough blocks,
    and what to change if not.
    """
    report = audit(request)
    log.info(
        "feasibility: verdict=%s required=%d capacity=%d blockers=%d in %dms",
        report.verdict, report.total_sessions_required, report.total_session_capacity,
        len(report.blockers), report.runtime_ms,
    )
    return report


@app.post("/reschedule", response_model=SolveResponse)
def reschedule(request: SolveRequest) -> SolveResponse:
    """Re-solve while holding the rest of the timetable still.

    Identical to /solve, but callers supply `previous` so the change penalty
    (soft constraint S6) pushes the engine towards the smallest edit that
    restores feasibility, and `locked` for sessions that must not move.
    """
    log.info("reschedule: %d locked, %d previous", len(request.locked), len(request.previous))
    return solve(request)


@app.exception_handler(Exception)
def unhandled(_request, exc: Exception) -> JSONResponse:
    log.exception("Unhandled solver error", exc_info=exc)
    return JSONResponse(
        status_code=500,
        content={
            "status": "ERROR",
            "schedule_score": 0.0,
            "runtime_ms": 0,
            "assignments": [],
            "violations": [{"code": "SOLVER_ERROR", "message": str(exc)}],
            "warnings": [],
        },
    )
