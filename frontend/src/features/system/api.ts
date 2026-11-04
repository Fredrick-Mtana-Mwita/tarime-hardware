import { getJson } from '../../shared/api/http'

export interface SystemInfo {
  application: string
  stage: string
  zeroBudget: boolean
  externalMessagingEnabled: boolean
}

export async function getSystemInfo(signal?: AbortSignal): Promise<SystemInfo> {
  const value = await getJson('/api/system/info', signal)
  if (!value || typeof value !== 'object' || !('application' in value) || typeof value.application !== 'string'
    || !('stage' in value) || typeof value.stage !== 'string'
    || !('zeroBudget' in value) || typeof value.zeroBudget !== 'boolean'
    || !('externalMessagingEnabled' in value) || typeof value.externalMessagingEnabled !== 'boolean') {
    throw new Error('Invalid system response')
  }
  return value as SystemInfo
}
