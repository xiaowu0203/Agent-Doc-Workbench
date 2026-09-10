import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

import PermissionGate from './PermissionGate.vue'

describe('PermissionGate', () => {
  it('renders the default slot only when the current space grants permission', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const workspaceStore = useWorkspaceStore()
    workspaceStore.setCurrentSpace(1001)
    workspaceStore.setEffectivePermissions({
      spaceId: 1001,
      platformSuperAdmin: false,
      role: null,
      permissions: [SPACE_PERMISSIONS.SKILL_READ],
    })

    const wrapper = mount(PermissionGate, {
      props: { permission: SPACE_PERMISSIONS.SKILL_READ },
      slots: {
        default: '可查看',
        fallback: '无权限',
      },
      global: { plugins: [pinia] },
    })

    expect(wrapper.text()).toBe('可查看')

    await wrapper.setProps({ permission: SPACE_PERMISSIONS.SKILL_MANAGE })
    expect(wrapper.text()).toBe('无权限')
  })
})
