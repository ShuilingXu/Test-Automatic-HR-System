<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interviewee Portal</p>
          <h1 class="page-title">面试者门户</h1>
          <p class="page-subtitle">先完善个人信息，再填写报名信息并进入线上面试。</p>
        </div>
        <div class="link-row">
          <RouterLink class="link-chip" to="/candidate/register">去报名</RouterLink>
          <RouterLink class="link-chip" to="/candidate/interview">去面试</RouterLink>
          <el-button @click="logout">退出登录</el-button>
        </div>
      </div>
      <div class="page-grid">
        <el-form :model="profileForm" label-position="top" class="surface">
          <h3>个人资料</h3>
          <el-form-item label="姓名"><el-input v-model="profileForm.displayName" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="profileForm.mobilePhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
          <el-button type="primary" @click="saveProfile">保存资料</el-button>
        </el-form>
        <div class="surface">
          <h3>当前会话</h3>
          <div class="kv-grid">
            <div><span>用户名</span><strong>{{ session.username || '-' }}</strong></div>
            <div><span>角色</span><strong>{{ session.roleCode || '-' }}</strong></div>
            <div><span>资料完成</span><strong>{{ session.profileCompleted === 1 ? '是' : '否' }}</strong></div>
            <div><span>邮箱</span><strong>{{ session.email || '-' }}</strong></div>
          </div>
          <p class="page-subtitle" style="margin-top: 16px">资料完成后再去报名页面，符合当前系统流程约束。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'

const router = useRouter()
const session = reactive({ username: '', roleCode: '', email: '', profileCompleted: 0 })
const profileForm = reactive({ displayName: '', mobilePhone: '', email: '' })

async function loadSession() {
  try {
    const response = await authApi.getSession()
    Object.assign(session, response.data)
    Object.assign(profileForm, {
      displayName: response.data.displayName || '',
      mobilePhone: response.data.mobilePhone || '',
      email: response.data.email || '',
    })
  } catch (error) {
    ElMessage.error(error.message || '请先登录')
    router.push('/login')
  }
}

async function saveProfile() {
  try {
    const response = await authApi.updateProfile({ ...profileForm })
    Object.assign(session, response.data)
    ElMessage.success('资料已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function logout() {
  await authApi.logout()
  router.push('/login')
}

onMounted(loadSession)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
@media (max-width: 900px) { .topline { flex-direction: column; } }
</style>
