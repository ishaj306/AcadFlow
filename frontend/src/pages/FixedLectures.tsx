import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, hhmm, titleCase } from '../lib/api'
import type {
  AcademicTerm,
  DayOfWeek,
  Faculty,
  FixedCommitment,
  Laboratory,
  TimeSlot,
  WorkingDay,
} from '../lib/types'
import Modal, { ConfirmDialog } from '../components/Modal'
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

const ALL_DAYS: DayOfWeek[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
]

const empty = {
  title: '',
  commitmentType: 'LECTURE',
  facultyId: '',
  labId: '',
  dayOfWeek: 'MONDAY',
  startSlotId: '',
  endSlotId: '',
  allTerms: false,
  note: '',
}

export default function FixedLectures() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<typeof empty | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [removeId, setRemoveId] = useState<number | null>(null)

  const list = useQuery({
    queryKey: ['fixed-commitments'],
    queryFn: () => api<FixedCommitment[]>('/fixed-commitments'),
  })
  const faculty = useQuery({
    queryKey: ['faculty', 'active'],
    queryFn: () => api<Faculty[]>('/faculty/active'),
  })
  const labs = useQuery({
    queryKey: ['labs'],
    queryFn: () => api<Laboratory[]>('/labs'),
  })
  const slots = useQuery({
    queryKey: ['time-slots'],
    queryFn: () => api<TimeSlot[]>('/config/time-slots'),
  })
  const days = useQuery({
    queryKey: ['working-days'],
    queryFn: () => api<WorkingDay[]>('/config/working-days'),
  })
  const term = useQuery({
    queryKey: ['term', 'current'],
    queryFn: () => api<AcademicTerm>('/config/terms/current'),
    retry: false,
  })

  const teachingSlots = (slots.data ?? []).filter((s) => s.active)
  const activeDays = (days.data ?? []).filter((d) => d.active).map((d) => d.dayOfWeek)
  const dayOptions = activeDays.length > 0 ? activeDays : ALL_DAYS

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['fixed-commitments'] })
    queryClient.invalidateQueries({ queryKey: ['workload'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  function fail(err: unknown) {
    setError(err instanceof ApiError ? err.message : 'The request failed.')
  }

  const save = useMutation({
    mutationFn: (values: typeof empty) => {
      const body = {
        title: values.title,
        commitmentType: values.commitmentType,
        facultyId: values.facultyId ? Number(values.facultyId) : null,
        labId: values.labId ? Number(values.labId) : null,
        academicTermId: values.allTerms ? null : (term.data?.id ?? null),
        dayOfWeek: values.dayOfWeek,
        startSlotId: Number(values.startSlotId),
        endSlotId: Number(values.endSlotId),
        note: values.note || null,
      }
      return api<FixedCommitment>('/fixed-commitments', { method: 'POST', body })
    },
    onSuccess: () => {
      setForm(null)
      setError(null)
      refresh()
    },
    onError: fail,
  })

  const remove = useMutation({
    mutationFn: (id: number) => api(`/fixed-commitments/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setRemoveId(null)
      refresh()
    },
    onError: fail,
  })

  const data = list.data ?? []
  const lectures = data.filter((c) => c.commitmentType === 'LECTURE').length
  const withFaculty = data.filter((c) => c.facultyId != null).length

  return (
    <>
      <PageHeader
        title="Fixed Lectures & Commitments"
        description="Recurring weekly obligations the practical optimiser must work around but cannot move — fixed lectures, meetings, or externally reserved labs. Each one blocks its slot for the named faculty and/or lab, and its hours count towards that teacher's total weekly load."
        actions={
          <Button
            variant="primary"
            onClick={() => {
              setForm({
                ...empty,
                dayOfWeek: dayOptions[0] ?? 'MONDAY',
                startSlotId: String(teachingSlots[0]?.id ?? ''),
                endSlotId: String(teachingSlots[0]?.id ?? ''),
              })
              setError(null)
            }}
          >
            <Icon name="plus" className="h-3.5 w-3.5" />
            Add commitment
          </Button>
        }
      />

      {teachingSlots.length === 0 && (
        <div className="mb-4">
          <InfoNote tone="warn">
            Define working hours in Settings before adding fixed commitments.
          </InfoNote>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <StatCard label="Commitments" value={data.length} />
        <StatCard label="Fixed lectures" value={lectures} />
        <StatCard label="Tied to a teacher" value={withFaculty} sub="Count toward workload" />
      </div>

      <Card className="mt-4" bodyClassName="">
        {list.isLoading && <Spinner label="Loading commitments" />}
        {list.error && (
          <ErrorNote message={(list.error as Error).message} onRetry={() => list.refetch()} />
        )}
        {list.data && data.length === 0 && (
          <EmptyState
            title="No fixed commitments yet"
            hint="Add the fixed lectures and reserved lab slots that already occupy your faculty and rooms, so the practical timetable is built around them."
          />
        )}
        {data.length > 0 && (
          <Table
            head={
              <tr>
                <Th>Title</Th>
                <Th>Type</Th>
                <Th>Day</Th>
                <Th>Time</Th>
                <Th>Faculty</Th>
                <Th>Laboratory</Th>
                <Th />
              </tr>
            }
          >
            {data.map((c) => (
              <tr key={c.id} className="hover:bg-navy-50/50">
                <Td className="font-medium">{c.title}</Td>
                <Td>
                  <Badge>{titleCase(c.commitmentType)}</Badge>
                </Td>
                <Td>{titleCase(c.dayOfWeek)}</Td>
                <Td className="tabular">
                  {hhmm(c.startTime)}–{hhmm(c.endTime)}
                </Td>
                <Td>{c.facultyName ?? '—'}</Td>
                <Td>{c.labName ?? '—'}</Td>
                <Td className="text-right">
                  <Button size="sm" variant="ghost" onClick={() => setRemoveId(c.id)}>
                    Remove
                  </Button>
                </Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        title="Add fixed commitment"
        description="Name a faculty member, a laboratory, or both — whichever the commitment ties up."
        open={form != null}
        error={error}
        submitting={save.isPending}
        width="max-w-2xl"
        onClose={() => setForm(null)}
        onSubmit={() => form && save.mutate(form)}
      >
        {form && (
          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Title" hint="e.g. Data Structures lecture">
                <TextInput
                  value={form.title}
                  required
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                />
              </Field>
              <Field label="Type">
                <Select
                  value={form.commitmentType}
                  onChange={(e) => setForm({ ...form, commitmentType: e.target.value })}
                >
                  <option value="LECTURE">Lecture</option>
                  <option value="MEETING">Meeting</option>
                  <option value="RESERVED">Reserved lab</option>
                </Select>
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Faculty" hint="Optional — blocks and loads this teacher">
                <Select
                  value={form.facultyId}
                  onChange={(e) => setForm({ ...form, facultyId: e.target.value })}
                >
                  <option value="">None</option>
                  {(faculty.data ?? []).map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Laboratory" hint="Optional — blocks this room">
                <Select
                  value={form.labId}
                  onChange={(e) => setForm({ ...form, labId: e.target.value })}
                >
                  <option value="">None</option>
                  {(labs.data ?? []).map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.labName}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Day">
                <Select
                  value={form.dayOfWeek}
                  onChange={(e) => setForm({ ...form, dayOfWeek: e.target.value })}
                >
                  {dayOptions.map((d) => (
                    <option key={d} value={d}>
                      {titleCase(d)}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Start period">
                <Select
                  value={form.startSlotId}
                  required
                  onChange={(e) => setForm({ ...form, startSlotId: e.target.value })}
                >
                  {teachingSlots.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.label} ({hhmm(s.startTime)})
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="End period">
                <Select
                  value={form.endSlotId}
                  required
                  onChange={(e) => setForm({ ...form, endSlotId: e.target.value })}
                >
                  {teachingSlots.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.label} ({hhmm(s.endTime)})
                    </option>
                  ))}
                </Select>
              </Field>
            </div>

            <Field label="Note" hint="Optional">
              <TextInput
                value={form.note}
                onChange={(e) => setForm({ ...form, note: e.target.value })}
              />
            </Field>

            <label className="flex items-center gap-2 text-[13px] text-navy-700">
              <input
                type="checkbox"
                checked={form.allTerms}
                onChange={(e) => setForm({ ...form, allTerms: e.target.checked })}
              />
              Applies to every term (otherwise only the current term
              {term.data ? ` — ${term.data.label}` : ''})
            </label>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={removeId != null}
        title="Remove commitment"
        message="This frees the slot for the optimiser and stops counting its hours towards workload."
        confirmLabel="Remove"
        busy={remove.isPending}
        onConfirm={() => removeId != null && remove.mutate(removeId)}
        onClose={() => setRemoveId(null)}
      />
    </>
  )
}
