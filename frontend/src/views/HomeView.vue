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
          <h3>{{ item.jobTitle }}</h3>
          <p class="job-meta">
            {{ item.departmentName }} / {{ item.workLocation || "地点待定" }}
          </p>
          <p class="job-requirements">{{ item.requirements }}</p>
          <strong class="job-salary">{{
            item.salaryRange || "薪资面议"
          }}</strong>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { ElMessage } from "element-plus";
import { recruitmentApi } from "../services/api";

const jobs = ref([]);
async function loadJobs() {
  try {
    jobs.value = (await recruitmentApi.listOpenJobs()).data;
  } catch (error) {
    ElMessage.error(error.message || "加载岗位失败");
  }
}
onMounted(loadJobs);
</script>

<style scoped>
.job-card {
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.job-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(16, 37, 50, 0.1);
}

.job-card h3 {
  margin-top: 0;
  margin-bottom: 8px;
}

.job-meta {
  color: #61727d;
  margin: 8px 0;
  font-size: 14px;
}

.job-requirements {
  color: #42515b;
  margin: 10px 0;
  line-height: 1.6;
  font-size: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.job-salary {
  display: block;
  margin-top: 12px;
  color: var(--brand-accent);
  font-size: 16px;
}
</style>
