'use client'

import { useState } from 'react'
import { ArrowRight, ArrowRightLeft, CheckCircle2, ChevronRight, CircleHelp, Expand, Monitor, Pencil, RotateCcw, Scan, Shield, ShieldCheck, SlidersHorizontal, Zap } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Slider } from '@/components/ui/slider'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { builtInPresets, formatMultiplier, projectedSize } from '@/lib/cat-types'
import { useCat } from './app-context'
import { shizukuStatus } from '@/lib/shizuku-status'

export function HomeScreen() {
  const { device, multiplier, setMultiplier, busy, run, navigate, openSetup, language, t } = useCat()
  const [projectionOpen, setProjectionOpen] = useState(false)
  const display = device.display
  const nativeWidth = display?.nativeWidth ?? 1080
  const nativeHeight = display?.nativeHeight ?? 2400
  const projected = projectedSize(nativeWidth, nativeHeight, multiplier)
  const resolutionLabel = (w: number, h: number) => `${Math.max(w, h)} × ${Math.min(w, h)}`
  const ready = device.shizuku === 'ready'
  const connection = shizukuStatus(device, t)
  const awaitingDevice = device.native && !display
  const presets = [...device.presets, ...builtInPresets].slice(0, 4)

  return (
    <div className="page-enter">
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <p className="hero-kicker">{t('TAKE CONTROL.', 'ASSUMA O CONTROLE.')}</p>
          <h1 id="hero-title" className="font-serif text-balance">{t('STRETCHED', 'TELA')}<span>{t('DISPLAY', 'ESTICADA')}</span></h1>
          <p className="hero-description text-pretty">{t('Your display. Your perspective. Fine-tune every pixel, your way.', 'Alongamento inteligente para mais visão, projeção precisa e controle rápido.')}</p>
        </div>
        <img src="/images/cat-emblem.png" className="hero-mascot" alt="Geometric purple and black cat emblem" width={164} height={164} fetchPriority="high" />
      </section>

      <div className="dashboard-grid">
        <div className="dashboard-primary">
          <section className="resolution-panel" aria-label={t('Resolution controls', 'Controles de resolução')}>
            <div className="text-center">
              <h2 className="eyebrow font-serif">{t('STRETCH MULTIPLIER', 'MULTIPLICADOR ATUAL')}</h2>
              <div className="multiplier-value font-serif" aria-live="polite">{multiplier.toFixed(2).replace('.', language === 'pt' ? ',' : '.')}<small>×</small></div>
            </div>
            <div className="slider-wrap">
              <Slider aria-label={t('Stretch multiplier', 'Multiplicador de tela')} value={[Math.round(multiplier * 100)]} onValueChange={(value) => setMultiplier((Array.isArray(value) ? value[0] : value) / 100)} min={101} max={130} step={1} disabled={busy || !!display?.pending} />
              <div className="range-labels"><span>{formatMultiplier(1.01, language)}</span><span>{formatMultiplier(1.3, language)}</span></div>
            </div>
            <div className="resolution-comparison">
              <div className="resolution-item"><Monitor aria-hidden="true" /><div><p>{display || device.native ? t('NATIVE', 'NATIVA') : t('REFERENCE', 'REFERÊNCIA')}</p><strong>{awaitingDevice ? t('Not connected', 'Não conectado') : resolutionLabel(nativeWidth, nativeHeight)}</strong></div></div>
              <ArrowRight className="size-4 text-muted-foreground" aria-hidden="true" />
              <div className="resolution-item"><ArrowRightLeft aria-hidden="true" /><div><p>{t('PROJECTED', 'PROJEÇÃO')}</p><strong>{awaitingDevice ? '—' : resolutionLabel(projected.width, projected.height)}</strong></div></div>
            </div>
            <div className="control-actions">
              <Button className="activate-button font-serif" disabled={busy || !!display?.pending} onClick={() => ready ? void run('apply', { multiplier }) : openSetup()}><CheckCircle2 data-icon="inline-start" />{busy ? t('WORKING…', 'APLICANDO…') : t('ACTIVATE', 'ATIVAR')}</Button>
              <Button variant="outline" className="restore-button font-serif" disabled={busy} onClick={() => void run('restore', {}, t('Original resolution restored.', 'Resolução original restaurada.'))}><RotateCcw data-icon="inline-start" />{t('RESTORE', 'RESTAURAR')}</Button>
            </div>
            <p className="control-footnote">{display ? t('Tap Keep changes after Activate to save your resolution.', 'Toque em Manter alterações após Ativar para salvar a resolução.') : awaitingDevice ? t('Connect Shizuku to read your actual display.', 'Conecte o Shizuku para ler sua tela real.') : t('Example dimensions · device access requires Android', 'Dimensões de exemplo · requer o aplicativo Android')}</p>
          </section>
          <button className="connection-strip" data-ready={ready} onClick={openSetup}>
            <div className="flex items-center gap-3">
              {ready ? <ShieldCheck className="connection-icon" /> : <Shield className="connection-icon" />}
              <div><strong className="font-serif uppercase">{connection.title}</strong><p>{connection.description}</p></div>
            </div>
            {ready ? <CheckCircle2 className="size-5 text-success" /> : <ChevronRight className="size-5 text-primary" />}
          </button>
        </div>
        <div>
          <section className="section-block" aria-labelledby="quick-presets-title">
            <div className="section-heading">
              <h2 id="quick-presets-title" className="eyebrow font-serif"><Zap className="size-4" fill="currentColor" />{t('QUICK PRESETS', 'PRESETS RÁPIDOS')}</h2>
              <Button variant="ghost" onClick={() => navigate('presets')}>{t('Customize', 'Personalizar')}<Pencil data-icon="inline-end" /></Button>
            </div>
            <ToggleGroup className="quick-presets" aria-label={t('Quick presets', 'Presets rápidos')} value={[multiplier.toFixed(2)]} onValueChange={(values) => { if (values[0]) setMultiplier(Number(values[0])) }} disabled={busy || !!display?.pending}>
              {presets.map((preset) => <ToggleGroupItem key={preset.id} value={preset.multiplier.toFixed(2)} aria-label={`${preset.name} ${formatMultiplier(preset.multiplier)}`}>{formatMultiplier(preset.multiplier, language)}</ToggleGroupItem>)}
            </ToggleGroup>
          </section>
          <section className="section-block" aria-labelledby="features-title">
            <div className="section-heading"><h2 id="features-title" className="eyebrow font-serif">{t('BUILT FOR YOUR GAME', 'RECURSOS PRINCIPAIS')}</h2></div>
            <div className="feature-grid">
              <button onClick={() => navigate('overlay')}><Scan aria-hidden="true" /><strong className="font-serif">{t('FLOATING', 'OVERLAY')}<br />{t('OVERLAY', 'FLUTUANTE')}</strong><p>{t('Controls that stay with you.', 'Ajuste durante a partida.')}</p></button>
              <button onClick={() => setProjectionOpen(true)}><Expand aria-hidden="true" /><strong className="font-serif">{t('LIVE', 'PROJEÇÃO EM')}<br />{t('PROJECTION', 'TEMPO REAL')}</strong><p>{t('See your next resolution.', 'Veja sua próxima resolução.')}</p></button>
              <button onClick={() => void run('restore', {}, t('Original resolution restored.', 'Resolução original restaurada.'))}><RotateCcw aria-hidden="true" /><strong className="font-serif">{t('ONE-TAP', 'RESTAURAÇÃO')}<br />{t('RESTORE', 'INSTANTÂNEA')}</strong><p>{t('Back to your original view.', 'Volte com um toque.')}</p></button>
            </div>
          </section>
          <div className="px-6 pb-5"><button onClick={openSetup} className="flex items-center gap-2 text-sm text-muted-foreground hover:text-primary"><CircleHelp className="size-4" />{t('New here? Get set up', 'Primeira vez? Configure o aplicativo')}<ArrowRight className="size-4" /></button></div>
        </div>
      </div>
      <Dialog open={projectionOpen} onOpenChange={setProjectionOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('Live projection', 'Projeção em tempo real')}</DialogTitle><DialogDescription>{t('The long edge scales with your selected multiplier. Your display density stays unchanged.', 'O lado maior é ajustado pelo multiplicador. A densidade da tela não é alterada.')}</DialogDescription></DialogHeader>
          <div className="rounded-xl bg-secondary p-6 text-center text-secondary-foreground"><SlidersHorizontal className="mx-auto mb-3 size-6" /><p className="font-serif text-4xl font-bold italic">{awaitingDevice ? t('Connect Shizuku first', 'Conecte o Shizuku primeiro') : resolutionLabel(projected.width, projected.height)}</p><p className="text-sm">{formatMultiplier(multiplier, language)} · {t('calculated resolution', 'resolução calculada')}</p></div>
          <p className="native-note">{t('Android controls how apps fit the new resolution. Some phones or games may letterbox or ignore aspect-ratio changes. This is not a promise of increased field of view or FPS.', 'O Android controla a adaptação dos aplicativos. Alguns celulares ou jogos podem adicionar bordas ou ignorar o formato. Não há garantia de aumento de campo de visão ou FPS.')}</p>
        </DialogContent>
      </Dialog>
    </div>
  )
}
