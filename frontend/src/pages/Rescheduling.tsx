import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, formatDate, formatDateTime, hhmm, titleCase } from '../lib/api'
import type { Reschedule, TimetableDetail } from '../lib/types'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  InfoNote,
  PageHeader,
  Select,
  Spinner,
  Table,
  Td,
  Th,
  statusTone,
} from '../components/ui'

export default function Rescheduling() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('PROPOSED')
  const [openId, setOpenId] = useState<number | null>(null)
  const [banner, setBanner] = useState<{ tone: 'ok' | 'danger'; text: string } | null>(null)
  const [manualEntryId, setManualEntryId] = useState('')

  const list = useQuery({
    queryKey: ['reschedules', statusFilter],
    queryFn: () =>
      api<Reschedule[]>(`/rescheduling${statusFilter ? `?status=${statusFilter}` : ''}`),
  })

  const detail = useQuery({
    queryKey: ['reschedule', openId],
    queryFn: () => api<Reschedule>(`/rescheduling/${openId}`),
    enabled: openId != null,
  })

  const published = useQuery({
    queryKey: ['timetable', 'current'],
    queryFn: () => api<TimetableDetail>('/timetable/current'),
    retry: false,
  })

  function refreshAll() {
    queryClient.invalidateQueries({ queryKey: ['reschedules'] })
    queryClient.invalidateQueries({ queryKey: ['reschedule'] })
    queryClient.invalidateQueries({ queryKey: ['timetable'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['workload'] })
  }

  const analyse = useMutation({
    mutationFn: (entryId: number) =>
      api<Reschedule>('/rescheduling/analyze', {
        method: 'POST',
        body: { timetableEntryId: entryId, reason: 'ADMINISTRATIVE', maxCandidates: 5 },
        timeoutMs: 120_000,
      }),
    onSuccess: (result) => {
      setOpenId(result.id)
      setStatusFilter('PROPOSED')
      setBanner({ tone: 'ok', text: `${result.candidates.length} alternative(s) found and ranked.` })
      refreshAll()
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Analysis failed.',
      }),
  })

  const approve = useMutation({
    mutationFn: (input: { id: number; candidateId: number }) =>
      api<Reschedule>(`/rescheduling/${input.id}/approve`, {
        method: 'POST',
        body: { candidateId: input.candidateId },
      }),
    onSuccess: (result) => {
      setBanner({
        tone: 'ok',
        text: `Moved to ${titleCase(result.newDay ?? '')} ${hhmm(result.newStart)} with ${result.newFacultyName} in ${result.newLabName}. Affected students and faculty have been notified.`,
      })
      refreshAll()
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Approval failed.',
      }),
  })

  const reject = useMutation({
    mutationFn: (id: number) => api<Reschedule>(`/rescheduling/${id}/reject`, { method: 'POST' }),
    onSuccess: () => {
      setBanner({ tone: 'ok', text: 'Proposal rejected; the timetable is unchanged.' })
      refreshAll()
    },
  })

  const current = detail.data

  return (
    <>
      <PageHeader
        title="Rescheduling"
        description="When a practical is disrupted, the system searches every legal alternative, scores it by disruption, and presents a ranked shortlist for approval."
        actions={
          <Select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="w-40"
          >
            <option value="PROPOSED">Awaiting approval</option>
            <option value="APPLIED">Applied</option>
            <option value="REJECTED">Rejected</option>
            <option value="">All</option>
          </Select>
        }
      />

      {banner && (
        <div className="mb-4">
          <InfoNote tone={banner.tone}>{banner.text}</InfoNote>
        </div>
      )}

      <Card
        className="mb-4"
        title="Start a rescheduling request"
        description="Pick a scheduled session to move; leave-driven proposals are raised automatically when a leave is approved."
      >
        <div className="flex flex-wrap items-end gap-2">
          <div className="min-w-[320px] flex-1">
            <span className="mb-1 block text-xs font-medium text-navy-600">Scheduled session</span>
            <Select value={manualEntryId} onChange={(e) => setManualEntryId(e.target.value)}>
              <option value="">Select a practical session…</option>
              {(published.data?.entries ?? []).map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {titleCase(entry.dayOfWeek)} {hhmm(entry.startTime)} — {entry.subjectName} (
                  {entry.batchName}/{entry.division}) · {entry.facultyName} · {entry.labName}
                </option>
              ))}
            </Select>
          </div>
          <Button
            variant="primary"
            disabled={!manualEntryId || analyse.isPending}
            onClick={() => analyse.mutate(Number(manualEntryId))}
          >
            <Icon name="search" className="h-3.5 w-3.5" />
            {analyse.isPending ? 'Searching…' : 'Find alternatives'}
          </Button>
        </div>
        {!published.data && (
          <p className="mt-2 text-[12px] text-navy-500">
            No timetable is published yet, so there is nothing to reschedule.
          </p>
        )}
      </Card>

      {list.isLoading && <Spinner label="Loading requests" />}
      {list.error && <ErrorNote message={(list.error as Error).message} onRetry={() => list.refetch()} />}

      {list.data && (
        <Card title="Rescheduling requests" bodyClassName="">
          {list.data.length === 0 ? (
            <EmptyState
              title="Nothing to review"
              hint="Approved faculty leave raises proposals automatically, or start one above."
            />
          ) : (
            <Table
              head={
                <tr>
                  <Th>Practical</Th>
                  <Th>Reason</Th>
                  <Th>Original slot</Th>
                  <Th>New slot</Th>
                  <Th>Status</Th>
                  <Th>Raised</Th>
                  <Th />
                </tr>
              }
            >
              {list.data.map((row) => (
                <tr key={row.id} className="hover:bg-navy-50/50">
                  <Td>
                    <div className="font-medium">{row.subjectName}</div>
                    <div className="text-[12px] text-navy-500">
                      {row.batchName} · Division {row.division}
                    </div>
                  </Td>
                  <Td>
                    <div>{row.reasonLabel}</div>
                    {row.affectedDate && (
                      <div className="text-[12px] text-navy-500">{formatDate(row.affectedDate)}</div>
                    )}
                  </Td>
                  <Td className="text-[12px] whitespace-nowrap">
                    {titleCase(row.originalDay)} {hhmm(row.originalStart)}–{hhmm(row.originalEnd)}
                    <div className="text-navy-500">
                      {row.originalFacultyName} · {row.originalLabName}
                    </div>
                  </Td>
                  <Td className="text-[12px] whitespace-nowrap">
                    {row.newDay ? (
                      <>
                        {titleCase(row.newDay)} {hhmm(row.newStart)}–{hhmm(row.newEnd)}
                        <div className="text-navy-500">
                          {row.newFacultyName} · {row.newLabName}
                        </div>
                      </>
                    ) : (
                      <span className="text-navy-400">—</span>
                    )}
                  </Td>
                  <Td>
                    <Badge tone={statusTone(row.status)}>{row.status}</Badge>
                  </Td>
                  <Td className="text-[12px] whitespace-nowrap text-navy-500">
                    {formatDateTime(row.createdAt)}
                  </Td>
                  <Td>
                    <Button size="sm" onClick={() => setOpenId(openId === row.id ? null : row.id)}>
                      {openId === row.id ? 'Hide' : 'Review'}
                    </Button>
                  </Td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      )}

      {openId != null && (
        <div className="mt-4">
          {detail.isLoading && <Spinner label="Loading proposal" />}
          {current && (
            <Card
              title={`Proposal #${current.id} — ${current.subjectName}`}
              description={`${current.batchName}, Division ${current.division} · ${current.reasonLabel}`}
            >
              <div className="grid gap-4 lg:grid-cols-3">
                <div className="rounded border border-navy-100 bg-navy-50 p-3">
                  <div className="mb-2 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                    Original schedule
                  </div>
                  <dl className="space-y-1 text-[13px]">
                    <Row label="Day" value={titleCase(current.originalDay)} />
                    <Row
                      label="Time"
                      value={`${hhmm(current.originalStart)}–${hhmm(current.originalEnd)}`}
                    />
                    <Row label="Faculty" value={current.originalFacultyName} />
                    <Row label="Laboratory" value={current.originalLabName} />
                    {current.affectedDate && (
                      <Row label="Affected date" value={formatDate(current.affectedDate)} />
                    )}
                  </dl>
                  {current.reasonDetail && (
                    <p className="mt-2 border-t border-navy-200 pt-2 text-[12px] text-navy-600">
                      {current.reasonDetail}
                    </p>
                  )}
                </div>

                <div className="lg:col-span-2">
                  <div className="mb-2 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                    Suggested alternatives
                  </div>
                  {current.candidates.length === 0 ? (
                    <EmptyState title="No alternatives" hint="No legal slot was available." />
                  ) : (
                    <div className="overflow-x-auto rounded border border-navy-100">
                      <table className="w-full min-w-[640px] border-collapse text-[13px]">
                        <thead className="bg-navy-50 text-left text-[11px] tracking-wide text-navy-600 uppercase">
                          <tr>
                            <Th>Slot</Th>
                            <Th>Faculty</Th>
                            <Th>Laboratory</Th>
                            <Th className="text-right">Score</Th>
                            <Th />
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-navy-100">
                          {current.candidates.map((candidate, index) => (
                            <tr
                              key={candidate.id}
                              className={index === 0 ? 'bg-ok-50/40' : 'hover:bg-navy-50/50'}
                            >
                              <Td className="whitespace-nowrap">
                                <div className="font-medium">
                                  {titleCase(candidate.dayOfWeek)} {hhmm(candidate.startTime)}–
                                  {hhmm(candidate.endTime)}
                                </div>
                                {index === 0 && (
                                  <span className="text-[11px] font-medium text-ok-700">
                                    Recommended
                                  </span>
                                )}
                              </Td>
                              <Td>{candidate.facultyName}</Td>
                              <Td>
                                <div>{candidate.labName}</div>
                                {candidate.labLocation && (
                                  <div className="text-[11px] text-navy-500">
                                    {candidate.labLocation}
                                  </div>
                                )}
                              </Td>
                              <Td className="tabular text-right">
                                <span className="text-[15px] font-semibold text-navy-900">
                                  {candidate.score.toFixed(1)}
                                </span>
                                <div
                                  className="max-w-[240px] text-[11px] leading-tight text-navy-500"
                                  title={candidate.scoreBreakdown}
                                >
                                  {candidate.scoreBreakdown}
                                </div>
                              </Td>
                              <Td>
                                {current.status === 'PROPOSED' && (
                                  <Button
                                    size="sm"
                                    variant={index === 0 ? 'primary' : 'secondary'}
                                    disabled={approve.isPending}
                                    onClick={() =>
                                      approve.mutate({ id: current.id, candidateId: candidate.id })
                                    }
                                  >
                                    {index === 0 ? 'Accept recommendation' : 'Choose this'}
                                  </Button>
                                )}
                                {candidate.selected && <Badge tone="ok">Applied</Badge>}
                              </Td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                  {current.status === 'PROPOSED' && (
                    <div className="mt-3 flex items-center gap-2">
                      <Button variant="danger" size="sm" onClick={() => reject.mutate(current.id)}>
                        <Icon name="close" className="h-3.5 w-3.5" />
                        Reject proposal
                      </Button>
                      <span className="text-[12px] text-navy-500">
                        Rejecting leaves the timetable unchanged.
                      </span>
                    </div>
                  )}

                  {current.status === 'APPLIED' && (
                    <div className="mt-3 rounded border border-ok-600/25 bg-ok-50 px-3 py-2 text-[13px] text-ok-700">
                      Applied by {current.approvedBy} on {formatDateTime(current.appliedAt)}.
                    </div>
                  )}
                </div>
              </div>
            </Card>
          )}
        </div>
      )}
    </>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-navy-500">{label}</dt>
      <dd className="text-right font-medium text-navy-900">{value}</dd>
    </div>
  )
}
