"""Fast feasibility pre-check.

Runs *before* the CP-SAT engine to explain, in plain resource terms, whether a
schedule can exist at all - and if not, what to change. It is deliberately
arithmetic rather than a solve: it computes necessary conditions (enough labs,
enough faculty hours, a block long enough for each practical) and reports the
binding shortage. A problem that passes every check here may still be tight for
the optimiser, but a problem that fails one is provably impossible, so the user
gets an actionable answer in milliseconds instead of waiting for an INFEASIBLE.

Capacity is measured in *sessions*: one laboratory, free for one practical-length
block, on one working day. Counts are computed optimistically (ignoring
availability holes and using the shortest practical as the block size), so a
reported shortage is real - the true capacity can only be lower.
"""

from __future__ import annotations

import time

from .models import (
    BatchLoad,
    BlockerItem,
    FeasibilityReport,
    ResourceCheck,
    SolveRequest,
    Suggestion,
)

# Below this headroom a passing problem is flagged TIGHT rather than FEASIBLE.
TIGHT_UTILISATION = 0.85


def _maximal_run_lengths(slots) -> list[int]:
    """Lengths, in minutes, of each maximal contiguous run of teachable slots.

    A run ends at the first non-teachable period or wherever one slot does not
    begin exactly when the previous ends - the same rule the engine uses, so the
    block sizes here match what can actually be scheduled.
    """
    ordered = sorted(slots, key=lambda s: s.order)
    runs: list[int] = []
    current = 0
    prev_end: int | None = None
    for slot in ordered:
        if not slot.teachable:
            if current:
                runs.append(current)
            current = 0
            prev_end = None
            continue
        if prev_end is not None and slot.start_minute != prev_end:
            runs.append(current)
            current = 0
        current += slot.end_minute - slot.start_minute
        prev_end = slot.end_minute
    if current:
        runs.append(current)
    return runs


def _blocks_per_day(runs: list[int], duration: int) -> int:
    """How many non-overlapping blocks of `duration` fit across a day's runs."""
    if duration <= 0:
        return 0
    return sum(run // duration for run in runs)


def audit(request: SolveRequest) -> FeasibilityReport:
    started = time.perf_counter()
    blockers: list[BlockerItem] = []
    checks: list[ResourceCheck] = []
    suggestions: list[Suggestion] = []
    notes: list[str] = []

    days = len(request.days)
    runs = _maximal_run_lengths(request.time_slots)
    longest_block = max(runs) if runs else 0

    if not request.practicals or not request.days or not request.time_slots:
        return FeasibilityReport(
            verdict="INFEASIBLE",
            runtime_ms=int((time.perf_counter() - started) * 1000),
            blockers=[BlockerItem(
                code="EMPTY_PROBLEM",
                message="Working days, teaching periods and practicals must all be defined first.",
            )],
        )

    faculty_by_subject: dict[int, list] = {}
    for fac in request.faculty:
        for subject_id in fac.qualified_subject_ids:
            faculty_by_subject.setdefault(subject_id, []).append(fac)

    labs_by_type: dict[str, list] = {}
    for lab in request.labs:
        labs_by_type.setdefault(lab.lab_type, []).append(lab)

    # ---- per-practical hard blockers --------------------------------------
    schedulable = []
    for p in request.practicals:
        qualified = faculty_by_subject.get(p.subject_id, [])
        suitable_labs = [
            lab for lab in labs_by_type.get(p.required_lab_type, [])
            if lab.capacity >= p.student_count
        ]
        if not qualified:
            blockers.append(BlockerItem(
                code="NO_QUALIFIED_FACULTY",
                message=f"No faculty member is qualified to teach subject {p.subject_id}.",
                practical_id=p.id, subject_id=p.subject_id,
            ))
            continue
        if not labs_by_type.get(p.required_lab_type):
            blockers.append(BlockerItem(
                code="NO_LAB_OF_TYPE",
                message=f"No '{p.required_lab_type}' laboratory exists.",
                practical_id=p.id, subject_id=p.subject_id,
            ))
            continue
        if not suitable_labs:
            biggest = max(lab.capacity for lab in labs_by_type[p.required_lab_type])
            blockers.append(BlockerItem(
                code="NO_LAB_LARGE_ENOUGH",
                message=(
                    f"The largest '{p.required_lab_type}' laboratory seats {biggest}, "
                    f"but this batch has {p.student_count} students."
                ),
                practical_id=p.id, subject_id=p.subject_id,
            ))
            continue
        if p.duration_minutes > longest_block:
            blockers.append(BlockerItem(
                code="DURATION_EXCEEDS_BLOCK",
                message=(
                    f"This practical needs {p.duration_minutes} continuous minutes, but the longest "
                    f"teaching block is only {longest_block} minutes."
                ),
                practical_id=p.id, subject_id=p.subject_id,
            ))
            continue
        schedulable.append(p)

    total_required = sum(max(1, p.sessions_per_week) for p in request.practicals)

    # ---- lab session capacity, per required type --------------------------
    total_capacity = 0
    for lab_type, labs in sorted(labs_by_type.items()):
        practicals_here = [p for p in schedulable if p.required_lab_type == lab_type]
        required = sum(max(1, p.sessions_per_week) for p in practicals_here)
        if required == 0:
            continue
        rep_duration = min(p.duration_minutes for p in practicals_here)
        blocks = _blocks_per_day(runs, rep_duration)
        capacity = len(labs) * days * blocks
        total_capacity += capacity
        satisfied = capacity >= required
        checks.append(ResourceCheck(
            key=f"lab:{lab_type}",
            label=f"{lab_type} laboratory slots",
            required=required,
            available=capacity,
            unit="sessions",
            satisfied=satisfied,
            utilization_percent=round(100.0 * required / capacity, 1) if capacity else 100.0,
            detail=(
                f"{len(labs)} lab(s) x {days} day(s) x {blocks} block(s)/day "
                f"= {capacity} sessions; {required} needed."
            ),
        ))
        if not satisfied:
            shortage = required - capacity
            per_lab = days * blocks
            per_day = len(labs) * blocks
            suggestions.append(Suggestion(
                code="ADD_LAB",
                title=f"Add a {lab_type} laboratory",
                detail=f"Each extra {lab_type} lab adds {per_lab} sessions/week. "
                       f"Short by {shortage}.",
                category="lab",
                estimated_gain_sessions=per_lab,
            ))
            if per_day > 0:
                suggestions.append(Suggestion(
                    code="ADD_DAY",
                    title="Add a working day",
                    detail=f"Another working day adds {per_day} {lab_type} sessions/week.",
                    category="time",
                    estimated_gain_sessions=per_day,
                ))
            if longest_block >= 2 * rep_duration:
                suggestions.append(Suggestion(
                    code="ADD_TIME_SLOT",
                    title="Add or lengthen a teaching period",
                    detail=(
                        f"A longer teaching block fits more {rep_duration}-minute practicals per day, "
                        f"adding {len(labs) * days} sessions/week per extra block."
                    ),
                    category="time",
                    estimated_gain_sessions=len(labs) * days,
                ))

    # ---- faculty session capacity, overall and per subject ----------------
    shortest = min((p.duration_minutes for p in schedulable), default=0)
    faculty_blocks = _blocks_per_day(runs, shortest) if shortest else 0
    faculty_capacity = len(request.faculty) * days * faculty_blocks
    if schedulable:
        satisfied = faculty_capacity >= total_required
        checks.append(ResourceCheck(
            key="faculty:overall",
            label="Faculty teaching slots",
            required=total_required,
            available=faculty_capacity,
            unit="sessions",
            satisfied=satisfied,
            utilization_percent=round(100.0 * total_required / faculty_capacity, 1)
            if faculty_capacity else 100.0,
            detail=(
                f"{len(request.faculty)} faculty x {days} day(s) x {faculty_blocks} block(s)/day "
                f"= {faculty_capacity} sessions; {total_required} needed."
            ),
        ))
        if not satisfied:
            per_faculty = days * faculty_blocks
            suggestions.append(Suggestion(
                code="ADD_FACULTY",
                title="Add teaching faculty",
                detail=f"Each additional faculty member adds up to {per_faculty} sessions/week. "
                       f"Short by {total_required - faculty_capacity}.",
                category="faculty",
                estimated_gain_sessions=per_faculty,
            ))

    # Per-subject faculty bottleneck: a subject taught by too few people.
    by_subject: dict[int, int] = {}
    for p in schedulable:
        by_subject[p.subject_id] = by_subject.get(p.subject_id, 0) + max(1, p.sessions_per_week)
    for subject_id, required in sorted(by_subject.items()):
        qualified = len(faculty_by_subject.get(subject_id, []))
        capacity = qualified * days * faculty_blocks
        if capacity >= required:
            continue
        checks.append(ResourceCheck(
            key=f"faculty:subject:{subject_id}",
            label=f"Faculty for subject {subject_id}",
            required=required,
            available=capacity,
            unit="sessions",
            satisfied=False,
            utilization_percent=round(100.0 * required / capacity, 1) if capacity else 100.0,
            detail=(
                f"{qualified} qualified faculty x {days} day(s) x {faculty_blocks} block(s)/day "
                f"= {capacity} sessions; {required} needed."
            ),
        ))
        suggestions.append(Suggestion(
            code="QUALIFY_FACULTY",
            title=f"Qualify more faculty for subject {subject_id}",
            detail=f"Only {qualified} faculty can teach it, capping capacity at {capacity} "
                   f"sessions/week against {required} needed.",
            category="faculty",
            estimated_gain_sessions=days * faculty_blocks,
        ))

    # ---- per-batch load, for the breakdown table --------------------------
    batch_loads: dict[int, BatchLoad] = {}
    for p in request.practicals:
        load = batch_loads.get(p.batch_id)
        sessions = max(1, p.sessions_per_week)
        if load is None:
            batch_loads[p.batch_id] = BatchLoad(
                batch_id=p.batch_id,
                sessions_required=sessions,
                lab_type=p.required_lab_type,
            )
        else:
            load.sessions_required += sessions

    # ---- verdict ----------------------------------------------------------
    failing = [c for c in checks if not c.satisfied]
    if blockers or failing:
        verdict = "INFEASIBLE"
    elif any(c.utilization_percent >= TIGHT_UTILISATION * 100 for c in checks):
        verdict = "TIGHT"
        notes.append(
            "Every resource check passes, but with little headroom - generation may still be slow "
            "or fail once availability and student conflicts are taken into account."
        )
    else:
        verdict = "FEASIBLE"

    notes.append(
        "Capacity is an optimistic upper bound: it ignores faculty/lab availability holes and "
        "student-group clashes, so a real schedule may need more headroom than shown."
    )

    # De-duplicate suggestions by (code, title), keeping the first.
    seen = set()
    unique_suggestions = []
    for s in suggestions:
        marker = (s.code, s.title)
        if marker in seen:
            continue
        seen.add(marker)
        unique_suggestions.append(s)

    return FeasibilityReport(
        verdict=verdict,
        runtime_ms=int((time.perf_counter() - started) * 1000),
        total_sessions_required=total_required,
        total_session_capacity=total_capacity,
        blockers=blockers,
        resource_checks=checks,
        batch_loads=sorted(batch_loads.values(), key=lambda b: b.batch_id),
        suggestions=unique_suggestions,
        notes=notes,
    )
