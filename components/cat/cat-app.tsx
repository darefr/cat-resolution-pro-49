'use client'

import { useEffect, useRef, useState } from 'react'
import { Home, SlidersHorizontal, Scan, History, Settings, Smartphone, ShieldCheck, TriangleAlert, ArrowUpRight } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Toaster } from '@/components/ui/sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useDevice, nativeRequest } from '@/lib/native-bridge'
import type { Screen } from '@/lib/cat-types'
import { CatContext } from './app-context'
import { HomeScreen } from './home-screen'
import { PresetsScreen } from './presets-screen'
import { OverlayScreen } from './overlay-screen'
import { HistoryScreen } from './history-screen'
import { SettingsScreen } from './settings-screen'
import { SetupDialog } from './setup-dialog'

const navigation = [
  { id: 'home' as const, icon: Home, en: 'Home', pt: 'Início' },
  { id: 'presets' as const, icon: SlidersHorizontal, en: 'Presets', pt: 'Presets' },
  { id: 'overlay' as const, icon: Scan, en: 'Overlay', pt: 'Overlay' },
  { id: 'history' as const, icon: History, en: 'History', pt: 'Histórico' },
  { id: 'settings' as const, icon: Settings, en: 'Settings', pt: 'Config.' },
]

export function CatApp() {
  const { device, error, refresh } = useDevice()
  const [screen, setScreen] = useState<Screen>('home')
  const [multiplier, setMultiplier] = useState(1.16)
  const [busy, setBusy] = useState(false)
  const [setupOpen, setSetupOpen] = useState(false)
  const [browserLanguage, setBrowserLanguage] = useState<'en' | 'pt'>('en')
  const [ready, setReady] = useState(false)
  const operation = useRef(false)
  const mainRef = useRef<HTMLElement>(null)
  const language = device.native ? device.preferences.language : browserLanguage
  const t = (en: string, pt: string) => language === 'pt' ? pt : en

  useEffect(() => { setReady(true) }, [])
  useEffect(() => { document.documentElement.lang = language === 'pt' ? 'pt-BR' : 'en' }, [language])

  async function run(action: string, payload: Record<string, unknown> = {}, message?: string) {
    if (!device.native) { setSetupOpen(true); return false }
    if (operation.current) return false
    operation.current = true
    setBusy(true)
    try {
      await nativeRequest(action, payload)
      await refresh()
      if (message) toast.success(message)
      return true
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('Unable to complete this action.', 'Não foi possível concluir a ação.'))
      void refresh()
      return false
    } finally {
      operation.current = false
      setBusy(false)
    }
  }

  function navigate(next: Screen) {
    setScreen(next)
    window.scrollTo({ top: 0, behavior: 'instant' })
    requestAnimationFrame(() => mainRef.current?.focus({ preventScroll: true }))
  }

  const context = {
    device, multiplier, setMultiplier, busy, run, navigate, language,
    openSetup: () => setSetupOpen(true),
    setLanguage: (value: 'en' | 'pt') => { if (device.native) void run('saveSettings', { language: value }); else setBrowserLanguage(value) },
  }

  return (
    <CatContext.Provider value={context}>
      <div className="app-shell" data-app-ready={ready}>
        <header className="app-header flex items-center justify-between">
          <button className="brand-lockup" aria-label="Cat Resolution home" onClick={() => navigate('home')}><img src="/images/cat-emblem.png" className="brand-image" alt="" width={42} height={42} /><span className="brand-name font-serif">CAT RESOLUTION</span><Badge>PRO</Badge></button>
          <div className="flex items-center gap-2"><Button variant="outline" size="icon" className="header-button" aria-label={t('Device connection', 'Conexão do dispositivo')} onClick={() => setSetupOpen(true)}>{device.shizuku === 'ready' ? <ShieldCheck /> : <Smartphone />}</Button><Button variant="ghost" size="icon" className="header-button" aria-label={t('Open settings', 'Abrir configurações')} onClick={() => navigate('settings')}><Settings /></Button></div>
        </header>
        {!device.native && <div className="preview-banner"><span>{t('Web preview · Android 11–14', 'Prévia web · Android 11–14')}</span><button onClick={() => setSetupOpen(true)}>{t('Get the app', 'Obter o app')}<ArrowUpRight aria-hidden="true" className="ml-1 inline size-4" /></button></div>}
        {(error || device.error || device.display?.error) && <div className="p-4"><Alert><TriangleAlert /><AlertTitle>{t('Device connection needs attention', 'Verifique a conexão')}</AlertTitle><AlertDescription>{error?.message ?? device.error ?? device.display?.error}</AlertDescription></Alert></div>}
        <main ref={mainRef} tabIndex={-1} className="outline-none" id="main-content">
          {screen === 'home' && <HomeScreen />}
          {screen === 'presets' && <PresetsScreen />}
          {screen === 'overlay' && <OverlayScreen />}
          {screen === 'history' && <HistoryScreen />}
          {screen === 'settings' && <SettingsScreen />}
        </main>
        <nav className="app-navigation" aria-label={t('Main navigation', 'Navegação principal')}>{navigation.map(({ id, icon: Icon, en, pt }) => <button key={id} className="nav-item" aria-current={screen === id ? 'page' : undefined} onClick={() => navigate(id)}><span className="nav-icon"><Icon aria-hidden="true" /></span><span>{t(en, pt)}</span></button>)}</nav>
      </div>
      <SetupDialog open={setupOpen} onOpenChange={setSetupOpen} />
      <Dialog open={!!device.display?.pending} onOpenChange={() => undefined} disablePointerDismissal><DialogContent showCloseButton={false}><DialogHeader><DialogTitle>{t('Keep this resolution?', 'Manter esta resolução?')}</DialogTitle><DialogDescription>{t('Check that the display and touch controls work correctly. Without confirmation, your original display is restored.', 'Verifique a tela e os controles de toque. Sem confirmação, a resolução original será restaurada.')}</DialogDescription></DialogHeader><p className="py-2 text-center font-serif text-6xl font-bold italic text-primary" role="timer">{device.display?.remainingSeconds ?? 0}<span className="text-2xl">s</span></p><div className="flex gap-2"><Button disabled={busy} className="h-12 flex-1" onClick={() => void run('confirm', {}, t('Resolution confirmed.', 'Resolução confirmada.'))}>{t('Keep changes', 'Manter alterações')}</Button><Button variant="outline" disabled={busy} className="h-12 flex-1" onClick={() => void run('revert')}>{t('Revert now', 'Reverter agora')}</Button></div></DialogContent></Dialog>
      <Toaster theme="light" position="top-center" closeButton />
    </CatContext.Provider>
  )
}
