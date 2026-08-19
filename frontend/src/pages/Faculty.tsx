import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, formatDate } from '../lib/api'
import type { FacultyLeave, Faculty as FacultyRow, IdName, Page, Subject } from '../lib/types'
import Modal from '../components/Modal'
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
  employeeCode: '',
  name: '',
  email: '',
  departmentId: '',
  designation: 'Assistant Professor',
  maxWeeklyHours: '18',
  status: 'ACTIVE',
  subjectIds: [] as number[],
}

const DESIGNATIONS = [
  'Professor',
  'Associate Professor',
  'Assistant Professor',
  'Lecturer',
  'Lab Assistant',
]

const CSV_TEMPLATE =
  'employee_code,name,email,department_code,designation,max_weekly_hours,subject_codes\r\n' +
  'EMP001,Dr. Example Sharma,emp001@college.edu,CSE,Assistant Professor,18,CS501;CS502\r\n'

export default function Faculty() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [page, setPage] = useState(0)

  const [form, setForm] = useState<typeof empty | null>(null)
  const [editId, setEditId] = useState<number | null>(null)
  const [account, setAccount] = useState<{ faculty: FacultyRow; username: string; password: string; role: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })

  const subjects = useQuery({
    queryKey: ['subjects', 'all-practical'],
    queryFn: () => api<Subject[]>('/subjects?practicalOnly=true'),
  })

  const params = new URLSearchParams({ page: String(page), size: '25' })
  if (search) params.set('search', search)
  if (departmentId) params.set('departmentId', departmentId)

  const list = useQuery({
    queryKey: ['faculty', params.toString()],
    queryFn: () => api<Page<FacultyRow>>(`/faculty?${params.toString()}`),
  })

  const leaves = useQuery({
    queryKey: ['leaves', 'PENDING'],
    queryFn: () => api<FacultyLeave[]>('/faculty/leaves?status=PENDING'),
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['faculty'] })
    queryClient.invalidateQueries({ queryKey: ['subjects'] })
    queryClient.invalidateQueries({ queryKey: ['setup'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  function fail(err: unknown) {
    setError(err instanceof ApiError ? err.message : 'The request failed.')
  }

  const save = useMutation({
    mutationFn: (values: typeof empty) => {
      const body = {
        ...values,
        departmentId: Number(values.departmentId),
        maxWeeklyHours: Number(values.maxWeeklyHours),
      }
      return editId == null
        ? api<FacultyRow>('/faculty', { method: 'POST', body })
        : api<FacultyRow>(`/faculty/${editId}`, { method: 'PUT', body })
    },
    onSuccess: () => {
      setForm(null)
      setEditId(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const createAccount = useMutation({
    mutationFn: (input: { id: number; username: string; password: string; role: string }) =>
      api<{ username: string }>(`/faculty/${input.id}/account?role=${input.role}`, {
        method: 'POST',
        body: { username: input.username, password: input.password },
      }),
    onSuccess: (result) => {
      setAccount(null)
      setError(null)
      setNotice(`Sign-in account '${result.username}' created.`)
      refresh()
    },
    onError: fail,
  })

  const decide = useMutation({
    mutationFn: (input: { id: number; approve: boolean }) =>
      api<FacultyLeave>(`/faculty/leaves/${input.id}/${input.approve ? 'approve' : 'reject'}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leaves'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: fail,
  })

  const imp = useCsvImport('/faculty/import', refresh)

  const noDepartments = departments.data?.length === 0

  function toggleSubject(subjectId: number) {
    if (!form) return
    const selected = form.subjectIds.includes(subjectId)
    setForm({
      ...form,
      subjectIds: selected
        ? form.subjectIds.filter((id) => id !== subjectId)
        : [...form.subjectIds, subjectId],
    })
  }

  return (
    <>
      <PageHeader
        title="Faculty"
        description="Teaching staff, weekly hour limits and the subjects each is qualified to take. The optimiser will not assign a subject to anyone not listed as qualified for it."
        actions={
          <>
            <TextInput
              placeholder="Search name, code or email"
              value={search}
              onChange={(e) => {
                setPage(0)
                setSearch(e.target.value)
              }}
              className="w-56"
            />
            <Select
              value={departmentId}
              onChange={(e) => {
                setPage(0)
                setDepartmentId(e.target.value)
              }}
              className="w-40"
            >
              <option value="">All departments</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.code}
                </option>
              ))}
            </Select>
            <CsvImportButtons
              imp={imp}
              onDownloadTemplate={() => downloadCsv('faculty-template.csv', CSV_TEMPLATE)}
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
              Add faculty
            </Button>
          </>
        }
      />

      {notice && (
        <div className="mb-4">
          <InfoNote tone="ok">{notice}</InfoNote>
        </div>
      )}
      {noDepartments && (
        <div className="mb-4">
          <InfoNote tone="warn">Add a department in Settings before adding faculty.</InfoNote>
        </div>
      )}

      <ImportResultPanel result={imp.result} error={imp.error} referenceLabel="Employee code" />

      {leaves.data && leaves.data.length > 0 && (
        <Card
          className="mb-4"
          title="Leave requests awaiting review"
          description="Approving a leave raises rescheduling proposals for affected practicals; nothing moves without a second approval."
          bodyClassName=""
        >
          <Table
            head={
              <tr>
                <Th>Faculty</Th>
                <Th>Dates</Th>
                <Th>Type</Th>
                <Th>Reason</Th>
                <Th />
              </tr>
            }
          >
            {leaves.data.map((leave) => (
              <tr key={leave.id} className="hover:bg-navy-50/50">
                <Td>
                  <div className="font-medium">{leave.facultyName}</div>
                  <div className="tabular text-[12px] text-navy-500">{leave.employeeCode}</div>
                </Td>
                <Td className="whitespace-nowrap">
                  {formatDate(leave.startDate)} – {formatDate(leave.endDate)}
                </Td>
                <Td>{leave.leaveType}</Td>
                <Td className="text-[12px] text-navy-600">{leave.reason ?? '—'}</Td>
                <Td>
                  <div className="flex gap-1">
                    <Button
                      size="sm"
                      variant="primary"
                      disabled={decide.isPending}
                      onClick={() => decide.mutate({ id: leave.id, approve: true })}
                    >
                      Approve
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={decide.isPending}
                      onClick={() => decide.mutate({ id: leave.id, approve: false })}
                    >
                      Reject
                    </Button>
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        </Card>
      )}

      <Card
        title="Teaching staff"
        description={list.data ? `${list.data.totalElements} member(s)` : undefined}
        bodyClassName=""
      >
        {list.isLoading && <Spinner label="Loading faculty" />}
        {list.error && <ErrorNote message={(list.error as Error).message} onRetry={() => list.refetch()} />}
        {list.data && list.data.content.length === 0 && (
          <EmptyState
            title="No faculty yet"
            hint="Add teaching staff and tick the subjects each can take — a subject with no qualified staff cannot be scheduled."
          />
        )}
        {list.data && list.data.content.length > 0 && (
          <>
            <Table
              head={
                <tr>
                  <Th>Code</Th>
                  <Th>Name</Th>
                  <Th>Designation</Th>
                  <Th>Dept</Th>
                  <Th className="text-right">Max hours</Th>
                  <Th>Qualified subjects</Th>
                  <Th>Status</Th>
                  <Th />
                </tr>
              }
            >
              {list.data.content.map((member) => (
                <tr key={member.id} className="hover:bg-navy-50/50">
                  <Td className="tabular font-medium">{member.employeeCode}</Td>
                  <Td>
                    <div>{member.name}</div>
                    <div className="text-[12px] text-navy-500">{member.email}</div>
                  </Td>
                  <Td className="text-[12px]">{member.designation}</Td>
                  <Td>{member.departmentCode}</Td>
                  <Td className="tabular text-right">{member.maxWeeklyHours}h</Td>
                  <Td>
                    {member.subjects.length === 0 ? (
                      <span className="text-[12px] text-danger-700">
                        None — cannot be scheduled
                      </span>
                    ) : (
                      <div className="flex flex-wrap gap-1">
                        {member.subjects.map((subject) => (
                          <Badge key={subject.id}>{subject.code}</Badge>
                        ))}
                      </div>
                    )}
                  </Td>
                  <Td>
                    <Badge tone={statusTone(member.status)}>{member.status}</Badge>
                  </Td>
                  <Td>
                    <div className="flex justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setEditId(member.id)
                          setForm({
                            employeeCode: member.employeeCode,
                            name: member.name,
                            email: member.email,
                            departmentId: String(member.departmentId),
                            designation: member.designation,
                            maxWeeklyHours: String(member.maxWeeklyHours),
                            status: member.status,
                            subjectIds: member.subjects.map((s) => s.id),
                          })
                          setError(null)
                        }}
                      >
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setAccount({
                            faculty: member,
                            username: member.employeeCode.toLowerCase(),
                            password: '',
                            role: 'FACULTY',
                          })
                          setError(null)
                        }}
                      >
                        Login
                      </Button>
                    </div>
                  </Td>
                </tr>
              ))}
            </Table>

            <div className="flex items-center justify-between border-t border-navy-100 px-3 py-2 text-[12px] text-navy-600">
              <span>
                Page {list.data.page + 1} of {list.data.totalPages}
              </span>
              <div className="flex gap-1">
                <Button size="sm" disabled={list.data.first} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button size="sm" disabled={list.data.last} onClick={() => setPage((p) => p + 1)}>
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>

      <Modal
        title={editId == null ? 'Add faculty member' : 'Edit faculty member'}
        description="Tick every subject this person is qualified to take."
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
              <Field label="Employee code" hint="e.g. CSE-F01">
                <TextInput
                  value={form.employeeCode}
                  required
                  onChange={(e) => setForm({ ...form, employeeCode: e.target.value })}
                />
              </Field>
              <Field label="Full name">
                <TextInput
                  value={form.name}
                  required
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Email">
                <TextInput
                  type="email"
                  value={form.email}
                  required
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                />
              </Field>
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
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Designation">
                <Select
                  value={form.designation}
                  onChange={(e) => setForm({ ...form, designation: e.target.value })}
                >
                  {DESIGNATIONS.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Max weekly hours" hint="Practical load limit">
                <TextInput
                  type="number"
                  min={1}
                  max={60}
                  value={form.maxWeeklyHours}
                  required
                  onChange={(e) => setForm({ ...form, maxWeeklyHours: e.target.value })}
                />
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

            <div>
              <span className="mb-1 block text-xs font-medium text-navy-600">
                Qualified subjects ({form.subjectIds.length} selected)
              </span>
              {subjects.data?.length === 0 ? (
                <p className="rounded border border-navy-200 bg-navy-50 px-3 py-2 text-[12px] text-navy-600">
                  No practical subjects exist yet. Add subjects first, then edit this person to tick
                  them.
                </p>
              ) : (
                <div className="max-h-52 overflow-y-auto rounded border border-navy-200 p-2">
                  {(subjects.data ?? []).map((subject) => (
                    <label
                      key={subject.id}
                      className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-[13px] hover:bg-navy-50"
                    >
                      <input
                        type="checkbox"
                        checked={form.subjectIds.includes(subject.id)}
                        onChange={() => toggleSubject(subject.id)}
                      />
                      <span className="tabular text-navy-500">{subject.subjectCode}</span>
                      <span className="text-navy-800">{subject.subjectName}</span>
                      <span className="ml-auto text-[11px] text-navy-400">
                        {subject.departmentCode} · sem {subject.semester}
                      </span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title="Create sign-in account"
        description={
          account ? `Gives ${account.faculty.name} access to their own timetable and workload.` : ''
        }
        open={account != null}
        error={error}
        submitting={createAccount.isPending}
        submitLabel="Create account"
        onClose={() => setAccount(null)}
        onSubmit={() =>
          account &&
          createAccount.mutate({
            id: account.faculty.id,
            username: account.username,
            password: account.password,
            role: account.role,
          })
        }
      >
        {account && (
          <div className="space-y-3">
            <Field label="Username" hint="Defaults to the employee code">
              <TextInput
                value={account.username}
                required
                onChange={(e) => setAccount({ ...account, username: e.target.value })}
              />
            </Field>
            <Field label="Password" hint="At least 8 characters">
              <TextInput
                type="text"
                value={account.password}
                required
                minLength={8}
                onChange={(e) => setAccount({ ...account, password: e.target.value })}
              />
            </Field>
            <Field label="Role" hint="HOD adds departmental coordination rights">
              <Select
                value={account.role}
                onChange={(e) => setAccount({ ...account, role: e.target.value })}
              >
                <option value="FACULTY">Faculty</option>
                <option value="HOD">Head of Department</option>
              </Select>
            </Field>
          </div>
        )}
      </Modal>
    </>
  )
}
