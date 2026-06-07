import { createRouter, createWebHistory } from 'vue-router'
import ConsoleView from '../views/ConsoleView.vue'
import LoginView from '../views/LoginView.vue'
import CandidateRegisterView from '../views/CandidateRegisterView.vue'
import CandidateInterviewView from '../views/CandidateInterviewView.vue'
import InterviewAdminView from '../views/InterviewAdminView.vue'

const routes = [
  { path: '/', name: 'workspace', component: ConsoleView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/candidate/register', name: 'candidate-register', component: CandidateRegisterView },
  { path: '/candidate/interview', name: 'candidate-interview', component: CandidateInterviewView },
  { path: '/interview/admin', name: 'interview-admin', component: InterviewAdminView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
