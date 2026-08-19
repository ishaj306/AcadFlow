"""Standalone sanity check for the CP-SAT engine.

Builds a small but non-trivial problem, solves it, and asserts that every hard
constraint actually holds in the returned schedule. Run with:

    .venv\\Scripts\\python.exe smoke_test.py
"""

from app.engine import solve
from app.models import FacultyIn, LabIn, PracticalIn, SolveRequest, TimeSlotIn


def build_request() -> SolveRequest:
    slots = [
        TimeSlotIn(id=1, order=1, start_minute=540, end_minute=600),   # 09:00-10:00
        TimeSlotIn(id=2, order=2, start_minute=600, end_minute=660),   # 10:00-11:00
        TimeSlotIn(id=3, order=3, start_minute=660, end_minute=720),   # 11:00-12:00
        TimeSlotIn(id=4, order=4, start_minute=720, end_minute=780, teachable=False),  # lunch
        TimeSlotIn(id=5, order=5, start_minute=780, end_minute=840),   # 13:00-14:00
        TimeSlotIn(id=6, order=6, start_minute=840, end_minute=900),   # 14:00-15:00
    ]
    labs = [
        LabIn(id=1, capacity=30, lab_type="Programming"),
        LabIn(id=2, capacity=30, lab_type="Programming"),
        LabIn(id=3, capacity=25, lab_type="Networking"),
    ]
    faculty = [
        FacultyIn(id=1, max_weekly_minutes=18 * 60, qualified_subject_ids=[101, 102]),
        FacultyIn(id=2, max_weekly_minutes=18 * 60, qualified_subject_ids=[101]),
        FacultyIn(id=3, max_weekly_minutes=18 * 60, qualified_subject_ids=[102, 103],
                  unavailable=[("MONDAY", 1), ("MONDAY", 2), ("MONDAY", 3)]),
    ]
    practicals = [
        PracticalIn(id=1, subject_id=101, batch_id=11, student_count=30,
                    required_lab_type="Programming", duration_minutes=120),
        PracticalIn(id=2, subject_id=101, batch_id=12, student_count=28,
                    required_lab_type="Programming", duration_minutes=120),
        PracticalIn(id=3, subject_id=102, batch_id=11, student_count=30,
                    required_lab_type="Programming", duration_minutes=120),
        PracticalIn(id=4, subject_id=102, batch_id=12, student_count=28,
                    required_lab_type="Programming", duration_minutes=120),
        PracticalIn(id=5, subject_id=103, batch_id=13, student_count=25,
                    required_lab_type="Networking", duration_minutes=120,
                    sessions_per_week=2),
    ]
    return SolveRequest(
        days=["MONDAY", "TUESDAY", "WEDNESDAY"],
        time_slots=slots,
        labs=labs,
        faculty=faculty,
        practicals=practicals,
        # Batch 11 attends both subject 101 and 102, likewise batch 12.
        student_conflict_groups=[[1, 3], [2, 4]],
        max_seconds=15.0,
    )


def overlaps(a, b) -> bool:
    if a.day != b.day:
        return False
    return a.start_time < b.end_time and b.start_time < a.end_time


def main() -> None:
    request = build_request()
    response = solve(request)

    print(f"status        : {response.status}")
    print(f"score         : {response.schedule_score}")
    print(f"runtime       : {response.runtime_ms} ms")
    print(f"assignments   : {len(response.assignments)}")
    print(f"breakdown     : {response.breakdown.model_dump()}")
    for w in response.warnings:
        print(f"warning       : {w}")
    for v in response.violations:
        print(f"violation     : {v.code} {v.message}")

    assert response.status == "SUCCESS", response.status
    # 4 single-session practicals + 1 practical with 2 sessions = 6 assignments.
    assert len(response.assignments) == 6, len(response.assignments)

    lookup = {p.id: p for p in request.practicals}
    labs = {lab.id: lab for lab in request.labs}
    faculty = {f.id: f for f in request.faculty}

    print("\nschedule")
    for a in sorted(response.assignments, key=lambda x: (x.day, x.start_time)):
        print(f"  {a.day:<10} {a.start_time}-{a.end_time}  practical {a.practical_id} "
              f"(session {a.session_index})  faculty {a.faculty_id}  lab {a.lab_id}")

    failures = []
    for i, a in enumerate(response.assignments):
        practical = lookup[a.practical_id]

        # H4 capacity, and the lab must match the required type.
        if labs[a.lab_id].capacity < practical.student_count:
            failures.append(f"H4 capacity violated for practical {a.practical_id}")
        if labs[a.lab_id].lab_type != practical.required_lab_type:
            failures.append(f"lab type mismatch for practical {a.practical_id}")

        # Qualification.
        if practical.subject_id not in faculty[a.faculty_id].qualified_subject_ids:
            failures.append(f"faculty {a.faculty_id} not qualified for practical {a.practical_id}")

        # H5 faculty availability.
        blocked = set(faculty[a.faculty_id].unavailable)
        for slot_id in (a.start_slot_id, a.end_slot_id):
            if (a.day, slot_id) in blocked:
                failures.append(f"H5 violated: faculty {a.faculty_id} unavailable {a.day} slot {slot_id}")

        # H7 never scheduled across the non-teachable lunch period.
        if a.start_time <= "12:00" < a.end_time and a.end_time > "13:00":
            failures.append(f"practical {a.practical_id} spans the lunch break")

        for b in response.assignments[i + 1:]:
            if not overlaps(a, b):
                continue
            if a.faculty_id == b.faculty_id:
                failures.append(f"H1 violated: faculty {a.faculty_id} double booked")
            if a.lab_id == b.lab_id:
                failures.append(f"H2 violated: lab {a.lab_id} double booked")
            for group in request.student_conflict_groups:
                if a.practical_id in group and b.practical_id in group:
                    failures.append(f"H3 violated: {a.practical_id} and {b.practical_id} overlap")
            if a.practical_id == b.practical_id:
                failures.append(f"sessions of practical {a.practical_id} overlap")

    if failures:
        print("\nFAILURES")
        for f in sorted(set(failures)):
            print(f"  - {f}")
        raise SystemExit(1)

    print("\nAll hard constraints hold (H1, H2, H3, H4, H5, H7 + qualification).")


if __name__ == "__main__":
    main()
