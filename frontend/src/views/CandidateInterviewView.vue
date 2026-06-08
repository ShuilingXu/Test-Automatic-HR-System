<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Interviewee</p>
      <h1 class="page-title">面试者面试系统</h1>
      <div class="page-grid">
        <div class="surface">
          <h3>流程入口</h3>
          <div class="summary-box">
            <p>绑定状态：{{ sessionForm.processId ? '已绑定面试流程' : '未选择报名记录' }}</p>
          </div>
          <div class="link-row">
            <el-button type="primary" @click="loadProcessRecords">加载流程</el-button>
            <RouterLink class="link-chip" to="/user">返回我的报名</RouterLink>
            <el-button type="primary" @click="enterAiExamMode">进入AI答题全屏</el-button>
            <el-button @click="joinVideo">加入视频面</el-button>
            <el-button @click="stopRecording">结束并上传录制</el-button>
          </div>
          <div v-if="processSummary" class="summary-box">
            <p>当前阶段：{{ processSummary.currentStage }}</p>
            <p>状态：{{ processSummary.processStatusView }}</p>
            <p>AI均分：{{ processSummary.aiAverageScore ?? '-' }}</p>
            <p>AI最少轮数：{{ processSummary.aiMinQuestionRounds || '-' }}</p>
            <p>AI最多轮数：{{ processSummary.aiMaxQuestionRounds || '-' }}</p>
            <p>反作弊：{{ antiCheat.fullscreen ? '全屏中' : '未全屏' }} / 切屏 {{ antiCheat.switchCount }} / {{ processSummary.antiCheatSwitchLimit || 5 }} 次</p>
            <p v-if="refreshState.retryCount > 0">自动重试：第 {{ refreshState.retryCount }} 次，{{ refreshState.lastError }}</p>
          </div>
          <div class="video-grid">
            <div class="video-box"><span>本地视频</span><video ref="localVideo" autoplay muted playsinline></video></div>
            <div class="video-box"><span>远端视频</span><video ref="remoteVideo" autoplay playsinline></video></div>
          </div>
        </div>

        <div class="surface">
          <h3>AI 面试</h3>
          <div v-if="currentQuestion" class="question-card highlighted-question">
            <strong>当前问题 {{ currentQuestion.sequenceNo }}</strong>
            <p>{{ currentQuestion.questionContent }}</p>
            <small>知识域：{{ currentQuestion.knowledgePoint }}</small>
          </div>
          <div v-else-if="processSummary?.currentStage === 'AI'" class="empty-box">题目生成中</div>
          <div v-if="aiRecords.length === 0" class="empty-box">暂无题目</div>
          <div v-for="item in aiRecords" :key="item.id" class="question-card">
            <strong>Q{{ item.sequenceNo }} {{ item.knowledgePoint }}</strong>
            <p>{{ item.questionContent }}</p>
            <small>单题均分：{{ item.averageScore ?? '-' }}</small>
            <p>你的回答：{{ item.answerContent || '待回答' }}</p>
            <p v-if="item.interviewerComment">面试官反馈：{{ item.interviewerComment }}</p>
          </div>
          <el-input v-model="aiAnswer.answerContent" type="textarea" :rows="4" placeholder="回答当前 AI 问题" />
          <div class="link-row"><el-button type="primary" @click="submitAiAnswer">提交 AI 回答</el-button></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { interviewApi } from '../services/api'
import { buildMediaErrorMessage, requestCameraAndMicrophone } from '../utils/media'

const route = useRoute()
const router = useRouter()
const sessionForm = reactive({ processId: route.query.processId ? Number(route.query.processId) : null })
const aiAnswer = reactive({ answerContent: '' })
const aiRecords = ref([])
const processSummary = ref(null)
const currentQuestion = ref(null)
const refreshState = reactive({ loading: false, retryCount: 0, lastError: '' })
const antiCheat = reactive({ fullscreen: false, switchCount: 0, hasEnteredFullscreen: false, aiEndNotified: false })
const localVideo = ref(null)
const remoteVideo = ref(null)
let localStream = null
let peer = null
let pollTimer = null
let aiRefreshTimer = null
let recorder = null
let recordedChunks = []
let addedHrIce = new Set()

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadProcessRecords(options = {}) {
  if (refreshState.loading) {
    return
  }
  refreshState.loading = true
  try {
    if (!sessionForm.processId) {
      ElMessage.warning('请从面试者首页的报名记录进入面试')
      router.push('/user')
      return
    }
    const [processResponse, questionResponse, recordsResponse] = await Promise.all([
      interviewApi.getIntervieweeProcess(sessionForm.processId),
      interviewApi.getNextAiQuestion(sessionForm.processId),
      interviewApi.listIntervieweeAiRecords({ processId: sessionForm.processId }),
    ])
    processSummary.value = processResponse.data
    antiCheat.switchCount = processSummary.value?.antiCheatSwitchCount || 0
    currentQuestion.value = questionResponse.data
    aiRecords.value = recordsResponse.data
    refreshState.retryCount = 0
    refreshState.lastError = ''
    cacheInterviewSession()
    notifyAiFinishedIfNeeded()
    syncAiAutoRefresh()
  } catch (error) {
    refreshState.retryCount += 1
    refreshState.lastError = error.message || '刷新失败'
    if (!options.silent) {
      fail(error)
    }
    scheduleAiRefresh(nextRefreshDelay())
  } finally {
    refreshState.loading = false
  }
}

function cacheInterviewSession() {
}

function syncAiAutoRefresh() {
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && !currentQuestion.value) {
    scheduleAiRefresh(3000)
  } else {
    clearAiRefresh()
  }
}

function scheduleAiRefresh(delay) {
  clearAiRefresh()
  aiRefreshTimer = setTimeout(() => loadProcessRecords({ silent: true }), delay)
}

function clearAiRefresh() {
  clearTimeout(aiRefreshTimer)
  aiRefreshTimer = null
}

function nextRefreshDelay() {
  return Math.min(3000 + refreshState.retryCount * 1000, 10000)
}
async function submitAiAnswer() {
  try {
    if (!antiCheat.fullscreen) {
      ElMessage.warning('请先进入全屏答题模式')
      await enterAiExamMode()
      return
    }
    clearAiRefresh()
    await interviewApi.submitAiAnswer({ processId: sessionForm.processId, answerContent: aiAnswer.answerContent })
    aiAnswer.answerContent = ''
    ElMessage.success('AI 回答已提交')
    await loadProcessRecords()
  } catch (error) {
    fail(error)
    scheduleAiRefresh(nextRefreshDelay())
  }
}

async function enterAiExamMode() {
  if (document.fullscreenElement !== document.documentElement) {
    try {
      await document.documentElement.requestFullscreen()
      antiCheat.hasEnteredFullscreen = true
    } catch (error) {
      await reportAntiCheat('FULLSCREEN_DENIED', error.message || '全屏授权失败')
      ElMessage.warning('浏览器未允许全屏，请允许后继续答题')
    }
  } else {
    antiCheat.hasEnteredFullscreen = true
  }
}

async function reportAntiCheat(eventType, detail) {
  if (!sessionForm.processId) return null
  try {
    const response = await interviewApi.reportAntiCheatEvent({ processId: sessionForm.processId, eventType, detail })
    if (response.data) {
      processSummary.value = response.data
      antiCheat.switchCount = response.data.antiCheatSwitchCount || antiCheat.switchCount
      notifyAiFinishedIfNeeded()
      if (response.data.stageStatus === 'WAITING_APPROVAL') {
        currentQuestion.value = null
        clearAiRefresh()
      }
    }
    return response.data
  } catch { return null }
}

function handleFullscreenChange() {
  antiCheat.fullscreen = document.fullscreenElement === document.documentElement
  if (antiCheat.fullscreen) {
    antiCheat.hasEnteredFullscreen = true
    return
  }
  if (shouldReportSwitch()) {
    reportAntiCheat('FULLSCREEN_EXIT', `退出全屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function handleVisibilityChange() {
  if (document.hidden && shouldReportSwitch()) {
    reportAntiCheat('TAB_HIDDEN', `页面隐藏/切屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function handleWindowBlur() {
  if (shouldReportSwitch()) {
    reportAntiCheat('WINDOW_BLUR', `窗口失焦/切屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function shouldReportSwitch() {
  return antiCheat.hasEnteredFullscreen && processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && processSummary.value?.overallStatus === 'IN_PROGRESS'
}

function notifyAiFinishedIfNeeded() {
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'WAITING_APPROVAL' && !antiCheat.aiEndNotified) {
    antiCheat.aiEndNotified = true
    currentQuestion.value = null
    clearAiRefresh()
    ElMessageBox.alert(processSummary.value.processStatusView || 'AI面试已结束，请等待HR人工审批。', '面试结束', { confirmButtonText: '知道了' })
  }
}
async function joinVideo() {
  try {
    await interviewApi.intervieweeJoin(sessionForm.processId)
    localStream = await requestCameraAndMicrophone()
    localVideo.value.srcObject = localStream
    peer = new RTCPeerConnection()
    addedHrIce = new Set()
    localStream.getTracks().forEach((track) => peer.addTrack(track, localStream))
    peer.ontrack = (event) => { remoteVideo.value.srcObject = event.streams[0] }
    peer.onicecandidate = async (event) => {
      if (event.candidate) {
        await interviewApi.addIntervieweeIce(sessionForm.processId, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    recorder = new MediaRecorder(localStream)
    recordedChunks = []
    recorder.ondataavailable = (event) => { if (event.data.size > 0) recordedChunks.push(event.data) }
    recorder.start(1000)
    pollTimer = setInterval(async () => {
      const state = (await interviewApi.getVideoState(sessionForm.processId)).data
      if (state.offerSdp && !peer.currentRemoteDescription) {
        await peer.setRemoteDescription(JSON.parse(state.offerSdp))
        const answer = await peer.createAnswer()
        await peer.setLocalDescription(answer)
        await interviewApi.submitVideoAnswer(sessionForm.processId, { answerSdp: JSON.stringify(answer) })
      }
      if (state.hrIceCandidates) {
        const candidates = state.hrIceCandidates.split('\n').filter(Boolean)
        for (const item of candidates) {
          if (!addedHrIce.has(item)) {
            addedHrIce.add(item)
            try { await peer.addIceCandidate(JSON.parse(item)) } catch {}
          }
        }
      }
    }, 2000)
    ElMessage.success('已加入视频面并开始录制')
  } catch (error) { ElMessage.error(buildMediaErrorMessage(error)) }
}
async function stopRecording() {
  try {
    if (recorder && recorder.state !== 'inactive') {
      await new Promise((resolve) => { recorder.onstop = resolve; recorder.stop() })
      const blob = new Blob(recordedChunks, { type: 'video/webm' })
      const file = new File([blob], `interviewee-${sessionForm.processId}.webm`, { type: 'video/webm' })
      await interviewApi.uploadVideoRecording(sessionForm.processId, file)
      ElMessage.success('录制文件已上传')
    }
    disconnectVideo()
  } catch (error) { fail(error) }
}

function disconnectVideo() {
  clearInterval(pollTimer)
  pollTimer = null
  peer?.getSenders?.().forEach((sender) => sender.track?.stop())
  peer?.close()
  peer = null
  localStream?.getTracks().forEach((track) => track.stop())
  localStream = null
  if (localVideo.value) localVideo.value.srcObject = null
  if (remoteVideo.value) remoteVideo.value.srcObject = null
}

onBeforeUnmount(() => {
  clearAiRefresh()
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('blur', handleWindowBlur)
  disconnectVideo()
})

onMounted(async () => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('blur', handleWindowBlur)
  if (!sessionForm.processId) {
    ElMessage.warning('请从面试者首页选择报名记录进入面试')
    router.push('/user')
    return
  }
  await loadProcessRecords()
  syncAiAutoRefresh()
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.overallStatus === 'IN_PROGRESS') {
    ElMessage.info('AI面试需要全屏答题，切屏操作会被记录')
  }
})
</script>

<style scoped>
.summary-box { margin-top: 16px; padding: 14px; border-radius: 16px; background: rgba(255,255,255,0.82); }
.summary-box p { margin: 6px 0; }
.empty-box { padding: 18px; border-radius: 16px; background: rgba(255,255,255,0.75); color: #6d7a83; }
.question-card { display: grid; gap: 8px; padding: 16px; border-radius: 18px; background: rgba(255,255,255,0.82); margin-bottom: 14px; }
.question-card p { margin: 0; line-height: 1.7; }
.question-card small { color: #6d7a83; }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; margin-top: 18px; }
.video-box { background: rgba(255,255,255,0.82); padding: 12px; border-radius: 16px; }
.video-box span { display: block; margin-bottom: 8px; color: #6d7a83; }
.video-box video { width: 100%; min-height: 220px; background: #111; border-radius: 12px; }
@media (max-width: 900px) { .video-grid { grid-template-columns: 1fr; } }
</style>
