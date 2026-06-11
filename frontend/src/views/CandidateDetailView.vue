<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Candidate Detail</p>
          <h1 class="page-title">候选人详情</h1>
        </div>
        <RouterLink class="link-chip" to="/admin/recruitment/candidates">返回候选人列表</RouterLink>
      </div>

      <div v-if="loading" class="empty-box">正在加载候选人信息...</div>
      <div v-else-if="!candidate" class="empty-box">候选人不存在或无权访问</div>
      <template v-else>
        <section class="surface detail-surface">
          <div class="detail-head">
            <div>
              <p class="page-eyebrow">{{ candidate.jobTitle || '未绑定岗位' }}</p>
              <h2>{{ candidate.fullName }}</h2>
            </div>
            <div class="score-card">
              <span>LLM简历评分</span>
              <strong>{{ candidate.resumeLlmScore ?? resumeLlmStatusLabel(candidate.resumeLlmStatus) }}</strong>
            </div>
          </div>

          <div class="detail-grid">
            <div><span>候选人编号</span><strong>{{ candidate.id }}</strong></div>
            <div><span>应聘岗位</span><strong>{{ candidate.jobTitle || '-' }}</strong></div>
            <div><span>联系电话</span><strong>{{ candidate.mobilePhone || '-' }}</strong></div>
            <div><span>邮箱</span><strong>{{ candidate.email || '-' }}</strong></div>
            <div><span>身份证号</span><strong>{{ candidate.idCardNo || '-' }}</strong></div>
            <div><span>专业</span><strong>{{ candidate.major || '-' }}</strong></div>
            <div><span>学历</span><strong>{{ candidate.educationLevel || '-' }}</strong></div>
            <div><span>毕业院校</span><strong>{{ candidate.graduationSchool || '-' }}</strong></div>
            <div><span>工作年限</span><strong>{{ candidate.yearsOfExperience ?? '-' }}</strong></div>
            <div><span>期望薪资</span><strong>{{ candidate.expectedSalary || '-' }}</strong></div>
            <div><span>投递状态</span><strong>{{ candidate.applicationStatus || '-' }}</strong></div>
            <div><span>面试阶段</span><strong>{{ candidate.interviewStageStatus || '-' }}</strong></div>
            <div><span>面试者用户ID</span><strong>{{ candidate.intervieweeUserId || '-' }}</strong></div>
            <div><span>流程流水号</span><strong>{{ candidate.interviewProcessId || '-' }}</strong></div>
            <div><span>评分状态</span><strong>{{ resumeLlmStatusLabel(candidate.resumeLlmStatus) }}</strong></div>
            <div><span>评分时间</span><strong>{{ candidate.resumeLlmEvaluatedAt || '-' }}</strong></div>
          </div>

          <div class="intro-box">
            <span>LLM简历评价</span>
            <p>{{ candidate.resumeLlmComment || '暂无评价' }}</p>
          </div>
          <div class="intro-box">
            <span>个人简介</span>
            <p>{{ candidate.selfIntroduction || '未填写' }}</p>
          </div>
          <div class="action-row">
            <a v-if="candidate.resumeFileId" class="link-chip" :href="resumeUrl(candidate.resumeFileId)" target="_blank">打开简历文件</a>
            <el-button :disabled="!canReevaluateResumeLlm" :loading="reevaluating" @click="reevaluateResumeLlm">{{ resumeLlmReevaluateLabel }}</el-button>
            <RouterLink v-if="candidate.interviewProcessId" class="link-chip" :to="`/interview/hr/processes/${candidate.interviewProcessId}`">查看面试流程</RouterLink>
          </div>
        </section>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { recruitmentApi } from '../services/api'

const route = useRoute()
const loading = ref(false)
const candidate = ref(null)
const reevaluating = ref(false)
const canReevaluateResumeLlm = computed(() => candidate.value?.resumeLlmStatus !== 'PENDING')
const resumeLlmReevaluateLabel = computed(() => canReevaluateResumeLlm.value ? 'AI简历重评' : '评分中不可重评')

function resumeUrl(id) { return recruitmentApi.getResumeUrl(id) }
function resumeLlmStatusLabel(status) { return ({ PENDING: '评分中', COMPLETED: '已完成', FAILED: '评分失败' })[status] || '-' }

async function loadCandidate() {
  loading.value = true
  try {
    candidate.value = (await recruitmentApi.getCandidate(route.params.id)).data
  } catch (error) {
    ElMessage.error(error.message || '候选人加载失败')
    candidate.value = null
  } finally {
    loading.value = false
  }
}

async function reevaluateResumeLlm() {
  reevaluating.value = true
  try {
    candidate.value = (await recruitmentApi.reevaluateResumeLlm(candidate.value.id)).data
    ElMessage.success('已提交AI简历重评')
  } catch (error) {
    ElMessage.error(error.message || 'AI简历重评失败')
  } finally {
    reevaluating.value = false
  }
}

onMounted(loadCandidate)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 20px; }
.detail-surface { padding: 18px; }
.detail-head { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; margin-bottom: 18px; }
.detail-head h2 { margin: 6px 0 0; font-size: 32px; }
.score-card { min-width: 150px; border-radius: 18px; padding: 16px; background: #102532; color: #f8f5ef; }
.score-card span { display: block; opacity: 0.76; margin-bottom: 6px; }
.score-card strong { font-size: 34px; }
.detail-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.detail-grid div, .intro-box { background: rgba(255,255,255,0.82); border-radius: 14px; padding: 12px; }
.detail-grid span, .intro-box span { display: block; color: #6d7a83; margin-bottom: 6px; }
.intro-box { margin: 12px 0; }
.intro-box p { margin: 0; line-height: 1.7; }
.action-row { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 12px; }
@media (max-width: 980px) { .detail-head { display: block; } .score-card { margin-top: 12px; } .detail-grid { grid-template-columns: 1fr; } }
</style>
