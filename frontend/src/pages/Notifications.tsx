import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, formatDateTime } from '../lib/api'
import type { Notification } from '../lib/types'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  PageHeader,
  Spinner,
} from '../components/ui'

const severityTone: Record<string, 'ok' | 'warn' | 'danger' | 'info'> = {
  SUCCESS: 'ok',
  WARNING: 'warn',
  ERROR: 'danger',
  INFO: 'info',
}

export default function Notifications() {
  const queryClient = useQueryClient()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api<Notification[]>('/notifications'),
  })

  const markAll = useMutation({
    mutationFn: () => api('/notifications/read-all', { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] })
    },
  })

  const unread = (data ?? []).filter((n) => !n.read).length

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Schedule changes, publications and conflicts affecting you."
        actions={
          unread > 0 ? (
            <Button onClick={() => markAll.mutate()} disabled={markAll.isPending}>
              Mark all as read
            </Button>
          ) : undefined
        }
      />

      {isLoading && <Spinner label="Loading notifications" />}
      {error && <ErrorNote message={(error as Error).message} onRetry={() => refetch()} />}

      {data && data.length === 0 && (
        <Card>
          <EmptyState
            title="No notifications"
            hint="You will be notified when a timetable is published or one of your practicals moves."
          />
        </Card>
      )}

      {data && data.length > 0 && (
        <div className="space-y-2">
          {data.map((notification) => (
            <div
              key={notification.id}
              className={`rounded border bg-white px-4 py-3 ${
                notification.read ? 'border-navy-100' : 'border-l-4 border-navy-100 border-l-info-600'
              }`}
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-[13px] font-semibold text-navy-900">
                    {notification.title}
                  </span>
                  <Badge tone={severityTone[notification.severity] ?? 'neutral'}>
                    {notification.category.replace(/_/g, ' ').toLowerCase()}
                  </Badge>
                  {!notification.read && <Badge tone="info">New</Badge>}
                </div>
                <span className="text-[12px] whitespace-nowrap text-navy-500">
                  {formatDateTime(notification.createdAt)}
                </span>
              </div>
              <p className="mt-1 text-[13px] leading-relaxed text-navy-700">{notification.body}</p>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
