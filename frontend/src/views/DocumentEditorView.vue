<template>
  <section class="document-page">
    <header class="document-page__header">
      <div class="document-page__brand">
        <div class="document-page__title-row">
          <h1>文档</h1>
          <span class="document-page__divider"></span>
          <span class="document-page__space-name">{{
            workspaceStore.currentSpace?.name || '当前空间'
          }}</span>
        </div>
        <p>管理空间文档与 Agent 协作内容</p>
      </div>
      <div class="document-page__header-actions">
        <el-button :icon="Refresh" :loading="treeLoading" @click="loadTree">刷新</el-button>
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreateDocumentDialog">
          新增文档/目录
        </el-button>
      </div>
    </header>

    <div class="document-workspace surface-card">
      <aside class="document-tree-panel">
        <div class="document-tree-panel__header">
          <strong>文档目录</strong>
          <span>{{ treeDocumentCount }} 篇文档 · {{ treeDirectoryCount }} 个目录</span>
        </div>
        <div class="document-tree-panel__search">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索文档或目录"
            aria-label="搜索文档或目录"
            @clear="loadTree"
            @keyup.enter="loadTree"
          >
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
          </el-input>
        </div>
        <div class="document-tree-panel__toolbar">
          <span>全部文档</span>
          <button
            type="button"
            :title="hasExpandedTreeNode ? '收起全部' : '展开全部'"
            @click="toggleAllExpansion"
          >
            {{ hasExpandedTreeNode ? '收起' : '展开' }}
          </button>
        </div>
        <div v-if="treeLoading" class="document-tree-state" role="status">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在加载文档目录</span>
        </div>
        <div
          v-else-if="treeError"
          class="document-tree-state document-tree-state--error"
          role="alert"
        >
          <span>{{ treeError }}</span>
          <el-button type="primary" link @click="loadTree">重新加载</el-button>
        </div>
        <div v-else-if="!tree.length" class="document-tree-state">
          <el-icon><Document /></el-icon>
          <span>{{ keyword ? '没有匹配的文档' : '当前空间还没有文档' }}</span>
          <el-button
            v-if="!keyword && canCreate"
            type="primary"
            link
            @click="openCreateDocumentDialog"
          >
            新建第一篇文档
          </el-button>
        </div>
        <div v-else class="document-tree" aria-label="文档目录树" data-document-tree-root-container>
          <div
            v-if="draggingNode && canEdit"
            class="document-tree__root-drop-zone"
            :class="{ 'document-tree__root-drop-zone--active': dropTargetId === null }"
            data-document-tree-root-drop-zone
            @pointerenter="handleRootPointerEnter"
            @pointermove="handleRootPointerEnter"
            @pointerleave="handleRootPointerLeave"
          >
            拖到这里移到空间根层
          </div>
          <DocumentTreeNode
            v-for="node in tree"
            :key="node.id"
            :node="node"
            :selected-id="selectedDocumentId"
            :expanded-ids="expandedIds"
            :can-move="canEdit"
            :can-rename="canEdit"
            :renaming-id="renamingNodeId"
            :dragging-id="draggingNode?.id || null"
            :drop-target-id="dropTargetId"
            @select="selectTreeNode"
            @toggle="toggleNode"
            @pointer-start="handlePointerStart"
            @rename="renameTreeNode"
          />
        </div>
      </aside>

      <main class="document-editor-panel">
        <div
          v-if="detailLoading && !documentDetail && !directoryDetail"
          class="document-editor-state"
          role="status"
        >
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <span>正在打开文档</span>
        </div>
        <div
          v-else-if="detailError"
          class="document-editor-state document-editor-state--error"
          role="alert"
        >
          <el-icon :size="24"><WarningFilled /></el-icon>
          <span>{{ detailError }}</span>
          <el-button type="primary" @click="reloadSelectedDocument">重新加载</el-button>
        </div>
        <div v-else-if="!documentDetail && !directoryDetail" class="document-editor-state">
          <el-icon :size="42"><Document /></el-icon>
          <strong>选择一个文档或目录</strong>
          <span>文档内容会以 Markdown 形式保存，目录用于组织文档</span>
        </div>
        <template v-else>
          <div v-if="detailLoading" class="document-editor-loading-overlay" role="status">
            <el-icon class="is-loading" :size="22"><Loading /></el-icon>
            <span>正在打开</span>
          </div>
          <header class="document-editor__header">
            <div class="document-editor__breadcrumb">
              <span>{{ workspaceStore.currentSpace?.name || '空间' }}</span>
              <el-icon><ArrowRight /></el-icon>
              <span>{{
                isDirectory ? '目录' : documentDetail?.docType === 'FORMAL' ? '正式文档' : '草稿'
              }}</span>
              <el-icon><ArrowRight /></el-icon>
              <strong>{{ selectedTitle }}</strong>
            </div>
            <div class="document-editor__actions">
              <span
                v-if="!isDirectory"
                class="save-state"
                :class="{ 'save-state--dirty': isDirty }"
              >
                <el-icon>
                  <CircleCheckFilled v-if="!isDirty && !isHistoricalVersion" />
                  <WarningFilled v-else-if="!isHistoricalVersion" />
                </el-icon>
                {{ isHistoricalVersion ? '历史版本' : isDirty ? '未保存' : '已保存' }}
              </span>
              <el-select
                v-if="!isDirectory"
                v-model="selectedVersionNo"
                class="version-select"
                :loading="versionLoading"
                :disabled="versionDetailLoading || rollbackLoading"
                aria-label="选择文档版本"
                @change="handleVersionChange"
              >
                <el-option
                  v-for="version in versionOptions"
                  :key="version.versionNo"
                  :label="`v${version.versionNo}`"
                  :value="version.versionNo"
                >
                  <span>v{{ version.versionNo }}</span>
                  <span
                    v-if="version.versionNo === documentDetail?.version"
                    class="version-option-current"
                  >
                    当前
                  </span>
                </el-option>
              </el-select>
              <el-button
                v-if="canEdit && isHistoricalVersion"
                plain
                :loading="rollbackLoading"
                @click="rollbackSelectedVersion"
              >
                回滚到此版本
              </el-button>
              <el-button
                v-if="canEdit && !isDirectory && !isHistoricalVersion"
                type="primary"
                :loading="saving"
                :disabled="!isDirty"
                @click="saveDocument"
              >
                保存
              </el-button>
              <el-button
                v-if="canEdit && !isHistoricalVersion"
                type="danger"
                plain
                :icon="Delete"
                @click="archiveCurrentDocument"
              >
                归档
              </el-button>
              <el-button
                v-if="canCreateTask && !isDirectory"
                plain
                :icon="VideoPlay"
                @click="openTaskDialog"
              >
                发起 Agent 任务
              </el-button>
            </div>
          </header>

          <div v-if="isDirectory" class="directory-editor-state">
            <el-icon :size="48"><Folder /></el-icon>
            <strong>这是一个目录</strong>
            <span>目录用于组织文档，可以在此目录下继续创建文档或子目录。</span>
            <el-button v-if="canCreate" type="primary" @click="openCreateDocumentDialog">
              在此目录下新建文档
            </el-button>
          </div>
          <template v-else>
            <div v-if="documentDetail?.docType === 'FORMAL'" class="formal-notice">
              <el-icon><InfoFilled /></el-icon>
              <span>正式文档：Agent 修改将生成变更请求，审批后合并</span>
            </div>

            <div class="markdown-editor">
              <div
                v-if="markdownOutline.length"
                class="document-outline-float"
                :class="{ 'is-collapsed': outlineCollapsed }"
              >
                <button
                  type="button"
                  class="document-outline-float__toggle"
                  :aria-expanded="!outlineCollapsed"
                  aria-controls="document-outline"
                  @click="toggleOutline"
                >
                  <span>{{ outlineCollapsed ? '大纲' : '文档大纲' }}</span>
                  <span>{{ outlineCollapsed ? '展开' : '收起' }}</span>
                </button>
                <nav
                  v-if="!outlineCollapsed"
                  id="document-outline"
                  class="document-outline"
                  aria-label="文档大纲"
                >
                  <button
                    v-for="item in markdownOutline"
                    :key="item.id"
                    type="button"
                    class="document-outline__item"
                    :class="[
                      `is-level-${Math.min(item.level, 6)}`,
                      {
                        'is-active':
                          activeOutlineId === item.id ||
                          (!activeOutlineId && item.id === markdownOutline[0]?.id),
                      },
                    ]"
                    :style="{ paddingLeft: `${12 + (item.level - 1) * 14}px` }"
                    @click="jumpToHeading(item.id)"
                  >
                    {{ item.title }}
                  </button>
                </nav>
              </div>
              <div v-if="versionDetailLoading" class="version-detail-loading" role="status">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>正在加载版本</span>
              </div>
              <div class="markdown-editor__toolbar" aria-label="Markdown 工具栏">
                <span class="markdown-editor__mode">Markdown</span>
                <span class="markdown-editor__separator"></span>
                <template v-if="contentMode !== 'preview'">
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="insertMarkdown('# ', '')"
                  >
                    标题
                  </button>
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="insertMarkdown('**', '**')"
                  >
                    <strong>B</strong>
                  </button>
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="insertMarkdown('- ', '')"
                  >
                    列表
                  </button>
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="insertMarkdown('> ', '')"
                  >
                    引用
                  </button>
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="insertMarkdown('```\n', '\n```')"
                  >
                    代码
                  </button>
                  <button type="button" :disabled="isHistoricalVersion" @click="openTableDialog">
                    表格
                  </button>
                  <button
                    type="button"
                    :disabled="isHistoricalVersion"
                    @click="imageInput?.click()"
                  >
                    <el-icon><Picture /></el-icon> 图片
                  </button>
                  <input
                    ref="imageInput"
                    class="visually-hidden"
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    @change="uploadImage"
                  />
                </template>
                <div class="markdown-editor__view-switch" role="tablist" aria-label="内容查看模式">
                  <button
                    v-if="canEdit && !isHistoricalVersion"
                    type="button"
                    role="tab"
                    aria-label="编辑模式"
                    :aria-selected="contentMode === 'edit'"
                    :class="{ 'is-active': contentMode === 'edit' }"
                    @click="contentMode = 'edit'"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-label="预览模式"
                    :aria-selected="contentMode === 'preview'"
                    :class="{ 'is-active': contentMode === 'preview' }"
                    @click="contentMode = 'preview'"
                  >
                    预览
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-label="源代码模式"
                    :aria-selected="contentMode === 'source'"
                    :class="{ 'is-active': contentMode === 'source' }"
                    @click="contentMode = 'source'"
                  >
                    源代码
                  </button>
                </div>
              </div>
              <div v-if="contentMode !== 'preview'" class="markdown-editor__title">
                <el-input
                  v-model="draftTitle"
                  maxlength="200"
                  show-word-limit
                  :readonly="isHistoricalVersion"
                  placeholder="请输入文档标题"
                />
              </div>
              <div
                v-if="contentMode === 'preview'"
                ref="previewHost"
                class="markdown-editor__preview"
                aria-label="Markdown 预览"
              >
                <h1>{{ draftTitle || '未命名文档' }}</h1>
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="markdown-preview__body" v-html="renderMarkdown(draftContent)"></div>
              </div>
              <!-- eslint-disable vue/no-v-html -->
              <div
                v-else-if="contentMode === 'edit'"
                ref="richEditorHost"
                class="markdown-editor__rich-content markdown-preview__body"
                contenteditable="true"
                role="textbox"
                aria-label="Markdown 可视化编辑器"
                spellcheck="false"
                @input="handleRichTextInput"
                v-html="richEditorHtml"
              ></div>
              <!-- eslint-enable vue/no-v-html -->
              <textarea
                v-else
                ref="contentInput"
                v-model="draftContent"
                class="markdown-editor__content"
                placeholder="从这里开始编写 Markdown 文档…"
                spellcheck="false"
                :readonly="isHistoricalVersion"
                aria-label="Markdown 文档内容"
              ></textarea>
              <footer class="markdown-editor__footer">
                <span>Markdown · {{ wordCount }} 字</span>
                <span
                  >版本 v{{ documentDetail?.version }} · 最后保存
                  {{ formatDateTime(documentDetail?.updatedAt || null) }}</span
                >
              </footer>
            </div>
          </template>

          <div class="document-editor__bottom-actions">
            <span v-if="!isDirectory">
              {{
                isHistoricalVersion
                  ? '历史版本仅供查看，回滚后可继续编辑'
                  : '正文修改会生成版本快照'
              }}
            </span>
          </div>
        </template>
      </main>

      <aside class="document-info-panel">
        <section class="document-info-card">
          <header class="document-info-card__header"><strong>文档信息</strong></header>
          <div v-if="documentDetail || directoryDetail" class="document-info-card__body">
            <dl>
              <div>
                <dt>文档类型</dt>
                <dd>
                  {{
                    isDirectory
                      ? '目录'
                      : documentDetail?.docType === 'FORMAL'
                        ? '正式文档'
                        : '草稿'
                  }}
                </dd>
              </div>
              <div>
                <dt>{{ isHistoricalVersion ? '查看版本' : '当前版本' }}</dt>
                <dd>
                  {{
                    isDirectory
                      ? '—'
                      : `v${isHistoricalVersion ? selectedVersionNo : documentDetail?.version}`
                  }}
                </dd>
              </div>
              <div>
                <dt>负责人</dt>
                <dd>{{ responsibleName }}</dd>
              </div>
              <div>
                <dt>访问范围</dt>
                <dd>按空间权限控制</dd>
              </div>
            </dl>
            <el-button class="document-info-card__button" plain @click="showDocumentInfo">
              查看详情
            </el-button>
          </div>
          <div v-else class="document-info-card__empty">选择文档后查看信息</div>
        </section>

        <section class="document-info-card document-activity-card">
          <header class="document-info-card__header">
            <strong>关联活动</strong>
            <span v-if="activityPage.total">{{ activityPage.total }} 条</span>
          </header>
          <div
            v-if="activityLoading && !activityPage.records.length"
            class="document-info-card__empty"
            role="status"
          >
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>正在加载活动</span>
          </div>
          <div
            v-else-if="activityError"
            class="document-info-card__empty document-info-card__empty--error"
          >
            {{ activityError }}
          </div>
          <div v-else-if="!canReadActivities" class="document-info-card__empty">
            当前角色没有任务或变更记录读取权限
          </div>
          <div v-else-if="!activityPage.records.length" class="document-info-card__empty">
            暂无关联活动
          </div>
          <div v-else class="activity-list" :class="{ 'activity-list--loading': activityLoading }">
            <article
              v-for="activity in activityPage.records"
              :key="`${activity.type}-${activity.id}`"
              class="activity-item"
            >
              <span
                class="activity-item__icon"
                :class="`activity-item__icon--${activity.type.toLowerCase()}`"
              >
                <el-icon><component :is="activityIcon(activity)" /></el-icon>
              </span>
              <div class="activity-item__content">
                <div class="activity-item__headline">
                  <strong>{{ activity.title }}</strong>
                  <el-tag size="small" effect="light" :type="activityStatusType(activity)">
                    {{ activity.status || '未知状态' }}
                  </el-tag>
                </div>
                <span
                  >{{ activity.type === 'TASK' ? 'Agent 任务' : '变更请求' }} ·
                  {{ activity.operatorName || '—' }}</span
                >
                <time>{{ formatRelativeTime(activity.activityAt) }}</time>
              </div>
            </article>
            <div v-if="activityLoading" class="activity-list__loading-overlay" role="status">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在加载活动</span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <el-dialog
      v-model="createDialogVisible"
      :title="createForm.nodeType === 'DIRECTORY' ? '新建目录' : '新建文档'"
      width="460px"
    >
      <el-form label-position="top" @submit.prevent="createNewDocument">
        <el-form-item
          :label="createForm.nodeType === 'DIRECTORY' ? '目录名称' : '文档标题'"
          required
        >
          <el-input
            v-model="createForm.title"
            maxlength="200"
            show-word-limit
            placeholder="例如：产品规划"
          />
        </el-form-item>
        <el-form-item label="创建类型">
          <el-radio-group v-model="createForm.nodeType">
            <el-radio-button label="DOCUMENT">文档</el-radio-button>
            <el-radio-button label="DIRECTORY">目录</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所在目录">
          <el-tree-select
            v-model="createForm.parentId"
            :data="directoryOptions"
            :props="directoryTreeProps"
            check-strictly
            clearable
            default-expand-all
            placeholder="选择目录（不选则放在空间根层）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="createForm.nodeType === 'DOCUMENT'" label="文档类型">
          <el-radio-group v-model="createForm.docType">
            <el-radio-button label="DRAFT">草稿</el-radio-button>
            <el-radio-button label="FORMAL">正式文档</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <p class="dialog-help">
          创建位置：{{ createParentLabel }}。
          <template v-if="createForm.nodeType === 'DIRECTORY'"> 目录最多支持 3 层嵌套。 </template>
          <template v-else>正式文档的 Agent 修改会进入变更审批流程。</template>
        </p>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createNewDocument">
          {{ createForm.nodeType === 'DIRECTORY' ? '创建目录' : '创建文档' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tableDialogVisible" title="插入表格" width="380px">
      <el-form label-position="top" class="table-size-form" @submit.prevent="insertTableContent">
        <el-form-item label="行数">
          <el-input-number v-model="tableRows" :min="2" :max="20" controls-position="right" />
        </el-form-item>
        <el-form-item label="列数">
          <el-input-number v-model="tableColumns" :min="1" :max="10" controls-position="right" />
        </el-form-item>
      </el-form>
      <p class="dialog-help">第一行会作为表头创建，插入后可以直接填写单元格内容。</p>
      <template #footer>
        <el-button @click="tableDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="insertTableContent">插入表格</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="documentInfoDialogVisible" title="文档详情" width="520px">
      <div v-if="documentDetail || directoryDetail" class="document-detail">
        <div class="document-detail__hero">
          <span class="document-detail__hero-icon">
            <el-icon :size="21"><Folder v-if="isDirectory" /><Document v-else /></el-icon>
          </span>
          <div class="document-detail__hero-content">
            <strong class="document-detail__title">{{ selectedTitle }}</strong>
            <span class="document-detail__type">{{ infoTypeLabel }}</span>
          </div>
          <el-tag
            size="small"
            effect="light"
            :type="infoStatus === 'ARCHIVED' ? 'info' : 'success'"
          >
            {{ documentStatusLabel(infoStatus) }}
          </el-tag>
        </div>
        <dl class="document-detail__grid">
          <div class="document-detail__item document-detail__item--wide">
            <dt>所在位置</dt>
            <dd>{{ documentLocation }}</dd>
          </div>
          <div v-if="!isDirectory" class="document-detail__item">
            <dt>当前版本</dt>
            <dd>v{{ documentDetail?.version }}</dd>
          </div>
          <div v-if="!isDirectory" class="document-detail__item">
            <dt>负责人</dt>
            <dd>{{ responsibleName }}</dd>
          </div>
          <div v-if="!isDirectory" class="document-detail__item">
            <dt>最后修改人</dt>
            <dd>{{ lastUpdatedByName }}</dd>
          </div>
          <div class="document-detail__item">
            <dt>最后更新时间</dt>
            <dd>{{ formatDateTime(infoUpdatedAt) }}</dd>
          </div>
          <div class="document-detail__item document-detail__item--wide">
            <dt>{{ isDirectory ? '目录 ID' : '文档 ID' }}</dt>
            <dd>{{ isDirectory ? directoryDetail?.id : documentDetail?.id }}</dd>
          </div>
        </dl>
      </div>
      <p v-else class="document-info-card__empty">暂无详情</p>
    </el-dialog>

    <el-dialog
      v-model="taskDialogVisible"
      title="发起 Agent 任务"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" @submit.prevent="createDocumentTask">
        <el-form-item label="目标文档" required>
          <el-tree-select
            v-model="taskForm.documentId"
            :data="taskDocumentOptions"
            :props="directoryTreeProps"
            check-strictly
            default-expand-all
            :loading="taskDocumentsLoading"
            :disabled="taskCreating"
            placeholder="选择目标文档"
            style="width: 100%"
          />
          <p v-if="taskDocumentsError" class="dialog-error">{{ taskDocumentsError }}</p>
        </el-form-item>
        <el-form-item label="Agent" required>
          <el-select
            v-model="taskForm.agentId"
            placeholder="选择要执行任务的 Agent"
            :loading="taskAgentsLoading"
            :disabled="taskCreating"
            style="width: 100%"
          >
            <el-option
              v-for="agent in enabledAgents"
              :key="agent.id"
              :label="agent.name"
              :value="agent.id"
            />
          </el-select>
          <p v-if="taskAgentsError" class="dialog-error">{{ taskAgentsError }}</p>
          <p v-else-if="!taskAgentsLoading && !enabledAgents.length" class="dialog-help">
            当前空间没有可用的 Agent，请先在 Agent 管理中启用一个 Agent。
          </p>
        </el-form-item>
        <el-form-item label="任务名称" required>
          <el-input
            v-model="taskForm.name"
            maxlength="100"
            show-word-limit
            placeholder="例如：审阅这篇文档"
            :disabled="taskCreating"
          />
        </el-form-item>
        <el-form-item label="任务指令" required>
          <el-input
            v-model="taskForm.instruction"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="告诉 Agent 需要如何处理这篇文档"
            :disabled="taskCreating"
          />
        </el-form-item>
        <el-form-item label="Token 预算（可选）">
          <el-input-number
            v-model="taskForm.tokenBudget"
            :min="1"
            :max="100000000"
            controls-position="right"
            placeholder="不填写则使用 Agent 或空间预算"
            :disabled="taskCreating"
            style="width: 100%"
          />
        </el-form-item>
        <p class="dialog-help">正式文档的 Agent 修改会生成变更请求，审批后才会合并。</p>
      </el-form>
      <template #footer>
        <el-button :disabled="taskCreating" @click="taskDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="taskCreating"
          :disabled="!enabledAgents.length"
          @click="createDocumentTask"
        >
          创建任务
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import {
  ArrowRight,
  CircleCheckFilled,
  Delete,
  Document,
  Folder,
  InfoFilled,
  Loading,
  Picture,
  Plus,
  Refresh,
  Search,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
  ElTreeSelect,
} from 'element-plus'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

import { ApiError, normalizeApiError } from '@/api/errors'
import { listAgents, type AgentOption } from '@/features/agent/api/agent-api'
import {
  archiveDirectory,
  archiveDocument,
  clearDocumentDraft,
  createDirectory,
  createDocument,
  getDocument,
  getDocumentDraft,
  getDocumentVersion,
  listDocumentActivities,
  listDocumentTree,
  listDocumentVersions,
  moveDirectory,
  moveDocument,
  readDocumentImage,
  rollbackDocumentVersion,
  saveDocumentDraft,
  uploadDocumentImage,
  updateDirectory,
  updateDocument,
} from '@/features/document/api/document-api'
import DocumentTreeNode from '@/features/document/components/DocumentTreeNode.vue'
import type {
  DocumentActivity,
  DocumentDetail,
  DirectoryDetail,
  DocumentNodeType,
  DocumentStatus,
  DocumentTreeNode as DocumentTreeNodeData,
  DocumentVersion,
  PageResult,
} from '@/features/document/types'
import { createTask } from '@/features/task/api/task-api'
import type { EntityId } from '@/features/workspace/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()

const ACTIVITY_POLL_INTERVAL_MS = 5000
const ACTIVE_ACTIVITY_STATUSES = new Set([
  '待运行',
  '已分发',
  '运行中',
  '等待输入',
  '等待授权',
  '取消中',
])

const keyword = ref('')
const tree = ref<DocumentTreeNodeData[]>([])
const expandedIds = ref(new Set<string>())
let expandedIdsBeforeSearch: Set<string> | null = null
const treeLoading = ref(false)
const treeError = ref('')
const detailLoading = ref(false)
const detailError = ref('')
const documentDetail = ref<DocumentDetail | null>(null)
const directoryDetail = ref<DirectoryDetail | null>(null)
const selectedVersionNo = ref<number | null>(null)
const versionRecords = ref<DocumentVersion[]>([])
const versionLoading = ref(false)
const versionDetailLoading = ref(false)
const rollbackLoading = ref(false)
const draftTitle = ref('')
const draftContent = ref('')
const draftBaseVersion = ref<number | null>(null)
const contentMode = ref<'preview' | 'edit' | 'source'>('preview')
const outlineCollapsed = ref(true)
const activeOutlineId = ref('')
const draggingNode = ref<DocumentTreeNodeData | null>(null)
const dropTargetId = ref<EntityId | null>(null)
const rootDropActive = ref(false)
const movingNode = ref(false)
const renamingNodeId = ref<EntityId | null>(null)
const pendingPointerDrag = ref<{
  node: DocumentTreeNodeData
  x: number
  y: number
} | null>(null)
const saving = ref(false)
const creating = ref(false)
const createDialogVisible = ref(false)
const tableDialogVisible = ref(false)
const documentInfoDialogVisible = ref(false)
const taskDialogVisible = ref(false)
const taskAgentsLoading = ref(false)
const taskAgentsError = ref('')
const taskCreating = ref(false)
const taskAgents = ref<AgentOption[]>([])
const taskDocumentsLoading = ref(false)
const taskDocumentsError = ref('')
const taskDocuments = ref<DocumentTreeNodeData[]>([])
const tableRows = ref(3)
const tableColumns = ref(3)
const imageInput = ref<HTMLInputElement | null>(null)
const contentInput = ref<HTMLTextAreaElement | null>(null)
const previewHost = ref<HTMLElement | null>(null)
const richEditorHost = ref<HTMLElement | null>(null)
const richEditorHtml = ref('')
const resolvedImageUrls = new Map<string, string>()
const imageResolutionPromises = new Map<string, Promise<string | null>>()
let outlineScrollContainer: HTMLElement | null = null
let draftSaveTimer: ReturnType<typeof setTimeout> | null = null
let draftSavePromise: Promise<void> | null = null
let draftHydrating = false
let suppressDraftSave = false
const createForm = reactive<{
  title: string
  docType: 'FORMAL' | 'DRAFT'
  nodeType: DocumentNodeType
  parentId: EntityId | null
}>({
  title: '',
  docType: 'DRAFT',
  nodeType: 'DOCUMENT',
  parentId: null,
})
const taskForm = reactive<{
  agentId: EntityId | null
  documentId: EntityId | null
  name: string
  instruction: string
  tokenBudget: number | null
}>({
  agentId: null,
  documentId: null,
  name: '',
  instruction: '',
  tokenBudget: null,
})
const enabledAgents = computed(() => taskAgents.value.filter((agent) => agent.status === 'ENABLED'))
const taskDocumentOptions = computed(() => buildTaskDocumentOptions(taskDocuments.value))
const activityLoading = ref(false)
const activityError = ref('')
const activityPage = ref<PageResult<DocumentActivity>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 8,
})

interface DirectoryOption {
  value: EntityId
  label: string
  children: DirectoryOption[]
  disabled: boolean
}

interface MarkdownOutlineItem {
  id: string
  level: number
  title: string
}

const directoryTreeProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  disabled: 'disabled',
}

let treeController: AbortController | null = null
let detailController: AbortController | null = null
let versionDetailController: AbortController | null = null
let activityController: AbortController | null = null
let activityPollTimer: ReturnType<typeof setTimeout> | null = null
let taskAgentsController: AbortController | null = null
let taskDocumentsController: AbortController | null = null
let searchTimer: ReturnType<typeof setTimeout> | null = null
let loadedTreeSpaceId: EntityId | null = null
let pointerMoveListener: ((event: PointerEvent) => void) | null = null
let pointerUpListener: ((event: PointerEvent) => void) | null = null

const canCreate = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.DOCUMENT_CREATE))
const canEdit = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.DOCUMENT_EDIT))
const canCreateTask = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.TASK_CREATE))
const canReadActivities = computed(
  () =>
    workspaceStore.hasPermission(SPACE_PERMISSIONS.TASK_READ) &&
    workspaceStore.hasPermission(SPACE_PERMISSIONS.CHANGE_REQUEST_READ),
)
const selectedDocumentId = computed<EntityId | null>(() => {
  const value = route.params.documentId
  return Array.isArray(value) ? value[0] || null : value || null
})
const isDirectory = computed(() => directoryDetail.value !== null)
const selectedTitle = computed(
  () => documentDetail.value?.title || directoryDetail.value?.title || '',
)
const infoStatus = computed<DocumentStatus>(
  () => documentDetail.value?.status || directoryDetail.value?.status || 'NORMAL',
)
const infoTypeLabel = computed(() =>
  isDirectory.value
    ? '目录'
    : documentDetail.value?.docType === 'FORMAL'
      ? '正式文档'
      : '草稿',
)
const documentLocation = computed(() => {
  const directoryId = documentDetail.value?.directoryId ?? directoryDetail.value?.parentId
  if (!directoryId) return '空间根层'
  const path = findNodePath(tree.value, directoryId)
    ?.filter((node) => node.nodeType === 'DIRECTORY')
    .map((node) => node.title)
  return path?.length ? path.join(' / ') : '空间根层'
})
const infoUpdatedAt = computed(
  () => documentDetail.value?.updatedAt ?? directoryDetail.value?.updatedAt ?? null,
)
const lastUpdatedByName = computed(() => {
  const current = documentDetail.value
  if (!current?.updatedBy) return '—'
  if (String(current.updatedBy) === String(current.createdBy) && current.creatorName) {
    return current.creatorName
  }
  return `用户 ${current.updatedBy}`
})
const isHistoricalVersion = computed(
  () =>
    documentDetail.value !== null &&
    selectedVersionNo.value !== null &&
    selectedVersionNo.value !== documentDetail.value.version,
)
const isDirty = computed(
  () =>
    documentDetail.value !== null &&
    !isHistoricalVersion.value &&
    (draftTitle.value !== documentDetail.value.title ||
      draftContent.value !== (documentDetail.value.content || '')),
)
const wordCount = computed(() => draftContent.value.replace(/\s/g, '').length)
const treeDocumentCount = computed(() => countNodesByType(tree.value, 'DOCUMENT'))
const treeDirectoryCount = computed(() => countNodesByType(tree.value, 'DIRECTORY'))
const versionOptions = computed<DocumentVersion[]>(() => {
  const records = [...versionRecords.value]
  const currentVersion = documentDetail.value
  if (currentVersion && !records.some((version) => version.versionNo === currentVersion.version)) {
    records.unshift({
      id: currentVersion.id,
      documentId: currentVersion.id,
      versionNo: currentVersion.version,
      changeSummary: '当前版本',
      createdBy: currentVersion.updatedBy,
      createdAt: currentVersion.updatedAt,
    })
  }
  return records.sort((left, right) => right.versionNo - left.versionNo)
})
const hasExpandedTreeNode = computed(() =>
  allNodes(tree.value).some(
    (node) => node.children.length > 0 && expandedIds.value.has(String(node.id)),
  ),
)
const responsibleName = computed(() => {
  if (!documentDetail.value) return '—'
  return (
    documentDetail.value.creatorName ||
    (documentDetail.value.createdBy ? `用户 ${documentDetail.value.createdBy}` : '—')
  )
})
const createParentLabel = computed(() => {
  if (!createForm.parentId) {
    return createForm.nodeType === 'DIRECTORY' ? '空间根目录' : '空间根层'
  }
  return findNode(tree.value, createForm.parentId)?.title || '当前目录'
})
const directoryOptions = computed(() =>
  buildDirectoryOptions(tree.value, 1, createForm.nodeType === 'DIRECTORY'),
)

const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
}).enable('table')

function createHeadingId(title: string, usedIds: Map<string, number>): string {
  const slug =
    title
      .replace(/[`*_~[\]()>#]/g, '')
      .trim()
      .toLocaleLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, '-')
      .replace(/^-|-$/g, '') || 'section'
  const baseId = `heading-${slug}`
  const count = (usedIds.get(baseId) || 0) + 1
  usedIds.set(baseId, count)
  return count === 1 ? baseId : `${baseId}-${count}`
}

function parseMarkdownWithOutline(markdown: string): {
  tokens: ReturnType<MarkdownIt['parse']>
  outline: MarkdownOutlineItem[]
} {
  const tokens = markdownRenderer.parse(markdown, {})
  const usedIds = new Map<string, number>()
  const outline: MarkdownOutlineItem[] = []

  tokens.forEach((token, index) => {
    if (token.type !== 'heading_open') return
    const inlineToken = tokens[index + 1]
    const title = inlineToken?.type === 'inline' ? inlineToken.content.trim() : ''
    const level = Number(token.tag.slice(1))
    const id = createHeadingId(title, usedIds)
    token.attrSet('id', id)
    outline.push({ id, level, title: title || '未命名标题' })
  })
  addImageSourceAttributes(tokens)

  return { tokens, outline }
}

function addImageSourceAttributes(tokens: ReturnType<MarkdownIt['parse']>): void {
  tokens.forEach((token) => {
    if (token.type === 'image') {
      const sourceUrl = token.attrGet('src')
      if (sourceUrl) token.attrSet('data-markdown-src', sourceUrl)
    }
    if (token.children) addImageSourceAttributes(token.children)
  })
}

const markdownOutline = computed<MarkdownOutlineItem[]>(() => {
  const source = draftContent.value.trim()
  return source ? parseMarkdownWithOutline(source).outline : []
})

function renderMarkdown(markdown: string): string {
  const source = markdown.trim()
  if (!source) return '<p class="markdown-preview__empty">暂无内容</p>'
  const parsed = parseMarkdownWithOutline(source)
  return DOMPurify.sanitize(
    markdownRenderer.renderer.render(parsed.tokens, markdownRenderer.options, {}),
    {
      USE_PROFILES: { html: true },
      ADD_ATTR: ['id', 'data-markdown-src'],
    },
  )
}

function serializeInlineMarkdown(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent || ''
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const element = node as HTMLElement
  const content = Array.from(element.childNodes).map(serializeInlineMarkdown).join('')
  switch (element.tagName) {
    case 'BR':
      return '\n'
    case 'STRONG':
    case 'B':
      return `**${content}**`
    case 'EM':
    case 'I':
      return `*${content}*`
    case 'CODE':
      return `\`${content}\``
    case 'A': {
      const href = element.getAttribute('href') || ''
      return href ? `[${content}](${href})` : content
    }
    case 'IMG': {
      const sourceUrl =
        element.getAttribute('data-markdown-src') || element.getAttribute('src') || ''
      const alt = element.getAttribute('alt') || ''
      return sourceUrl ? `![${alt}](${sourceUrl})` : ''
    }
    default:
      return content
  }
}

function serializeTable(table: HTMLTableElement): string {
  const rows = Array.from(table.querySelectorAll('tr')).map((row) =>
    Array.from(row.querySelectorAll('th, td')).map((cell) =>
      serializeInlineMarkdown(cell).replace(/\|/g, '\\|').replace(/\n/g, ' ').trim(),
    ),
  )
  if (!rows.length || !rows[0].length) return ''
  const header = `| ${rows[0].join(' | ')} |`
  const separator = `| ${rows[0].map(() => '---').join(' | ')} |`
  const body = rows.slice(1).map((row) => `| ${row.join(' | ')} |`)
  return [header, separator, ...body].join('\n')
}

function serializeBlockMarkdown(node: Element): string {
  const tagName = node.tagName
  if (/^H[1-6]$/.test(tagName)) {
    return `${'#'.repeat(Number(tagName.slice(1)))} ${serializeInlineMarkdown(node).trim()}`
  }
  if (tagName === 'TABLE') return serializeTable(node as HTMLTableElement)
  if (tagName === 'PRE') return `\`\`\`\n${node.textContent || ''}\n\`\`\``
  if (tagName === 'BLOCKQUOTE') {
    return serializeInlineMarkdown(node)
      .trim()
      .split('\n')
      .map((line) => `> ${line}`)
      .join('\n')
  }
  if (tagName === 'UL' || tagName === 'OL') {
    return Array.from(node.children)
      .filter((child) => child.tagName === 'LI')
      .map(
        (child, index) =>
          `${tagName === 'OL' ? `${index + 1}.` : '-'} ${serializeInlineMarkdown(child).trim()}`,
      )
      .join('\n')
  }
  return serializeInlineMarkdown(node).trim()
}

function serializeRichText(root: HTMLElement): string {
  return Array.from(root.childNodes)
    .map((node) =>
      node.nodeType === Node.ELEMENT_NODE
        ? serializeBlockMarkdown(node as Element)
        : (node.textContent || '').trim(),
    )
    .filter(Boolean)
    .join('\n\n')
}

function renderCurrentMarkdownBlock(): void {
  const editor = richEditorHost.value
  const selection = window.getSelection()
  const anchor = selection?.anchorNode
  if (!editor || !anchor) return
  const anchorElement =
    anchor.nodeType === Node.ELEMENT_NODE ? (anchor as Element) : anchor.parentElement
  const block = anchorElement?.closest('p, div')
  if (!block || block === editor || !editor.contains(block)) return

  const source = block.textContent || ''
  const hasBlockSyntax = /^(#{1,6})\s+\S|^[-+*]\s+\S|^\d+\.\s+\S|^>\s+\S/.test(source)
  const hasInlineSyntax = /\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\[[^\]]+\]\([^)]+\)/.test(source)
  if (!hasBlockSyntax && !hasInlineSyntax) return

  const rendered = document.createElement('div')
  rendered.innerHTML = renderMarkdown(source)
  const nodes = Array.from(rendered.childNodes)
  if (!nodes.length) return
  block.replaceWith(...nodes)
  const lastNode = nodes[nodes.length - 1]
  const range = document.createRange()
  range.selectNodeContents(lastNode)
  range.collapse(false)
  selection.removeAllRanges()
  selection.addRange(range)
}

function syncRichEditor(): void {
  richEditorHtml.value = renderMarkdown(draftContent.value)
}

async function refreshRichEditor(): Promise<void> {
  syncRichEditor()
  await nextTick()
  await hydrateImages(richEditorHost.value)
}

function handleRichTextInput(): void {
  if (!richEditorHost.value) return
  draftContent.value = serializeRichText(richEditorHost.value)
  renderCurrentMarkdownBlock()
}

async function loadTree(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId) return
  treeController?.abort()
  const controller = new AbortController()
  treeController = controller
  treeLoading.value = true
  treeError.value = ''
  try {
    tree.value = await listDocumentTree(spaceId, {
      keyword: keyword.value,
      signal: controller.signal,
    })
    if (keyword.value.trim() && tree.value.length) {
      expandAll()
    } else if (!keyword.value.trim() && expandedIdsBeforeSearch) {
      expandedIds.value = new Set(expandedIdsBeforeSearch)
      expandedIdsBeforeSearch = null
    }
    loadedTreeSpaceId = spaceId
    const currentId = selectedDocumentId.value
    if (currentId && findNode(tree.value, currentId)) {
      await loadDocument(currentId)
    } else if (!currentId && tree.value.length) {
      await selectTreeNode(firstNode(tree.value)!)
    } else if (currentId) {
      documentDetail.value = null
      directoryDetail.value = null
      await router.replace({ name: 'space-documents', params: { spaceId } })
    }
  } catch (error) {
    if (!controller.signal.aborted) treeError.value = normalizeApiError(error).message
  } finally {
    if (!controller.signal.aborted) treeLoading.value = false
  }
}

async function loadDocument(documentId: EntityId): Promise<void> {
  detailController?.abort()
  versionDetailController?.abort()
  clearActivityPollTimer()
  const detailRequest = new AbortController()
  detailController = detailRequest
  activityController?.abort()
  const activityRequest = new AbortController()
  activityController = activityRequest
  detailLoading.value = true
  detailError.value = ''
  activityError.value = ''
  selectedVersionNo.value = null
  versionRecords.value = []
  versionLoading.value = false
  versionDetailLoading.value = false
  contentMode.value = 'preview'
  draftHydrating = true
  try {
    const node = findNode(tree.value, documentId)
    if (node?.nodeType === 'DIRECTORY') {
      documentDetail.value = null
      directoryDetail.value = {
        id: node.id,
        spaceId: workspaceStore.currentSpaceId!,
        parentId: node.parentId,
        title: node.title,
        status: 'NORMAL',
        createdAt: null,
        updatedAt: null,
      }
      draftTitle.value = ''
      draftContent.value = ''
      draftBaseVersion.value = null
      activityPage.value = { records: [], total: 0, pageNum: 1, pageSize: 8 }
      return
    }
    const detail = await getDocument(documentId, detailRequest.signal)
    if (detailRequest.signal.aborted) return
    directoryDetail.value = null
    documentDetail.value = detail
    draftBaseVersion.value = detail.version
    draftTitle.value = detail.title
    draftContent.value = detail.content || ''
    try {
      const draft = await getDocumentDraft(detail.id, detailRequest.signal)
      if (detailRequest.signal.aborted) return
      if (draft) {
        draftBaseVersion.value = draft.baseVersion
        draftTitle.value = draft.title ?? detail.title
        draftContent.value = draft.content
        ElMessage.info(
          draft.baseVersion === detail.version
            ? '已恢复该文档未保存的草稿'
            : `已恢复基于 v${draft.baseVersion} 的未保存草稿，保存时将进行版本冲突校验`,
        )
      }
    } catch (error) {
      if (!detailRequest.signal.aborted) {
        ElMessage.warning(`未保存草稿加载失败：${normalizeApiError(error).message}`)
      }
    }
    selectedVersionNo.value = detail.version
    void loadDocumentVersions(documentId, detailRequest.signal)
    if (canReadActivities.value) {
      void loadActivities(documentId, activityRequest.signal)
    } else {
      activityPage.value = { records: [], total: 0, pageNum: 1, pageSize: 8 }
      clearActivityPollTimer()
    }
  } catch (error) {
    if (!detailRequest.signal.aborted) detailError.value = normalizeApiError(error).message
  } finally {
    draftHydrating = false
    if (!detailRequest.signal.aborted) detailLoading.value = false
  }
}

async function loadDocumentVersions(documentId: EntityId, signal: AbortSignal): Promise<void> {
  versionLoading.value = true
  try {
    const page = await listDocumentVersions(documentId, 100, signal)
    if (!signal.aborted) versionRecords.value = page.records
  } catch (error) {
    if (!signal.aborted) ElMessage.warning(`文档版本加载失败：${normalizeApiError(error).message}`)
  } finally {
    if (!signal.aborted) versionLoading.value = false
  }
}

async function handleVersionChange(versionNo: number): Promise<void> {
  const current = documentDetail.value
  if (!current) return
  if (versionNo === current.version) {
    versionDetailController?.abort()
    versionDetailLoading.value = false
    contentMode.value = 'preview'
    draftTitle.value = current.title
    draftContent.value = current.content || ''
    return
  }

  versionDetailController?.abort()
  const controller = new AbortController()
  versionDetailController = controller
  versionDetailLoading.value = true
  contentMode.value = 'preview'
  try {
    const detail = await getDocumentVersion(current.id, versionNo, controller.signal)
    if (controller.signal.aborted) return
    draftTitle.value = current.title
    draftContent.value = detail.content || ''
  } catch (error) {
    if (!controller.signal.aborted) {
      selectedVersionNo.value = current.version
      ElMessage.error(normalizeApiError(error).message)
    }
  } finally {
    if (!controller.signal.aborted) versionDetailLoading.value = false
  }
}

async function rollbackSelectedVersion(): Promise<void> {
  const current = documentDetail.value
  const versionNo = selectedVersionNo.value
  if (!current || versionNo === null || !isHistoricalVersion.value || !canEdit.value) return
  try {
    await ElMessageBox.confirm(
      `将 v${versionNo} 的内容作为新版本保存，原有历史版本不会被覆盖。确定继续吗？`,
      '回滚文档版本',
      {
        type: 'warning',
        confirmButtonText: '确认回滚',
        cancelButtonText: '取消',
      },
    )
    rollbackLoading.value = true
    await rollbackDocumentVersion(current.id, versionNo)
    await loadDocument(current.id)
    ElMessage.success(`已回滚到 v${versionNo}，并生成新的最新版本`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  } finally {
    rollbackLoading.value = false
  }
}

async function loadActivities(documentId: EntityId, signal: AbortSignal): Promise<void> {
  activityLoading.value = true
  try {
    const page = await listDocumentActivities(documentId, 8, signal)
    if (!signal.aborted) {
      activityError.value = ''
      activityPage.value = page
      scheduleActivityPolling(documentId, signal)
    }
  } catch (error) {
    if (!signal.aborted) {
      activityError.value = normalizeApiError(error).message
      scheduleActivityPolling(documentId, signal)
    }
  } finally {
    if (!signal.aborted) activityLoading.value = false
  }
}

function clearActivityPollTimer(): void {
  if (activityPollTimer === null) return
  clearTimeout(activityPollTimer)
  activityPollTimer = null
}

function hasActiveActivities(): boolean {
  return activityPage.value.records.some(
    (activity) => activity.type === 'TASK' && ACTIVE_ACTIVITY_STATUSES.has(activity.status || ''),
  )
}

function scheduleActivityPolling(documentId: EntityId, signal: AbortSignal): void {
  clearActivityPollTimer()
  if (signal.aborted || document.hidden || !hasActiveActivities()) return
  activityPollTimer = setTimeout(() => {
    activityPollTimer = null
    if (
      signal.aborted ||
      document.hidden ||
      String(selectedDocumentId.value) !== String(documentId)
    ) {
      return
    }
    void loadActivities(documentId, signal)
  }, ACTIVITY_POLL_INTERVAL_MS)
}

function refreshVisibleDocumentActivities(): void {
  if (document.hidden || activityLoading.value || !canReadActivities.value) return
  const documentId = documentDetail.value?.id
  if (!documentId) return
  clearActivityPollTimer()
  activityController?.abort()
  const controller = new AbortController()
  activityController = controller
  void loadActivities(documentId, controller.signal)
}

function handleDocumentVisibilityChange(): void {
  if (document.hidden) {
    clearActivityPollTimer()
    return
  }
  refreshVisibleDocumentActivities()
}

async function selectTreeNode(node: DocumentTreeNodeData): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId || String(selectedDocumentId.value) === String(node.id)) return
  if (!(await flushDraftBeforeNavigation())) return
  await router.push({
    name: 'space-documents',
    params: { spaceId: String(spaceId), documentId: String(node.id) },
  })
}

async function renameTreeNode(payload: {
  node: DocumentTreeNodeData
  title: string
}): Promise<void> {
  if (!canEdit.value || renamingNodeId.value !== null) return
  const { node, title } = payload
  if (
    node.nodeType === 'DOCUMENT' &&
    String(selectedDocumentId.value) === String(node.id) &&
    isDirty.value
  ) {
    ElMessage.warning('当前文档有未保存内容，请先保存后再修改名称')
    return
  }

  renamingNodeId.value = node.id
  try {
    if (node.nodeType === 'DIRECTORY') {
      const updated = await updateDirectory(node.id, { title })
      updateTreeNodeTitle(tree.value, node.id, updated.title)
      if (String(directoryDetail.value?.id) === String(node.id)) {
        directoryDetail.value = updated
      }
      ElMessage.success('目录名称已更新')
    } else {
      const current =
        String(documentDetail.value?.id) === String(node.id) && documentDetail.value
          ? documentDetail.value
          : await getDocument(node.id)
      const updated = await updateDocument(node.id, {
        baseVersion: current.version,
        title,
      })
      updateTreeNodeTitle(tree.value, node.id, updated.title)
      if (String(documentDetail.value?.id) === String(node.id)) {
        documentDetail.value = updated
        if (!isHistoricalVersion.value) {
          suppressDraftSave = true
          draftTitle.value = updated.title
          await nextTick()
          suppressDraftSave = false
        }
      }
      ElMessage.success('文档名称已更新')
    }
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    suppressDraftSave = false
    renamingNodeId.value = null
  }
}

function toggleNode(id: EntityId): void {
  const next = new Set(expandedIds.value)
  const key = String(id)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedIds.value = next
}

function expandAll(): void {
  expandedIds.value = new Set(
    allNodes(tree.value)
      .filter((node) => node.children.length)
      .map((node) => String(node.id)),
  )
}

function collapseAll(): void {
  expandedIds.value = new Set()
}

function toggleAllExpansion(): void {
  if (hasExpandedTreeNode.value) collapseAll()
  else expandAll()
}

function handleDragEnd(): void {
  removePointerListeners()
  pendingPointerDrag.value = null
  draggingNode.value = null
  dropTargetId.value = null
  rootDropActive.value = false
}

function handlePointerStart(payload: { node: DocumentTreeNodeData; event: PointerEvent }): void {
  removePointerListeners()
  draggingNode.value = null
  dropTargetId.value = null
  rootDropActive.value = false
  pendingPointerDrag.value = {
    node: payload.node,
    x: payload.event.clientX,
    y: payload.event.clientY,
  }
  pointerMoveListener = handlePointerMove
  pointerUpListener = handlePointerUp
  window.addEventListener('pointermove', pointerMoveListener)
  window.addEventListener('pointerup', pointerUpListener)
  window.addEventListener('pointercancel', pointerUpListener)
}

function handlePointerMove(event: PointerEvent): void {
  const pending = pendingPointerDrag.value
  if (!pending) return
  const movedDistance = Math.hypot(event.clientX - pending.x, event.clientY - pending.y)
  if (!draggingNode.value && movedDistance < 6) return
  if (!draggingNode.value) draggingNode.value = pending.node

  updatePointerDropTarget(event.clientX, event.clientY)
}

function updatePointerDropTarget(clientX: number, clientY: number): void {
  const targetElement = document.elementFromPoint(clientX, clientY)
  if (isRootDropTarget(targetElement)) {
    dropTargetId.value = null
    rootDropActive.value = true
    return
  }
  const nodeElement = targetElement?.closest<HTMLElement>('[data-document-tree-node-id]')
  const targetId = nodeElement?.dataset.documentTreeNodeId
  const targetNode = targetId ? findNode(tree.value, targetId) : null
  if (targetNode) {
    handleDragOver(targetNode)
  } else {
    dropTargetId.value = null
    rootDropActive.value = false
  }
}

function isRootDropTarget(targetElement: Element | null): boolean {
  if (targetElement?.closest('[data-document-tree-root-drop-zone]')) return true
  return Boolean(
    targetElement?.closest('[data-document-tree-root-container]') &&
    !targetElement.closest('[data-document-tree-node-id]'),
  )
}

function handleRootPointerEnter(): void {
  if (!draggingNode.value) return
  dropTargetId.value = null
  rootDropActive.value = true
}

function handleRootPointerLeave(): void {
  if (!draggingNode.value) return
  rootDropActive.value = false
}

function handlePointerUp(event: PointerEvent): void {
  const source = draggingNode.value
  if (source) {
    updatePointerDropTarget(event.clientX, event.clientY)
    if (isPointInsideRootDropZone(event.clientX, event.clientY)) {
      dropTargetId.value = null
      rootDropActive.value = true
    }
  }
  const targetDirectoryId = rootDropActive.value ? null : dropTargetId.value
  removePointerListeners()
  pendingPointerDrag.value = null
  if (!source) {
    dropTargetId.value = null
    rootDropActive.value = false
    return
  }
  if (!rootDropActive.value && targetDirectoryId === null) {
    handleDragEnd()
    return
  }
  void moveDraggedNode(targetDirectoryId, source)
}

function isPointInsideRootDropZone(clientX: number, clientY: number): boolean {
  const rootDropZone = document.querySelector<HTMLElement>('[data-document-tree-root-drop-zone]')
  if (!rootDropZone) return false
  const rect = rootDropZone.getBoundingClientRect()
  return (
    clientX >= rect.left && clientX <= rect.right && clientY >= rect.top && clientY <= rect.bottom
  )
}

function removePointerListeners(): void {
  if (pointerMoveListener) window.removeEventListener('pointermove', pointerMoveListener)
  if (pointerUpListener) {
    window.removeEventListener('pointerup', pointerUpListener)
    window.removeEventListener('pointercancel', pointerUpListener)
  }
  pointerMoveListener = null
  pointerUpListener = null
}

function handleDragOver(node: DocumentTreeNodeData): void {
  rootDropActive.value = false
  if (!draggingNode.value || node.nodeType !== 'DIRECTORY') return
  if (String(draggingNode.value.id) === String(node.id)) {
    dropTargetId.value = null
    return
  }
  if (draggingNode.value.nodeType === 'DIRECTORY' && containsNode(draggingNode.value, node.id)) {
    dropTargetId.value = null
    return
  }
  dropTargetId.value = node.id
}

async function moveDraggedNode(
  directoryId: EntityId | null,
  sourceNode: DocumentTreeNodeData | null = null,
): Promise<void> {
  const source = sourceNode || draggingNode.value
  if (!source || !canEdit.value || movingNode.value) return
  if (String(source.parentId) === String(directoryId)) {
    handleDragEnd()
    return
  }
  if (source.nodeType === 'DIRECTORY' && directoryId && containsNode(source, directoryId)) {
    ElMessage.warning('不能移动到当前目录或其子目录中')
    handleDragEnd()
    return
  }

  movingNode.value = true
  try {
    if (source.nodeType === 'DIRECTORY') {
      await moveDirectory(source.id, directoryId)
    } else {
      await moveDocument(source.id, directoryId)
    }
    ElMessage.success(`${source.nodeType === 'DIRECTORY' ? '目录' : '文档'}已移动`)
    handleDragEnd()
    await loadTree()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    movingNode.value = false
    handleDragEnd()
  }
}

function scheduleDraftSave(): void {
  const current = documentDetail.value
  if (
    suppressDraftSave ||
    draftHydrating ||
    !current ||
    isHistoricalVersion.value ||
    !canEdit.value
  )
    return
  if (!isDirty.value) {
    void clearDocumentDraft(current.id).catch(() => undefined)
    return
  }
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = setTimeout(() => {
    draftSaveTimer = null
    void persistDraft().catch(() => undefined)
  }, 800)
}

async function persistDraft(): Promise<void> {
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
  const current = documentDetail.value
  if (!current || draftHydrating || isHistoricalVersion.value || !canEdit.value || !isDirty.value)
    return
  if (draftSavePromise) {
    await draftSavePromise
    if (documentDetail.value?.id === current.id && isDirty.value) await persistDraft()
    return
  }

  const request = saveDocumentDraft(current.id, {
    baseVersion: draftBaseVersion.value ?? current.version,
    title: draftTitle.value,
    content: draftContent.value,
  })
  draftSavePromise = request.then(() => undefined)
  try {
    await draftSavePromise
  } finally {
    draftSavePromise = null
  }
}

async function flushDraftSave(): Promise<void> {
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = null
    await persistDraft()
  } else if (draftSavePromise) {
    await draftSavePromise
  }
}

async function flushDraftBeforeNavigation(): Promise<boolean> {
  try {
    await flushDraftSave()
    return true
  } catch (error) {
    ElMessage.warning(`未保存草稿暂存失败，已取消切换：${normalizeApiError(error).message}`)
    return false
  }
}

async function saveDocument(): Promise<void> {
  if (!documentDetail.value || !canEdit.value || !isDirty.value) return
  saving.value = true
  try {
    try {
      await flushDraftSave()
    } catch (error) {
      ElMessage.warning(
        `未保存草稿暂存失败，但仍将尝试保存文档：${normalizeApiError(error).message}`,
      )
    }
    const updated = await updateDocument(documentDetail.value.id, {
      baseVersion: draftBaseVersion.value ?? documentDetail.value.version,
      title: draftTitle.value.trim() || '未命名文档',
      content: draftContent.value,
    })
    documentDetail.value = updated
    draftBaseVersion.value = updated.version
    draftTitle.value = updated.title
    draftContent.value = updated.content || ''
    try {
      await clearDocumentDraft(updated.id)
    } catch (error) {
      ElMessage.warning(`文档已保存，但未保存草稿清理失败：${normalizeApiError(error).message}`)
    }
    await loadTree()
    ElMessage.success('文档已保存')
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (apiError instanceof ApiError && apiError.code === 40900) {
      ElMessage.error('文档已被其他人修改，请刷新后重试，当前内容未覆盖服务器版本')
    } else {
      ElMessage.error(apiError.message)
    }
  } finally {
    saving.value = false
  }
}

function openCreateDialog(nodeType: DocumentNodeType = 'DOCUMENT'): void {
  createForm.title = ''
  createForm.docType = 'DRAFT'
  createForm.nodeType = nodeType
  createForm.parentId = isDirectory.value ? directoryDetail.value?.id || null : null
  if (
    nodeType === 'DIRECTORY' &&
    createForm.parentId &&
    directoryDepth(tree.value, createForm.parentId) >= 3
  ) {
    createForm.parentId = null
  }
  createDialogVisible.value = true
}

function openCreateDocumentDialog(): void {
  openCreateDialog('DOCUMENT')
}

async function createNewDocument(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  const title = createForm.title.trim()
  if (!spaceId || !title) {
    ElMessage.warning(createForm.nodeType === 'DIRECTORY' ? '请输入目录名称' : '请输入文档标题')
    return
  }
  creating.value = true
  try {
    const created =
      createForm.nodeType === 'DIRECTORY'
        ? await createDirectory({
            spaceId,
            parentId: createForm.parentId,
            title,
          })
        : await createDocument({
            spaceId,
            directoryId: createForm.parentId,
            title,
            docType: createForm.docType,
            content: '',
          })
    createDialogVisible.value = false
    await router.push({
      name: 'space-documents',
      params: { spaceId: String(spaceId), documentId: String(created.id) },
    })
    await loadTree()
    ElMessage.success(createForm.nodeType === 'DIRECTORY' ? '目录已创建' : '文档已创建')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    creating.value = false
  }
}

async function archiveCurrentDocument(): Promise<void> {
  if (!documentDetail.value && !directoryDetail.value) return
  try {
    const itemType = isDirectory.value ? '目录' : '文档'
    await ElMessageBox.confirm(`确定归档“${selectedTitle.value}”吗？`, `归档${itemType}`, {
      type: 'warning',
      confirmButtonText: '归档',
      cancelButtonText: '取消',
    })
    if (isDirectory.value) await archiveDirectory(directoryDetail.value!.id)
    else await archiveDocument(documentDetail.value!.id)
    ElMessage.success(`${itemType}已归档`)
    await router.replace({
      name: 'space-documents',
      params: { spaceId: String(workspaceStore.currentSpaceId) },
    })
    await loadTree()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function openTableDialog(): void {
  if (isHistoricalVersion.value) return
  tableRows.value = 3
  tableColumns.value = 3
  tableDialogVisible.value = true
}

function insertTableContent(): void {
  if (isHistoricalVersion.value) return
  insertTextAtCursor(createMarkdownTable(tableRows.value, tableColumns.value))
  tableDialogVisible.value = false
}

function createMarkdownTable(rows: number, columns: number): string {
  const header = Array.from({ length: columns }, (_, index) => `列 ${index + 1}`)
  const emptyRow = Array.from({ length: columns }, () => ' ')
  return [
    `| ${header.join(' | ')} |`,
    `| ${header.map(() => '---').join(' | ')} |`,
    ...Array.from({ length: rows - 1 }, () => `| ${emptyRow.join(' | ')} |`),
    '',
  ].join('\n')
}

function updateActiveOutline(): void {
  const container = previewHost.value || richEditorHost.value
  if (!container) return
  const headings = Array.from(
    container.querySelectorAll<HTMLElement>('h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]'),
  )
  if (!headings.length) {
    if (!markdownOutline.value.length) activeOutlineId.value = ''
    return
  }

  const threshold = container.getBoundingClientRect().top + 24
  let activeHeading = headings[0]
  for (const heading of headings) {
    if (heading.getBoundingClientRect().top <= threshold) activeHeading = heading
    else break
  }
  activeOutlineId.value = activeHeading.id
}

function detachOutlineScroll(): void {
  outlineScrollContainer?.removeEventListener('scroll', updateActiveOutline)
  outlineScrollContainer = null
}

function attachOutlineScroll(): void {
  const container = previewHost.value || richEditorHost.value
  if (!container) {
    detachOutlineScroll()
    activeOutlineId.value = ''
    return
  }
  if (outlineScrollContainer !== container) {
    detachOutlineScroll()
    outlineScrollContainer = container
    outlineScrollContainer.addEventListener('scroll', updateActiveOutline, { passive: true })
  }
  updateActiveOutline()
}

function toggleOutline(): void {
  outlineCollapsed.value = !outlineCollapsed.value
  if (!outlineCollapsed.value) {
    activeOutlineId.value = markdownOutline.value[0]?.id || ''
    void nextTick(() => attachOutlineScroll())
  }
}

async function jumpToHeading(id: string): Promise<void> {
  activeOutlineId.value = id
  if (contentMode.value !== 'preview') contentMode.value = 'preview'
  await nextTick()
  const container = previewHost.value
  if (!container) return
  const heading = Array.from(
    container.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6'),
  ).find((element) => element.id === id)
  if (!heading) return
  const top =
    heading.getBoundingClientRect().top -
    container.getBoundingClientRect().top +
    container.scrollTop
  container.scrollTo({ top: Math.max(0, top - 12), behavior: 'smooth' })
}

async function uploadImage(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !documentDetail.value || isHistoricalVersion.value) return
  try {
    const asset = await uploadDocumentImage(documentDetail.value.id, file)
    insertTextAtCursor(`![${asset.originalName}](${asset.url})`)
    ElMessage.success('图片已上传并插入文档')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    if (imageInput.value) imageInput.value.value = ''
  }
}

function insertMarkdown(prefix: string, suffix: string): void {
  if (isHistoricalVersion.value) return
  const input = contentInput.value
  const start = input?.selectionStart
  const end = input?.selectionEnd
  const selected = input ? draftContent.value.slice(start, end) : ''
  const replacement = `${prefix}${selected || '文本'}${suffix}`
  insertTextAtCursor(
    replacement,
    start,
    end,
    start === undefined ? undefined : start + replacement.length,
  )
}

function insertTextAtCursor(text: string, start?: number, end?: number, cursor?: number): void {
  const input = contentInput.value
  if (!input) {
    if (contentMode.value !== 'edit') return
    draftContent.value = `${draftContent.value.trimEnd()}\n\n${text}`.trimStart()
    void refreshRichEditor()
    void nextTick(() => richEditorHost.value?.focus())
    return
  }
  const selectionStart = start ?? input.selectionStart
  const selectionEnd = end ?? input.selectionEnd
  draftContent.value = `${draftContent.value.slice(0, selectionStart)}${text}${draftContent.value.slice(selectionEnd)}`
  requestAnimationFrame(() => {
    input.focus()
    const nextCursor = cursor ?? selectionStart + text.length
    input.setSelectionRange(nextCursor, nextCursor)
  })
}

function clearResolvedImageUrls(): void {
  for (const url of resolvedImageUrls.values()) URL.revokeObjectURL(url)
  resolvedImageUrls.clear()
  imageResolutionPromises.clear()
}

function parseDocumentImageUrl(sourceUrl: string): {
  documentId: EntityId
  assetId: EntityId
} | null {
  const match = sourceUrl.match(/(?:^|\/)api\/document\/documents\/([^/]+)\/assets\/([^/?#]+)$/)
  if (!match) return null
  return { documentId: match[1], assetId: match[2] }
}

async function resolveDocumentImageUrl(sourceUrl: string): Promise<string | null> {
  if (sourceUrl.startsWith('blob:')) return sourceUrl
  const cachedUrl = resolvedImageUrls.get(sourceUrl)
  if (cachedUrl) return cachedUrl
  const pendingRequest = imageResolutionPromises.get(sourceUrl)
  if (pendingRequest) return pendingRequest

  const reference = parseDocumentImageUrl(sourceUrl)
  if (!reference) return null
  const request = readDocumentImage(reference.documentId, reference.assetId)
    .then((blob) => {
      const resolvedUrl = URL.createObjectURL(blob)
      resolvedImageUrls.set(sourceUrl, resolvedUrl)
      return resolvedUrl
    })
    .catch(() => null)
    .finally(() => imageResolutionPromises.delete(sourceUrl))
  imageResolutionPromises.set(sourceUrl, request)
  return request
}

async function hydrateImages(container: HTMLElement | null): Promise<void> {
  if (!container) return
  const images = Array.from(container.querySelectorAll<HTMLImageElement>('img'))
  await Promise.all(
    images.map(async (image) => {
      const sourceUrl = image.getAttribute('data-markdown-src') || image.getAttribute('src')
      if (!sourceUrl || sourceUrl.startsWith('blob:')) return
      const cachedUrl = resolvedImageUrls.get(sourceUrl)
      if (cachedUrl) {
        image.src = cachedUrl
        return
      }
      const resolvedUrl = await resolveDocumentImageUrl(sourceUrl)
      if (resolvedUrl) image.src = resolvedUrl
    }),
  )
}

async function openTaskDialog(): Promise<void> {
  const document = documentDetail.value
  const spaceId = workspaceStore.currentSpaceId
  if (!document || !spaceId || !canCreateTask.value) return

  taskForm.agentId = null
  taskForm.documentId = document.id
  taskForm.name = document.title
  taskForm.instruction = ''
  taskForm.tokenBudget = null
  taskAgentsError.value = ''
  taskDocumentsError.value = ''
  taskAgents.value = []
  taskDocuments.value = []
  taskDialogVisible.value = true

  taskAgentsController?.abort()
  taskDocumentsController?.abort()
  const controller = new AbortController()
  const documentsController = new AbortController()
  taskAgentsController = controller
  taskDocumentsController = documentsController
  taskAgentsLoading.value = true
  taskDocumentsLoading.value = true
  void listDocumentTree(spaceId, { signal: documentsController.signal })
    .then((documents) => {
      if (!documentsController.signal.aborted) taskDocuments.value = documents
    })
    .catch((error) => {
      if (!documentsController.signal.aborted) {
        taskDocumentsError.value = normalizeApiError(error).message
      }
    })
    .finally(() => {
      if (!documentsController.signal.aborted) taskDocumentsLoading.value = false
    })
  try {
    taskAgents.value = await listAgents(spaceId, controller.signal)
    if (!controller.signal.aborted && enabledAgents.value.length === 1) {
      taskForm.agentId = enabledAgents.value[0].id
    }
  } catch (error) {
    if (!controller.signal.aborted) taskAgentsError.value = normalizeApiError(error).message
  } finally {
    if (!controller.signal.aborted) taskAgentsLoading.value = false
  }
}

async function createDocumentTask(): Promise<void> {
  const documentId = taskForm.documentId
  const agentId = taskForm.agentId
  const name = taskForm.name.trim()
  const instruction = taskForm.instruction.trim()
  if (!documentId) {
    ElMessage.warning('请选择目标文档')
    return
  }
  if (!agentId) {
    ElMessage.warning('请选择 Agent')
    return
  }
  if (!name) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (!instruction) {
    ElMessage.warning('请输入任务指令')
    return
  }

  taskCreating.value = true
  try {
    await createTask({
      agentId,
      documentId,
      name,
      instruction,
      tokenBudget: taskForm.tokenBudget,
    })
    taskDialogVisible.value = false
    ElMessage.success('Agent 任务已创建')
    if (canReadActivities.value && String(documentId) === String(documentDetail.value?.id)) {
      activityController?.abort()
      const controller = new AbortController()
      activityController = controller
      void loadActivities(documentId, controller.signal)
    }
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    taskCreating.value = false
  }
}

function showDocumentInfo(): void {
  if (documentDetail.value || directoryDetail.value) documentInfoDialogVisible.value = true
}

async function reloadSelectedDocument(): Promise<void> {
  if (selectedDocumentId.value) await loadDocument(selectedDocumentId.value)
}

function activityIcon(activity: DocumentActivity) {
  return activity.type === 'CHANGE_REQUEST'
    ? InfoFilled
    : activity.status === '已完成'
      ? CircleCheckFilled
      : VideoPlay
}

function activityStatusType(
  activity: DocumentActivity,
): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (activity.type === 'CHANGE_REQUEST')
    return activity.status === '已合并' ? 'success' : 'warning'
  if (activity.status === '已完成') return 'success'
  if (activity.status === '异常失败') return 'danger'
  if (activity.status === '运行中' || activity.status === '已分发') return 'primary'
  return 'info'
}

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatRelativeTime(value: string | null): string {
  if (!value) return '时间未知'
  return formatDateTime(value)
}

function countNodesByType(nodes: DocumentTreeNodeData[], type: DocumentNodeType): number {
  return nodes.reduce(
    (count, node) =>
      count + (node.nodeType === type ? 1 : 0) + countNodesByType(node.children, type),
    0,
  )
}

function documentStatusLabel(status: DocumentStatus): string {
  return status === 'ARCHIVED' ? '已归档' : '正常'
}

function allNodes(nodes: DocumentTreeNodeData[]): DocumentTreeNodeData[] {
  return nodes.flatMap((node) => [node, ...allNodes(node.children)])
}

function findNode(nodes: DocumentTreeNodeData[], id: EntityId): DocumentTreeNodeData | null {
  for (const node of nodes) {
    if (String(node.id) === String(id)) return node
    const child = findNode(node.children, id)
    if (child) return child
  }
  return null
}

function findNodePath(
  nodes: DocumentTreeNodeData[],
  id: EntityId,
  ancestors: DocumentTreeNodeData[] = [],
): DocumentTreeNodeData[] | null {
  for (const node of nodes) {
    const path = [...ancestors, node]
    if (String(node.id) === String(id)) return path
    const childPath = findNodePath(node.children, id, path)
    if (childPath) return childPath
  }
  return null
}

function updateTreeNodeTitle(nodes: DocumentTreeNodeData[], id: EntityId, title: string): boolean {
  for (const node of nodes) {
    if (String(node.id) === String(id)) {
      node.title = title
      return true
    }
    if (updateTreeNodeTitle(node.children, id, title)) return true
  }
  return false
}

function containsNode(node: DocumentTreeNodeData, id: EntityId): boolean {
  return node.children.some((child) => String(child.id) === String(id) || containsNode(child, id))
}

function firstNode(nodes: DocumentTreeNodeData[]): DocumentTreeNodeData | null {
  return nodes[0] || null
}

function buildDirectoryOptions(
  nodes: DocumentTreeNodeData[],
  depth: number,
  disableThirdLevel: boolean,
): DirectoryOption[] {
  return nodes
    .filter((node) => node.nodeType === 'DIRECTORY')
    .map((node) => ({
      value: node.id,
      label: node.title,
      children: buildDirectoryOptions(node.children, depth + 1, disableThirdLevel),
      disabled: disableThirdLevel && depth >= 3,
    }))
}

function buildTaskDocumentOptions(nodes: DocumentTreeNodeData[]): DirectoryOption[] {
  return nodes.map((node) => ({
    value: node.id,
    label: node.title,
    children: buildTaskDocumentOptions(node.children),
    disabled: node.nodeType === 'DIRECTORY',
  }))
}

function directoryDepth(nodes: DocumentTreeNodeData[], id: EntityId, depth = 1): number {
  for (const node of nodes) {
    if (String(node.id) === String(id)) return node.nodeType === 'DIRECTORY' ? depth : 0
    const childDepth = directoryDepth(node.children, id, depth + 1)
    if (childDepth > 0) return childDepth
  }
  return 0
}

watch(keyword, (value, previousValue) => {
  if (!previousValue.trim() && value.trim()) {
    expandedIdsBeforeSearch = new Set(expandedIds.value)
  }
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void loadTree(), 260)
})

watch(contentMode, (mode) => {
  if (mode === 'edit') {
    void refreshRichEditor().then(() => attachOutlineScroll())
    return
  }
  void nextTick(() => {
    attachOutlineScroll()
    if (mode === 'preview') void hydrateImages(previewHost.value)
  })
})

watch(draftContent, () => {
  void nextTick(() => {
    attachOutlineScroll()
    const imageHost = contentMode.value === 'edit' ? richEditorHost.value : previewHost.value
    if (contentMode.value !== 'source') void hydrateImages(imageHost)
  })
})

watch([draftTitle, draftContent], scheduleDraftSave)

onBeforeRouteUpdate(async (to, from) => {
  const routeDocumentId = (value: string | string[] | undefined): string =>
    Array.isArray(value) ? value[0] || '' : value || ''
  const sameDocument =
    String(to.params.spaceId) === String(from.params.spaceId) &&
    routeDocumentId(to.params.documentId) === routeDocumentId(from.params.documentId)
  return sameDocument || flushDraftBeforeNavigation()
})

watch(
  () => [route.params.spaceId, route.params.documentId] as const,
  ([, documentIdParam]) => {
    const spaceId = workspaceStore.currentSpaceId
    const documentId = Array.isArray(documentIdParam) ? documentIdParam[0] : documentIdParam
    if (!spaceId) return
    if (String(loadedTreeSpaceId) !== String(spaceId)) {
      void loadTree()
      return
    }
    if (!documentId) {
      if (tree.value.length) void selectTreeNode(firstNode(tree.value)!)
      return
    }
    if (findNode(tree.value, documentId)) void loadDocument(documentId)
    else void loadTree()
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('visibilitychange', handleDocumentVisibilityChange)
})

onBeforeUnmount(() => {
  detachOutlineScroll()
  clearResolvedImageUrls()
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  treeController?.abort()
  detailController?.abort()
  activityController?.abort()
  clearActivityPollTimer()
  document.removeEventListener('visibilitychange', handleDocumentVisibilityChange)
  taskAgentsController?.abort()
  taskDocumentsController?.abort()
  removePointerListeners()
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.document-page {
  display: grid;
  height: 100%;
  min-width: 0;
  min-height: 0;
  gap: var(--adw-space-5);
  grid-template-rows: auto minmax(0, 1fr);
}

.document-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-5);
}

.document-page__title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.document-page__title-row h1 {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: 28px;
}

.document-page__divider {
  width: 1px;
  height: 22px;
  background: var(--adw-border-color);
}

.document-page__space-name,
.document-page__brand p {
  color: var(--adw-text-secondary);
}

.document-page__brand p {
  margin: 6px 0 0;
  font-size: var(--adw-font-size-body);
}

.document-page__header-actions {
  display: flex;
  gap: var(--adw-space-3);
}

.surface-card,
.document-info-card {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-lg);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
}

.document-workspace {
  display: grid;
  height: 100%;
  min-height: 0;
  grid-template-columns: 250px minmax(0, 1fr) 278px;
  overflow: hidden;
}

.document-tree-panel,
.document-editor-panel,
.document-info-panel {
  min-width: 0;
}

.document-tree-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--adw-border-color-light);
  background: #fbfcfe;
}

.document-tree-panel__header,
.document-info-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-3);
  padding: 18px 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
}

.document-tree-panel__header strong,
.document-info-card__header strong {
  color: var(--adw-text-primary);
  font-size: 15px;
}

.document-tree-panel__header span,
.document-info-card__header span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.document-tree-panel__search {
  padding: 14px 12px 8px;
}

.document-tree-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px 8px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.document-tree-panel__toolbar span {
  margin-right: auto;
}

.document-tree-panel__toolbar button {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.document-tree {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 2px;
  align-content: start;
  overflow: auto;
  padding: 6px 10px 16px;
}

.document-tree__root-drop-zone {
  min-height: 34px;
  padding: 8px 10px;
  border: 1px dashed var(--adw-border-color);
  border-radius: 6px;
  color: var(--adw-text-tertiary);
  background: var(--adw-surface);
  font-size: 12px;
  text-align: center;
}

.document-tree__root-drop-zone--active {
  border-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.document-tree-state,
.document-editor-state,
.document-info-card__empty {
  display: grid;
  place-items: center;
  gap: 9px;
  padding: 40px 20px;
  color: var(--adw-text-tertiary);
  text-align: center;
}

.document-tree-state--error,
.document-editor-state--error,
.document-info-card__empty--error {
  color: var(--adw-color-danger);
}

.document-editor-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: #fff;
}

.document-info-panel {
  min-height: 0;
  overflow-y: auto;
}

.document-editor-loading-overlay {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 8px;
  color: var(--adw-text-tertiary);
  background: rgb(255 255 255 / 72%);
}

.document-editor__header {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 18px;
  border-bottom: 1px solid var(--adw-border-color-light);
}

.document-editor__breadcrumb {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: var(--adw-text-tertiary);
  font-size: 13px;
}

.document-editor__breadcrumb strong {
  max-width: 300px;
  overflow: hidden;
  color: var(--adw-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-editor__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.save-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--adw-color-success);
  font-size: 12px;
}

.save-state--dirty {
  color: var(--adw-color-warning);
}

.version-select {
  width: 96px;
}

.version-option-current {
  margin-left: auto;
  padding: 2px 5px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.formal-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 14px 0;
  padding: 10px 12px;
  border: 1px solid #f4c978;
  border-radius: var(--adw-radius-sm);
  color: #946200;
  background: #fff9e9;
  font-size: 13px;
}

.directory-editor-state {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 420px;
  padding: 32px;
  color: var(--adw-text-tertiary);
  text-align: center;
}

.directory-editor-state .el-icon {
  color: var(--adw-color-primary);
}

.directory-editor-state strong {
  color: var(--adw-text-primary);
  font-size: 18px;
}

.directory-editor-state span {
  max-width: 360px;
  line-height: 1.6;
}

.markdown-editor {
  position: relative;
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  margin: 12px 14px 0;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}

.document-outline-float {
  position: absolute;
  z-index: 2;
  top: 56px;
  right: 14px;
  width: 200px;
  max-height: calc(100% - 74px);
  overflow: hidden;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 8px 24px rgb(16 24 40 / 10%);
}

.document-outline-float.is-collapsed {
  width: auto;
}

.document-outline-float__toggle {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border: 0;
  color: var(--adw-text-primary);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.document-outline-float__toggle span:last-child {
  color: var(--adw-color-primary);
  font-weight: 400;
}

.document-outline-float__toggle:hover {
  background: var(--adw-color-primary-soft);
}

.version-detail-loading {
  position: absolute;
  z-index: 1;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 8px;
  color: var(--adw-text-tertiary);
  background: rgb(255 255 255 / 72%);
}

.markdown-editor__toolbar {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border-bottom: 1px solid var(--adw-border-color-light);
  color: var(--adw-text-secondary);
}

.markdown-editor__toolbar button {
  display: inline-flex;
  min-width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0 7px;
  border: 0;
  border-radius: 5px;
  color: var(--adw-text-secondary);
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.markdown-editor__toolbar button:hover {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.markdown-editor__mode {
  min-width: 70px;
  padding-left: 5px;
  color: var(--adw-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.markdown-editor__separator {
  width: 1px;
  height: 22px;
  margin: 0 4px;
  background: var(--adw-border-color-light);
}

.markdown-editor__view-switch {
  display: flex;
  margin-left: auto;
  padding: 2px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 6px;
  background: var(--adw-surface-muted);
}

.markdown-editor__view-switch button {
  min-width: 58px;
  height: 26px;
  padding: 0 9px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.markdown-editor__view-switch button.is-active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  font-weight: 600;
}

.markdown-editor__title {
  padding: 20px 20px 8px;
}

.markdown-editor__title :deep(.el-input__wrapper) {
  padding: 0;
  box-shadow: none;
}

.markdown-editor__title :deep(.el-input__inner) {
  height: 42px;
  color: var(--adw-text-primary);
  font-size: 28px;
  font-weight: 700;
}

.markdown-editor__title :deep(.el-input__inner[readonly]),
.markdown-editor__content[readonly] {
  cursor: default;
}

.markdown-editor__preview {
  min-height: 360px;
  flex: 1;
  overflow: auto;
  padding: 26px 28px 30px;
  color: var(--adw-text-primary);
}

.markdown-editor__rich-content {
  width: 100%;
  max-width: none;
  min-height: 360px;
  flex: 1;
  overflow: auto;
  padding: 26px 28px 30px;
  outline: 0;
  color: var(--adw-text-primary);
}

.markdown-editor__preview > h1 {
  margin: 0 0 22px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
  font-size: 28px;
  line-height: 1.3;
}

.markdown-preview__body {
  max-width: 860px;
  font-size: 15px;
  line-height: 1.8;
}

.markdown-preview__body :deep(h1),
.markdown-preview__body :deep(h2),
.markdown-preview__body :deep(h3),
.markdown-preview__body :deep(h4),
.markdown-preview__body :deep(h5),
.markdown-preview__body :deep(h6) {
  margin: 24px 0 10px;
  color: var(--adw-text-primary);
  line-height: 1.4;
}

.markdown-preview__body :deep(h1) {
  font-size: 25px;
}

.markdown-preview__body :deep(h2) {
  font-size: 22px;
}

.markdown-preview__body :deep(h3) {
  font-size: 19px;
}

.markdown-preview__body :deep(p) {
  margin: 0 0 14px;
}

.markdown-preview__body :deep(ul),
.markdown-preview__body :deep(ol) {
  margin: 0 0 16px;
  padding-left: 26px;
}

.markdown-preview__body :deep(li) {
  padding-left: 4px;
}

.markdown-preview__body :deep(blockquote) {
  margin: 0 0 16px;
  padding: 8px 16px;
  border-left: 3px solid var(--adw-color-primary);
  color: var(--adw-text-secondary);
  background: var(--adw-color-primary-soft);
}

.markdown-preview__body :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.9em;
}

.markdown-preview__body :deep(pre) {
  overflow: auto;
  margin: 0 0 16px;
  padding: 14px 16px;
  border-radius: 6px;
  color: #e5edf9;
  background: #1e293b;
}

.markdown-preview__body :deep(pre code) {
  padding: 0;
  color: inherit;
  background: transparent;
  font-size: 13px;
}

.markdown-preview__body :deep(a) {
  color: var(--adw-color-primary);
  text-decoration: underline;
}

.markdown-preview__body :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 12px 0 16px;
  border-radius: var(--adw-radius-sm);
}

.markdown-preview__body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  font-size: 14px;
}

.markdown-preview__body :deep(th),
.markdown-preview__body :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--adw-border-color-light);
  text-align: left;
}

.markdown-preview__body :deep(th) {
  background: var(--adw-surface-muted);
  font-weight: 600;
}

.markdown-preview__empty {
  color: var(--adw-text-tertiary);
}

.markdown-editor__content[readonly] {
  background: #fcfdff;
}

.markdown-editor__content {
  width: 100%;
  min-height: 360px;
  flex: 1;
  resize: none;
  padding: 8px 20px 20px;
  border: 0;
  outline: 0;
  color: var(--adw-text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 1.8;
}

.markdown-editor__footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 14px;
  border-top: 1px solid var(--adw-border-color-light);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.document-editor__bottom-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 14px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.document-info-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 14px;
  border-left: 1px solid var(--adw-border-color-light);
  background: #fbfcfe;
}

.document-info-card {
  overflow: hidden;
  border-radius: var(--adw-radius-md);
  box-shadow: none;
}

.document-outline {
  max-height: 260px;
  overflow-y: auto;
  padding: 8px 6px;
  border-top: 1px solid var(--adw-border-color-light);
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.document-outline::-webkit-scrollbar {
  display: none;
}

.document-outline__item {
  display: block;
  width: 100%;
  overflow: hidden;
  box-sizing: border-box;
  margin: 1px 0;
  padding-top: 7px;
  padding-right: 10px;
  padding-bottom: 7px;
  border: 0;
  border-radius: 5px;
  color: var(--adw-text-secondary);
  background: transparent;
  font-size: 12px;
  line-height: 1.5;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    box-shadow 0.15s ease;
}

.document-outline__item.is-level-1 {
  color: var(--adw-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.document-outline__item.is-level-2 {
  color: var(--adw-text-secondary);
  font-weight: 500;
}

.document-outline__item.is-level-3,
.document-outline__item.is-level-4,
.document-outline__item.is-level-5,
.document-outline__item.is-level-6 {
  color: var(--adw-text-tertiary);
}

.document-outline__item.is-active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  box-shadow: inset 3px 0 0 var(--adw-color-primary);
  font-weight: 700;
}

.document-outline__item:hover {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.document-info-card__body {
  padding: 16px;
}

.document-info-card dl {
  display: grid;
  gap: 15px;
  margin: 0;
}

.document-info-card dl div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.document-info-card dt {
  color: var(--adw-text-secondary);
  font-size: 13px;
}

.document-info-card dd {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: 13px;
  text-align: right;
}

.document-detail {
  display: grid;
  gap: 20px;
}

.document-detail__hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid #d8e3fb;
  border-radius: var(--adw-radius-md);
  background: linear-gradient(135deg, #f5f8ff 0%, #fbfcff 100%);
}

.document-detail__hero-icon {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  place-items: center;
  color: var(--adw-color-primary);
  border-radius: 12px;
  background: var(--adw-color-primary-soft);
}

.document-detail__hero-content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 3px;
}

.document-detail__title {
  overflow-wrap: anywhere;
  color: var(--adw-text-primary);
  font-size: 17px;
  line-height: 1.4;
}

.document-detail__type {
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.document-detail__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.document-detail__item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface-muted);
}

.document-detail__item--wide {
  grid-column: 1 / -1;
}

.document-detail__item dt {
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.document-detail__item dd {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.document-info-card__button {
  width: 100%;
  margin-top: 18px;
}

.activity-list {
  position: relative;
  display: grid;
}

.activity-list__loading-overlay {
  position: absolute;
  z-index: 1;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 6px;
  color: var(--adw-text-tertiary);
  background: rgb(251 252 254 / 78%);
  font-size: 12px;
}

.activity-item {
  display: flex;
  gap: 10px;
  padding: 14px 12px;
  border-bottom: 1px solid var(--adw-border-color-light);
}

.activity-item:last-child {
  border-bottom: 0;
}

.activity-item__icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.activity-item__icon--change_request {
  color: var(--adw-color-success);
  background: var(--adw-color-success-soft);
}

.activity-item__content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.activity-item__headline {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.activity-item__headline strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-item__content > span,
.activity-item__content time {
  overflow: hidden;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-editor-state {
  min-height: 500px;
  flex: 1;
}

.document-editor-state strong {
  color: var(--adw-text-primary);
}

.document-editor-state--error {
  color: var(--adw-color-danger);
}

.dialog-help {
  margin: 0;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

.dialog-error {
  margin: 6px 0 0;
  color: var(--adw-color-danger);
  font-size: 12px;
}

.table-size-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--adw-space-4);
}

.table-size-form .el-form-item {
  margin-bottom: 0;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .document-workspace {
    grid-template-columns: 230px minmax(0, 1fr);
  }

  .document-info-panel {
    display: none;
  }
}

@media (max-width: 760px) {
  .document-page {
    height: auto;
  }

  .document-page__header,
  .document-editor__header,
  .document-editor__actions,
  .markdown-editor__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .document-page__header-actions {
    width: 100%;
  }

  .document-page__header-actions .el-button {
    flex: 1;
  }

  .document-workspace {
    display: block;
    height: auto;
  }

  .document-tree-panel {
    border-right: 0;
    border-bottom: 1px solid var(--adw-border-color-light);
  }

  .document-tree {
    max-height: 280px;
    overflow-y: auto;
  }

  .document-editor__header {
    gap: 10px;
    padding-block: 12px;
  }

  .document-editor__actions {
    width: 100%;
  }

  .markdown-editor__content {
    min-height: 320px;
  }

  .markdown-editor__rich-content {
    min-height: 320px;
  }
}
</style>
