import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { AcademicTerm, Role } from '../lib/types'
import { Icon } from './icons'
import AssistantDrawer from './AssistantDrawer'

interface NavItem {
  to: string
  label: string
  icon: string
  roles: Role[]
}

const NAV: { section: string; items: NavItem[] }[] = [
  {
    section: 'Overview',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: 'dashboard', roles: ['ADMIN', 'HOD'] },
      { to: '/student/dashboard', label: 'My Dashboard', icon: 'dashboard', roles: ['STUDENT'] },
      { to: '/faculty/dashboard', label: 'My Dashboard', icon: 'dashboard', roles: ['FACULTY'] },
    ],
  },
  {
    section: 'Master data',
    items: [
      { to: '/students', label: 'Students', icon: 'students', roles: ['ADMIN', 'HOD'] },
      { to: '/faculty', label: 'Faculty', icon: 'faculty', roles: ['ADMIN', 'HOD'] },
      { to: '/subjects', label: 'Subjects', icon: 'subjects', roles: ['ADMIN', 'HOD'] },
      { to: '/laboratories', label: 'Laboratories', icon: 'labs', roles: ['ADMIN', 'HOD'] },
      { to: '/fixed-lectures', label: 'Fixed Lectures', icon: 'clock', roles: ['ADMIN', 'HOD'] },
    ],
  },
  {
    section: 'Scheduling',
    items: [
      { to: '/batches', label: 'Practical Batches', icon: 'batches', roles: ['ADMIN', 'HOD'] },
      { to: '/timetable', label: 'Timetable', icon: 'timetable', roles: ['ADMIN', 'HOD'] },
      { to: '/what-if', label: 'What-if Simulator', icon: 'settings', roles: ['ADMIN', 'HOD'] },
      { to: '/rescheduling', label: 'Rescheduling', icon: 'reschedule', roles: ['ADMIN', 'HOD'] },
      { to: '/conflicts', label: 'Conflicts', icon: 'conflicts', roles: ['ADMIN', 'HOD'] },
      { to: '/workload', label: 'Faculty Workload', icon: 'workload', roles: ['ADMIN', 'HOD'] },
    ],
  },
  {
    section: 'My schedule',
    items: [
      { to: '/student/timetable', label: 'My Timetable', icon: 'timetable', roles: ['STUDENT'] },
      { to: '/faculty/timetable', label: 'My Timetable', icon: 'timetable', roles: ['FACULTY'] },
      { to: '/faculty/workload', label: 'My Workload', icon: 'workload', roles: ['FACULTY'] },
      { to: '/faculty/availability', label: 'My Availability', icon: 'clock', roles: ['FACULTY'] },
    ],
  },
  {
    section: 'Administration',
    items: [
      { to: '/reports', label: 'Reports', icon: 'reports', roles: ['ADMIN', 'HOD'] },
      {
        to: '/notifications',
        label: 'Notifications',
        icon: 'bell',
        roles: ['ADMIN', 'HOD', 'FACULTY', 'STUDENT'],
      },
      { to: '/settings', label: 'Settings', icon: 'settings', roles: ['ADMIN'] },
    ],
  },
]

export default function Shell() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [assistantOpen, setAssistantOpen] = useState(false)

  const { data: term } = useQuery({
    queryKey: ['term', 'current'],
    queryFn: () => api<AcademicTerm>('/config/terms/current'),
    retry: false,
  })

  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ count: number }>('/notifications/unread-count'),
    refetchInterval: 60_000,
  })

  const sections = NAV.map((section) => ({
    ...section,
    items: section.items.filter((item) => user && item.roles.includes(user.role)),
  })).filter((section) => section.items.length > 0)

  const initials = (user?.fullName ?? '')
    .split(' ')
    .filter((part) => /[A-Za-z]/.test(part[0] ?? ''))
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <div className="flex min-h-screen">
      {/* ------------------------------------------------------- sidebar */}
      <aside
        className={`${menuOpen ? 'block' : 'hidden'} fixed inset-y-0 left-0 z-30 w-60 shrink-0 overflow-y-auto bg-navy-900 md:static md:block no-print`}
      >
        <div className="flex h-14 items-center gap-2 border-b border-white/10 px-4">
          <div className="flex h-7 w-7 items-center justify-center rounded bg-white/10 text-xs font-bold text-white">
            PB
          </div>
          <div className="leading-tight">
            <div className="text-[13px] font-semibold text-white">Practical Scheduler</div>
            <div className="text-[10px] tracking-wide text-navy-300 uppercase">
              College Administration
            </div>
          </div>
        </div>

        <nav className="px-2 py-3">
          {sections.map((section) => (
            <div key={section.section} className="mb-4">
              <div className="px-2 pb-1 text-[10px] font-semibold tracking-wider text-navy-400 uppercase">
                {section.section}
              </div>
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    `mb-0.5 flex items-center gap-2.5 rounded px-2.5 py-1.5 text-[13px] transition-colors ${
                      isActive
                        ? 'bg-white/10 font-medium text-white'
                        : 'text-navy-200 hover:bg-white/5 hover:text-white'
                    }`
                  }
                >
                  <Icon name={item.icon} className="h-4 w-4 shrink-0" />
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      {/* --------------------------------------------------------- main */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b border-navy-100 bg-white px-4 no-print">
          <div className="flex items-center gap-3">
            <button
              className="rounded p-1.5 text-navy-600 hover:bg-navy-50 md:hidden"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label="Toggle navigation"
            >
              <Icon name="menu" />
            </button>
            <div className="hidden items-center gap-2 text-[13px] text-navy-600 sm:flex">
              <Icon name="calendar" className="h-4 w-4 text-navy-400" />
              <span className="font-medium text-navy-800">
                {term?.academicYear ?? '—'}
              </span>
              <span className="text-navy-300">|</span>
              <span>Semester {term?.semester ?? '—'}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setAssistantOpen(true)}
              className="flex items-center gap-1.5 rounded border border-navy-200 px-2.5 py-1.5 text-[12px] font-medium text-navy-600 hover:bg-navy-50"
              aria-label="Open timetable assistant"
            >
              <Icon name="search" className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Assistant</span>
            </button>
            <button
              onClick={() => navigate('/notifications')}
              className="relative rounded p-2 text-navy-600 hover:bg-navy-50"
              aria-label="Notifications"
            >
              <Icon name="bell" />
              {!!unread?.count && (
                <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger-600 px-1 text-[10px] font-semibold text-white">
                  {unread.count > 99 ? '99+' : unread.count}
                </span>
              )}
            </button>

            <div className="flex items-center gap-2 border-l border-navy-100 pl-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-navy-100 text-xs font-semibold text-navy-700">
                {initials || '?'}
              </div>
              <div className="hidden leading-tight sm:block">
                <div className="text-[13px] font-medium text-navy-900">{user?.fullName}</div>
                <div className="text-[11px] text-navy-500">
                  {user?.role}
                  {user?.departmentName ? ` · ${user.departmentName}` : ''}
                </div>
              </div>
              <button
                onClick={() => {
                  signOut()
                  navigate('/login')
                }}
                className="ml-1 rounded p-2 text-navy-500 hover:bg-navy-50 hover:text-navy-800"
                title="Sign out"
                aria-label="Sign out"
              >
                <Icon name="logout" />
              </button>
            </div>
          </div>
        </header>

        <main className="min-w-0 flex-1 p-4 lg:p-6">
          <Outlet />
        </main>
      </div>

      <AssistantDrawer open={assistantOpen} onClose={() => setAssistantOpen(false)} />
    </div>
  )
}
