import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api, formatDate, hhmm, titleCase } from '../lib/api'
import type { Dashboard as DashboardData } from '../lib/types'
import SetupChecklist from '../components/SetupChecklist'
import {
  Badge,
  Card,
  EmptyState,
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

const OK = '#15803d'
const WARN = '#b45309'
const DANGER = '#b91c1c'
const NAVY = '#486581'

export default function Dashboard() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<DashboardData>('/dashboard'),
  })

  if (isLoading) return <Spinner label="Loading dashboard" />
  if (error) return <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />
  if (!data) return null

  const c = data.counters

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`${data.currentTermLabel}${
          data.publishedTimetableName ? ` · ${data.publishedTimetableName}` : ' · no timetable published yet'
        }`}
      />

      <SetupChecklist />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Students" value={c.totalStudents} sub="Active enrolment" />
        <StatCard label="Faculty" value={c.totalFaculty} sub="Teaching staff" />
        <StatCard label="Laboratories" value={c.totalLabs} sub="Available for practicals" />
        <StatCard label="Practical batches" value={c.practicalBatches} sub="Current term" />
        <StatCard
          label="Today's practicals"
          value={c.todaysPracticals}
          sub={`${titleCase(data.todayDayOfWeek)}, ${formatDate(data.today)}`}
          tone="info"
        />
        <StatCard
          label="Open conflicts"
          value={c.activeConflicts}
          sub={c.activeConflicts === 0 ? 'None outstanding' : 'Needs review'}
          tone={c.activeConflicts === 0 ? 'ok' : 'danger'}
        />
        <StatCard
          label="Faculty overloaded"
          value={c.overloadedFaculty}
          sub={c.overloadedFaculty === 0 ? 'All within limits' : 'Over weekly hours'}
          tone={c.overloadedFaculty === 0 ? 'ok' : 'danger'}
        />
        <StatCard
          label="Lab utilisation"
          value={`${c.labUtilizationPercent}%`}
          sub={c.scheduleScore != null ? `Schedule score ${c.scheduleScore}/100` : 'No published schedule'}
          tone="neutral"
        />
      </div>

      {(c.pendingReschedules > 0 || c.pendingLeaves > 0) && (
        <div className="mt-3 flex flex-wrap gap-3">
          {c.pendingReschedules > 0 && (
            <Link
              to="/rescheduling"
              className="flex-1 rounded border border-warn-600/25 bg-warn-50 px-4 py-2.5 text-[13px] text-warn-700 hover:bg-warn-50/70"
            >
              <strong>{c.pendingReschedules}</strong> rescheduling proposal(s) awaiting approval →
            </Link>
          )}
          {c.pendingLeaves > 0 && (
            <Link
              to="/faculty"
              className="flex-1 rounded border border-info-600/25 bg-info-50 px-4 py-2.5 text-[13px] text-info-700 hover:bg-info-50/70"
            >
              <strong>{c.pendingLeaves}</strong> leave request(s) pending review →
            </Link>
          )}
        </div>
      )}

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <Card
          title="Faculty workload distribution"
          description="Assigned practical hours per week against each member's limit"
        >
          {data.facultyWorkload.length === 0 ? (
            <EmptyState title="No workload yet" hint="Publish a timetable to see workload." />
          ) : (
            <ResponsiveContainer width="100%" height={Math.max(220, data.facultyWorkload.length * 22)}>
              <BarChart
                data={data.facultyWorkload}
                layout="vertical"
                margin={{ left: 8, right: 16, top: 4, bottom: 4 }}
              >
                <CartesianGrid horizontal={false} stroke="#e6ebf1" />
                <XAxis
                  type="number"
                  tick={{ fontSize: 11, fill: '#627d98' }}
                  stroke="#bcccdc"
                  unit="h"
                />
                <YAxis
                  type="category"
                  dataKey="employeeCode"
                  width={72}
                  tick={{ fontSize: 11, fill: '#627d98' }}
                  stroke="#bcccdc"
                />
                <Tooltip
                  cursor={{ fill: '#f4f6f8' }}
                  contentStyle={{ fontSize: 12, borderColor: '#d9e2ec', borderRadius: 4 }}
                  formatter={((value: unknown, _name: unknown, item: { payload: (typeof data.facultyWorkload)[number] }) => [
                    `${value}h of ${item.payload.maxHours}h (${item.payload.utilizationPercent}%)`,
                    item.payload.facultyName,
                  ]) as never}
                />
                <Bar dataKey="assignedHours" radius={[0, 2, 2, 0]} barSize={11}>
                  {data.facultyWorkload.map((row) => (
                    <Cell
                      key={row.employeeCode}
                      fill={
                        row.status === 'Overloaded'
                          ? DANGER
                          : row.status === 'Near limit'
                            ? WARN
                            : row.status === 'Balanced'
                              ? OK
                              : NAVY
                      }
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card
          title="Laboratory utilisation"
          description="Share of weekly teaching periods each laboratory is booked for"
        >
          {data.labUtilization.length === 0 ? (
            <EmptyState title="No utilisation yet" hint="Publish a timetable to see utilisation." />
          ) : (
            <div className="space-y-2.5">
              {data.labUtilization.map((lab) => (
                <div key={lab.labCode}>
                  <div className="mb-1 flex items-baseline justify-between gap-2">
                    <span className="truncate text-[13px] text-navy-800">{lab.labName}</span>
                    <span className="tabular shrink-0 text-[12px] text-navy-500">
                      {lab.sessionsPerWeek} sessions · {lab.utilizationPercent}%
                    </span>
                  </div>
                  <Meter
                    percent={lab.utilizationPercent}
                    tone={
                      lab.utilizationPercent >= 85
                        ? 'danger'
                        : lab.utilizationPercent >= 60
                          ? 'warn'
                          : lab.utilizationPercent > 0
                            ? 'ok'
                            : 'neutral'
                    }
                  />
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <Card title="Today's practicals" description={`${titleCase(data.todayDayOfWeek)} schedule`} bodyClassName="">
          {data.todaysPracticals.length === 0 ? (
            <EmptyState title="No practicals today" hint="Nothing is scheduled for today." />
          ) : (
            <Table
              head={
                <tr>
                  <Th>Time</Th>
                  <Th>Subject</Th>
                  <Th>Batch</Th>
                  <Th>Faculty</Th>
                  <Th>Laboratory</Th>
                </tr>
              }
            >
              {data.todaysPracticals.map((entry) => (
                <tr key={entry.id} className="hover:bg-navy-50/50">
                  <Td className="tabular whitespace-nowrap">
                    {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
                  </Td>
                  <Td className="font-medium">{entry.subjectName}</Td>
                  <Td>
                    {entry.batchName} <span className="text-navy-400">({entry.division})</span>
                  </Td>
                  <Td>{entry.facultyName}</Td>
                  <Td>{entry.labName}</Td>
                </tr>
              ))}
            </Table>
          )}
        </Card>

        <Card
          title="Unresolved conflicts"
          description="Detected by the conflict engine against the published schedule"
          bodyClassName=""
          actions={
            <Link to="/conflicts" className="text-[12px] font-medium text-info-600 hover:underline">
              View all
            </Link>
          }
        >
          {data.openConflicts.length === 0 ? (
            <EmptyState
              title="No open conflicts"
              hint="Every hard constraint is currently satisfied."
            />
          ) : (
            <Table
              head={
                <tr>
                  <Th>Severity</Th>
                  <Th>Type</Th>
                  <Th>Description</Th>
                </tr>
              }
            >
              {data.openConflicts.map((conflict) => (
                <tr key={conflict.id} className="hover:bg-navy-50/50">
                  <Td>
                    <Badge tone={statusTone(conflict.severity)}>{conflict.severity}</Badge>
                  </Td>
                  <Td className="whitespace-nowrap">{conflict.conflictLabel}</Td>
                  <Td className="text-[12px] text-navy-600">{conflict.description}</Td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>

      {data.upcomingPracticals.length > 0 && (
        <Card className="mt-4" title="Upcoming practicals" description="Next working days" bodyClassName="">
          <Table
            head={
              <tr>
                <Th>Day</Th>
                <Th>Time</Th>
                <Th>Subject</Th>
                <Th>Batch</Th>
                <Th>Faculty</Th>
                <Th>Laboratory</Th>
              </tr>
            }
          >
            {data.upcomingPracticals.map((entry) => (
              <tr key={entry.id} className="hover:bg-navy-50/50">
                <Td className="whitespace-nowrap">{titleCase(entry.dayOfWeek)}</Td>
                <Td className="tabular whitespace-nowrap">
                  {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
                </Td>
                <Td className="font-medium">{entry.subjectName}</Td>
                <Td>
                  {entry.batchName} <span className="text-navy-400">({entry.division})</span>
                </Td>
                <Td>{entry.facultyName}</Td>
                <Td>{entry.labName}</Td>
              </tr>
            ))}
          </Table>
        </Card>
      )}
    </>
  )
}
