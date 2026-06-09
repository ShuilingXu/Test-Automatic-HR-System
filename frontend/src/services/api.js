import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config) => {
  const token = window.localStorage.getItem('demo-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      window.localStorage.removeItem('demo-token')
      window.localStorage.removeItem('session-user')
    }
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

function authenticatedFileUrl(path) {
  const token = window.localStorage.getItem('demo-token')
  return token ? `${path}${path.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}` : path
}

export const hrApi = {
  getDashboard() { return request.get('/hr/dashboard') },
  listDepartments(params) { return request.get('/hr/departments', { params }) },
  getDepartmentTree() { return request.get('/hr/departments/tree') },
  getDepartmentDetail(id) { return request.get(`/hr/departments/${id}`) },
  saveDepartment(payload) { return request.post('/hr/departments', payload) },
  deleteDepartment(id) { return request.delete(`/hr/departments/${id}`) },
  listEmployees(params) { return request.get('/hr/employees', { params }) },
  getEmployeeDetail(id) { return request.get(`/hr/employees/${id}`) },
  saveEmployee(payload) { return request.post('/hr/employees', payload) },
  deleteEmployee(id) { return request.delete(`/hr/employees/${id}`) },
  listBindings(params) { return request.get('/hr/bindings', { params }) },
  saveBinding(payload) { return request.post('/hr/bindings', payload) },
}

export const recruitmentApi = {
  saveJob(payload) { return request.post('/recruitment/admin/jobs', payload) },
  listAdminJobs(params) { return request.get('/recruitment/admin/jobs', { params }) },
  getAdminJob(id) { return request.get(`/recruitment/admin/jobs/${id}`) },
  deleteJob(id) { return request.delete(`/recruitment/admin/jobs/${id}`) },
  listCandidates(params) { return request.get('/recruitment/admin/candidates', { params }) },
  rejectCandidateResume(id) { return request.post(`/recruitment/admin/candidates/${id}/reject-resume`) },
  deleteCandidate(id) { return request.delete(`/recruitment/admin/candidates/${id}`) },
  listOpenJobs(params) { return request.get('/recruitment/jobs', { params }) },
  apply(payload) { return request.post('/recruitment/candidates', payload) },
  listMyCandidates() { return request.get('/recruitment/candidates/mine') },
  uploadResume(candidateId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post(`/recruitment/candidates/${candidateId}/resume`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getResumeUrl(id) { return authenticatedFileUrl(`/api/recruitment/resumes/${id}`) },
}

export const authApi = {
  login(payload) { return request.post('/auth/login', payload) },
  register(payload) { return request.post('/auth/register', payload) },
  getSession() { return request.get('/auth/me') },
  updateProfile(payload) { return request.post('/auth/profile', payload) },
  listUsers(params) { return request.get('/auth/admin/users', { params }) },
  listAuditLogs(params) { return request.get('/auth/admin/audit-logs', { params }) },
  updateUser(id, payload) { return request.post(`/auth/admin/users/${id}`, payload) },
  logout() {
    return request.post('/auth/logout').finally(() => {
      window.localStorage.removeItem('demo-token')
      window.localStorage.removeItem('session-user')
    })
  },
}

export const interviewApi = {
  getRuntimeConfig() { return request.get('/interview/runtime-config') },
  getIceServers() { return request.get('/interview/ice-servers') },
  saveKnowledgeBase(payload) { return request.post('/interview/hr/knowledge-bases', payload) },
  listKnowledgeBases(params) { return request.get('/interview/hr/knowledge-bases', { params }) },
  deleteKnowledgeBase(id) { return request.post(`/interview/hr/knowledge-bases/${id}/delete`) },
  saveKnowledgeItem(payload) { return request.post('/interview/hr/knowledge-items', payload) },
  listKnowledgeItems(params) { return request.get('/interview/hr/knowledge-items', { params }) },
  deleteKnowledgeItem(id) { return request.post(`/interview/hr/knowledge-items/${id}/delete`) },
  saveJobKnowledgeWeight(payload) { return request.post('/interview/hr/job-knowledge-weights', payload) },
  listJobKnowledgeWeights(params) { return request.get('/interview/hr/job-knowledge-weights', { params }) },
  deleteJobKnowledgeWeight(id) { return request.post(`/interview/hr/job-knowledge-weights/${id}/delete`) },
  saveLlmConfig(payload) { return request.post('/interview/it/llm-configs', payload) },
  listLlmConfigs(params) { return request.get('/interview/it/llm-configs', { params }) },
  deleteLlmConfig(id) { return request.post(`/interview/it/llm-configs/${id}/delete`) },
  startProcess(payload) { return request.post('/interview/hr/processes', payload) },
  listProcesses(params) { return request.get('/interview/hr/processes', { params }) },
  getIntervieweeProcess(processId) { return request.get(`/interview/interviewee/process/${processId}`) },
  getNextAiQuestion(processId) { return request.get(`/interview/interviewee/next-question/${processId}`) },
  listAiRecords(params) { return request.get('/interview/hr/ai-records', { params }) },
  listIntervieweeAiRecords(params) { return request.get('/interview/interviewee/ai-records', { params }) },
  createVideoSession(processId, params) { return request.post(`/interview/hr/video-session/${processId}`, null, { params }) },
  publishVideoOffer(processId, payload) { return request.post(`/interview/hr/video-offer/${processId}`, payload) },
  getVideoState(processId) { return request.get(`/interview/interviewee/video-state/${processId}`) },
  getHrVideoState(processId) { return request.get(`/interview/hr/video-state/${processId}`) },
  submitVideoAnswer(processId, payload) { return request.post(`/interview/interviewee/video-answer/${processId}`, payload) },
  addHrIce(processId, payload) { return request.post(`/interview/hr/video-ice/${processId}`, payload) },
  addIntervieweeIce(processId, payload) { return request.post(`/interview/interviewee/video-ice/${processId}`, payload) },
  uploadVideoRecording(processId, file) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('originalFileName', file.name)
    formData.append('contentType', file.type || 'video/webm')
    return request.post(`/interview/interviewee/video-recording/${processId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  reportAntiCheatEvent(payload) { return request.post('/interview/interviewee/anti-cheat-event', payload) },
  uploadHrVideoRecording(processId, file) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('originalFileName', file.name)
    formData.append('contentType', file.type || 'video/webm')
    return request.post(`/interview/hr/video-recording/${processId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getRecordingUrl(processId) { return authenticatedFileUrl(`/api/interview/hr/video-recording/${processId}`) },
  intervieweeJoin(processId) { return request.post(`/interview/interviewee/video-join/${processId}`) },
  hrJoin(processId, params) { return request.post(`/interview/hr/video-join/${processId}`, null, { params }) },
  completeVideo(processId, params) { return request.post(`/interview/hr/video-complete/${processId}`, null, { params }) },
  approveAi(processId, payload) { return request.post(`/interview/hr/approve-ai/${processId}`, payload) },
  approveVideo(processId, payload) { return request.post(`/interview/hr/approve-video/${processId}`, payload) },
  approveOnsite(processId, payload) { return request.post(`/interview/hr/approve-onsite/${processId}`, payload) },
  terminateProcess(processId, payload) { return request.post(`/interview/hr/terminate/${processId}`, payload) },
  submitAiAnswer(payload) { return request.post('/interview/interviewee/ai-answer', payload) },
}
