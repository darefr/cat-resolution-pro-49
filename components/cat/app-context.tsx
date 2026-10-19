'use client'

import { createContext, useContext } from 'react'
import type { DeviceState, Screen } from '@/lib/cat-types'

export type CatContextValue = {
  device: DeviceState
  multiplier: number
  setMultiplier: (value: number) => void
  busy: boolean
  run: (action: string, payload?: Record<string, unknown>, message?: string) => Promise<boolean>
  openSetup: () => void
  navigate: (screen: Screen) => void
  language: 'en' | 'pt'
  setLanguage: (language: 'en' | 'pt') => void
}

export const CatContext = createContext<CatContextValue | null>(null)
export function useCat() {
  const context = useContext(CatContext)
  if (!context) throw new Error('CatContext is required')
  return { ...context, t: (english: string, portuguese: string) => context.language === 'pt' ? portuguese : english }
}
