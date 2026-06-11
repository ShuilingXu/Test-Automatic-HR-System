import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import ConsoleView from '../views/ConsoleView.vue'
import LoginView from '../views/LoginView.vue'
import CandidateRegisterView from '../views/CandidateRegisterView.vue'
import CandidateInterviewView from '../views/CandidateInterviewView.vue'
import CandidateDetailView from '../views/CandidateDetailView.vue'
import InterviewAdminView from '../views/InterviewAdminView.vue'
import UserPortalView from '../views/UserPortalView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/admin', redirect: '/admin/dashboard' },
  { path: '/admin/dashboard', name: 'admin-dashboard', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'dashboard' } },
  { path: '/admin/audit', name: 'admin-audit', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN'], consoleTab: 'audit' } },
  { path: '/admin/users', name: 'admin-users', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN'], consoleTab: 'users' } },
  { path: '/admin/users/:id', name: 'admin-user-detail', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN'], consoleTab: 'users' } },
  { path: '/admin/departments', name: 'admin-departments', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'departments' } },
  { path: '/admin/departments/:id', name: 'admin-department-detail', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'departments' } },
  { path: '/admin/employees', name: 'admin-employees', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'employees' } },
  { path: '/admin/employees/:id', name: 'admin-employee-detail', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'employees' } },
  { path: '/admin/bindings', name: 'admin-bindings', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'bindings' } },
  { path: '/admin/bindings/:id', name: 'admin-binding-detail', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'bindings' } },
  { path: '/admin/recruitment/jobs', name: 'admin-recruitment-jobs', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'recruitment', recruitmentMode: 'jobQuery' } },
  { path: '/admin/recruitment/jobs/new', name: 'admin-recruitment-job-new', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'recruitment', recruitmentMode: 'jobCreate' } },
  { path: '/admin/recruitment/jobs/:id', name: 'admin-recruitment-job-detail', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'recruitment', recruitmentMode: 'jobEdit' } },
  { path: '/admin/recruitment/candidates', name: 'admin-recruitment-candidates', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], consoleTab: 'recruitment', recruitmentMode: 'candidates' } },
  { path: '/admin/recruitment/candidates/:id', name: 'admin-recruitment-candidate-detail', component: CandidateDetailView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'] } },
  { path: '/admin/interview', redirect: '/interview/hr' },
  { path: '/interview/hr', redirect: '/interview/hr/processes' },
  { path: '/interview/hr/knowledge-bases', name: 'interview-knowledge-bases', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'kb' } },
  { path: '/interview/hr/knowledge-bases/:id', name: 'interview-knowledge-base-detail', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'kb' } },
  { path: '/interview/hr/weights', name: 'interview-weights', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'weights' } },
  { path: '/interview/hr/weights/:id', name: 'interview-weight-detail', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'weights' } },
  { path: '/interview/hr/system', name: 'interview-system', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN'], interviewTab: 'system' } },
  { path: '/interview/hr/processes', name: 'interview-processes', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'process' } },
  { path: '/interview/hr/processes/:id', name: 'interview-process-detail', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'], interviewTab: 'process' } },
  { path: '/user', redirect: '/user/applications' },
  { path: '/user/profile', name: 'user-profile', component: UserPortalView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'], portalPage: 'profile' } },
  { path: '/user/applications', name: 'user-applications', component: UserPortalView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'], portalPage: 'applications' } },
  { path: '/user/applications/:id', name: 'user-application-detail', component: UserPortalView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'], portalPage: 'applications' } },
  { path: '/candidate/register', name: 'candidate-register', component: CandidateRegisterView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/candidate/interview', redirect: '/user' },
  { path: '/interview/interviewee', name: 'interview-interviewee', component: CandidateInterviewView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/interview/interviewee/processes/:processId', name: 'interview-interviewee-process', component: CandidateInterviewView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) {
    return true
  }
  const raw = localStorage.getItem('session-user')
  if (!raw) {
    return '/login'
  }
  const session = JSON.parse(raw)
  if (to.meta.roles && !to.meta.roles.includes(session.roleCode)) {
    return session.roleCode === 'INTERVIEWEE' ? '/user' : '/admin'
  }
  return true
})

export default router
