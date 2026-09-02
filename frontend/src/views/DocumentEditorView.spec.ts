import { createPinia, setActivePinia } from 'pinia'
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import DocumentEditorView from './DocumentEditorView.vue'

import * as agentApi from '@/features/agent/api/agent-api'
import * as documentApi from '@/features/document/api/document-api'
import * as taskApi from '@/features/task/api/task-api'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/agent/api/agent-api', () => ({
  listAgents: vi.fn(),
}))

vi.mock('@/features/document/api/document-api', () => ({
  archiveDocument: vi.fn(),
  archiveDirectory: vi.fn(),
  clearDocumentDraft: vi.fn(),
  createDocument: vi.fn(),
  getDocument: vi.fn(),
  getDocumentDraft: vi.fn(),
  getDocumentVersion: vi.fn(),
  listDocumentActivities: vi.fn(),
  listDocumentTree: vi.fn(),
  listDocumentVersions: vi.fn(),
  moveDirectory: vi.fn(),
  moveDocument: vi.fn(),
  readDocumentImage: vi.fn(),
  rollbackDocumentVersion: vi.fn(),
  saveDocumentDraft: vi.fn(),
  uploadDocumentImage: vi.fn(),
  updateDirectory: vi.fn(),
  updateDocument: vi.fn(),
}))

vi.mock('@/features/task/api/task-api', () => ({
  createTask: vi.fn(),
}))

const detail = {
  id: 101,
  spaceId: 7,
  directoryId: null,
  title: '产品上线方案',
  docType: 'FORMAL' as const,
  content: '# 方案\n\n正文',
  version: 3,
  status: 'NORMAL' as const,
  updatedAt: '2026-09-01T10:00:00Z',
  updatedBy: 2,
  createdBy: 1,
  creatorName: '张三',
}

const tree = [
  {
    id: 101,
    parentId: null,
    title: '产品上线方案',
    docType: 'FORMAL' as const,
    nodeType: 'DOCUMENT' as const,
    children: [
      {
        id: 103,
        parentId: 101,
        title: '临时草稿',
        docType: 'DRAFT' as const,
        nodeType: 'DOCUMENT' as const,
        children: [],
      },
    ],
  },
  {
    id: 102,
    parentId: null,
    title: '会议记录草稿',
    docType: 'DRAFT' as const,
    nodeType: 'DOCUMENT' as const,
    children: [],
  },
]

async function mountDocument(
  permissions = Object.values(SPACE_PERMISSIONS),
  documentId: number | string = 101,
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.spaces = [
    {
      id: 7,
      name: '产品研发空间',
      description: '文档协作空间',
      ownerId: 1,
      tokenBudget: null,
      status: 'ACTIVE',
      role: null,
      platformSuperAdmin: false,
      createdAt: '2026-08-31T00:00:00Z',
    },
  ]
  workspaceStore.setCurrentSpace(7)
  workspaceStore.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions,
  })

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/spaces/:spaceId/documents/:documentId?',
        name: 'space-documents',
        component: DocumentEditorView,
      },
    ],
  })
  await router.push({ name: 'space-documents', params: { spaceId: 7, documentId } })
  await router.isReady()
  const wrapper = mount(DocumentEditorView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(documentApi.listDocumentTree).mockResolvedValue(tree)
  vi.mocked(documentApi.getDocument).mockResolvedValue(detail)
  vi.mocked(documentApi.getDocumentDraft).mockResolvedValue(null)
  vi.mocked(documentApi.saveDocumentDraft).mockResolvedValue({
    documentId: detail.id,
    baseVersion: detail.version,
    title: detail.title,
    content: detail.content,
  })
  vi.mocked(documentApi.clearDocumentDraft).mockResolvedValue()
  vi.mocked(documentApi.listDocumentVersions).mockResolvedValue({
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 100,
  })
  vi.mocked(documentApi.listDocumentActivities).mockResolvedValue({
    records: [
      {
        id: 201,
        type: 'TASK',
        title: '文档审计',
        status: '运行中',
        sourceTaskId: 201,
        operatorName: '李四',
        activityAt: '2026-09-01T10:10:00Z',
      },
    ],
    total: 1,
    pageNum: 1,
    pageSize: 8,
  })
  vi.mocked(documentApi.updateDocument).mockResolvedValue(detail)
  vi.mocked(agentApi.listAgents).mockResolvedValue([
    { id: 301, spaceId: 7, name: '文档审阅 Agent', status: 'ENABLED' },
  ])
  vi.mocked(taskApi.createTask).mockResolvedValue({ id: 401 })
})

describe('DocumentEditorView', () => {
  it('renders the document tree, creator as responsible person, and related activities', async () => {
    const wrapper = await mountDocument()

    expect(wrapper.text()).toContain('文档目录')
    expect(wrapper.text()).toContain('3 篇文档 · 0 个目录')
    expect(wrapper.text()).toContain('产品上线方案')
    expect(wrapper.text()).not.toContain('临时草稿')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('文档审计')
    expect(wrapper.text()).toContain('正式文档：Agent 修改将生成变更请求')
  })

  it('opens document details in an inline dialog', async () => {
    const wrapper = await mountDocument()
    const detailButton = wrapper
      .findAll('.document-info-card__button')
      .find((button) => button.text() === '查看详情')

    expect(detailButton).toBeDefined()
    await detailButton!.trigger('click')
    await nextTick()

    const detailList = document.body.querySelector('.document-detail')
    expect(detailList?.textContent).toContain('产品上线方案')
    expect(detailList?.textContent).toContain('最后修改人')
    expect(detailList?.textContent).toContain('张三')
    expect(detailList?.textContent).toContain('文档 ID')
  })

  it('polls active document activities and stops after the task completes', async () => {
    vi.useFakeTimers()
    try {
      let activityCalls = 0
      vi.mocked(documentApi.listDocumentActivities).mockImplementation(async () => {
        activityCalls += 1
        const status = activityCalls === 1 ? '运行中' : '已完成'
        return {
          records: [
            {
              id: 201,
              type: 'TASK',
              title: '文档审计',
              status,
              sourceTaskId: 201,
              operatorName: '李四',
              activityAt: '2026-09-01T10:10:00Z',
            },
          ],
          total: 1,
          pageNum: 1,
          pageSize: 8,
        }
      })
      const wrapper = await mountDocument()
      await flushPromises()

      expect(activityCalls).toBe(1)
      await vi.advanceTimersByTimeAsync(5000)
      await flushPromises()
      expect(activityCalls).toBe(2)

      await vi.advanceTimersByTimeAsync(5000)
      expect(activityCalls).toBe(2)
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('renames a document after double-clicking its title', async () => {
    const wrapper = await mountDocument()

    const title = wrapper.find('[data-document-tree-node-id="101"] .document-tree-node__title')
    await title.trigger('dblclick')
    const input = wrapper.find('.document-tree-node__title-input')
    await input.setValue('新的文档名称')
    await input.trigger('keydown.enter')
    await flushPromises()

    expect(documentApi.updateDocument).toHaveBeenCalledWith(101, {
      baseVersion: detail.version,
      title: '新的文档名称',
    })
  })

  it('collapses a document tree node independently', async () => {
    const wrapper = await mountDocument()

    const row = wrapper.find('.document-tree-node__row')
    expect(row.attributes('aria-expanded')).toBe('false')

    await row.trigger('click')

    expect(row.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.document-tree').text()).toContain('临时草稿')

    await row.trigger('click')

    expect(wrapper.find('.document-tree').text()).not.toContain('临时草稿')
  })

  it('preserves collapsed directories when opening another root document', async () => {
    vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
      {
        id: 501,
        parentId: null,
        title: '根层文档',
        docType: 'FORMAL',
        nodeType: 'DOCUMENT',
        children: [],
      },
      {
        id: 502,
        parentId: null,
        title: '项目目录',
        docType: null,
        nodeType: 'DIRECTORY',
        children: [
          {
            id: 503,
            parentId: 502,
            title: '目录内文档',
            docType: 'DRAFT',
            nodeType: 'DOCUMENT',
            children: [],
          },
        ],
      },
    ])

    const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 503)
    const expansionToggle = () =>
      wrapper.findAll('button').find((button) => button.text() === '展开')!
    expect(expansionToggle().exists()).toBe(true)
    await expansionToggle().trigger('click')
    expect(wrapper.find('.document-tree').text()).toContain('目录内文档')
    expect(wrapper.findAll('button').some((button) => button.text() === '收起')).toBe(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '收起')!
      .trigger('click')
    expect(wrapper.find('.document-tree').text()).not.toContain('目录内文档')
    expect(expansionToggle().exists()).toBe(true)

    await wrapper.find('.document-tree-node__row').trigger('click')
    await flushPromises()

    expect(wrapper.find('.document-tree').text()).not.toContain('目录内文档')
  })

  it('restores the directory expansion state after clearing a search', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
        {
          id: 601,
          parentId: null,
          title: '项目目录',
          docType: null,
          nodeType: 'DIRECTORY',
          children: [
            {
              id: 602,
              parentId: 601,
              title: '目录内文档',
              docType: 'DRAFT',
              nodeType: 'DOCUMENT',
              children: [],
            },
          ],
        },
      ])

      const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 602)
      const searchInput = wrapper.get('input[aria-label="搜索文档或目录"]')
      expect(wrapper.find('.document-tree').text()).not.toContain('目录内文档')

      await searchInput.setValue('目录内文档')
      vi.advanceTimersByTime(260)
      await flushPromises()
      expect(wrapper.find('.document-tree').text()).toContain('目录内文档')

      await searchInput.setValue('')
      vi.advanceTimersByTime(260)
      await flushPromises()
      expect(wrapper.find('.document-tree').text()).not.toContain('目录内文档')
    } finally {
      vi.useRealTimers()
    }
  })

  it('defaults to rendered preview and allows switching to source mode', async () => {
    const wrapper = await mountDocument()

    expect(wrapper.find('.markdown-editor__preview').exists()).toBe(true)
    expect(wrapper.find('.markdown-editor__preview').text()).toContain('方案')
    expect(wrapper.find('textarea').exists()).toBe(false)

    await wrapper.get('button[aria-label="源代码模式"]').trigger('click')

    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.find('.markdown-editor__preview').exists()).toBe(false)
  })

  it('preserves plain text entered in the visual editor after switching to preview', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({ ...detail, content: '' })
    const wrapper = await mountDocument()

    await wrapper.get('button[aria-label="编辑模式"]').trigger('click')
    await nextTick()
    const editor = wrapper.get('[aria-label="Markdown 可视化编辑器"]')
    editor.element.textContent = '切换后仍然保留的内容'
    await editor.trigger('input')

    await wrapper.get('button[aria-label="预览模式"]').trigger('click')
    await nextTick()

    expect(wrapper.get('.markdown-editor__preview').text()).toContain('切换后仍然保留的内容')
  })

  it('renders Markdown tables in preview mode', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({
      ...detail,
      content: '| 名称 | 状态 |\n| --- | --- |\n| 文档 | 正常 |',
    })
    const wrapper = await mountDocument()

    expect(wrapper.find('.markdown-preview__body table').exists()).toBe(true)
    expect(wrapper.find('.markdown-preview__body').text()).toContain('文档')
  })

  it('opens the single-column visual Markdown editor for document editing', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({
      ...detail,
      content:
        '# 方案\n\n| 名称 | 状态 |\n| --- | --- |\n| 文档 | 正常 |\n\n![流程图](/api/document/documents/101/assets/301)',
    })
    vi.mocked(documentApi.readDocumentImage).mockResolvedValue(new Blob(['image']))
    const wrapper = await mountDocument()

    await wrapper.get('button[aria-label="编辑模式"]').trigger('click')
    await nextTick()

    const editor = wrapper.get('[aria-label="Markdown 可视化编辑器"]')
    expect(wrapper.find('[aria-label="Markdown 实时预览"]').exists()).toBe(false)
    expect(editor.find('table').exists()).toBe(true)
    expect(editor.find('img').exists()).toBe(true)
    expect(editor.text()).toContain('正常')
  })

  it('reuses the resolved image when switching between preview and edit modes', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:document-image'),
    })
    vi.mocked(documentApi.readDocumentImage).mockClear()
    vi.mocked(documentApi.getDocument).mockResolvedValue({
      ...detail,
      content: '![流程图](/api/document/documents/101/assets/301)',
    })
    vi.mocked(documentApi.readDocumentImage).mockResolvedValue(new Blob(['image']))
    const wrapper = await mountDocument()
    await flushPromises()

    await vi.waitFor(() =>
      expect(wrapper.get('[aria-label="Markdown 预览"] img').attributes('src')).toMatch(/^blob:/),
    )
    const resolvedUrl = wrapper.get('[aria-label="Markdown 预览"] img').attributes('src')
    expect(documentApi.readDocumentImage).toHaveBeenCalledTimes(1)

    await wrapper.get('button[aria-label="编辑模式"]').trigger('click')
    await nextTick()
    expect(wrapper.get('[aria-label="Markdown 可视化编辑器"] img').attributes('src')).toBe(
      resolvedUrl,
    )

    await wrapper.get('button[aria-label="预览模式"]').trigger('click')
    await nextTick()
    expect(wrapper.get('[aria-label="Markdown 预览"] img').attributes('src')).toBe(resolvedUrl)
    expect(documentApi.readDocumentImage).toHaveBeenCalledTimes(1)
    Reflect.deleteProperty(URL, 'createObjectURL')
  })

  it('renders Markdown block and inline syntax in the visual editor', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({ ...detail, content: '' })
    const wrapper = await mountDocument()

    await wrapper.get('button[aria-label="源代码模式"]').trigger('click')
    await wrapper
      .get('textarea[aria-label="Markdown 文档内容"]')
      .setValue('# 1\n\n- 条目\n\n**加粗**')
    await wrapper.get('button[aria-label="编辑模式"]').trigger('click')
    await nextTick()

    const editor = wrapper.get('[aria-label="Markdown 可视化编辑器"]')
    expect(editor.find('h1').text()).toBe('1')
    expect(editor.find('ul li').text()).toBe('条目')
    expect(editor.find('strong').text()).toBe('加粗')
  })

  it('renders and hydrates an image immediately after uploading in edit mode', async () => {
    vi.mocked(documentApi.uploadDocumentImage).mockResolvedValue({
      id: 401,
      documentId: 101,
      originalName: '流程图.png',
      contentType: 'image/png',
      sizeBytes: 10,
      url: '/api/document/documents/101/assets/401',
      createdAt: null,
    })
    vi.mocked(documentApi.readDocumentImage).mockResolvedValue(new Blob(['image']))
    const wrapper = await mountDocument()

    await wrapper.get('button[aria-label="编辑模式"]').trigger('click')
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      value: [new File(['image'], '流程图.png', { type: 'image/png' })],
    })
    await input.trigger('change')
    await flushPromises()

    const image = wrapper.get('[aria-label="Markdown 可视化编辑器"] img')
    expect(image.attributes('data-markdown-src')).toBe('/api/document/documents/101/assets/401')
    expect(documentApi.readDocumentImage).toHaveBeenCalledWith('101', '401')
  })

  it('preserves an unsaved draft when switching documents', async () => {
    const otherDetail = {
      ...detail,
      id: 102,
      title: '会议记录草稿',
      content: '# 其他文档',
    }
    let cachedDraft: {
      documentId: number
      baseVersion: number
      title: string
      content: string
    } | null = null
    vi.mocked(documentApi.getDocument).mockImplementation(async (documentId) =>
      String(documentId) === '102' ? otherDetail : detail,
    )
    vi.mocked(documentApi.saveDocumentDraft).mockImplementation(async (documentId, payload) => {
      cachedDraft = { documentId: Number(documentId), ...payload }
      return cachedDraft
    })
    vi.mocked(documentApi.getDocumentDraft).mockImplementation(async (documentId) =>
      String(documentId) === '101' ? cachedDraft : null,
    )
    const wrapper = await mountDocument()

    await wrapper.get('button[aria-label="源代码模式"]').trigger('click')
    await wrapper.get('textarea').setValue('# 临时编辑内容')
    await nextTick()

    await wrapper.find('[data-document-tree-node-id="102"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('会议记录草稿')

    await wrapper.find('[data-document-tree-node-id="101"]').trigger('click')
    await flushPromises()
    await wrapper.get('button[aria-label="源代码模式"]').trigger('click')

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('# 临时编辑内容')
  })

  it('renders a clickable outline from Markdown headings', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({
      ...detail,
      content: '# 背景\n\n## 目标\n\n正文',
    })
    const wrapper = await mountDocument()

    const outlineToggle = wrapper.get('.document-outline-float__toggle')
    expect(outlineToggle.attributes('aria-expanded')).toBe('false')
    await outlineToggle.trigger('click')
    await nextTick()

    const outlineItems = wrapper.findAll('.document-outline__item')
    expect(outlineItems).toHaveLength(2)
    expect(outlineItems[0].text()).toContain('背景')
    expect(outlineItems[1].attributes('style')).toContain('padding-left: 26px')
    const previewHost = wrapper.get('.markdown-editor__preview').element as HTMLElement
    const scrollTo = vi.fn()
    Object.defineProperty(previewHost, 'scrollTo', { value: scrollTo })
    await outlineItems[1].trigger('click')
    await nextTick()
    expect(outlineItems[1].classes()).toContain('is-active')
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'smooth' }))
  })

  it('sends the loaded version as baseVersion when saving', async () => {
    const wrapper = await mountDocument()
    await wrapper.get('button[aria-label="源代码模式"]').trigger('click')
    const textarea = wrapper.get('textarea')
    await textarea.setValue('# 已修改')

    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存')
    expect(saveButton).toBeDefined()
    await saveButton!.trigger('click')
    await flushPromises()

    expect(documentApi.updateDocument).toHaveBeenCalledWith(101, {
      baseVersion: 3,
      title: '产品上线方案',
      content: '# 已修改',
    })
  })

  it('does not show editing actions without document edit permission', async () => {
    const wrapper = await mountDocument([SPACE_PERMISSIONS.DOCUMENT_READ])

    expect(
      wrapper
        .findAll('.document-editor__actions button')
        .some((button) => button.text() === '归档'),
    ).toBe(false)
    expect(wrapper.find('button[aria-label="编辑模式"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text() === '保存')).toBe(false)
  })

  it('opens an inline task dialog and creates a task without navigating away', async () => {
    const wrapper = await mountDocument()
    const route = wrapper.vm.$router.currentRoute.value.fullPath
    const taskButton = wrapper
      .findAll('.document-editor__actions button')
      .find((button) => button.text() === '发起 Agent 任务')

    expect(taskButton).toBeDefined()
    await taskButton!.trigger('click')
    await flushPromises()

    expect(agentApi.listAgents).toHaveBeenCalledWith(7, expect.any(AbortSignal))
    const taskTextarea = document.body.querySelector(
      'textarea[placeholder="告诉 Agent 需要如何处理这篇文档"]',
    )
    expect(taskTextarea).not.toBeNull()
    const dialog = new DOMWrapper(taskTextarea!.closest('.el-dialog')!)
    await dialog
      .get('textarea[placeholder="告诉 Agent 需要如何处理这篇文档"]')
      .setValue('请检查文档结构')
    await dialog
      .findAll('button')
      .find((button) => button.text() === '创建任务')!
      .trigger('click')
    await flushPromises()

    expect(taskApi.createTask).toHaveBeenCalledWith({
      agentId: 301,
      documentId: 101,
      name: '产品上线方案',
      instruction: '请检查文档结构',
      tokenBudget: null,
    })
    expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe(route)
  })

  it('opens a directory without requesting document detail', async () => {
    vi.mocked(documentApi.getDocument).mockClear()
    vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
      {
        id: 201,
        parentId: null,
        title: '产品资料',
        docType: null,
        nodeType: 'DIRECTORY',
        children: [],
      },
    ])

    const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 201)

    expect(wrapper.text()).toContain('这是一个目录')
    expect(wrapper.text()).toContain('目录用于组织文档')
    expect(documentApi.getDocument).not.toHaveBeenCalled()
  })

  it('moves a document into a directory by drag and drop', async () => {
    vi.mocked(documentApi.moveDocument).mockResolvedValue()
    vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
      {
        id: 301,
        parentId: null,
        title: '产品资料',
        docType: null,
        nodeType: 'DIRECTORY',
        children: [],
      },
      {
        id: 302,
        parentId: null,
        title: '待归档文档',
        docType: 'DRAFT',
        nodeType: 'DOCUMENT',
        children: [],
      },
    ])

    const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 302)
    const rows = wrapper.findAll('.document-tree-node__row')
    expect(rows[1].attributes('title')).toBe('拖动以移动')
    const originalElementFromPoint = document.elementFromPoint
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: () => rows[0].element,
    })
    await rows[1].trigger('pointerdown', { button: 0, clientX: 0, clientY: 0, pointerId: 1 })
    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 20, clientY: 20 }))
    window.dispatchEvent(new MouseEvent('pointerup', { clientX: 20, clientY: 20 }))
    await flushPromises()
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: originalElementFromPoint,
    })

    expect(documentApi.moveDocument).toHaveBeenCalledWith(302, 301)
  })

  it('moves a child directory to the space root by dropping on the root zone', async () => {
    vi.mocked(documentApi.moveDirectory).mockResolvedValue({
      id: 402,
      spaceId: 7,
      parentId: null,
      title: '项目文档库',
      status: 'NORMAL',
      createdAt: null,
      updatedAt: null,
    })
    vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
      {
        id: 401,
        parentId: null,
        title: '产品资料',
        docType: null,
        nodeType: 'DIRECTORY',
        children: [
          {
            id: 402,
            parentId: 401,
            title: '项目文档库',
            docType: null,
            nodeType: 'DIRECTORY',
            children: [],
          },
        ],
      },
    ])

    const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 402)
    await wrapper.find('.document-tree-node__toggle').trigger('click')
    const rows = wrapper.findAll('.document-tree-node__row')
    const originalElementFromPoint = document.elementFromPoint
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: () => null,
    })
    await rows[1].trigger('pointerdown', { button: 0, clientX: 0, clientY: 0, pointerId: 1 })
    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 20, clientY: 20 }))
    await nextTick()

    const rootDropZone = wrapper.find('[data-document-tree-root-drop-zone]')
    expect(rootDropZone.exists()).toBe(true)
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: () => rootDropZone.element,
    })
    window.dispatchEvent(new MouseEvent('pointerup', { clientX: 20, clientY: 20 }))
    await flushPromises()
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: originalElementFromPoint,
    })

    expect(documentApi.moveDirectory).toHaveBeenCalledWith(402, null)
  })

  it('moves a document to the space root when dropped in the tree blank area', async () => {
    vi.mocked(documentApi.moveDocument).mockResolvedValue()
    vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
      {
        id: 701,
        parentId: null,
        title: '产品资料',
        docType: null,
        nodeType: 'DIRECTORY',
        children: [
          {
            id: 702,
            parentId: 701,
            title: '待归档文档',
            docType: 'DRAFT',
            nodeType: 'DOCUMENT',
            children: [],
          },
        ],
      },
    ])

    const wrapper = await mountDocument(Object.values(SPACE_PERMISSIONS), 702)
    await wrapper.find('.document-tree-node__toggle').trigger('click')
    const rows = wrapper.findAll('.document-tree-node__row')
    const treeElement = wrapper.find('[data-document-tree-root-container]').element
    const originalElementFromPoint = document.elementFromPoint
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: () => treeElement,
    })
    await rows[1].trigger('pointerdown', { button: 0, clientX: 0, clientY: 0, pointerId: 1 })
    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 20, clientY: 20 }))
    window.dispatchEvent(new MouseEvent('pointerup', { clientX: 20, clientY: 20 }))
    await flushPromises()
    Object.defineProperty(document, 'elementFromPoint', {
      configurable: true,
      value: originalElementFromPoint,
    })

    expect(documentApi.moveDocument).toHaveBeenCalledWith(702, null)
  })
})
