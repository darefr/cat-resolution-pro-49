'use client'

import { ArrowUpRight, ChevronRight, Shield, Smartphone, LockKeyhole, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { Field, FieldContent, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { useCat } from './app-context'

export function SettingsScreen() {
  const { device, run, busy, openSetup, language, setLanguage, t } = useCat()
  return (
    <section className="page-content page-enter">
      <div className="page-heading"><h1 className="text-balance">{t('Make it yours.', 'Do seu jeito.')}</h1><p>{t('A little setup. A lot more control.', 'Uma configuração simples. Muito mais controle.')}</p></div>
      <section className="settings-section"><h2 className="eyebrow font-serif">{t('DEVICE & CONNECTION', 'DISPOSITIVO E CONEXÃO')}</h2>
        <div className="settings-row"><div className="flex items-center gap-3"><Smartphone className="size-5 text-primary" /><div><strong>{device.deviceName}</strong><p>{device.native ? `Android ${device.androidVersion}` : t('Android 11–14 app required', 'Requer aplicativo Android 11–14')}</p></div></div></div>
        <button className="settings-row w-full text-left" onClick={openSetup}><div className="flex items-center gap-3"><Shield className="size-5 text-primary" /><div><strong>Shizuku</strong><p>{device.shizuku === 'ready' ? t('Authorized and connected', 'Autorizado e conectado') : t('Setup and permissions', 'Configuração e permissões')}</p></div></div><ChevronRight className="size-5 text-muted-foreground" /></button>
      </section>
      <section className="settings-section"><h2 className="eyebrow font-serif">{t('SAFETY FIRST', 'SEGURANÇA PRIMEIRO')}</h2><FieldGroup>
        <Field orientation="horizontal"><FieldContent><FieldLabel htmlFor="restore-on-lock">{t('Restore when the screen locks', 'Restaurar ao bloquear a tela')}</FieldLabel><FieldDescription>{t('A running controller restores your original display on screen-off.', 'O controle ativo restaura a tela original ao bloquear.')}</FieldDescription></FieldContent><Switch id="restore-on-lock" checked={device.preferences.restoreOnLock} disabled={busy} onCheckedChange={(value) => void run('saveSettings', { restoreOnLock: value })} /></Field>
        <Field><FieldLabel>{t('Confirmation timer', 'Tempo de confirmação')}</FieldLabel><ToggleGroup aria-label={t('Confirmation timer', 'Tempo de confirmação')} variant="outline" value={[String(device.preferences.safetyTimeout)]} onValueChange={(values) => { if (values[0]) void run('saveSettings', { safetyTimeout: Number(values[0]) }) }} disabled={busy}>{[15, 20, 30].map((seconds) => <ToggleGroupItem className="h-10" key={seconds} value={String(seconds)}>{seconds} {t('seconds', 'segundos')}</ToggleGroupItem>)}</ToggleGroup><FieldDescription>{t('Unconfirmed changes revert automatically. The timer runs outside the app UI.', 'Alterações não confirmadas são revertidas. O timer funciona fora da interface.')}</FieldDescription></Field>
      </FieldGroup></section>
      <section className="settings-section"><h2 className="eyebrow font-serif">{t('PREFERENCES', 'PREFERÊNCIAS')}</h2><Field><FieldLabel>{t('Language', 'Idioma')}</FieldLabel><ToggleGroup aria-label={t('Language', 'Idioma')} variant="outline" value={[language]} onValueChange={(values) => { if (values[0]) setLanguage(values[0] as 'en' | 'pt') }}><ToggleGroupItem className="h-10" value="en">English</ToggleGroupItem><ToggleGroupItem className="h-10" value="pt">Português</ToggleGroupItem></ToggleGroup></Field></section>
      <div className="flex flex-col gap-3"><Button variant="outline" className="h-11" onClick={() => void run('notificationPermission')}><Info data-icon="inline-start" />{t('Notification settings', 'Configurações de notificação')}<ArrowUpRight data-icon="inline-end" /></Button><Button variant="outline" className="h-11" onClick={() => void run('restore', {}, t('Original resolution restored.', 'Resolução original restaurada.'))}>{t('Restore native resolution', 'Restaurar resolução nativa')}</Button></div>
      <div className="pt-6"><Alert><LockKeyhole /><AlertTitle>{t('On your phone. Not in the cloud.', 'No seu celular. Não na nuvem.')}</AlertTitle><AlertDescription>{t('The Android app works offline. It does not need an account, collect gameplay, or modify game files. Presets and history stay in its private SQLite database.', 'O aplicativo funciona offline. Não exige conta, coleta partidas ou modifica arquivos de jogos. Presets e histórico ficam no banco SQLite privado.')}</AlertDescription></Alert></div>
      <p className="native-note pt-6 text-center">Cat Resolution · 1.0.0 test build<br />{t('Independent recreation · not affiliated with the original app', 'Recriação independente · sem vínculo com o app original')}</p>
    </section>
  )
}
