<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Recruitment Home</p>
          <h1 class="page-title">招聘信息首页</h1>
          <p class="page-subtitle">统一展示开放招聘岗位。请通过统一登录/注册进入管理员门户或面试者门户。</p>
        </div>
        <div class="link-row">
          <RouterLink class="link-chip" to="/login">登录 / 注册</RouterLink>
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
