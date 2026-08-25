import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('@/views/Home.vue'), meta: { tab: true, title: '书城' } },
  { path: '/discover', name: 'discover', component: () => import('@/views/Discover.vue'), meta: { tab: true, title: '分类' } },
  { path: '/rank', name: 'rank', component: () => import('@/views/Rank.vue'), meta: { tab: true, title: '排行' } },
  { path: '/mine', name: 'mine', component: () => import('@/views/Mine.vue'), meta: { tab: true, title: '我的' } },
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('@/views/Register.vue'), meta: { title: '注册' } },
  { path: '/feedback', name: 'feedback', component: () => import('@/views/Feedback.vue'), meta: { title: '意见反馈', requiresAuth: true } },
  { path: '/feadback', redirect: '/feedback' },
  { path: '/comments', name: 'comments', component: () => import('@/views/Comments.vue'), meta: { title: '我的评论', requiresAuth: true } },
  { path: '/book/:id', name: 'book', component: () => import('@/views/BookDetail.vue'), meta: { title: '书籍详情' } },
  { path: '/chapter_list/:bookId', name: 'chapterList', component: () => import('@/views/ChapterList.vue'), meta: { title: '目录' } },
  { path: '/book/:id/:chapterId', name: 'reader', component: () => import('@/views/Reader.vue'), meta: { title: '阅读' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !getToken()) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

router.afterEach(() => {
  window.scrollTo(0, 0)
})

export default router
