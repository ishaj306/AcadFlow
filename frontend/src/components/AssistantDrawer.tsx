import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError, api, hhmm, titleCase } from '../lib/api'
import type { AssistantAnswer } from '../lib/types'
import { Icon } from './icons'
import { Button } from './ui'

interface Turn {
  role: 'user' | 'assistant'
  text: string
  answer?: AssistantAnswer
}

const GREETING: Turn = {
  role: 'assistant',
  text: "Hi! Ask me about the published timetable — a day, a division, a faculty member, a lab, or a count.",
  answer: {
    answer: '',
    entries: [],
    suggestions: ["What's on today?", 'Show Division A schedule', 'How many practicals this week?'],
  },
}

export default function AssistantDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [turns, setTurns] = useState<Turn[]>([GREETING])
  const [input, setInput] = useState('')
  const endRef = useRef<HTMLDivElement>(null)

  const ask = useMutation({
    mutationFn: (question: string) =>
      api<AssistantAnswer>('/assistant/query', { method: 'POST', body: { question } }),
    onSuccess: (answer) =>
      setTurns((t) => [...t, { role: 'assistant', text: answer.answer, answer }]),
    onError: (err) =>
      setTurns((t) => [
        ...t,
        { role: 'assistant', text: err instanceof ApiError ? err.message : 'Something went wrong.' },
      ]),
  })

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  const send = (question: string) => {
    const q = question.trim()
    if (!q || ask.isPending) return
    setTurns((t) => [...t, { role: 'user', text: q }])
    setInput('')
    ask.mutate(q)
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-40 no-print">
      <div className="absolute inset-0 bg-navy-900/30" onClick={onClose} />
      <aside className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-navy-200 bg-white shadow-xl">
        <header className="flex items-center justify-between border-b border-navy-100 px-4 py-3">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded bg-info-600/10 text-info-700">
              <Icon name="search" className="h-4 w-4" />
            </div>
            <div className="leading-tight">
              <div className="text-[13px] font-semibold text-navy-900">Timetable Assistant</div>
              <div className="text-[11px] text-navy-500">Answers from the published schedule</div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded p-1.5 text-navy-400 hover:bg-navy-50 hover:text-navy-700"
            aria-label="Close assistant"
          >
            <Icon name="close" />
          </button>
        </header>

        <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
          {turns.map((turn, i) => (
            <div key={i} className={turn.role === 'user' ? 'flex justify-end' : ''}>
              <div
                className={`max-w-[85%] rounded-lg px-3 py-2 text-[13px] ${
                  turn.role === 'user'
                    ? 'bg-navy-800 text-white'
                    : 'bg-navy-50 text-navy-800'
                }`}
              >
                {turn.text && <p>{turn.text}</p>}

                {turn.answer && turn.answer.entries.length > 0 && (
                  <ul className="mt-2 space-y-1.5">
                    {turn.answer.entries.slice(0, 12).map((e) => (
                      <li key={e.id} className="rounded border border-navy-100 bg-white px-2 py-1.5">
                        <div className="text-[12px] font-medium text-navy-900">{e.subjectName}</div>
                        <div className="tabular text-[11px] text-navy-500">
                          {titleCase(e.dayOfWeek)} {hhmm(e.startTime)}–{hhmm(e.endTime)} · {e.batchName}
                        </div>
                        <div className="text-[11px] text-navy-500">
                          {e.facultyName} · {e.labName}
                        </div>
                      </li>
                    ))}
                    {turn.answer.entries.length > 12 && (
                      <li className="text-[11px] text-navy-400">
                        …and {turn.answer.entries.length - 12} more
                      </li>
                    )}
                  </ul>
                )}

                {turn.answer && turn.answer.suggestions.length > 0 && turn.role === 'assistant' && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {turn.answer.suggestions.map((s) => (
                      <button
                        key={s}
                        onClick={() => send(s)}
                        className="rounded-full border border-navy-200 bg-white px-2.5 py-0.5 text-[11px] text-navy-600 hover:bg-navy-50"
                      >
                        {s}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
          {ask.isPending && (
            <div className="text-[12px] text-navy-400">Thinking…</div>
          )}
          <div ref={endRef} />
        </div>

        <form
          className="flex items-center gap-2 border-t border-navy-100 px-3 py-3"
          onSubmit={(e) => {
            e.preventDefault()
            send(input)
          }}
        >
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask about the timetable…"
            className="min-w-0 flex-1 rounded border border-navy-200 px-3 py-1.5 text-[13px] focus:border-info-500 focus:outline-none"
          />
          <Button type="submit" variant="primary" disabled={ask.isPending || !input.trim()}>
            Send
          </Button>
        </form>
      </aside>
    </div>
  )
}
