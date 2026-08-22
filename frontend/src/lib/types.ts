export type Role = 'ADMIN' | 'HOD' | 'FACULTY' | 'STUDENT'
export type RecordStatus = 'ACTIVE' | 'INACTIVE'
export type LabStatus = 'ACTIVE' | 'MAINTENANCE' | 'INACTIVE'
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type TimetableStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'REJECTED'
export type DayOfWeek =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface CurrentUser {
  id: number
  username: string
  fullName: string
  email: string
  role: Role
  departmentId?: number
  departmentName?: string
  facultyId?: number
  studentId?: number
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: CurrentUser
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface IdName {
  id: number
  code: string
  name: string
}

export interface AcademicTerm {
  id: number
  academicYear: string
  semester: number
  startDate: string
  endDate: string
  current: boolean
  label: string
}

export interface Student {
  id: number
  rollNumber: string
  name: string
  email: string
  departmentId: number
  departmentCode: string
  departmentName: string
  semester: number
  studyYear: number
  division: string
  status: RecordStatus
  hasLogin: boolean
}

export interface Faculty {
  id: number
  employeeCode: string
  name: string
  email: string
  departmentId: number
  departmentCode: string
  departmentName: string
  designation: string
  maxWeeklyHours: number
  status: RecordStatus
  subjects: IdName[]
}

export interface Subject {
  id: number
  subjectCode: string
  subjectName: string
  departmentId: number
  departmentCode: string
  semester: number
  subjectType: 'PRACTICAL' | 'THEORY' | 'BOTH'
  practicalDurationMin: number
  sessionsPerWeek: number
  studentsPerBatch: number
  requiredLabType: string
  status: RecordStatus
  qualifiedFacultyCount: number
}

export interface Laboratory {
  id: number
  labCode: string
  labName: string
  departmentId: number
  departmentCode: string
  capacity: number
  labType: string
  location?: string
  status: LabStatus
}

export interface TimeSlot {
  id: number
  label: string
  startTime: string
  endTime: string
  slotOrder: number
  slotType: 'TEACHING' | 'BREAK' | 'LUNCH' | 'RESTRICTED' | 'SPECIAL'
  active: boolean
  durationMinutes: number
}

export interface WorkingDay {
  id: number
  dayOfWeek: DayOfWeek
  active: boolean
  dayOrder: number
}

export interface Batch {
  id: number
  batchCode: string
  batchName: string
  subjectId: number
  subjectCode: string
  subjectName: string
  departmentCode: string
  division: string
  semester: number
  capacity: number
  studentCount: number
  requiredLabType: string
  status: RecordStatus
  students: { studentId: number; rollNumber: string; name: string }[]
}

export interface TimetableEntry {
  id: number
  practicalId?: number
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  startSlotId: number
  endSlotId: number
  sessionIndex: number
  status: 'SCHEDULED' | 'RESCHEDULED' | 'CANCELLED'
  subjectId: number
  subjectCode: string
  subjectName: string
  batchId: number
  batchName: string
  division: string
  studentCount: number
  facultyId: number
  facultyName: string
  facultyCode: string
  labId: number
  labName: string
  labLocation?: string
  labCapacity: number
}

export interface ValidationSummary {
  publishable: boolean
  hardViolations: number
  facultyConflicts: number
  labConflicts: number
  studentConflicts: number
  capacityViolations: number
  availabilityViolations: number
  holidayConflicts: number
  qualificationViolations: number
  labUtilizationPercent: number
  facultyWorkloadBalance: string
  overloadedFaculty: number
  warnings: string[]
}

export interface TimetableSummary {
  id: number
  name: string
  academicTermLabel: string
  departmentName?: string
  status: TimetableStatus
  score?: number
  hardViolations: number
  solverEngine?: string
  solverStatus?: string
  solverRuntimeMs?: number
  entryCount: number
  generatedAt: string
  generatedBy?: string
  approvedAt?: string
  approvedBy?: string
  publishedAt?: string
  notes?: string
}

export interface TimetableDetail {
  timetable: TimetableSummary
  days: DayOfWeek[]
  timeSlots: TimeSlot[]
  entries: TimetableEntry[]
  validation?: ValidationSummary
}

export interface GenerationResult {
  status: string
  preview: TimetableDetail
  solverWarnings: string[]
  solverViolations: string[]
  metrics: {
    engine: string
    runtimeMs: number
    rawScore: number
    workloadImbalanceMinutes?: number
    facultyPreferenceViolations?: number
    studentGapSlots?: number
    facultyIdleSlots?: number
    labSurplusSeats?: number
    scheduleChanges?: number
  }
}

export interface FeasibilityReport {
  verdict: 'FEASIBLE' | 'TIGHT' | 'INFEASIBLE'
  totalSessionsRequired: number
  totalSessionCapacity: number
  blockers: {
    code: string
    message: string
    practicalId?: number
    subjectName?: string
    batchName?: string
    division?: string
  }[]
  resourceChecks: {
    key: string
    label: string
    required: number
    available: number
    unit: string
    satisfied: boolean
    utilizationPercent: number
    detail: string
  }[]
  batchLoads: {
    batchId: number
    batchName: string
    division?: string
    sessionsRequired: number
    labType: string
  }[]
  suggestions: {
    code: string
    title: string
    detail: string
    category: 'lab' | 'faculty' | 'time' | 'duration' | string
    estimatedGainSessions?: number
  }[]
  notes: string[]
  runtimeMs: number
}

export interface WhatIfResult {
  baseline: FeasibilityReport
  simulated: FeasibilityReport
  changes: string[]
}

export interface AssistantAnswer {
  answer: string
  entries: TimetableEntry[]
  suggestions: string[]
}

export interface ParsedStudent {
  rollNumber: string
  name: string
  division?: string
}

export interface StudentParseResult {
  students: ParsedStudent[]
  suggestedBatches: {
    batchName: string
    division?: string
    studentCount: number
  }[]
  warnings: string[]
}

export interface Conflict {
  id: number
  conflictType: string
  conflictLabel: string
  severity: Severity
  description: string
  suggestedResolution?: string
  facultyName?: string
  batchName?: string
  labName?: string
  subjectName?: string
  dayOfWeek?: DayOfWeek
  startTime?: string
  endTime?: string
  status: 'OPEN' | 'RESOLVED' | 'IGNORED'
  detectedAt: string
}

export interface RescheduleCandidate {
  id: number
  rank: number
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  facultyId: number
  facultyName: string
  labId: number
  labName: string
  labLocation?: string
  score: number
  scoreBreakdown: string
  selected: boolean
}

export interface Reschedule {
  id: number
  timetableEntryId: number
  reason: string
  reasonLabel: string
  reasonDetail?: string
  affectedDate?: string
  subjectName: string
  subjectCode: string
  batchName: string
  division: string
  originalDay: DayOfWeek
  originalStart: string
  originalEnd: string
  originalFacultyName: string
  originalLabName: string
  newDay?: DayOfWeek
  newStart?: string
  newEnd?: string
  newFacultyName?: string
  newLabName?: string
  candidateScore?: number
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'APPLIED' | 'CANCELLED'
  initiatedBy?: string
  approvedBy?: string
  createdAt: string
  appliedAt?: string
  candidates: RescheduleCandidate[]
}

export interface FacultyWorkload {
  facultyId: number
  employeeCode: string
  facultyName: string
  designation: string
  departmentCode: string
  assignedMinutes: number
  assignedHours: number
  fixedLoadHours: number
  totalLoadHours: number
  maxWeeklyHours: number
  utilizationPercent: number
  practicalCount: number
  freeTeachingSlots: number
  status: string
  subjects: string[]
}

export interface FixedCommitment {
  id: number
  title: string
  commitmentType: string
  facultyId?: number
  facultyName?: string
  labId?: number
  labName?: string
  academicTermId?: number
  dayOfWeek: DayOfWeek
  startSlotId: number
  endSlotId: number
  startTime: string
  endTime: string
  durationMinutes: number
  note?: string
}

export interface WorkloadSummary {
  timetableId: number
  timetableName: string
  facultyCount: number
  averageUtilizationPercent: number
  overloadedCount: number
  nearLimitCount: number
  balancedCount: number
  underutilizedCount: number
  spreadHours: number
  balanceVerdict: string
  faculty: FacultyWorkload[]
}

export interface Dashboard {
  counters: {
    totalStudents: number
    totalFaculty: number
    totalLabs: number
    practicalBatches: number
    todaysPracticals: number
    activeConflicts: number
    overloadedFaculty: number
    pendingReschedules: number
    pendingLeaves: number
    labUtilizationPercent: number
    scheduleScore?: number
  }
  facultyWorkload: {
    facultyName: string
    employeeCode: string
    assignedHours: number
    maxHours: number
    utilizationPercent: number
    status: string
  }[]
  labUtilization: {
    labName: string
    labCode: string
    utilizationPercent: number
    sessionsPerWeek: number
    capacity: number
  }[]
  todaysPracticals: TimetableEntry[]
  upcomingPracticals: TimetableEntry[]
  openConflicts: Conflict[]
  currentTermLabel: string
  publishedTimetableName?: string
  today: string
  todayDayOfWeek: DayOfWeek
}

export interface Notification {
  id: number
  title: string
  body: string
  category: string
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR'
  read: boolean
  createdAt: string
}

export interface SetupStatus {
  ready: boolean
  completedSteps: number
  totalSteps: number
  steps: {
    key: string
    title: string
    description: string
    route: string
    complete: boolean
    count: number
    blocker?: string
  }[]
}

export interface FacultyLeave {
  id: number
  facultyId: number
  facultyName: string
  employeeCode: string
  startDate: string
  endDate: string
  leaveType: string
  reason?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  appliedAt: string
  reviewedAt?: string
  reviewedBy?: string
}
