import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api, hhmm, titleCase } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { DayOfWeek, TimeSlot, WorkingDay } from '../../lib/types'
import { Button, Card, ErrorNote, InfoNote, PageHeader, Spinner } from '../../components/ui'

type Availability = 'AVAILABLE' | 'UNAVAILABLE' | 'PREFERRED'

interface Cell {
  dayOfWeek: DayOfWeek
  timeSlotId: number
  availability: Availability
  note?: string
}

// Clicking a cell cycles through the three states.
const NEXT: Record<Availability, Availability> = {
  AVAILABLE: 'PREFERRED',
  PREFERRED: 'UNAVAILABLE',
  UNAVAILABLE: 'AVAILABLE',
}

const STYLE: Record<Availability, string> = {
  AVAILABLE: 'bg-white border-navy-200 text-navy-500 hover:bg-navy-50',
  PREFERRED: 'bg-ok-50 border-ok-600/40 text-ok-700 font-medium',
  UNAVAILABLE: 'bg-danger-50 border-danger-600/40 text-danger-700 font-medium',
}

const LABEL: Record<Availability, string> = {
  AVAILABLE: 'Available',
  PREFERRED: 'Preferred',
  UNAVAILABLE: 'Unavailable',
}

export default function FacultyAvailability() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const facultyId = user?.facultyId

  const [cells, setCells] = useState<Map<string, Availability>>(new Map())
  const [banner, setBanner] = useState<{ tone: 'ok' | 'danger'; text: string } | null>(null)

  const slots = useQuery({
    queryKey: ['timeslots'],
    queryFn: () => api<TimeSlot[]>('/config/time-slots'),
  })
  const days = useQuery({
    queryKey: ['workingdays'],
    queryFn: () => api<WorkingDay[]>('/config/working-days'),
  })
  const availability = useQuery({
    queryKey: ['availability', facultyId],
    queryFn: () => api<Cell[]>(`/faculty/${facultyId}/availability`),
    enabled: !!facultyId,
  })

  useEffect(() => {
    if (!availability.data) return
    const map = new Map<string, Availability>()
    for (const cell of availability.data) {
      map.set(`${cell.dayOfWeek}|${cell.timeSlotId}`, cell.availability)
    }
    setCells(map)
  }, [availability.data])

  const save = useMutation({
    mutationFn: () => {
      const payload: Cell[] = []
      for (const [key, value] of cells) {
        if (value === 'AVAILABLE') continue
        const [dayOfWeek, timeSlotId] = key.split('|')
        payload.push({
          dayOfWeek: dayOfWeek as DayOfWeek,
          timeSlotId: Number(timeSlotId),
          availability: value,
        })
      }
      return api<Cell[]>(`/faculty/${facultyId}/availability`, { method: 'PUT', body: payload })
    },
    onSuccess: () => {
      setBanner({
        tone: 'ok',
        text: 'Availability saved. It will be respected the next time a timetable is generated.',
      })
      queryClient.invalidateQueries({ queryKey: ['availability', facultyId] })
    },
    onError: (error) =>
      setBanner({
        tone: 'danger',
        text: error instanceof ApiError ? error.message : 'Could not save availability.',
      }),
  })

  if (!facultyId) {
    return (
      <>
        <PageHeader title="My availability" />
        <InfoNote tone="warn">This account is not linked to a faculty record.</InfoNote>
      </>
    )
  }

  const teaching = (slots.data ?? []).filter(
    (slot) => slot.active && (slot.slotType === 'TEACHING' || slot.slotType === 'SPECIAL'),
  )
  const activeDays = (days.data ?? []).filter((day) => day.active)

  function cycle(day: DayOfWeek, slotId: number) {
    const key = `${day}|${slotId}`
    const current = cells.get(key) ?? 'AVAILABLE'
    const next = new Map(cells)
    next.set(key, NEXT[current])
    setCells(next)
  }

  return (
    <>
      <PageHeader
        title="My availability"
        description="Mark periods you cannot teach, and periods you would prefer. Unavailable periods are treated as a hard constraint; preferred periods are optimised for."
        actions={
          <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save availability'}
          </Button>
        }
      />

      {banner && (
        <div className="mb-4">
          <InfoNote tone={banner.tone}>{banner.text}</InfoNote>
        </div>
      )}

      {(slots.isLoading || days.isLoading || availability.isLoading) && <Spinner />}
      {availability.error && (
        <ErrorNote
          message={(availability.error as Error).message}
          onRetry={() => availability.refetch()}
        />
      )}

      {teaching.length > 0 && activeDays.length > 0 && (
        <Card bodyClassName="p-3">
          <div className="mb-3 flex flex-wrap gap-3 text-[12px]">
            {(['AVAILABLE', 'PREFERRED', 'UNAVAILABLE'] as Availability[]).map((state) => (
              <span key={state} className="flex items-center gap-1.5">
                <span className={`inline-block h-3.5 w-6 rounded border ${STYLE[state]}`} />
                {LABEL[state]}
              </span>
            ))}
            <span className="text-navy-500">Click a cell to cycle through the three states.</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse">
              <thead>
                <tr>
                  <th className="w-28 border border-navy-100 bg-navy-50 px-2 py-2 text-left text-[11px] font-semibold tracking-wide text-navy-600 uppercase">
                    Period
                  </th>
                  {activeDays.map((day) => (
                    <th
                      key={day.id}
                      className="border border-navy-100 bg-navy-50 px-2 py-2 text-center text-[11px] font-semibold tracking-wide text-navy-600 uppercase"
                    >
                      {titleCase(day.dayOfWeek)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {teaching.map((slot) => (
                  <tr key={slot.id}>
                    <td className="tabular border border-navy-100 bg-white px-2 py-1.5 text-[12px] text-navy-700">
                      {hhmm(slot.startTime)}–{hhmm(slot.endTime)}
                    </td>
                    {activeDays.map((day) => {
                      const state = cells.get(`${day.dayOfWeek}|${slot.id}`) ?? 'AVAILABLE'
                      return (
                        <td key={day.id} className="border border-navy-100 p-1">
                          <button
                            onClick={() => cycle(day.dayOfWeek, slot.id)}
                            className={`w-full rounded border px-2 py-1.5 text-[11px] transition-colors ${STYLE[state]}`}
                          >
                            {LABEL[state]}
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  )
}
