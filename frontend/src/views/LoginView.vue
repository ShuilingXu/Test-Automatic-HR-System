<template>
  <div class="login-page">
    <section class="login-layout">
      <aside class="login-intro">
        <RouterLink class="login-brand" to="/"><span class="brand-mark">A</span><span><strong>千早爱音</strong><small>人才与组织中心</small></span></RouterLink>
        <div class="login-intro-copy"><p class="page-eyebrow">PEOPLE OPERATIONS</p><h1>欢迎回来，<br /><em>从这里继续。</em></h1><p>统一管理招聘、面试与团队信息，让每一次协作都更清晰。</p></div>
        <div class="login-intro-foot"><span>安全连接</span><span>·</span><span>组织工作台</span></div>
      </aside>
      <div class="login-forms">
        <div class="login-forms-head"><div><p class="page-eyebrow">Unified access</p><h2>进入工作台</h2></div><RouterLink to="/">返回首页&nbsp;↗</RouterLink></div>
        <div class="form-columns">
          <el-form :model="loginForm" label-position="top" class="login-form" autocomplete="off">
            <h3>登录账号</h3>
          <el-form-item label="用户名"><el-input v-model="loginForm.username" autocomplete="off" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="图形验证码">
            <div class="captcha-row">
              <el-input v-model="loginForm.captchaCode" placeholder="输入图片字符" />
              <button type="button" class="captcha-image" @click="loadLoginCaptcha"><img :src="loginCaptcha.imageBase64" alt="登录图形验证码" /></button>
            </div>
          </el-form-item>
            <div class="link-row"><el-button type="primary" @click="login">登录</el-button></div>
          </el-form>
          <el-button class="reset-password-trigger" text type="primary" @click="openPasswordReset">忘记密码？</el-button>
          <el-form :model="registerForm" label-position="top" class="login-form register-form">
            <h3>面试者注册</h3>
          <el-form-item label="用户名"><el-input v-model="registerForm.username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="registerForm.displayName" /></el-form-item>
          <el-form-item label="验证方式">
            <el-radio-group v-model="registerForm.contactType" class="contact-type" @change="changeContactType">
              <el-radio-button label="phone">手机号</el-radio-button>
              <el-radio-button label="email">邮箱</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="registerForm.contactType === 'phone' ? '手机号' : '邮箱'">
            <el-input v-if="registerForm.contactType === 'phone'" v-model="registerForm.mobilePhone" placeholder="请输入手机号" />
            <el-input v-else v-model="registerForm.email" placeholder="请输入邮箱" />
          </el-form-item>
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
        <p class="login-note">面试者可以先注册账号，完善资料后查看和参与已发起的面试流程。</p>
      </div>
    </section>
    <el-dialog v-model="passwordResetVisible" class="password-reset-dialog" title="重置密码" width="min(460px, calc(100vw - 32px))" destroy-on-close>
      <p class="reset-intro">使用已绑定的手机号或邮箱验证后设置新密码。</p>
      <el-form :model="passwordResetForm" label-position="top">
        <el-form-item label="验证方式">
          <el-radio-group v-model="passwordResetForm.contactType" @change="changePasswordResetContactType">
            <el-radio value="phone">手机号</el-radio>
            <el-radio value="email">邮箱</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="passwordResetForm.contactType === 'phone'" label="手机号"><el-input v-model="passwordResetForm.mobilePhone" autocomplete="tel" /></el-form-item>
        <el-form-item v-else label="邮箱"><el-input v-model="passwordResetForm.email" autocomplete="email" /></el-form-item>
        <el-form-item label="验证码">
          <div class="code-row"><el-input v-model="passwordResetForm.verificationCode" inputmode="numeric" /><el-button :loading="sendingPasswordResetCode" @click="sendPasswordResetCode">获取验证码</el-button></div>
        </el-form-item>
        <el-form-item label="图形验证码">
          <div class="captcha-row"><el-input v-model="passwordResetForm.captchaCode" placeholder="输入图片字符" /><button type="button" class="captcha-image" @click="loadPasswordResetCaptcha"><img :src="passwordResetCaptcha.imageBase64" alt="重置密码图形验证码" /></button></div>
        </el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordResetForm.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少6位" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="passwordResetVisible = false">取消</el-button><el-button type="primary" @click="resetPassword">确认重置</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../services/api'

const router = useRouter()
const loginForm = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })
const registerForm = reactive({ username: '', password: '', displayName: '', contactType: 'phone', mobilePhone: '', email: '', verificationCode: '', captchaId: '', captchaCode: '' })
const passwordResetForm = reactive({ contactType: 'phone', mobilePhone: '', email: '', verificationCode: '', newPassword: '', captchaId: '', captchaCode: '' })
const loginCaptcha = reactive({ imageBase64: '' })
const registerCaptcha = reactive({ imageBase64: '' })
const passwordResetCaptcha = reactive({ imageBase64: '' })
const sendingCode = ref(false)
const passwordResetVisible = ref(false)
const sendingPasswordResetCode = ref(false)

function targetByRole(roleCode) {
  return roleCode === 'INTERVIEWEE' ? '/user' : '/admin'
}

async function login() {
  try {
    const response = await authApi.login({ ...loginForm })
    localStorage.setItem('demo-token', response.data.token)
    localStorage.setItem('session-user', JSON.stringify(response.data.user))
    ElMessage.success('登录成功')
    router.push(Number(response.data.user.mustChangePassword) === 1 ? '/change-password' : targetByRole(response.data.user.roleCode))
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
    await loadLoginCaptcha()
  }
}

async function register() {
  try {
    const { contactType, ...payload } = registerForm
    await authApi.register(payload)
    ElMessage.success('注册成功，请登录后完善信息')
    loginForm.username = registerForm.username
    loginForm.password = registerForm.password
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
    await loadRegisterCaptcha()
  }
}

function changeContactType(type) {
  if (type === 'phone') registerForm.email = ''
  else registerForm.mobilePhone = ''
}

function changePasswordResetContactType(type) {
  if (type === 'phone') passwordResetForm.email = ''
  else passwordResetForm.mobilePhone = ''
}

async function openPasswordReset() {
  passwordResetForm.verificationCode = ''
  passwordResetForm.newPassword = ''
  passwordResetVisible.value = true
  await loadPasswordResetCaptcha()
}

async function sendPasswordResetCode() {
  const contact = passwordResetForm.contactType === 'phone' ? passwordResetForm.mobilePhone : passwordResetForm.email
  if (!contact.trim()) {
    ElMessage.warning(passwordResetForm.contactType === 'phone' ? '请填写手机号' : '请填写邮箱')
    return
  }
  sendingPasswordResetCode.value = true
  try {
    await authApi.sendPasswordResetCode({ mobilePhone: passwordResetForm.mobilePhone, email: passwordResetForm.email, captchaId: passwordResetForm.captchaId, captchaCode: passwordResetForm.captchaCode })
    ElMessage.success('如该联系方式已绑定账号，验证码将很快发送')
  } catch (error) {
    ElMessage.error(error.message || '验证码发送失败')
  } finally {
    sendingPasswordResetCode.value = false
    await loadPasswordResetCaptcha()
  }
}

async function resetPassword() {
  const contact = passwordResetForm.contactType === 'phone' ? passwordResetForm.mobilePhone : passwordResetForm.email
  if (!contact.trim() || !passwordResetForm.verificationCode || passwordResetForm.newPassword.length < 6) {
    ElMessage.warning('请完整填写联系方式、验证码和至少6位的新密码')
    return
  }
  try {
    const { contactType, captchaId, captchaCode, ...payload } = passwordResetForm
    await authApi.resetPassword(payload)
    passwordResetVisible.value = false
    loginForm.password = ''
    ElMessage.success('密码已重置，请使用新密码登录')
    await loadLoginCaptcha()
  } catch (error) {
    ElMessage.error(error.message || '密码重置失败')
  }
}

async function sendRegisterCode() {
  const contact = registerForm.contactType === 'phone' ? registerForm.mobilePhone : registerForm.email
  if (!contact.trim()) {
    ElMessage.warning(registerForm.contactType === 'phone' ? '请填写手机号' : '请填写邮箱')
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

async function loadPasswordResetCaptcha() {
  const response = await authApi.getCaptcha()
  passwordResetForm.captchaId = response.data.captchaId
  passwordResetForm.captchaCode = ''
  passwordResetCaptcha.imageBase64 = response.data.imageBase64
}

onMounted(async () => {
  await Promise.all([loadLoginCaptcha(), loadRegisterCaptcha()])
})
</script>

<style scoped>
.login-page { min-height: 100vh; padding: 26px; background: #eef2ef; display: grid; place-items: center; }
.login-layout { width: min(1180px, 100%); min-height: 720px; display: grid; grid-template-columns: minmax(280px, .76fr) minmax(0, 1.5fr); overflow: hidden; border: 1px solid #d2ddd6; border-radius: 12px; background: #fff; box-shadow: 0 24px 65px rgba(28, 55, 45, .12); }
.login-intro { display: flex; flex-direction: column; padding: 34px; color: #f5f8f5; background: #164f46; }.login-brand { display: inline-flex; align-items: center; gap: 11px; color: inherit; text-decoration: none; }.login-brand .brand-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 8px; background: #d6eee2; color: #164f46; font-weight: 800; }.login-brand strong, .login-brand small { display: block; }.login-brand strong { font-size: 15px; }.login-brand small { margin-top: 2px; color: rgba(245, 248, 245, .62); font-size: 11px; }.login-intro .page-eyebrow { color: #afd8c6; }.login-intro-copy { margin: auto 0; }.login-intro-copy h1 { margin: 17px 0; color: #fff; font-size: clamp(34px, 4vw, 56px); line-height: 1.08; letter-spacing: -.04em; }.login-intro-copy h1 em { color: #c5e9d6; font-style: normal; }.login-intro-copy p:last-child { max-width: 310px; margin: 0; color: rgba(245, 248, 245, .72); line-height: 1.75; }.login-intro-foot { display: flex; gap: 9px; color: rgba(245, 248, 245, .58); font-size: 12px; }.login-forms { padding: 54px 58px 40px; }.login-forms-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 43px; }.login-forms-head h2 { margin: 9px 0 0; font-size: 32px; letter-spacing: -.03em; }.login-forms-head a { color: #5c7067; font-size: 13px; text-decoration: none; }.login-forms-head a:hover { color: #164f46; }.form-columns { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); gap: 54px; }.login-form { min-width: 0; }.login-form h3 { margin: 0 0 26px; font-size: 18px; }.register-form { padding-left: 54px; border-left: 1px solid #dce5df; }.login-note { margin: 45px 0 0; color: #7c8982; font-size: 12px; line-height: 1.7; }
.code-row { display: grid; grid-template-columns: 1fr auto; gap: 10px; width: 100%; }
.captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(96px, 128px); gap: 10px; width: 100%; min-width: 0; }
.captcha-image { width: 100%; max-width: 128px; height: 48px; padding: 3px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface-soft); cursor: pointer; overflow: visible; }
.captcha-image img { display: block; width: 100%; height: 100%; object-fit: contain; }
.reset-password-trigger { margin-top: 10px; padding-inline: 0; }
.reset-intro { margin: 0 0 18px; color: var(--text-muted); line-height: 1.65; }
:global(.password-reset-dialog) { display: flex; flex-direction: column; max-height: calc(100vh - 32px); margin: 16px auto; }
:global(.password-reset-dialog .el-dialog__body) { overflow-y: auto; }
@media (max-width: 900px) { .login-page { padding: 14px; }.login-layout { grid-template-columns: 1fr; }.login-intro { min-height: 250px; padding: 26px; }.login-intro-copy { margin: 52px 0 34px; }.login-intro-copy h1 { font-size: 38px; }.form-columns { grid-template-columns: 1fr; gap: 44px; }.register-form { padding-left: 0; border-left: 0; border-top: 1px solid #dce5df; padding-top: 38px; }.login-forms { padding: 38px 30px; } }
@media (max-width: 560px) { .code-row, .captcha-row { grid-template-columns: minmax(0, 1fr); } .captcha-image { max-width: 100%; } .login-forms-head { margin-bottom: 32px; }.login-forms-head h2 { font-size: 26px; } }
</style>
