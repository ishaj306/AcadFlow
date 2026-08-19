import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, formatDate, hhmm, titleCase } from '../lib/api'
import type { AcademicTerm, IdName, TimeSlot, WorkingDay } from '../lib/types'
import Modal, { ConfirmDialog } from '../components/Modal'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  InfoNote,
  PageHeader,
  Select,
  Spinner,
  Table,
  Td,
  Th,
  TextInput,
} from '../components/ui'

interface Holiday {
  id: number
  holidayDate: string
  name: string
  description?: string
  departmentId?: number
  departmentName?: string
}

const emptyDepartment = { code: '', name: '' }
const emptyTerm = { academicYear: '', semester: '5', startDate: '', endDate: '', makeCurrent: true }
const emptySlot = {
  label: '',
  startTime: '09:00',
  endTime: '10:00',
  slotOrder: '1',
  slotType: 'TEACHING',
  active: true,
}
const emptyHoliday = { holidayDate: '', name: '', description: '', departmentId: '' }

export default function Settings() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const [departmentForm, setDepartmentForm] = useState<typeof emptyDepartment | null>(null)
  const [departmentId, setDepartmentId] = useState<number | null>(null)
  const [deleteDept, setDeleteDept] = useState<IdName | null>(null)

  const [termForm, setTermForm] = useState<typeof emptyTerm | null>(null)

  const [slotForm, setSlotForm] = useState<typeof emptySlot | null>(null)
  const [slotId, setSlotId] = useState<number | null>(null)
  const [deleteSlot, setDeleteSlot] = useState<TimeSlot | null>(null)

  const [holidayForm, setHolidayForm] = useState<typeof emptyHoliday | null>(null)

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<IdName[]>('/config/departments'),
  })
  const terms = useQuery({ queryKey: ['terms'], queryFn: () => api<AcademicTerm[]>('/config/terms') })
  const slots = useQuery({
    queryKey: ['timeslots'],
    queryFn: () => api<TimeSlot[]>('/config/time-slots'),
  })
  const days = useQuery({
    queryKey: ['workingdays'],
    queryFn: () => api<WorkingDay[]>('/config/working-days'),
  })
  const holidays = useQuery({
    queryKey: ['holidays'],
    queryFn: () => api<Holiday[]>('/config/holidays'),
  })

  function refresh(...keys: string[]) {
    keys.forEach((key) => queryClient.invalidateQueries({ queryKey: [key] }))
    queryClient.invalidateQueries({ queryKey: ['setup'] })
  }

  function fail(err: unknown) {
    setError(err instanceof ApiError ? err.message : 'The request failed.')
  }

  // ------------------------------------------------------------- departments

  const saveDepartment = useMutation({
    mutationFn: (form: typeof emptyDepartment) =>
      departmentId == null
        ? api<IdName>('/config/departments', { method: 'POST', body: form })
        : api<IdName>(`/config/departments/${departmentId}`, { method: 'PUT', body: form }),
    onSuccess: () => {
      setDepartmentForm(null)
      setDepartmentId(null)
      setError(null)
      refresh('departments')
    },
    onError: fail,
  })

  const removeDepartment = useMutation({
    mutationFn: (id: number) => api(`/config/departments/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDeleteDept(null)
      setError(null)
      refresh('departments')
    },
    onError: fail,
  })

  // ------------------------------------------------------------------- terms

  const saveTerm = useMutation({
    mutationFn: (form: typeof emptyTerm) =>
      api<AcademicTerm>('/config/terms', {
        method: 'POST',
        body: { ...form, semester: Number(form.semester) },
      }),
    onSuccess: () => {
      setTermForm(null)
      setError(null)
      refresh('terms', 'term')
    },
    onError: fail,
  })

  const setCurrentTerm = useMutation({
    mutationFn: (id: number) => api<AcademicTerm>(`/config/terms/${id}/current`, { method: 'POST' }),
    onSuccess: () => refresh('terms', 'term'),
    onError: fail,
  })

  // -------------------------------------------------------------- time slots

  const saveSlot = useMutation({
    mutationFn: (form: typeof emptySlot) => {
      const body = { ...form, slotOrder: Number(form.slotOrder) }
      return slotId == null
        ? api<TimeSlot>('/config/time-slots', { method: 'POST', body })
        : api<TimeSlot>(`/config/time-slots/${slotId}`, { method: 'PUT', body })
    },
    onSuccess: () => {
      setSlotForm(null)
      setSlotId(null)
      setError(null)
      refresh('timeslots')
    },
    onError: fail,
  })

  const removeSlot = useMutation({
    mutationFn: (id: number) => api(`/config/time-slots/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDeleteSlot(null)
      setError(null)
      refresh('timeslots')
    },
    onError: fail,
  })

  const toggleDay = useMutation({
    mutationFn: (updated: WorkingDay[]) =>
      api<WorkingDay[]>('/config/working-days', { method: 'PUT', body: updated }),
    onSuccess: () => refresh('workingdays'),
    onError: fail,
  })

  // ---------------------------------------------------------------- holidays

  const saveHoliday = useMutation({
    mutationFn: (form: typeof emptyHoliday) =>
      api<Holiday>('/config/holidays', {
        method: 'POST',
        body: { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null },
      }),
    onSuccess: () => {
      setHolidayForm(null)
      setError(null)
      refresh('holidays')
    },
    onError: fail,
  })

  const removeHoliday = useMutation({
    mutationFn: (id: number) => api(`/config/holidays/${id}`, { method: 'DELETE' }),
    onSuccess: () => refresh('holidays'),
    onError: fail,
  })

  /** Next free slot order, so adding periods in sequence needs no thought. */
  const nextSlotOrder = String((slots.data ?? []).reduce((max, s) => Math.max(max, s.slotOrder), 0) + 1)

  return (
    <>
      <PageHeader
        title="Settings"
        description="Institution configuration: departments, academic terms, the periods of the college day, working days and holidays. All of it is data the scheduling engine reads at generation time."
      />

      <div className="grid gap-4 xl:grid-cols-2">
        {/* ------------------------------------------------- departments */}
        <Card
          title="Departments"
          description="Everything else belongs to a department"
          bodyClassName=""
          actions={
            <Button
              size="sm"
              variant="primary"
              onClick={() => {
                setDepartmentId(null)
                setDepartmentForm(emptyDepartment)
                setError(null)
              }}
            >
              <Icon name="plus" className="h-3.5 w-3.5" />
              Add
            </Button>
          }
        >
          {departments.isLoading && <Spinner />}
          {departments.data?.length === 0 && (
            <EmptyState
              title="No departments yet"
              hint="Add your first department to begin — for example CSE, Computer Science and Engineering."
            />
          )}
          {!!departments.data?.length && (
            <Table
              head={
                <tr>
                  <Th>Code</Th>
                  <Th>Name</Th>
                  <Th />
                </tr>
              }
            >
              {departments.data.map((department) => (
                <tr key={department.id} className="hover:bg-navy-50/50">
                  <Td className="font-medium">{department.code}</Td>
                  <Td>{department.name}</Td>
                  <Td>
                    <div className="flex justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setDepartmentId(department.id)
                          setDepartmentForm({ code: department.code, name: department.name })
                          setError(null)
                        }}
                      >
                        Edit
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setDeleteDept(department)}>
                        Delete
                      </Button>
                    </div>
                  </Td>
                </tr>
              ))}
            </Table>
          )}
        </Card>

        {/* ------------------------------------------------------- terms */}
        <Card
          title="Academic terms"
          description="Scheduling always runs against the current term"
          bodyClassName=""
          actions={
            <Button
              size="sm"
              variant="primary"
              onClick={() => {
                setTermForm(emptyTerm)
                setError(null)
              }}
            >
              <Icon name="plus" className="h-3.5 w-3.5" />
              Add
            </Button>
          }
        >
          {terms.isLoading && <Spinner />}
          {terms.data?.length === 0 && (
            <EmptyState
              title="No academic term yet"
              hint="Add the term you are scheduling — the first one becomes current automatically."
            />
          )}
          {!!terms.data?.length && (
            <Table
              head={
                <tr>
                  <Th>Term</Th>
                  <Th>Starts</Th>
                  <Th>Ends</Th>
                  <Th />
                </tr>
              }
            >
              {terms.data.map((term) => (
                <tr key={term.id} className="hover:bg-navy-50/50">
                  <Td className="font-medium">
                    {term.label}
                    {term.current && (
                      <span className="ml-2">
                        <Badge tone="ok">Current</Badge>
                      </span>
                    )}
                  </Td>
                  <Td className="whitespace-nowrap">{formatDate(term.startDate)}</Td>
                  <Td className="whitespace-nowrap">{formatDate(term.endDate)}</Td>
                  <Td>
                    {!term.current && (
                      <Button
                        size="sm"
                        disabled={setCurrentTerm.isPending}
                        onClick={() => setCurrentTerm.mutate(term.id)}
                      >
                        Set current
                      </Button>
                    )}
                  </Td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>

      {/* ---------------------------------------------------- time slots */}
      <Card
        className="mt-4"
        title="Periods of the college day"
        description="Practicals may only occupy teaching periods, and never run across a break or lunch."
        bodyClassName=""
        actions={
          <Button
            size="sm"
            variant="primary"
            onClick={() => {
              setSlotId(null)
              setSlotForm({ ...emptySlot, slotOrder: nextSlotOrder })
              setError(null)
            }}
          >
            <Icon name="plus" className="h-3.5 w-3.5" />
            Add period
          </Button>
        }
      >
        {slots.isLoading && <Spinner />}
        {slots.data?.length === 0 && (
          <EmptyState
            title="No periods configured"
            hint="Add the periods of your college day, in order — for example 09:00–10:00, then 10:00–11:00, with a lunch period in the middle."
          />
        )}
        {!!slots.data?.length && (
          <Table
            head={
              <tr>
                <Th className="text-right">#</Th>
                <Th>Label</Th>
                <Th>Start</Th>
                <Th>End</Th>
                <Th className="text-right">Duration</Th>
                <Th>Type</Th>
                <Th>Active</Th>
                <Th />
              </tr>
            }
          >
            {slots.data.map((slot) => (
              <tr key={slot.id} className="hover:bg-navy-50/50">
                <Td className="tabular text-right text-navy-500">{slot.slotOrder}</Td>
                <Td className="font-medium">{slot.label}</Td>
                <Td className="tabular">{hhmm(slot.startTime)}</Td>
                <Td className="tabular">{hhmm(slot.endTime)}</Td>
                <Td className="tabular text-right">{slot.durationMinutes} min</Td>
                <Td>
                  <Badge tone={slot.slotType === 'TEACHING' ? 'ok' : 'neutral'}>
                    {titleCase(slot.slotType)}
                  </Badge>
                </Td>
                <Td>
                  <Badge tone={slot.active ? 'ok' : 'neutral'}>{slot.active ? 'Yes' : 'No'}</Badge>
                </Td>
                <Td>
                  <div className="flex justify-end gap-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setSlotId(slot.id)
                        setSlotForm({
                          label: slot.label,
                          startTime: hhmm(slot.startTime),
                          endTime: hhmm(slot.endTime),
                          slotOrder: String(slot.slotOrder),
                          slotType: slot.slotType,
                          active: slot.active,
                        })
                        setError(null)
                      }}
                    >
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setDeleteSlot(slot)}>
                      Delete
                    </Button>
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      {/* -------------------------------------------------- working days */}
      <Card
        className="mt-4"
        title="Working days"
        description="Days practicals may be scheduled on. Click a day to include or exclude it."
      >
        {days.isLoading && <Spinner />}
        {days.data && (
          <>
            <div className="flex flex-wrap gap-2">
              {days.data.map((day) => (
                <button
                  key={day.id}
                  onClick={() =>
                    toggleDay.mutate(
                      days.data.map((d) => (d.id === day.id ? { ...d, active: !d.active } : d)),
                    )
                  }
                  disabled={toggleDay.isPending}
                  className={`rounded border px-3 py-1.5 text-[13px] font-medium transition-colors ${
                    day.active
                      ? 'border-ok-600/30 bg-ok-50 text-ok-700'
                      : 'border-navy-200 bg-white text-navy-400'
                  }`}
                >
                  {titleCase(day.dayOfWeek)}
                </button>
              ))}
            </div>
            {days.data.every((day) => !day.active) && (
              <div className="mt-3">
                <InfoNote tone="warn">
                  No working day is active, so nothing can be scheduled. Select the days your
                  college runs practicals.
                </InfoNote>
              </div>
            )}
          </>
        )}
      </Card>

      {/* ------------------------------------------------------ holidays */}
      <Card
        className="mt-4"
        title="Holidays"
        description="No practical runs on a declared holiday"
        bodyClassName=""
        actions={
          <Button
            size="sm"
            onClick={() => {
              setHolidayForm(emptyHoliday)
              setError(null)
            }}
          >
            <Icon name="plus" className="h-3.5 w-3.5" />
            Add holiday
          </Button>
        }
      >
        {holidays.isLoading && <Spinner />}
        {holidays.data?.length === 0 && (
          <EmptyState title="No holidays declared" hint="Optional — add them whenever you like." />
        )}
        {!!holidays.data?.length && (
          <Table
            head={
              <tr>
                <Th>Date</Th>
                <Th>Day</Th>
                <Th>Name</Th>
                <Th>Applies to</Th>
                <Th />
              </tr>
            }
          >
            {holidays.data.map((holiday) => (
              <tr key={holiday.id} className="hover:bg-navy-50/50">
                <Td className="whitespace-nowrap">{formatDate(holiday.holidayDate)}</Td>
                <Td>
                  {new Date(holiday.holidayDate).toLocaleDateString(undefined, { weekday: 'long' })}
                </Td>
                <Td className="font-medium">{holiday.name}</Td>
                <Td>{holiday.departmentName ?? 'All departments'}</Td>
                <Td>
                  <div className="flex justify-end">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => removeHoliday.mutate(holiday.id)}
                    >
                      Remove
                    </Button>
                  </div>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      {/* --------------------------------------------------------- forms */}

      <Modal
        title={departmentId == null ? 'Add department' : 'Edit department'}
        open={departmentForm != null}
        error={error}
        submitting={saveDepartment.isPending}
        onClose={() => {
          setDepartmentForm(null)
          setDepartmentId(null)
        }}
        onSubmit={() => departmentForm && saveDepartment.mutate(departmentForm)}
      >
        {departmentForm && (
          <div className="space-y-3">
            <Field label="Code" hint="Short identifier, e.g. CSE">
              <TextInput
                value={departmentForm.code}
                required
                maxLength={16}
                onChange={(e) => setDepartmentForm({ ...departmentForm, code: e.target.value })}
              />
            </Field>
            <Field label="Name">
              <TextInput
                value={departmentForm.name}
                required
                onChange={(e) => setDepartmentForm({ ...departmentForm, name: e.target.value })}
              />
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        title="Add academic term"
        description="The term scheduling runs against."
        open={termForm != null}
        error={error}
        submitting={saveTerm.isPending}
        onClose={() => setTermForm(null)}
        onSubmit={() => termForm && saveTerm.mutate(termForm)}
      >
        {termForm && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Academic year" hint="e.g. 2026-27">
                <TextInput
                  value={termForm.academicYear}
                  required
                  onChange={(e) => setTermForm({ ...termForm, academicYear: e.target.value })}
                />
              </Field>
              <Field label="Semester">
                <Select
                  value={termForm.semester}
                  onChange={(e) => setTermForm({ ...termForm, semester: e.target.value })}
                >
                  {Array.from({ length: 8 }, (_, i) => i + 1).map((s) => (
                    <option key={s} value={s}>
                      Semester {s}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Start date">
                <TextInput
                  type="date"
                  value={termForm.startDate}
                  required
                  onChange={(e) => setTermForm({ ...termForm, startDate: e.target.value })}
                />
              </Field>
              <Field label="End date">
                <TextInput
                  type="date"
                  value={termForm.endDate}
                  required
                  onChange={(e) => setTermForm({ ...termForm, endDate: e.target.value })}
                />
              </Field>
            </div>
            <label className="flex items-center gap-2 text-[13px] text-navy-700">
              <input
                type="checkbox"
                checked={termForm.makeCurrent}
                onChange={(e) => setTermForm({ ...termForm, makeCurrent: e.target.checked })}
              />
              Make this the current term
            </label>
          </div>
        )}
      </Modal>

      <Modal
        title={slotId == null ? 'Add period' : 'Edit period'}
        description="Periods must not overlap. Mark breaks and lunch so practicals never run through them."
        open={slotForm != null}
        error={error}
        submitting={saveSlot.isPending}
        onClose={() => {
          setSlotForm(null)
          setSlotId(null)
        }}
        onSubmit={() => slotForm && saveSlot.mutate(slotForm)}
      >
        {slotForm && (
          <div className="space-y-3">
            <Field label="Label" hint="Shown in the timetable, e.g. 09:00 - 10:00">
              <TextInput
                value={slotForm.label}
                required
                onChange={(e) => setSlotForm({ ...slotForm, label: e.target.value })}
              />
            </Field>
            <div className="grid grid-cols-3 gap-3">
              <Field label="Start">
                <TextInput
                  type="time"
                  value={slotForm.startTime}
                  required
                  onChange={(e) => {
                    const startTime = e.target.value
                    setSlotForm({
                      ...slotForm,
                      startTime,
                      label: slotForm.label || `${startTime} - ${slotForm.endTime}`,
                    })
                  }}
                />
              </Field>
              <Field label="End">
                <TextInput
                  type="time"
                  value={slotForm.endTime}
                  required
                  onChange={(e) => setSlotForm({ ...slotForm, endTime: e.target.value })}
                />
              </Field>
              <Field label="Order" hint="Position in the day">
                <TextInput
                  type="number"
                  min={1}
                  value={slotForm.slotOrder}
                  required
                  onChange={(e) => setSlotForm({ ...slotForm, slotOrder: e.target.value })}
                />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Type">
                <Select
                  value={slotForm.slotType}
                  onChange={(e) => setSlotForm({ ...slotForm, slotType: e.target.value })}
                >
                  <option value="TEACHING">Teaching</option>
                  <option value="BREAK">Break</option>
                  <option value="LUNCH">Lunch</option>
                  <option value="RESTRICTED">Restricted</option>
                  <option value="SPECIAL">Special</option>
                </Select>
              </Field>
              <Field label="Active">
                <Select
                  value={slotForm.active ? 'yes' : 'no'}
                  onChange={(e) => setSlotForm({ ...slotForm, active: e.target.value === 'yes' })}
                >
                  <option value="yes">Yes</option>
                  <option value="no">No</option>
                </Select>
              </Field>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title="Add holiday"
        open={holidayForm != null}
        error={error}
        submitting={saveHoliday.isPending}
        onClose={() => setHolidayForm(null)}
        onSubmit={() => holidayForm && saveHoliday.mutate(holidayForm)}
      >
        {holidayForm && (
          <div className="space-y-3">
            <Field label="Date">
              <TextInput
                type="date"
                value={holidayForm.holidayDate}
                required
                onChange={(e) => setHolidayForm({ ...holidayForm, holidayDate: e.target.value })}
              />
            </Field>
            <Field label="Name">
              <TextInput
                value={holidayForm.name}
                required
                onChange={(e) => setHolidayForm({ ...holidayForm, name: e.target.value })}
              />
            </Field>
            <Field label="Applies to">
              <Select
                value={holidayForm.departmentId}
                onChange={(e) => setHolidayForm({ ...holidayForm, departmentId: e.target.value })}
              >
                <option value="">All departments</option>
                {(departments.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={deleteDept != null}
        title="Delete department"
        message={`Delete ${deleteDept?.name}? This is only possible while nothing belongs to it.`}
        busy={removeDepartment.isPending}
        error={error}
        onConfirm={() => deleteDept && removeDepartment.mutate(deleteDept.id)}
        onClose={() => {
          setDeleteDept(null)
          setError(null)
        }}
      />

      <ConfirmDialog
        open={deleteSlot != null}
        title="Delete period"
        message={`Delete the ${deleteSlot?.label} period? A period already used by a timetable cannot be removed.`}
        busy={removeSlot.isPending}
        error={error}
        onConfirm={() => deleteSlot && removeSlot.mutate(deleteSlot.id)}
        onClose={() => {
          setDeleteSlot(null)
          setError(null)
        }}
      />
    </>
  )
}
