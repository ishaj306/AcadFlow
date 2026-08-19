import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button, Field, TextInput } from '../components/ui'

export default function Login() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await signIn(username.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Sign-in failed. Please try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      {/* Identity panel - flat navy, no gradient. */}
      <div className="hidden flex-1 flex-col justify-between bg-navy-900 p-10 lg:flex">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded bg-white/10 text-sm font-bold text-white">
            PB
          </div>
          <div>
            <div className="text-[15px] font-semibold text-white">Practical Scheduler</div>
            <div className="text-[11px] tracking-wide text-navy-300 uppercase">
              College Administration System
            </div>
          </div>
        </div>

        <div className="max-w-md">
          <h1 className="text-2xl leading-snug font-semibold text-white">
            Practical batch allocation and timetable scheduling
          </h1>
          <p className="mt-3 text-sm leading-relaxed text-navy-200">
            Generates practical batches and conflict-free laboratory timetables using constraint
            optimisation, detects scheduling conflicts, and finds the least disruptive alternative
            when a session has to move.
          </p>
          <dl className="mt-8 grid grid-cols-2 gap-x-6 gap-y-4 text-navy-200">
            {[
              ['Hard constraints', 'Faculty, laboratory and student clashes'],
              ['Optimisation', 'Workload balance and student gaps'],
              ['Rescheduling', 'Ranked alternatives with scores'],
              ['Audit', 'Every approval recorded'],
            ].map(([term, detail]) => (
              <div key={term}>
                <dt className="text-[12px] font-medium text-white">{term}</dt>
                <dd className="mt-0.5 text-[12px] text-navy-300">{detail}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="text-[11px] text-navy-400">
          Timetables are produced by a deterministic constraint solver.
        </div>
      </div>

      {/* Sign-in panel */}
      <div className="flex flex-1 items-center justify-center bg-white p-6">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center gap-3 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded bg-navy-900 text-sm font-bold text-white">
              PB
            </div>
            <div className="text-[15px] font-semibold text-navy-900">Practical Scheduler</div>
          </div>

          <h2 className="text-lg font-semibold text-navy-900">Sign in</h2>
          <p className="mt-1 text-[13px] text-navy-500">
            Use your college account to continue.
          </p>

          <form onSubmit={submit} className="mt-6 space-y-4">
            <Field label="Username">
              <TextInput
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </Field>
            <Field label="Password">
              <TextInput
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </Field>

            {error && (
              <div className="rounded border border-danger-600/25 bg-danger-50 px-3 py-2 text-[13px] text-danger-700">
                {error}
              </div>
            )}

            <Button type="submit" variant="primary" disabled={busy} className="w-full justify-center">
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-8 border-t border-navy-100 pt-4 text-[12px] leading-relaxed text-navy-500">
            Faculty and students sign in with accounts created for them by the
            administrator. If this is a new installation, the administrator
            username and password are printed once in the server startup log.
          </p>
        </div>
      </div>
    </div>
  )
}
