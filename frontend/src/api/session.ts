export interface LoginInfo {
  userId: string
  accessToken: string
  initialConversationId?: string
}

const USER_ID_KEY = 'user_id'
const ACCESS_TOKEN_KEY = 'access_token'
const INITIAL_CONVERSATION_ID_KEY = 'initial_conversation_id'

export function saveLoginInfo(data: LoginInfo) {
  window.localStorage.setItem(USER_ID_KEY, data.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
  if (data.initialConversationId) {
    saveInitialConversationId(data.initialConversationId)
  } else {
    window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
  }
}

export function saveInitialConversationId(initialConversationId: string) {
  window.localStorage.setItem(INITIAL_CONVERSATION_ID_KEY, initialConversationId)
}

export function readLoginInfo(): LoginInfo | null {
  const userId = window.localStorage.getItem(USER_ID_KEY)
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  const initialConversationId = window.localStorage.getItem(INITIAL_CONVERSATION_ID_KEY)

  if (!userId || !accessToken) {
    return null
  }

  return {
    userId,
    accessToken,
    initialConversationId: initialConversationId || undefined
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(INITIAL_CONVERSATION_ID_KEY)
}

export function isLoggedIn() {
  return Boolean(readLoginInfo())
}
