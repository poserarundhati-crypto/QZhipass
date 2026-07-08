<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isValidMobile, sendSmsCode } from '../api/auth'
import { useAuthStore } from '../stores/auth'

type LoginTab = 'phone' | 'email' | 'sms'

const INDEX_ROUTE = '/index'
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/
const PASSWORD_REQUIREMENT_MESSAGE = '密码不符合要求：必须至少8位，并包含大写字母、小写字母、数字和特殊字符'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

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

function ensureLoginFavicon() {
  if (typeof document === 'undefined' || document.querySelector('link[rel~="icon"]')) {
    return
  }

  const link = document.createElement('link')
  link.rel = 'icon'
  link.href = 'data:,'
  document.head.appendChild(link)
}

ensureLoginFavicon()

const normalizedPhoneMobile = computed(() => phoneForm.mobile.trim())
const normalizedEmail = computed(() => emailForm.email.trim())
const normalizedSmsMobile = computed(() => smsForm.mobile.trim())
const canSubmitPhone = computed(
  () => normalizedPhoneMobile.value.length > 0 && PASSWORD_PATTERN.test(phoneForm.password) && !submitting.value
)
const canSubmitEmail = computed(
  () => normalizedEmail.value.length > 0 && PASSWORD_PATTERN.test(emailForm.password) && !submitting.value
)
const hasInvalidSmsMobile = computed(
  () => normalizedSmsMobile.value.length > 0 && !isValidMobile(normalizedSmsMobile.value)
)
const canSendSms = computed(
  () => isValidMobile(normalizedSmsMobile.value) && countdown.value === 0 && !smsSending.value
)
const canSubmitSms = computed(
  () => isValidMobile(normalizedSmsMobile.value) && smsForm.code.trim().length > 0 && !submitting.value
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

function handlePasswordInputEvent(event: Event) {
  const password = event.target instanceof HTMLInputElement ? event.target.value : ''
  handlePasswordInput(password)
}

function handleSmsMobileInputEvent(event: Event) {
  const mobile = event.target instanceof HTMLInputElement ? event.target.value.trim() : ''

  if (!mobile) {
    clearMessage()
    return
  }

  if (!isValidMobile(mobile)) {
    showMessage('请输入正确的手机号', 'error')
    return
  }

  clearMessage()
}

async function redirectAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : INDEX_ROUTE
  const target = redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : INDEX_ROUTE

  showMessage('登录成功', 'success')
  await router.push(target)
}

function getErrorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

async function handlePhoneLogin() {
  if (!canSubmitPhone.value) {
    return
  }

  if (!validatePassword(phoneForm.password)) {
    return
  }

  submitting.value = true
  clearMessage()

  try {
    await authStore.passwordLogin(normalizedPhoneMobile.value, phoneForm.password)
    await redirectAfterLogin()
  } catch (error) {
    showMessage(getErrorText(error, '账号或密码错误'), 'error')
  } finally {
    submitting.value = false
  }
}

async function handleEmailLogin() {
  if (!canSubmitEmail.value) {
    return
  }

  if (!validatePassword(emailForm.password)) {
    return
  }

  submitting.value = true
  clearMessage()

  try {
    await authStore.emailPasswordLogin(normalizedEmail.value, emailForm.password)
    await redirectAfterLogin()
  } catch (error) {
    showMessage(getErrorText(error, '账号或密码错误'), 'error')
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
    if (!isValidMobile(normalizedSmsMobile.value)) {
      showMessage('请输入正确的手机号', 'error')
    }
    return
  }

  smsSending.value = true
  clearMessage()

  try {
    await sendSmsCode(normalizedSmsMobile.value)
    startCountdown()
    showMessage('验证码已发送', 'success')
  } catch (error) {
    showMessage(getErrorText(error, '验证码发送失败'), 'error')
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
    await authStore.smsLogin(normalizedSmsMobile.value, smsForm.code.trim())
    await redirectAfterLogin()
  } catch (error) {
    showMessage(getErrorText(error, '验证码登录失败'), 'error')
  } finally {
    submitting.value = false
  }
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
      <div class="brand-content">
        <div class="brand-emblem" aria-hidden="true">Q</div>
        <h1>企智通</h1>
        <p class="brand-subtitle">企业智能 AI 中枢</p>
        <p class="brand-slogan">让 AI 在企业内部安全创造价值</p>

        <div class="brand-tags" aria-label="产品能力">
          <span>统一认证</span>
          <span>模型可控</span>
          <span>数据不出</span>
          <span>高效协作</span>
        </div>

        <p class="brand-footer">员工能用 AI · 企业能管 AI · 数据不出企业</p>
      </div>
    </section>

    <section class="login-side" aria-label="登录区域">
      <div class="login-panel" data-testid="login-card">
        <header class="login-header">
          <div class="login-brand">
            <span class="login-brand-mark" aria-hidden="true">Q</span>
            <span>企智通</span>
          </div>
          <h2>登录企智通</h2>
          <p>选择企业账号登录方式。</p>
        </header>

        <div class="tabs" role="tablist" aria-label="登录方式">
          <button type="button" :class="{ active: activeTab === 'phone' }" :disabled="submitting" @click="setActiveTab('phone')">
            手机号密码
          </button>
          <button type="button" :class="{ active: activeTab === 'sms' }" :disabled="submitting" @click="setActiveTab('sms')">
            手机验证码
          </button>
          <button type="button" :class="{ active: activeTab === 'email' }" :disabled="submitting" @click="setActiveTab('email')">
            邮箱密码
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
              @input="handlePasswordInputEvent"
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
              @input="handlePasswordInputEvent"
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
              @input="handleSmsMobileInputEvent"
            />
            <small v-if="hasInvalidSmsMobile" class="field-hint">请输入正确的手机号</small>
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
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(390px, 0.96fr);
  overflow-x: hidden;
  color: #0f172a;
  background: #f8fafc;
}

.brand-panel {
  position: relative;
  min-height: 100vh;
  display: grid;
  align-items: center;
  padding: clamp(40px, 7vw, 96px);
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(circle at 18% 18%, rgba(56, 189, 248, 0.34), transparent 28%),
    radial-gradient(circle at 86% 72%, rgba(6, 182, 212, 0.2), transparent 30%),
    linear-gradient(150deg, #1e3a8a 0%, #2563eb 48%, #0f172a 100%);
  background-size: 120% 120%, 120% 120%, 100% 100%;
  animation: brand-glow 11s ease-in-out infinite alternate;
}

.brand-panel::before {
  position: absolute;
  inset: 0;
  content: "";
  background:
    linear-gradient(90deg, rgba(248, 250, 252, 0.13) 1px, transparent 1px),
    linear-gradient(rgba(248, 250, 252, 0.1) 1px, transparent 1px);
  background-position: 0 0;
  background-size: 58px 58px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.88), rgba(0, 0, 0, 0.2));
  animation: grid-drift 18s linear infinite;
}

.brand-panel::after {
  position: absolute;
  right: -20vw;
  bottom: 11vh;
  width: 68vw;
  height: 190px;
  content: "";
  border-top: 1px solid rgba(186, 230, 253, 0.42);
  border-bottom: 1px solid rgba(186, 230, 253, 0.2);
  background: linear-gradient(90deg, transparent, rgba(224, 242, 254, 0.18), transparent);
  transform: rotate(-16deg);
  animation: light-sweep 7s ease-in-out infinite alternate;
}

.brand-content {
  position: relative;
  z-index: 1;
  width: min(100%, 620px);
}

.brand-content::before,
.brand-content::after {
  position: absolute;
  content: "";
  pointer-events: none;
}

.brand-content::before {
  right: 6%;
  top: -12%;
  width: 150px;
  height: 150px;
  border: 1px solid rgba(224, 242, 254, 0.3);
  border-radius: 8px;
  transform: rotate(12deg);
  animation: float-panel 8s ease-in-out infinite alternate;
}

.brand-content::after {
  left: 42%;
  bottom: -16%;
  width: 240px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(224, 242, 254, 0.72), transparent);
  animation: scan-line 5s ease-in-out infinite;
}

.brand-emblem {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin-bottom: 34px;
  border: 1px solid rgba(248, 250, 252, 0.5);
  border-radius: 8px;
  color: #f8fafc;
  background: rgba(15, 23, 42, 0.18);
  font-size: 28px;
  font-weight: 900;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16);
  animation: emblem-glow 4s ease-in-out infinite alternate;
}

.brand-content h1 {
  margin: 0;
  font-size: clamp(54px, 7vw, 92px);
  line-height: 1;
  letter-spacing: 0;
}

.brand-subtitle {
  margin: 16px 0 0;
  color: #dbeafe;
  font-size: clamp(20px, 2vw, 28px);
  font-weight: 800;
  letter-spacing: 0;
}

.brand-slogan {
  max-width: 560px;
  margin: 22px 0 0;
  color: rgba(248, 250, 252, 0.96);
  font-size: clamp(24px, 3vw, 40px);
  font-weight: 850;
  line-height: 1.24;
}

.brand-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.brand-tags span {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 0 16px;
  border: 1px solid rgba(224, 242, 254, 0.46);
  border-radius: 8px;
  color: #f8fafc;
  background: rgba(15, 23, 42, 0.18);
  font-size: 15px;
  font-weight: 800;
  white-space: nowrap;
  backdrop-filter: blur(10px);
}

.brand-footer {
  margin: 44px 0 0;
  color: rgba(248, 250, 252, 0.88);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.6;
}

.login-side {
  min-width: 0;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: clamp(28px, 6vw, 76px);
}

.login-panel {
  width: min(100%, 460px);
  min-width: 0;
  padding: clamp(28px, 4vw, 40px);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 22px 54px rgba(15, 23, 42, 0.1);
}

.login-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 850;
}

.login-brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  font-size: 18px;
  font-weight: 900;
}

.login-header h2 {
  margin: 28px 0 8px;
  color: #0f172a;
  font-size: 34px;
  line-height: 1.14;
  letter-spacing: 0;
}

.login-header p {
  margin: 0;
  color: #64748b;
  font-size: 15px;
  line-height: 1.7;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: 28px 0 24px;
  padding: 4px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f1f5f9;
}

.tabs button {
  min-width: 0;
  min-height: 42px;
  border: 0;
  border-radius: 6px;
  color: #64748b;
  background: transparent;
  font: inherit;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: color 160ms ease, background 160ms ease, box-shadow 160ms ease;
}

.tabs button.active {
  color: #2563eb;
  background: #ffffff;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.12);
}

.tabs button:disabled {
  cursor: not-allowed;
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
  color: #0f172a;
  font-size: 14px;
  font-weight: 780;
}

.field input {
  width: 100%;
  min-height: 52px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  outline: none;
  color: #0f172a;
  background: #ffffff;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.field input::placeholder {
  color: #94a3b8;
}

.field input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.field-hint {
  color: #b42318;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
}

.sms-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 10px;
}

.primary-button,
.secondary-button {
  width: 100%;
  min-height: 52px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 850;
  opacity: 1;
}

.primary-button {
  margin-top: 4px;
  border: 0;
  color: #ffffff;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  box-shadow: 0 18px 30px rgba(37, 99, 235, 0.24);
  cursor: pointer;
  transition: background 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.primary-button:not(:disabled):hover {
  background: linear-gradient(135deg, #1d4ed8, #0891b2);
  box-shadow: 0 20px 34px rgba(37, 99, 235, 0.28);
  transform: translateY(-1px);
}

.primary-button:disabled {
  color: #64748b;
  background: #cbd5e1;
  box-shadow: none;
  cursor: not-allowed;
  opacity: 1;
  transform: none;
}

.secondary-button {
  border: 1px solid #bfdbfe;
  color: #2563eb;
  background: #eff6ff;
  cursor: pointer;
}

.secondary-button:not(:disabled):hover {
  color: #1d4ed8;
  background: #dbeafe;
}

.secondary-button:disabled {
  color: #64748b;
  background: #e2e8f0;
  border-color: #cbd5e1;
  cursor: not-allowed;
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

@keyframes grid-drift {
  from {
    background-position: 0 0;
  }

  to {
    background-position: 58px 58px;
  }
}

@keyframes brand-glow {
  from {
    background-position: 0% 0%, 100% 100%, 0 0;
  }

  to {
    background-position: 12% 8%, 86% 92%, 0 0;
  }
}

@keyframes float-panel {
  from {
    opacity: 0.28;
    transform: translate3d(0, 0, 0) rotate(12deg);
  }

  to {
    opacity: 0.58;
    transform: translate3d(14px, 18px, 0) rotate(18deg);
  }
}

@keyframes scan-line {
  0% {
    opacity: 0;
    transform: translateX(-28px);
  }

  42% {
    opacity: 0.65;
  }

  100% {
    opacity: 0;
    transform: translateX(44px);
  }
}

@keyframes emblem-glow {
  from {
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.16),
      0 0 0 rgba(224, 242, 254, 0);
  }

  to {
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.16),
      0 14px 34px rgba(56, 189, 248, 0.24);
  }
}

@keyframes light-sweep {
  from {
    opacity: 0.5;
    transform: translateX(-12px) rotate(-16deg);
  }

  to {
    opacity: 0.9;
    transform: translateX(18px) rotate(-16deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-panel,
  .brand-content::before,
  .brand-content::after,
  .brand-emblem,
  .brand-panel::before,
  .brand-panel::after {
    animation: none;
  }
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .brand-panel {
    min-height: 390px;
    padding: 34px 24px;
  }

  .brand-emblem {
    margin-bottom: 24px;
  }

  .brand-content h1 {
    font-size: 46px;
  }

  .brand-subtitle {
    font-size: 20px;
  }

  .brand-slogan {
    max-width: 500px;
    font-size: 28px;
  }

  .brand-footer {
    margin-top: 30px;
  }

  .login-side {
    min-height: auto;
    place-items: start center;
    padding: 34px 20px 48px;
  }
}

@media (max-width: 520px) {
  .brand-panel {
    min-height: 360px;
  }

  .brand-slogan {
    font-size: 24px;
  }

  .brand-tags {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, max-content));
    gap: 10px;
  }

  .brand-tags span {
    min-height: 36px;
    padding: 0 12px;
  }

  .brand-footer {
    font-size: 14px;
  }

  .login-panel {
    padding: 26px 20px;
  }

  .login-header h2 {
    font-size: 30px;
  }

  .tabs,
  .sms-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
