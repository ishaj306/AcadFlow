import { Fragment, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { Batch, IdName, Subject } from '../lib/types'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  InfoNote,
  PageHeader,
  Select,
  Spinner,
  StatCard,
  Table,
  Td,
  Th,
  TextInput,
} from '../components/ui'

export default function Batches() {
  const queryClient = useQueryClient()
  const [departmentId, setDepartmentId] = useState('')
  const [semester, setSemester] = useState('5')
  const [maxBatchSize, setMaxBatchSize] = useState('')
  const [regenerate, setRegenerate] = useState(true)
  const [banner, setBanner] = useState<{ tone: 'ok' | 'danger' | 'warn'; text: string } | null>(null)
  const [subjectFilter, setSubjectFilter] = useState('')
  const [expanded, setExpanded] = useState<number | null>(null)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })

  const subjects = useQuery({
    queryKey: ['subjects', 'practical'],
    queryFn: () => api<Subject[]>('/subjects?practicalOnly=true'),
  })

  const batches = useQuery({
    queryKey: ['batches'],
    queryFn: () => api<Batch[]>('/batches'),
  })

  const generate = useMutation({
    mutationFn: () =>
      api<{
        subjectsProcessed: number
        batchesCreated: number
        batchesReplaced: number
        studentsAssigned: number
        warnings: string[]
      }>('/batches/generate', {
        method: 'POST',
        body: {
          departmentId: Number(departmentId),
          semester: Number(semester),
          maxBatchSize: maxBatchSize ? Number(maxBatchSize) : null,
          regenerate,
        },
        timeoutMs: 120_000,
      }),
    onSuccess: (result) => {
      setBanner({
        tone: result.warnings.length > 0 ? 'warn' : 'ok',
        text:
          `${result.batchesCreated} batch(es) created across ${result.subjectsProcessed} subject(s); ` +
          `${result.studentsAssigned} student placements made.` +
          (result.warnings.length ? ` ${result.warnings.join(' ')}` : ''),
      })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Batch generation failed.',
      }),
  })

  const rows = useMemo(
    () =>
      (batches.data ?? []).filter(
        (batch) => !subjectFilter || batch.subjectId === Number(subjectFilter),
      ),
    [batches.data, subjectFilter],
  )

  const totals = useMemo(() => {
    const list = batches.data ?? []
    return {
      batches: list.length,
      students: list.reduce((sum, b) => sum + b.studentCount, 0),
      subjects: new Set(list.map((b) => b.subjectId)).size,
      divisions: new Set(list.map((b) => b.division)).size,
    }
  }, [batches.data])

  return (
    <>
      <PageHeader
        title="Practical batches"
        description="Divisions are split into evenly sized batches that never exceed the capacity of a matching laboratory. 60 students at a capacity of 25 become 20/20/20, not 25/25/10."
      />

      {banner && (
        <div className="mb-4">
          <InfoNote tone={banner.tone}>{banner.text}</InfoNote>
        </div>
      )}

      <Card className="mb-4" title="Generate batches">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Field label="Department">
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">Select…</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Semester">
            <Select value={semester} onChange={(e) => setSemester(e.target.value)}>
              {Array.from({ length: 8 }, (_, i) => i + 1).map((s) => (
                <option key={s} value={s}>
                  Semester {s}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Maximum batch size" hint="Optional override">
            <TextInput
              type="number"
              min={1}
              value={maxBatchSize}
              placeholder="From subject/lab"
              onChange={(e) => setMaxBatchSize(e.target.value)}
            />
          </Field>
          <Field label="Existing batches">
            <Select
              value={regenerate ? 'replace' : 'keep'}
              onChange={(e) => setRegenerate(e.target.value === 'replace')}
            >
              <option value="replace">Replace</option>
              <option value="keep">Keep (skip)</option>
            </Select>
          </Field>
          <div className="flex items-end">
            <Button
              variant="primary"
              className="w-full justify-center"
              disabled={!departmentId || generate.isPending}
              onClick={() => generate.mutate()}
            >
              <Icon name="play" className="h-3.5 w-3.5" />
              {generate.isPending ? 'Generating…' : 'Generate'}
            </Button>
          </div>
        </div>
      </Card>

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Batches" value={totals.batches} />
        <StatCard label="Student placements" value={totals.students} sub="One per subject" />
        <StatCard label="Subjects covered" value={totals.subjects} />
        <StatCard label="Divisions" value={totals.divisions} />
      </div>

      <Card
        title="Generated batches"
        description={`${rows.length} shown`}
        actions={
          <Select
            value={subjectFilter}
            onChange={(e) => setSubjectFilter(e.target.value)}
            className="w-56"
          >
            <option value="">All subjects</option>
            {(subjects.data ?? []).map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.subjectName}
              </option>
            ))}
          </Select>
        }
        bodyClassName=""
      >
        {batches.isLoading && <Spinner label="Loading batches" />}
        {batches.error && (
          <ErrorNote message={(batches.error as Error).message} onRetry={() => batches.refetch()} />
        )}
        {batches.data && rows.length === 0 && (
          <EmptyState
            title="No batches yet"
            hint="Generate batches for a department and semester to begin."
          />
        )}
        {rows.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Batch</Th>
                <Th>Subject</Th>
                <Th>Division</Th>
                <Th className="text-right">Students</Th>
                <Th className="text-right">Capacity</Th>
                <Th>Required lab</Th>
                <Th>Roll numbers</Th>
              </tr>
            }
          >
            {rows.map((batch) => (
              <Fragment key={batch.id}>
                <tr className="hover:bg-navy-50/50">
                  <Td className="font-medium whitespace-nowrap">{batch.batchName}</Td>
                  <Td>
                    <div>{batch.subjectName}</div>
                    <div className="tabular text-[12px] text-navy-500">{batch.subjectCode}</div>
                  </Td>
                  <Td>{batch.division}</Td>
                  <Td className="tabular text-right font-medium">{batch.studentCount}</Td>
                  <Td className="tabular text-right text-navy-500">{batch.capacity}</Td>
                  <Td>
                    <Badge>{batch.requiredLabType}</Badge>
                  </Td>
                  <Td>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setExpanded(expanded === batch.id ? null : batch.id)}
                    >
                      {expanded === batch.id ? 'Hide' : `Show ${batch.students.length}`}
                    </Button>
                  </Td>
                </tr>
                {expanded === batch.id && (
                  <tr>
                    <td colSpan={7} className="bg-navy-50/60 px-3 py-2">
                      <div className="mb-1 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                        {batch.batchName} — {batch.students.length} students
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {batch.students.map((student) => (
                          <span
                            key={student.studentId}
                            className="tabular rounded border border-navy-200 bg-white px-1.5 py-0.5 text-[11px] text-navy-700"
                            title={student.name}
                          >
                            {student.rollNumber}
                          </span>
                        ))}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </Table>
        )}
      </Card>
    </>
  )
}
