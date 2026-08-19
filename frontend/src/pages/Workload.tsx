import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { WorkloadSummary } from '../lib/types'
import {
  Badge,
  Card,
  ErrorNote,
  Meter,
  PageHeader,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  statusTone,
} from '../components/ui'

export default function Workload() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['workload'],
    queryFn: () => api<WorkloadSummary>('/workload'),
    retry: false,
  })

  if (isLoading) return <Spinner label="Loading workload" />
  if (error)
    return (
      <>
        <PageHeader title="Faculty workload" />
        <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />
      </>
    )
  if (!data) return null

  return (
    <>
      <PageHeader
        title="Faculty workload"
        description={`Assigned practical hours against each member's weekly limit, from ${data.timetableName}.`}
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard
          label="Average utilisation"
          value={`${data.averageUtilizationPercent}%`}
          sub={`${data.facultyCount} faculty members`}
        />
        <StatCard
          label="Workload spread"
          value={`${data.spreadHours}h`}
          sub={`Busiest minus quietest · ${data.balanceVerdict}`}
          tone={statusTone(data.balanceVerdict)}
        />
        <StatCard
          label="Overloaded"
          value={data.overloadedCount}
          sub="Above weekly limit"
          tone={data.overloadedCount === 0 ? 'ok' : 'danger'}
        />
        <StatCard
          label="Near limit"
          value={data.nearLimitCount}
          sub="85% or more of limit"
          tone={data.nearLimitCount === 0 ? 'ok' : 'warn'}
        />
      </div>

      <div className="mt-3 flex flex-wrap gap-2 text-[12px] text-navy-600">
        <Badge tone="ok">Balanced {data.balancedCount}</Badge>
        <Badge tone="info">Under-utilised {data.underutilizedCount}</Badge>
        <Badge tone="warn">Near limit {data.nearLimitCount}</Badge>
        <Badge tone="danger">Overloaded {data.overloadedCount}</Badge>
      </div>

      <Card className="mt-4" title="Per-faculty breakdown" bodyClassName="">
        <Table
          head={
            <tr>
              <Th>Faculty</Th>
              <Th>Designation</Th>
              <Th className="text-right">Assigned</Th>
              <Th className="text-right">Maximum</Th>
              <Th className="w-48">Utilisation</Th>
              <Th className="text-right">Practicals</Th>
              <Th className="text-right">Free periods</Th>
              <Th>Status</Th>
              <Th>Qualified for</Th>
            </tr>
          }
        >
          {data.faculty.map((row) => (
            <tr key={row.facultyId} className="hover:bg-navy-50/50">
              <Td>
                <div className="font-medium">{row.facultyName}</div>
                <div className="tabular text-[12px] text-navy-500">
                  {row.employeeCode} · {row.departmentCode}
                </div>
              </Td>
              <Td className="text-[12px] text-navy-600">{row.designation}</Td>
              <Td className="tabular text-right font-medium">{row.assignedHours}h</Td>
              <Td className="tabular text-right text-navy-500">{row.maxWeeklyHours}h</Td>
              <Td>
                <div className="tabular mb-1 text-[12px] text-navy-600">
                  {row.utilizationPercent}%
                </div>
                <Meter percent={row.utilizationPercent} tone={statusTone(row.status)} />
              </Td>
              <Td className="tabular text-right">{row.practicalCount}</Td>
              <Td className="tabular text-right text-navy-500">{row.freeTeachingSlots}</Td>
              <Td>
                <Badge tone={statusTone(row.status)}>{row.status}</Badge>
              </Td>
              <Td className="max-w-[240px] text-[12px] text-navy-500">
                {row.subjects.length > 0 ? row.subjects.join(', ') : '—'}
              </Td>
            </tr>
          ))}
        </Table>
      </Card>
    </>
  )
}
