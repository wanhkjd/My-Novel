import { createRouter, createWebHistory } from 'vue-router'
import { getReaderToken } from '@/utils/auth'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/PortalLayout.vue'),
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'home', component: () => import('@/views/portal/Home.vue') },
      { path: 'search', name: 'search', component: () => import('@/views/portal/Search.vue') },
      { path: 'category', name: 'category', component: () => import('@/views/portal/BookClass.vue') },
      { path: 'rank', name: 'rank', component: () => import('@/views/portal/BookRank.vue') },
      { path: 'book/:id', name: 'bookDetail', component: () => import('@/views/portal/BookDetail.vue') },
      { path: 'book/:id/catalog', name: 'chapterList', component: () => import('@/views/portal/ChapterList.vue') },
      { path: 'news/:id', name: 'news', component: () => import('@/views/portal/News.vue') },
      { path: 'login', name: 'login', component: () => import('@/views/portal/Login.vue') },
      { path: 'register', name: 'register', component: () => import('@/views/portal/Register.vue') },
      { path: 'user/bookshelf', name: 'bookshelf', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Bookshelf.vue') },
      { path: 'user/comments', name: 'myComments', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/MyComments.vue') },
      { path: 'user/feedback', name: 'feedback', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Feedback.vue') },
      { path: 'user/profile', name: 'profile', meta: { requiresAuth: true }, component: () => import('@/views/portal/user/Profile.vue') }
    ]
  },
  // 阅读页独立布局（无门户导航），沉浸式阅读
  { path: '/read/:chapterId', name: 'reader', component: () => import('@/views/portal/BookReader.vue') }
  // 第 2 期：/author/*   第 3 期：/admin/*
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !getReaderToken()) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
