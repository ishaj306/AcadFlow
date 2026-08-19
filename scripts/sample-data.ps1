<#
.SYNOPSIS
    Optionally populates an empty installation with a realistic sample college.

.DESCRIPTION
    This is NOT built into the application. The database ships empty apart from
    the roles and one administrator; this script simply calls the same public
    API you would use by hand, so nothing here is hidden seeding. Run it only if
    you want a populated system to try things out, and delete the data (or the
    backend/data folder) whenever you like.

    Everything it creates is ordinary data you could type in yourself:
      2 departments, 1 academic term, 8 periods, Mon-Fri working days,
      10 practical subjects, 8 laboratories, 18 faculty with qualifications,
      and 520 students imported as CSV.

.PARAMETER AdminPassword
    Password for the administrator account. Printed once in the backend log the
    first time it starts against an empty database.

.EXAMPLE
    powershell -File scripts/sample-data.ps1 -AdminPassword "S8gaxTBf99KBZYpb"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AdminPassword,
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [string]$AdminUsername = "admin"
)

$ErrorActionPreference = 'Stop'

function Say($text, $colour = 'Gray') { Write-Host "  $text" -ForegroundColor $colour }

$login = Invoke-RestMethod "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = $AdminUsername; password = $AdminPassword } | ConvertTo-Json)
$H = @{ Authorization = "Bearer $($login.accessToken)" }
Say "Signed in as $($login.user.fullName)" Green

function Post($path, $body) {
    return Invoke-RestMethod "$BaseUrl$path" -Method Post -Headers $H `
        -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6)
}

# ------------------------------------------------------------- departments
Write-Host "`n  Departments" -ForegroundColor Cyan
$departments = @{}
foreach ($d in @(
    @{ code = 'CSE'; name = 'Computer Science and Engineering' },
    @{ code = 'IT';  name = 'Information Technology' })) {
    $created = Post "/config/departments" $d
    $departments[$d.code] = $created.id
    Say "$($created.code) - $($created.name)"
}

# -------------------------------------------------------------------- term
Write-Host "`n  Academic term" -ForegroundColor Cyan
$term = Post "/config/terms" @{
    academicYear = '2026-27'; semester = 5
    startDate = '2026-07-20'; endDate = '2026-11-27'; makeCurrent = $true
}
Say "$($term.label) (current)"

# ------------------------------------------------------------------ periods
Write-Host "`n  Periods and working days" -ForegroundColor Cyan
$periods = @(
    @{ label = '09:00 - 10:00'; startTime = '09:00'; endTime = '10:00'; slotOrder = 1; slotType = 'TEACHING' },
    @{ label = '10:00 - 11:00'; startTime = '10:00'; endTime = '11:00'; slotOrder = 2; slotType = 'TEACHING' },
    @{ label = '11:00 - 12:00'; startTime = '11:00'; endTime = '12:00'; slotOrder = 3; slotType = 'TEACHING' },
    @{ label = '12:00 - 13:00'; startTime = '12:00'; endTime = '13:00'; slotOrder = 4; slotType = 'TEACHING' },
    @{ label = '13:00 - 14:00'; startTime = '13:00'; endTime = '14:00'; slotOrder = 5; slotType = 'LUNCH' },
    @{ label = '14:00 - 15:00'; startTime = '14:00'; endTime = '15:00'; slotOrder = 6; slotType = 'TEACHING' },
    @{ label = '15:00 - 16:00'; startTime = '15:00'; endTime = '16:00'; slotOrder = 7; slotType = 'TEACHING' },
    @{ label = '16:00 - 17:00'; startTime = '16:00'; endTime = '17:00'; slotOrder = 8; slotType = 'TEACHING' })
foreach ($p in $periods) { Post "/config/time-slots" ($p + @{ active = $true }) | Out-Null }
Say "$($periods.Count) periods (lunch 13:00-14:00)"

$days = Invoke-RestMethod "$BaseUrl/config/working-days" -Headers $H
$weekdays = @('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY')
foreach ($day in $days) { $day.active = $weekdays -contains $day.dayOfWeek }
Invoke-RestMethod "$BaseUrl/config/working-days" -Method Put -Headers $H `
    -ContentType 'application/json' -Body ($days | ConvertTo-Json -Depth 4) | Out-Null
Say "Monday to Friday active"

# ----------------------------------------------------------------- subjects
Write-Host "`n  Subjects" -ForegroundColor Cyan
$subjectSeed = @(
    @{ code = 'CS501'; name = 'Java Programming';            dept = 'CSE'; lab = 'Programming'; batch = 30 },
    @{ code = 'CS502'; name = 'Database Management Systems'; dept = 'CSE'; lab = 'Database';    batch = 30 },
    @{ code = 'CS503'; name = 'Operating Systems';           dept = 'CSE'; lab = 'Programming'; batch = 30 },
    @{ code = 'CS504'; name = 'Software Engineering';        dept = 'CSE'; lab = 'Programming'; batch = 30 },
    @{ code = 'CS505'; name = 'Computer Networks';           dept = 'CSE'; lab = 'Networking';  batch = 25 },
    @{ code = 'IT501'; name = 'Web Technologies';            dept = 'IT';  lab = 'Web';         batch = 30 },
    @{ code = 'IT502'; name = 'Data Structures';             dept = 'IT';  lab = 'Programming'; batch = 30 },
    @{ code = 'IT503'; name = 'Computer Hardware';           dept = 'IT';  lab = 'Hardware';    batch = 25 },
    @{ code = 'IT504'; name = 'Python Programming';          dept = 'IT';  lab = 'Programming'; batch = 30 },
    @{ code = 'IT505'; name = 'Information Security';        dept = 'IT';  lab = 'Networking';  batch = 25 })

$subjects = @{}
foreach ($s in $subjectSeed) {
    $created = Post "/subjects" @{
        subjectCode = $s.code; subjectName = $s.name
        departmentId = $departments[$s.dept]; semester = 5
        subjectType = 'PRACTICAL'; practicalDurationMin = 120; sessionsPerWeek = 1
        studentsPerBatch = $s.batch; requiredLabType = $s.lab; status = 'ACTIVE'
    }
    $subjects[$s.code] = $created.id
}
Say "$($subjectSeed.Count) practical subjects"

# ------------------------------------------------------------- laboratories
Write-Host "`n  Laboratories" -ForegroundColor Cyan
$labSeed = @(
    @{ code = 'PL1'; name = 'Programming Lab 1';     dept = 'CSE'; cap = 30; type = 'Programming'; loc = 'Block A - 204' },
    @{ code = 'PL2'; name = 'Programming Lab 2';     dept = 'CSE'; cap = 30; type = 'Programming'; loc = 'Block A - 205' },
    @{ code = 'DBL'; name = 'Database Lab';          dept = 'CSE'; cap = 30; type = 'Database';    loc = 'Block A - 301' },
    @{ code = 'NL1'; name = 'Networking Lab 1';      dept = 'CSE'; cap = 25; type = 'Networking';  loc = 'Block B - 102' },
    @{ code = 'PL3'; name = 'Programming Lab 3';     dept = 'IT';  cap = 30; type = 'Programming'; loc = 'Block B - 210' },
    @{ code = 'WTL'; name = 'Web Technologies Lab';  dept = 'IT';  cap = 30; type = 'Web';         loc = 'Block B - 201' },
    @{ code = 'HWL'; name = 'Hardware Lab';          dept = 'IT';  cap = 25; type = 'Hardware';    loc = 'Block B - 105' },
    @{ code = 'NL2'; name = 'Networking Lab 2';      dept = 'IT';  cap = 25; type = 'Networking';  loc = 'Block B - 108' })
foreach ($l in $labSeed) {
    Post "/labs" @{
        labCode = $l.code; labName = $l.name; departmentId = $departments[$l.dept]
        capacity = $l.cap; labType = $l.type; location = $l.loc; status = 'ACTIVE'
    } | Out-Null
}
Say "$($labSeed.Count) laboratories"

# ------------------------------------------------------------------ faculty
Write-Host "`n  Faculty" -ForegroundColor Cyan
$cseNames = @(
    'Dr. Anjali Sharma', 'Prof. Vikram Deshmukh', 'Dr. Kavita Rane', 'Prof. Sandeep Nair',
    'Dr. Neha Kulkarni', 'Prof. Arjun Mehta', 'Dr. Sunita Joshi', 'Prof. Rohit Bhosale',
    'Prof. Pooja Chavan')
$itNames = @(
    'Dr. Suresh Menon', 'Prof. Ritu Agarwal', 'Dr. Manoj Patil', 'Prof. Sneha Kadam',
    'Dr. Amit Verma', 'Prof. Deepa Naik', 'Dr. Prakash Rao', 'Prof. Shweta Gokhale',
    'Prof. Nilesh Jadhav')
$designations = @('Professor', 'Associate Professor', 'Assistant Professor')
$maxHours = @{ 'Professor' = 16; 'Associate Professor' = 18; 'Assistant Professor' = 20 }

function Add-Faculty($names, $deptCode, $prefix, $subjectCodes) {
    $ids = @()
    for ($i = 0; $i -lt $names.Count; $i++) {
        $designation = $designations[$i % $designations.Count]
        $code = "$prefix-F{0:D2}" -f ($i + 1)
        # Four qualified staff per subject gives the optimiser room to balance load.
        $qualified = @()
        for ($s = 0; $s -lt $subjectCodes.Count; $s++) {
            for ($offset = 0; $offset -lt 4; $offset++) {
                if ((($s * 2 + $offset) % $names.Count) -eq $i) { $qualified += $subjects[$subjectCodes[$s]] }
            }
        }
        $created = Post "/faculty" @{
            employeeCode = $code; name = $names[$i]
            email = "$($code.ToLower().Replace('-', '.'))@college.edu"
            departmentId = $departments[$deptCode]; designation = $designation
            maxWeeklyHours = $maxHours[$designation]; status = 'ACTIVE'
            subjectIds = @($qualified | Select-Object -Unique)
        }
        $ids += $created.id
    }
    return $ids
}

Add-Faculty $cseNames 'CSE' 'CSE' @('CS501', 'CS502', 'CS503', 'CS504', 'CS505') | Out-Null
Add-Faculty $itNames  'IT'  'IT'  @('IT501', 'IT502', 'IT503', 'IT504', 'IT505') | Out-Null
Say "$($cseNames.Count + $itNames.Count) faculty, each qualified for their department's subjects"

# ----------------------------------------------------------------- students
Write-Host "`n  Students (via CSV import)" -ForegroundColor Cyan
$first = @('Aarav','Ananya','Rohan','Isha','Karan','Priya','Siddharth','Meera','Aditya','Sanya',
           'Vivek','Riya','Nikhil','Tanvi','Harsh','Diya','Yash','Sneha','Rahul','Nikita',
           'Omkar','Pallavi','Sameer','Aditi','Kunal','Shruti','Varun','Nandini','Akash','Kritika')
$last  = @('Sharma','Patel','Reddy','Nair','Iyer','Desai','Kulkarni','Joshi','Mehta','Shah',
           'Rao','Gupta','Bose','Chauhan','Pillai','Kapoor','Malhotra','Bhatt','Sinha','Menon')

$random = New-Object System.Random 20260814
$rows = [System.Collections.Generic.List[string]]::new()
$rows.Add('roll_number,name,email,department_code,semester,year,division')

function Add-Cohort($prefix, $deptCode, $semester, $studyYear, $divisions, $perDivision) {
    foreach ($division in $divisions) {
        for ($i = 1; $i -le $perDivision; $i++) {
            $roll = "$prefix$semester$division{0:D3}" -f $i
            $name = "$($first[$random.Next($first.Count)]) $($last[$random.Next($last.Count)])"
            $rows.Add("$roll,$name,$($roll.ToLower())@college.edu,$deptCode,$semester,$studyYear,$division")
        }
    }
}

Add-Cohort 'CS' 'CSE' 5 3 @('A', 'B', 'C') 60
Add-Cohort 'IT' 'IT'  5 3 @('A', 'B', 'C') 60
Add-Cohort 'CS' 'CSE' 3 2 @('A', 'B') 40
Add-Cohort 'IT' 'IT'  3 2 @('A', 'B') 40

$csvPath = Join-Path $env:TEMP 'batchmaker-students.csv'
Set-Content -Path $csvPath -Value ($rows -join "`r`n") -Encoding UTF8

# Windows PowerShell 5.1 has no -Form, so the multipart body is built by hand.
$boundary = [Guid]::NewGuid().ToString()
$csvText = [IO.File]::ReadAllText($csvPath)
$multipart = "--$boundary`r`n" +
    "Content-Disposition: form-data; name=`"file`"; filename=`"students.csv`"`r`n" +
    "Content-Type: text/csv`r`n`r`n" +
    "$csvText`r`n--$boundary--`r`n"

$import = Invoke-RestMethod "$BaseUrl/students/import?updateExisting=true" -Method Post -Headers $H `
    -ContentType "multipart/form-data; boundary=$boundary" `
    -Body ([Text.Encoding]::UTF8.GetBytes($multipart))
Remove-Item $csvPath -ErrorAction SilentlyContinue

Say "$($import.imported) imported, $($import.updated) updated, $($import.skipped) skipped"
if ($import.errors.Count -gt 0) {
    $import.errors | Select-Object -First 5 | ForEach-Object {
        Write-Host "    row $($_.rowNumber): $($_.message)" -ForegroundColor Yellow
    }
}

# ------------------------------------------------------------------ summary
$status = Invoke-RestMethod "$BaseUrl/setup/status" -Headers $H
Write-Host ""
Write-Host "  Setup: $($status.completedSteps) of $($status.totalSteps) steps complete" -ForegroundColor Green
$status.steps | Where-Object { -not $_.complete } | ForEach-Object {
    Write-Host "    remaining - $($_.title)" -ForegroundColor Gray
}
Write-Host ""
Write-Host "  Next: generate practical batches, then generate and publish the timetable." -ForegroundColor White
Write-Host "  Faculty and students have no sign-in accounts; create them from their pages if needed." -ForegroundColor DarkGray
Write-Host ""
