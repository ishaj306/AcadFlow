import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Button } from './ui'
import { Icon } from './icons'

/**
 * Centred dialog used for every create/edit form. Deliberately plain: a titled
 * panel, the form, and a footer with the primary action on the right.
 */
export default function Modal({
  title,
  description,
  open,
  onClose,
  onSubmit,
  submitLabel = 'Save',
  submitting,
  error,
  width = 'max-w-lg',
  children,
}: {
  title: string
  description?: string
  open: boolean
  onClose: () => void
  onSubmit?: () => void
  submitLabel?: string
  submitting?: boolean
  error?: string | null
  width?: string
  children: ReactNode
}) {
  // Escape closes, and the page behind must not scroll while it is open.
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-navy-900/40 p-4 sm:items-center">
      <div className={`w-full ${width} rounded border border-navy-200 bg-white shadow-lg`}>
        <header className="flex items-start justify-between gap-3 border-b border-navy-100 px-4 py-3">
          <div>
            <h2 className="text-sm font-semibold text-navy-900">{title}</h2>
            {description && <p className="mt-0.5 text-xs text-navy-500">{description}</p>}
          </div>
          <button
            onClick={onClose}
            className="rounded p-1 text-navy-400 hover:bg-navy-50 hover:text-navy-700"
            aria-label="Close"
          >
            <Icon name="close" />
          </button>
        </header>

        <form
          onSubmit={(event) => {
            event.preventDefault()
            onSubmit?.()
          }}
        >
          <div className="max-h-[70vh] overflow-y-auto px-4 py-4">
            {error && (
              <div className="mb-3 rounded border border-danger-600/25 bg-danger-50 px-3 py-2 text-[13px] text-danger-700">
                {error}
              </div>
            )}
            {children}
          </div>

          <footer className="flex items-center justify-end gap-2 border-t border-navy-100 bg-navy-50/60 px-4 py-3">
            <Button onClick={onClose} type="button">
              Cancel
            </Button>
            {onSubmit && (
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? 'Saving…' : submitLabel}
              </Button>
            )}
          </footer>
        </form>
      </div>
    </div>
  )
}

/** Confirmation dialog for destructive actions. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Delete',
  busy,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  busy?: boolean
  error?: string | null
  onConfirm: () => void
  onClose: () => void
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-navy-900/40 p-4">
      <div className="w-full max-w-md rounded border border-navy-200 bg-white shadow-lg">
        <div className="px-4 py-4">
          <h2 className="text-sm font-semibold text-navy-900">{title}</h2>
          <p className="mt-1.5 text-[13px] text-navy-600">{message}</p>
          {error && (
            <div className="mt-3 rounded border border-danger-600/25 bg-danger-50 px-3 py-2 text-[13px] text-danger-700">
              {error}
            </div>
          )}
        </div>
        <div className="flex items-center justify-end gap-2 border-t border-navy-100 bg-navy-50/60 px-4 py-3">
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="danger" onClick={onConfirm} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
