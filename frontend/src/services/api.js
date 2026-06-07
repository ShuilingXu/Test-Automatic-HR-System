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
  getDashboard() {
    return request.get('/hr/dashboard')
  },
  listDepartments() {
    return request.get('/hr/departments')
  },
  getDepartmentTree() {
    return request.get('/hr/departments/tree')
  },
  getDepartmentDetail(id) {
    return request.get(`/hr/departments/${id}`)
  },
  saveDepartment(payload) {
    return request.post('/hr/departments', payload)
  },
  listEmployees(params) {
    return request.get('/hr/employees', { params })
  },
  getEmployeeDetail(id) {
    return request.get(`/hr/employees/${id}`)
  },
  saveEmployee(payload) {
    return request.post('/hr/employees', payload)
  },
  listBindings(params) {
    return request.get('/hr/bindings', { params })
  },
  saveBinding(payload) {
    return request.post('/hr/bindings', payload)
  },
}

export const recruitmentApi = {
  saveJob(payload) {
    return request.post('/recruitment/admin/jobs', payload)
  },
  listAdminJobs(params) {
    return request.get('/recruitment/admin/jobs', { params })
  },
  getAdminJob(id) {
    return request.get(`/recruitment/admin/jobs/${id}`)
  },
  listCandidates(params) {
    return request.get('/recruitment/admin/candidates', { params })
  },
  listOpenJobs(params) {
    return request.get('/recruitment/jobs', { params })
  },
  apply(payload) {
    return request.post('/recruitment/candidates', payload)
  },
  uploadResume(candidateId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post(`/recruitment/candidates/${candidateId}/resume`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export const authApi = {
  login(payload) {
    return Promise.resolve({ success: true, message: '预留登录接口', data: payload })
  },
  logout() {
    return Promise.resolve({ success: true, message: '预留登出接口', data: null })
  },
  getSession() {
    return Promise.resolve({
      success: true,
      message: '预留会话接口',
      data: {
        authenticated: false,
        userName: 'guest',
        roles: ['tester'],
      },
    })
  },
}

export const interviewApi = {
  getSystems() {
    return Promise.resolve({
      success: true,
      message: '预留系统入口',
      data: [
        { code: 'INTERVIEW', name: '面试系统管理入口', path: '/interview/admin' },
        { code: 'RECRUITMENT', name: '招聘系统入口', path: '/recruitment' },
        { code: 'PERFORMANCE', name: '绩效系统入口', path: '/performance' },
      ],
    })
  },
  getCandidateEntry() {
    return Promise.resolve({
      success: true,
      message: '预留测试者入口',
      data: {
        registerPath: '/candidate/register',
        onlineInterviewPath: '/candidate/interview',
      },
    })
  },
}
