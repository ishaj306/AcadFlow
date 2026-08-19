import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api'
import type { StudentParseResult } from '../lib/types'
import { Badge, Button, Card, InfoNote, Table, Td, Th } from './ui'
import { Icon } from './icons'

const PLACEHOLDER = `Paste roster text, one student per line. Examples it understands:
21CS001  Aarav Sharma  A
21CS002, Diya Patel, A
Rohan Mehta 21CS003 B`

/**
 * Paste-to-parse tool: turns free-form roster text into structured students and
 * a suggested batch split. This is a preview only — it does not save anyone.
 */
export default function StudentParser() {
  const [text, setText] = useState('')
  const [batchSize, setBatchSize] = useState(30)
  const [result, setResult] = useState<StudentParseResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const parse = useMutation({
    mutationFn: () =>
      api<StudentParseResult>('/students/parse', {
        method: 'POST',
        body: { rawText: text, batchSize },
      }),
    onSuccess: (data) => {
      setResult(data)
      setError(null)
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not parse the text.'),
  })

  return (
    <Card
      className="mb-4"
      title="Paste a roster"
      description="Extract students from pasted text and preview a batch split — nothing is saved"
    >
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={PLACEHOLDER}
        rows={6}
        className="w-full rounded border border-navy-200 px-3 py-2 font-mono text-[12px] focus:border-info-500 focus:outline-none"
      />
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-[13px] text-navy-700">
          Students per batch
          <input
            type="number"
            min={1}
            max={200}
            value={batchSize}
            onChange={(e) => setBatchSize(Number(e.target.value))}
            className="w-20 rounded border border-navy-200 px-2 py-1 text-[13px]"
          />
        </label>
        <Button
          variant="primary"
          onClick={() => parse.mutate()}
          disabled={parse.isPending || !text.trim()}
        >
          <Icon name="search" className="h-3.5 w-3.5" />
          {parse.isPending ? 'Parsing…' : 'Parse roster'}
        </Button>
      </div>

      {error && (
        <div className="mt-3">
          <InfoNote tone="danger">{error}</InfoNote>
        </div>
      )}

      {result && (
        <div className="mt-4 space-y-4">
          <div className="flex flex-wrap gap-2 text-[13px]">
            <Badge tone="ok">{result.students.length} students found</Badge>
            <Badge tone="info">{result.suggestedBatches.length} batches suggested</Badge>
            {result.warnings.length > 0 && (
              <Badge tone="warn">{result.warnings.length} lines skipped</Badge>
            )}
          </div>

          {result.suggestedBatches.length > 0 && (
            <div>
              <div className="mb-1.5 text-[11px] font-semibold tracking-wide text-navy-500 uppercase">
                Suggested batches
              </div>
              <div className="flex flex-wrap gap-2">
                {result.suggestedBatches.map((b, i) => (
                  <div
                    key={i}
                    className="rounded-lg border border-navy-100 bg-navy-50/40 px-3 py-2 text-[12px]"
                  >
                    <div className="font-semibold text-navy-800">{b.batchName}</div>
                    <div className="text-navy-500">{b.studentCount} students</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {result.students.length > 0 && (
            <div className="max-h-64 overflow-y-auto rounded border border-navy-100">
              <Table
                head={
                  <tr>
                    <Th>Roll number</Th>
                    <Th>Name</Th>
                    <Th>Division</Th>
                  </tr>
                }
              >
                {result.students.map((s, i) => (
                  <tr key={i}>
                    <Td className="tabular font-medium">{s.rollNumber}</Td>
                    <Td>{s.name}</Td>
                    <Td>{s.division ?? '—'}</Td>
                  </tr>
                ))}
              </Table>
            </div>
          )}

          {result.warnings.length > 0 && (
            <details>
              <summary className="cursor-pointer text-[12px] font-medium text-warn-700 select-none">
                {result.warnings.length} line(s) could not be read
              </summary>
              <ul className="mt-1.5 space-y-1 text-[12px] text-navy-600">
                {result.warnings.map((w, i) => (
                  <li key={i}>• {w}</li>
                ))}
              </ul>
            </details>
          )}

          <InfoNote tone="info">
            This is a preview. To save these students, use the CSV/Excel import above.
          </InfoNote>
        </div>
      )}
    </Card>
  )
}
