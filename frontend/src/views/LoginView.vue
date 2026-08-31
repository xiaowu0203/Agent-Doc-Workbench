<template>
  <main class="login-page">
    <section class="login-intro" aria-labelledby="login-slogan">
      <BrandMark />

      <div class="login-intro__content">
        <h1 id="login-slogan">让 Agent 的每次文档修改，<br />都可审核、可回滚、可追溯</h1>
        <p>面向个人与小团队的 AI 文档协作工作台</p>

        <ul class="login-features" aria-label="产品能力">
          <li>
            <el-icon><DocumentChecked /></el-icon>
            <span>正式文档由人工审批后合并</span>
          </li>
          <li>
            <el-icon><Connection /></el-icon>
            <span>Agent、Skill 与 MCP 能力按空间管理</span>
          </li>
          <li>
            <el-icon><DataAnalysis /></el-icon>
            <span>任务预算、执行快照与审计全程可见</span>
          </li>
        </ul>
      </div>

      <p class="login-intro__footnote">
        <el-icon><CircleCheck /></el-icon>
        Apache-2.0 开源 · 数据与凭证由你掌控
      </p>
    </section>

    <section class="login-panel" aria-label="登录区域">
      <div class="login-card">
        <header class="login-card__header">
          <span>欢迎回来</span>
          <h2>登录工作台</h2>
          <p>使用你的账号继续访问空间</p>
        </header>

        <div class="login-tabs" role="tablist" aria-label="登录方式">
          <button class="login-tabs__item login-tabs__item--active" role="tab" aria-selected="true">
            账号登录
          </button>
          <button class="login-tabs__item" role="tab" aria-selected="false" @click="showComingSoon">
            OAuth2 登录
          </button>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submitLogin"
        >
          <el-form-item label="邮箱或用户名" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="Message"
              autocomplete="username"
              placeholder="请输入邮箱或用户名"
              clearable
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              type="password"
            />
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="rememberSession">记住我</el-checkbox>
            <button type="button" class="text-action" @click="showComingSoon">忘记密码？</button>
          </div>

          <el-alert
            v-if="errorMessage"
            class="login-error"
            :closable="false"
            :title="errorMessage"
            type="error"
            show-icon
          />

          <el-button class="login-submit" type="primary" :loading="submitting" native-type="submit">
            登录
          </el-button>
        </el-form>

        <div class="login-divider"><span>或使用以下方式</span></div>

        <div class="login-providers">
          <el-button size="large" @click="showComingSoon">
            <el-icon><Platform /></el-icon>
            GitHub 登录
          </el-button>
          <el-button size="large" @click="showComingSoon">
            <el-icon><OfficeBuilding /></el-icon>
            企业 OAuth2
          </el-button>
        </div>

        <p class="login-register">还没有账号？<button @click="showComingSoon">创建账号</button></p>
      </div>

      <footer class="login-footer">
        <button @click="showComingSoon">隐私说明</button>
        <button @click="showComingSoon">使用文档</button>
        <span>v0.1</span>
      </footer>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  CircleCheck,
  Connection,
  DataAnalysis,
  DocumentChecked,
  Lock,
  Message,
  OfficeBuilding,
  Platform,
} from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import BrandMark from '@/shared/components/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const rememberSession = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: '',
  password: '',
})
const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入邮箱或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function showComingSoon(): void {
  ElMessage.info('即将支持')
}

async function submitLogin(): Promise<void> {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false))) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await authStore.login({ username: form.username.trim(), password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect.startsWith('/') ? redirect : '/')
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(430px, 1fr) minmax(520px, 1fr);
  overflow: hidden;
  background: #f7f8fb;
}

.login-intro {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  padding: clamp(32px, 4vw, 56px) clamp(36px, 4.5vw, 68px);
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(circle at 18% 82%, rgb(24 91 181 / 26%), transparent 26%),
    radial-gradient(circle at 85% 22%, rgb(29 99 196 / 16%), transparent 31%),
    linear-gradient(145deg, #031b3a 0%, #062a58 52%, #031d42 100%);
}

.login-intro::before,
.login-intro::after {
  position: absolute;
  content: '';
  pointer-events: none;
}

.login-intro::before {
  inset: 0;
  opacity: 0.25;
  background-image:
    linear-gradient(90deg, transparent 97%, rgb(97 159 255 / 32%) 97%),
    linear-gradient(transparent 97%, rgb(97 159 255 / 22%) 97%);
  background-size: 180px 180px;
  mask-image: linear-gradient(to bottom right, transparent 8%, #000 52%, transparent 92%);
}

.login-intro::after {
  right: 6%;
  bottom: 18%;
  width: 170px;
  height: 170px;
  border: 1px solid rgb(93 157 255 / 12%);
  border-radius: 18px;
  transform: rotate(45deg);
}

.login-intro > * {
  z-index: 1;
}

.login-intro__content {
  width: min(100%, 600px);
  margin: auto 0;
  padding-block: 40px;
}

.login-intro h1 {
  margin: 0;
  font-size: clamp(30px, 2.3vw, 42px);
  font-weight: 750;
  letter-spacing: -1.5px;
  line-height: 1.45;
}

.login-intro__content > p {
  margin: 16px 0 36px;
  color: rgb(220 232 251 / 72%);
  font-size: clamp(16px, 1.2vw, 20px);
}

.login-features {
  display: grid;
  max-width: 560px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.login-features li {
  display: flex;
  min-height: 82px;
  align-items: center;
  gap: 24px;
  border-bottom: 1px solid rgb(122 172 239 / 18%);
  color: rgb(245 248 255 / 88%);
  font-size: 16px;
}

.login-features li:last-child {
  border-bottom: 0;
}

.login-features .el-icon {
  flex: 0 0 auto;
  color: #69a2ff;
  font-size: 35px;
}

.login-intro__footnote {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  margin: 0;
  color: #8db6f1;
  font-size: 14px;
}

.login-intro__footnote .el-icon {
  font-size: 22px;
}

.login-panel {
  position: relative;
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 20px 36px 60px;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 15%, rgb(255 255 255 / 92%), transparent 36%),
    radial-gradient(circle at 90% 75%, rgb(213 227 255 / 55%), transparent 42%),
    linear-gradient(145deg, #eef2f8, #f9fafc 48%, #e9eef7);
}

.login-panel::before {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  background: rgb(255 255 255 / 55%);
  content: '';
  filter: blur(18px);
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 440px);
  padding: 26px 28px;
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: 14px;
  background: rgb(255 255 255 / 63%);
  box-shadow:
    0 26px 70px rgb(29 43 72 / 13%),
    inset 0 1px 0 rgb(255 255 255 / 82%);
  backdrop-filter: blur(24px) saturate(135%);
}

.login-card__header {
  text-align: center;
}

.login-card__header span {
  color: var(--adw-text-secondary);
  font-size: 14px;
}

.login-card__header h2 {
  margin: 6px 0 5px;
  font-size: 26px;
  line-height: 1.2;
}

.login-card__header p {
  margin: 0;
  color: var(--adw-text-secondary);
}

.login-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin: 16px 0;
  border-bottom: 1px solid var(--adw-border-color);
}

.login-tabs__item {
  position: relative;
  height: 38px;
  border: 0;
  color: var(--adw-text-tertiary);
  background: transparent;
  cursor: pointer;
}

.login-tabs__item--active {
  color: var(--adw-color-primary);
  font-weight: 700;
}

.login-tabs__item--active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  background: var(--adw-color-primary);
  content: '';
}

.login-card :deep(.el-form-item) {
  margin-bottom: 14px;
}

.login-card :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: #4e596b;
  line-height: 1.2;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 7px;
  background: rgb(255 255 255 / 58%);
  box-shadow: 0 0 0 1px rgb(190 199 214 / 78%) inset;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 0 14px;
}

.text-action,
.login-register button,
.login-footer button {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  cursor: pointer;
}

.login-error {
  margin: -4px 0 12px;
}

.login-submit {
  width: 100%;
  height: 44px;
  border-radius: 7px;
  font-size: 15px;
  box-shadow: 0 10px 22px rgb(36 91 219 / 20%);
}

.login-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 18px 0 14px;
  color: var(--adw-text-tertiary);
  font-size: 14px;
}

.login-divider::before,
.login-divider::after {
  height: 1px;
  flex: 1;
  background: var(--adw-border-color);
  content: '';
}

.login-providers {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.login-providers .el-button {
  width: 100%;
  margin: 0;
  color: #3f4a5b;
  background: rgb(255 255 255 / 52%);
}

.login-register {
  margin: 16px 0 0;
  color: var(--adw-text-secondary);
  text-align: center;
}

.login-register button {
  margin-left: 8px;
}

.login-footer {
  position: absolute;
  z-index: 1;
  bottom: 20px;
  display: flex;
  gap: 38px;
  color: var(--adw-text-secondary);
  font-size: 14px;
}

.login-footer button {
  color: inherit;
}

@media (max-width: 1050px) {
  .login-page {
    grid-template-columns: 42% 58%;
  }

  .login-intro {
    padding-inline: 32px;
  }

  .login-intro h1 {
    font-size: 31px;
  }

  .login-features li {
    gap: 18px;
    font-size: 15px;
  }
}

@media (max-width: 760px) {
  .login-page {
    display: block;
    background: #edf2fa;
  }

  .login-intro {
    min-height: auto;
    padding: 21px 22px;
  }

  .login-intro__content,
  .login-intro__footnote {
    display: none;
  }

  .login-panel {
    min-height: calc(100vh - 86px);
    padding: 24px 18px 70px;
  }

  .login-card {
    padding: 26px 22px;
  }

  .login-footer {
    bottom: 20px;
    gap: 28px;
  }
}

@media (max-width: 420px) {
  .login-providers {
    grid-template-columns: 1fr;
  }
}
</style>
