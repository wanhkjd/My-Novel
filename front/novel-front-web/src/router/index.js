import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const router = createRouter({
  // createWebHistory 路由模式路径不带#号(生产环境下不能直接访问项目，需要 nginx 转发)
  // createWebHashHistory 路由模式路径带#号
  history: createWebHashHistory(), 
  routes: [
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/home',
      name: 'home',
      component: () => import('@/views/Home')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register')
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login')
    },
    {
      path: '/feadback',
      name: 'feadback',
      component: () => import('@/views/FeadBack')
    },
    {
      path: '/news/:id',
      name: 'news',
      component: () => import('@/views/News')
    },
    {
      path: '/bookclass',
      name: 'bookclass',
      component: () => import('@/views/BookClass')
    },
    {
      path: '/book_rank',
      name: 'bookRank',
      component: () => import('@/views/BookRank')
    },
    {
      path: '/book/:id',
      name: 'book',
      component: () => import('@/views/Book')
	   
    },
    {
      path: '/chapter_list/:bookId',
      name: 'chapterList',
      component: () => import('@/views/ChapterList')
	   
    },
    {
      path: '/book/:id/:chapterId',
      name: 'bookContent',
      component: () => import('@/views/BookContent')
	   
    },
    {
      path: '/user/setup',
      name: 'userSetup',
      component: () => import('@/views/UserSetup')
    },
    {
      path: '/user/comment',
      name: 'userComment',
      component: () => import('@/views/UserComment')
	   
    },
    {
      path: '/author/register',
      name: 'authorRegister',
      component: () => import('@/views/author/Register')
    },
    {
      path: '/author/book_list',
      name: 'authorBookList',
      component: () => import('@/views/author/BookList')
    },
    {
      path: '/author/book_add',
      name: 'authorBookAdd',
      component: () => import('@/views/author/BookAdd')
    },
    {
      path: '/author/chapter_list',
      name: 'authorChapterList',
      component: () => import('@/views/author/ChapterList')
    },
    {
      path: '/author/chapter_add',
      name: 'authorChapterAdd',
      component: () => import('@/views/author/ChapterAdd')
    },
    {
      path: '/author/chapter_update',
      name: 'authorChapterUpdate',
      component: () => import('@/views/author/ChapterUpdate')
    },
    {
      path: '/admin/login',
      name: 'adminLogin',
      component: () => import('@/views/admin/Login')
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/Layout'),
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'adminDashboard',
          component: () => import('@/views/admin/Dashboard'),
          meta: { title: '数据总览' }
        },
        {
          path: 'books',
          name: 'adminBooks',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '小说管理', resource: 'books' }
        },
        {
          path: 'categories',
          name: 'adminCategories',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '分类管理', resource: 'book/categories' }
        },
        {
          path: 'authors',
          name: 'adminAuthors',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '作家管理', resource: 'authors' }
        },
        {
          path: 'users',
          name: 'adminUsers',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '用户管理', resource: 'users' }
        },
        {
          path: 'news',
          name: 'adminNews',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '新闻管理', resource: 'news' }
        },
        {
          path: 'comments',
          name: 'adminComments',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '评论管理', resource: 'comments' }
        },
        {
          path: 'home-books',
          name: 'adminHomeBooks',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '首页推荐', resource: 'home-books' }
        },
        {
          path: 'friend-links',
          name: 'adminFriendLinks',
          component: () => import('@/views/admin/ResourceManage'),
          meta: { title: '友情链接', resource: 'friend-links' }
        },
        {
          path: 'settings',
          name: 'adminSettings',
          component: () => import('@/views/admin/Settings'),
          meta: { title: '系统设置' }
        }
      ]
    }
  ]
})

// 解决 vue 中路由跳转时，总是从新页面中间开始显示
router.afterEach((to,from,next) => {
  window.scrollTo(0,0)
})

router.beforeEach((to, from, next) => {
  if (to.path.startsWith('/admin') && to.name !== 'adminLogin' && !getToken()) {
    next({ name: 'adminLogin' })
    return
  }
  if (to.name === 'adminLogin' && getToken()) {
    next({ name: 'adminDashboard' })
    return
  }
  next()
})

export default router
