'use client'

import { Download, ShieldCheck, Smartphone, ArrowUpRight, CircleCheck } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button, buttonVariants } from '@/components/ui/button'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { androidRelease } from '@/lib/android-release'
import { cn } from '@/lib/utils'
import { useCat } from './app-context'

export function SetupDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (value: boolean) => void }) {
  const { device, run, busy, t } = useCat()
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader><div className="mb-2 flex size-12 items-center justify-center rounded-xl bg-secondary text-primary"><Smartphone className="size-6" /></div><DialogTitle>{t('A better view starts here.', 'Uma nova visão começa aqui.')}</DialogTitle><DialogDescription>{t('Android 11–14 · Shizuku · No root required', 'Android 11–14 · Shizuku · Sem root')}</DialogDescription></DialogHeader>
        {!device.native && <Alert><Smartphone /><AlertTitle>{t('You’re exploring the browser preview', 'Você está na prévia do navegador')}</AlertTitle><AlertDescription>{t('Only the installed Android app can read or change your phone’s resolution and show an overlay over games.', 'Somente o aplicativo Android instalado pode alterar a resolução e exibir controles sobre os jogos.')}</AlertDescription></Alert>}
        <ol className="setup-steps">
          <li><div><strong>{t('Install Cat Resolution', 'Instale o Cat Resolution')}</strong><p>{t('Use the test APK below on your Android phone. It is an independent recreation, not the original app.', 'Use o APK de teste abaixo no seu Android. É uma recriação independente, não o aplicativo original.')}</p></div></li>
          <li><div><strong>{t('Start Shizuku', 'Inicie o Shizuku')}</strong><p>{t('Enable Developer options and wireless debugging. Pair in Shizuku, then tap Start. Restart Shizuku after each phone reboot.', 'Ative as opções do desenvolvedor e a depuração sem fio. Faça o pareamento no Shizuku e toque em Iniciar. Reinicie o Shizuku após cada reinicialização do celular.')}</p></div></li>
          <li><div><strong>{t('Authorize and take control', 'Autorize e assuma o controle')}</strong><p>{t('Open this app, authorize Shizuku, and allow “Display over other apps” for the floating controller. Start with 1.05×.', 'Abra o aplicativo, autorize o Shizuku e permita exibição sobre outros apps para o controle flutuante. Comece com 1,05×.')}</p></div></li>
        </ol>
        {device.native ? <div className="flex flex-col gap-2">{!device.notificationPermission && <Button variant="outline" className="h-11" onClick={() => void run('notificationPermission')}>{t('Enable recovery notifications', 'Ativar notificações de recuperação')}</Button>}<Button className="h-11" disabled={busy || device.shizuku === 'ready' || device.shizuku === 'connecting'} onClick={() => void run('requestShizuku')}><ShieldCheck data-icon="inline-start" />{device.shizuku === 'ready' ? t('Shizuku authorized', 'Shizuku autorizado') : device.shizuku === 'connecting' ? t('Connecting…', 'Conectando…') : t('Authorize Shizuku', 'Autorizar Shizuku')}</Button><Button variant="outline" className="h-11" onClick={() => void run('openShizuku')}>{t('Open or install Shizuku', 'Abrir ou instalar Shizuku')}<ArrowUpRight data-icon="inline-end" /></Button></div> : <div className="flex flex-col gap-2">{androidRelease.available ? <><a href={androidRelease.apkPath} download className={cn(buttonVariants(), 'h-12')}><Download className="size-4" />{t('Download Android APK', 'Baixar APK Android')}</a><a href={androidRelease.sourcePath} download className={cn(buttonVariants({ variant: 'outline' }), 'h-11')}>{t('Download Android source', 'Baixar código-fonte Android')}</a></> : <p className="native-note">{t('The Android source project is included in this project. The APK will be available after a successful native build.', 'O código-fonte Android está incluído. O APK estará disponível após a compilação nativa.')}</p>}<a href="https://shizuku.rikka.app/guide/setup/" target="_blank" rel="noopener noreferrer" className={cn(buttonVariants({ variant: 'ghost' }), 'h-11')}>{t('Official Shizuku setup guide', 'Guia oficial do Shizuku')}<ArrowUpRight className="size-4" /></a></div>}
        <div className="rounded-xl bg-muted p-3 text-muted-foreground"><p className="text-sm leading-relaxed"><CircleCheck className="mr-1 inline size-4" />{t('Changes require confirmation and revert on timeout. Physical-device testing is still required. OEM restrictions, game policies, and letterboxing can vary.', 'Alterações exigem confirmação e são revertidas após o tempo limite. Ainda é necessário testar em um celular real. Restrições do fabricante, políticas de jogos e bordas podem variar.')}</p></div>
      </DialogContent>
    </Dialog>
  )
}
