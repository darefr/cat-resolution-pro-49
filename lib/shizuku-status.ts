import type { DeviceState } from '@/lib/cat-types'

type Translate = (english: string, portuguese: string) => string

export function shizukuStatus(device: DeviceState, t: Translate) {
  if (!device.native) return {
    title: t('Connect with Shizuku', 'Conecte com Shizuku'),
    description: t('Install the Android app to control your display.', 'Instale o aplicativo Android para controlar sua tela.'),
  }
  switch (device.shizuku) {
    case 'ready': return {
      title: t('Ready to change resolution', 'Pronto para alterar a resolução'),
      description: t('Shizuku is authorized and Android display access is working.', 'Shizuku autorizado e acesso à tela do Android funcionando.'),
    }
    case 'waiting': return {
      title: t('Waiting for Shizuku', 'Aguardando o Shizuku'),
      description: t('Waiting for the Android connection. Keep Shizuku running.', 'Aguardando a conexão do Android. Mantenha o Shizuku em execução.'),
    }
    case 'permission': return {
      title: t('Shizuku is running. Allow access.', 'Shizuku em execução. Permita o acesso.'),
      description: t('Tap Authorize Shizuku, then tap Allow in its permission dialog.', 'Toque em Autorizar Shizuku e depois em Permitir na solicitação.'),
    }
    case 'requesting': return {
      title: t('Waiting for your permission', 'Aguardando sua permissão'),
      description: t('Tap Allow in the Shizuku dialog. No dialog? Open Shizuku → Authorized applications → Cat Resolution and enable access.', 'Toque em Permitir. Não apareceu? Abra Shizuku → Aplicativos autorizados → Cat Resolution e permita o acesso.'),
    }
    case 'denied': return {
      title: t('Shizuku access was not allowed', 'Acesso ao Shizuku não permitido'),
      description: t('Open Shizuku → Authorized applications and enable Cat Resolution. Return here to reconnect.', 'Abra Shizuku → Aplicativos autorizados e ative Cat Resolution. Volte aqui para reconectar.'),
    }
    case 'connecting': return {
      title: t('Starting display control', 'Iniciando o controle de tela'),
      description: t('Permission is granted. Connecting to the privileged display service…', 'Permissão concedida. Conectando ao serviço de tela…'),
    }
    case 'error': return {
      title: device.shizukuPermissionGranted ? t('Authorized. Reconnect display control.', 'Autorizado. Reconecte o controle de tela.') : t('Connection needs attention', 'Verifique a conexão'),
      description: t('Tap Reconnect display control. If it still fails, restart Shizuku and reopen this app.', 'Toque em Reconectar controle de tela. Se falhar, reinicie o Shizuku e abra este aplicativo novamente.'),
    }
    default: return {
      title: t('Shizuku is not connected', 'Shizuku não está conectado'),
      description: t('Pairing does not start Shizuku. Open Shizuku, tap Start, wait for “Shizuku is running”, then return here.', 'O pareamento não inicia o Shizuku. Abra-o, toque em Iniciar, aguarde “Shizuku em execução” e volte aqui.'),
    }
  }
}
