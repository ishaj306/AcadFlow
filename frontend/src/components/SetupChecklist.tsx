import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import type { SetupStatus } from '../lib/types'
import { Icon } from './icons'
import { Card, Meter } from './ui'

/**
 * Shown while an installation is still being populated.
 *
 * A fresh database holds only the roles and one administrator, so every screen
 * would otherwise be empty with no indication of what to do first. Steps are
 * listed in dependency order and each says exactly what is missing.
 */
export default function SetupChecklist({ compact = false }: { compact?: boolean }) {
  const { data } = useQuery({
    queryKey: ['setup'],
    queryFn: () => api<SetupStatus>('/setup/status'),
  })

  if (!data || data.ready) return null

  const percent = Math.round((data.completedSteps / data.totalSteps) * 100)
  // The first incomplete step is the one to do next; everything after it waits.
  const nextIndex = data.steps.findIndex((step) => !step.complete)

  return (
    <Card
      className={compact ? '' : 'mb-4'}
      title="Set up your college"
      description="Work through these in order — each step depends on the ones above it."
      actions={
        <span className="tabular text-[12px] text-navy-500">
          {data.completedSteps} of {data.totalSteps} done
        </span>
      }
    >
      <Meter percent={percent} tone={percent === 100 ? 'ok' : 'info'} />

      <ol className="mt-4 space-y-1">
        {data.steps.map((step, index) => {
          const isNext = index === nextIndex
          const waiting = !step.complete && !isNext

          return (
            <li key={step.key}>
              <Link
                to={step.route}
                className={`flex items-start gap-3 rounded border px-3 py-2.5 transition-colors ${
                  isNext
                    ? 'border-navy-300 bg-navy-50 hover:bg-navy-100/60'
                    : 'border-transparent hover:bg-navy-50/60'
                }`}
              >
                <span
                  className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold ${
                    step.complete
                      ? 'border-ok-600 bg-ok-600 text-white'
                      : isNext
                        ? 'border-navy-700 bg-white text-navy-800'
                        : 'border-navy-200 bg-white text-navy-300'
                  }`}
                >
                  {step.complete ? <Icon name="check" className="h-3 w-3" /> : index + 1}
                </span>

                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span
                      className={`text-[13px] font-medium ${
                        step.complete ? 'text-navy-500 line-through' : waiting ? 'text-navy-400' : 'text-navy-900'
                      }`}
                    >
                      {step.title}
                    </span>
                    {step.complete && step.count > 0 && (
                      <span className="tabular text-[11px] text-ok-700">{step.count}</span>
                    )}
                    {isNext && (
                      <span className="rounded border border-navy-300 bg-white px-1.5 text-[10px] font-semibold tracking-wide text-navy-700 uppercase">
                        Next
                      </span>
                    )}
                  </span>

                  <span
                    className={`mt-0.5 block text-[12px] ${
                      waiting ? 'text-navy-400' : 'text-navy-500'
                    }`}
                  >
                    {step.complete ? step.description : (step.blocker ?? step.description)}
                  </span>
                </span>

                {!step.complete && (
                  <Icon name="chevron" className="mt-1 h-4 w-4 shrink-0 text-navy-300" />
                )}
              </Link>
            </li>
          )
        })}
      </ol>
    </Card>
  )
}
