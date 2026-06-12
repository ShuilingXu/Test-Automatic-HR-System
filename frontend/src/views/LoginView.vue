<template>
  <div class="page-shell">
    <section class="page-card auth-card">
      <p class="page-eyebrow">Unified Login</p>
      <h1 class="page-title">统一登录</h1>
      <div class="page-grid">
        <el-form :model="loginForm" label-position="top" class="surface login-form" autocomplete="off">
          <h3>登录</h3>
          <el-form-item label="用户名"><el-input v-model="loginForm.username" autocomplete="off" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="图形验证码">
            <div class="captcha-row">
              <el-input v-model="loginForm.captchaCode" placeholder="输入图片字符" />
              <button type="button" class="captcha-image" @click="loadLoginCaptcha"><img :src="loginCaptcha.imageBase64" alt="登录图形验证码" /></button>
            </div>
          </el-form-item>
          <div class="link-row">
            <el-button type="primary" @click="login">登录</el-button>
            <RouterLink class="link-chip" to="/">返回首页</RouterLink>
          </div>
        </el-form>
        <el-form :model="registerForm" label-position="top" class="surface login-form">
          <h3>面试者注册</h3>
          <el-form-item label="用户名"><el-input v-model="registerForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="registerForm.displayName" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="registerForm.mobilePhone" placeholder="手机号和邮箱择一填写" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="registerForm.email" placeholder="手机号和邮箱择一填写" /></el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="registerForm.verificationCode" />
              <el-button :loading="sendingCode" @click="sendRegisterCode">获取验证码</el-button>
            </div>
          </el-form-item>
          <el-form-item label="图形验证码">
            <div class="captcha-row">
              <el-input v-model="registerForm.captchaCode" placeholder="输入图片字符" />
              <button type="button" class="captcha-image" @click="loadRegisterCaptcha"><img :src="registerCaptcha.imageBase64" alt="注册图形验证码" /></button>
            </div>
          </el-form-item>
          <el-button type="primary" @click="register">注册面试者</el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'

const router = useRouter()
const loginForm = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })
const registerForm = reactive({ username: '', password: '', displayName: '', mobilePhone: '', email: '', verificationCode: '', captchaId: '', captchaCode: '' })
const loginCaptcha = reactive({ imageBase64: '' })
const registerCaptcha = reactive({ imageBase64: '' })
const sendingCode = ref(false)

function targetByRole(roleCode) {
  return roleCode === 'INTERVIEWEE' ? '/user' : '/admin'
}

async function login() {
  try {
    const response = await authApi.login({ ...loginForm })
    localStorage.setItem('demo-token', response.data.token)
    localStorage.setItem('session-user', JSON.stringify(response.data.user))
    ElMessage.success('登录成功')
    router.push(targetByRole(response.data.user.roleCode))
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
    await loadLoginCaptcha()
  }
}

async function register() {
  try {
    await authApi.register({ ...registerForm })
    ElMessage.success('注册成功，请登录后完善信息')
    loginForm.username = registerForm.username
    loginForm.password = registerForm.password
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
    await loadRegisterCaptcha()
  }
}

async function sendRegisterCode() {
  const hasPhone = Boolean(registerForm.mobilePhone.trim())
  const hasEmail = Boolean(registerForm.email.trim())
  if (hasPhone === hasEmail) {
    ElMessage.warning('手机号和邮箱必须择一填写')
    return
  }
  sendingCode.value = true
  try {
    await authApi.sendRegisterCode({ mobilePhone: registerForm.mobilePhone, email: registerForm.email, captchaId: registerForm.captchaId, captchaCode: registerForm.captchaCode })
    ElMessage.success('验证码已发送')
    await loadRegisterCaptcha()
  } catch (error) {
    ElMessage.error(error.message || '验证码发送失败')
    await loadRegisterCaptcha()
  } finally {
    sendingCode.value = false
  }
}

async function loadLoginCaptcha() {
  const response = await authApi.getCaptcha()
  loginForm.captchaId = response.data.captchaId
  loginForm.captchaCode = ''
  loginCaptcha.imageBase64 = response.data.imageBase64
}

async function loadRegisterCaptcha() {
  const response = await authApi.getCaptcha()
  registerForm.captchaId = response.data.captchaId
  registerForm.captchaCode = ''
  registerCaptcha.imageBase64 = response.data.imageBase64
}

onMounted(async () => {
  await Promise.all([loadLoginCaptcha(), loadRegisterCaptcha()])
})
</script>

<style scoped>
.auth-card { max-width: 1080px; }
.login-form { margin-top: 12px; }
.code-row { display: grid; grid-template-columns: 1fr auto; gap: 10px; width: 100%; }
.captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(96px, 128px); gap: 10px; width: 100%; min-width: 0; }
.captcha-image { width: 100%; max-width: 128px; height: 44px; padding: 0; border: 1px solid rgba(16, 37, 50, 0.16); border-radius: 10px; background: #f6f1e8; cursor: pointer; overflow: hidden; }
.captcha-image img { display: block; width: 100%; height: 100%; object-fit: contain; }
@media (max-width: 560px) { .code-row, .captcha-row { grid-template-columns: minmax(0, 1fr); } .captcha-image { max-width: 100%; } }
</style>
