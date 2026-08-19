import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { TimetableDetail } from '../../lib/types'
import TimetableGrid from '../../components/TimetableGrid'
import { Icon } from '../../components/icons'
import { Button, Card, ErrorNote, InfoNote, PageHeader, Spinner } from '../../components/ui'

export default function FacultyTimetable() {
  const { user } = useAuth()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['timetable', 'faculty', user?.facultyId],
    queryFn: () => api<TimetableDetail>(`/timetable/faculty/${user?.facultyId}`),
    enabled: !!user?.facultyId,
    retry: false,
  })

  if (!user?.facultyId) {
    return (
      <>
        <PageHeader title="My timetable" />
        <InfoNote tone="warn">This account is not linked to a faculty record.</InfoNote>
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="My timetable"
        description={data ? `${data.timetable.name} · ${data.timetable.academicTermLabel}` : undefined}
        actions={
          <Button onClick={() => window.print()}>
            <Icon name="download" className="h-3.5 w-3.5" />
            Print
          </Button>
        }
      />

      {isLoading && <Spinner label="Loading your timetable" />}
      {error && <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />}

      {data && (
        <Card bodyClassName="p-2">
          <TimetableGrid
            days={data.days}
            timeSlots={data.timeSlots}
            entries={data.entries}
            labels={{ showFaculty: false }}
            emptyHint="You have no practical sessions in the published timetable."
          />
        </Card>
      )}
    </>
  )
}
