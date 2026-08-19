import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { DayOfWeek, Faculty, Laboratory, Page, TimeSlot, TimetableEntry } from '../lib/types'
import Modal, { ConfirmDialog } from './Modal'
import { Field, Select } from './ui'

/**
 * Manual edit of one generated session: reassign the faculty or lab, move it to
 * another day or period, or remove it. Every save re-validates the whole draft
 * on the server, so conflicts surface immediately.
 */
export default function EntryEditorModal({
  timetableId,
  entry,
  days,
  timeSlots,
  onClose,
}: {
  timetableId: number
  entry: TimetableEntry
  days: DayOfWeek[]
  timeSlots: TimeSlot[]
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [facultyId, setFacultyId] = useState(entry.facultyId)
  const [labId, setLabId] = useState(entry.labId)
  const [day, setDay] = useState<DayOfWeek>(entry.dayOfWeek)
  const [startSlotId, setStartSlotId] = useState(entry.startSlotId)
  const [endSlotId, setEndSlotId] = useState(entry.endSlotId)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const faculty = useQuery({
    queryKey: ['faculty', 'all'],
    queryFn: () => api<Page<Faculty>>('/faculty?size=200'),
  })
  const labs = useQuery({ queryKey: ['labs'], queryFn: () => api<Laboratory[]>('/labs') })

  const teachable = timeSlots.filter((s) => s.slotType === 'TEACHING' || s.slotType === 'SPECIAL')

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['timetable', timetableId] })
    queryClient.invalidateQueries({ queryKey: ['timetables'] })
  }

  const save = useMutation({
    mutationFn: () =>
      api(`/timetable/${timetableId}/entries/${entry.id}`, {
        method: 'PUT',
        body: {
          practicalId: entry.practicalId,
          facultyId,
          labId,
          dayOfWeek: day,
          startSlotId,
          endSlotId,
        },
      }),
    onSuccess: () => {
      invalidate()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the change.'),
  })

  const remove = useMutation({
    mutationFn: () =>
      api(`/timetable/${timetableId}/entries/${entry.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      invalidate()
      onClose()
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not delete the session.'),
  })

  if (!entry.practicalId) {
    return (
      <Modal open title="Cannot edit this session" onClose={onClose}>
        <p className="text-[13px] text-navy-600">
          This session is not linked to a practical, so it can't be edited here.
        </p>
      </Modal>
    )
  }

  return (
    <>
      <Modal
        open
        title={`Edit: ${entry.subjectName}`}
        description={`${entry.batchName} · Division ${entry.division}`}
        onClose={onClose}
        onSubmit={() => save.mutate()}
        submitLabel="Save changes"
        submitting={save.isPending}
        error={error}
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="Faculty">
            <Select value={facultyId} onChange={(e) => setFacultyId(Number(e.target.value))}>
              {faculty.data?.content.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Laboratory">
            <Select value={labId} onChange={(e) => setLabId(Number(e.target.value))}>
              {labs.data?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.labName} ({l.labType})
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Day">
            <Select value={day} onChange={(e) => setDay(e.target.value as DayOfWeek)}>
              {days.map((d) => (
                <option key={d} value={d}>
                  {d.charAt(0) + d.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          </Field>
          <div />
          <Field label="Start period">
            <Select value={startSlotId} onChange={(e) => setStartSlotId(Number(e.target.value))}>
              {teachable.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="End period">
            <Select value={endSlotId} onChange={(e) => setEndSlotId(Number(e.target.value))}>
              {teachable.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <button
          type="button"
          onClick={() => setConfirmDelete(true)}
          className="mt-4 text-[12px] font-medium text-danger-700 hover:underline"
        >
          Delete this session
        </button>
      </Modal>

      <ConfirmDialog
        open={confirmDelete}
        title="Delete session?"
        message={`Remove ${entry.subjectName} for ${entry.batchName} from the draft? This cannot be undone.`}
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirmDelete(false)}
      />
    </>
  )
}
