import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import WorkbenchView from './views/WorkbenchView.vue'
import { session } from './session'

// 用 hash 路由：部署到任意静态服务器/反向代理都不用配 rewrite 规则
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/workbench', name: 'workbench', component: WorkbenchView }
  ]
})

router.beforeEach((to) => {
  if (!to.meta.public && !session.isLoggedIn) {
    return { name: 'login' }
  }
  if (to.name === 'login' && session.isLoggedIn) {
    return { name: 'workbench' }
  }
  return true
})

export default router
