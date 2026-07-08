import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../api/session'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import SensitiveWordsView from '../views/SensitiveWordsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (isLoggedIn() ? '/index' : '/login')
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/home',
      name: 'home',
      redirect: '/index'
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
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/index'
    return redirect
  }

  return true
})

export default router
