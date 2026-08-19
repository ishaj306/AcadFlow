import { useQuery } from '@tanstack/react-query'
import { api, hhmm, titleCase } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { TimetableDetail, TimetableEntry } from '../../lib/types'
import { Icon } from '../../components/icons'
import { Card, EmptyState, ErrorNote, InfoNote, PageHeader, Spinner } from '../../components/ui'

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export default function StudentDashboard() {
  const { user } = useAuth()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['timetable', 'student', user?.studentId],
    queryFn: () => api<TimetableDetail>(`/timetable/student/${user?.studentId}`),
    enabled: !!user?.studentId,
    retry: false,
  })

  if (!user?.studentId) {
    return (
      <>
        <PageHeader title={`Welcome, ${user?.fullName ?? ''}`} />
        <InfoNote tone="warn">
          This account is not linked to a student record, so no personal timetable is available.
        </InfoNote>
      </>
    )
  }

  const today = DAY_ORDER[new Date().getDay() === 0 ? 6 : new Date().getDay() - 1]
  const entries = data?.entries ?? []
  const todays = entries
    .filter((entry) => entry.dayOfWeek === today)
    .sort((a, b) => a.startTime.localeCompare(b.startTime))

  const upcoming = [...entries].sort(
    (a, b) =>
      DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) ||
      a.startTime.localeCompare(b.startTime),
  )

  return (
    <>
      <PageHeader
        title={`Welcome, ${user.fullName}`}
        description={data ? `${data.timetable.academicTermLabel} · ${entries.length} practicals per week` : undefined}
      />

      {isLoading && <Spinner label="Loading your schedule" />}
      {error && <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />}

      {data && (
        <>
          <Card
            className="mb-4"
            title={`Today — ${titleCase(today)}`}
            description={todays.length === 0 ? undefined : `${todays.length} practical(s)`}
          >
            {todays.length === 0 ? (
              <EmptyState
                title="No practicals today"
                hint="Enjoy the day — check the week ahead below."
              />
            ) : (
              <div className="space-y-2">
                {todays.map((entry) => (
                  <PracticalCard key={entry.id} entry={entry} highlight />
                ))}
              </div>
            )}
          </Card>

          <Card title="This week" description="All your practical sessions">
            {upcoming.length === 0 ? (
              <EmptyState
                title="No practicals assigned"
                hint="You have not been placed into any practical batch yet."
              />
            ) : (
              <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                {upcoming.map((entry) => (
                  <PracticalCard key={entry.id} entry={entry} showDay />
                ))}
              </div>
            )}
          </Card>
        </>
      )}
    </>
  )
}

function PracticalCard({
  entry,
  highlight,
  showDay,
}: {
  entry: TimetableEntry
  highlight?: boolean
  showDay?: boolean
}) {
  return (
    <div
      className={`rounded border px-4 py-3 ${
        highlight ? 'border-l-4 border-navy-100 border-l-navy-800 bg-navy-50' : 'border-navy-100 bg-white'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[14px] font-semibold text-navy-900">{entry.subjectName}</div>
          <div className="tabular mt-0.5 text-[12px] text-navy-500">{entry.subjectCode}</div>
        </div>
        <div className="text-right">
          {showDay && (
            <div className="text-[11px] font-medium tracking-wide text-navy-500 uppercase">
              {titleCase(entry.dayOfWeek)}
            </div>
          )}
          <div className="tabular text-[13px] font-semibold text-navy-900">
            {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
          </div>
        </div>
      </div>

      <dl className="mt-2.5 space-y-1 border-t border-navy-100 pt-2 text-[12px]">
        <div className="flex items-center gap-1.5 text-navy-700">
          <Icon name="batches" className="h-3.5 w-3.5 text-navy-400" />
          {entry.batchName} · Division {entry.division}
        </div>
        <div className="flex items-center gap-1.5 text-navy-700">
          <Icon name="labs" className="h-3.5 w-3.5 text-navy-400" />
          {entry.labName}
          {entry.labLocation ? ` · ${entry.labLocation}` : ''}
        </div>
        <div className="flex items-center gap-1.5 text-navy-700">
          <Icon name="faculty" className="h-3.5 w-3.5 text-navy-400" />
          {entry.facultyName}
        </div>
      </dl>

      {entry.status === 'RESCHEDULED' && (
        <div className="mt-2 rounded border border-warn-600/30 bg-warn-50 px-2 py-1 text-[11px] font-medium text-warn-700">
          This session was rescheduled
        </div>
      )}
    </div>
  )
}
