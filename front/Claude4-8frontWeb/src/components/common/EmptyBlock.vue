<template>
  <div class="empty-block">
    <el-icon :size="48" color="#c9c2b8"><Document /></el-icon>
    <p>{{ text }}</p>
  </div>
</template>

<script setup>
defineProps({ text: { type: String, default: '暂无数据' } })
</script>

<style scoped>
.empty-block { text-align: center; padding: 48px 0; color: var(--text-3); }
.empty-block p { margin-top: 12px; }
</style>
