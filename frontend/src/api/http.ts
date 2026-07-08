import axios from 'axios'
import { clearLoginInfo, readLoginInfo } from './session'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
  }
}

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const http = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000,
  withCredentials: true
})

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data

    if (responseData && typeof responseData === 'object' && 'message' in responseData) {
      const message = responseData.message

      if (typeof message === 'string' && message.trim()) {
        return message
      }
    }

    if (typeof responseData === 'string' && responseData.trim()) {
      return responseData
    }

    if (error.message) {
      return error.message
    }
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

http.interceptors.request.use(config => {
  if (apiBaseURL.replace(/\/+$/, '').endsWith('/api') && config.url?.startsWith('/api/')) {
    config.url = config.url.slice('/api'.length)
  }

  const accessToken = readLoginInfo()?.accessToken

  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

http.interceptors.response.use(
  response => response,
  error => {
    if (
      axios.isAxiosError(error) &&
      !error.config?.skipAuthRedirect &&
      (error.response?.status === 401 || error.response?.status === 403)
    ) {
      clearLoginInfo()

      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }

    return Promise.reject(error)
  }
)

export default http
