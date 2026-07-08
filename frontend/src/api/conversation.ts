import axios from 'axios'
import http, { getErrorMessage } from './http'

interface CreateSessionPayload {
  code?: number
  message?: string
  data?: {
    session_id?: string | number
    sessionId?: string | number
    created_at?: string
  }
  session_id?: string | number
  sessionId?: string | number
  created_at?: string
}

export class CreateSessionError extends Error {
  status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'CreateSessionError'
    this.status = status
  }
}

function normalizeSessionId(value: unknown) {
  if (typeof value === 'string' && value.trim()) {
    return value.trim()
  }

  if (typeof value === 'number') {
    return String(value)
  }

  return ''
}

function readSessionId(payload: CreateSessionPayload) {
  return (
    normalizeSessionId(payload.session_id) ||
    normalizeSessionId(payload.sessionId) ||
    normalizeSessionId(payload.data?.session_id) ||
    normalizeSessionId(payload.data?.sessionId)
  )
}

export async function createSession() {
  try {
    const { data } = await http.post<CreateSessionPayload>(
      '/api/v1/sessions',
      {},
      {
        skipAuthRedirect: true
      }
    )

    if (typeof data.code === 'number' && data.code >= 400) {
      throw new CreateSessionError(data.message || '新建对话失败，请稍后重试', data.code)
    }

    const sessionId = readSessionId(data)

    if (!sessionId) {
      throw new CreateSessionError('新建对话成功但未返回 session_id，请确认后端字段')
    }

    return {
      sessionId,
      createdAt: data.created_at || data.data?.created_at || ''
    }
  } catch (error) {
    if (error instanceof CreateSessionError) {
      throw error
    }

    if (axios.isAxiosError(error)) {
      const status = error.response?.status

      if (status === 401 || status === 403) {
        throw new CreateSessionError('登录状态已失效，请重新登录', status)
      }

      if (status === 404) {
        throw new CreateSessionError('新建对话接口不存在，请确认后端路径', status)
      }

      if (status && status >= 500) {
        throw new CreateSessionError('新建对话失败，请稍后重试', status)
      }

      if (!error.response) {
        throw new CreateSessionError('接口连接失败，请确认网络或 CORS 配置')
      }
    }

    throw new CreateSessionError(getErrorMessage(error, '新建对话失败，请稍后重试'))
  }
}
