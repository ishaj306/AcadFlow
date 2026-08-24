import { hhmm, titleCase } from '../lib/api'
import type { DayOfWeek, FixedCommitment, TimeSlot, TimetableEntry } from '../lib/types'
import { EmptyState } from './ui'

/**
 * Weekly practical grid: periods down the left, working days across the top.
 *
 * Several batches legitimately run in the same period in different
 * laboratories, so a cell stacks every session that *starts* in it. Each block
 * prints its own time range, which reads more reliably than row spanning once
 * cells can hold more than one session.
 */

// Muted, print-friendly tints. Subjects are colour-coded so a reader can track
// one subject across the week without reading every cell.
const TINTS = [
  'bg-[#eef2f7] border-l-[#486581]',
  'bg-[#eaf3ef] border-l-[#2f7a5a]',
  'bg-[#f5efe6] border-l-[#a9762c]',
  'bg-[#f2eef5] border-l-[#6b5b95]',
  'bg-[#f7eeee] border-l-[#a04747]',
  'bg-[#e9f1f5] border-l-[#37728c]',
  'bg-[#f0f2e8] border-l-[#6f7d3c]',
  'bg-[#f5eef2] border-l-[#8c4a6b]',
]

function tintFor(subjectId: number) {
  return TINTS[subjectId % TINTS.length]
}

export interface GridLabels {
  /** What each block leads with; the rest is shown beneath. */
  primary?: (entry: TimetableEntry) => string
  showFaculty?: boolean
  showLab?: boolean
  showBatch?: boolean
}

export default function TimetableGrid({
  days,
  timeSlots,
  entries,
  lectures = [],
  labels = {},
  highlightEntryId,
  emptyHint,
  onEntryClick,
}: {
  days: DayOfWeek[]
  timeSlots: TimeSlot[]
  entries: TimetableEntry[]
  /**
   * Whole-class fixed lectures (from the Fixed Lectures page). Practicals and
   * lectures live in different periods, so a lecture simply fills the cell it
   * starts in. Purely presentational — they are constraints, not solver output.
   */
  lectures?: FixedCommitment[]
  labels?: GridLabels
  highlightEntryId?: number
  emptyHint?: string
  /** When set, each session becomes clickable (used for manual editing). */
  onEntryClick?: (entry: TimetableEntry) => void
}) {
  const { primary = (e) => e.subjectName, showFaculty = true, showLab = true, showBatch = true } =
    labels

  if (entries.length === 0 && lectures.length === 0) {
    return (
      <EmptyState
        title="Nothing scheduled"
        hint={emptyHint ?? 'No practical sessions match the current selection.'}
      />
    )
  }

  const slots = [...timeSlots].sort((a, b) => a.slotOrder - b.slotOrder)

  // Index sessions by the cell they begin in.
  const startingAt = new Map<string, TimetableEntry[]>()
  for (const entry of entries) {
    const key = `${entry.dayOfWeek}|${entry.startSlotId}`
    const bucket = startingAt.get(key)
    if (bucket) bucket.push(entry)
    else startingAt.set(key, [entry])
  }

  // Whole-class lectures, indexed the same way.
  const lecturesAt = new Map<string, FixedCommitment[]>()
  for (const lecture of lectures) {
    const key = `${lecture.dayOfWeek}|${lecture.startSlotId}`
    const bucket = lecturesAt.get(key)
    if (bucket) bucket.push(lecture)
    else lecturesAt.set(key, [lecture])
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[900px] border-collapse">
        <thead>
          <tr>
            <th className="w-28 border border-navy-100 bg-navy-50 px-2 py-2 text-left text-[11px] font-semibold tracking-wide text-navy-600 uppercase">
              Period
            </th>
            {days.map((day) => (
              <th
                key={day}
                className="border border-navy-100 bg-navy-50 px-2 py-2 text-left text-[11px] font-semibold tracking-wide text-navy-600 uppercase"
              >
                {titleCase(day)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {slots.map((slot) => {
            const teachable = slot.slotType === 'TEACHING' || slot.slotType === 'SPECIAL'

            if (!teachable) {
              return (
                <tr key={slot.id}>
                  <td className="border border-navy-100 bg-navy-50/60 px-2 py-1.5 align-top">
                    <div className="tabular text-[11px] font-medium text-navy-500">
                      {hhmm(slot.startTime)}–{hhmm(slot.endTime)}
                    </div>
                  </td>
                  <td
                    colSpan={days.length}
                    className="border border-navy-100 bg-navy-50/60 px-3 py-1.5 text-center text-[11px] font-medium tracking-wide text-navy-500 uppercase"
                  >
                    {titleCase(slot.slotType)}
                  </td>
                </tr>
              )
            }

            return (
              <tr key={slot.id}>
                <td className="border border-navy-100 bg-white px-2 py-2 align-top">
                  <div className="tabular text-[12px] font-semibold text-navy-800">
                    {hhmm(slot.startTime)}
                  </div>
                  <div className="tabular text-[11px] text-navy-400">{hhmm(slot.endTime)}</div>
                </td>

                {days.map((day) => {
                  const cell = startingAt.get(`${day}|${slot.id}`) ?? []
                  const cellLectures = lecturesAt.get(`${day}|${slot.id}`) ?? []
                  return (
                    <td
                      key={day}
                      className="border border-navy-100 bg-white p-1 align-top"
                      style={{ minWidth: 170 }}
                    >
                      <div className="flex flex-col gap-1">
                        {cellLectures.map((lecture) => (
                          <div
                            key={`lec-${lecture.id}`}
                            className="rounded-sm border-l-[3px] border-l-[#8a3b4c] bg-[#f4ece2] px-2 py-1.5"
                          >
                            <div className="text-[12px] leading-tight font-semibold text-[#6b2f3d]">
                              {lecture.title}
                            </div>
                            <div className="tabular mt-0.5 text-[10px] text-navy-500">
                              {hhmm(lecture.startTime)}–{hhmm(lecture.endTime)} · Whole class
                            </div>
                            {lecture.labName && (
                              <div className="truncate text-[11px] text-navy-500">
                                {lecture.labName}
                              </div>
                            )}
                          </div>
                        ))}
                        {cell.map((entry) => (
                          <div
                            key={entry.id}
                            onClick={onEntryClick ? () => onEntryClick(entry) : undefined}
                            className={`rounded-sm border-l-[3px] px-2 py-1.5 ${tintFor(entry.subjectId)} ${
                              highlightEntryId === entry.id
                                ? 'ring-2 ring-info-600 ring-offset-1'
                                : ''
                            } ${onEntryClick ? 'cursor-pointer hover:ring-2 hover:ring-info-400' : ''}`}
                          >
                            <div className="text-[12px] leading-tight font-semibold text-navy-900">
                              {primary(entry)}
                            </div>
                            <div className="tabular mt-0.5 text-[10px] text-navy-500">
                              {hhmm(entry.startTime)}–{hhmm(entry.endTime)}
                              {showBatch && (
                                <>
                                  {' · '}
                                  {entry.batchName} ({entry.division})
                                </>
                              )}
                            </div>
                            {showFaculty && (
                              <div className="mt-0.5 truncate text-[11px] text-navy-700">
                                {entry.facultyName}
                              </div>
                            )}
                            {showLab && (
                              <div className="truncate text-[11px] text-navy-500">
                                {entry.labName}
                              </div>
                            )}
                            {entry.status === 'RESCHEDULED' && (
                              <div className="mt-1 inline-block rounded border border-warn-600/30 bg-warn-50 px-1 text-[10px] font-medium text-warn-700">
                                Rescheduled
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </td>
                  )
                })}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
