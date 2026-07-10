import http, { getErrorMessage } from './http'
import { saveLoginInfo, type LoginInfo } from './session'

type PortalLoginType = 'MOBILE_PWD' | 'EMAIL_PWD' | 'smsLogin' | 'wechatLogin'

interface PortalLoginResponse {
  success?: boolean
  message?: string
  data?: unknown
  payload?: unknown
  user_id?: unknown
  userId?: unknown
  id?: unknown
  access_token?: unknown
  accessToken?: unknown
  token?: unknown
}

interface LoginStatusResponse {
  success?: boolean
}

const MOBILE_PATTERN = /^1[3-9]\d{9}$/

export function isValidMobile(mobile: string) {
  return MOBILE_PATTERN.test(mobile)
}

function readString(value: unknown) {
  if (typeof value === 'string' && value.trim()) {
    return value.trim()
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }

  return ''
}

function readRecord(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}
}

function normalizeLoginInfo(response: PortalLoginResponse): LoginInfo {
  if (response.success === false) {
    throw new Error(response.message || '登录失败')
  }

  const data = readRecord(response.data)
  const payload = readRecord(response.payload)
  const conversation = readRecord(payload.conversation || data.conversation)
  const userId =
    readString(data.user_id) ||
    readString(data.userId) ||
    readString(data.id) ||
    readString(payload.user_id) ||
    readString(payload.userId) ||
    readString(payload.id) ||
    readString(response.user_id) ||
    readString(response.userId) ||
    readString(response.id) ||
    ''
  const accessToken =
    readString(data.access_token) ||
    readString(data.accessToken) ||
    readString(data.token) ||
    readString(payload.access_token) ||
    readString(payload.accessToken) ||
    readString(payload.token) ||
    readString(response.access_token) ||
    readString(response.accessToken) ||
    readString(response.token) ||
    ''
  const initialConversationId =
    readString(data.initialConversationId) ||
    readString(data.initial_conversation_id) ||
    readString(payload.initialConversationId) ||
    readString(payload.initial_conversation_id) ||
    readString(conversation.id) ||
    readString(conversation.conversationId) ||
    undefined

  if (!userId) {
    throw new Error('登录成功但后端未返回 user_id')
  }

  if (!accessToken) {
    throw new Error('登录成功但后端未返回 access_token')
  }

  return {
    userId,
    accessToken,
    initialConversationId
  }
}

function normalizeLoginError(message: string, fallback: string) {
  const rawMessage = message.trim()
  const lowerMessage = rawMessage.toLowerCase()

  if (!rawMessage) {
    return fallback
  }

  if (
    rawMessage.includes('用户不存在') ||
    lowerMessage.includes('user not found') ||
    lowerMessage.includes('user does not exist') ||
    lowerMessage.includes('user not exist')
  ) {
    return '用户不存在'
  }

  if (
    rawMessage.includes('注销') ||
    rawMessage.includes('停用') ||
    lowerMessage.includes('deactivated') ||
    lowerMessage.includes('not active') ||
    lowerMessage.includes('frozen')
  ) {
    return '您的账户已注销'
  }

  if (
    rawMessage.includes('密码错误') ||
    rawMessage.includes('账号或密码') ||
    lowerMessage.includes('wrong password') ||
    lowerMessage.includes('bad credentials') ||
    lowerMessage.includes('invalid credential')
  ) {
    return '账号或密码错误'
  }

  if (
    lowerMessage.includes('network error') ||
    lowerMessage.includes('econnrefused') ||
    lowerMessage.includes('error occurred while trying to proxy')
  ) {
    return '后端服务未启动或无法连接'
  }

  if (
    lowerMessage.includes('404') ||
    lowerMessage === 'not found' ||
    lowerMessage.includes('no static resource') ||
    lowerMessage.includes('unsupported login type')
  ) {
    return '登录接口不可用，请确认后端登录接口已按最新契约启动'
  }

  if (
    lowerMessage.includes('request failed with status code 500') ||
    lowerMessage.includes('request failed with status code 502') ||
    lowerMessage.includes('request failed with status code 503') ||
    lowerMessage.includes('request failed with status code 504')
  ) {
    return '登录服务异常，请确认后端服务已启动'
  }

  return rawMessage
}

async function login(loginType: PortalLoginType, credential: Record<string, string>, fallback: string) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/api/v1/auth/portal/login', {
      loginType,
      params: credential
    })
    const loginInfo = normalizeLoginInfo(data)

    saveLoginInfo(loginInfo)
    return loginInfo
  } catch (error) {
    throw new Error(normalizeLoginError(getErrorMessage(error, fallback), fallback))
  }
}

export async function loginByPassword(mobile: string, password: string) {
  return login(
    'MOBILE_PWD',
    {
      phone_number: mobile,
      password
    },
    '账号或密码错误'
  )
}

export async function loginByEmailPassword(email: string, password: string) {
  return login(
    'EMAIL_PWD',
    {
      email,
      password
    },
    '账号或密码错误'
  )
}

export async function sendSmsCode(mobile: string) {
  try {
    const { data } = await http.post<PortalLoginResponse>('/api/v1/auth/portal/sendcode', {
      phone: mobile
    })

    if (data?.success === false) {
      throw new Error(data.message || '验证码发送失败')
    }

    return true
  } catch (error) {
    throw new Error(getErrorMessage(error, '验证码发送失败'))
  }
}

export async function loginBySms(mobile: string, smsCode: string) {
  return login(
    'smsLogin',
    {
      phone_number: mobile,
      sms: smsCode
    },
    '验证码登录失败'
  )
}

export async function checkLoginStatus(userId: string) {
  try {
    void userId
    const { data } = await http.get<LoginStatusResponse>('/api/v1/token/check')

    return Boolean(data?.success)
  } catch {
    return false
  }
}

export async function logoutPortal() {
  try {
    await http.delete('/api/v1/auth/portal/logout')
  } catch {
    // Local login state is still cleared by the store.
  }
}
