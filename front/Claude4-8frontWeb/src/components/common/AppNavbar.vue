<template>
  <header class="navbar">
    <div class="container nav-inner">
      <router-link to="/home" class="logo">
        <span class="logo-mark">墨</span><span class="logo-text">墨书</span>
      </router-link>
      <nav class="nav-links">
        <router-link to="/home">首页</router-link>
        <router-link to="/category">分类</router-link>
        <router-link to="/rank">榜单</router-link>
      </nav>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索书名 / 作者" @keyup.enter="doSearch" clearable>
          <template #suffix><el-icon @click="doSearch" style="cursor:pointer"><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="user-area">
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="onCommand">
            <span class="user-trigger">{{ userStore.nickName || '我的' }}<el-icon><ArrowDown /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="bookshelf">我的书架</el-dropdown-item>
                <el-dropdown-item command="comments">我的评论</el-dropdown-item>
                <el-dropdown-item command="feedback">意见反馈</el-dropdown-item>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-text">登录</router-link>
          <router-link to="/register" class="btn-brand">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const doSearch = () => router.push({ name: 'search', query: { keyword: keyword.value } })
const onCommand = (cmd) => {
  if (cmd === 'logout') { userStore.logout(); router.push('/home'); return }
  const map = { bookshelf: '/user/bookshelf', comments: '/user/comments', feedback: '/user/feedback', profile: '/user/profile' }
  router.push(map[cmd])
}
</script>

<style scoped>
.navbar { background: var(--surface); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 40; }
.nav-inner { display: flex; align-items: center; gap: var(--sp-5); height: 64px; }
.logo { display: flex; align-items: center; gap: 8px; }
.logo-mark { width: 34px; height: 34px; background: var(--brand); color: #fff; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 20px; }
.logo-text { font-family: var(--font-serif); font-size: 22px; font-weight: 700; }
.nav-links { display: flex; gap: var(--sp-4); }
.nav-links a { color: var(--text-2); padding: 4px 0; }
.nav-links a.router-link-active { color: var(--brand); border-bottom: 2px solid var(--brand); }
.search-box { flex: 1; max-width: 360px; }
.user-area { display: flex; align-items: center; gap: var(--sp-3); }
.user-trigger { display: flex; align-items: center; gap: 4px; cursor: pointer; color: var(--text); }
.btn-text { color: var(--text-2); }
.btn-brand { background: var(--brand); color: #fff; padding: 6px 16px; border-radius: var(--radius-sm); }
</style>
