export interface LoginInfo {
  userId: string
  accessToken: string
}

const USER_ID_KEY = 'user_id'
const ACCESS_TOKEN_KEY = 'access_token'
const AUTH_COOKIE_KEY = 'Authorization'

export function readCookieValue(name: string) {
  const cookiePrefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map(item => item.trim())
    .find(item => item.startsWith(cookiePrefix))

  return cookie ? decodeURIComponent(cookie.slice(cookiePrefix.length)) : ''
}

export function readAuthCookies(): LoginInfo | null {
  const userId = readCookieValue(USER_ID_KEY)
  const accessToken = readCookieValue(AUTH_COOKIE_KEY)

  if (!userId || !accessToken) {
    return null
  }

  return {
    userId,
    accessToken
  }
}

export function saveLoginInfo(data: LoginInfo) {
  window.localStorage.setItem(USER_ID_KEY, data.userId)
  window.localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
}

export function readLoginInfo(): LoginInfo | null {
  const userId = window.localStorage.getItem(USER_ID_KEY) || readCookieValue(USER_ID_KEY)
  const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY) || readCookieValue(AUTH_COOKIE_KEY)

  if (!userId || !accessToken) {
    return null
  }

  return {
    userId,
    accessToken
  }
}

export function clearLoginInfo() {
  window.localStorage.removeItem(USER_ID_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export function isLoggedIn() {
  return Boolean(readLoginInfo())
}
