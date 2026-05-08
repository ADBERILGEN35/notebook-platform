import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import {
  archiveNotification,
  fetchUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './notifications-api'
import type { NotificationsQueryFilters } from './notifications-types'
import { connectNotificationEventStream } from './notification-events'
import { useAuthStore } from '../auth/auth-store'

export const notificationsKeys = {
  list: (filters: NotificationsQueryFilters) => ['notifications', filters] as const,
  unreadCount: () => ['notifications', 'unreadCount'] as const,
}

export function useNotifications(filters: NotificationsQueryFilters, enabled = true) {
  return useQuery({
    queryKey: notificationsKeys.list(filters),
    queryFn: () => listNotifications(filters),
    enabled,
  })
}

export function useUnreadNotificationCount(enabled = true) {
  return useQuery({
    queryKey: notificationsKeys.unreadCount(),
    queryFn: fetchUnreadCount,
    enabled,
    refetchInterval: 30_000,
  })
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useArchiveNotification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: archiveNotification,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useNotificationEventStream() {
  const queryClient = useQueryClient()
  const user = useAuthStore((state) => state.user)

  useEffect(() => {
    if (!user) return
    const source = connectNotificationEventStream(queryClient)
    return () => {
      source?.close()
    }
  }, [queryClient, user?.id])
}
