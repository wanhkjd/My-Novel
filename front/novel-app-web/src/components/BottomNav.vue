<template>
  <nav class="bottom-nav" aria-label="主要导航">
    <router-link v-for="item in items" :key="item.name" :to="{ name: item.name }" class="bottom-nav-item">
      <span class="nav-mark" :class="item.mark"></span>
      <span>{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script>
export default {
  name: 'BottomNav',
  setup() {
    const items = [
      { name: 'home', label: '书城', mark: 'mark-home' },
      { name: 'discover', label: '分类', mark: 'mark-grid' },
      { name: 'rank', label: '排行', mark: 'mark-rank' },
      { name: 'mine', label: '我的', mark: 'mark-user' }
    ]

    return { items }
  }
}
</script>
