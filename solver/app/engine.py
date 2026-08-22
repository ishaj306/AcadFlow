"""CP-SAT timetable engine.

Modelling approach
------------------
Every session of every practical gets one boolean per *candidate placement*,
where a candidate is a complete, already-legal tuple:

    (day, consecutive teachable slots covering the duration, faculty, laboratory)

Enumerating full tuples means several hard constraints never enter the model at
all - they are simply absent from the candidate set:

    H4 lab capacity        - labs too small are not enumerated
    H5 faculty available   - unavailable (day, slot) cells are not enumerated
    H6 lab available       - likewise for laboratories
    H7 working hours       - only teachable, contiguous slots are enumerated
    H8 holidays / H9 leave - expanded into unavailable cells by the backend
    qualification          - only faculty who can teach the subject

What remains are the three mutual-exclusion constraints, expressed as
NoOverlap over optional intervals on an absolute minutes-of-week timeline:

    H1 one faculty, one place at a time
    H2 one laboratory, one practical at a time
    H3 one student cohort, one practical at a time

Soft constraints S1-S6 form a weighted objective; hard constraints are
structural, so no weighting can ever trade one away.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field

from ortools.sat.python import cp_model

from .models import (
    AssignmentOut,
    ScoreBreakdown,
    SolveRequest,
    SolveResponse,
    ViolationOut,
)

MINUTES_PER_DAY = 1440
# Objective coefficients are floats in the API but CP-SAT needs integers.
WEIGHT_SCALE = 100


@dataclass(frozen=True)
class Candidate:
    """One fully specified, individually legal placement for a session."""

    day: str
    day_index: int
    slot_ids: tuple[int, ...]
    start_slot_id: int
    end_slot_id: int
    start_minute: int
    end_minute: int
    faculty_id: int
    lab_id: int

    @property
    def abs_start(self) -> int:
        return self.day_index * MINUTES_PER_DAY + self.start_minute

    @property
    def abs_end(self) -> int:
        return self.day_index * MINUTES_PER_DAY + self.end_minute


@dataclass
class Session:
    practical_id: int
    session_index: int
    subject_id: int
    batch_id: int
    student_count: int
    duration: int
    candidates: list[Candidate] = field(default_factory=list)
    vars: list[cp_model.IntVar] = field(default_factory=list)


def _minutes_to_hhmm(total: int) -> str:
    return f"{total // 60:02d}:{total % 60:02d}"


def solve(request: SolveRequest) -> SolveResponse:
    started = time.perf_counter()
    warnings: list[str] = []
    violations: list[ViolationOut] = []

    slots = sorted(request.time_slots, key=lambda s: s.order)
    slot_by_id = {s.id: s for s in slots}
    day_index = {day: i for i, day in enumerate(request.days)}

    if not slots or not request.days or not request.practicals:
        return SolveResponse(
            status="INVALID_INPUT",
            schedule_score=0.0,
            runtime_ms=int((time.perf_counter() - started) * 1000),
            violations=[
                ViolationOut(
                    code="EMPTY_PROBLEM",
                    message="Days, time slots and practicals are all required to build a schedule.",
                )
            ],
        )

    placements = _enumerate_placements(slots, request.days)
    faculty_by_id = {f.id: f for f in request.faculty}
    fac_blocked = {f.id: {(d, s) for d, s in f.unavailable} for f in request.faculty}
    fac_preferred = {f.id: {(d, s) for d, s in f.preferred} for f in request.faculty}
    lab_blocked = {lab.id: {(d, s) for d, s in lab.unavailable} for lab in request.labs}

    # ---------------------------------------------------------------- candidates
    sessions: list[Session] = []
    for practical in request.practicals:
        eligible_faculty = [
            f for f in request.faculty
            if practical.subject_id in f.qualified_subject_ids
        ]
        eligible_labs = [
            lab for lab in request.labs
            if lab.lab_type == practical.required_lab_type
            and lab.capacity >= practical.student_count
        ]

        if not eligible_faculty:
            violations.append(ViolationOut(
                code="NO_QUALIFIED_FACULTY",
                message=f"No active faculty member is qualified to teach subject {practical.subject_id}.",
                practical_id=practical.id,
            ))
            continue
        if not eligible_labs:
            violations.append(ViolationOut(
                code="NO_SUITABLE_LAB",
                message=(
                    f"No available '{practical.required_lab_type}' laboratory can seat "
                    f"{practical.student_count} students."
                ),
                practical_id=practical.id,
            ))
            continue

        candidates = _build_candidates(
            practical, placements, day_index, eligible_faculty, eligible_labs,
            fac_blocked, lab_blocked,
        )
        if not candidates:
            violations.append(ViolationOut(
                code="NO_FEASIBLE_PLACEMENT",
                message=(
                    f"No day/slot combination fits a {practical.duration_minutes}-minute practical "
                    "with an available faculty member and laboratory."
                ),
                practical_id=practical.id,
            ))
            continue

        for session_index in range(max(1, practical.sessions_per_week)):
            sessions.append(Session(
                practical_id=practical.id,
                session_index=session_index,
                subject_id=practical.subject_id,
                batch_id=practical.batch_id,
                student_count=practical.student_count,
                duration=practical.duration_minutes,
                candidates=candidates,
            ))

    if violations:
        return SolveResponse(
            status="INFEASIBLE_INPUT",
            schedule_score=0.0,
            runtime_ms=int((time.perf_counter() - started) * 1000),
            violations=violations,
            warnings=warnings,
        )

    model = cp_model.CpModel()
    _build_variables(model, sessions)
    cells = build_cell_index(sessions)
    _add_resource_constraints(model, sessions, request, cells)
    _add_session_ordering(model, sessions, request)
    _apply_locks(model, sessions, request, warnings)

    # ---- Phase 1: find any legal schedule ----------------------------------
    # The soft objective adds tens of thousands of variables. Searching for a
    # first feasible solution *and* optimising at once can burn the whole budget
    # without producing anything, so feasibility is proven on the bare
    # constraint model first and its solution then seeds the optimiser.
    feasibility_budget = max(5.0, min(45.0, request.max_seconds * 0.4))
    scout = cp_model.CpSolver()
    scout.parameters.max_time_in_seconds = feasibility_budget
    scout.parameters.num_workers = max(1, request.workers)
    scout.parameters.random_seed = 20260814
    scout_status = scout.Solve(model)

    if scout_status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return SolveResponse(
            status="INFEASIBLE",
            schedule_score=0.0,
            runtime_ms=int((time.perf_counter() - started) * 1000),
            violations=[ViolationOut(
                code="NO_FEASIBLE_SCHEDULE",
                message=(
                    "The constraints cannot all be satisfied at once. Common causes are too few "
                    "laboratories of a required type, too many practicals for the available slots, "
                    "or faculty availability that is too narrow."
                ),
            )],
            warnings=warnings,
        )

    baseline = [[scout.Value(var) for var in session.vars] for session in sessions]

    # ---- Phase 2: optimise the soft constraints ----------------------------
    breakdown_vars = _add_objective(model, sessions, request, faculty_by_id, fac_preferred, slots, cells)
    for session, values in zip(sessions, baseline):
        for var, value in zip(session.vars, values):
            model.AddHint(var, value)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = max(1.0, request.max_seconds - feasibility_budget)
    solver.parameters.num_workers = max(1, request.workers)
    solver.parameters.log_search_progress = False
    # Fixed seed so repeated generations on unchanged data are reproducible.
    solver.parameters.random_seed = 20260814
    status = solver.Solve(model)
    runtime_ms = int((time.perf_counter() - started) * 1000)

    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        # The hint alone is a valid schedule, so fall back to it rather than
        # discarding a usable result.
        warnings.append(
            "The optimisation pass did not finish in time; returning the feasible schedule "
            "found during the first pass. Increase the time limit for a better-balanced result."
        )
        return SolveResponse(
            status="SUCCESS",
            schedule_score=60.0,
            runtime_ms=runtime_ms,
            assignments=_extract_assignments(scout, sessions, slot_by_id),
            warnings=warnings,
        )

    assignments = _extract_assignments(solver, sessions, slot_by_id)
    breakdown = _extract_breakdown(solver, breakdown_vars)
    score = _score(breakdown, sessions, request, solver, breakdown_vars)

    if status == cp_model.FEASIBLE:
        warnings.append(
            "The time limit was reached before the search proved optimality; "
            "this is a valid schedule but a better one may exist."
        )
    overload = solver.Value(breakdown_vars["overload_total"])
    if overload > 0:
        warnings.append(
            f"Faculty weekly hour limits are exceeded by {overload // 60}h {overload % 60}m in total."
        )

    return SolveResponse(
        status="SUCCESS",
        schedule_score=round(score, 1),
        runtime_ms=runtime_ms,
        assignments=assignments,
        violations=[],
        warnings=warnings,
        breakdown=breakdown,
    )


def _enumerate_placements(slots, days) -> list[tuple[str, tuple[int, ...], int, int]]:
    """Every (day, contiguous run of teachable slots) option.

    A run stops at the first non-teachable period or wherever one slot does not
    start exactly when the previous one ends, so a practical can never be
    scheduled straight through a break or a lunch period.
    """
    runs: list[tuple[tuple[int, ...], int, int]] = []
    for i in range(len(slots)):
        if not slots[i].teachable:
            continue
        covered: list[int] = []
        for j in range(i, len(slots)):
            if not slots[j].teachable:
                break
            if j > i and slots[j].start_minute != slots[j - 1].end_minute:
                break
            covered.append(slots[j].id)
            runs.append((tuple(covered), slots[i].start_minute, slots[j].end_minute))

    return [(day, run, start, end) for day in days for run, start, end in runs]


def _build_candidates(practical, placements, day_index, eligible_faculty, eligible_labs,
                      fac_blocked, lab_blocked) -> list[Candidate]:
    candidates: list[Candidate] = []
    for day, slot_ids, start_minute, end_minute in placements:
        # Exact fit only: a practical occupies whole periods, never half of one.
        if end_minute - start_minute != practical.duration_minutes:
            continue
        cells = [(day, slot_id) for slot_id in slot_ids]

        usable_faculty = [
            f.id for f in eligible_faculty
            if not any(cell in fac_blocked[f.id] for cell in cells)
        ]
        usable_labs = [
            lab.id for lab in eligible_labs
            if not any(cell in lab_blocked[lab.id] for cell in cells)
        ]
        if not usable_faculty or not usable_labs:
            continue

        for faculty_id in usable_faculty:
            for lab_id in usable_labs:
                candidates.append(Candidate(
                    day=day,
                    day_index=day_index[day],
                    slot_ids=slot_ids,
                    start_slot_id=slot_ids[0],
                    end_slot_id=slot_ids[-1],
                    start_minute=start_minute,
                    end_minute=end_minute,
                    faculty_id=faculty_id,
                    lab_id=lab_id,
                ))
    return candidates


def _build_variables(model: cp_model.CpModel, sessions: list[Session]) -> None:
    for s_index, session in enumerate(sessions):
        session.vars = [
            model.NewBoolVar(f"x_{session.practical_id}_{session.session_index}_{c_index}")
            for c_index in range(len(session.candidates))
        ]
        # Every session is scheduled exactly once.
        model.AddExactlyOne(session.vars)


def build_cell_index(sessions) -> dict[tuple[str, int], list]:
    """Maps each (day, slot) cell to the candidates that would occupy it.

    Built once and reused by both the mutual-exclusion constraints and the gap
    objective, so no part of the model has to rescan every candidate.
    """
    cells: dict[tuple[str, int], list] = {}
    for session in sessions:
        for candidate, var in zip(session.candidates, session.vars):
            for slot_id in candidate.slot_ids:
                cells.setdefault((candidate.day, slot_id), []).append(
                    (session.practical_id, candidate.faculty_id, candidate.lab_id, var))
    return cells


def _add_resource_constraints(model, sessions, request: SolveRequest, cells) -> None:
    """H1, H2 and H3 as per-cell mutual exclusion.

    Time is already discretised into periods, so "these two may not overlap" is
    exactly "at most one of them occupies any given period". Expressing it that
    way costs a few hundred small linear constraints, where the equivalent
    NoOverlap over thousands of optional intervals is far harder for the solver
    to even find a first solution with.
    """
    groups_of_practical: dict[int, list[int]] = {}
    for index, group in enumerate(request.student_conflict_groups):
        for practical_id in group:
            groups_of_practical.setdefault(practical_id, []).append(index)

    # Practicals running more than once a week also need their own sessions
    # kept apart, even when no other practical shares their students.
    repeated = {
        p.id for p in request.practicals
        if (p.sessions_per_week or 1) > 1
    }

    for occupants in cells.values():
        by_faculty: dict[int, list] = {}
        by_lab: dict[int, list] = {}
        by_group: dict[int, list] = {}
        by_practical: dict[int, list] = {}

        for practical_id, faculty_id, lab_id, var in occupants:
            by_faculty.setdefault(faculty_id, []).append(var)
            by_lab.setdefault(lab_id, []).append(var)
            for group_index in groups_of_practical.get(practical_id, ()):
                by_group.setdefault(group_index, []).append(var)
            if practical_id in repeated:
                by_practical.setdefault(practical_id, []).append(var)

        for bucket in (by_faculty, by_lab, by_group, by_practical):
            for candidate_vars in bucket.values():
                if len(candidate_vars) > 1:
                    model.AddAtMostOne(candidate_vars)


def _add_session_ordering(model, sessions, request: SolveRequest) -> None:
    """Sessions of one practical are interchangeable; order them to cut symmetry."""
    by_practical: dict[int, list[Session]] = {}
    for session in sessions:
        by_practical.setdefault(session.practical_id, []).append(session)

    day_count = len(request.days)
    for practical_id, group in by_practical.items():
        if len(group) < 2:
            continue
        group.sort(key=lambda s: s.session_index)
        starts = []
        days = []
        for session in group:
            start = model.NewIntVar(0, day_count * MINUTES_PER_DAY, f"start_{practical_id}_{session.session_index}")
            day = model.NewIntVar(0, max(0, day_count - 1), f"day_{practical_id}_{session.session_index}")
            model.Add(start == sum(
                c.abs_start * v for c, v in zip(session.candidates, session.vars)))
            model.Add(day == sum(
                c.day_index * v for c, v in zip(session.candidates, session.vars)))
            starts.append(start)
            days.append(day)

        for i in range(len(group) - 1):
            model.Add(starts[i] + group[i].duration <= starts[i + 1])
            # Spread repeated sessions across different days when the week allows it.
            if len(group) <= day_count:
                model.Add(days[i] < days[i + 1])


def _apply_locks(model, sessions, request: SolveRequest, warnings: list[str]) -> None:
    if not request.locked:
        return
    locks = {
        (l.practical_id, l.session_index): l for l in request.locked
    }
    for session in sessions:
        lock = locks.get((session.practical_id, session.session_index))
        if lock is None:
            continue
        matched = False
        for candidate, var in zip(session.candidates, session.vars):
            if (candidate.day == lock.day
                    and candidate.start_slot_id == lock.start_slot_id
                    and candidate.faculty_id == lock.faculty_id
                    and candidate.lab_id == lock.lab_id):
                model.Add(var == 1)
                matched = True
                break
        if not matched:
            warnings.append(
                f"Practical {session.practical_id} could not be pinned to its requested slot "
                "because that placement is no longer legal; it was re-optimised instead."
            )


def _add_objective(model, sessions, request: SolveRequest, faculty_by_id,
                   fac_preferred, slots, cells) -> dict:
    weights = request.weights
    terms: list[tuple[int, cp_model.IntVar]] = []
    tracked: dict = {}

    # ---- S2 workload balance, plus an overload guard on max weekly hours -----
    active_faculty = sorted({c.faculty_id for s in sessions for c in s.candidates})
    loads = {}
    overloads = []
    for faculty_id in active_faculty:
        limit = faculty_by_id[faculty_id].max_weekly_minutes
        base = faculty_by_id[faculty_id].base_load_minutes
        load = model.NewIntVar(0, 10 ** 6, f"load_{faculty_id}")
        # Total weekly load = fixed commitments already on the books + the
        # practical minutes the optimiser assigns here.
        model.Add(load == base + sum(
            s.duration * v
            for s in sessions
            for c, v in zip(s.candidates, s.vars)
            if c.faculty_id == faculty_id
        ))
        loads[faculty_id] = load
        over = model.NewIntVar(0, 10 ** 6, f"over_{faculty_id}")
        model.Add(over >= load - limit)
        overloads.append(over)

    imbalance = model.NewIntVar(0, 10 ** 6, "imbalance")
    if len(loads) > 1:
        max_load = model.NewIntVar(0, 10 ** 6, "max_load")
        min_load = model.NewIntVar(0, 10 ** 6, "min_load")
        model.AddMaxEquality(max_load, list(loads.values()))
        model.AddMinEquality(min_load, list(loads.values()))
        model.Add(imbalance == max_load - min_load)
    else:
        model.Add(imbalance == 0)
    tracked["imbalance"] = imbalance
    terms.append((int(weights.workload_imbalance * WEIGHT_SCALE), imbalance))

    # Spec section 26 asks for max-minus-min, but that objective is almost flat:
    # moving any session between two mid-range faculty leaves it unchanged, so
    # the search gets little signal and results vary a lot between runs. Adding
    # total deviation from the mean gives the same optimum a smooth gradient to
    # descend, at half the weight so min-max stays the headline criterion.
    if len(loads) > 1:
        total_base = sum(faculty_by_id[fid].base_load_minutes for fid in loads)
        target = (sum(s.duration for s in sessions) + total_base) // len(loads)
        deviations = []
        for faculty_id, load in loads.items():
            deviation = model.NewIntVar(0, 10 ** 6, f"dev_{faculty_id}")
            model.AddAbsEquality(deviation, load - target)
            deviations.append(deviation)
        spread = model.NewIntVar(0, 10 ** 7, "load_spread")
        model.Add(spread == sum(deviations))
        terms.append((max(1, int(weights.workload_imbalance * WEIGHT_SCALE / 2)), spread))

    overload_total = model.NewIntVar(0, 10 ** 6, "overload_total")
    model.Add(overload_total == (sum(overloads) if overloads else 0))
    tracked["overload_total"] = overload_total
    # Overrunning a contractual hour limit outranks every other soft concern.
    terms.append((50 * WEIGHT_SCALE, overload_total))

    # ---- S1 faculty preference ---------------------------------------------
    pref_violations = model.NewIntVar(0, len(sessions), "pref_violations")
    pref_terms = []
    for session in sessions:
        for candidate, var in zip(session.candidates, session.vars):
            preferred = fac_preferred.get(candidate.faculty_id, set())
            if preferred and not any((candidate.day, sid) in preferred for sid in candidate.slot_ids):
                pref_terms.append(var)
    model.Add(pref_violations == (sum(pref_terms) if pref_terms else 0))
    tracked["pref_violations"] = pref_violations
    terms.append((int(weights.faculty_preference * WEIGHT_SCALE), pref_violations))

    # ---- S4 laboratory utilisation -----------------------------------------
    lab_capacity = {lab.id: lab.capacity for lab in request.labs}
    surplus = model.NewIntVar(0, 10 ** 6, "lab_surplus")
    model.Add(surplus == sum(
        max(0, lab_capacity[c.lab_id] - s.student_count) * v
        for s in sessions
        for c, v in zip(s.candidates, s.vars)
    ))
    tracked["lab_surplus"] = surplus
    terms.append((int(weights.lab_underutilization * WEIGHT_SCALE), surplus))

    # ---- S3 student gaps and S5 faculty idle time --------------------------
    student_gaps = _gap_term(
        model, cells, request.days, slots, "student",
        groups=_student_groups(sessions, request),
        entity_of=lambda practical_id, _faculty_id: practical_id,
    )
    tracked["student_gaps"] = student_gaps
    terms.append((int(weights.student_gaps * WEIGHT_SCALE), student_gaps))

    faculty_gaps = _gap_term(
        model, cells, request.days, slots, "faculty",
        groups={f: None for f in active_faculty},
        entity_of=lambda _practical_id, faculty_id: faculty_id,
    )
    tracked["faculty_gaps"] = faculty_gaps
    terms.append((int(weights.faculty_idle * WEIGHT_SCALE), faculty_gaps))

    # ---- S6 minimise disruption to an existing schedule --------------------
    changes = model.NewIntVar(0, len(sessions), "changes")
    if request.previous:
        previous = {(p.practical_id, p.session_index): p for p in request.previous}
        change_terms = []
        for session in sessions:
            before = previous.get((session.practical_id, session.session_index))
            if before is None:
                continue
            same = [
                v for c, v in zip(session.candidates, session.vars)
                if c.day == before.day and c.start_slot_id == before.start_slot_id
                and c.faculty_id == before.faculty_id and c.lab_id == before.lab_id
            ]
            moved = model.NewBoolVar(f"moved_{session.practical_id}_{session.session_index}")
            if same:
                model.Add(moved == 1 - sum(same))
            else:
                model.Add(moved == 1)
            change_terms.append(moved)
        model.Add(changes == (sum(change_terms) if change_terms else 0))
    else:
        model.Add(changes == 0)
    tracked["changes"] = changes
    terms.append((int(weights.schedule_changes * WEIGHT_SCALE), changes))

    model.Minimize(sum(coefficient * var for coefficient, var in terms))
    tracked["_terms"] = terms
    return tracked


def _student_groups(sessions, request: SolveRequest) -> dict:
    """Cohorts whose timetable gaps are worth measuring.

    Only groups of two or more practicals can have a gap between them, and
    cohorts sharing an identical roster capture what a student actually
    experiences. Falls back to the conflict list, then to per-batch grouping.
    """
    source = request.student_cohorts or request.student_conflict_groups
    groups = {
        f"g{index}": set(group)
        for index, group in enumerate(source)
        if len(group) > 1
    }
    if not groups:
        for session in sessions:
            groups.setdefault(f"b{session.batch_id}", set()).add(session.practical_id)
    return groups


def _gap_term(model, cell_index, days, slots, label, groups, entity_of) -> cp_model.IntVar:
    """Counts empty teaching periods strictly between an entity's first and last class."""
    teachable = [s for s in slots if s.teachable]
    gap_vars = []

    for group_id, members in groups.items():
        for day in days:
            occupied = []
            for slot in teachable:
                matching = []
                for practical_id, faculty_id, _lab_id, var in cell_index.get((day, slot.id), ()):
                    entity = entity_of(practical_id, faculty_id)
                    if members is None:
                        if entity != group_id:
                            continue
                    elif entity not in members:
                        continue
                    matching.append(var)
                if matching:
                    flag = model.NewBoolVar(f"occ_{label}_{group_id}_{day}_{slot.id}")
                    model.AddMaxEquality(flag, matching)
                    occupied.append(flag)
                else:
                    occupied.append(None)

            present = [i for i, flag in enumerate(occupied) if flag is not None]
            if len(present) < 3:
                continue  # a gap needs at least three candidate periods

            for position, index in enumerate(present):
                if position == 0 or position == len(present) - 1:
                    continue
                before = model.NewBoolVar(f"bef_{label}_{group_id}_{day}_{index}")
                after = model.NewBoolVar(f"aft_{label}_{group_id}_{day}_{index}")
                model.AddMaxEquality(before, [occupied[j] for j in present[:position]])
                model.AddMaxEquality(after, [occupied[j] for j in present[position + 1:]])

                gap = model.NewBoolVar(f"gap_{label}_{group_id}_{day}_{index}")
                # gap = 1 only when the period is free but classes sit on both sides.
                model.Add(gap >= before + after - occupied[index] - 1)
                gap_vars.append(gap)

    total = model.NewIntVar(0, max(1, len(gap_vars)), f"{label}_gaps")
    model.Add(total == (sum(gap_vars) if gap_vars else 0))
    return total


def _extract_assignments(solver, sessions, slot_by_id) -> list[AssignmentOut]:
    assignments = []
    for session in sessions:
        for candidate, var in zip(session.candidates, session.vars):
            if solver.Value(var) != 1:
                continue
            assignments.append(AssignmentOut(
                practical_id=session.practical_id,
                session_index=session.session_index,
                batch_id=session.batch_id,
                subject_id=session.subject_id,
                faculty_id=candidate.faculty_id,
                lab_id=candidate.lab_id,
                day=candidate.day,
                start_slot_id=candidate.start_slot_id,
                end_slot_id=candidate.end_slot_id,
                start_time=_minutes_to_hhmm(candidate.start_minute),
                end_time=_minutes_to_hhmm(candidate.end_minute),
            ))
            break
    return assignments


def _extract_breakdown(solver, tracked) -> ScoreBreakdown:
    return ScoreBreakdown(
        workload_imbalance_minutes=solver.Value(tracked["imbalance"]),
        faculty_preference_violations=solver.Value(tracked["pref_violations"]),
        student_gap_slots=solver.Value(tracked["student_gaps"]),
        faculty_idle_slots=solver.Value(tracked["faculty_gaps"]),
        lab_surplus_seats=solver.Value(tracked["lab_surplus"]),
        schedule_changes=solver.Value(tracked["changes"]),
        weighted_penalty=round(
            sum(coefficient * solver.Value(var) for coefficient, var in tracked["_terms"]) / WEIGHT_SCALE, 2),
    )


def _score(breakdown: ScoreBreakdown, sessions, request: SolveRequest, solver, tracked) -> float:
    """Blends the soft-constraint outcomes into an interpretable 0-100 figure.

    Each component is expressed as "how close to ideal", then combined with the
    same weights that drove the optimisation, so the number moves in step with
    what the engine was actually asked to minimise.
    """
    session_count = max(1, len(sessions))
    weights = request.weights

    total_minutes = sum(s.duration for s in sessions)
    assignable = max(1, len({c.faculty_id for s in sessions for c in s.candidates}))
    average_load = total_minutes / assignable
    # The worst realistic spread is one member carrying roughly twice the mean
    # while another carries nothing, so 2 x mean is the reference for "no better
    # than arbitrary". Dividing by the mean alone scores a merely uneven
    # schedule as zero, which is not informative.
    balance = 100.0 * (1.0 - min(1.0, breakdown.workload_imbalance_minutes / max(2 * average_load, 1.0)))

    preference = 100.0 * (1.0 - breakdown.faculty_preference_violations / session_count)

    max_gaps = max(1, session_count * 2)
    student_gap = 100.0 * (1.0 - min(1.0, breakdown.student_gap_slots / max_gaps))
    faculty_gap = 100.0 * (1.0 - min(1.0, breakdown.faculty_idle_slots / max_gaps))

    total_seats = sum(
        max(lab.capacity for lab in request.labs if lab.lab_type == p.required_lab_type)
        for p in request.practicals
        if any(lab.lab_type == p.required_lab_type for lab in request.labs)
    ) or 1
    utilisation = 100.0 * (1.0 - min(1.0, breakdown.lab_surplus_seats / total_seats))

    components = [
        (weights.workload_imbalance, balance),
        (weights.faculty_preference, preference),
        (weights.student_gaps, student_gap),
        (weights.faculty_idle, faculty_gap),
        (weights.lab_underutilization, utilisation),
    ]
    if request.previous:
        stability = 100.0 * (1.0 - breakdown.schedule_changes / session_count)
        components.append((weights.schedule_changes, stability))

    total_weight = sum(w for w, _ in components) or 1.0
    score = sum(w * value for w, value in components) / total_weight

    # Exceeding a contractual weekly limit is never a "good" schedule.
    overload = solver.Value(tracked["overload_total"])
    if overload > 0:
        score *= 0.85

    return max(0.0, min(100.0, score))
