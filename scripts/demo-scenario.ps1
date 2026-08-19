<#
.SYNOPSIS
    Drives the full 16-step demonstration scenario against a running backend.

.DESCRIPTION
    Exercises the whole pipeline end to end and asserts the result at each
    stage: seed data -> practical batches -> CP-SAT timetable generation ->
    independent validation -> approval and publication -> faculty leave ->
    automatic rescheduling -> approval of the chosen alternative -> role-scoped
    views and the workload dashboard.

    Start both services first:
        solver   : solver\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8090
        backend  : mvnw spring-boot:run          (from backend\)

    The installation must already contain data. Either enter it through the
    application, or run scripts/sample-data.ps1 first.

.PARAMETER AdminPassword
    Password for the administrator account, printed once in the backend log the
    first time it starts against an empty database.

.PARAMETER BaseUrl
    Backend API root. Defaults to http://127.0.0.1:8080/api

.PARAMETER SolveSeconds
    Time budget handed to the optimizer. Larger values give better schedules.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [int]$SolveSeconds = 60,
    [string]$AdminUsername = "admin"
)

$ErrorActionPreference = 'Stop'
$script:failures = 0

function Write-Step($number, $text) {
    Write-Host ""
    Write-Host ("  STEP {0,-2}  {1}" -f $number, $text) -ForegroundColor Cyan
    Write-Host ("  " + ("-" * 68)) -ForegroundColor DarkGray
}

function Assert($condition, $message) {
    if ($condition) {
        Write-Host "    [PASS] $message" -ForegroundColor Green
    } else {
        Write-Host "    [FAIL] $message" -ForegroundColor Red
        $script:failures++
    }
}

function Info($text) { Write-Host "           $text" -ForegroundColor Gray }

function Invoke-Api($Method, $Path, $Body, $Headers) {
    $uri = "$BaseUrl$Path"
    if ($null -ne $Body) {
        return Invoke-RestMethod -Uri $uri -Method $Method -Headers $Headers `
            -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 6) -TimeoutSec 600
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $Headers -TimeoutSec 600
}

Write-Host ""
Write-Host "  Smart Practical Batch and Timetable Generator - demonstration" -ForegroundColor White
Write-Host "  $BaseUrl" -ForegroundColor DarkGray

# ---------------------------------------------------------------- steps 1-5
Write-Step 1 "Administrator signs in; master data is present"
$login = Invoke-Api POST "/auth/login" @{ username = $AdminUsername; password = $AdminPassword } $null
$H = @{ Authorization = "Bearer $($login.accessToken)" }
Assert ($login.user.role -eq 'ADMIN') "signed in as $($login.user.fullName) [$($login.user.role)]"

$term     = Invoke-Api GET "/config/terms/current" $null $H
$students = Invoke-Api GET "/students?size=1" $null $H
$faculty  = Invoke-Api GET "/faculty?size=1" $null $H
$labs     = Invoke-Api GET "/labs" $null $H
$subjects = Invoke-Api GET "/subjects?practicalOnly=true" $null $H
$slots    = Invoke-Api GET "/config/time-slots" $null $H
$days     = Invoke-Api GET "/config/working-days" $null $H

Info "term $($term.label)"
Info "students=$($students.totalElements)  faculty=$($faculty.totalElements)  labs=$($labs.Count)  practical subjects=$($subjects.Count)"
Info "time slots=$($slots.Count)  working days=$(($days | Where-Object active).Count)"
if ($students.totalElements -eq 0 -or $faculty.totalElements -eq 0 -or $labs.Count -eq 0 -or $subjects.Count -eq 0) {
    Write-Host ""
    Write-Host "  The installation has no data yet." -ForegroundColor Yellow
    Write-Host "  Enter it through the application, or run:" -ForegroundColor Yellow
    Write-Host "      powershell -File scripts/sample-data.ps1 -AdminPassword <password>" -ForegroundColor Yellow
    exit 1
}
Assert ($students.totalElements -gt 0) "students on record"
Assert ($faculty.totalElements -gt 0)  "faculty on record"
Assert ($labs.Count -gt 0)             "laboratories on record"
Assert ($subjects.Count -gt 0)         "practical subjects on record"
Assert (($days | Where-Object active).Count -gt 0) "at least one working day is active"

# ------------------------------------------------------------------- step 6
Write-Step 6 "Generate practical batches"
$departments = Invoke-Api GET "/config/departments" $null $H
$totalBatches = 0
foreach ($d in $departments) {
    $result = Invoke-Api POST "/batches/generate" @{ departmentId = $d.id; semester = 5; regenerate = $true } $H
    Info "$($d.code): $($result.batchesCreated) batches from $($result.subjectsProcessed) subjects"
    $result.warnings | ForEach-Object { Info "  warning: $_" }
    $totalBatches += $result.batchesCreated
}
$batches = Invoke-Api GET "/batches" $null $H
Assert ($batches.Count -eq $totalBatches) "$totalBatches practical batches created"

# Even-distribution rule: no batch may exceed capacity, and sizes differ by <= 1.
$uneven = @()
$batches | Group-Object { "$($_.subjectCode)|$($_.division)" } | ForEach-Object {
    $sizes = $_.Group | ForEach-Object { $_.studentCount }
    if ((($sizes | Measure-Object -Maximum).Maximum - ($sizes | Measure-Object -Minimum).Minimum) -gt 1) {
        $uneven += $_.Name
    }
}
$overCapacity = $batches | Where-Object { $_.studentCount -gt $_.capacity }
Assert ($uneven.Count -eq 0)       "every division is split evenly (sizes differ by at most one)"
Assert ($overCapacity.Count -eq 0) "no batch exceeds its laboratory capacity"

$sample = $batches | Where-Object { $_.subjectCode -eq $batches[0].subjectCode -and $_.division -eq 'A' }
$sizes = ($sample | ForEach-Object { "$($_.batchName)=$($_.studentCount)" }) -join ", "
Info "example - $($sample[0].subjectCode) division A: $sizes (capacity $($sample[0].capacity))"

# ----------------------------------------------------------------- steps 7-9
Write-Step 7 "Coordinator runs timetable generation (CP-SAT optimizer)"
$sw = [Diagnostics.Stopwatch]::StartNew()
$generation = Invoke-Api POST "/timetable/generate" @{ name = "Practical Timetable $($term.label)"; maxSeconds = $SolveSeconds } $H
$sw.Stop()

$timetableId = $generation.preview.timetable.id
Info "engine $($generation.metrics.engine), $([math]::Round($generation.metrics.runtimeMs/1000,1))s solve / $([math]::Round($sw.Elapsed.TotalSeconds,1))s total"
Info "schedule score $($generation.preview.timetable.score) / 100 over $($generation.preview.timetable.entryCount) sessions"
Info "workload imbalance $($generation.metrics.workloadImbalanceMinutes) min, preference misses $($generation.metrics.facultyPreferenceViolations), student gaps $($generation.metrics.studentGapSlots)"
$generation.solverWarnings | ForEach-Object { Info "note: $_" }
Assert ($generation.status -eq 'SUCCESS') "optimizer returned a schedule"
Assert ($generation.preview.timetable.entryCount -eq $batches.Count) "every practical batch was scheduled"

Write-Step 9 "Independent validation of the generated schedule"
$v = $generation.preview.validation
Info "faculty conflicts   : $($v.facultyConflicts)"
Info "laboratory conflicts: $($v.labConflicts)"
Info "student conflicts   : $($v.studentConflicts)"
Info "capacity violations : $($v.capacityViolations)"
Info "availability / qualification: $($v.availabilityViolations) / $($v.qualificationViolations)"
Info "lab utilisation $($v.labUtilizationPercent)%, workload balance $($v.facultyWorkloadBalance)"
Assert ($v.facultyConflicts -eq 0)    "no faculty double bookings (H1)"
Assert ($v.labConflicts -eq 0)        "no laboratory double bookings (H2)"
Assert ($v.studentConflicts -eq 0)    "no student batch overlaps (H3)"
Assert ($v.capacityViolations -eq 0)  "no capacity violations (H4)"
Assert ($v.availabilityViolations -eq 0) "no availability violations (H5/H6)"
Assert ($v.qualificationViolations -eq 0) "no unqualified assignments"
Assert ($v.hardViolations -eq 0)      "zero hard constraint violations overall"
Assert $v.publishable                 "schedule is publishable"

# ------------------------------------------------------------------ step 10
Write-Step 10 "Coordinator approves and publishes"
$published = Invoke-Api POST "/timetable/$timetableId/approve" $null $H
Info "status $($published.timetable.status), approved by $($published.timetable.approvedBy)"
Assert ($published.timetable.status -eq 'PUBLISHED') "timetable published"

# --------------------------------------------------------------- steps 11-13
Write-Step 11 "A faculty member goes on leave"
$current = Invoke-Api GET "/timetable/current" $null $H
$target = $current.entries | Select-Object -First 1
Info "affected practical: $($target.subjectName) / $($target.batchName) (div $($target.division))"
Info "currently $($target.dayOfWeek) $($target.startTime.Substring(0,5))-$($target.endTime.Substring(0,5)), $($target.facultyName), $($target.labName)"

$date = [datetime]::Parse($term.startDate).AddDays(30)
while ($date.DayOfWeek.ToString().ToUpper() -ne $target.dayOfWeek) { $date = $date.AddDays(1) }
$leave = Invoke-Api POST "/faculty/leaves" @{
    facultyId = $target.facultyId
    startDate = $date.ToString('yyyy-MM-dd')
    endDate   = $date.ToString('yyyy-MM-dd')
    leaveType = "MEDICAL"
    reason    = "Medical leave"
} $H
$leave = Invoke-Api POST "/faculty/leaves/$($leave.id)/approve" $null $H
Assert ($leave.status -eq 'APPROVED') "leave approved for $($leave.facultyName) on $($leave.startDate)"

Write-Step 12 "System identifies affected practicals and scores alternatives"
$proposals = Invoke-Api POST "/rescheduling/from-leave/$($leave.id)" $null $H
Assert ($proposals.Count -ge 1) "$($proposals.Count) rescheduling proposal(s) raised automatically"
$proposal = $proposals[0]

Write-Step 13 "Ranked alternatives"
Write-Host ("           {0,-5} {1,-10} {2,-13} {3,-24} {4,-22} {5}" -f "Rank","Day","Time","Faculty","Laboratory","Score") -ForegroundColor DarkGray
foreach ($c in $proposal.candidates) {
    Write-Host ("           {0,-5} {1,-10} {2,-13} {3,-24} {4,-22} {5}" -f `
        $c.rank, $c.dayOfWeek, "$($c.startTime.Substring(0,5))-$($c.endTime.Substring(0,5))", `
        $c.facultyName, $c.labName, $c.score)
}
Info "top candidate reasoning: $($proposal.candidates[0].scoreBreakdown)"
Assert ($proposal.candidates.Count -ge 1) "alternatives found and ranked by disruption"
$descending = $true
for ($i = 1; $i -lt $proposal.candidates.Count; $i++) {
    if ($proposal.candidates[$i].score -gt $proposal.candidates[$i-1].score) { $descending = $false }
}
Assert $descending "candidates are ordered best-first"

# ------------------------------------------------------------------ step 14
Write-Step 14 "Coordinator accepts the recommendation"
$applied = Invoke-Api POST "/rescheduling/$($proposal.id)/approve" @{ candidateId = $proposal.candidates[0].id } $H
Info "$($applied.originalDay) $($applied.originalStart.Substring(0,5)) [$($applied.originalFacultyName)]  ->  $($applied.newDay) $($applied.newStart.Substring(0,5)) [$($applied.newFacultyName)] in $($applied.newLabName)"
Assert ($applied.status -eq 'APPLIED') "rescheduling applied and recorded for audit"

# ------------------------------------------------------------------ step 15
Write-Step 15 "Everyone sees the updated schedule"
$current = Invoke-Api GET "/timetable/current" $null $H
$moved = $current.entries | Where-Object { $_.id -eq $applied.timetableEntryId }
Assert ($moved.dayOfWeek -eq $applied.newDay) "published timetable reflects the move ($($moved.dayOfWeek) $($moved.startTime.Substring(0,5)))"

$after = Invoke-Api GET "/timetable/$timetableId/validate" $null $H
Assert ($after.hardViolations -eq 0) "still zero hard violations after rescheduling"

$unread = Invoke-Api GET "/notifications/unread-count" $null $H
$recent = Invoke-Api GET "/notifications/recent" $null $H
$change = $recent | Where-Object { $_.category -eq 'SCHEDULE_CHANGE' } | Select-Object -First 1
Assert ($null -ne $change) "affected users notified ($($unread.count) unread)"
if ($change) { Info $change.body }

$facultyView = Invoke-Api GET "/timetable/faculty/$($moved.facultyId)" $null $H
Info "faculty view for $($moved.facultyName): $($facultyView.entries.Count) sessions this week"
Assert ($facultyView.entries.Count -ge 1) "faculty timetable view works"

$batchView = Invoke-Api GET "/timetable/batch/$($moved.batchId)" $null $H
Assert ($batchView.entries.Count -ge 1) "batch timetable view works"

# ------------------------------------------------------------------ step 16
Write-Step 16 "Workload dashboard reflects the change"
$workload = Invoke-Api GET "/workload" $null $H
Info "$($workload.facultyCount) faculty, average utilisation $($workload.averageUtilizationPercent)%, spread $($workload.spreadHours)h ($($workload.balanceVerdict))"
Info "overloaded=$($workload.overloadedCount)  near limit=$($workload.nearLimitCount)  balanced=$($workload.balancedCount)  under-utilised=$($workload.underutilizedCount)"
Write-Host ("           {0,-26} {1,-10} {2,-8} {3,-8} {4}" -f "Faculty","Assigned","Max","Util %","Status") -ForegroundColor DarkGray
$workload.faculty | Sort-Object utilizationPercent -Descending | Select-Object -First 5 | ForEach-Object {
    Write-Host ("           {0,-26} {1,-10} {2,-8} {3,-8} {4}" -f $_.facultyName, "$($_.assignedHours)h", "$($_.maxWeeklyHours)h", $_.utilizationPercent, $_.status)
}
Assert ($workload.overloadedCount -eq 0) "no faculty member is over their weekly limit"

$dashboard = Invoke-Api GET "/dashboard" $null $H
$c = $dashboard.counters
Info "dashboard: students=$($c.totalStudents) faculty=$($c.totalFaculty) labs=$($c.totalLabs) batches=$($c.practicalBatches) todaysPracticals=$($c.todaysPracticals) openConflicts=$($c.activeConflicts) labUtil=$($c.labUtilizationPercent)% score=$($c.scheduleScore)"
Assert ($null -ne $dashboard.publishedTimetableName) "dashboard reports the published timetable"

# ------------------------------------------------------------------- summary
Write-Host ""
Write-Host ("  " + ("=" * 68)) -ForegroundColor DarkGray
if ($script:failures -eq 0) {
    Write-Host "  DEMONSTRATION COMPLETE - all checks passed" -ForegroundColor Green
    exit 0
}
Write-Host "  DEMONSTRATION FINISHED WITH $($script:failures) FAILED CHECK(S)" -ForegroundColor Red
exit 1
