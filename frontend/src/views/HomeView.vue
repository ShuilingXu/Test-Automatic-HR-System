<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Recruitment Home</p>
          <h1 class="page-title">招聘信息首页</h1>
          <p class="page-subtitle">统一展示开放招聘岗位。管理员入口在 `/admin`，面试者登录后进入 `/user`。</p>
        </div>
        <div class="link-row">
          <RouterLink class="link-chip" to="/login">统一登录</RouterLink>
          <RouterLink class="link-chip" to="/admin">管理员门户</RouterLink>
          <RouterLink class="link-chip" to="/user">面试者门户</RouterLink>
        </div>
      </div>
      <div class="page-grid">
        <div class="surface" v-for="item in jobs" :key="item.id">
          <h3>{{ item.jobTitle }}</h3>
          <p>{{ item.departmentName }} / {{ item.workLocation || '地点待定' }}</p>
          <p>{{ item.requirements }}</p>
          <strong>{{ item.salaryRange || '薪资面议' }}</strong>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { recruitmentApi } from '../services/api'

const jobs = ref([])
async function loadJobs() {
  try { jobs.value = (await recruitmentApi.listOpenJobs()).data } catch (error) { ElMessage.error(error.message || '加载岗位失败') }
}
onMounted(loadJobs)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
@media (max-width: 900px) { .topline { flex-direction: column; } }
</style>
