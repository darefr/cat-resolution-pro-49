'use client'

import { Download, Smartphone, ArrowUpRight, CircleCheck } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button, buttonVariants } from '@/components/ui/button'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { androidRelease } from '@/lib/android-release'
import { cn } from '@/lib/utils'
import { useCat } from './app-context'
import { ShizukuConnection } from './shizuku-connection'

export function SetupDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (value: boolean) => void }) {
  const { device, run, busy, navigate, t } = useCat()
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <div className="mb-2 flex size-12 items-center justify-center rounded-xl bg-secondary text-primary"><Smartphone className="size-6" /></div>
          <DialogTitle>{device.native ? t('Connect your display.', 'Conecte sua tela.') : t('A better view starts here.', 'Uma nova visão começa aqui.')}</DialogTitle>
          <DialogDescription>{t('Android 11–14 · Shizuku · No root required', 'Android 11–14 · Shizuku · Sem root')}</DialogDescription>
        </DialogHeader>
        {device.native ? <>
          <ShizukuConnection onReady={() => { onOpenChange(false); navigate('home') }} />
          {device.shizuku === 'ready' && !device.notificationPermission && <Button variant="outline" className="h-11" disabled={busy} onClick={() => void run('notificationPermission')}>{t('Enable recovery notifications', 'Ativar notificações de recuperação')}</Button>}
        </> : <>
          <Alert><Smartphone /><AlertTitle>{t('You’re exploring the browser preview', 'Você está na prévia do navegador')}</AlertTitle><AlertDescription>{t('Install the APK on your phone to change its actual resolution and use the floating controller.', 'Instale o APK no celular para alterar a resolução real e usar o controle flutuante.')}</AlertDescription></Alert>
          <ol className="setup-steps">
            <li><div><strong>{t('Install Cat Resolution', 'Instale o Cat Resolution')}</strong><p>{t('Download the updated APK below and install it over the previous version. This is an independent recreation.', 'Baixe o APK atualizado abaixo e instale sobre a versão anterior. Esta é uma recriação independente.')}</p></div></li>
            <li><div><strong>{t('Pair, then START Shizuku', 'Pareie e INICIE o Shizuku')}</strong><p>{t('After wireless-debugging pairing, tap Start in Shizuku. Wait until it says “Shizuku is running”. Pairing alone is not enough.', 'Após parear pela depuração sem fio, toque em Iniciar no Shizuku. Aguarde “Shizuku em execução”. Apenas parear não basta.')}</p></div></li>
            <li><div><strong>{t('Allow access, then Activate', 'Permita o acesso e ative')}</strong><p>{t('Return to Cat Resolution, tap Authorize Shizuku, and choose Allow. Once display access is verified, choose 1.05×, tap Activate, then Keep changes.', 'Volte ao Cat Resolution, autorize o Shizuku e escolha Permitir. Com acesso verificado, escolha 1,05×, toque em Ativar e Manter alterações.')}</p></div></li>
          </ol>
          <div className="flex flex-col gap-2">
            {androidRelease.available ? <>
              <a href={androidRelease.apkPath} download className={cn(buttonVariants(), 'h-12')}><Download data-icon="inline-start" />{t('Download Android APK', 'Baixar APK Android')} · v{androidRelease.version}</a>
              <a href={androidRelease.sourcePath} download className={cn(buttonVariants({ variant: 'outline' }), 'h-11')}>{t('Download Android source', 'Baixar código-fonte Android')}</a>
            </> : <p className="native-note">{t('The APK will be available after a successful native build.', 'O APK estará disponível após a compilação nativa.')}</p>}
            <a href="https://shizuku.rikka.app/guide/setup/" target="_blank" rel="noopener noreferrer" className={cn(buttonVariants({ variant: 'ghost' }), 'h-11')}>{t('Official Shizuku setup guide', 'Guia oficial do Shizuku')}<ArrowUpRight data-icon="inline-end" /></a>
          </div>
        </>}
        <Alert><CircleCheck /><AlertTitle>{t('A way back if the screen looks wrong', 'Uma saída se a tela ficar errada')}</AlertTitle><AlertDescription>{t(`After Activate, tap Keep changes within ${device.preferences.safetyTimeout} seconds. Otherwise the saved original resolution is restored automatically.`, `Após Ativar, toque em Manter alterações em ${device.preferences.safetyTimeout} segundos. Caso contrário, a resolução original salva será restaurada automaticamente.`)}</AlertDescription></Alert>
      </DialogContent>
    </Dialog>
  )
}
