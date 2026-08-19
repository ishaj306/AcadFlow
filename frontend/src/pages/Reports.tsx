import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, getToken, hhmm, titleCase } from '../lib/api'
import type { TimetableDetail, WorkloadSummary } from '../lib/types'
import TimetableGrid from '../components/TimetableGrid'
import { Icon } from '../components/icons'
import {
  Button,
  Card,
  ErrorNote,
  InfoNote,
  PageHeader,
  Select,
  Spinner,
  Table,
  Td,
  Th,
} from '../components/ui'

type ReportKind = 'division' | 'faculty' | 'laboratory' | 'batch' | 'workload' | 'utilisation'

const REPORTS: { value: ReportKind; label: string; description: string }[] = [
  { value: 'division', label: 'Division timetable', description: 'Weekly grid for one division' },
  { value: 'faculty', label: 'Faculty timetable', description: 'Weekly grid for one faculty member' },
  { value: 'laboratory', label: 'Laboratory timetable', description: 'Weekly grid for one laboratory' },
  { value: 'batch', label: 'Batch timetable', description: 'Weekly grid for one practical batch' },
  { value: 'workload', label: 'Faculty workload', description: 'Assigned hours and utilisation' },
  { value: 'utilisation', label: 'Laboratory utilisation', description: 'Sessions and hours per lab' },
]

function downloadCsv(filename: string, rows: (string | number)[][]) {
  const escape = (cell: string | number) => {
    const text = String(cell ?? '')
    return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
  }
  const csv = rows.map((row) => row.map(escape).join(',')).join('\r\n')
  // BOM so Excel opens UTF-8 correctly.
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export default function Reports() {
  const [kind, setKind] = useState<ReportKind>('division')
  const [selection, setSelection] = useState('')
  const [exportError, setExportError] = useState<string | null>(null)
  const [exporting, setExporting] = useState<'xlsx' | 'pdf' | null>(null)

  const timetable = useQuery({
    queryKey: ['timetable', 'current'],
    queryFn: () => api<TimetableDetail>('/timetable/current'),
    retry: false,
  })

  const workload = useQuery({
    queryKey: ['workload'],
    queryFn: () => api<WorkloadSummary>('/workload'),
    retry: false,
    enabled: kind === 'workload',
  })

  const entries = timetable.data?.entries ?? []

  const choices = useMemo(() => {
    const map = new Map<string, string>()
    for (const entry of entries) {
      if (kind === 'division') map.set(entry.division, `Division ${entry.division}`)
      if (kind === 'faculty') map.set(String(entry.facultyId), entry.facultyName)
      if (kind === 'laboratory') map.set(String(entry.labId), entry.labName)
      if (kind === 'batch')
        map.set(
          String(entry.batchId),
          `${entry.subjectName} — ${entry.batchName} (${entry.division})`,
        )
    }
    return [...map].sort((a, b) => a[1].localeCompare(b[1]))
  }, [entries, kind])

  const filtered = useMemo(() => {
    if (!selection) return entries
    switch (kind) {
      case 'division':
        return entries.filter((e) => e.division === selection)
      case 'faculty':
        return entries.filter((e) => e.facultyId === Number(selection))
      case 'laboratory':
        return entries.filter((e) => e.labId === Number(selection))
      case 'batch':
        return entries.filter((e) => e.batchId === Number(selection))
      default:
        return entries
    }
  }, [entries, kind, selection])

  const labUtilisation = useMemo(() => {
    const map = new Map<string, { name: string; sessions: number; minutes: number; capacity: number }>()
    for (const entry of entries) {
      const key = String(entry.labId)
      const start = new Date(`1970-01-01T${entry.startTime}`)
      const end = new Date(`1970-01-01T${entry.endTime}`)
      const minutes = (end.getTime() - start.getTime()) / 60000
      const existing = map.get(key)
      if (existing) {
        existing.sessions += 1
        existing.minutes += minutes
      } else {
        map.set(key, {
          name: entry.labName,
          sessions: 1,
          minutes,
          capacity: entry.labCapacity,
        })
      }
    }
    return [...map.values()].sort((a, b) => b.minutes - a.minutes)
  }, [entries])

  const isGrid = ['division', 'faculty', 'laboratory', 'batch'].includes(kind)
  const activeLabel = choices.find(([value]) => value === selection)?.[1] ?? 'All'

  function exportCsv() {
    if (kind === 'workload' && workload.data) {
      downloadCsv('faculty-workload.csv', [
        ['Employee code', 'Faculty', 'Designation', 'Department', 'Assigned hours', 'Max hours', 'Utilisation %', 'Practicals', 'Status'],
        ...workload.data.faculty.map((row) => [
          row.employeeCode,
          row.facultyName,
          row.designation,
          row.departmentCode,
          row.assignedHours,
          row.maxWeeklyHours,
          row.utilizationPercent,
          row.practicalCount,
          row.status,
        ]),
      ])
      return
    }
    if (kind === 'utilisation') {
      downloadCsv('laboratory-utilisation.csv', [
        ['Laboratory', 'Sessions per week', 'Hours per week', 'Capacity'],
        ...labUtilisation.map((row) => [row.name, row.sessions, (row.minutes / 60).toFixed(1), row.capacity]),
      ])
      return
    }
    downloadCsv(`timetable-${kind}-${selection || 'all'}.csv`, [
      ['Day', 'Start', 'End', 'Subject', 'Code', 'Batch', 'Division', 'Students', 'Faculty', 'Laboratory', 'Location'],
      ...filtered.map((e) => [
        titleCase(e.dayOfWeek),
        hhmm(e.startTime),
        hhmm(e.endTime),
        e.subjectName,
        e.subjectCode,
        e.batchName,
        e.division,
        e.studentCount,
        e.facultyName,
        e.labName,
        e.labLocation ?? '',
      ]),
    ])
  }

  // Server-generated .xlsx / .pdf, built from the live database. Workload maps to
  // the workload report; every timetable-shaped report maps to the timetable one.
  async function downloadServerReport(format: 'xlsx' | 'pdf') {
    const endpoint = kind === 'workload' ? 'workload' : 'timetable'
    setExporting(format)
    setExportError(null)
    try {
      const res = await fetch(`/api/reports/${endpoint}/export?format=${format}`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      })
      if (!res.ok) {
        let message = `Export failed (status ${res.status}).`
        try {
          const payload = await res.json()
          if (payload?.message) message = payload.message
        } catch {
          /* binary or empty body — keep the default message */
        }
        throw new Error(message)
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${endpoint}.${format}`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setExportError((err as Error).message)
    } finally {
      setExporting(null)
    }
  }

  return (
    <>
      <PageHeader
        title="Reports"
        description="Timetable and analytics reports for the published schedule. Export as CSV, Excel or PDF."
        actions={
          <>
            <Button onClick={exportCsv}>
              <Icon name="download" className="h-3.5 w-3.5" />
              Export CSV
            </Button>
            <Button onClick={() => downloadServerReport('xlsx')} disabled={exporting !== null}>
              <Icon name="download" className="h-3.5 w-3.5" />
              {exporting === 'xlsx' ? 'Preparing…' : 'Excel'}
            </Button>
            <Button variant="primary" onClick={() => downloadServerReport('pdf')} disabled={exporting !== null}>
              <Icon name="reports" className="h-3.5 w-3.5" />
              {exporting === 'pdf' ? 'Preparing…' : 'PDF'}
            </Button>
          </>
        }
      />

      {exportError && (
        <div className="mb-4">
          <InfoNote tone="danger">{exportError}</InfoNote>
        </div>
      )}

      <Card className="mb-4 no-print">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-navy-600">Report</span>
            <Select
              value={kind}
              onChange={(e) => {
                setKind(e.target.value as ReportKind)
                setSelection('')
              }}
            >
              {REPORTS.map((report) => (
                <option key={report.value} value={report.value}>
                  {report.label}
                </option>
              ))}
            </Select>
            <span className="mt-1 block text-xs text-navy-400">
              {REPORTS.find((r) => r.value === kind)?.description}
            </span>
          </label>

          {isGrid && (
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-navy-600">Scope</span>
              <Select value={selection} onChange={(e) => setSelection(e.target.value)}>
                <option value="">All</option>
                {choices.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </label>
          )}
        </div>
      </Card>

      {timetable.isLoading && <Spinner label="Loading published timetable" />}
      {timetable.error && (
        <ErrorNote
          message={(timetable.error as Error).message}
          onRetry={() => timetable.refetch()}
        />
      )}

      {timetable.data && (
        <Card
          title={`${REPORTS.find((r) => r.value === kind)?.label} — ${isGrid ? activeLabel : 'All'}`}
          description={`${timetable.data.timetable.name} · ${timetable.data.timetable.academicTermLabel}`}
          bodyClassName={isGrid ? 'p-2' : ''}
        >
          {isGrid && (
            <TimetableGrid
              days={timetable.data.days}
              timeSlots={timetable.data.timeSlots}
              entries={filtered}
            />
          )}

          {kind === 'utilisation' && (
            <Table
              head={
                <tr>
                  <Th>Laboratory</Th>
                  <Th className="text-right">Sessions / week</Th>
                  <Th className="text-right">Hours / week</Th>
                  <Th className="text-right">Capacity</Th>
                </tr>
              }
            >
              {labUtilisation.map((row) => (
                <tr key={row.name}>
                  <Td className="font-medium">{row.name}</Td>
                  <Td className="tabular text-right">{row.sessions}</Td>
                  <Td className="tabular text-right">{(row.minutes / 60).toFixed(1)}</Td>
                  <Td className="tabular text-right">{row.capacity}</Td>
                </tr>
              ))}
            </Table>
          )}

          {kind === 'workload' && (
            <>
              {workload.isLoading && <Spinner label="Loading workload" />}
              {workload.data && (
                <Table
                  head={
                    <tr>
                      <Th>Code</Th>
                      <Th>Faculty</Th>
                      <Th>Department</Th>
                      <Th className="text-right">Assigned</Th>
                      <Th className="text-right">Maximum</Th>
                      <Th className="text-right">Utilisation</Th>
                      <Th className="text-right">Practicals</Th>
                      <Th>Status</Th>
                    </tr>
                  }
                >
                  {workload.data.faculty.map((row) => (
                    <tr key={row.facultyId}>
                      <Td className="tabular">{row.employeeCode}</Td>
                      <Td className="font-medium">{row.facultyName}</Td>
                      <Td>{row.departmentCode}</Td>
                      <Td className="tabular text-right">{row.assignedHours}h</Td>
                      <Td className="tabular text-right">{row.maxWeeklyHours}h</Td>
                      <Td className="tabular text-right">{row.utilizationPercent}%</Td>
                      <Td className="tabular text-right">{row.practicalCount}</Td>
                      <Td>{row.status}</Td>
                    </tr>
                  ))}
                </Table>
              )}
            </>
          )}
        </Card>
      )}

      <div className="mt-4 no-print">
        <InfoNote>
          CSV exports exactly the report shown. Excel and PDF are generated on the server from the
          live published timetable (or the workload report) and download as ready-to-share files.
        </InfoNote>
      </div>
    </>
  )
}
