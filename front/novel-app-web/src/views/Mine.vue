<template>
  <div class="page">
    <AppHeader title="我的" />

    <section class="section page-card">
      <div class="mine-head">
        <img class="avatar" :src="avatarUrl" alt="用户头像" @error="useFallback" />
        <div>
          <h1 class="mine-name">{{ displayName }}</h1>
          <p class="mine-sub">{{ token ? '欢迎回来，继续你的阅读' : '登录后可发表评论和查看个人内容' }}</p>
        </div>
      </div>
      <div v-if="!token" class="button-row" style="margin-top: 16px;">
        <router-link class="primary-button" :to="{ name: 'login' }">登录</router-link>
        <router-link class="secondary-button" :to="{ name: 'register' }">注册</router-link>
      </div>
    </section>

    <section class="section menu-list">
      <router-link :to="{ name: 'comments' }">
        <span>我的评论</span>
        <span>查看</span>
      </router-link>
      <router-link :to="{ name: 'feedback' }">
        <span>意见反馈</span>
        <span>提交</span>
      </router-link>
      <router-link :to="{ name: 'discover' }">
        <span>继续找书</span>
        <span>去分类</span>
      </router-link>
      <button v-if="token" class="danger-button" type="button" @click="logout">
        <span>退出登录</span>
        <span></span>
      </button>
    </section>

    <BottomNav />
  </div>
</template>

<script>
import { computed, onMounted, reactive, toRefs } from 'vue'
import { useRouter } from 'vue-router'
import { getUserinfo } from '@/api/user'
import { getNickName, getToken, removeNickName, removeToken, removeUid } from '@/utils/auth'
import man from '@/assets/images/man.png'
import AppHeader from '@/components/AppHeader.vue'
import BottomNav from '@/components/BottomNav.vue'

export default {
  name: 'MineView',
  components: { AppHeader, BottomNav },
  setup() {
    const router = useRouter()
    const imgBaseUrl = process.env.VUE_APP_BASE_IMG_URL || ''
    const state = reactive({
      token: getToken(),
      nickName: getNickName(),
      userPhoto: ''
    })

    const displayName = computed(() => state.token ? (state.nickName || '书友') : '未登录')
    const avatarUrl = computed(() => state.userPhoto ? imgBaseUrl + state.userPhoto : man)

    const loadUser = async () => {
      if (!state.token) return
      try {
        const { data } = await getUserinfo()
        state.nickName = data.nickName || state.nickName
        state.userPhoto = data.userPhoto || ''
      } catch (error) {
        state.userPhoto = ''
      }
    }

    const logout = () => {
      removeToken()
      removeNickName()
      removeUid()
      state.token = ''
      state.nickName = ''
      router.push({ name: 'home' })
    }

    const useFallback = event => {
      event.target.src = man
    }

    onMounted(loadUser)

    return { ...toRefs(state), displayName, avatarUrl, logout, useFallback }
  }
}
</script>
