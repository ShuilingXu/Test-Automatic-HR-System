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
            <el-button @click="stopRecording">结束并上传录制</el-button>
          </div>
          <div v-if="processSummary" class="summary-box">
            <p>当前阶段：{{ processSummary.currentStage }}</p>
            <p>状态：{{ processSummary.processStatusView }}</p>
            <p>AI均分：{{ processSummary.aiAverageScore ?? '-' }}</p>
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
          <div v-if="aiRecords.length === 0" class="empty-box">流程开始后系统会先发出 AI 问题</div>
          <div v-for="item in aiRecords" :key="item.id" class="question-card">
            <strong>Q{{ item.sequenceNo }} {{ item.knowledgePoint }}</strong>
            <p>{{ item.questionContent }}</p>
            <small>单题均分：{{ item.averageScore ?? '-' }}</small>
            <p>你的回答：{{ item.answerContent || '待回答' }}</p>
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
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { interviewApi } from '../services/api'

const route = useRoute()
const sessionForm = reactive({ processId: route.query.processId ? Number(route.query.processId) : null })
const aiAnswer = reactive({ answerContent: '' })
const aiRecords = ref([])
const processSummary = ref(null)
const currentQuestion = ref(null)
const localVideo = ref(null)
const remoteVideo = ref(null)
let localStream = null
let peer = null
let pollTimer = null
let recorder = null
let recordedChunks = []
let addedHrIce = new Set()

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadProcessRecords() {
  try {
    if (!sessionForm.processId) {
      ElMessage.warning('请输入面试流程ID')
      return
    }
    processSummary.value = (await interviewApi.getIntervieweeProcess(sessionForm.processId)).data
    currentQuestion.value = (await interviewApi.getNextAiQuestion(sessionForm.processId)).data
    aiRecords.value = (await interviewApi.listIntervieweeAiRecords({ processId: sessionForm.processId })).data
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
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
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
  } catch (error) { fail(error) }
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
  disconnectVideo()
})

onMounted(async () => {
  if (sessionForm.processId) {
    await loadProcessRecords()
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
