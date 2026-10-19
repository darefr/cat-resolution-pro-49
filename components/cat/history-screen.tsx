'use client'

import { useState } from 'react'
import { History, RotateCcw, SlidersHorizontal, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle, EmptyDescription, EmptyContent } from '@/components/ui/empty'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { formatMultiplier } from '@/lib/cat-types'
import { useCat } from './app-context'

export function HistoryScreen() {
  const { device, run, busy, navigate, openSetup, language, t } = useCat()
  const [confirmClear, setConfirmClear] = useState(false)
  return (
    <section className="page-content page-enter">
      <div className="page-heading"><h1 className="text-balance">{t('Every adjustment.', 'Cada ajuste.')}</h1><p>{t('A clear record of your display changes.', 'O histórico das alterações da sua tela.')}</p></div>
      {device.history.length ? <><div className="mb-4 flex items-center justify-between"><span className="text-sm text-muted-foreground">{device.history.length} {t('recent events', 'eventos recentes')}</span><Button variant="ghost" onClick={() => setConfirmClear(true)}><Trash2 data-icon="inline-start" />{t('Clear', 'Limpar')}</Button></div><ol className="flex flex-col gap-3">{device.history.map((entry) => <li className="rounded-xl border p-4" key={entry.id}><div className="flex items-center gap-3"><div className="rounded-lg bg-secondary p-2 text-primary">{entry.action.includes('restore') || entry.action.includes('revert') ? <RotateCcw className="size-5" /> : <SlidersHorizontal className="size-5" />}</div><div className="min-w-0 flex-1"><strong className="block font-medium capitalize">{entry.action.replaceAll('_', ' ')}</strong><p className="text-sm text-muted-foreground">{entry.detail}</p></div><span className="font-serif text-xl font-bold italic">{formatMultiplier(entry.multiplier, language)}</span></div><p className="pt-3 text-sm text-muted-foreground">{new Date(entry.createdAt).toLocaleString(language === 'pt' ? 'pt-BR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' })}</p></li>)}</ol></> : <Empty className="min-h-80 border"><EmptyHeader><EmptyMedia variant="icon"><History /></EmptyMedia><EmptyTitle>{t('A fresh perspective starts here', 'Um novo começo')}</EmptyTitle><EmptyDescription>{device.native ? t('Apply your first resolution to see its history here. Changes are stored only on your phone.', 'Aplique uma resolução para começar. O histórico fica apenas no celular.') : t('Real display changes appear here in the Android app. The browser preview does not create device activity.', 'As alterações reais aparecem no aplicativo Android. A prévia no navegador não cria atividade no dispositivo.')}</EmptyDescription></EmptyHeader><EmptyContent><Button className="h-11" onClick={device.native ? () => navigate('home') : openSetup}>{device.native ? t('Adjust your display', 'Ajustar sua tela') : t('Get the Android app', 'Obter o aplicativo Android')}</Button></EmptyContent></Empty>}
      <Dialog open={confirmClear} onOpenChange={setConfirmClear}><DialogContent><DialogHeader><DialogTitle>{t('Clear your history?', 'Limpar histórico?')}</DialogTitle><DialogDescription>{t('This permanently deletes recorded events on this phone. Saved presets and display settings are kept.', 'Os eventos serão excluídos do celular. Presets e configurações serão mantidos.')}</DialogDescription></DialogHeader><Button disabled={busy} onClick={async () => { if (await run('clearHistory', {}, t('History cleared.', 'Histórico limpo.'))) setConfirmClear(false) }}>{t('Clear history', 'Limpar histórico')}</Button></DialogContent></Dialog>
    </section>
  )
}
