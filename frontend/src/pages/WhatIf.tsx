import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { Faculty, Laboratory, Page, WhatIfResult } from '../lib/types'
import FeasibilityPanel from '../components/FeasibilityPanel'
import { Icon } from '../components/icons'
import { Badge, Button, Card, ErrorNote, InfoNote, PageHeader, StatCard } from '../components/ui'

export default function WhatIf() {
  const [closedLabIds, setClosedLabIds] = useState<number[]>([])
  const [absentFacultyIds, setAbsentFacultyIds] = useState<number[]>([])
  const [studentIncrease, setStudentIncrease] = useState(0)
  const [result, setResult] = useState<WhatIfResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const labs = useQuery({
    queryKey: ['labs'],
    queryFn: () => api<Laboratory[]>('/labs'),
  })
  const faculty = useQuery({
    queryKey: ['faculty', 'all'],
    queryFn: () => api<Page<Faculty>>('/faculty?size=200'),
  })

  const simulate = useMutation({
    mutationFn: () =>
      api<WhatIfResult>('/timetable/what-if', {
        method: 'POST',
        body: {
          closedLabIds,
          absentFacultyIds,
          additionalStudentPercent: studentIncrease,
        },
      }),
    onSuccess: (data) => {
      setResult(data)
      setError(null)
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Simulation failed.'),
  })

  const toggle = (list: number[], id: number) =>
    list.includes(id) ? list.filter((x) => x !== id) : [...list, id]

  const verdictDelta =
    result && result.baseline.verdict !== result.simulated.verdict
      ? `${result.baseline.verdict} → ${result.simulated.verdict}`
      : result?.simulated.verdict

  const capacityDelta = result
    ? result.simulated.totalSessionCapacity - result.baseline.totalSessionCapacity
    : 0

  return (
    <>
      <PageHeader
        title="What-if simulator"
        description="Test a change against the current data before you commit to it — close a lab, mark faculty absent, or grow enrolment, and see how feasibility shifts. Nothing is saved."
      />

      <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
        {/* ---------------------------------------------- scenario controls */}
        <Card title="Scenario" description="Choose what to change">
          <div className="space-y-4">
            <div>
              <div className="mb-1.5 flex items-center gap-1.5 text-[12px] font-semibold text-navy-700">
                <Icon name="labs" className="h-3.5 w-3.5" />
                Close laboratories
              </div>
              <div className="max-h-40 space-y-1 overflow-y-auto">
                {labs.data?.map((lab) => (
                  <label
                    key={lab.id}
                    className="flex items-center gap-2 rounded px-1.5 py-1 text-[13px] hover:bg-navy-50"
                  >
                    <input
                      type="checkbox"
                      checked={closedLabIds.includes(lab.id)}
                      onChange={() => setClosedLabIds((l) => toggle(l, lab.id))}
                    />
                    <span className="text-navy-700">{lab.labName}</span>
                    <span className="text-[11px] text-navy-400">({lab.labType})</span>
                  </label>
                ))}
                {labs.data?.length === 0 && <p className="text-[12px] text-navy-400">No labs defined.</p>}
              </div>
            </div>

            <div>
              <div className="mb-1.5 flex items-center gap-1.5 text-[12px] font-semibold text-navy-700">
                <Icon name="faculty" className="h-3.5 w-3.5" />
                Mark faculty absent
              </div>
              <div className="max-h-40 space-y-1 overflow-y-auto">
                {faculty.data?.content.map((f) => (
                  <label
                    key={f.id}
                    className="flex items-center gap-2 rounded px-1.5 py-1 text-[13px] hover:bg-navy-50"
                  >
                    <input
                      type="checkbox"
                      checked={absentFacultyIds.includes(f.id)}
                      onChange={() => setAbsentFacultyIds((l) => toggle(l, f.id))}
                    />
                    <span className="text-navy-700">{f.name}</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <label className="mb-1 block text-[12px] font-semibold text-navy-700">
                Increase student intake: <span className="text-info-700">+{studentIncrease}%</span>
              </label>
              <input
                type="range"
                min={0}
                max={50}
                step={5}
                value={studentIncrease}
                onChange={(e) => setStudentIncrease(Number(e.target.value))}
                className="w-full"
              />
            </div>

            <Button
              variant="primary"
              className="w-full"
              onClick={() => simulate.mutate()}
              disabled={simulate.isPending}
            >
              <Icon name="play" className="h-3.5 w-3.5" />
              {simulate.isPending ? 'Simulating…' : 'Run simulation'}
            </Button>
          </div>
        </Card>

        {/* -------------------------------------------------------- results */}
        <div className="min-w-0">
          {error && (
            <div className="mb-4">
              <ErrorNote message={error} />
            </div>
          )}

          {!result && !error && (
            <InfoNote tone="info">
              Choose a scenario on the left and run the simulation to compare it against today's data.
            </InfoNote>
          )}

          {result && (
            <>
              <Card className="mb-4" title="Impact" description="Baseline vs simulated scenario">
                <div className="mb-3 flex flex-wrap gap-1.5">
                  {result.changes.map((c, i) => (
                    <Badge key={i} tone="warn">
                      {c}
                    </Badge>
                  ))}
                </div>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
                  <StatCard
                    label="Verdict"
                    value={result.simulated.verdict}
                    sub={verdictDelta !== result.simulated.verdict ? `was ${result.baseline.verdict}` : 'unchanged'}
                    tone={
                      result.simulated.verdict === 'FEASIBLE'
                        ? 'ok'
                        : result.simulated.verdict === 'TIGHT'
                          ? 'warn'
                          : 'danger'
                    }
                  />
                  <StatCard
                    label="Session capacity"
                    value={result.simulated.totalSessionCapacity}
                    sub={
                      capacityDelta === 0
                        ? 'no change'
                        : `${capacityDelta > 0 ? '+' : ''}${capacityDelta} vs baseline`
                    }
                    tone={capacityDelta < 0 ? 'danger' : 'ok'}
                  />
                  <StatCard
                    label="Sessions required"
                    value={result.simulated.totalSessionsRequired}
                    sub={
                      result.simulated.totalSessionsRequired > result.baseline.totalSessionsRequired
                        ? `up from ${result.baseline.totalSessionsRequired}`
                        : 'unchanged'
                    }
                  />
                </div>
              </Card>

              <div className="mb-2 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                Simulated scenario
              </div>
              <FeasibilityPanel report={result.simulated} />

              <details className="mb-4">
                <summary className="cursor-pointer text-[12px] font-medium text-navy-600 select-none">
                  Show baseline (before changes)
                </summary>
                <div className="mt-2">
                  <FeasibilityPanel report={result.baseline} />
                </div>
              </details>
            </>
          )}
        </div>
      </div>
    </>
  )
}
