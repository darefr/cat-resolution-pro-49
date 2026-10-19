'use client'

import { useState, type FormEvent } from 'react'
import { Check, Plus, Trash2, Bookmark } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldGroup, FieldLabel, FieldDescription, FieldError } from '@/components/ui/field'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert'
import { builtInPresets, formatMultiplier } from '@/lib/cat-types'
import { useCat } from './app-context'

export function PresetsScreen() {
  const { device, multiplier, setMultiplier, navigate, run, busy, t, language } = useCat()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [factor, setFactor] = useState('1.16')
  const [error, setError] = useState('')
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const presets = [...builtInPresets, ...device.presets]
  async function save(event: FormEvent) {
    event.preventDefault()
    const value = Number(factor)
    if (!name.trim() || !Number.isFinite(value) || value < 1.01 || value > 1.3) {
      setError(t('Enter a name and a multiplier between 1.01 and 1.30.', 'Informe um nome e um multiplicador entre 1,01 e 1,30.'))
      return
    }
    if (await run('savePreset', { name: name.trim(), multiplier: value }, t('Preset saved on your phone.', 'Preset salvo no celular.'))) {
      setEditing(false)
      setName('')
    }
  }
  return (
    <section className="page-content page-enter">
      <div className="page-heading"><h1 className="text-balance">{t('Your perfect setup.', 'Seu ajuste perfeito.')}</h1><p>{t('Find your sweet spot. Keep it one tap away.', 'Encontre seu ajuste ideal e salve para depois.')}</p></div>
      <div className="preset-list">
        {presets.map((preset) => <div key={preset.id} className="preset-row" data-selected={Math.abs(multiplier - preset.multiplier) < 0.001}>
          <button className="preset-select" onClick={() => { setMultiplier(preset.multiplier); navigate('home') }} aria-label={`${t('Select', 'Selecionar')} ${preset.name} ${formatMultiplier(preset.multiplier)}`}><span className="preset-factor">{formatMultiplier(preset.multiplier, language)}</span><span><strong className="block font-semibold">{preset.name}</strong><span className="text-sm text-muted-foreground">{preset.id < 0 ? t('Built-in preset', 'Preset padrão') : t('Saved on this device', 'Salvo neste dispositivo')}</span></span></button>
          {preset.id > 0 ? <Button size="icon" variant="ghost" aria-label={`${t('Delete', 'Excluir')} ${preset.name}`} onClick={() => setDeleteId(preset.id)}><Trash2 /></Button> : Math.abs(multiplier - preset.multiplier) < 0.001 ? <Check className="size-5 text-primary" /> : null}
        </div>)}
      </div>
      <div className="py-5"><Button className="h-12 w-full" onClick={() => { setFactor(multiplier.toFixed(2)); setError(''); setEditing(true) }}><Plus data-icon="inline-start" />{t('Create a preset', 'Criar preset')}</Button></div>
      <Alert><Bookmark /><AlertTitle>{t('A setup that stays yours', 'Um ajuste só seu')}</AlertTitle><AlertDescription>{t('Custom presets are stored in the Android app’s on-device database. No account or cloud connection needed.', 'Os presets são salvos no banco de dados do aplicativo Android. Sem conta ou conexão com a nuvem.')}</AlertDescription></Alert>
      <Dialog open={editing} onOpenChange={setEditing}><DialogContent><DialogHeader><DialogTitle>{t('Create a preset', 'Criar preset')}</DialogTitle><DialogDescription>{t('Give your go-to resolution a name.', 'Dê um nome à sua resolução favorita.')}</DialogDescription></DialogHeader>
        <form onSubmit={save}><FieldGroup><Field><FieldLabel htmlFor="preset-name">{t('Preset name', 'Nome do preset')}</FieldLabel><Input id="preset-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={32} placeholder={t('My gaming setup', 'Meu ajuste de jogo')} required className="h-11" /></Field><Field data-invalid={!!error}><FieldLabel htmlFor="preset-factor">{t('Multiplier', 'Multiplicador')}</FieldLabel><Input id="preset-factor" type="number" min="1.01" max="1.30" step="0.01" value={factor} onChange={(e) => setFactor(e.target.value)} required className="h-11" /><FieldDescription>1.01× – 1.30×</FieldDescription>{error && <FieldError>{error}</FieldError>}</Field><Button type="submit" disabled={busy} className="h-11">{t('Save preset', 'Salvar preset')}</Button></FieldGroup></form>
      </DialogContent></Dialog>
      <Dialog open={deleteId !== null} onOpenChange={(open) => { if (!open) setDeleteId(null) }}><DialogContent><DialogHeader><DialogTitle>{t('Delete this preset?', 'Excluir este preset?')}</DialogTitle><DialogDescription>{t('This removes the saved preset from this phone. Your current resolution is not changed.', 'O preset será removido do celular. A resolução atual não será alterada.')}</DialogDescription></DialogHeader><Button disabled={busy} onClick={async () => { if (await run('deletePreset', { id: deleteId }, t('Preset deleted.', 'Preset excluído.'))) setDeleteId(null) }}>{t('Delete preset', 'Excluir preset')}</Button></DialogContent></Dialog>
    </section>
  )
}
