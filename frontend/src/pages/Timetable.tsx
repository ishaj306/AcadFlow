import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, formatDateTime, getToken } from '../lib/api'
import type {
  FeasibilityReport,
  GenerationResult,
  TimetableDetail,
  TimetableSummary,
} from '../lib/types'
import TimetableGrid from '../components/TimetableGrid'
import FeasibilityPanel from '../components/FeasibilityPanel'
import EntryEditorModal from '../components/EntryEditorModal'
import type { TimetableEntry } from '../lib/types'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Card,
  ErrorNote,
  InfoNote,
  PageHeader,
  Select,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  statusTone,
} from '../components/ui'

export default function Timetable() {
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [solveSeconds, setSolveSeconds] = useState(60)
  const [banner, setBanner] = useState<{ tone: 'ok' | 'danger'; text: string } | null>(null)
  const [feasibility, setFeasibility] = useState<FeasibilityReport | null>(null)
  const [filters, setFilters] = useState({ division: '', facultyId: '', labId: '', subjectId: '' })
  const [useCustomWeights, setUseCustomWeights] = useState(false)
  const [weights, setWeights] = useState({
    workloadImbalance: 6,
    facultyPreference: 2,
    studentGaps: 3,
    labUnderutilization: 1,
    facultyIdle: 2,
  })
  const [editMode, setEditMode] = useState(false)
  const [editingEntry, setEditingEntry] = useState<TimetableEntry | null>(null)
  const [exporting, setExporting] = useState<'xlsx' | 'pdf' | null>(null)

  // Server-generated .xlsx / .pdf of the selected timetable — draft or published.
  async function exportTimetable(format: 'xlsx' | 'pdf') {
    if (activeId == null) return
    setExporting(format)
    try {
      const res = await fetch(`/api/reports/timetable/${activeId}/export?format=${format}`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      })
      if (!res.ok) {
        let message = `Export failed (status ${res.status}).`
        try {
          const payload = await res.json()
          if (payload?.message) message = payload.message
        } catch {
          /* binary or empty body */
        }
        throw new Error(message)
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `timetable-${activeId}.${format}`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setBanner({ tone: 'danger', text: (err as Error).message })
    } finally {
      setExporting(null)
    }
  }

  const versions = useQuery({
    queryKey: ['timetables'],
    queryFn: () => api<TimetableSummary[]>('/timetable'),
  })

  // Default to the newest version once the list arrives.
  const activeId =
    selectedId ?? versions.data?.find((t) => t.status === 'PUBLISHED')?.id ?? versions.data?.[0]?.id ?? null

  const detail = useQuery({
    queryKey: ['timetable', activeId],
    queryFn: () => api<TimetableDetail>(`/timetable/${activeId}`),
    enabled: activeId != null,
  })

  const generate = useMutation({
    mutationFn: () =>
      api<GenerationResult>('/timetable/generate', {
        method: 'POST',
        body: { maxSeconds: solveSeconds, weights: useCustomWeights ? weights : undefined },
        // The solver runs for up to solveSeconds plus assembly and persistence.
        timeoutMs: (solveSeconds + 120) * 1000,
      }),
    onSuccess: (result) => {
      setSelectedId(result.preview.timetable.id)
      setBanner({
        tone: 'ok',
        text: `Draft generated in ${(result.metrics.runtimeMs / 1000).toFixed(1)}s with a score of ${result.preview.timetable.score}/100. Review it below, then approve to publish.`,
      })
      queryClient.invalidateQueries({ queryKey: ['timetables'] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Generation failed.',
      }),
  })

  const generateOptions = useMutation({
    mutationFn: () =>
      api<GenerationResult[]>('/timetable/generate-options', {
        method: 'POST',
        body: { maxSeconds: Math.min(solveSeconds, 30) },
        // Three solves back to back; give the whole call generous headroom.
        timeoutMs: (Math.min(solveSeconds, 30) * 3 + 120) * 1000,
      }),
    onSuccess: (results) => {
      if (results[0]) setSelectedId(results[0].preview.timetable.id)
      setBanner({
        tone: 'ok',
        text: `Generated ${results.length} option${results.length > 1 ? 's' : ''}. Compare the scores below and approve the one you prefer.`,
      })
      queryClient.invalidateQueries({ queryKey: ['timetables'] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Option generation failed.',
      }),
  })

  const checkFeasibility = useMutation({
    mutationFn: () => api<FeasibilityReport>('/timetable/feasibility', { method: 'POST' }),
    onSuccess: (report) => {
      setFeasibility(report)
      setBanner(null)
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Feasibility check failed.',
      }),
  })

  const approve = useMutation({
    mutationFn: (id: number) => api<TimetableDetail>(`/timetable/${id}/approve`, { method: 'POST' }),
    onSuccess: () => {
      setBanner({ tone: 'ok', text: 'Timetable approved and published. Affected users have been notified.' })
      queryClient.invalidateQueries({ queryKey: ['timetables'] })
      queryClient.invalidateQueries({ queryKey: ['timetable', activeId] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Approval failed.',
      }),
  })

  const entries = detail.data?.entries ?? []

  const options = useMemo(() => {
    const divisions = new Set<string>()
    const faculty = new Map<number, string>()
    const labs = new Map<number, string>()
    const subjects = new Map<number, string>()
    for (const entry of entries) {
      divisions.add(entry.division)
      faculty.set(entry.facultyId, entry.facultyName)
      labs.set(entry.labId, entry.labName)
      subjects.set(entry.subjectId, entry.subjectName)
    }
    return {
      divisions: [...divisions].sort(),
      faculty: [...faculty].sort((a, b) => a[1].localeCompare(b[1])),
      labs: [...labs].sort((a, b) => a[1].localeCompare(b[1])),
      subjects: [...subjects].sort((a, b) => a[1].localeCompare(b[1])),
    }
  }, [entries])

  const filtered = entries.filter(
    (entry) =>
      (!filters.division || entry.division === filters.division) &&
      (!filters.facultyId || entry.facultyId === Number(filters.facultyId)) &&
      (!filters.labId || entry.labId === Number(filters.labId)) &&
      (!filters.subjectId || entry.subjectId === Number(filters.subjectId)),
  )

  const summary = detail.data?.timetable
  const validation = detail.data?.validation

  return (
    <>
      <PageHeader
        title="Timetable"
        description="Generate, review and publish the practical timetable. Generation always produces a draft; nothing is published without approval."
        actions={
          <>
            <div className="flex items-center gap-2">
              <span className="text-xs text-navy-500">Time limit</span>
              <Select
                value={solveSeconds}
                onChange={(e) => setSolveSeconds(Number(e.target.value))}
                className="w-24"
              >
                <option value={30}>30 s</option>
                <option value={60}>60 s</option>
                <option value={120}>120 s</option>
                <option value={180}>180 s</option>
              </Select>
            </div>
            <Button onClick={() => checkFeasibility.mutate()} disabled={checkFeasibility.isPending}>
              <Icon name="check" className="h-3.5 w-3.5" />
              {checkFeasibility.isPending ? 'Checking…' : 'Check feasibility'}
            </Button>
            <Button
              onClick={() => generateOptions.mutate()}
              disabled={generateOptions.isPending || generate.isPending}
            >
              <Icon name="package" className="h-3.5 w-3.5" />
              {generateOptions.isPending ? 'Generating…' : 'Generate 3 options'}
            </Button>
            <Button variant="primary" onClick={() => generate.mutate()} disabled={generate.isPending}>
              <Icon name="play" className="h-3.5 w-3.5" />
              {generate.isPending ? 'Optimising…' : 'Generate timetable'}
            </Button>
          </>
        }
      />

      {banner && (
        <div className="mb-4">
          <InfoNote tone={banner.tone}>{banner.text}</InfoNote>
        </div>
      )}

      {feasibility && (
        <FeasibilityPanel report={feasibility} onClose={() => setFeasibility(null)} />
      )}

      <details className="mb-4">
        <summary className="inline-flex cursor-pointer items-center gap-1.5 text-[12px] font-medium text-navy-600 select-none">
          <Icon name="settings" className="h-3.5 w-3.5" />
          Tune optimisation priorities
        </summary>
        <Card className="mt-2">
          <label className="mb-3 flex items-center gap-2 text-[13px] text-navy-700">
            <input
              type="checkbox"
              checked={useCustomWeights}
              onChange={(e) => setUseCustomWeights(e.target.checked)}
            />
            Use custom weights when generating (higher = the optimiser cares more)
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            {(
              [
                ['workloadImbalance', 'Balance faculty workload'],
                ['facultyPreference', 'Respect faculty slot preferences'],
                ['studentGaps', 'Minimise gaps in student days'],
                ['labUnderutilization', 'Fill labs efficiently'],
                ['facultyIdle', 'Minimise faculty idle time'],
              ] as const
            ).map(([key, label]) => (
              <div key={key} className={useCustomWeights ? '' : 'opacity-50'}>
                <div className="flex items-center justify-between text-[12px] text-navy-600">
                  <span>{label}</span>
                  <span className="tabular font-semibold text-navy-800">{weights[key]}</span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={20}
                  step={1}
                  disabled={!useCustomWeights}
                  value={weights[key]}
                  onChange={(e) => setWeights((w) => ({ ...w, [key]: Number(e.target.value) }))}
                  className="w-full"
                />
              </div>
            ))}
          </div>
        </Card>
      </details>

      {generateOptions.data && generateOptions.data.length > 0 && (
        <Card
          className="mb-4"
          title="Generated options"
          description="Each option optimises for a different priority — pick the one that fits best"
        >
          <div className="grid gap-3 sm:grid-cols-3">
            {generateOptions.data.map((option) => {
              const t = option.preview.timetable
              const isActive = t.id === activeId
              return (
                <button
                  key={t.id}
                  onClick={() => setSelectedId(t.id)}
                  className={`rounded-lg border p-3 text-left transition-colors ${
                    isActive
                      ? 'border-info-500 bg-info-50/40 ring-1 ring-info-500'
                      : 'border-navy-100 hover:bg-navy-50'
                  }`}
                >
                  <div className="text-[13px] font-semibold text-navy-900">
                    {t.name.split('—').pop()?.trim() ?? t.name}
                  </div>
                  <div className="mt-1 flex items-baseline gap-1">
                    <span className="text-2xl font-bold text-navy-900">
                      {t.score?.toFixed(0) ?? '—'}
                    </span>
                    <span className="text-[11px] text-navy-500">/ 100</span>
                  </div>
                  <div className="mt-1 text-[11px] text-navy-500">
                    {t.hardViolations === 0 ? (
                      <span className="text-ok-700">No hard violations</span>
                    ) : (
                      <span className="text-danger-700">{t.hardViolations} hard violation(s)</span>
                    )}
                  </div>
                  {isActive && (
                    <div className="mt-1.5 text-[11px] font-medium text-info-700">Selected ↓</div>
                  )}
                </button>
              )
            })}
          </div>
        </Card>
      )}

      {generate.isPending && (
        <Card className="mb-4">
          <div className="flex items-center gap-3 text-[13px] text-navy-600">
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-navy-200 border-t-navy-600" />
            Running constraint optimisation — hard constraints are enforced first, then faculty
            workload balance, preferences and student gaps are optimised. This can take up to{' '}
            {solveSeconds} seconds.
          </div>
        </Card>
      )}

      {versions.isLoading && <Spinner label="Loading timetables" />}
      {versions.error && (
        <ErrorNote message={(versions.error as Error).message} onRetry={() => versions.refetch()} />
      )}

      {versions.data && versions.data.length === 0 && !generate.isPending && (
        <Card>
          <div className="px-2 py-10 text-center">
            <p className="text-sm font-medium text-navy-700">No timetable has been generated yet</p>
            <p className="mx-auto mt-1 max-w-md text-[13px] text-navy-500">
              Make sure practical batches exist, then run generation. The optimiser will place every
              batch into a laboratory and period without violating any hard constraint.
            </p>
          </div>
        </Card>
      )}

      {versions.data && versions.data.length > 0 && (
        <Card
          className="mb-4"
          title="Versions"
          description="Each generation is stored as a draft until it is approved"
          bodyClassName=""
        >
          <Table
            head={
              <tr>
                <Th>Name</Th>
                <Th>Status</Th>
                <Th className="text-right">Score</Th>
                <Th className="text-right">Sessions</Th>
                <Th className="text-right">Violations</Th>
                <Th>Engine</Th>
                <Th>Generated</Th>
                <Th />
              </tr>
            }
          >
            {versions.data.map((row) => (
              <tr
                key={row.id}
                className={`cursor-pointer hover:bg-navy-50/60 ${row.id === activeId ? 'bg-navy-50' : ''}`}
                onClick={() => setSelectedId(row.id)}
              >
                <Td className="font-medium">{row.name}</Td>
                <Td>
                  <Badge tone={statusTone(row.status)}>{row.status}</Badge>
                </Td>
                <Td className="tabular text-right">{row.score?.toFixed(1) ?? '—'}</Td>
                <Td className="tabular text-right">{row.entryCount}</Td>
                <Td className="tabular text-right">
                  {row.hardViolations === 0 ? (
                    <span className="text-ok-700">0</span>
                  ) : (
                    <span className="font-semibold text-danger-700">{row.hardViolations}</span>
                  )}
                </Td>
                <Td className="text-[12px] text-navy-500">
                  {row.solverEngine}
                  {row.solverRuntimeMs ? ` · ${(row.solverRuntimeMs / 1000).toFixed(1)}s` : ''}
                </Td>
                <Td className="text-[12px] whitespace-nowrap text-navy-500">
                  {formatDateTime(row.generatedAt)}
                </Td>
                <Td>
                  {row.status === 'DRAFT' && (
                    <Button
                      size="sm"
                      variant="primary"
                      disabled={approve.isPending}
                      onClick={() => approve.mutate(row.id)}
                    >
                      <Icon name="check" className="h-3.5 w-3.5" />
                      Approve &amp; publish
                    </Button>
                  )}
                </Td>
              </tr>
            ))}
          </Table>
        </Card>
      )}

      {detail.isLoading && <Spinner label="Loading schedule" />}

      {summary && validation && (
        <Card
          className="mb-4"
          title="Validation"
          description="Re-checked against the database, independently of the optimiser's own report"
        >
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard
              label="Schedule score"
              value={`${summary.score?.toFixed(1) ?? '—'}`}
              sub="out of 100"
              tone={
                (summary.score ?? 0) >= 80 ? 'ok' : (summary.score ?? 0) >= 60 ? 'warn' : 'danger'
              }
            />
            <StatCard
              label="Hard violations"
              value={validation.hardViolations}
              sub={validation.publishable ? 'Safe to publish' : 'Must be resolved'}
              tone={validation.hardViolations === 0 ? 'ok' : 'danger'}
            />
            <StatCard
              label="Lab utilisation"
              value={`${validation.labUtilizationPercent}%`}
              sub="of available periods"
            />
            <StatCard
              label="Workload balance"
              value={validation.facultyWorkloadBalance}
              sub={`${validation.overloadedFaculty} over limit`}
              tone={statusTone(validation.facultyWorkloadBalance)}
            />
          </div>

          <div className="mt-4 grid grid-cols-2 gap-x-6 gap-y-1.5 text-[13px] lg:grid-cols-3">
            {[
              ['Faculty double bookings (H1)', validation.facultyConflicts],
              ['Laboratory double bookings (H2)', validation.labConflicts],
              ['Student batch overlaps (H3)', validation.studentConflicts],
              ['Capacity violations (H4)', validation.capacityViolations],
              ['Availability violations (H5/H6)', validation.availabilityViolations],
              ['Unqualified assignments', validation.qualificationViolations],
            ].map(([label, value]) => (
              <div key={label as string} className="flex items-center justify-between gap-3">
                <span className="text-navy-600">{label}</span>
                <span
                  className={`tabular font-semibold ${value === 0 ? 'text-ok-700' : 'text-danger-700'}`}
                >
                  {value as number}
                </span>
              </div>
            ))}
          </div>

          {validation.warnings.length > 0 && (
            <div className="mt-4 border-t border-navy-100 pt-3">
              <div className="mb-1.5 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                Warnings ({validation.warnings.length})
              </div>
              <ul className="space-y-1 text-[12px] text-navy-600">
                {validation.warnings.map((warning) => (
                  <li key={warning} className="flex gap-2">
                    <span className="text-warn-600">•</span>
                    {warning}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {summary.status === 'DRAFT' && (
            <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-navy-100 pt-3">
              <Button
                variant="primary"
                disabled={!validation.publishable || approve.isPending}
                onClick={() => approve.mutate(summary.id)}
                title={
                  validation.publishable
                    ? undefined
                    : 'Hard constraint violations must be resolved before publishing'
                }
              >
                <Icon name="check" className="h-3.5 w-3.5" />
                Approve &amp; publish
              </Button>
              <Button onClick={() => generate.mutate()} disabled={generate.isPending}>
                <Icon name="reschedule" className="h-3.5 w-3.5" />
                Regenerate
              </Button>
              {!validation.publishable && (
                <span className="text-[12px] text-danger-700">
                  Publishing is blocked while hard constraints are violated.
                </span>
              )}
            </div>
          )}
        </Card>
      )}

      {detail.data && (
        <Card
          title="Weekly grid"
          description={`${filtered.length} of ${entries.length} sessions shown`}
          actions={
            <>
              <Select
                value={filters.division}
                onChange={(e) => setFilters((f) => ({ ...f, division: e.target.value }))}
                className="w-32"
              >
                <option value="">All divisions</option>
                {options.divisions.map((d) => (
                  <option key={d} value={d}>
                    Division {d}
                  </option>
                ))}
              </Select>
              <Select
                value={filters.subjectId}
                onChange={(e) => setFilters((f) => ({ ...f, subjectId: e.target.value }))}
                className="w-44"
              >
                <option value="">All subjects</option>
                {options.subjects.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </Select>
              <Select
                value={filters.facultyId}
                onChange={(e) => setFilters((f) => ({ ...f, facultyId: e.target.value }))}
                className="w-44"
              >
                <option value="">All faculty</option>
                {options.faculty.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </Select>
              <Select
                value={filters.labId}
                onChange={(e) => setFilters((f) => ({ ...f, labId: e.target.value }))}
                className="w-44"
              >
                <option value="">All laboratories</option>
                {options.labs.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </Select>
              {summary?.status === 'DRAFT' && (
                <Button
                  size="sm"
                  variant={editMode ? 'primary' : 'secondary'}
                  onClick={() => setEditMode((v) => !v)}
                >
                  <Icon name="settings" className="h-3.5 w-3.5" />
                  {editMode ? 'Done editing' : 'Edit sessions'}
                </Button>
              )}
              <Button
                size="sm"
                disabled={exporting !== null || activeId == null}
                onClick={() => exportTimetable('xlsx')}
              >
                <Icon name="download" className="h-3.5 w-3.5" />
                {exporting === 'xlsx' ? 'Exporting…' : 'Excel'}
              </Button>
              <Button
                size="sm"
                disabled={exporting !== null || activeId == null}
                onClick={() => exportTimetable('pdf')}
              >
                <Icon name="download" className="h-3.5 w-3.5" />
                {exporting === 'pdf' ? 'Exporting…' : 'PDF'}
              </Button>
              <Button size="sm" onClick={() => window.print()}>
                <Icon name="download" className="h-3.5 w-3.5" />
                Print
              </Button>
            </>
          }
          bodyClassName="p-2"
        >
          {editMode && summary?.status === 'DRAFT' && (
            <div className="mx-2 mb-2 rounded border border-info-200 bg-info-50/60 px-3 py-2 text-[12px] text-info-800">
              Edit mode is on — click any session to move it, reassign its faculty or lab, or delete it.
              Changes are re-validated instantly.
            </div>
          )}
          <TimetableGrid
            days={detail.data.days}
            timeSlots={detail.data.timeSlots}
            entries={filtered}
            emptyHint="No sessions match the selected filters."
            onEntryClick={editMode && summary?.status === 'DRAFT' ? setEditingEntry : undefined}
          />
        </Card>
      )}

      {editingEntry && activeId != null && (
        <EntryEditorModal
          timetableId={activeId}
          entry={editingEntry}
          days={detail.data?.days ?? []}
          timeSlots={detail.data?.timeSlots ?? []}
          onClose={() => setEditingEntry(null)}
        />
      )}
    </>
  )
}
