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
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

export const hrApi = {
  getDashboard() { return request.get('/hr/dashboard') },
  listDepartments() { return request.get('/hr/departments') },
  getDepartmentTree() { return request.get('/hr/departments/tree') },
  getDepartmentDetail(id) { return request.get(`/hr/departments/${id}`) },
  saveDepartment(payload) { return request.post('/hr/departments', payload) },
  listEmployees(params) { return request.get('/hr/employees', { params }) },
  getEmployeeDetail(id) { return request.get(`/hr/employees/${id}`) },
  saveEmployee(payload) { return request.post('/hr/employees', payload) },
  listBindings(params) { return request.get('/hr/bindings', { params }) },
  saveBinding(payload) { return request.post('/hr/bindings', payload) },
}

export const recruitmentApi = {
  saveJob(payload) { return request.post('/recruitment/admin/jobs', payload) },
  listAdminJobs(params) { return request.get('/recruitment/admin/jobs', { params }) },
  getAdminJob(id) { return request.get(`/recruitment/admin/jobs/${id}`) },
  deleteJob(id) { return request.delete(`/recruitment/admin/jobs/${id}`) },
  listCandidates(params) { return request.get('/recruitment/admin/candidates', { params }) },
  listOpenJobs(params) { return request.get('/recruitment/jobs', { params }) },
  apply(payload) { return request.post('/recruitment/candidates', payload) },
  uploadResume(candidateId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post(`/recruitment/candidates/${candidateId}/resume`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export const authApi = {
  login(payload) { return request.post('/auth/login', payload) },
  register(payload) { return request.post('/auth/register', payload) },
  getSession() { return request.get('/auth/me') },
  updateProfile(payload) { return request.post('/auth/profile', payload) },
  listUsers(params) { return request.get('/auth/admin/users', { params }) },
  updateUser(id, payload) { return request.post(`/auth/admin/users/${id}`, payload) },
  logout() {
    return request.post('/auth/logout').finally(() => {
      window.localStorage.removeItem('demo-token')
      window.localStorage.removeItem('session-user')
    })
  },
}

export const interviewApi = {
  saveBatch(payload) { return request.post('/interview/admin/batches', payload) },
  listBatches(params) { return request.get('/interview/admin/batches', { params }) },
  saveQuestion(payload) { return request.post('/interview/admin/questions', payload) },
  listQuestions(params) { return request.get('/interview/admin/questions', { params }) },
  assignCandidate(payload) { return request.post('/interview/admin/candidates', payload) },
  listInterviewCandidates(params) { return request.get('/interview/admin/candidates', { params }) },
  listSubmissions(params) { return request.get('/interview/admin/submissions', { params }) },
  scoreSubmission(submissionId, payload) { return request.post(`/interview/admin/submissions/${submissionId}/score`, payload) },
  listCandidateQuestions(interviewCandidateId) { return request.get(`/interview/candidates/${interviewCandidateId}/questions`) },
  submitAnswer(payload) { return request.post('/interview/submissions', payload) },
  getSystems() {
    return Promise.resolve({ success: true, message: 'system entries', data: [
      { code: 'ADMIN', name: '管理员门户', path: '/admin' },
      { code: 'USER', name: '面试者门户', path: '/user' },
      { code: 'LOGIN', name: '统一登录', path: '/login' },
    ] })
  },
  getCandidateEntry() {
    return Promise.resolve({ success: true, message: 'candidate entries', data: { registerPath: '/user', onlineInterviewPath: '/candidate/interview' } })
  },
}
