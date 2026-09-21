<template>
  <div v-if="links.length" class="friend-links container">
    <span class="fl-label">友情链接：</span>
    <a v-for="(l, i) in links" :key="i" :href="l.linkUrl" target="_blank" rel="noopener">{{ l.linkName }}</a>
  </div>
</template>
<script setup>
defineProps({ links: { type: Array, default: () => [] } })
</script>
<style scoped>
.friend-links { padding: var(--sp-4) var(--sp-4) 0; display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
.fl-label { color: var(--text-3); font-size: 13px; }
.friend-links a { color: var(--text-2); font-size: 13px; }
.friend-links a:hover { color: var(--brand); }
</style>
