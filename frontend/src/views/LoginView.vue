<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

type LoginTab = 'phone' | 'email' | 'sms'

const DEMO_MODE = true
const LOGIN_API = '/api/v1/auth/portal/login'
const SEND_CODE_API = '/api/v1/auth/portal/sendcode'
const MOCK_MOBILE = '13800138000'
const MOCK_PASSWORD = '12345@Abc'
const MOCK_EMAIL = 'demo@qzhipass.com'
const MOCK_SMS_CODE = '123456'
const INDEX_ROUTE = '/index'
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/
const PASSWORD_REQUIREMENT_MESSAGE = '密码不符合要求：必须至少8位，并包含大写字母、小写字母、数字和特殊字符'

const router = useRouter()

const activeTab = ref<LoginTab>('phone')
const submitting = ref(false)
const smsSending = ref(false)
const countdown = ref(0)
const messageText = ref('')
const messageType = ref<'error' | 'success'>('error')
const smsTimer = ref<number>()

const phoneForm = reactive({
  mobile: '',
  password: ''
})

const emailForm = reactive({
  email: '',
  password: ''
})

const smsForm = reactive({
  mobile: '',
  code: ''
})

const normalizedPhoneMobile = computed(() => phoneForm.mobile.trim())
const normalizedEmail = computed(() => emailForm.email.trim())
const normalizedSmsMobile = computed(() => smsForm.mobile.trim())
const canSubmitPhone = computed(
  () => normalizedPhoneMobile.value.length > 0 && PASSWORD_PATTERN.test(phoneForm.password) && !submitting.value
)
const canSubmitEmail = computed(
  () => normalizedEmail.value.length > 0 && PASSWORD_PATTERN.test(emailForm.password) && !submitting.value
)
const canSendSms = computed(() => normalizedSmsMobile.value.length > 0 && countdown.value === 0 && !smsSending.value)
const canSubmitSms = computed(
  () => normalizedSmsMobile.value.length > 0 && smsForm.code.trim().length > 0 && !submitting.value
)
const smsButtonText = computed(() => (countdown.value > 0 ? `${countdown.value} 秒后重试` : '发送验证码'))

function setActiveTab(tab: LoginTab) {
  activeTab.value = tab
  clearMessage()
}

function clearMessage() {
  messageText.value = ''
}

function showMessage(text: string, type: 'error' | 'success') {
  messageText.value = text
  messageType.value = type
}

function validatePassword(password: string) {
  if (PASSWORD_PATTERN.test(password)) {
    return true
  }

  showMessage(PASSWORD_REQUIREMENT_MESSAGE, 'error')
  return false
}

function handlePasswordInput(password: string) {
  if (!password || PASSWORD_PATTERN.test(password)) {
    clearMessage()
    return
  }

  showMessage(PASSWORD_REQUIREMENT_MESSAGE, 'error')
}

function saveMockSession() {
  window.localStorage.setItem('user_id', '10001')
  window.localStorage.setItem('access_token', 'mock-access-token')
}

async function redirectAfterLogin() {
  showMessage('登录成功', 'success')
  await router.push(INDEX_ROUTE)
}

async function requestRealLogin(loginType: string, params: Record<string, string>) {
  const response = await window.fetch(LOGIN_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      loginType,
      params
    })
  })

  if (!response.ok) {
    throw new Error('真实登录接口暂不可用')
  }

  return response.json()
}

async function requestRealSmsCode(mobile: string) {
  const response = await window.fetch(SEND_CODE_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      phone: mobile
    })
  })

  if (!response.ok) {
    throw new Error('真实验证码接口暂不可用')
  }
}

async function handlePhoneLogin() {
  if (submitting.value || normalizedPhoneMobile.value.length === 0) {
    return
  }

  if (!validatePassword(phoneForm.password)) {
    return
  }

  submitting.value = true
  clearMessage()

  try {
    if (DEMO_MODE) {
      if (normalizedPhoneMobile.value !== MOCK_MOBILE) {
        showMessage('用户不存在', 'error')
        return
      }

      if (phoneForm.password !== MOCK_PASSWORD) {
        showMessage('账号或密码错误', 'error')
        return
      }

      saveMockSession()
      await redirectAfterLogin()
      return
    }

    await requestRealLogin('MOBILE_PWD', {
      phone_number: normalizedPhoneMobile.value,
      password: phoneForm.password
    })
    await redirectAfterLogin()
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '账号或密码错误', 'error')
  } finally {
    submitting.value = false
  }
}

async function handleEmailLogin() {
  if (submitting.value || normalizedEmail.value.length === 0) {
    return
  }

  if (!validatePassword(emailForm.password)) {
    return
  }

  submitting.value = true
  clearMessage()

  try {
    if (DEMO_MODE) {
      if (normalizedEmail.value !== MOCK_EMAIL || emailForm.password !== MOCK_PASSWORD) {
        showMessage('邮箱或密码错误', 'error')
        return
      }

      saveMockSession()
      await redirectAfterLogin()
      return
    }

    await requestRealLogin('EMAIL_PWD', {
      email: normalizedEmail.value,
      password: emailForm.password
    })
    await redirectAfterLogin()
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '邮箱或密码错误', 'error')
  } finally {
    submitting.value = false
  }
}

function startCountdown() {
  countdown.value = 60

  if (smsTimer.value) {
    window.clearInterval(smsTimer.value)
  }

  smsTimer.value = window.setInterval(() => {
    countdown.value -= 1

    if (countdown.value <= 0 && smsTimer.value) {
      window.clearInterval(smsTimer.value)
      smsTimer.value = undefined
    }
  }, 1000)
}

async function handleSendSmsCode() {
  if (!canSendSms.value) {
    return
  }

  smsSending.value = true
  clearMessage()

  try {
    if (!DEMO_MODE) {
      await requestRealSmsCode(normalizedSmsMobile.value)
    }

    startCountdown()
    showMessage('验证码已发送', 'success')
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '验证码发送失败', 'error')
  } finally {
    smsSending.value = false
  }
}

async function handleSmsLogin() {
  if (!canSubmitSms.value) {
    return
  }

  submitting.value = true
  clearMessage()

  try {
    if (DEMO_MODE) {
      if (smsForm.code.trim() !== MOCK_SMS_CODE) {
        showMessage('验证码错误或已过期', 'error')
        return
      }

      saveMockSession()
      await redirectAfterLogin()
      return
    }

    await requestRealLogin('smsLogin', {
      phone_number: normalizedSmsMobile.value,
      sms: smsForm.code.trim()
    })
    await redirectAfterLogin()
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '验证码错误或已过期', 'error')
  } finally {
    submitting.value = false
  }
}

function handleWechatLogin() {
  window.alert('微信登录暂未接入，可后续对接后端授权码接口')
}

onBeforeUnmount(() => {
  if (smsTimer.value) {
    window.clearInterval(smsTimer.value)
  }
})
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="企智通品牌展示">
      <div class="brand-frame">
        <div class="brand-logo brand-logo--light">
          <span class="brand-mark">Q</span>
          <span>企智通</span>
        </div>
        <p class="brand-kicker">QZhipass</p>
        <h1>企业智能通行入口</h1>
        <p class="brand-description">连接企业账号与智能工作台，登录后进入企智通系统首页。</p>
        <div class="signal-strip" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </section>

    <section class="login-side" aria-label="登录区域">
      <div class="login-panel" data-testid="login-card">
        <div class="brand-logo">
          <span class="brand-mark">Q</span>
          <span>企智通</span>
        </div>

        <header class="login-header">
          <h2>登录企智通</h2>
          <p>使用手机号、邮箱或验证码进入企业智能通行入口。</p>
        </header>

        <div class="tabs" role="tablist" aria-label="登录方式">
          <button type="button" :class="{ active: activeTab === 'phone' }" @click="setActiveTab('phone')">
            手机号密码登录
          </button>
          <button type="button" :class="{ active: activeTab === 'email' }" @click="setActiveTab('email')">
            邮箱密码登录
          </button>
          <button type="button" :class="{ active: activeTab === 'sms' }" @click="setActiveTab('sms')">
            验证码登录
          </button>
        </div>

        <form v-if="activeTab === 'phone'" class="login-form" @submit.prevent="handlePhoneLogin">
          <label class="field">
            <span>手机号</span>
            <input
              v-model="phoneForm.mobile"
              autocomplete="username"
              maxlength="11"
              placeholder="请输入手机号"
              type="tel"
              @input="clearMessage"
            />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="phoneForm.password"
              autocomplete="current-password"
              placeholder="请输入密码"
              type="password"
              @input="handlePasswordInput(phoneForm.password)"
            />
          </label>

          <button class="primary-button" data-testid="password-login-button" type="submit" :disabled="!canSubmitPhone">
            {{ submitting ? '登录中' : '登录' }}
          </button>
        </form>

        <form v-else-if="activeTab === 'email'" class="login-form" @submit.prevent="handleEmailLogin">
          <label class="field">
            <span>邮箱</span>
            <input
              v-model="emailForm.email"
              autocomplete="username"
              placeholder="请输入邮箱"
              type="email"
              @input="clearMessage"
            />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="emailForm.password"
              autocomplete="current-password"
              placeholder="请输入密码"
              type="password"
              @input="handlePasswordInput(emailForm.password)"
            />
          </label>

          <button class="primary-button" data-testid="email-login-button" type="submit" :disabled="!canSubmitEmail">
            {{ submitting ? '登录中' : '登录' }}
          </button>
        </form>

        <form v-else class="login-form" @submit.prevent="handleSmsLogin">
          <label class="field">
            <span>手机号</span>
            <input
              v-model="smsForm.mobile"
              autocomplete="tel"
              maxlength="11"
              placeholder="请输入手机号"
              type="tel"
              @input="clearMessage"
            />
          </label>

          <label class="field">
            <span>验证码</span>
            <div class="sms-row">
              <input
                v-model="smsForm.code"
                inputmode="numeric"
                maxlength="6"
                placeholder="请输入验证码"
                type="text"
                @input="clearMessage"
              />
              <button class="secondary-button" type="button" :disabled="!canSendSms" @click="handleSendSmsCode">
                {{ smsSending ? '发送中' : smsButtonText }}
              </button>
            </div>
          </label>

          <button class="primary-button" data-testid="sms-login-button" type="submit" :disabled="!canSubmitSms">
            {{ submitting ? '登录中' : '登录' }}
          </button>
        </form>

        <p v-if="messageText" :class="['message', messageType]" role="status" aria-live="polite">{{ messageText }}</p>

        <button class="wechat-button" type="button" @click="handleWechatLogin">微信登录</button>
        <p class="demo-note">演示账号：手机号 13800138000，密码 12345@Abc。验证码登录的 mock 验证码为 123456。</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  overflow-x: hidden;
  color: #17233c;
  background: #f7f9fc;
}

.brand-panel {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(36px, 6vw, 88px);
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(circle at 72% 18%, rgba(35, 211, 255, 0.28), transparent 28%),
    linear-gradient(145deg, #0036bd 0%, #002fa7 44%, #021b63 100%);
}

.brand-panel::before {
  position: absolute;
  inset: 0;
  content: "";
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.14) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.14) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.92), rgba(0, 0, 0, 0.24));
}

.brand-frame {
  position: relative;
  z-index: 1;
  width: min(100%, 620px);
  min-height: min(70vh, 760px);
  display: grid;
  align-content: center;
  padding: clamp(30px, 5vw, 54px);
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0)),
    rgba(2, 26, 91, 0.08);
}

.brand-frame::after {
  position: absolute;
  right: clamp(18px, 4vw, 46px);
  bottom: clamp(18px, 4vw, 46px);
  width: clamp(130px, 18vw, 230px);
  height: clamp(88px, 12vw, 140px);
  content: "";
  border: 1px solid rgba(115, 221, 255, 0.36);
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgba(115, 221, 255, 0.3) 1px, transparent 1px),
    linear-gradient(rgba(115, 221, 255, 0.24) 1px, transparent 1px);
  background-size: 24px 24px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #14233d;
  font-size: 18px;
  font-weight: 850;
}

.brand-logo--light {
  gap: 12px;
  margin-bottom: 44px;
  color: #ffffff;
  font-size: 28px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid #c6d4e7;
  border-radius: 8px;
  color: #002fa7;
  background: #ffffff;
  font-size: 18px;
  font-weight: 900;
}

.brand-logo--light .brand-mark {
  width: 48px;
  height: 48px;
  border-color: rgba(255, 255, 255, 0.46);
  color: #ffffff;
  background: rgba(255, 255, 255, 0.1);
  font-size: 25px;
}

.brand-kicker {
  margin: 0 0 14px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 14px;
  font-weight: 850;
}

.brand-panel h1 {
  max-width: 560px;
  margin: 0;
  font-size: clamp(44px, 6vw, 78px);
  line-height: 1.02;
  letter-spacing: 0;
}

.brand-description {
  max-width: 540px;
  margin: 24px 0 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 17px;
  line-height: 1.75;
}

.signal-strip {
  width: min(100%, 420px);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 34px;
}

.signal-strip span {
  min-height: 6px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(115, 221, 255, 0.92), rgba(255, 255, 255, 0.2));
}

.login-side {
  min-width: 0;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(32px, 6vw, 76px);
}

.login-panel {
  width: min(100%, 430px);
  min-width: 0;
}

.login-header {
  margin: 28px 0 0;
}

.login-header h2 {
  margin: 0 0 8px;
  color: #0f2345;
  font-size: 36px;
  line-height: 1.12;
  letter-spacing: 0;
}

.login-header p {
  margin: 0;
  color: #60708a;
  font-size: 15px;
  line-height: 1.7;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: 30px 0 26px;
  padding: 4px;
  border: 1px solid #d8e2ee;
  border-radius: 8px;
  background: #edf3fb;
}

.tabs button {
  min-width: 0;
  min-height: 42px;
  border: 0;
  border-radius: 6px;
  color: #526176;
  background: transparent;
  font: inherit;
  font-size: 14px;
  font-weight: 780;
  cursor: pointer;
}

.tabs button.active {
  color: #002fa7;
  background: #ffffff;
  box-shadow: 0 0 0 1px rgba(0, 47, 167, 0.12);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span {
  color: #233149;
  font-size: 14px;
  font-weight: 780;
}

.field input {
  width: 100%;
  min-height: 52px;
  padding: 0 14px;
  border: 1px solid #d6e0ee;
  border-radius: 8px;
  outline: none;
  color: #16233c;
  background: #ffffff;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.field input:focus {
  border-color: #002fa7;
  box-shadow: 0 0 0 3px rgba(0, 47, 167, 0.1);
}

.sms-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 10px;
}

.primary-button,
.secondary-button,
.wechat-button {
  width: 100%;
  min-height: 52px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 850;
}

.primary-button {
  margin-top: 4px;
  border: 0;
  color: #ffffff;
  background: linear-gradient(135deg, #002fa7, #2265e8);
  box-shadow: 0 18px 32px rgba(0, 47, 167, 0.22);
}

.primary-button:disabled {
  color: rgba(255, 255, 255, 0.72);
  background: #8da3d7;
  box-shadow: none;
  cursor: not-allowed;
}

.secondary-button {
  border: 1px solid #b9cdf0;
  color: #002fa7;
  background: #ffffff;
}

.secondary-button:disabled {
  color: #8b98aa;
  background: #eef3fa;
  border-color: #d8e2ee;
  cursor: not-allowed;
}

.wechat-button {
  margin-top: 16px;
  border: 1px solid #b8dec6;
  color: #137333;
  background: #f1fbf5;
}

.message {
  min-height: 42px;
  display: flex;
  align-items: center;
  margin: 18px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 760;
  line-height: 1.5;
}

.message.error {
  color: #b42318;
  background: #fff0ed;
  border: 1px solid #ffc8c0;
}

.message.success {
  color: #116b35;
  background: #edf9f0;
  border: 1px solid #b7e3c3;
}

.demo-note {
  margin: 18px 0 0;
  color: #6c7a92;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .brand-panel {
    min-height: 340px;
    place-items: end start;
    padding: 32px 24px;
  }

  .brand-frame {
    min-height: auto;
    padding: 28px;
  }

  .brand-logo--light {
    margin-bottom: 28px;
    font-size: 22px;
  }

  .brand-panel h1 {
    font-size: 38px;
  }

  .brand-description,
  .signal-strip {
    display: none;
  }

  .login-side {
    min-height: auto;
    place-items: start center;
    padding: 34px 20px 46px;
  }
}

@media (max-width: 500px) {
  .tabs,
  .sms-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .login-header h2 {
    font-size: 30px;
  }
}
</style>
