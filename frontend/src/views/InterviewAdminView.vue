<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interview Admin</p>
          <h1 class="page-title">面试系统管理</h1>
          <p class="page-subtitle">维护面试批次、题库、候选人分配和评分结果。</p>
        </div>
        <RouterLink class="link-chip" to="/">返回 HR 管理台</RouterLink>
      </div>

      <div class="sub-tabs">
        <button :class="{ active: activeTab === 'batches' }" @click="activeTab = 'batches'">面试批次</button>
        <button :class="{ active: activeTab === 'questions' }" @click="activeTab = 'questions'">题库管理</button>
        <button :class="{ active: activeTab === 'candidates' }" @click="activeTab = 'candidates'">候选人进度</button>
        <button :class="{ active: activeTab === 'scores' }" @click="activeTab = 'scores'">评分管理</button>
      </div>

      <section v-if="activeTab === 'batches'" class="surface">
        <h3>面试批次</h3>
        <el-form :model="batchForm" label-position="top" class="form-grid">
          <el-form-item label="批次名称"><el-input v-model="batchForm.batchName" /></el-form-item>
          <el-form-item label="批次编码"><el-input v-model="batchForm.batchCode" /></el-form-item>
          <el-form-item label="关联岗位"><el-select v-model="batchForm.jobId" clearable><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="batchForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
          <el-form-item label="说明" class="wide"><el-input v-model="batchForm.description" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <div class="link-row"><el-button type="primary" @click="saveBatch">保存批次</el-button><el-button @click="resetBatch">清空</el-button></div>
        <el-table :data="batches" stripe class="data-table" @row-click="editBatch">
          <el-table-column prop="batchName" label="批次" />
          <el-table-column prop="batchCode" label="编码" />
          <el-table-column prop="status" label="状态"><template #default="scope">{{ scope.row.status === 1 ? '启用' : '停用' }}</template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'questions'" class="surface">
        <h3>题库管理</h3>
        <el-form :model="questionForm" label-position="top" class="form-grid">
          <el-form-item label="题目标题"><el-input v-model="questionForm.questionTitle" /></el-form-item>
          <el-form-item label="题型"><el-input v-model="questionForm.questionType" /></el-form-item>
          <el-form-item label="难度"><el-input v-model="questionForm.difficulty" /></el-form-item>
          <el-form-item label="分值"><el-input-number v-model="questionForm.score" :min="1" /></el-form-item>
          <el-form-item label="标签" class="wide"><el-input v-model="questionForm.tags" /></el-form-item>
          <el-form-item label="题目内容" class="wide"><el-input v-model="questionForm.content" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="参考答案" class="wide"><el-input v-model="questionForm.referenceAnswer" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <div class="link-row"><el-button type="primary" @click="saveQuestion">保存题目</el-button><el-button @click="resetQuestion">清空</el-button></div>
        <el-table :data="questions" stripe class="data-table" @row-click="editQuestion">
          <el-table-column prop="questionTitle" label="标题" />
          <el-table-column prop="difficulty" label="难度" />
          <el-table-column prop="score" label="分值" />
          <el-table-column prop="tags" label="标签" />
        </el-table>
      </section>

      <section v-if="activeTab === 'candidates'" class="surface">
        <h3>候选人分配</h3>
        <el-form :model="assignForm" label-position="top" class="form-grid">
          <el-form-item label="面试批次"><el-select v-model="assignForm.batchId"><el-option v-for="batch in batches" :key="batch.id" :label="batch.batchName" :value="batch.id" /></el-select></el-form-item>
          <el-form-item label="招聘候选人"><el-select v-model="assignForm.recruitmentCandidateId"><el-option v-for="item in recruitmentCandidates" :key="item.id" :label="`${item.fullName} / ${item.mobilePhone}`" :value="item.id" /></el-select></el-form-item>
        </el-form>
        <div class="link-row"><el-button type="primary" @click="assignCandidate">分配候选人</el-button></div>
        <el-table :data="interviewCandidates" stripe class="data-table" @row-click="selectInterviewCandidate">
          <el-table-column prop="candidateName" label="候选人" />
          <el-table-column prop="mobilePhone" label="电话" />
          <el-table-column prop="interviewStatus" label="状态" />
          <el-table-column prop="totalScore" label="总分" />
        </el-table>
      </section>

      <section v-if="activeTab === 'scores'" class="surface">
        <h3>评分管理</h3>
        <el-table :data="submissions" stripe class="data-table" @row-click="editSubmissionScore">
          <el-table-column prop="interviewCandidateId" label="候选人编号" />
          <el-table-column prop="questionId" label="题目编号" />
          <el-table-column prop="answerContent" label="答题内容" min-width="220" />
          <el-table-column prop="score" label="得分" />
        </el-table>
        <el-form :model="scoreForm" label-position="top" class="form-grid score-form">
          <el-form-item label="答题记录 ID"><el-input v-model="scoreForm.submissionId" disabled /></el-form-item>
          <el-form-item label="得分"><el-input-number v-model="scoreForm.score" :min="0" /></el-form-item>
          <el-form-item label="评语" class="wide"><el-input v-model="scoreForm.reviewerComment" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="scoreSubmission">保存评分</el-button>
      </section>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { interviewApi, recruitmentApi } from '../services/api'

const activeTab = ref('batches')
const batches = ref([])
const questions = ref([])
const jobs = ref([])
const recruitmentCandidates = ref([])
const interviewCandidates = ref([])
const submissions = ref([])

const batchForm = reactive({ id: null, batchName: '', batchCode: '', jobId: null, description: '', status: 1 })
const questionForm = reactive({ id: null, questionTitle: '', questionType: 'TEXT', difficulty: '中等', tags: '', content: '', referenceAnswer: '', score: 10, status: 1 })
const assignForm = reactive({ batchId: null, recruitmentCandidateId: null })
const scoreForm = reactive({ submissionId: null, score: 0, reviewerComment: '' })

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadAll() {
  try {
    batches.value = (await interviewApi.listBatches()).data
    questions.value = (await interviewApi.listQuestions()).data
    jobs.value = (await recruitmentApi.listAdminJobs()).data
    recruitmentCandidates.value = (await recruitmentApi.listCandidates()).data
    interviewCandidates.value = (await interviewApi.listInterviewCandidates()).data
    submissions.value = (await interviewApi.listSubmissions()).data
  } catch (error) { fail(error) }
}
function resetBatch() { Object.assign(batchForm, { id: null, batchName: '', batchCode: '', jobId: null, description: '', status: 1 }) }
function editBatch(row) { Object.assign(batchForm, row) }
async function saveBatch() { try { await interviewApi.saveBatch({ ...batchForm }); ElMessage.success('批次已保存'); resetBatch(); await loadAll() } catch (error) { fail(error) } }
function resetQuestion() { Object.assign(questionForm, { id: null, questionTitle: '', questionType: 'TEXT', difficulty: '中等', tags: '', content: '', referenceAnswer: '', score: 10, status: 1 }) }
function editQuestion(row) { Object.assign(questionForm, row) }
async function saveQuestion() { try { await interviewApi.saveQuestion({ ...questionForm }); ElMessage.success('题目已保存'); resetQuestion(); await loadAll() } catch (error) { fail(error) } }
async function assignCandidate() { try { await interviewApi.assignCandidate({ ...assignForm }); ElMessage.success('候选人已分配'); await loadAll() } catch (error) { fail(error) } }
async function selectInterviewCandidate(row) { assignForm.batchId = row.batchId; submissions.value = (await interviewApi.listSubmissions({ interviewCandidateId: row.id })).data }
function editSubmissionScore(row) { Object.assign(scoreForm, { submissionId: row.id, score: row.score || 0, reviewerComment: row.reviewerComment || '' }) }
async function scoreSubmission() { try { await interviewApi.scoreSubmission(scoreForm.submissionId, { score: scoreForm.score, reviewerComment: scoreForm.reviewerComment }); ElMessage.success('评分已保存'); await loadAll() } catch (error) { fail(error) } }

onMounted(loadAll)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.sub-tabs { display: flex; gap: 10px; flex-wrap: wrap; margin: 22px 0; }
.sub-tabs button { border: 1px solid rgba(16, 37, 50, 0.12); background: #f8f5ef; color: #102532; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
.sub-tabs button.active { background: #102532; color: #f4efe7; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.wide { grid-column: 1 / -1; }
.data-table, .score-form { margin-top: 18px; }
@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } }
</style>
