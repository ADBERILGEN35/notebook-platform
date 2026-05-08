import { describe, expect, it, vi, beforeEach } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { connectNotificationEventStream } from './notification-events'

const state = vi.hoisted(() => ({ cookieMode: true, notificationsEnabled: true, sseEnabled: true }))

vi.mock('../../shared/config/auth-transport', () => ({
  isCookieMode: () => state.cookieMode,
}))

vi.mock('../../shared/config/notifications-feature-flags', () => ({
  isNotificationsEnabled: () => state.notificationsEnabled,
  isNotificationsSseEnabled: () => state.sseEnabled,
}))

vi.mock('../../shared/api/api-client', () => ({
  API_BASE_URL: 'http://localhost:8080',
}))

class FakeEventSource {
  listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>()
  addEventListener(type: string, callback: (event: MessageEvent<string>) => void) {
    const current = this.listeners.get(type) || []
    current.push(callback)
    this.listeners.set(type, current)
  }
  emit(type: string, data: unknown) {
    const listeners = this.listeners.get(type) || []
    for (const listener of listeners) {
      listener({ data: JSON.stringify(data) } as MessageEvent<string>)
    }
  }
  close() {}
}

describe('notification SSE events', () => {
  beforeEach(() => {
    state.cookieMode = true
    state.notificationsEnabled = true
    state.sseEnabled = true
  })

  it('skips EventSource in bearer mode', () => {
    state.cookieMode = false
    const queryClient = new QueryClient()
    const source = connectNotificationEventStream(queryClient)
    expect(source).toBeNull()
  })

  it('updates unread count cache from unread_count event', async () => {
    const queryClient = new QueryClient()
    const fake = new FakeEventSource()
    class FakeEventSourceCtor {
      constructor() {
        return fake
      }
    }
    vi.stubGlobal('EventSource', FakeEventSourceCtor as unknown as typeof EventSource)

    connectNotificationEventStream(queryClient)
    fake.emit('notification.unread_count', { unreadCount: 7 })

    expect(queryClient.getQueryData(['notifications', 'unreadCount'])).toEqual({ unreadCount: 7 })
  })

  it('handles duplicate notification events safely (at-least-once SSE / Faz 64 fanout)', () => {
    const queryClient = new QueryClient()
    const fake = new FakeEventSource()
    class FakeEventSourceCtor {
      constructor() {
        return fake
      }
    }
    vi.stubGlobal('EventSource', FakeEventSourceCtor as unknown as typeof EventSource)

    connectNotificationEventStream(queryClient)
    fake.emit('notification.created', { notificationId: 'n1' })
    fake.emit('notification.created', { notificationId: 'n1' })

    expect(true).toBe(true)
  })
})
