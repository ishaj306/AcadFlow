"""Request/response contract between the Spring Boot API and the CP-SAT engine.

The backend owns all persistence; this service receives a self-contained
snapshot of the scheduling problem and returns assignments. It never reads a
database and holds no state between calls.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

DayName = Literal[
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
]


class TimeSlotIn(BaseModel):
    id: int
    order: int
    start_minute: int = Field(description="Minutes from midnight, e.g. 09:00 -> 540")
    end_minute: int
    teachable: bool = True


class LabIn(BaseModel):
    id: int
    capacity: int
    lab_type: str
    # (day, time_slot_id) cells the lab cannot be used in - hard constraint H6.
    unavailable: list[tuple[DayName, int]] = Field(default_factory=list)


class FacultyIn(BaseModel):
    id: int
    max_weekly_minutes: int
    qualified_subject_ids: list[int] = Field(default_factory=list)
    # Hard constraint H5 / H9 (leave is expanded into unavailable cells upstream).
    unavailable: list[tuple[DayName, int]] = Field(default_factory=list)
    # Soft constraint S1.
    preferred: list[tuple[DayName, int]] = Field(default_factory=list)


class PracticalIn(BaseModel):
    id: int
    subject_id: int
    batch_id: int
    student_count: int
    required_lab_type: str
    duration_minutes: int
    sessions_per_week: int = 1
    preferred_faculty_id: int | None = None


class LockedAssignment(BaseModel):
    """Pins one session in place; used when repairing an existing timetable."""

    practical_id: int
    session_index: int
    day: DayName
    start_slot_id: int
    faculty_id: int
    lab_id: int


class PreviousAssignment(BaseModel):
    """Where a session sat before. Moving it away costs the S6 change penalty."""

    practical_id: int
    session_index: int
    day: DayName
    start_slot_id: int
    faculty_id: int
    lab_id: int


class Weights(BaseModel):
    """Objective coefficients (spec section 16); all tunable from the database."""

    workload_imbalance: float = 6.0
    faculty_preference: float = 2.0
    student_gaps: float = 3.0
    lab_underutilization: float = 1.0
    schedule_changes: float = 10.0
    faculty_idle: float = 2.0


class SolveRequest(BaseModel):
    days: list[DayName]
    time_slots: list[TimeSlotIn]
    labs: list[LabIn]
    faculty: list[FacultyIn]
    practicals: list[PracticalIn]
    # Each inner list is a set of practicals that share students and therefore
    # may never overlap in time - hard constraint H3. Partial overlaps between
    # cohorts appear here as two-element pairs, so this list can be long.
    student_conflict_groups: list[list[int]] = Field(default_factory=list)
    # Practicals attended by exactly the same students. Used for the student-gap
    # objective (S3), where the pairwise conflict list would create thousands of
    # near-duplicate constraints for no extra accuracy.
    student_cohorts: list[list[int]] = Field(default_factory=list)
    locked: list[LockedAssignment] = Field(default_factory=list)
    previous: list[PreviousAssignment] = Field(default_factory=list)
    weights: Weights = Field(default_factory=Weights)
    max_seconds: float = 30.0
    workers: int = 8


class AssignmentOut(BaseModel):
    practical_id: int
    session_index: int
    batch_id: int
    subject_id: int
    faculty_id: int
    lab_id: int
    day: str
    start_slot_id: int
    end_slot_id: int
    start_time: str
    end_time: str


class ViolationOut(BaseModel):
    code: str
    message: str
    practical_id: int | None = None


class ScoreBreakdown(BaseModel):
    workload_imbalance_minutes: int = 0
    faculty_preference_violations: int = 0
    student_gap_slots: int = 0
    faculty_idle_slots: int = 0
    lab_surplus_seats: int = 0
    schedule_changes: int = 0
    weighted_penalty: float = 0.0


class SolveResponse(BaseModel):
    status: str
    schedule_score: float
    engine: str = "ortools-cpsat"
    runtime_ms: int
    assignments: list[AssignmentOut] = Field(default_factory=list)
    violations: list[ViolationOut] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    breakdown: ScoreBreakdown = Field(default_factory=ScoreBreakdown)


# --------------------------------------------------------------- feasibility


class BlockerItem(BaseModel):
    """A hard reason one practical cannot be placed at all."""

    code: str
    message: str
    practical_id: int | None = None
    subject_id: int | None = None


class ResourceCheck(BaseModel):
    """One necessary-condition test: enough of some resource for the demand on it."""

    key: str
    label: str
    required: int
    available: int
    unit: str = "sessions"
    satisfied: bool
    utilization_percent: float
    detail: str


class Suggestion(BaseModel):
    """A concrete change that would relieve a shortage, with its capacity gain."""

    code: str
    title: str
    detail: str
    category: str
    estimated_gain_sessions: int | None = None


class BatchLoad(BaseModel):
    batch_id: int
    sessions_required: int
    lab_type: str


class FeasibilityReport(BaseModel):
    verdict: str  # FEASIBLE | TIGHT | INFEASIBLE
    runtime_ms: int
    total_sessions_required: int = 0
    total_session_capacity: int = 0
    blockers: list[BlockerItem] = Field(default_factory=list)
    resource_checks: list[ResourceCheck] = Field(default_factory=list)
    batch_loads: list[BatchLoad] = Field(default_factory=list)
    suggestions: list[Suggestion] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
