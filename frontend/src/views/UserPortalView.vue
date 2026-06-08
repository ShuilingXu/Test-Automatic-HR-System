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
      <div class="surface application-panel">
        <div class="section-head">
          <div>
            <h3>我的报名记录</h3>
            <p class="page-subtitle">这里会长期保留你的报名记录和对应的面试入口，不再需要输入流程流水号。</p>
          </div>
          <el-button @click="loadMyCandidates">刷新</el-button>
        </div>
        <div v-if="candidates.length === 0" class="empty-box">暂无报名记录，完成报名后会显示在这里。</div>
        <div v-else class="application-list">
          <div v-for="item in candidates" :key="item.id" class="application-card">
            <div>
              <strong>{{ item.jobTitle || `岗位 ${item.jobId}` }}</strong>
              <span>报名编号：{{ item.id }} / 状态：{{ item.interviewStageStatus || item.applicationStatus || '已提交' }}</span>
              <small>简历：{{ item.resumeFileName || '未上传' }}</small>
            </div>
            <RouterLink v-if="item.interviewProcessId" class="link-chip" :to="`/interview/interviewee?processId=${item.interviewProcessId}`">进入面试</RouterLink>
            <span v-else class="pending-chip">等待 HR 发起面试</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, recruitmentApi } from '../services/api'

const router = useRouter()
const session = reactive({ username: '', roleCode: '', email: '', profileCompleted: 0 })
const profileForm = reactive({ displayName: '', mobilePhone: '', email: '' })
const candidates = ref([])

async function loadSession() {
  try {
    const response = await authApi.getSession()
    Object.assign(session, response.data)
    Object.assign(profileForm, {
      displayName: response.data.displayName || '',
      mobilePhone: response.data.mobilePhone || '',
      email: response.data.email || '',
    })
    await loadMyCandidates()
  } catch (error) {
    ElMessage.error(error.message || '请先登录')
    router.push('/login')
  }
}

async function loadMyCandidates() {
  try {
    candidates.value = (await recruitmentApi.listMyCandidates()).data
  } catch (error) {
    ElMessage.error(error.message || '报名记录加载失败')
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
.application-panel { margin-top: 18px; }
.section-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.application-list { display: grid; gap: 12px; margin-top: 12px; }
.application-card { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 16px; border-radius: 18px; background: rgba(255, 255, 255, 0.82); }
.application-card strong, .application-card span, .application-card small { display: block; }
.application-card span { margin: 6px 0; color: #61727d; }
.pending-chip { padding: 8px 12px; border-radius: 999px; background: #eef2f4; color: #61727d; white-space: nowrap; }
@media (max-width: 900px) { .topline { flex-direction: column; } }
@media (max-width: 900px) { .section-head, .application-card { flex-direction: column; align-items: flex-start; } }
</style>
