<template>
  <div class="page-shell">
    <section class="page-card auth-card">
      <p class="page-eyebrow">Session</p>
      <h1 class="page-title">登录与会话预留</h1>
      <p class="page-subtitle">当前不启用鉴权拦截，只保留登录、登出、会话刷新和 token 注入流程。</p>
      <el-form :model="form" label-position="top" class="login-form">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <div class="link-row">
          <el-button type="primary" @click="login">模拟登录</el-button>
          <el-button @click="logout">清理会话</el-button>
          <RouterLink class="link-chip" to="/">返回管理台</RouterLink>
        </div>
      </el-form>
      <div class="surface">
        <h3>当前会话</h3>
        <pre>{{ session }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'

const form = reactive({ username: 'tester', password: '123456' })
const session = reactive({ authenticated: false, userName: 'guest', roles: [] })
async function refreshSession() { Object.assign(session, (await authApi.getSession()).data) }
async function login() { await authApi.login({ ...form }); localStorage.setItem('demo-token', 'demo-token-value'); ElMessage.success('模拟登录完成'); await refreshSession() }
async function logout() { await authApi.logout(); localStorage.removeItem('demo-token'); ElMessage.success('本地会话已清理'); await refreshSession() }
onMounted(refreshSession)
</script>

<style scoped>
.auth-card { max-width: 760px; }
.login-form { margin-top: 24px; }
pre { white-space: pre-wrap; margin: 0; }
</style>
