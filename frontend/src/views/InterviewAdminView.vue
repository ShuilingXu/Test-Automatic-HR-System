<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Interview Admin</p>
      <h1 class="page-title">面试系统管理入口原型</h1>
      <p class="page-subtitle">预留面试批次、题库、候选人、评分和与 HR 员工档案挂接的管理入口。</p>
      <div class="page-grid">
        <div v-for="item in cards" :key="item.title" class="surface">
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
          <strong>{{ item.api }}</strong>
        </div>
      </div>
      <div class="link-row">
        <RouterLink class="link-chip" to="/">返回 HR 管理台</RouterLink>
        <RouterLink class="link-chip" to="/candidate/interview">查看线上面试页</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'

const cards = [
  { title: '面试批次', desc: '创建批次、排期、候选人分组。', api: '/api/interview/batches' },
  { title: '题库管理', desc: '维护题目、标签、难度和评分规则。', api: '/api/interview/questions' },
  { title: '候选人进度', desc: '查看报名、答题、评分和结果状态。', api: '/api/interview/candidates' },
  { title: 'HR 挂接', desc: '面试通过后关联 HR 员工或待入职人员。', api: '/api/hr/bindings?moduleCode=INTERVIEW' },
]
</script>
