import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { IdName, LabStatus, Laboratory } from '../lib/types'
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
  StatCard,
  Table,
  Td,
  Th,
  TextInput,
  statusTone,
} from '../components/ui'

const empty = {
  labCode: '',
  labName: '',
  departmentId: '',
  capacity: '30',
  labType: '',
  location: '',
  status: 'ACTIVE',
}

const CSV_TEMPLATE =
  'lab_code,lab_name,department_code,capacity,lab_type,location,status\r\n' +
  'PL1,Programming Lab 1,CSE,30,Programming,Block A - 204,ACTIVE\r\n'

export default function Laboratories() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<typeof empty | null>(null)
  const [editId, setEditId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })

  const labTypes = useQuery({
    queryKey: ['labTypes'],
    queryFn: () => api<string[]>('/subjects/lab-types'),
  })

  const { data, isLoading, error: loadError, refetch } = useQuery({
    queryKey: ['labs'],
    queryFn: () => api<Laboratory[]>('/labs'),
  })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['labs'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['conflicts'] })
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
        capacity: Number(values.capacity),
      }
      return editId == null
        ? api<Laboratory>('/labs', { method: 'POST', body })
        : api<Laboratory>(`/labs/${editId}`, { method: 'PUT', body })
    },
    onSuccess: () => {
      setForm(null)
      setEditId(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const changeStatus = useMutation({
    mutationFn: (input: { id: number; status: LabStatus }) =>
      api<Laboratory>(`/labs/${input.id}/status?status=${input.status}`, { method: 'POST' }),
    onSuccess: refresh,
    onError: fail,
  })

  const imp = useCsvImport('/labs/import', refresh)

  const active = (data ?? []).filter((lab) => lab.status === 'ACTIVE')
  const seats = active.reduce((sum, lab) => sum + lab.capacity, 0)
  const types = new Set((data ?? []).map((lab) => lab.labType)).size
  const noDepartments = departments.data?.length === 0

  return (
    <>
      <PageHeader
        title="Laboratories"
        description="Capacity and type decide which batches can be hosted where. Marking one for maintenance does not silently move practicals — affected sessions are raised as conflicts to reschedule."
        actions={
          <>
            <CsvImportButtons
              imp={imp}
              onDownloadTemplate={() => downloadCsv('laboratories-template.csv', CSV_TEMPLATE)}
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
              Add laboratory
            </Button>
          </>
        }
      />

      {noDepartments && (
        <div className="mb-4">
          <InfoNote tone="warn">
            Add a department in Settings before adding laboratories.
          </InfoNote>
        </div>
      )}

      <ImportResultPanel result={imp.result} error={imp.error} referenceLabel="Lab code" />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Laboratories" value={data?.length ?? 0} />
        <StatCard label="Active" value={active.length} tone={active.length > 0 ? 'ok' : 'neutral'} />
        <StatCard label="Total seats" value={seats} sub="Across active labs" />
        <StatCard label="Lab types" value={types} />
      </div>

      <Card className="mt-4" bodyClassName="">
        {isLoading && <Spinner label="Loading laboratories" />}
        {loadError && <ErrorNote message={(loadError as Error).message} onRetry={() => refetch()} />}
        {data && data.length === 0 && (
          <EmptyState
            title="No laboratories yet"
            hint="Add each laboratory with its seating capacity and type. The type must match what your subjects ask for."
          />
        )}
        {data && data.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Code</Th>
                <Th>Laboratory</Th>
                <Th>Type</Th>
                <Th>Location</Th>
                <Th>Department</Th>
                <Th className="text-right">Capacity</Th>
                <Th>Status</Th>
                <Th />
              </tr>
            }
          >
            {data.map((lab) => (
              <tr key={lab.id} className="hover:bg-navy-50/50">
                <Td className="tabular font-medium">{lab.labCode}</Td>
                <Td>{lab.labName}</Td>
                <Td>
                  <Badge>{lab.labType}</Badge>
                </Td>
                <Td className="text-[12px] text-navy-600">
                  {lab.location ? (
                    <span className="inline-flex items-center gap-1">
                      <Icon name="location" className="h-3.5 w-3.5 text-navy-400" />
                      {lab.location}
                    </span>
                  ) : (
                    '—'
                  )}
                </Td>
                <Td>{lab.departmentCode}</Td>
                <Td className="tabular text-right">{lab.capacity}</Td>
                <Td>
                  <Badge tone={statusTone(lab.status)}>{lab.status}</Badge>
                </Td>
                <Td>
                  <div className="flex items-center justify-end gap-1">
                    <Select
                      value={lab.status}
                      disabled={changeStatus.isPending}
                      onChange={(e) =>
                        changeStatus.mutate({ id: lab.id, status: e.target.value as LabStatus })
                      }
                      className="w-36"
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="INACTIVE">Inactive</option>
                    </Select>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setEditId(lab.id)
                        setForm({
                          labCode: lab.labCode,
                          labName: lab.labName,
                          departmentId: String(lab.departmentId),
                          capacity: String(lab.capacity),
                          labType: lab.labType,
                          location: lab.location ?? '',
                          status: lab.status,
                        })
                        setError(null)
                      }}
                    >
                      Edit
                    </Button>
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        title={editId == null ? 'Add laboratory' : 'Edit laboratory'}
        description="Type must match the required laboratory type on your subjects, exactly."
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
              <Field label="Lab code" hint="e.g. PL1">
                <TextInput
                  value={form.labCode}
                  required
                  onChange={(e) => setForm({ ...form, labCode: e.target.value })}
                />
              </Field>
              <Field label="Lab name">
                <TextInput
                  value={form.labName}
                  required
                  onChange={(e) => setForm({ ...form, labName: e.target.value })}
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
              <Field label="Capacity" hint="Seats available">
                <TextInput
                  type="number"
                  min={1}
                  value={form.capacity}
                  required
                  onChange={(e) => setForm({ ...form, capacity: e.target.value })}
                />
              </Field>
              <Field label="Status">
                <Select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                >
                  <option value="ACTIVE">Active</option>
                  <option value="MAINTENANCE">Maintenance</option>
                  <option value="INACTIVE">Inactive</option>
                </Select>
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Lab type" hint="e.g. Programming, Networking">
                <TextInput
                  value={form.labType}
                  required
                  list="existing-lab-types"
                  onChange={(e) => setForm({ ...form, labType: e.target.value })}
                />
                <datalist id="existing-lab-types">
                  {(labTypes.data ?? []).map((type) => (
                    <option key={type} value={type} />
                  ))}
                </datalist>
              </Field>
              <Field label="Location" hint="Optional">
                <TextInput
                  value={form.location}
                  placeholder="e.g. Block A - 204"
                  onChange={(e) => setForm({ ...form, location: e.target.value })}
                />
              </Field>
            </div>
          </div>
        )}
      </Modal>
    </>
  )
}
