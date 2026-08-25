<template>
  <div class="empty-state">
    <div class="empty-symbol"></div>
    <p>{{ text }}</p>
  </div>
</template>

<script>
export default {
  name: 'EmptyState',
  props: {
    text: { type: String, default: '暂无内容' }
  }
}
</script>
