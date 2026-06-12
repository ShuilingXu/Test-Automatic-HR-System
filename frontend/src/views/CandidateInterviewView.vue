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
            <p v-if="processSummary.currentStage === 'AI'">AI强制录像：{{ aiExamRecordingStatusText }}</p>
            <p v-if="refreshState.retryCount > 0">自动重试：第 {{ refreshState.retryCount }} 次，{{ refreshState.lastError }}</p>
          </div>
          <div class="video-grid">
            <div class="video-box"><span>本地视频</span><video ref="localVideo" autoplay muted playsinline></video></div>
            <div class="video-box"><span>远端视频</span><video ref="remoteVideo" autoplay playsinline></video></div>
          </div>
        </div>

        <div class="surface ai-panel">
          <h3>AI 面试</h3>
          <div v-if="aiStatusText" class="ai-status-card" :class="{ busy: aiSubmitState.submitting }">
            <span class="status-dot"></span>
            <strong>{{ aiStatusText }}</strong>
            <small>{{ aiStatusHint }}</small>
          </div>
          <div v-if="aiSubmitState.submitting" class="ai-submit-overlay">
            <div class="ai-orbit"><span></span><span></span><span></span></div>
            <strong>{{ aiSubmitState.message }}</strong>
            <p>请勿重复点击或刷新页面，AI 正在评分并生成后续安排。</p>
          </div>
          <div v-if="processSummary?.currentStage === 'AI'" class="ai-recording-card" :class="{ active: aiExamRecording.active }">
            <div>
              <strong>{{ aiExamRecordingStatusText }}</strong>
              <p>AI答题期间必须开启摄像头和麦克风录像，未开始录像不能作答或提交。</p>
            </div>
            <video ref="aiExamVideo" autoplay muted playsinline></video>
          </div>
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
          <el-input v-model="aiAnswer.answerContent" type="textarea" :rows="4" placeholder="回答当前 AI 问题" :disabled="aiSubmitState.submitting || !currentQuestion || aiAnswerDisabled" @copy.prevent @cut.prevent @paste.prevent @drop.prevent />
          <div class="link-row"><el-button type="primary" :loading="aiSubmitState.submitting" :disabled="aiSubmitState.submitting || !currentQuestion || aiAnswerDisabled" @click="submitAiAnswer">{{ aiSubmitState.submitting ? 'AI处理中' : '提交 AI 回答' }}</el-button></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { interviewApi } from '../services/api'
import { attachRemoteTrack, buildMediaErrorMessage, createPeerConnection, defaultIceServers, isRelayIceCandidate, playVideo, requestCameraAndMicrophone } from '../utils/media'

const route = useRoute()
const router = useRouter()
const sessionForm = reactive({ processId: route.params.processId ? Number(route.params.processId) : route.query.processId ? Number(route.query.processId) : null })
const aiAnswer = reactive({ answerContent: '' })
const aiRecords = ref([])
const processSummary = ref(null)
const currentQuestion = ref(null)
const refreshState = reactive({ loading: false, retryCount: 0, lastError: '' })
const aiSubmitState = reactive({ submitting: false, message: '' })
const aiPendingRefresh = reactive({ active: false, attempts: 0, questionId: null })
const runtimeConfig = reactive({ disableDevtoolsShortcuts: true })
const antiCheat = reactive({ fullscreen: false, switchCount: 0, hasEnteredFullscreen: false, aiEndNotified: false })
const aiExamRecording = reactive({ active: false, starting: false, uploading: false, uploaded: false, error: '' })
const localVideo = ref(null)
const remoteVideo = ref(null)
const aiExamVideo = ref(null)
let localStream = null
let aiExamStream = null
let peer = null
let pollTimer = null
let aiRefreshTimer = null
let recorder = null
let recordedChunks = []
let addedHrIce = new Set()
let remoteStream = null
let pendingHrIce = []
let recordingStopInProgress = false
let handledRecordingEndSignal = ''
let recordingEndTimer = null
let aiExamRecorder = null
let aiExamRecordedChunks = []
let aiExamRecordingStopInProgress = false

const aiStatusText = computed(() => {
  if (aiSubmitState.submitting) return aiSubmitState.message || 'AI正在处理你的回答'
  if (aiPendingRefresh.active) return 'AI仍在后台处理中'
  if (processSummary.value?.currentStage !== 'AI') return ''
  if (processSummary.value?.stageStatus === 'WAITING_APPROVAL') return 'AI面试已完成，等待HR审批'
  if (processSummary.value?.stageStatus === 'REJECTED' || processSummary.value?.overallStatus === 'REJECTED') return 'AI面试已结束'
  if (refreshState.loading) return '正在同步面试状态'
  if (!currentQuestion.value) return '正在生成下一道题'
  return '请阅读当前问题并作答'
})

const aiStatusHint = computed(() => {
  if (aiSubmitState.submitting) return '评分、评价和下一题生成可能需要几十秒'
  if (aiPendingRefresh.active) return '请求已超时但不代表失败，系统会自动刷新最新面试状态'
  if (processSummary.value?.stageStatus === 'WAITING_APPROVAL') return '请保持关注流程状态，HR审批后会进入下一阶段'
  if (!currentQuestion.value && processSummary.value?.currentStage === 'AI') return '系统会自动刷新题目，请不要重复提交'
  return '提交后按钮会锁定，避免重复提交'
})

const aiAnswerDisabled = computed(() => isAiExamInProgress() && !aiExamRecording.active)

const aiExamRecordingStatusText = computed(() => {
  if (aiExamRecording.uploading) return '录像上传中'
  if (aiExamRecording.active) return '摄像头和麦克风录像中'
  if (aiExamRecording.starting) return '正在请求摄像头和麦克风权限'
  if (aiExamRecording.uploaded) return '录像已上传'
  if (aiExamRecording.error) return aiExamRecording.error
  return '未开始录像'
})

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

function isTimeoutError(error) {
  return error?.code === 'ECONNABORTED' || /timeout|exceeded/i.test(error?.message || '')
}

function nextRefreshDelay() {
  return Math.min(3000 + refreshState.retryCount * 1000, 10000)
}
async function submitAiAnswer() {
  if (aiSubmitState.submitting) {
    ElMessage.info('AI正在处理上一轮回答，请稍候')
    return
  }
  try {
    if (!currentQuestion.value) {
      ElMessage.warning('当前没有可提交的问题，请等待题目生成')
      return
    }
    if (!aiAnswer.answerContent.trim()) {
      ElMessage.warning('请先填写回答内容')
      return
    }
    if (!aiExamRecording.active || !antiCheat.fullscreen) {
      ElMessage.warning('请先开启摄像头/麦克风录像并进入全屏答题模式')
      await enterAiExamMode()
      return
    }
    aiSubmitState.submitting = true
    aiSubmitState.message = 'AI正在评分并生成下一步'
    aiPendingRefresh.questionId = currentQuestion.value.id
    clearAiRefresh()
    await interviewApi.submitAiAnswer({ processId: sessionForm.processId, answerContent: aiAnswer.answerContent })
    aiSubmitState.message = '正在同步最新面试状态'
    aiPendingRefresh.active = false
    aiPendingRefresh.attempts = 0
    aiPendingRefresh.questionId = null
    aiAnswer.answerContent = ''
    ElMessage.success('AI 回答已提交')
    await loadProcessRecords()
  } catch (error) {
    if (isTimeoutError(error)) {
      aiSubmitState.message = 'AI仍在后台处理，正在自动刷新状态'
      aiPendingRefresh.active = true
      aiPendingRefresh.attempts = 0
      ElMessage.info('AI处理时间较长，系统将自动刷新状态，请不要重复提交')
      schedulePendingAiRefresh()
      return
    }
    fail(error)
    scheduleAiRefresh(nextRefreshDelay())
  }
  finally {
    if (!aiPendingRefresh.active) {
      aiSubmitState.submitting = false
      aiSubmitState.message = ''
    }
  }
}

function schedulePendingAiRefresh() {
  clearAiRefresh()
  aiRefreshTimer = setTimeout(refreshPendingAiState, Math.min(3000 + aiPendingRefresh.attempts * 1000, 10000))
}

async function refreshPendingAiState() {
  aiPendingRefresh.attempts += 1
  aiSubmitState.message = `AI仍在后台处理，正在第 ${aiPendingRefresh.attempts} 次同步状态`
  try {
    await loadProcessRecords({ silent: true })
    if (!isPendingAiResolved()) {
      schedulePendingAiRefresh()
      return
    }
    aiPendingRefresh.active = false
    aiPendingRefresh.attempts = 0
    aiPendingRefresh.questionId = null
    aiSubmitState.submitting = false
    aiSubmitState.message = ''
    aiAnswer.answerContent = ''
    ElMessage.success('AI处理已完成，状态已更新')
  } catch {
    schedulePendingAiRefresh()
  }
}

function isPendingAiResolved() {
  if (processSummary.value?.currentStage !== 'AI' || processSummary.value?.stageStatus === 'WAITING_APPROVAL' || processSummary.value?.overallStatus !== 'IN_PROGRESS') {
    return true
  }
  const pendingRecord = aiRecords.value.find((item) => item.id === aiPendingRefresh.questionId)
  if (pendingRecord?.answerContent) {
    return true
  }
  return currentQuestion.value && currentQuestion.value.id !== aiPendingRefresh.questionId
}

async function enterAiExamMode() {
  if (isAiExamInProgress()) {
    await ensureAiExamRecordingStarted()
  }
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

async function ensureAiExamRecordingStarted() {
  if (aiExamRecording.active || aiExamRecording.starting) return
  if (!window.MediaRecorder) {
    aiExamRecording.error = '当前浏览器不支持录像，请使用最新版 Chrome/Edge'
    await reportAntiCheat('AI_RECORDING_UNSUPPORTED', aiExamRecording.error)
    throw new Error(aiExamRecording.error)
  }
  aiExamRecording.starting = true
  aiExamRecording.error = ''
  try {
    aiExamStream = await requestCameraAndMicrophone()
    if (aiExamVideo.value) {
      aiExamVideo.value.srcObject = aiExamStream
      playVideo(aiExamVideo.value)
    }
    aiExamRecorder = createMediaRecorder(aiExamStream)
    aiExamRecordedChunks = []
    aiExamRecorder.ondataavailable = (event) => { if (event.data.size > 0) aiExamRecordedChunks.push(event.data) }
    aiExamRecorder.onstop = () => { aiExamRecording.active = false }
    aiExamRecorder.start(1000)
    aiExamRecording.active = true
    aiExamRecording.uploaded = false
    await reportAntiCheat('AI_RECORDING_STARTED', 'AI答题摄像头和麦克风录像已开始')
    ElMessage.success('AI答题录像已开始')
  } catch (error) {
    aiExamRecording.error = buildMediaErrorMessage(error)
    await reportAntiCheat('AI_RECORDING_DENIED', aiExamRecording.error)
    ElMessage.error(aiExamRecording.error)
    throw error
  } finally {
    aiExamRecording.starting = false
  }
}

function createMediaRecorder(stream) {
  const mimeType = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type))
  return mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
}

async function stopAndUploadAiExamRecording() {
  if (aiExamRecordingStopInProgress) return
  if ((!aiExamRecorder || aiExamRecorder.state === 'inactive') && aiExamRecordedChunks.length === 0) return
  aiExamRecordingStopInProgress = true
  aiExamRecording.uploading = true
  try {
    if (aiExamRecorder && aiExamRecorder.state !== 'inactive') {
      const currentRecorder = aiExamRecorder
      await new Promise((resolve) => {
        const previousStop = currentRecorder.onstop
        currentRecorder.onstop = (event) => { previousStop?.(event); resolve() }
        currentRecorder.stop()
      })
      aiExamRecorder = null
    }
    const blob = new Blob(aiExamRecordedChunks, { type: 'video/webm' })
    if (blob.size > 0) {
      const file = new File([blob], `ai-exam-${sessionForm.processId}.webm`, { type: 'video/webm' })
      await interviewApi.uploadAiExamRecording(sessionForm.processId, file)
      aiExamRecordedChunks = []
      aiExamRecording.uploaded = true
      await reportAntiCheat('AI_RECORDING_UPLOADED', 'AI答题录像已上传')
      ElMessage.success('AI答题录像已上传')
    }
  } finally {
    aiExamRecording.uploading = false
    aiExamRecordingStopInProgress = false
    stopAiExamStream()
  }
}

function stopAiExamStream() {
  aiExamStream?.getTracks().forEach((track) => track.stop())
  aiExamStream = null
  aiExamRecording.active = false
  if (aiExamVideo.value) aiExamVideo.value.srcObject = null
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

function handleRestrictedShortcut(event) {
  if (!runtimeConfig.disableDevtoolsShortcuts) return
  const key = event.key?.toLowerCase()
  const blocked = event.key === 'F12'
    || (event.ctrlKey && event.shiftKey && ['i', 'j', 'c'].includes(key))
    || (event.metaKey && event.altKey && ['i', 'j', 'c'].includes(key))
    || (event.ctrlKey && key === 'u')
    || (isAiExamInProgress() && (event.ctrlKey || event.metaKey) && ['c', 'v', 'x'].includes(key))
  if (!blocked) return
  event.preventDefault()
  event.stopPropagation()
  ElMessage.warning(isAiExamInProgress() && ['c', 'v', 'x'].includes(key) ? 'AI答题期间禁止复制、剪切和粘贴' : '面试期间已禁用开发者工具快捷键')
}

function handleContextMenu(event) {
  if (!runtimeConfig.disableDevtoolsShortcuts) return
  event.preventDefault()
}

function shouldReportSwitch() {
  return antiCheat.hasEnteredFullscreen && processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && processSummary.value?.overallStatus === 'IN_PROGRESS'
}

function notifyAiFinishedIfNeeded() {
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'WAITING_APPROVAL' && !antiCheat.aiEndNotified) {
    antiCheat.aiEndNotified = true
    currentQuestion.value = null
    clearAiRefresh()
    stopAndUploadAiExamRecording().catch(fail)
    ElMessageBox.alert(processSummary.value.processStatusView || 'AI面试已结束，请等待HR人工审批。', '面试结束', { confirmButtonText: '知道了' })
  }
}

function isAiExamInProgress() {
  return processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && processSummary.value?.overallStatus === 'IN_PROGRESS'
}

function handleClipboardBlocked(event) {
  if (!isAiExamInProgress()) return
  event.preventDefault()
  event.stopPropagation()
  reportAntiCheat('CLIPBOARD_BLOCKED', `AI答题期间阻止${event.type}操作`)
  ElMessage.warning('AI答题期间禁止复制、剪切、粘贴和拖拽')
}
async function joinVideo() {
  try {
    disconnectVideo()
    await interviewApi.intervieweeJoin(sessionForm.processId)
    localStream = await requestCameraAndMicrophone()
    localVideo.value.srcObject = localStream
    playVideo(localVideo.value)
    peer = createPeerConnection(await loadIceServers())
    addedHrIce = new Set()
    pendingHrIce = []
    localStream.getTracks().forEach((track) => peer.addTrack(track, localStream))
    remoteStream = null
    peer.ontrack = (event) => { remoteStream = attachRemoteTrack(remoteVideo.value, event, remoteStream) }
    peer.onconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(peer.connectionState)) {
        ElMessage.warning('远端视频连接不稳定，请双方保持页面打开，必要时重新加入视频面')
      }
    }
    peer.onicecandidate = async (event) => {
      if (isRelayIceCandidate(event.candidate)) {
        await interviewApi.addIntervieweeIce(sessionForm.processId, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    pollTimer = setInterval(async () => {
      const state = (await interviewApi.getVideoState(sessionForm.processId)).data
      if (state.offerSdp && !peer.currentRemoteDescription) {
        await peer.setRemoteDescription(JSON.parse(state.offerSdp))
        await flushPendingHrIce()
        const answer = await peer.createAnswer()
        await peer.setLocalDescription(answer)
        await interviewApi.submitVideoAnswer(sessionForm.processId, { answerSdp: JSON.stringify(answer) })
      }
      if (state.hrIceCandidates) {
        const candidates = state.hrIceCandidates.split('\n').filter(Boolean)
        for (const item of candidates) {
          if (!addedHrIce.has(item)) {
            addedHrIce.add(item)
            await addHrIceCandidate(item)
          }
        }
      }
      if (state.sessionStatus === 'RECORDING') {
        startRecordingIfNeeded()
      }
      if (shouldHandleRecordingEnd(state)) {
        handledRecordingEndSignal = recordingEndSignalKey(state)
        clearInterval(pollTimer)
        pollTimer = null
        scheduleRecordingStop(state.recordingEndRequestedAt)
      }
    }, 1000)
    ElMessage.success('已加入视频面，等待HR就绪后同步开始录制')
  } catch (error) { ElMessage.error(buildMediaErrorMessage(error)) }
}
async function stopRecording() {
  try {
    clearInterval(pollTimer)
    pollTimer = null
    const response = await interviewApi.completeIntervieweeVideo(sessionForm.processId)
    handledRecordingEndSignal = recordingEndSignalKey(response.data || {})
    scheduleRecordingStop(response.data?.recordingEndRequestedAt)
  } catch (error) { fail(error) }
}

function recordingEndSignalKey(state) {
  return state.recordingEndRequestedAt || (state.sessionStatus === 'END_REQUESTED' ? 'END_REQUESTED' : '')
}

function shouldHandleRecordingEnd(state) {
  const signal = recordingEndSignalKey(state)
  return signal && signal !== handledRecordingEndSignal
}

function scheduleRecordingStop(endAt) {
  clearTimeout(recordingEndTimer)
  const delay = Math.max(new Date(endAt || Date.now()).getTime() - Date.now(), 0)
  recordingEndTimer = setTimeout(async () => {
    try {
      await stopAndUploadRecording()
      disconnectVideo()
    } catch (error) {
      fail(error)
    }
  }, delay)
}

function startRecordingIfNeeded() {
  if (!localStream || (recorder && recorder.state !== 'inactive')) return
  recorder = new MediaRecorder(localStream)
  recordedChunks = []
  recorder.ondataavailable = (event) => { if (event.data.size > 0) recordedChunks.push(event.data) }
  recorder.start(1000)
  ElMessage.success('双方已进入视频面，录制已同步开始')
}

async function stopAndUploadRecording() {
  if (recordingStopInProgress) return
  if ((!recorder || recorder.state === 'inactive') && recordedChunks.length === 0) return
  recordingStopInProgress = true
  try {
    if (recorder && recorder.state !== 'inactive') {
      const currentRecorder = recorder
      await new Promise((resolve) => { currentRecorder.onstop = resolve; currentRecorder.stop() })
      recorder = null
    }
    const blob = new Blob(recordedChunks, { type: 'video/webm' })
    if (blob.size > 0) {
      const file = new File([blob], `interviewee-${sessionForm.processId}.webm`, { type: 'video/webm' })
      await interviewApi.uploadVideoRecording(sessionForm.processId, file)
      recordedChunks = []
      ElMessage.success('面试者录制已上传')
    }
  } finally {
    recordingStopInProgress = false
  }
}

function disconnectVideo() {
  clearInterval(pollTimer)
  clearTimeout(recordingEndTimer)
  pollTimer = null
  recordingEndTimer = null
  peer?.getSenders?.().forEach((sender) => sender.track?.stop())
  peer?.close()
  peer = null
  recorder = null
  recordedChunks = []
  recordingStopInProgress = false
  handledRecordingEndSignal = ''
  localStream?.getTracks().forEach((track) => track.stop())
  localStream = null
  remoteStream = null
  pendingHrIce = []
  if (localVideo.value) localVideo.value.srcObject = null
  if (remoteVideo.value) remoteVideo.value.srcObject = null
}

async function addHrIceCandidate(item) {
  if (!peer?.remoteDescription) {
    pendingHrIce.push(item)
    return
  }
  try {
    await peer.addIceCandidate(JSON.parse(item))
  } catch (error) {
    console.warn('添加HR ICE失败', error)
  }
}

async function flushPendingHrIce() {
  const items = pendingHrIce
  pendingHrIce = []
  for (const item of items) {
    await addHrIceCandidate(item)
  }
}

async function loadIceServers() {
  try {
    const response = await interviewApi.getIceServers()
    return response.data?.length ? response.data : defaultIceServers()
  } catch {
    return defaultIceServers()
  }
}

async function loadRuntimeConfig() {
  try {
    const response = await interviewApi.getRuntimeConfig()
    runtimeConfig.disableDevtoolsShortcuts = response.data?.disableDevtoolsShortcuts !== false
  } catch {
    runtimeConfig.disableDevtoolsShortcuts = true
  }
}

onBeforeUnmount(() => {
  clearAiRefresh()
  document.removeEventListener('keydown', handleRestrictedShortcut, true)
  document.removeEventListener('contextmenu', handleContextMenu, true)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('blur', handleWindowBlur)
  document.removeEventListener('copy', handleClipboardBlocked, true)
  document.removeEventListener('cut', handleClipboardBlocked, true)
  document.removeEventListener('paste', handleClipboardBlocked, true)
  document.removeEventListener('drop', handleClipboardBlocked, true)
  stopAndUploadAiExamRecording().catch(() => {})
  disconnectVideo()
})

onMounted(async () => {
  await loadRuntimeConfig()
  document.addEventListener('keydown', handleRestrictedShortcut, true)
  document.addEventListener('contextmenu', handleContextMenu, true)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('blur', handleWindowBlur)
  document.addEventListener('copy', handleClipboardBlocked, true)
  document.addEventListener('cut', handleClipboardBlocked, true)
  document.addEventListener('paste', handleClipboardBlocked, true)
  document.addEventListener('drop', handleClipboardBlocked, true)
  if (!sessionForm.processId) {
    ElMessage.warning('请从面试者首页选择报名记录进入面试')
    router.push('/user')
    return
  }
  await loadProcessRecords()
  syncAiAutoRefresh()
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.overallStatus === 'IN_PROGRESS') {
    ElMessage.info('AI面试需要全屏答题并开启摄像头/麦克风录像，切屏和复制粘贴会被记录')
  }
})
</script>

<style scoped>
.summary-box { margin-top: 16px; padding: 14px; border-radius: 16px; background: rgba(255,255,255,0.82); }
.summary-box p { margin: 6px 0; }
.ai-panel { position: relative; overflow: hidden; }
.ai-status-card { display: grid; grid-template-columns: auto 1fr; gap: 4px 10px; align-items: center; margin-bottom: 14px; padding: 14px; border-radius: 16px; background: rgba(16, 37, 50, 0.06); border: 1px solid rgba(16, 37, 50, 0.08); }
.ai-status-card small { grid-column: 2; color: #6d7a83; }
.ai-status-card.busy { background: rgba(15, 108, 143, 0.1); border-color: rgba(15, 108, 143, 0.22); }
.status-dot { width: 10px; height: 10px; border-radius: 999px; background: #0f6c8f; box-shadow: 0 0 0 6px rgba(15, 108, 143, 0.12); }
.ai-status-card.busy .status-dot { animation: pulse 1.2s ease-in-out infinite; }
.ai-recording-card { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) minmax(120px, 160px); gap: 14px; align-items: center; margin-bottom: 14px; padding: 14px; border-radius: 16px; background: rgba(143, 15, 15, 0.08); border: 1px solid rgba(143, 15, 15, 0.18); }
.ai-recording-card.active { background: rgba(28, 120, 74, 0.1); border-color: rgba(28, 120, 74, 0.25); }
.ai-recording-card p { margin: 6px 0 0; color: #6d7a83; line-height: 1.6; }
.ai-recording-card video { width: 160px; height: 100px; object-fit: cover; background: #111; border-radius: 12px; }
.ai-submit-overlay { position: absolute; inset: 0; z-index: 5; display: grid; place-content: center; justify-items: center; gap: 12px; padding: 24px; text-align: center; background: rgba(248, 245, 239, 0.88); backdrop-filter: blur(8px); }
.ai-submit-overlay p { max-width: 360px; margin: 0; color: #6d7a83; line-height: 1.7; }
.ai-orbit { position: relative; width: 64px; height: 64px; border-radius: 999px; border: 2px solid rgba(15, 108, 143, 0.18); animation: spin 1.4s linear infinite; }
.ai-orbit span { position: absolute; width: 12px; height: 12px; border-radius: 999px; background: #0f6c8f; }
.ai-orbit span:nth-child(1) { top: -6px; left: 26px; }
.ai-orbit span:nth-child(2) { right: 2px; bottom: 8px; background: #f0b66f; }
.ai-orbit span:nth-child(3) { left: 2px; bottom: 8px; background: #102532; }
.empty-box { padding: 18px; border-radius: 16px; background: rgba(255,255,255,0.75); color: #6d7a83; }
.question-card { display: grid; gap: 8px; padding: 16px; border-radius: 18px; background: rgba(255,255,255,0.82); margin-bottom: 14px; }
.question-card p { margin: 0; line-height: 1.7; }
.question-card small { color: #6d7a83; }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; margin-top: 18px; }
.video-box { min-width: 0; background: rgba(255,255,255,0.82); padding: 12px; border-radius: 16px; }
.video-box span { display: block; margin-bottom: 8px; color: #6d7a83; }
.video-box video { width: 100%; min-height: 220px; background: #111; border-radius: 12px; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.45); opacity: 0.55; } }
@media (max-width: 900px) { .video-grid, .ai-recording-card { grid-template-columns: 1fr; } }
</style>
