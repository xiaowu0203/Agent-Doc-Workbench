import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    name: 'Agent-Doc-Workbench',
    version: '0.1.0',
  }),
})
