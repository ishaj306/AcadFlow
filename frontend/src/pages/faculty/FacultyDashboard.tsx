import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, formatDate, hhmm, titleCase } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { FacultyLeave, FacultyWorkload, TimetableDetail } from '../../lib/types'
import { Icon } from '../../components/icons'
import {
  Badge,
  Card,
  EmptyState,
  ErrorNote,
  InfoNote,
  Meter,
  PageHeader,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  statusTone,
} from '../../components/ui'

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export default function FacultyDashboard() {
  const { user } = useAuth()
  const facultyId = user?.facultyId

  const timetable = useQuery({
    queryKey: ['timetable', 'faculty', facultyId],
    queryFn: () => api<TimetableDetail>(`/timetable/faculty/${facultyId}`),
    enabled: !!facultyId,
    retry: false,
  })

  const workload = useQuery({
    queryKey: ['workload', 'faculty', facultyId],
    queryFn: () => api<FacultyWorkload>(`/workload/faculty/${facultyId}`),
    enabled: !!facultyId,
    retry: false,
  })

  const leaves = useQuery({
    queryKey: ['leaves', 'faculty', facultyId],
    queryFn: () => api<FacultyLeave[]>(`/faculty/${facultyId}/leaves`),
    enabled: !!facultyId,
  })

  if (!facultyId) {
    return (
      <>
        <PageHeader title={`Welcome, ${user?.fullName ?? ''}`} />
        <InfoNote tone="warn">This account is not linked to a faculty record.</InfoNote>
      </>
    )
  }

  const today = DAY_ORDER[new Date().getDay() === 0 ? 6 : new Date().getDay() - 1]
  const entries = timetable.data?.entries ?? []
  const todays = entries
    .filter((entry) => entry.dayOfWeek === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))

  return (
    <>
      <PageHeader
        title={`Welcome, ${user?.fullName}`}
        description={
          timetable.data
            ? `${timetable.data.timetable.academicTermLabel} · ${entries.length} practical sessions per week`
            : undefined
        }
      />

      {timetable.isLoading && <Spinner label="Loading your schedule" />}
      {timetable.error && (
        <ErrorNote
          message={(timetable.error as Error).message}
          onRetry={() => timetable.refetch()}
        />
      )}

      {workload.data && (
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard label="Assigned this week" value={`${workload.data.assignedHours}h`} />
          <StatCard label="Weekly limit" value={`${workload.data.maxWeeklyHours}h`} />
          <StatCard
            label="Utilisation"
            value={`${workload.data.utilizationPercent}%`}
            sub={workload.data.status}
            tone={statusTone(workload.data.status)}
          />
          <StatCard label="Practical sessions" value={workload.data.practicalCount} />
        </div>
      )}

      <div className="grid gap-4 xl:grid-cols-2">
        <Card title={`Today — ${titleCase(today)}`}>
          {todays.length === 0 ? (
            <EmptyState title="No practicals today" hint="Your next sessions are listed opposite." />
          ) : (
            <div className="space-y-2">
              {todays.map((entry) => (
                <div
                  key={entry.id}
                  className="rounded border border-l-4 border-navy-100 border-l-navy-800 bg-navy-50 px-4 py-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-[14px] font-semibold text-navy-900">
                        {entry.subjectName}
                      </div>
                      <div className="mt-0.5 text-[12px] text-navy-600">
                        {entry.batchName} · Division {entry.division} · {entry.studentCount} students
                      </div>
                    </div>
                    <div className="tabular text-[13px] font-semibold whitespace-nowrap text-navy-900">
                      {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
                    </div>
                  </div>
                  <div className="mt-2 flex items-center gap-1.5 border-t border-navy-200 pt-2 text-[12px] text-navy-700">
                    <Icon name="labs" className="h-3.5 w-3.5 text-navy-400" />
                    {entry.labName}
                    {entry.labLocation ? ` · ${entry.labLocation}` : ''}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Weekly summary"
          description="Your sessions across the week"
          bodyClassName=""
          actions={
            <Link
              to="/faculty/timetable"
              className="text-[12px] font-medium text-info-600 hover:underline"
            >
              Full timetable
            </Link>
          }
        >
          {entries.length === 0 ? (
            <EmptyState title="No sessions assigned" />
          ) : (
            <Table
              head={
                <tr>
                  <Th>Day</Th>
                  <Th>Time</Th>
                  <Th>Subject</Th>
                  <Th>Batch</Th>
                  <Th>Laboratory</Th>
                </tr>
              }
            >
              {[...entries]
                .sort(
                  (a, b) =>
                    DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) ||
                    a.startTime.localeCompare(b.startTime),
                )
                .map((entry) => (
                  <tr key={entry.id} className="hover:bg-navy-50/50">
                    <Td className="whitespace-nowrap">{titleCase(entry.dayOfWeek)}</Td>
                    <Td className="tabular whitespace-nowrap">
                      {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
                    </Td>
                    <Td className="font-medium">{entry.subjectName}</Td>
                    <Td>
                      {entry.batchName} ({entry.division})
                    </Td>
                    <Td>{entry.labName}</Td>
                  </tr>
                ))}
            </Table>
          )}
        </Card>
      </div>

      {workload.data && (
        <Card className="mt-4" title="Workload">
          <div className="mb-1 flex items-baseline justify-between text-[13px]">
            <span className="text-navy-600">
              {workload.data.assignedHours}h of {workload.data.maxWeeklyHours}h
            </span>
            <Badge tone={statusTone(workload.data.status)}>{workload.data.status}</Badge>
          </div>
          <Meter percent={workload.data.utilizationPercent} tone={statusTone(workload.data.status)} />
          <p className="mt-3 text-[12px] text-navy-500">
            Qualified for: {workload.data.subjects.join(', ') || '—'}
          </p>
        </Card>
      )}

      {leaves.data && leaves.data.length > 0 && (
        <Card className="mt-4" title="My leave requests" bodyClassName="">
          <Table
            head={
              <tr>
                <Th>Dates</Th>
                <Th>Type</Th>
                <Th>Reason</Th>
                <Th>Status</Th>
              </tr>
            }
          >
            {leaves.data.map((leave) => (
              <tr key={leave.id}>
                <Td className="whitespace-nowrap">
                  {formatDate(leave.startDate)} – {formatDate(leave.endDate)}
                </Td>
                <Td>{leave.leaveType}</Td>
                <Td className="text-[12px] text-navy-600">{leave.reason ?? '—'}</Td>
                <Td>
                  <Badge tone={statusTone(leave.status)}>{leave.status}</Badge>
                </Td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
    </>
  )
}
