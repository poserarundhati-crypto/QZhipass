<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const userId = computed(() => window.localStorage.getItem('user_id') || '未登录')
const accessToken = computed(() => window.localStorage.getItem('access_token') || '未写入')

function logout() {
  window.localStorage.removeItem('user_id')
  window.localStorage.removeItem('access_token')
  router.push('/login')
}
</script>

<template>
  <main class="home-page">
    <header class="home-header">
      <div class="brand-logo">
        <span class="brand-mark">Q</span>
        <span>企智通</span>
      </div>
      <button class="logout-button" type="button" @click="logout">退出登录</button>
    </header>

    <section class="home-content" aria-label="企智通首页">
      <span class="home-status">登录成功</span>
      <p class="eyebrow">QZhipass</p>
      <h1>欢迎进入企智通</h1>
      <p class="welcome-text">当前用户 ID：{{ userId }}</p>
      <div class="home-summary" aria-label="mock 首页信息">
        <div>
          <strong>企业智能通行入口</strong>
          <span>前端演示模式已启用</span>
        </div>
        <div>
          <strong>access_token</strong>
          <span>{{ accessToken }}</span>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 28px clamp(20px, 5vw, 72px) 54px;
  color: #17233c;
  background:
    radial-gradient(circle at 78% 12%, rgba(35, 211, 255, 0.18), transparent 28%),
    linear-gradient(90deg, rgba(0, 47, 167, 0.08) 1px, transparent 1px),
    linear-gradient(rgba(0, 47, 167, 0.08) 1px, transparent 1px),
    #f7f9fc;
  background-size: auto, 48px 48px, 48px 48px, auto;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #14233d;
  font-size: 22px;
  font-weight: 850;
}

.brand-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid #c6d4e7;
  border-radius: 8px;
  color: #002fa7;
  background: #ffffff;
  font-size: 21px;
  font-weight: 900;
}

.logout-button {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid #b9cdf0;
  border-radius: 8px;
  color: #002fa7;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.home-content {
  width: min(100%, 820px);
  margin-top: clamp(78px, 12vw, 150px);
}

.home-status {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #b7e3c3;
  border-radius: 8px;
  color: #116b35;
  background: #edf9f0;
  font-size: 14px;
  font-weight: 800;
}

.eyebrow {
  margin: 24px 0 14px;
  color: #002fa7;
  font-size: 14px;
  font-weight: 850;
}

.home-content h1 {
  margin: 0;
  color: #0f2345;
  font-size: clamp(48px, 8vw, 90px);
  line-height: 1.02;
  letter-spacing: 0;
}

.welcome-text {
  margin: 26px 0 0;
  color: #526176;
  font-size: 18px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.home-summary {
  width: min(100%, 560px);
  display: grid;
  gap: 10px;
  margin-top: 30px;
}

.home-summary div {
  display: grid;
  gap: 5px;
  padding: 16px;
  border: 1px solid #d8e2ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
}

.home-summary strong {
  color: #17233c;
  font-size: 15px;
}

.home-summary span {
  color: #60708a;
  font-size: 14px;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .home-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .home-content h1 {
    font-size: 42px;
  }
}
</style>
