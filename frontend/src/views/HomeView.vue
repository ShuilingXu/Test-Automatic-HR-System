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
.job-card { position: relative; overflow: hidden; transition: transform 0.18s ease, box-shadow 0.18s ease; }
.job-card::after { position: absolute; right: -36px; top: -36px; width: 110px; height: 110px; content: ''; border-radius: 999px; background: rgba(215, 139, 51, 0.13); }
.job-card:hover { transform: translateY(-3px); box-shadow: 0 18px 42px rgba(16, 37, 50, 0.1); }
.job-badge { display: inline-flex; margin-bottom: 14px; padding: 6px 10px; border-radius: 999px; background: rgba(16, 37, 50, 0.08); color: #42515b; font-size: 12px; font-weight: 800; }
.job-meta { color: #0f6c8f; font-weight: 700; }
.job-desc { color: #61727d; line-height: 1.7; }
.job-card strong { display: inline-flex; margin-top: 8px; color: #102532; }
@media (max-width: 900px) { .topline { flex-direction: column; } }
</style>
