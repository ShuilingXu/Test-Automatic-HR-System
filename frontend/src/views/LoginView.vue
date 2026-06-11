<template>
  <div class="page-shell">
    <section class="page-card auth-card">
      <p class="page-eyebrow">Unified Login</p>
      <h1 class="page-title">统一登录</h1>
      <div class="page-grid">
        <el-form
          :model="loginForm"
          label-position="top"
          class="surface login-form"
          autocomplete="off"
        >
          <h3>登录</h3>
          <el-form-item label="用户名"
            ><el-input v-model="loginForm.username" autocomplete="off"
          /></el-form-item>
          <el-form-item label="密码"
            ><el-input
              v-model="loginForm.password"
              type="password"
              show-password
              autocomplete="new-password"
          /></el-form-item>
          <div class="link-row">
            <el-button type="primary" @click="login">登录</el-button>
            <RouterLink class="link-chip" to="/">返回首页</RouterLink>
          </div>
        </el-form>
        <el-form
          :model="registerForm"
          label-position="top"
          class="surface login-form"
        >
          <h3>面试者注册</h3>
          <el-form-item label="用户名"
            ><el-input v-model="registerForm.username"
          /></el-form-item>
          <el-form-item label="密码"
            ><el-input
              v-model="registerForm.password"
              type="password"
              show-password
          /></el-form-item>
          <el-form-item label="姓名"
            ><el-input v-model="registerForm.displayName"
          /></el-form-item>
          <el-form-item label="手机号"
            ><el-input v-model="registerForm.mobilePhone"
          /></el-form-item>
          <el-form-item label="邮箱"
            ><el-input v-model="registerForm.email"
          /></el-form-item>
          <el-button type="primary" @click="register">注册面试者</el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive } from "vue";
import { useRouter, RouterLink } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi } from "../services/api";

const router = useRouter();
const loginForm = reactive({ username: "", password: "" });
const registerForm = reactive({
  username: "",
  password: "",
  displayName: "",
  mobilePhone: "",
  email: "",
});

function targetByRole(roleCode) {
  return roleCode === "INTERVIEWEE" ? "/user" : "/admin";
}

async function login() {
  try {
    const response = await authApi.login({ ...loginForm });
    localStorage.setItem("demo-token", response.data.token);
    localStorage.setItem("session-user", JSON.stringify(response.data.user));
    ElMessage.success("登录成功");
    router.push(targetByRole(response.data.user.roleCode));
  } catch (error) {
    ElMessage.error(error.message || "登录失败");
  }
}

async function register() {
  try {
    await authApi.register({ ...registerForm });
    ElMessage.success("注册成功，请登录后完善信息");
    loginForm.username = registerForm.username;
    loginForm.password = registerForm.password;
  } catch (error) {
    ElMessage.error(error.message || "注册失败");
  }
}
</script>

<style scoped>
.auth-card {
  max-width: 1080px;
}
.login-form {
  margin-top: 12px;
}
</style>
