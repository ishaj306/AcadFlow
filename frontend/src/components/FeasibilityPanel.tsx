import type { FeasibilityReport } from '../lib/types'
import { Badge, Button, Card, Meter, StatCard, Table, Td, Th } from './ui'
import { Icon } from './icons'

const VERDICT: Record<
  FeasibilityReport['verdict'],
  { tone: 'ok' | 'warn' | 'danger'; label: string; blurb: string }
> = {
  FEASIBLE: {
    tone: 'ok',
    label: 'Schedulable',
    blurb: 'Every resource check passes with headroom. Generation should succeed.',
  },
  TIGHT: {
    tone: 'warn',
    label: 'Tight',
    blurb:
      'The data is schedulable, but with little slack. Generation may be slow or fail once availability and student clashes are taken into account.',
  },
  INFEASIBLE: {
    tone: 'danger',
    label: 'Not schedulable',
    blurb:
      'At least one resource is over-subscribed, so no valid timetable can exist yet. Apply one of the fixes below, then re-check.',
  },
}

const CATEGORY_ICON: Record<string, Parameters<typeof Icon>[0]['name']> = {
  lab: 'labs',
  faculty: 'faculty',
  time: 'calendar',
  duration: 'clock',
}

export default function FeasibilityPanel({
  report,
  onClose,
}: {
  report: FeasibilityReport
  onClose?: () => void
}) {
  const verdict = VERDICT[report.verdict]
  const utilisation =
    report.totalSessionCapacity > 0
      ? Math.round((report.totalSessionsRequired / report.totalSessionCapacity) * 100)
      : 100

  return (
    <Card
      className="mb-4"
      title="Feasibility check"
      description="A fast resource audit run before generation — no schedule is produced"
      actions={
        onClose ? (
          <Button size="sm" onClick={onClose}>
            <Icon name="close" className="h-3.5 w-3.5" />
            Dismiss
          </Button>
        ) : undefined
      }
    >
      <div className="mb-4 flex items-center gap-3">
        <Badge tone={verdict.tone}>{verdict.label}</Badge>
        <p className="text-[13px] text-navy-600">{verdict.blurb}</p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <StatCard
          label="Sessions required"
          value={report.totalSessionsRequired}
          sub="per week, all batches"
        />
        <StatCard
          label="Session capacity"
          value={report.totalSessionCapacity}
          sub="labs × days × blocks"
          tone={report.totalSessionCapacity >= report.totalSessionsRequired ? 'ok' : 'danger'}
        />
        <StatCard
          label="Demand vs capacity"
          value={`${utilisation}%`}
          sub={utilisation > 100 ? 'over capacity' : 'of capacity used'}
          tone={utilisation > 100 ? 'danger' : utilisation >= 85 ? 'warn' : 'ok'}
        />
      </div>

      {/* Hard blockers: practicals that cannot be placed at all */}
      {report.blockers.length > 0 && (
        <div className="mt-4 rounded-lg border border-danger-200 bg-danger-50/60 p-3">
          <div className="mb-2 flex items-center gap-2 text-[12px] font-semibold text-danger-800">
            <Icon name="close" className="h-4 w-4" />
            {report.blockers.length} blocking issue{report.blockers.length > 1 ? 's' : ''}
          </div>
          <ul className="space-y-1.5 text-[13px] text-navy-700">
            {report.blockers.map((b, i) => (
              <li key={i} className="flex gap-2">
                <span className="text-danger-600">•</span>
                <span>
                  {(b.subjectName || b.batchName) && (
                    <span className="font-medium">
                      {[b.subjectName, b.batchName && `${b.batchName}`].filter(Boolean).join(' — ')}
                      {b.division ? ` (Div ${b.division})` : ''}:{' '}
                    </span>
                  )}
                  {b.message}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Resource checks */}
      {report.resourceChecks.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
            Resource checks
          </div>
          <div className="space-y-2.5">
            {report.resourceChecks.map((c) => {
              const pct = Math.min(100, Math.round(c.utilizationPercent))
              return (
                <div key={c.key} className="rounded-md border border-navy-100 px-3 py-2">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-[13px] font-medium text-navy-700">{c.label}</span>
                    <span
                      className={`tabular text-[12px] font-semibold ${
                        c.satisfied ? 'text-ok-700' : 'text-danger-700'
                      }`}
                    >
                      {c.required} / {c.available} {c.unit}
                    </span>
                  </div>
                  <div className="mt-1.5">
                    <Meter percent={pct} tone={c.satisfied ? (pct >= 85 ? 'warn' : 'ok') : 'danger'} />
                  </div>
                  <p className="mt-1 text-[11px] text-navy-500">{c.detail}</p>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Resolution suggestions */}
      {report.suggestions.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
            How to fix it
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            {report.suggestions.map((s, i) => (
              <div
                key={i}
                className="flex gap-2.5 rounded-lg border border-navy-100 bg-navy-50/40 p-3"
              >
                <div className="mt-0.5 text-navy-500">
                  <Icon name={CATEGORY_ICON[s.category] ?? 'plus'} className="h-4 w-4" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-[13px] font-semibold text-navy-800">{s.title}</span>
                    {s.estimatedGainSessions != null && (
                      <Badge tone="info">+{s.estimatedGainSessions} / wk</Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-[12px] text-navy-600">{s.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Per-batch load breakdown */}
      {report.batchLoads.length > 0 && (
        <details className="mt-4 rounded-md border border-navy-100">
          <summary className="cursor-pointer px-3 py-2 text-[12px] font-medium text-navy-600 select-none">
            Per-batch demand ({report.batchLoads.length} batches)
          </summary>
          <div className="border-t border-navy-100">
            <Table
              head={
                <tr>
                  <Th>Batch</Th>
                  <Th>Division</Th>
                  <Th>Lab type</Th>
                  <Th className="text-right">Sessions / week</Th>
                </tr>
              }
            >
              {report.batchLoads.map((b) => (
                <tr key={b.batchId}>
                  <Td className="font-medium">{b.batchName}</Td>
                  <Td>{b.division ?? '—'}</Td>
                  <Td>{b.labType}</Td>
                  <Td className="tabular text-right">{b.sessionsRequired}</Td>
                </tr>
              ))}
            </Table>
          </div>
        </details>
      )}

      {report.notes.length > 0 && (
        <ul className="mt-4 space-y-1 border-t border-navy-100 pt-3 text-[11px] text-navy-500">
          {report.notes.map((n, i) => (
            <li key={i} className="flex gap-1.5">
              <Icon name="info" className="mt-0.5 h-3 w-3 shrink-0" />
              {n}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
