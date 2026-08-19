import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, formatDate } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { FacultyLeave, FacultyWorkload as Workload } from '../../lib/types'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  InfoNote,
  Meter,
  PageHeader,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  TextInput,
  statusTone,
} from '../../components/ui'

export default function FacultyWorkload() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const facultyId = user?.facultyId

  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [leaveType, setLeaveType] = useState('CASUAL')
  const [reason, setReason] = useState('')
  const [banner, setBanner] = useState<{ tone: 'ok' | 'danger'; text: string } | null>(null)

  const workload = useQuery({
    queryKey: ['workload', 'faculty', facultyId],
    queryFn: () => api<Workload>(`/workload/faculty/${facultyId}`),
    enabled: !!facultyId,
    retry: false,
  })

  const leaves = useQuery({
    queryKey: ['leaves', 'faculty', facultyId],
    queryFn: () => api<FacultyLeave[]>(`/faculty/${facultyId}/leaves`),
    enabled: !!facultyId,
  })

  const submit = useMutation({
    mutationFn: () =>
      api<FacultyLeave>('/faculty/leaves', {
        method: 'POST',
        body: { facultyId, startDate, endDate, leaveType, reason },
      }),
    onSuccess: () => {
      setBanner({
        tone: 'ok',
        text: 'Leave request submitted. Once approved, any affected practical is raised for rescheduling.',
      })
      setStartDate('')
      setEndDate('')
      setReason('')
      queryClient.invalidateQueries({ queryKey: ['leaves'] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Could not submit the request.',
      }),
  })

  if (!facultyId) {
    return (
      <>
        <PageHeader title="My workload" />
        <InfoNote tone="warn">This account is not linked to a faculty record.</InfoNote>
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="My workload"
        description="Your assigned practical hours and leave requests."
      />

      {banner && (
        <div className="mb-4">
          <InfoNote tone={banner.tone}>{banner.text}</InfoNote>
        </div>
      )}

      {workload.isLoading && <Spinner label="Loading workload" />}
      {workload.error && (
        <ErrorNote message={(workload.error as Error).message} onRetry={() => workload.refetch()} />
      )}

      {workload.data && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard label="Assigned" value={`${workload.data.assignedHours}h`} sub="per week" />
            <StatCard label="Maximum" value={`${workload.data.maxWeeklyHours}h`} sub="contractual limit" />
            <StatCard
              label="Utilisation"
              value={`${workload.data.utilizationPercent}%`}
              sub={workload.data.status}
              tone={statusTone(workload.data.status)}
            />
            <StatCard label="Free periods" value={workload.data.freeTeachingSlots} sub="per week" />
          </div>

          <Card className="mt-4" title="Utilisation">
            <Meter
              percent={workload.data.utilizationPercent}
              tone={statusTone(workload.data.status)}
            />
            <div className="mt-2 flex items-center justify-between text-[12px] text-navy-600">
              <span>
                {workload.data.practicalCount} practical session(s) · {workload.data.designation}
              </span>
              <Badge tone={statusTone(workload.data.status)}>{workload.data.status}</Badge>
            </div>
            <p className="mt-3 border-t border-navy-100 pt-2 text-[12px] text-navy-500">
              Qualified for: {workload.data.subjects.join(', ') || '—'}
            </p>
          </Card>
        </>
      )}

      <Card
        className="mt-4"
        title="Submit leave or unavailability"
        description="Approved leave automatically raises rescheduling proposals for affected practicals."
      >
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="From">
            <TextInput type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          </Field>
          <Field label="To">
            <TextInput type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          </Field>
          <Field label="Type">
            <select
              value={leaveType}
              onChange={(e) => setLeaveType(e.target.value)}
              className="w-full rounded border border-navy-200 bg-white px-2.5 py-1.5 text-[13px] outline-none focus:border-navy-500 focus:ring-1 focus:ring-navy-500"
            >
              <option value="CASUAL">Casual</option>
              <option value="MEDICAL">Medical</option>
              <option value="DUTY">On duty</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Reason">
            <TextInput
              value={reason}
              placeholder="Optional"
              onChange={(e) => setReason(e.target.value)}
            />
          </Field>
        </div>
        <div className="mt-3">
          <Button
            variant="primary"
            disabled={!startDate || !endDate || submit.isPending}
            onClick={() => submit.mutate()}
          >
            {submit.isPending ? 'Submitting…' : 'Submit request'}
          </Button>
        </div>
      </Card>

      <Card className="mt-4" title="My leave requests" bodyClassName="">
        {leaves.isLoading && <Spinner />}
        {leaves.data && leaves.data.length === 0 && (
          <EmptyState title="No leave requests" hint="Submitted requests appear here." />
        )}
        {leaves.data && leaves.data.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Dates</Th>
                <Th>Type</Th>
                <Th>Reason</Th>
                <Th>Status</Th>
                <Th>Reviewed by</Th>
              </tr>
            }
          >
            {leaves.data.map((leave) => (
              <tr key={leave.id} className="hover:bg-navy-50/50">
                <Td className="whitespace-nowrap">
                  {formatDate(leave.startDate)} – {formatDate(leave.endDate)}
                </Td>
                <Td>{leave.leaveType}</Td>
                <Td className="text-[12px] text-navy-600">{leave.reason ?? '—'}</Td>
                <Td>
                  <Badge tone={statusTone(leave.status)}>{leave.status}</Badge>
                </Td>
                <Td className="text-[12px] text-navy-500">{leave.reviewedBy ?? '—'}</Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  )
}
