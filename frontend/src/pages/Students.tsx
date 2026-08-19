import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, getToken } from '../lib/api'
import type { IdName, Page, Student } from '../lib/types'
import Modal, { ConfirmDialog } from '../components/Modal'
import StudentParser from '../components/StudentParser'
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
  Table,
  Td,
  Th,
  TextInput,
  statusTone,
} from '../components/ui'

interface ImportResult {
  totalRows: number
  imported: number
  updated: number
  skipped: number
  errors: { rowNumber: number; rollNumber?: string; message: string }[]
}

const empty = {
  rollNumber: '',
  name: '',
  email: '',
  departmentId: '',
  semester: '5',
  studyYear: '3',
  division: 'A',
  status: 'ACTIVE',
}

const CSV_TEMPLATE =
  'roll_number,name,email,department_code,semester,year,division\r\n' +
  'CS5A001,Example Student,cs5a001@college.edu,CSE,5,3,A\r\n'

export default function Students() {
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)

  const [search, setSearch] = useState('')
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [semesterFilter, setSemesterFilter] = useState('')
  const [divisionFilter, setDivisionFilter] = useState('')
  const [page, setPage] = useState(0)

  const [form, setForm] = useState<typeof empty | null>(null)
  const [editId, setEditId] = useState<number | null>(null)
  const [account, setAccount] = useState<{ student: Student; username: string; password: string } | null>(null)
  const [toDelete, setToDelete] = useState<Student | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [importError, setImportError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })

  const params = new URLSearchParams({ page: String(page), size: '25' })
  if (search) params.set('search', search)
  if (departmentFilter) params.set('departmentId', departmentFilter)
  if (semesterFilter) params.set('semester', semesterFilter)
  if (divisionFilter) params.set('division', divisionFilter)

  const { data, isLoading, error: loadError, refetch } = useQuery({
    queryKey: ['students', params.toString()],
    queryFn: () => api<Page<Student>>(`/students?${params.toString()}`),
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['students'] })
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
        semester: Number(values.semester),
        studyYear: Number(values.studyYear),
      }
      return editId == null
        ? api<Student>('/students', { method: 'POST', body })
        : api<Student>(`/students/${editId}`, { method: 'PUT', body })
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
    mutationFn: (id: number) => api(`/students/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setToDelete(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const createAccount = useMutation({
    mutationFn: (input: { id: number; username: string; password: string }) =>
      api<{ username: string }>(`/students/${input.id}/account`, {
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

  async function upload(file: File) {
    setUploading(true)
    setImportError(null)
    setImportResult(null)
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await fetch('/api/students/import?updateExisting=true', {
        method: 'POST',
        headers: { Authorization: `Bearer ${getToken()}` },
        body,
      })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload?.message ?? 'Import failed.')
      setImportResult(payload as ImportResult)
      refresh()
    } catch (err) {
      setImportError((err as Error).message)
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  function downloadTemplate() {
    const blob = new Blob(['﻿' + CSV_TEMPLATE], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'students-template.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  const noDepartments = departments.data?.length === 0

  return (
    <>
      <PageHeader
        title="Students"
        description="Enrolment records used to build practical batches. Roll numbers and email addresses must be unique."
        actions={
          <>
            <input
              ref={fileInput}
              type="file"
              accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) upload(file)
              }}
            />
            <Button onClick={downloadTemplate}>
              <Icon name="download" className="h-3.5 w-3.5" />
              CSV template
            </Button>
            <Button onClick={() => fileInput.current?.click()} disabled={uploading || noDepartments}>
              <Icon name="download" className="h-3.5 w-3.5" />
              {uploading ? 'Importing…' : 'Import CSV / Excel'}
            </Button>
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
              Add student
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
          <InfoNote tone="warn">Add a department in Settings before adding students.</InfoNote>
        </div>
      )}

      <details className="mb-4">
        <summary className="inline-flex cursor-pointer items-center gap-1.5 text-[12px] font-medium text-navy-600 select-none">
          <Icon name="plus" className="h-3.5 w-3.5" />
          Paste a roster to extract students
        </summary>
        <div className="mt-2">
          <StudentParser />
        </div>
      </details>

      {(importResult || importError) && (
        <Card className="mb-4" title="Import result">
          {importError && <InfoNote tone="danger">{importError}</InfoNote>}
          {importResult && (
            <div className="space-y-2">
              <InfoNote tone={importResult.errors.length > 0 ? 'warn' : 'ok'}>
                {importResult.totalRows} row(s) read · {importResult.imported} added ·{' '}
                {importResult.updated} updated · {importResult.skipped} skipped
              </InfoNote>
              {importResult.errors.length > 0 && (
                <div className="max-h-48 overflow-y-auto rounded border border-navy-100">
                  <Table
                    head={
                      <tr>
                        <Th>Row</Th>
                        <Th>Roll number</Th>
                        <Th>Problem</Th>
                      </tr>
                    }
                  >
                    {importResult.errors.map((row) => (
                      <tr key={`${row.rowNumber}-${row.rollNumber}`}>
                        <Td className="tabular">{row.rowNumber}</Td>
                        <Td className="tabular">{row.rollNumber ?? '—'}</Td>
                        <Td className="text-[12px] text-danger-700">{row.message}</Td>
                      </tr>
                    ))}
                  </Table>
                </div>
              )}
            </div>
          )}
        </Card>
      )}

      <Card
        title="Enrolment"
        description={data ? `${data.totalElements} student(s)` : undefined}
        actions={
          <>
            <TextInput
              placeholder="Search name, roll number or email"
              value={search}
              onChange={(e) => {
                setPage(0)
                setSearch(e.target.value)
              }}
              className="w-56"
            />
            <Select
              value={departmentFilter}
              onChange={(e) => {
                setPage(0)
                setDepartmentFilter(e.target.value)
              }}
              className="w-36"
            >
              <option value="">All depts</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.code}
                </option>
              ))}
            </Select>
            <Select
              value={semesterFilter}
              onChange={(e) => {
                setPage(0)
                setSemesterFilter(e.target.value)
              }}
              className="w-32"
            >
              <option value="">All sems</option>
              {Array.from({ length: 8 }, (_, i) => i + 1).map((s) => (
                <option key={s} value={s}>
                  Sem {s}
                </option>
              ))}
            </Select>
            <TextInput
              placeholder="Division"
              value={divisionFilter}
              onChange={(e) => {
                setPage(0)
                setDivisionFilter(e.target.value.toUpperCase())
              }}
              className="w-24"
            />
          </>
        }
        bodyClassName=""
      >
        {isLoading && <Spinner label="Loading students" />}
        {loadError && <ErrorNote message={(loadError as Error).message} onRetry={() => refetch()} />}
        {data && data.content.length === 0 && (
          <EmptyState
            title="No students yet"
            hint="Add students one at a time, or import a roll list as CSV — download the template to see the expected columns."
          />
        )}
        {data && data.content.length > 0 && (
          <>
            <Table
              head={
                <tr>
                  <Th>Roll number</Th>
                  <Th>Name</Th>
                  <Th>Email</Th>
                  <Th>Dept</Th>
                  <Th className="text-right">Sem</Th>
                  <Th className="text-right">Year</Th>
                  <Th>Division</Th>
                  <Th>Status</Th>
                  <Th />
                </tr>
              }
            >
              {data.content.map((student) => (
                <tr key={student.id} className="hover:bg-navy-50/50">
                  <Td className="tabular font-medium">{student.rollNumber}</Td>
                  <Td>{student.name}</Td>
                  <Td className="text-[12px] text-navy-500">{student.email}</Td>
                  <Td>{student.departmentCode}</Td>
                  <Td className="tabular text-right">{student.semester}</Td>
                  <Td className="tabular text-right">{student.studyYear}</Td>
                  <Td>{student.division}</Td>
                  <Td>
                    <Badge tone={statusTone(student.status)}>{student.status}</Badge>
                  </Td>
                  <Td>
                    <div className="flex justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setEditId(student.id)
                          setForm({
                            rollNumber: student.rollNumber,
                            name: student.name,
                            email: student.email,
                            departmentId: String(student.departmentId),
                            semester: String(student.semester),
                            studyYear: String(student.studyYear),
                            division: student.division,
                            status: student.status,
                          })
                          setError(null)
                        }}
                      >
                        Edit
                      </Button>
                      {!student.hasLogin && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            setAccount({
                              student,
                              username: student.rollNumber.toLowerCase(),
                              password: '',
                            })
                            setError(null)
                          }}
                        >
                          Login
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => setToDelete(student)}>
                        Delete
                      </Button>
                    </div>
                  </Td>
                </tr>
              ))}
            </Table>

            <div className="flex items-center justify-between border-t border-navy-100 px-3 py-2 text-[12px] text-navy-600">
              <span>
                Page {data.page + 1} of {data.totalPages}
              </span>
              <div className="flex gap-1">
                <Button size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>

      <Modal
        title={editId == null ? 'Add student' : 'Edit student'}
        description="Division determines how the cohort is split into practical batches."
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
              <Field label="Roll number" hint="Must be unique">
                <TextInput
                  value={form.rollNumber}
                  required
                  onChange={(e) => setForm({ ...form, rollNumber: e.target.value })}
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

            <div className="grid gap-3 sm:grid-cols-4">
              <Field label="Semester">
                <Select
                  value={form.semester}
                  onChange={(e) => setForm({ ...form, semester: e.target.value })}
                >
                  {Array.from({ length: 8 }, (_, i) => i + 1).map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Year">
                <Select
                  value={form.studyYear}
                  onChange={(e) => setForm({ ...form, studyYear: e.target.value })}
                >
                  {Array.from({ length: 6 }, (_, i) => i + 1).map((y) => (
                    <option key={y} value={y}>
                      {y}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Division">
                <TextInput
                  value={form.division}
                  required
                  maxLength={8}
                  onChange={(e) => setForm({ ...form, division: e.target.value.toUpperCase() })}
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
          </div>
        )}
      </Modal>

      <Modal
        title="Create sign-in account"
        description={account ? `Lets ${account.student.name} view their own batch and timetable.` : ''}
        open={account != null}
        error={error}
        submitting={createAccount.isPending}
        submitLabel="Create account"
        onClose={() => setAccount(null)}
        onSubmit={() =>
          account &&
          createAccount.mutate({
            id: account.student.id,
            username: account.username,
            password: account.password,
          })
        }
      >
        {account && (
          <div className="space-y-3">
            <Field label="Username" hint="Defaults to the roll number">
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
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={toDelete != null}
        title="Delete student"
        message={`Permanently delete ${toDelete?.name} (${toDelete?.rollNumber})? Their batch memberships go with them.`}
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
