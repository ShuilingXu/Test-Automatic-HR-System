<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Online Interview</p>
      <h1 class="page-title">线上面试</h1>
      <p class="page-subtitle">选择已分配的面试候选人，加载题目并提交答题内容。</p>
      <div class="page-grid">
        <div class="surface interview-stage">
          <div class="camera-box">视频预览区</div>
          <el-form :model="sessionForm" label-position="top">
            <el-form-item label="面试候选人">
              <el-select v-model="sessionForm.interviewCandidateId" filterable placeholder="选择候选人">
                <el-option v-for="item in interviewCandidates" :key="item.id" :label="`${item.candidateName} / ${item.mobilePhone}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-form>
          <div class="link-row">
            <el-button type="primary" @click="loadQuestions">开始面试</el-button>
            <el-button @click="deviceChecked = true">设备检测</el-button>
          </div>
          <p v-if="deviceChecked" class="ok-text">设备检测已完成</p>
        </div>

        <div class="surface">
          <h3>题目与答题</h3>
          <div v-if="questions.length === 0" class="empty-box">请选择候选人并开始面试</div>
          <div v-for="question in questions" :key="question.id" class="question-card">
            <strong>{{ question.questionTitle }}</strong>
            <p>{{ question.content }}</p>
            <small>{{ question.difficulty || '未设难度' }} / {{ question.score }} 分</small>
            <el-input v-model="answers[question.id]" type="textarea" :rows="4" placeholder="填写答题内容" />
            <el-button type="primary" @click="submitAnswer(question.id)">提交本题</el-button>
          </div>
          <RouterLink class="link-chip" to="/candidate/register">返回报名页</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { interviewApi } from '../services/api'

const interviewCandidates = ref([])
const questions = ref([])
const answers = reactive({})
const deviceChecked = ref(false)
const sessionForm = reactive({ interviewCandidateId: null })

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadCandidates() { try { interviewCandidates.value = (await interviewApi.listInterviewCandidates()).data } catch (error) { fail(error) } }
async function loadQuestions() {
  if (!sessionForm.interviewCandidateId) { ElMessage.warning('请选择面试候选人'); return }
  try { questions.value = (await interviewApi.listCandidateQuestions(sessionForm.interviewCandidateId)).data } catch (error) { fail(error) }
}
async function submitAnswer(questionId) {
  try {
    await interviewApi.submitAnswer({ interviewCandidateId: sessionForm.interviewCandidateId, questionId, answerContent: answers[questionId] || '' })
    ElMessage.success('答题已提交')
  } catch (error) { fail(error) }
}

onMounted(loadCandidates)
</script>

<style scoped>
.camera-box { height: 320px; border-radius: 22px; background: #102532; color: #f4efe7; display: grid; place-items: center; font-size: 22px; margin-bottom: 16px; }
.ok-text { color: #177245; font-weight: 700; }
.empty-box { padding: 18px; border-radius: 16px; background: rgba(255,255,255,0.75); color: #6d7a83; }
.question-card { display: grid; gap: 10px; padding: 16px; border-radius: 18px; background: rgba(255,255,255,0.82); margin-bottom: 14px; }
.question-card p { margin: 0; line-height: 1.7; }
.question-card small { color: #6d7a83; }
</style>
