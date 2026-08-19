import type { ReactNode } from 'react'

/* ------------------------------------------------------------------ layout */

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3 border-b border-navy-100 pb-4">
      <div>
        <h1 className="text-xl font-semibold text-navy-900">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-[13px] text-navy-500">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 no-print">{actions}</div>}
    </div>
  )
}

export function Card({
  title,
  description,
  actions,
  children,
  className = '',
  bodyClassName = 'p-4',
}: {
  title?: string
  description?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}) {
  return (
    <section className={`rounded border border-navy-100 bg-white ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-navy-100 px-4 py-3">
          <div>
            {title && <h2 className="text-sm font-semibold text-navy-800">{title}</h2>}
            {description && <p className="mt-0.5 text-xs text-navy-500">{description}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  )
}

/* ------------------------------------------------------------------ inputs */

type ButtonProps = {
  children: ReactNode
  onClick?: () => void
  type?: 'button' | 'submit'
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  size?: 'sm' | 'md'
  disabled?: boolean
  title?: string
  className?: string
}

export function Button({
  children,
  onClick,
  type = 'button',
  variant = 'secondary',
  size = 'md',
  disabled,
  title,
  className = '',
}: ButtonProps) {
  const variants: Record<string, string> = {
    primary: 'bg-navy-800 text-white border-navy-800 hover:bg-navy-900 disabled:bg-navy-300 disabled:border-navy-300',
    secondary: 'bg-white text-navy-700 border-navy-200 hover:bg-navy-50 disabled:text-navy-300',
    danger: 'bg-white text-danger-600 border-danger-600/40 hover:bg-danger-50 disabled:text-navy-300',
    ghost: 'bg-transparent text-navy-600 border-transparent hover:bg-navy-50',
  }
  const sizes: Record<string, string> = {
    sm: 'px-2.5 py-1 text-xs',
    md: 'px-3.5 py-1.5 text-[13px]',
  }
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`inline-flex items-center gap-1.5 rounded border font-medium transition-colors disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {children}
    </button>
  )
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-navy-600">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-navy-400">{hint}</span>}
    </label>
  )
}

const controlClass =
  'w-full rounded border border-navy-200 bg-white px-2.5 py-1.5 text-[13px] text-navy-900 outline-none focus:border-navy-500 focus:ring-1 focus:ring-navy-500'

export function TextInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${controlClass} ${props.className ?? ''}`} />
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${controlClass} ${props.className ?? ''}`} />
}

/* ------------------------------------------------------------------ status */

type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral'

const toneClass: Record<Tone, string> = {
  ok: 'bg-ok-50 text-ok-700 border-ok-600/25',
  warn: 'bg-warn-50 text-warn-700 border-warn-600/25',
  danger: 'bg-danger-50 text-danger-700 border-danger-600/25',
  info: 'bg-info-50 text-info-700 border-info-600/25',
  neutral: 'bg-navy-50 text-navy-600 border-navy-200',
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap ${toneClass[tone]}`}
    >
      {children}
    </span>
  )
}

/** Maps domain vocabulary onto the four status colours. */
export function statusTone(value?: string): Tone {
  switch ((value ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'PUBLISHED':
    case 'APPROVED':
    case 'APPLIED':
    case 'RESOLVED':
    case 'GOOD':
    case 'BALANCED':
      return 'ok'
    case 'MAINTENANCE':
    case 'PENDING':
    case 'PROPOSED':
    case 'DRAFT':
    case 'NEAR LIMIT':
    case 'ACCEPTABLE':
    case 'RESCHEDULED':
    case 'MEDIUM':
      return 'warn'
    case 'CRITICAL':
    case 'HIGH':
    case 'OVERLOADED':
    case 'REJECTED':
    case 'CANCELLED':
    case 'UNEVEN':
      return 'danger'
    case 'SCHEDULED':
    case 'UNDERUTILISED':
    case 'LOW':
      return 'info'
    default:
      return 'neutral'
  }
}

export function StatCard({
  label,
  value,
  sub,
  tone = 'neutral',
}: {
  label: string
  value: ReactNode
  sub?: string
  tone?: Tone
}) {
  const accent: Record<Tone, string> = {
    ok: 'border-l-ok-600',
    warn: 'border-l-warn-600',
    danger: 'border-l-danger-600',
    info: 'border-l-info-600',
    neutral: 'border-l-navy-400',
  }
  return (
    <div className={`rounded border border-navy-100 border-l-4 bg-white px-4 py-3 ${accent[tone]}`}>
      <div className="text-[11px] font-medium tracking-wide text-navy-500 uppercase">{label}</div>
      <div className="tabular mt-1 text-2xl font-semibold text-navy-900">{value}</div>
      {sub && <div className="mt-0.5 text-xs text-navy-500">{sub}</div>}
    </div>
  )
}

/* ------------------------------------------------------------------ tables */

export function Table({ head, children }: { head: ReactNode; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] border-collapse text-[13px]">
        <thead className="bg-navy-50 text-left text-[11px] tracking-wide text-navy-600 uppercase">
          {head}
        </thead>
        <tbody className="divide-y divide-navy-100">{children}</tbody>
      </table>
    </div>
  )
}

export function Th({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return <th className={`px-3 py-2 font-semibold whitespace-nowrap ${className}`}>{children}</th>
}

export function Td({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return <td className={`px-3 py-2 align-middle text-navy-800 ${className}`}>{children}</td>
}

/* ------------------------------------------------------------------- state */

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-navy-500">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-navy-200 border-t-navy-600" />
      {label}…
    </div>
  )
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="px-4 py-12 text-center">
      <p className="text-sm font-medium text-navy-700">{title}</p>
      {hint && <p className="mx-auto mt-1 max-w-md text-[13px] text-navy-500">{hint}</p>}
    </div>
  )
}

export function ErrorNote({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded border border-danger-600/25 bg-danger-50 px-4 py-3 text-[13px] text-danger-700">
      <span>{message}</span>
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function InfoNote({ children, tone = 'info' }: { children: ReactNode; tone?: Tone }) {
  return (
    <div className={`rounded border px-4 py-3 text-[13px] ${toneClass[tone]}`}>{children}</div>
  )
}

/** Horizontal meter used for utilisation and workload readouts. */
export function Meter({ percent, tone = 'info' }: { percent: number; tone?: Tone }) {
  const fill: Record<Tone, string> = {
    ok: 'bg-ok-600',
    warn: 'bg-warn-600',
    danger: 'bg-danger-600',
    info: 'bg-info-600',
    neutral: 'bg-navy-400',
  }
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-navy-100">
      <div
        className={`h-full rounded-full ${fill[tone]}`}
        style={{ width: `${Math.max(0, Math.min(100, percent))}%` }}
      />
    </div>
  )
}
