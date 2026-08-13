<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Recruitment Home</p>
          <h1 class="page-title">千早爱音信息科技人事管理系统</h1>
        </div>
        <div class="link-row">
          <RouterLink class="link-chip" to="/login">登录 / 注册</RouterLink>
        </div>
      </div>
      <div class="page-grid">
        <div class="surface job-card" v-for="item in jobs" :key="item.id">
          <span class="job-badge">{{ item.departmentName }}</span>
          <h3>{{ item.jobTitle }}</h3>
          <p class="job-meta">{{ item.workLocation || '地点待定' }}</p>
          <p class="job-desc">{{ item.requirements }}</p>
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
.job-card { position: relative; overflow: hidden; transition: transform 0.18s ease, box-shadow 0.18s ease; cursor: pointer; }
.job-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-hover); border-color: var(--primary); }
.job-badge { display: inline-flex; margin-bottom: 14px; padding: 5px 10px; border-radius: 999px; background: var(--primary-soft); color: var(--primary); font-size: 12px; font-weight: 700; }
.job-meta { color: var(--primary); font-weight: 600; }
.job-desc { color: var(--text-muted); line-height: 1.7; }
.job-card strong { display: inline-flex; margin-top: 8px; color: var(--ink); font-weight: 700; }
@media (max-width: 900px) { .topline { flex-direction: column; } }
</style>
