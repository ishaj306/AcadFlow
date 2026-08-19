import { Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import Shell from './components/Shell'
import { useAuth } from './lib/auth'
import { Spinner } from './components/ui'
import type { Role } from './lib/types'

import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Students from './pages/Students'
import FacultyPage from './pages/Faculty'
import Subjects from './pages/Subjects'
import Laboratories from './pages/Laboratories'
import Batches from './pages/Batches'
import Timetable from './pages/Timetable'
import WhatIf from './pages/WhatIf'
import Rescheduling from './pages/Rescheduling'
import Conflicts from './pages/Conflicts'
import Workload from './pages/Workload'
import Reports from './pages/Reports'
import Settings from './pages/Settings'
import Notifications from './pages/Notifications'
import StudentDashboard from './pages/student/StudentDashboard'
import StudentTimetable from './pages/student/StudentTimetable'
import FacultyDashboard from './pages/faculty/FacultyDashboard'
import FacultyTimetable from './pages/faculty/FacultyTimetable'
import FacultyWorkload from './pages/faculty/FacultyWorkload'
import FacultyAvailability from './pages/faculty/FacultyAvailability'

/** Landing route per role, so each user starts where their work is. */
function homeFor(role: Role): string {
  switch (role) {
    case 'STUDENT':
      return '/student/dashboard'
    case 'FACULTY':
      return '/faculty/dashboard'
    default:
      return '/dashboard'
  }
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <Spinner label="Restoring session" />
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />
  return <>{children}</>
}

function Home() {
  const { user, loading } = useAuth()
  if (loading) return <Spinner label="Restoring session" />
  return <Navigate to={user ? homeFor(user.role) : '/login'} replace />
}

const STAFF: Role[] = ['ADMIN', 'HOD']

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route path="/" element={<Home />} />

        <Route path="/dashboard" element={<RequireRole roles={STAFF}><Dashboard /></RequireRole>} />
        <Route path="/students" element={<RequireRole roles={STAFF}><Students /></RequireRole>} />
        <Route path="/faculty" element={<RequireRole roles={STAFF}><FacultyPage /></RequireRole>} />
        <Route path="/subjects" element={<RequireRole roles={STAFF}><Subjects /></RequireRole>} />
        <Route path="/laboratories" element={<RequireRole roles={STAFF}><Laboratories /></RequireRole>} />
        <Route path="/batches" element={<RequireRole roles={STAFF}><Batches /></RequireRole>} />
        <Route path="/timetable" element={<RequireRole roles={STAFF}><Timetable /></RequireRole>} />
        <Route path="/what-if" element={<RequireRole roles={STAFF}><WhatIf /></RequireRole>} />
        <Route path="/rescheduling" element={<RequireRole roles={STAFF}><Rescheduling /></RequireRole>} />
        <Route path="/conflicts" element={<RequireRole roles={STAFF}><Conflicts /></RequireRole>} />
        <Route path="/workload" element={<RequireRole roles={STAFF}><Workload /></RequireRole>} />
        <Route path="/reports" element={<RequireRole roles={STAFF}><Reports /></RequireRole>} />
        <Route path="/settings" element={<RequireRole roles={['ADMIN']}><Settings /></RequireRole>} />

        <Route path="/notifications" element={<Notifications />} />

        <Route
          path="/student/dashboard"
          element={<RequireRole roles={['STUDENT', 'ADMIN']}><StudentDashboard /></RequireRole>}
        />
        <Route
          path="/student/timetable"
          element={<RequireRole roles={['STUDENT', 'ADMIN']}><StudentTimetable /></RequireRole>}
        />

        <Route
          path="/faculty/dashboard"
          element={<RequireRole roles={['FACULTY', 'ADMIN']}><FacultyDashboard /></RequireRole>}
        />
        <Route
          path="/faculty/timetable"
          element={<RequireRole roles={['FACULTY', 'ADMIN']}><FacultyTimetable /></RequireRole>}
        />
        <Route
          path="/faculty/workload"
          element={<RequireRole roles={['FACULTY', 'ADMIN']}><FacultyWorkload /></RequireRole>}
        />
        <Route
          path="/faculty/availability"
          element={<RequireRole roles={['FACULTY', 'ADMIN']}><FacultyAvailability /></RequireRole>}
        />
      </Route>

      <Route path="*" element={<Home />} />
    </Routes>
  )
}
