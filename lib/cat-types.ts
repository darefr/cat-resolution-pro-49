export type Screen = 'home' | 'presets' | 'overlay' | 'history' | 'settings'

export type DisplayState = {
  nativeWidth: number
  nativeHeight: number
  currentWidth: number
  currentHeight: number
  density: number
  active: boolean
  pending: boolean
  remainingSeconds: number
  revision: number
  lastOutcome: string
  error?: string
}

export type Preset = { id: number; name: string; multiplier: number }
export type HistoryEntry = { id: number; action: string; multiplier: number; detail: string; createdAt: number }
export type Preferences = { overlayOpacity: number; overlaySize: number; restoreOnLock: boolean; safetyTimeout: number; language: 'en' | 'pt' }

export type DeviceState = {
  native: boolean
  shizuku: 'unavailable' | 'stopped' | 'permission' | 'connecting' | 'ready'
  overlayPermission: boolean
  overlayRunning: boolean
  notificationPermission: boolean
  deviceName: string
  androidVersion: string
  display: DisplayState | null
  presets: Preset[]
  history: HistoryEntry[]
  preferences: Preferences
  error?: string
}

export const builtInPresets: Preset[] = [
  { id: -1, name: 'Subtle', multiplier: 1.05 },
  { id: -2, name: 'Balanced', multiplier: 1.1 },
  { id: -3, name: 'Precision', multiplier: 1.11 },
  { id: -4, name: 'Wide', multiplier: 1.15 },
]

export const initialDeviceState: DeviceState = {
  native: false,
  shizuku: 'unavailable',
  overlayPermission: false,
  overlayRunning: false,
  notificationPermission: false,
  deviceName: 'Browser preview',
  androidVersion: '',
  display: null,
  presets: [],
  history: [],
  preferences: { overlayOpacity: 90, overlaySize: 56, restoreOnLock: true, safetyTimeout: 15, language: 'en' },
}

export function projectedSize(width: number, height: number, multiplier: number) {
  const value = Math.min(1.3, Math.max(1, multiplier))
  return width > height
    ? { width: Math.round(width * value), height }
    : { width, height: Math.round(height * value) }
}

export function formatMultiplier(value: number, language: 'en' | 'pt' = 'en') {
  return `${value.toFixed(2).replace('.', language === 'pt' ? ',' : '.')}×`
}
