'use client'

import { ArrowUpRight, Check, LoaderCircle, RefreshCw, Shield, ShieldCheck } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { androidRelease } from '@/lib/android-release'
import { shizukuStatus } from '@/lib/shizuku-status'
import { useCat } from './app-context'

export function ShizukuConnection({ onReady }: { onReady: () => void }) {
  const { device, run, busy, t } = useCat()
  const status = shizukuStatus(device, t)
  const ready = device.shizuku === 'ready'
  const connecting = ['waiting', 'requesting', 'connecting'].includes(device.shizuku)
  const label = ready ? t('Change resolution', 'Alterar resolução')
    : device.shizuku === 'requesting' ? t('Waiting for Allow…', 'Aguardando Permitir…')
    : connecting ? t('Connecting…', 'Conectando…')
    : device.shizukuPermissionGranted ? t('Reconnect display control', 'Reconectar controle de tela')
    : device.shizuku === 'stopped' ? (device.shizukuInstalled ? t('Open Shizuku and start', 'Abrir e iniciar Shizuku') : t('Install Shizuku', 'Instalar Shizuku'))
    : device.shizuku === 'denied' ? t('Open authorization settings', 'Abrir configurações de autorização')
    : t('Authorize Shizuku', 'Autorizar Shizuku')

  return (
    <div className="flex flex-col gap-4" data-connection-state={device.shizuku}>
      <div aria-live="polite" aria-atomic="true">
        <Alert>
          {ready ? <ShieldCheck /> : <Shield />}
          <AlertTitle>{status.title}</AlertTitle>
          <AlertDescription>{device.error || status.description}</AlertDescription>
        </Alert>
      </div>
      <dl className="divide-y divide-border text-sm">
        <div className="flex items-center justify-between py-2">
          <dt>{t('Shizuku service', 'Serviço Shizuku')}</dt>
          <dd className="flex items-center gap-1.5 font-medium">{device.shizukuRunning && <Check className="size-4 text-primary" aria-hidden="true" />}{device.shizukuRunning ? t('Running', 'Em execução') : t('Not connected', 'Não conectado')}</dd>
        </div>
        <div className="flex items-center justify-between py-2">
          <dt>{t('App authorization', 'Autorização do app')}</dt>
          <dd className="flex items-center gap-1.5 font-medium">{device.shizukuPermissionGranted && <Check className="size-4 text-primary" aria-hidden="true" />}{device.shizukuPermissionGranted ? t('Allowed', 'Permitida') : device.shizuku === 'requesting' ? t('Waiting for Allow', 'Aguardando Permitir') : t('Required', 'Necessária')}</dd>
        </div>
        <div className="flex items-center justify-between py-2">
          <dt>{t('Android display access', 'Acesso à tela Android')}</dt>
          <dd className="flex items-center gap-1.5 font-medium">{ready && <Check className="size-4 text-primary" aria-hidden="true" />}{ready ? t('Verified', 'Verificado') : device.shizuku === 'connecting' ? t('Connecting', 'Conectando') : t('Not ready', 'Não disponível')}</dd>
        </div>
      </dl>
      {ready && device.display && <p className="text-sm text-muted-foreground">{t('Current resolution', 'Resolução atual')}: <strong className="text-foreground">{device.display.currentWidth} × {device.display.currentHeight}</strong></p>}
      <div className="flex flex-col gap-2">
        <Button className="h-12" disabled={busy || connecting} onClick={() => {
          if (ready) onReady()
          else void run(device.shizukuPermissionGranted ? 'reconnectShizuku' : 'requestShizuku')
        }}>
          {connecting ? <LoaderCircle data-icon="inline-start" className="animate-spin" /> : <ShieldCheck data-icon="inline-start" />}{label}
        </Button>
        {!ready && <>
          <Button variant="outline" className="h-11" disabled={busy} onClick={() => void run('recheckShizuku')}><RefreshCw data-icon="inline-start" />{t('Recheck connection', 'Verificar conexão')}</Button>
          <Button variant="ghost" className="h-11" disabled={busy} onClick={() => void run('openShizuku')}>{t('Open Shizuku', 'Abrir Shizuku')}<ArrowUpRight data-icon="inline-end" /></Button>
        </>}
      </div>
      <p className="text-sm text-muted-foreground">{device.deviceName}{device.androidVersion && ` · Android ${device.androidVersion}`} · v{device.appVersion || androidRelease.version}</p>
    </div>
  )
}
