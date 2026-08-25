<template>
  <div class="admin-page">
    <section class="admin-stat-grid">
      <article v-for="item in stats" :key="item.label" class="admin-stat-card">
        <div class="admin-stat-icon">
          <component :is="item.icon" />
        </div>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
          <span>{{ item.note }}</span>
        </div>
      </article>
    </section>

    <section class="admin-board">
      <div class="admin-board-main">
        <div class="admin-section-title">
          <h2>近期作品动态</h2>
          <router-link :to="{ name: 'adminBooks' }">查看全部</router-link>
        </div>
        <el-table :data="recentBooks" stripe class="admin-table" empty-text="暂无数据">
          <el-table-column prop="bookName" label="书名" min-width="160" />
          <el-table-column prop="authorName" label="作者" width="120" />
          <el-table-column prop="categoryName" label="分类" width="110" />
          <el-table-column prop="wordCount" label="字数" width="100" />
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column prop="statusText" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <aside class="admin-board-side">
        <div class="admin-section-title">
          <h2>待处理事项</h2>
        </div>
        <ul class="admin-todo-list">
          <li v-for="item in todos" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </li>
        </ul>
      </aside>
    </section>
  </div>
</template>

<script>
import {
  ChatDotRound,
  Collection,
  Reading,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { onMounted, reactive, toRefs } from 'vue'
import { getDashboard } from '@/api/admin'

const fallbackDashboard = {
  stats: {
    bookCount: 128,
    authorCount: 36,
    userCount: 4280,
    commentCount: 921,
    categoryCount: 18
  },
  recentBooks: [
    {
      bookName: '星河旅人',
      authorName: '青砚',
      categoryName: '玄幻',
      wordCount: '82万',
      updateTime: '2026-07-21 09:30',
      status: 1,
      statusText: '已上架'
    },
    {
      bookName: '云端书局',
      authorName: '白榆',
      categoryName: '都市',
      wordCount: '41万',
      updateTime: '2026-07-20 22:12',
      status: 0,
      statusText: '待审核'
    },
    {
      bookName: '旧梦长街',
      authorName: '林隐',
      categoryName: '言情',
      wordCount: '67万',
      updateTime: '2026-07-20 18:46',
      status: 1,
      statusText: '已上架'
    }
  ],
  todos: {
    pendingBooks: 12,
    pendingAuthors: 4,
    reportedComments: 19
  }
}

void fallbackDashboard

export default {
  name: 'AdminDashboard',
  components: {
    ChatDotRound,
    Collection,
    Reading,
    User,
    UserFilled
  },
  setup() {
    const state = reactive({
      stats: [],
      recentBooks: [],
      todos: []
    })

    const buildDashboard = (data) => {
      const rawStats = data.stats || data
      state.stats = [
        { label: '作品总量', value: rawStats.bookCount || 0, note: '全站小说', icon: Reading },
        { label: '入驻作家', value: rawStats.authorCount || 0, note: '创作者账号', icon: UserFilled },
        { label: '注册用户', value: rawStats.userCount || 0, note: '会员规模', icon: User },
        { label: '评论数量', value: rawStats.commentCount || 0, note: '内容互动', icon: ChatDotRound },
        { label: '作品分类', value: rawStats.categoryCount || 0, note: '频道配置', icon: Collection }
      ]
      state.recentBooks = data.recentBooks || []
      const todoSource = data.todos || {}
      state.todos = [
        { label: '待审核作品', value: todoSource.pendingBooks || 0 },
        { label: '待认证作家', value: todoSource.pendingAuthors || 0 },
        { label: '待处理评论', value: todoSource.reportedComments || 0 }
      ]
    }

    const load = async () => {
      try {
        const { data } = await getDashboard()
        buildDashboard(data || {})
      } catch (e) {
        return
      }
    }

    onMounted(load)

    return {
      ...toRefs(state)
    }
  }
}
</script>
