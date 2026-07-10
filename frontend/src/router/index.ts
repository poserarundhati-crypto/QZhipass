import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../api/session'
import ChatView from '../views/ChatView.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import SensitiveWordsView from '../views/SensitiveWordsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (isLoggedIn() ? '/chat' : '/login')
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView,
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/index',
      name: 'index',
      component: ChatView,
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/chat/:sessionId',
      name: 'chat-session',
      component: ChatView,
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/chat',
      name: 'chat',
      component: ChatView,
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/admin/sensitive-words',
      name: 'sensitive-words',
      component: SensitiveWordsView,
      meta: {
        requiresAuth: true
      }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login'
    }
  ]
})

router.beforeEach(to => {
  const authed = isLoggedIn()
  const requiresAuth = to.matched.some(route => route.meta.requiresAuth)

  if (requiresAuth && !authed) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.path === '/login' && authed) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/chat'
    return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/chat'
  }

  return true
})

export default router
