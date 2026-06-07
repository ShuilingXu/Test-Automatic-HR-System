import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import ConsoleView from '../views/ConsoleView.vue'
import LoginView from '../views/LoginView.vue'
import CandidateRegisterView from '../views/CandidateRegisterView.vue'
import CandidateInterviewView from '../views/CandidateInterviewView.vue'
import InterviewAdminView from '../views/InterviewAdminView.vue'
import UserPortalView from '../views/UserPortalView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/admin', name: 'admin', component: ConsoleView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'] } },
  { path: '/admin/interview', redirect: '/interview/hr' },
  { path: '/interview/hr', name: 'interview-hr', component: InterviewAdminView, meta: { requiresAuth: true, roles: ['IT_ADMIN', 'HR_ADMIN', 'HR_USER'] } },
  { path: '/user', name: 'user-portal', component: UserPortalView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/candidate/register', name: 'candidate-register', component: CandidateRegisterView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
  { path: '/candidate/interview', redirect: '/interview/interviewee' },
  { path: '/interview/interviewee', name: 'interview-interviewee', component: CandidateInterviewView, meta: { requiresAuth: true, roles: ['INTERVIEWEE'] } },
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
