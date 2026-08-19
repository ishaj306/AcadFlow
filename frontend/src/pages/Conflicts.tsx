import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, formatDateTime, hhmm, titleCase } from '../lib/api'
import type { Conflict } from '../lib/types'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  PageHeader,
  Select,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  statusTone,
} from '../components/ui'

const SEVERITY_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export default function Conflicts() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('OPEN')

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['conflicts', status],
    queryFn: () => api<Conflict[]>(`/conflicts${status ? `?status=${status}` : ''}`),
  })

  const update = useMutation({
    mutationFn: (input: { id: number; status: string }) =>
      api<Conflict>(`/conflicts/${input.id}/status?status=${input.status}`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conflicts'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })

  const counts = SEVERITY_ORDER.map((severity) => ({
    severity,
    count: (data ?? []).filter((c) => c.severity === severity).length,
  }))

  return (
    <>
      <PageHeader
        title="Conflicts"
        description="Every scheduled session is re-checked against the database. Critical and high findings block publication; medium and low are advisory."
        actions={
          <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-36">
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="IGNORED">Ignored</option>
            <option value="">All</option>
          </Select>
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {counts.map(({ severity, count }) => (
          <StatCard
            key={severity}
            label={titleCase(severity)}
            value={count}
            tone={count === 0 ? 'ok' : statusTone(severity)}
            sub={
              severity === 'CRITICAL' || severity === 'HIGH'
                ? 'Blocks publication'
                : 'Advisory only'
            }
          />
        ))}
      </div>

      <Card className="mt-4" bodyClassName="">
        {isLoading && <Spinner label="Loading conflicts" />}
        {error && <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />}
        {data && data.length === 0 && (
          <EmptyState
            title="No conflicts"
            hint="Every hard constraint is satisfied for the current schedule."
          />
        )}
        {data && data.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Severity</Th>
                <Th>Type</Th>
                <Th>Description</Th>
                <Th>When</Th>
                <Th>Suggested resolution</Th>
                <Th>Detected</Th>
                <Th />
              </tr>
            }
          >
            {[...data]
              .sort(
                (a, b) =>
                  SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity),
              )
              .map((conflict) => (
                <tr key={conflict.id} className="hover:bg-navy-50/50">
                  <Td>
                    <Badge tone={statusTone(conflict.severity)}>{conflict.severity}</Badge>
                  </Td>
                  <Td className="whitespace-nowrap">{conflict.conflictLabel}</Td>
                  <Td className="max-w-[420px] text-[12px] text-navy-700">
                    {conflict.description}
                  </Td>
                  <Td className="text-[12px] whitespace-nowrap text-navy-500">
                    {conflict.dayOfWeek
                      ? `${titleCase(conflict.dayOfWeek)} ${hhmm(conflict.startTime)}`
                      : '—'}
                  </Td>
                  <Td className="max-w-[280px] text-[12px] text-navy-500">
                    {conflict.suggestedResolution ?? '—'}
                  </Td>
                  <Td className="text-[12px] whitespace-nowrap text-navy-500">
                    {formatDateTime(conflict.detectedAt)}
                  </Td>
                  <Td>
                    {conflict.status === 'OPEN' && (
                      <div className="flex gap-1">
                        <Button
                          size="sm"
                          onClick={() => update.mutate({ id: conflict.id, status: 'RESOLVED' })}
                        >
                          Resolve
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => update.mutate({ id: conflict.id, status: 'IGNORED' })}
                        >
                          Ignore
                        </Button>
                      </div>
                    )}
                    {conflict.status !== 'OPEN' && (
                      <Badge tone={statusTone(conflict.status)}>{conflict.status}</Badge>
                    )}
                  </Td>
                </tr>
              ))}
          </Table>
        )}
      </Card>
    </>
  )
}
