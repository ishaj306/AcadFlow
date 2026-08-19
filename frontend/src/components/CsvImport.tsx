import { useRef, useState } from 'react'
import { getToken } from '../lib/api'
import { Icon } from './icons'
import { Button, Card, InfoNote, Table, Td, Th } from './ui'

/** Matches the backend edu.batchmaker.dto.common.ImportResult record. */
export interface ImportResult {
  totalRows: number
  imported: number
  updated: number
  skipped: number
  errors: { rowNumber: number; reference?: string; message: string }[]
}

/** Triggers a browser download of an in-memory CSV template. */
export function downloadCsv(filename: string, content: string) {
  // Lead BOM so Excel opens UTF-8 correctly.
  const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * Encapsulates the upload half of a CSV import: a hidden file input, the
 * multipart POST, and the parsed result/error state. Rendering of the two
 * buttons and the result panel is left to the caller so they can slot into a
 * page header and body respectively.
 */
export function useCsvImport(endpoint: string, onImported: () => void) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function upload(file: File) {
    setUploading(true)
    setError(null)
    setResult(null)
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await fetch(`/api${endpoint}?updateExisting=true`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${getToken()}` },
        body,
      })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload?.message ?? 'Import failed.')
      setResult(payload as ImportResult)
      onImported()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  return { fileInput, uploading, result, error, upload, open: () => fileInput.current?.click() }
}

/** The hidden file input plus the "template" and "import" header buttons. */
export function CsvImportButtons({
  imp,
  onDownloadTemplate,
  disabled,
  disabledTitle,
}: {
  imp: ReturnType<typeof useCsvImport>
  onDownloadTemplate: () => void
  disabled?: boolean
  disabledTitle?: string
}) {
  return (
    <>
      <input
        ref={imp.fileInput}
        type="file"
        accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) imp.upload(file)
        }}
      />
      <Button onClick={onDownloadTemplate}>
        <Icon name="download" className="h-3.5 w-3.5" />
        CSV template
      </Button>
      <Button onClick={imp.open} disabled={disabled || imp.uploading} title={disabled ? disabledTitle : undefined}>
        <Icon name="download" className="h-3.5 w-3.5" />
        {imp.uploading ? 'Importing…' : 'Import CSV / Excel'}
      </Button>
    </>
  )
}

/** Summary of a completed import with a per-row table of anything rejected. */
export function ImportResultPanel({
  result,
  error,
  referenceLabel,
}: {
  result: ImportResult | null
  error: string | null
  referenceLabel: string
}) {
  if (!result && !error) return null
  return (
    <Card className="mb-4" title="Import result">
      {error && <InfoNote tone="danger">{error}</InfoNote>}
      {result && (
        <div className="space-y-2">
          <InfoNote tone={result.errors.length > 0 ? 'warn' : 'ok'}>
            {result.totalRows} row(s) read · {result.imported} added · {result.updated} updated ·{' '}
            {result.skipped} skipped
          </InfoNote>
          {result.errors.length > 0 && (
            <div className="max-h-48 overflow-y-auto rounded border border-navy-100">
              <Table
                head={
                  <tr>
                    <Th>Row</Th>
                    <Th>{referenceLabel}</Th>
                    <Th>Problem</Th>
                  </tr>
                }
              >
                {result.errors.map((row) => (
                  <tr key={`${row.rowNumber}-${row.reference ?? ''}`}>
                    <Td className="tabular">{row.rowNumber}</Td>
                    <Td className="tabular">{row.reference ?? '—'}</Td>
                    <Td className="text-[12px] text-danger-700">{row.message}</Td>
                  </tr>
                ))}
              </Table>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
