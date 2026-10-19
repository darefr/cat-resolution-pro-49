'use client'

import useSWR from 'swr'
import { initialDeviceState, type DeviceState } from '@/lib/cat-types'

declare global {
  interface Window {
    CatNative?: { request: (payload: string) => void }
  }
}

type Reply = { id: string; result?: unknown; error?: string }
const requests = new Map<string, { resolve: (value: unknown) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }>()
let listening = false
let requestSequence = 0

export function isAndroidApp() {
  return typeof window !== 'undefined' && !!window.CatNative
}

export async function nativeRequest<T = DeviceState>(action: string, payload: Record<string, unknown> = {}): Promise<T> {
  if (!isAndroidApp()) throw new Error('This feature needs the Android app. Install the APK and authorize Shizuku first.')
  if (!listening) {
    window.addEventListener('cat-native-result', ((event: CustomEvent<Reply>) => {
      const pending = requests.get(event.detail.id)
      if (!pending) return
      clearTimeout(pending.timer)
      requests.delete(event.detail.id)
      if (event.detail.error) pending.reject(new Error(event.detail.error))
      else pending.resolve(event.detail.result)
    }) as EventListener)
    listening = true
  }
  const id = `${Date.now()}-${++requestSequence}`
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      requests.delete(id)
      reject(new Error('Android did not respond. Reopen the app and check that Shizuku is running.'))
    }, 25000)
    requests.set(id, { resolve: resolve as (value: unknown) => void, reject, timer })
    try {
      window.CatNative!.request(JSON.stringify({ id, action, payload }))
    } catch (error) {
      clearTimeout(timer)
      requests.delete(id)
      reject(error)
    }
  })
}

export function useDevice() {
  const { data, error, mutate } = useSWR<DeviceState>('cat-device', () => isAndroidApp() ? nativeRequest('state') : Promise.resolve(initialDeviceState), {
    fallbackData: initialDeviceState,
    refreshInterval: (state) => state?.native ? (state.display?.pending ? 1000 : 4000) : 0,
    revalidateOnFocus: true,
    shouldRetryOnError: false,
    dedupingInterval: 400,
  })
  return { device: data ?? initialDeviceState, error, refresh: mutate }
}
