<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <router-link class="admin-brand" :to="{ name: 'adminDashboard' }">
        <img src="@/assets/images/logo_white.png" alt="小说精品屋" />
        <span>平台管理端</span>
      </router-link>
      <nav class="admin-nav" aria-label="平台管理">
        <router-link
          v-for="item in menus"
          :key="item.name"
          :to="{ name: item.name }"
          class="admin-nav-item"
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>
    <section class="admin-main">
      <header class="admin-topbar">
        <div>
          <p>小说精品屋</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="admin-account">
          <el-button :icon="House" @click="goHome">门户首页</el-button>
          <el-button type="primary" color="#f80" plain @click="logout">退出</el-button>
        </div>
      </header>
      <main class="admin-content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<script>
import '@/assets/styles/admin.css'
import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  Document,
  House,
  Link,
  Reading,
  Setting,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { removeNickName, removeToken, removeUid } from '@/utils/auth'

export default {
  name: 'AdminLayout',
  setup() {
    const route = useRoute()
    const router = useRouter()
    const menus = [
      { name: 'adminDashboard', label: '数据总览', icon: DataAnalysis },
      { name: 'adminBooks', label: '小说管理', icon: Reading },
      { name: 'adminCategories', label: '分类管理', icon: Collection },
      { name: 'adminAuthors', label: '作家管理', icon: UserFilled },
      { name: 'adminUsers', label: '用户管理', icon: User },
      { name: 'adminNews', label: '新闻管理', icon: Document },
      { name: 'adminComments', label: '评论管理', icon: ChatDotRound },
      { name: 'adminHomeBooks', label: '首页推荐', icon: House },
      { name: 'adminFriendLinks', label: '友情链接', icon: Link },
      { name: 'adminSettings', label: '系统设置', icon: Setting }
    ]
    const pageTitle = computed(() => route.meta.title || '平台管理端')

    const goHome = () => {
      router.push({ name: 'home' })
    }

    const logout = () => {
      removeToken()
      removeNickName()
      removeUid()
      router.push({ name: 'adminLogin' })
    }

    return {
      House,
      menus,
      pageTitle,
      goHome,
      logout
    }
  }
}
</script>
