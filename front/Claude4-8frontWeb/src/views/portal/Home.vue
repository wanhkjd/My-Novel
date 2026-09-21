<template>
  <div class="home">
    <div class="container home-layout">
      <div class="home-main">
        <HomeRecommend :books="homeBooks" />
      </div>
      <aside class="home-side">
        <RankPanel title="点击榜" :books="visitRank" />
        <RankPanel title="新书榜" :books="newestRank" />
        <RankPanel title="更新榜" :books="updateRank" />
        <LatestNews :news="news" />
      </aside>
    </div>
    <FriendLinks :links="links" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listHomeBooks, listHomeFriendLinks } from '@/api/home'
import { listVisitRankBooks, listNewestRankBooks, listUpdateRankBooks } from '@/api/book'
import { listLatestNews } from '@/api/news'
import HomeRecommend from '@/components/home/HomeRecommend.vue'
import RankPanel from '@/components/home/RankPanel.vue'
import LatestNews from '@/components/home/LatestNews.vue'
import FriendLinks from '@/components/home/FriendLinks.vue'

const homeBooks = ref([]), visitRank = ref([]), newestRank = ref([])
const updateRank = ref([]), news = ref([]), links = ref([])

async function safe(fn, target) {
  try { const { data } = await fn(); target.value = data || [] } catch (e) { /* 拦截器已提示 */ }
}
onMounted(() => {
  safe(listHomeBooks, homeBooks)
  safe(listVisitRankBooks, visitRank)
  safe(listNewestRankBooks, newestRank)
  safe(listUpdateRankBooks, updateRank)
  safe(listLatestNews, news)
  safe(listHomeFriendLinks, links)
})
</script>

<style scoped>
.home-layout { display: grid; grid-template-columns: 1fr 300px; gap: var(--sp-5); padding-top: var(--sp-5); }
.home-side { display: flex; flex-direction: column; gap: var(--sp-4); }
@media (max-width: 900px) { .home-layout { grid-template-columns: 1fr; } }
</style>
