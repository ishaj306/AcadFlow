import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { IdName, Subject } from '../lib/types'
import Modal, { ConfirmDialog } from '../components/Modal'
import { Icon } from '../components/icons'
import {
  CsvImportButtons,
  ImportResultPanel,
  downloadCsv,
  useCsvImport,
} from '../components/CsvImport'
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
  Table,
  Td,
  Th,
  TextInput,
  statusTone,
} from '../components/ui'

const empty = {
  subjectCode: '',
  subjectName: '',
  departmentId: '',
  semester: '5',
  subjectType: 'PRACTICAL',
  practicalDurationMin: '120',
  sessionsPerWeek: '1',
  studentsPerBatch: '30',
  requiredLabType: '',
  status: 'ACTIVE',
}

const CSV_TEMPLATE =
  'subject_code,subject_name,department_code,semester,subject_type,practical_duration_min,sessions_per_week,students_per_batch,required_lab_type\r\n' +
  'CS501,Java Programming Lab,CSE,5,PRACTICAL,120,1,30,Programming\r\n'

export default function Subjects() {
  const queryClient = useQueryClient()
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [practicalOnly, setPracticalOnly] = useState('true')
  const [form, setForm] = useState<typeof empty | null>(null)
  const [editId, setEditId] = useState<number | null>(null)
  const [toDelete, setToDelete] = useState<Subject | null>(null)
  const [error, setError] = useState<string | null>(null)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })

  const labTypes = useQuery({
    queryKey: ['labTypes'],
    queryFn: () => api<string[]>('/subjects/lab-types'),
  })

  const params = new URLSearchParams({ practicalOnly })
  if (departmentFilter) params.set('departmentId', departmentFilter)

  const { data, isLoading, error: loadError, refetch } = useQuery({
    queryKey: ['subjects', params.toString()],
    queryFn: () => api<Subject[]>(`/subjects?${params.toString()}`),
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['subjects'] })
    queryClient.invalidateQueries({ queryKey: ['labTypes'] })
    queryClient.invalidateQueries({ queryKey: ['setup'] })
  }

  function fail(err: unknown) {
    setError(err instanceof ApiError ? err.message : 'The request failed.')
  }

  const save = useMutation({
    mutationFn: (values: typeof empty) => {
      const body = {
        ...values,
        departmentId: Number(values.departmentId),
        semester: Number(values.semester),
        practicalDurationMin: Number(values.practicalDurationMin),
        sessionsPerWeek: Number(values.sessionsPerWeek),
        studentsPerBatch: Number(values.studentsPerBatch),
      }
      return editId == null
        ? api<Subject>('/subjects', { method: 'POST', body })
        : api<Subject>(`/subjects/${editId}`, { method: 'PUT', body })
    },
    onSuccess: () => {
      setForm(null)
      setEditId(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const remove = useMutation({
    mutationFn: (id: number) => api(`/subjects/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setToDelete(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const imp = useCsvImport('/subjects/import', refresh)

  const noDepartments = departments.data?.length === 0

  return (
    <>
      <PageHeader
        title="Subjects"
        description="Practical definitions drive scheduling: duration, sessions per week, batch capacity and the laboratory type required."
        actions={
          <>
            <Select
              value={departmentFilter}
              onChange={(e) => setDepartmentFilter(e.target.value)}
              className="w-44"
            >
              <option value="">All departments</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
            <Select
              value={practicalOnly}
              onChange={(e) => setPracticalOnly(e.target.value)}
              className="w-44"
            >
              <option value="true">Practical subjects</option>
              <option value="false">All subjects</option>
            </Select>
            <CsvImportButtons
              imp={imp}
              onDownloadTemplate={() => downloadCsv('subjects-template.csv', CSV_TEMPLATE)}
              disabled={noDepartments}
              disabledTitle="Add a department first"
            />
            <Button
              variant="primary"
              disabled={noDepartments}
              title={noDepartments ? 'Add a department first' : undefined}
              onClick={() => {
                setEditId(null)
                setForm({ ...empty, departmentId: String(departments.data?.[0]?.id ?? '') })
                setError(null)
              }}
            >
              <Icon name="plus" className="h-3.5 w-3.5" />
              Add subject
            </Button>
          </>
        }
      />

      {noDepartments && (
        <div className="mb-4">
          <InfoNote tone="warn">
            Add a department in Settings before adding subjects — every subject belongs to one.
          </InfoNote>
        </div>
      )}

      <ImportResultPanel result={imp.result} error={imp.error} referenceLabel="Subject code" />

      <Card bodyClassName="">
        {isLoading && <Spinner label="Loading subjects" />}
        {loadError && <ErrorNote message={(loadError as Error).message} onRetry={() => refetch()} />}
        {data && data.length === 0 && (
          <EmptyState
            title="No subjects yet"
            hint="Add the practical subjects for the semester you are scheduling. Duration and batch size determine how batches are formed."
          />
        )}
        {data && data.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Code</Th>
                <Th>Subject</Th>
                <Th>Department</Th>
                <Th className="text-right">Sem</Th>
                <Th className="text-right">Duration</Th>
                <Th className="text-right">Per week</Th>
                <Th className="text-right">Batch size</Th>
                <Th>Required lab</Th>
                <Th className="text-right">Qualified staff</Th>
                <Th>Status</Th>
                <Th />
              </tr>
            }
          >
            {data.map((subject) => (
              <tr key={subject.id} className="hover:bg-navy-50/50">
                <Td className="tabular font-medium">{subject.subjectCode}</Td>
                <Td>{subject.subjectName}</Td>
                <Td>{subject.departmentCode}</Td>
                <Td className="tabular text-right">{subject.semester}</Td>
                <Td className="tabular text-right">{subject.practicalDurationMin} min</Td>
                <Td className="tabular text-right">{subject.sessionsPerWeek}</Td>
                <Td className="tabular text-right">{subject.studentsPerBatch}</Td>
                <Td>
                  <Badge>{subject.requiredLabType}</Badge>
                </Td>
                <Td className="tabular text-right">
                  {subject.qualifiedFacultyCount === 0 ? (
                    <span
                      className="font-semibold text-danger-700"
                      title="No qualified faculty — this subject cannot be scheduled"
                    >
                      0
                    </span>
                  ) : (
                    subject.qualifiedFacultyCount
                  )}
                </Td>
                <Td>
                  <Badge tone={statusTone(subject.status)}>{subject.status}</Badge>
                </Td>
                <Td>
                  <div className="flex justify-end gap-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setEditId(subject.id)
                        setForm({
                          subjectCode: subject.subjectCode,
                          subjectName: subject.subjectName,
                          departmentId: String(subject.departmentId),
                          semester: String(subject.semester),
                          subjectType: subject.subjectType,
                          practicalDurationMin: String(subject.practicalDurationMin),
                          sessionsPerWeek: String(subject.sessionsPerWeek),
                          studentsPerBatch: String(subject.studentsPerBatch),
                          requiredLabType: subject.requiredLabType,
                          status: subject.status,
                        })
                        setError(null)
                      }}
                    >
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setToDelete(subject)}>
                      Delete
                    </Button>
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        title={editId == null ? 'Add subject' : 'Edit subject'}
        description="Batch size and required laboratory type together decide how a division is split."
        open={form != null}
        error={error}
        submitting={save.isPending}
        width="max-w-2xl"
        onClose={() => {
          setForm(null)
          setEditId(null)
        }}
        onSubmit={() => form && save.mutate(form)}
      >
        {form && (
          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Subject code" hint="e.g. CS501">
                <TextInput
                  value={form.subjectCode}
                  required
                  onChange={(e) => setForm({ ...form, subjectCode: e.target.value })}
                />
              </Field>
              <Field label="Subject name">
                <TextInput
                  value={form.subjectName}
                  required
                  onChange={(e) => setForm({ ...form, subjectName: e.target.value })}
                />
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Department">
                <Select
                  value={form.departmentId}
                  required
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                >
                  <option value="">Select…</option>
                  {(departments.data ?? []).map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Semester">
                <Select
                  value={form.semester}
                  onChange={(e) => setForm({ ...form, semester: e.target.value })}
                >
                  {Array.from({ length: 8 }, (_, i) => i + 1).map((s) => (
                    <option key={s} value={s}>
                      Semester {s}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Type">
                <Select
                  value={form.subjectType}
                  onChange={(e) => setForm({ ...form, subjectType: e.target.value })}
                >
                  <option value="PRACTICAL">Practical</option>
                  <option value="BOTH">Theory and practical</option>
                  <option value="THEORY">Theory only</option>
                </Select>
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Practical duration" hint="Minutes; must fit whole periods">
                <TextInput
                  type="number"
                  min={15}
                  step={15}
                  value={form.practicalDurationMin}
                  required
                  onChange={(e) => setForm({ ...form, practicalDurationMin: e.target.value })}
                />
              </Field>
              <Field label="Sessions per week">
                <TextInput
                  type="number"
                  min={1}
                  max={10}
                  value={form.sessionsPerWeek}
                  required
                  onChange={(e) => setForm({ ...form, sessionsPerWeek: e.target.value })}
                />
              </Field>
              <Field label="Students per batch" hint="Upper limit per batch">
                <TextInput
                  type="number"
                  min={1}
                  value={form.studentsPerBatch}
                  required
                  onChange={(e) => setForm({ ...form, studentsPerBatch: e.target.value })}
                />
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field
                label="Required laboratory type"
                hint="Must match the type on your laboratories exactly"
              >
                <TextInput
                  value={form.requiredLabType}
                  required
                  list="lab-types"
                  placeholder="e.g. Programming"
                  onChange={(e) => setForm({ ...form, requiredLabType: e.target.value })}
                />
                <datalist id="lab-types">
                  {(labTypes.data ?? []).map((type) => (
                    <option key={type} value={type} />
                  ))}
                </datalist>
              </Field>
              <Field label="Status">
                <Select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </Select>
              </Field>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={toDelete != null}
        title="Delete subject"
        message={`Delete ${toDelete?.subjectName}? A subject that already has practical batches cannot be deleted — set it inactive instead.`}
        busy={remove.isPending}
        error={error}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
        onClose={() => {
          setToDelete(null)
          setError(null)
        }}
      />
    </>
  )
}
