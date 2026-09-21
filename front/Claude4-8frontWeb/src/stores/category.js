import { defineStore } from 'pinia'
import { listCategory } from '@/api/book'

export const useCategoryStore = defineStore('category', {
  state: () => ({ map: { 0: [], 1: [] } }),
  actions: {
    async load(workDirection = 0) {
      if (this.map[workDirection] && this.map[workDirection].length) return this.map[workDirection]
      const { data } = await listCategory(workDirection)
      this.map[workDirection] = data || []
      return this.map[workDirection]
    }
  }
})
