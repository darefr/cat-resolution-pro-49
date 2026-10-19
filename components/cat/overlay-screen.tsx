'use client'

import { useState } from 'react'
import { Check, Minus, Plus, RotateCcw, GripHorizontal, ShieldCheck, ArrowUpRight, Layers } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Slider } from '@/components/ui/slider'
import { Field, FieldGroup, FieldLabel, FieldContent, FieldDescription } from '@/components/ui/field'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert'
import { formatMultiplier } from '@/lib/cat-types'
import { useCat } from './app-context'

export function OverlayScreen() {
  const { device, multiplier, setMultiplier, run, busy, openSetup, t, language } = useCat()
  const [expanded, setExpanded] = useState(true)
  const [opacity, setOpacity] = useState(device.preferences.overlayOpacity)
  return (
    <section className="page-content page-enter">
      <div className="page-heading"><h1 className="text-balance">{t('Stay in the game.', 'Continue no jogo.')}</h1><p>{t('Your display controls, always within reach.', 'Os controles da sua tela sempre ao alcance.')}</p></div>
      <div className="mb-3 flex items-center justify-between"><span className="eyebrow font-serif">{t('YOUR FLOATING CONTROLLER', 'SEU CONTROLE FLUTUANTE')}</span><Badge variant="secondary">{t('Preview', 'Prévia')}</Badge></div>
      <div className="overlay-demo">
        {expanded ? <div className="overlay-controller" style={{ opacity: opacity / 100 }}>
          <div className="flex items-center justify-between"><span className="font-serif font-bold italic text-primary">CAT CONTROL</span><button aria-label={t('Collapse preview', 'Recolher prévia')} onClick={() => setExpanded(false)} className="rounded-lg p-2 text-muted-foreground"><GripHorizontal className="size-5" /></button></div>
          <div className="flex items-center justify-between"><Button size="icon" variant="outline" aria-label={t('Decrease preview multiplier', 'Diminuir multiplicador')} onClick={() => setMultiplier(Math.max(1.01, Number((multiplier - 0.01).toFixed(2))))}><Minus /></Button><span className="overlay-value font-serif">{formatMultiplier(multiplier, language)}</span><Button size="icon" variant="outline" aria-label={t('Increase preview multiplier', 'Aumentar multiplicador')} onClick={() => setMultiplier(Math.min(1.3, Number((multiplier + 0.01).toFixed(2))))}><Plus /></Button></div>
          <div className="pt-3"><div className="flex gap-2"><Button className="h-10 flex-1" onClick={() => void run('apply', { multiplier })}><Check data-icon="inline-start" />{t('Apply', 'Aplicar')}</Button><Button className="h-10 flex-1" variant="outline" onClick={() => void run('restore')}><RotateCcw data-icon="inline-start" />{t('Restore', 'Restaurar')}</Button></div></div>
        </div> : <button className="overlay-puck" style={{ opacity: opacity / 100 }} onClick={() => setExpanded(true)} aria-label={t('Expand overlay preview', 'Expandir prévia')}><img src="/images/cat-emblem.png" alt="" width={56} height={56} /></button>}
      </div>
      <p className="native-note py-3">{t('Interactive layout preview. The Android overlay floats above other apps and can be dragged by its header.', 'Prévia interativa. No Android, o controle flutua sobre outros aplicativos e pode ser arrastado pelo cabeçalho.')}</p>
      <div className="settings-section"><FieldGroup>
        <Field orientation="horizontal"><FieldContent><FieldLabel htmlFor="overlay-enabled">{t('Floating overlay', 'Overlay flutuante')}</FieldLabel><FieldDescription>{t('Show the controller over other apps.', 'Mostrar o controle sobre outros aplicativos.')}</FieldDescription></FieldContent><Switch id="overlay-enabled" checked={device.overlayRunning} disabled={busy} onCheckedChange={(checked) => { if (!device.native) openSetup(); else if (checked && !device.overlayPermission) void run('overlayPermission'); else void run(checked ? 'startOverlay' : 'stopOverlay') }} /></Field>
        <Field><div className="flex justify-between"><FieldLabel htmlFor="overlay-opacity">{t('Opacity', 'Opacidade')}</FieldLabel><span className="text-sm text-muted-foreground">{opacity}%</span></div><Slider id="overlay-opacity" aria-label={t('Overlay opacity', 'Opacidade do overlay')} value={[opacity]} min={50} max={100} step={5} onValueChange={(value) => setOpacity(Array.isArray(value) ? value[0] : value)} onValueCommitted={(value) => { if (device.native) void run('saveSettings', { overlayOpacity: Array.isArray(value) ? value[0] : value }) }} /><FieldDescription>{t('Preview opacity here; the Android app saves it on your phone.', 'Visualize a opacidade. O aplicativo Android salva no celular.')}</FieldDescription></Field>
      </FieldGroup></div>
      <Button variant="outline" className="h-11 w-full" onClick={() => void run('overlayPermission')}><ShieldCheck data-icon="inline-start" />{device.overlayPermission ? t('Manage overlay permission', 'Gerenciar permissão de sobreposição') : t('Allow display over other apps', 'Permitir exibição sobre outros apps')}<ArrowUpRight data-icon="inline-end" /></Button>
      <div className="pt-5"><Alert><Layers /><AlertTitle>{t('Always in your control', 'Sempre no seu controle')}</AlertTitle><AlertDescription>{t('A persistent Android notification lets you restore the display or stop the overlay. Some games block overlays; this app does not bypass those restrictions.', 'Uma notificação permite restaurar a tela ou parar o overlay. Alguns jogos bloqueiam sobreposições; o aplicativo não contorna essas restrições.')}</AlertDescription></Alert></div>
    </section>
  )
}
