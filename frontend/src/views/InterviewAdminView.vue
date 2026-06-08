<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interview HR</p>
          <h1 class="page-title">面试系统 HR 入口</h1>
          <p class="page-subtitle">维护 AI 知识库、岗位权重、LLM 配置，并推进 AI 面、视频面、线下面流程。</p>
        </div>
        <RouterLink class="link-chip" to="/admin">返回管理后台</RouterLink>
      </div>

      <div class="sub-tabs">
        <button :class="{ active: activeTab === 'kb' }" @click="activeTab = 'kb'">知识库</button>
        <button :class="{ active: activeTab === 'weights' }" @click="activeTab = 'weights'">岗位权重</button>
        <button v-if="isItAdmin" :class="{ active: activeTab === 'llm' }" @click="activeTab = 'llm'">LLM配置</button>
        <button :class="{ active: activeTab === 'process' }" @click="activeTab = 'process'">面试流程</button>
      </div>

      <section v-if="activeTab === 'kb'" class="surface">
        <h3>知识库与知识点</h3>
        <div class="page-grid">
          <el-form :model="kbForm" label-position="top" class="surface inner-surface">
            <el-form-item label="知识库名称"><el-input v-model="kbForm.knowledgeBaseName" /></el-form-item>
            <el-form-item label="技术方向"><el-input v-model="kbForm.techCategory" /></el-form-item>
            <el-form-item label="岗位方向"><el-input v-model="kbForm.jobCategory" /></el-form-item>
            <el-button type="primary" @click="saveKnowledgeBase">保存知识库</el-button>
          </el-form>
          <el-form :model="itemForm" label-position="top" class="surface inner-surface">
            <el-form-item label="所属知识库"><el-select v-model="itemForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="知识点"><el-input v-model="itemForm.knowledgePoint" /></el-form-item>
            <el-form-item label="知识内容"><el-input v-model="itemForm.knowledgeContent" type="textarea" :rows="4" /></el-form-item>
            <el-button type="primary" @click="saveKnowledgeItem">保存知识点</el-button>
          </el-form>
        </div>
        <el-table :data="knowledgeBases" stripe class="data-table" @row-click="selectKnowledgeBase">
          <el-table-column prop="knowledgeBaseName" label="知识库" />
          <el-table-column prop="techCategory" label="技术方向" />
          <el-table-column prop="jobCategory" label="岗位方向" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteKnowledgeBase(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
        <el-table :data="knowledgeItems" stripe class="data-table">
          <el-table-column prop="knowledgePoint" label="知识点" />
          <el-table-column prop="knowledgeContent" label="知识内容" min-width="280" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteKnowledgeItem(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'weights'" class="surface">
        <h3>岗位知识权重</h3>
        <el-form :model="weightForm" label-position="top" class="form-grid">
          <el-form-item label="招聘岗位"><el-select v-model="weightForm.jobId"><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="知识库"><el-select v-model="weightForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="权重"><el-input-number v-model="weightForm.weight" :min="1" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveWeight">保存权重</el-button>
        <el-table :data="weights" stripe class="data-table">
          <el-table-column prop="jobId" label="岗位ID" />
          <el-table-column prop="knowledgeBaseId" label="知识库ID" />
          <el-table-column prop="weight" label="权重" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteWeight(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'llm'" class="surface">
        <h3>LLM 模型连接配置</h3>
        <el-form :model="llmForm" label-position="top" class="form-grid">
          <el-form-item label="配置名称"><el-input v-model="llmForm.configName" /></el-form-item>
          <el-form-item label="模型角色"><el-select v-model="llmForm.modelRole"><el-option label="面试官LLM A" value="INTERVIEWER" /><el-option label="评分LLM B" value="SCORER" /></el-select></el-form-item>
          <el-form-item label="OpenAI接口地址"><el-input v-model="llmForm.baseUrl" /></el-form-item>
          <el-form-item label="API Key"><el-input v-model="llmForm.apiKey" type="password" show-password /></el-form-item>
          <el-form-item label="API Key掩码"><el-input v-model="llmForm.apiKeyMasked" /></el-form-item>
          <el-form-item label="模型名称"><el-input v-model="llmForm.modelName" /></el-form-item>
          <el-form-item label="提示词模板" class="wide"><el-input v-model="llmForm.promptTemplate" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="系统级评分提示词" class="wide"><el-input v-model="llmForm.scoringRulePrompt" type="textarea" :rows="4" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveLlmConfig">保存配置</el-button>
        <el-table :data="llmConfigs" stripe class="data-table" @row-click="editLlmConfig">
          <el-table-column prop="configName" label="配置名称" />
          <el-table-column prop="modelRole" label="角色" />
          <el-table-column prop="baseUrl" label="接口地址" min-width="220" />
          <el-table-column prop="modelName" label="模型" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteLlmConfig(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'process'" class="surface">
        <h3>候选人面试流程</h3>
        <el-form :model="processForm" label-position="top" class="form-grid">
          <el-form-item label="招聘候选人"><el-select v-model="processForm.recruitmentCandidateId" @change="syncIntervieweeByCandidate"><el-option v-for="item in recruitmentCandidates" :key="item.id" :label="`${item.fullName} / ${item.mobilePhone}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="面试者用户ID"><el-input v-model="processForm.intervieweeUserId" disabled /></el-form-item>
          <el-form-item label="岗位"><el-select v-model="processForm.jobId"><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="AI通过阈值"><el-input-number v-model="processForm.aiThresholdScore" :min="1" :max="10" /></el-form-item>
        </el-form>
        <div class="link-row"><el-button type="primary" @click="startProcess">发起面试流程</el-button></div>
        <el-table :data="processes" stripe class="data-table" @row-click="selectProcess">
          <el-table-column prop="id" label="流程流水号" width="110" />
          <el-table-column prop="recruitmentCandidateId" label="候选人ID" />
          <el-table-column prop="intervieweeUserId" label="面试者用户ID" width="130" />
          <el-table-column prop="currentStage" label="当前轮次" />
          <el-table-column prop="processStatusView" label="状态展示" />
          <el-table-column prop="aiAverageScore" label="AI均分" />
          <el-table-column prop="overallStatus" label="总状态" />
        </el-table>
        <div v-if="selectedProcess" class="surface inner-surface detail-surface">
          <h3>流程审批</h3>
          <p class="serial-line">流程流水号：{{ selectedProcess.id }}</p>
          <div v-if="selectedProcess.videoJoinLink || selectedProcess.videoSerialNo" class="serial-line">
            <span v-if="selectedProcess.videoSerialNo">视频流水号：{{ selectedProcess.videoSerialNo }}</span>
            <a v-if="selectedProcess.videoJoinLink" :href="selectedProcess.videoJoinLink" target="_blank" class="video-link">打开面试链接</a>
            <a v-if="selectedProcess.recordingPath || selectedProcess.recordingFileName" :href="interviewApi.getRecordingUrl(selectedProcess.id)" target="_blank" class="video-link">查看录制文件</a>
          </div>
          <div class="video-grid">
            <div class="video-box"><span>HR本地视频</span><video ref="hrLocalVideo" autoplay muted playsinline></video></div>
            <div class="video-box"><span>面试者远端视频</span><video ref="hrRemoteVideo" autoplay playsinline></video></div>
          </div>
          <div class="link-row">
            <el-button v-if="canApproveAi" @click="approveAi(1)">AI通过并生成视频任务</el-button>
            <el-button v-if="canApproveAi" @click="approveAi(0)">AI不通过</el-button>
            <el-button v-if="canStartVideo" @click="startHrVideoCall">开始视频面</el-button>
            <el-button v-if="canStopVideo" @click="stopHrRecording">结束并上传录制</el-button>
            <el-button v-if="canApproveVideo" @click="approveVideo(1)">视频面通过进线下面</el-button>
            <el-button v-if="canApproveVideo" @click="approveVideo(0)">视频面不通过</el-button>
            <el-button v-if="canApproveOnsite" @click="approveOnsite(1)">线下面通过</el-button>
            <el-button v-if="canApproveOnsite" @click="approveOnsite(0)">线下面不通过</el-button>
            <el-button v-if="canTerminate" type="danger" @click="terminateProcess">终止流程</el-button>
          </div>
          <el-table :data="aiRecords" stripe class="data-table">
            <el-table-column prop="sequenceNo" label="题号" width="70" />
            <el-table-column prop="knowledgePoint" label="知识点" />
            <el-table-column prop="questionContent" label="提问" min-width="220" />
            <el-table-column prop="answerContent" label="回答" min-width="220" />
            <el-table-column prop="averageScore" label="均分" width="80" />
          </el-table>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, interviewApi, recruitmentApi } from '../services/api'

const sessionUser = ref(JSON.parse(localStorage.getItem('session-user') || 'null'))
const isItAdmin = computed(() => sessionUser.value?.roleCode === 'IT_ADMIN')
const activeTab = ref('kb')
const knowledgeBases = ref([])
const knowledgeItems = ref([])
const weights = ref([])
const llmConfigs = ref([])
const jobs = ref([])
const recruitmentCandidates = ref([])
const processes = ref([])
const aiRecords = ref([])
const selectedProcess = ref(null)
const videoActive = ref(false)

const hrLocalVideo = ref(null)
const hrRemoteVideo = ref(null)
let hrLocalStream = null
let hrPeer = null
let hrPollTimer = null
let hrRecorder = null
let hrRecordedChunks = []
let addedIntervieweeIce = new Set()

const kbForm = reactive({ id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 })
const itemForm = reactive({ id: null, knowledgeBaseId: null, knowledgePoint: '', knowledgeContent: '', status: 1 })
const weightForm = reactive({ id: null, jobId: null, knowledgeBaseId: null, weight: 1 })
const llmForm = reactive({ id: null, configName: '', modelRole: 'INTERVIEWER', baseUrl: '', apiKey: '', apiKeyMasked: '', modelName: '', promptTemplate: '', scoringRulePrompt: '', status: 1 })
const processForm = reactive({ recruitmentCandidateId: null, intervieweeUserId: '', jobId: null, aiThresholdScore: 7 })

const canTerminate = computed(() => selectedProcess.value?.overallStatus === 'IN_PROGRESS')
const canApproveAi = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'AI')
const canStartVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && selectedProcess.value?.videoJoinLink && !videoActive.value)
const canStopVideo = computed(() => videoActive.value)
const canApproveVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && ['WAITING_APPROVAL', 'RECORDED'].includes(selectedProcess.value?.sessionStatus))
const canApproveOnsite = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'ONSITE')

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadAll() {
  try {
    sessionUser.value = (await authApi.getSession()).data
    knowledgeBases.value = (await interviewApi.listKnowledgeBases()).data
    if (isItAdmin.value) {
      llmConfigs.value = (await interviewApi.listLlmConfigs()).data
    } else {
      llmConfigs.value = []
      if (activeTab.value === 'llm') activeTab.value = 'kb'
    }
    jobs.value = (await recruitmentApi.listAdminJobs()).data
    recruitmentCandidates.value = (await recruitmentApi.listCandidates()).data
    processes.value = (await interviewApi.listProcesses()).data
    if (selectedProcess.value) {
      selectedProcess.value = processes.value.find((item) => item.id === selectedProcess.value.id) || selectedProcess.value
    }
  } catch (error) { fail(error) }
}
async function selectKnowledgeBase(row) { itemForm.knowledgeBaseId = row.id; knowledgeItems.value = (await interviewApi.listKnowledgeItems({ knowledgeBaseId: row.id })).data }
async function saveKnowledgeBase() { try { await interviewApi.saveKnowledgeBase({ ...kbForm }); ElMessage.success('知识库已保存'); await loadAll() } catch (error) { fail(error) } }
async function deleteKnowledgeBase(id) { try { await interviewApi.deleteKnowledgeBase(id); ElMessage.success('知识库已删除'); await loadAll() } catch (error) { fail(error) } }
async function saveKnowledgeItem() { try { await interviewApi.saveKnowledgeItem({ ...itemForm }); ElMessage.success('知识点已保存'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
async function deleteKnowledgeItem(id) { try { await interviewApi.deleteKnowledgeItem(id); ElMessage.success('知识点已删除'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
async function saveWeight() { try { await interviewApi.saveJobKnowledgeWeight({ ...weightForm }); ElMessage.success('权重已保存'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { fail(error) } }
async function deleteWeight(id) { try { await interviewApi.deleteJobKnowledgeWeight(id); ElMessage.success('权重已删除'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { fail(error) } }
async function saveLlmConfig() { try { await interviewApi.saveLlmConfig({ ...llmForm }); ElMessage.success('LLM配置已保存'); await loadAll() } catch (error) { fail(error) } }
async function deleteLlmConfig(id) { try { await interviewApi.deleteLlmConfig(id); ElMessage.success('LLM配置已删除'); await loadAll() } catch (error) { fail(error) } }
function editLlmConfig(row) { Object.assign(llmForm, row) }
async function syncIntervieweeByCandidate(candidateId) { const candidate = recruitmentCandidates.value.find((item) => item.id === candidateId); processForm.intervieweeUserId = candidate?.intervieweeUserId ? String(candidate.intervieweeUserId) : '' }
async function startProcess() { try { if (!processForm.intervieweeUserId) { ElMessage.warning('未匹配到面试者账号'); return } const response = await interviewApi.startProcess({ ...processForm, intervieweeUserId: Number(processForm.intervieweeUserId) }); selectedProcess.value = response.data; ElMessage.success('面试流程已发起'); await loadAll() } catch (error) { fail(error) } }
async function selectProcess(row) { selectedProcess.value = row; aiRecords.value = (await interviewApi.listAiRecords({ processId: row.id })).data }
async function approveAi(approved) { try { await interviewApi.approveAi(selectedProcess.value.id, { approved }); ElMessage.success('AI审批完成'); await loadAll() } catch (error) { fail(error) } }
async function approveVideo(approved) { try { await interviewApi.approveVideo(selectedProcess.value.id, { approved }); ElMessage.success('视频面审批完成'); await loadAll() } catch (error) { fail(error) } }
async function approveOnsite(approved) { try { await interviewApi.approveOnsite(selectedProcess.value.id, { approved }); ElMessage.success('线下面审批完成'); await loadAll() } catch (error) { fail(error) } }
async function terminateProcess() { try { await interviewApi.terminateProcess(selectedProcess.value.id, { approved: 0 }); ElMessage.success('流程已终止'); await loadAll() } catch (error) { fail(error) } }

async function startHrVideoCall() {
  if (!selectedProcess.value) return
  try {
    hrLocalStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    hrLocalVideo.value.srcObject = hrLocalStream
    hrPeer = new RTCPeerConnection()
    addedIntervieweeIce = new Set()
    hrLocalStream.getTracks().forEach((track) => hrPeer.addTrack(track, hrLocalStream))
    hrPeer.ontrack = (event) => { hrRemoteVideo.value.srcObject = event.streams[0] }
    hrPeer.onicecandidate = async (event) => {
      if (event.candidate) {
        await interviewApi.addHrIce(selectedProcess.value.id, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    const offer = await hrPeer.createOffer()
    await hrPeer.setLocalDescription(offer)
    await interviewApi.publishVideoOffer(selectedProcess.value.id, { offerSdp: JSON.stringify(offer) })
    await interviewApi.hrJoin(selectedProcess.value.id)
    hrRecorder = new MediaRecorder(hrLocalStream)
    hrRecordedChunks = []
    hrRecorder.ondataavailable = (event) => { if (event.data.size > 0) hrRecordedChunks.push(event.data) }
    hrRecorder.start(1000)
    videoActive.value = true
    hrPollTimer = setInterval(async () => {
      const state = (await interviewApi.getHrVideoState(selectedProcess.value.id)).data
      if (state.answerSdp && !hrPeer.currentRemoteDescription) {
        await hrPeer.setRemoteDescription(JSON.parse(state.answerSdp))
      }
      if (state.intervieweeIceCandidates) {
        const candidates = state.intervieweeIceCandidates.split('\n').filter(Boolean)
        for (const item of candidates) {
          if (!addedIntervieweeIce.has(item)) {
            addedIntervieweeIce.add(item)
            try { await hrPeer.addIceCandidate(JSON.parse(item)) } catch {}
          }
        }
      }
    }, 2000)
    ElMessage.success('HR视频通话已开始')
  } catch (error) { fail(error) }
}

async function stopHrRecording() {
  try {
    if (hrRecorder && hrRecorder.state !== 'inactive') {
      await new Promise((resolve) => {
        hrRecorder.onstop = resolve
        hrRecorder.stop()
      })
      const blob = new Blob(hrRecordedChunks, { type: 'video/webm' })
      const file = new File([blob], `hr-${selectedProcess.value.id}.webm`, { type: 'video/webm' })
      await interviewApi.uploadHrVideoRecording(selectedProcess.value.id, file)
      await interviewApi.completeVideo(selectedProcess.value.id)
      ElMessage.success('录制已上传')
    }
    disconnectHrVideo()
    await loadAll()
  } catch (error) { fail(error) }
}

function disconnectHrVideo() {
  clearInterval(hrPollTimer)
  hrPollTimer = null
  hrPeer?.getSenders?.().forEach((sender) => sender.track?.stop())
  hrPeer?.close()
  hrPeer = null
  hrLocalStream?.getTracks().forEach((track) => track.stop())
  hrLocalStream = null
  if (hrLocalVideo.value) hrLocalVideo.value.srcObject = null
  if (hrRemoteVideo.value) hrRemoteVideo.value.srcObject = null
  videoActive.value = false
}

onBeforeUnmount(() => {
  disconnectHrVideo()
})

onMounted(loadAll)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.sub-tabs { display: flex; gap: 10px; flex-wrap: wrap; margin: 22px 0; }
.sub-tabs button { border: 1px solid rgba(16, 37, 50, 0.12); background: #f8f5ef; color: #102532; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
.sub-tabs button.active { background: #102532; color: #f4efe7; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.wide { grid-column: 1 / -1; }
.inner-surface { background: rgba(255,255,255,0.82); }
.detail-surface { margin-top: 18px; }
.serial-line { margin: 8px 0 14px; color: #42515b; }
.video-link { margin-left: 12px; color: #0f6c8f; font-weight: 700; text-decoration: none; }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; margin: 12px 0 18px; }
.video-box { background: rgba(255,255,255,0.82); padding: 12px; border-radius: 16px; }
.video-box span { display: block; margin-bottom: 8px; color: #6d7a83; }
.video-box video { width: 100%; min-height: 220px; background: #111; border-radius: 12px; }
.data-table { margin-top: 18px; }
@media (max-width: 900px) { .form-grid, .video-grid { grid-template-columns: 1fr; } }
</style>
