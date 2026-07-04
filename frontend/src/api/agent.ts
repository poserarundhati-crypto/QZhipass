import http, { getErrorMessage } from './http'

interface AgentCallResponse {
  success?: boolean
  message?: string
  payload?: {
    response?: string
    tokensUsed?: number
    currentUsage?: {
      tokenUsed?: number
      tokenLimit?: number
    }
  }
}

export async function callAgent() {
  try {
    const { data } = await http.post<AgentCallResponse>('/api/v1/agent/call')

    if (data.success === false) {
      throw new Error(data.message || 'Agent 调用失败')
    }

    return data
  } catch (error) {
    throw new Error(getErrorMessage(error, 'Agent 调用失败'))
  }
}
