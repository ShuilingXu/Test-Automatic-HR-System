<template>
  <nav class="admin-nav" aria-label="管理后台导航">
    <RouterLink to="/admin/dashboard">仪表盘</RouterLink>
    <RouterLink to="/admin/employees">员工管理</RouterLink>
    <RouterLink to="/admin/payroll">薪资计算</RouterLink>
    <RouterLink to="/admin/statistics">员工统计</RouterLink>
    <RouterLink to="/admin/recruitment/jobs">招聘管理</RouterLink>
    <RouterLink to="/interview/hr/processes">面试流程</RouterLink>
  </nav>
</template>

<style scoped>
.admin-nav { display: flex; gap: 4px; overflow-x: auto; padding: 10px max(16px, calc((100vw - 1440px) / 2)); border-bottom: 1px solid var(--border); background: #fff; }
.admin-nav a { flex: 0 0 auto; padding: 8px 12px; color: var(--text-muted); text-decoration: none; border-bottom: 2px solid transparent; }
.admin-nav a.router-link-active { color: #176e60; border-bottom-color: #176e60; }
</style>
