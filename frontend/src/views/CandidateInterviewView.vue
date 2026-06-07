<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Interviewee</p>
      <h1 class="page-title">面试者面试系统</h1>
      <p class="page-subtitle">当前流程包括 AI 面试、视频面试、线下面试。系统会根据当前阶段动态展示操作。</p>
      <div class="page-grid">
        <div class="surface">
          <h3>流程绑定</h3>
          <el-form :model="sessionForm" label-position="top">
            <el-form-item label="面试流程ID"><el-input-number v-model="sessionForm.processId" :min="1" /></el-form-item>
          </el-form>
          <div class="link-row">
            <el-button type="primary" @click="loadProcessRecords">加载流程</el-button>
            <el-button @click="joinVideo">加入视频面</el-button>
          </div>
          <div v-if="processSummary" class="summary-box">
            <p>当前阶段：{{ processSummary.currentStage }}</p>
            <p>状态：{{ processSummary.processStatusView }}</p>
            <p>AI均分：{{ processSummary.aiAverageScore ?? '-' }}</p>
          </div>
        </div>

        <div class="surface">
          <h3>AI 面试</h3>
          <div v-if="aiRecords.length === 0" class="empty-box">输入流程ID后加载问答记录并继续作答</div>
          <div v-for="item in aiRecords" :key="item.id" class="question-card">
            <strong>Q{{ item.sequenceNo }} {{ item.knowledgePoint }}</strong>
            <p>{{ item.questionContent }}</p>
            <small>单题均分：{{ item.averageScore }}</small>
            <p>你的回答：{{ item.answerContent }}</p>
          </div>
          <el-input v-model="aiAnswer.answerContent" type="textarea" :rows="4" placeholder="继续回答 AI 面试问题" />
          <div class="link-row"><el-button type="primary" @click="submitAiAnswer">提交 AI 回答</el-button></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { interviewApi } from '../services/api'

const sessionForm = reactive({ processId: null })
const aiAnswer = reactive({ answerContent: '' })
const aiRecords = ref([])
const processSummary = ref(null)

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadProcessRecords() {
  try {
    const processList = (await interviewApi.listProcesses()).data
    processSummary.value = processList.find((item) => item.id === sessionForm.processId) || null
    aiRecords.value = (await interviewApi.listAiRecords({ processId: sessionForm.processId })).data
  } catch (error) { fail(error) }
}
async function submitAiAnswer() {
  try {
    await interviewApi.submitAiAnswer({ processId: sessionForm.processId, answerContent: aiAnswer.answerContent })
    aiAnswer.answerContent = ''
    ElMessage.success('AI 回答已提交')
    await loadProcessRecords()
  } catch (error) { fail(error) }
}
async function joinVideo() {
  try {
    await interviewApi.intervieweeJoin(sessionForm.processId)
    ElMessage.success('已记录加入视频面时间')
  } catch (error) { fail(error) }
}
</script>

<style scoped>
.summary-box { margin-top: 16px; padding: 14px; border-radius: 16px; background: rgba(255,255,255,0.82); }
.summary-box p { margin: 6px 0; }
.empty-box { padding: 18px; border-radius: 16px; background: rgba(255,255,255,0.75); color: #6d7a83; }
.question-card { display: grid; gap: 8px; padding: 16px; border-radius: 18px; background: rgba(255,255,255,0.82); margin-bottom: 14px; }
.question-card p { margin: 0; line-height: 1.7; }
.question-card small { color: #6d7a83; }
</style>
