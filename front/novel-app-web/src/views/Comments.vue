<template>
  <div class="page no-tab">
    <AppHeader title="我的评论" back />

    <section class="section page-card">
      <EmptyState v-if="!loading && total === 0" text="你还没有发表过评论" />
      <article v-for="item in comments" :key="item.id" class="comment-item">
        <p class="comment-title">{{ item.commentBook }}</p>
        <p class="comment-content" v-html="item.commentContent"></p>
        <span class="comment-time">{{ item.commentTime }}</span>
      </article>
      <button v-if="comments.length < total" class="secondary-button load-more" type="button" @click="loadMore">加载更多</button>
    </section>
  </div>
</template>

<script>
import { onMounted, reactive, toRefs } from 'vue'
import { listComments } from '@/api/user'
import AppHeader from '@/components/AppHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

export default {
  name: 'CommentsView',
  components: { AppHeader, EmptyState },
  setup() {
    const state = reactive({
      comments: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      loading: false
    })

    const load = async (append = false) => {
      state.loading = true
      try {
        const pageNum = append ? state.pageNum + 1 : 1
        const { data } = await listComments({ pageNum, pageSize: state.pageSize })
        state.pageNum = data.pageNum || pageNum
        state.total = Number(data.total || 0)
        state.comments = append ? state.comments.concat(data.list || []) : (data.list || [])
      } finally {
        state.loading = false
      }
    }

    const loadMore = () => load(true)

    onMounted(() => load())

    return { ...toRefs(state), loadMore }
  }
}
</script>
