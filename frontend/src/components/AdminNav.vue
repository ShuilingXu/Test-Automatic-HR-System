<template>
  <nav class="admin-nav" aria-label="管理后台导航">
    <div class="admin-nav-inner">
      <RouterLink class="admin-brand" to="/admin/dashboard" aria-label="Auto HR 仪表盘">
        <span class="brand-mark">AH</span>
        <span class="brand-name">Auto HR</span>
      </RouterLink>
      <div class="admin-nav-links">
        <RouterLink v-for="item in visibleItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
      </div>
      <div class="admin-nav-actions">
        <span v-if="sessionUser" class="admin-user">{{ sessionUser.displayName || sessionUser.username }}</span>
        <button type="button" class="logout-control" @click="logout">退出登录</button>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../services/api'
import { readSessionUser } from '../utils/session'

const router = useRouter()
const sessionUser = ref(readSessionUser())
const privileged = computed(() => ['IT_ADMIN', 'HR_ADMIN'].includes(sessionUser.value?.roleCode))
const visibleItems = computed(() => {
  const items = [
    { label: '仪表盘', to: '/admin/dashboard' },
    { label: '员工管理', to: '/admin/employees' },
    { label: '部门管理', to: '/admin/departments' },
    { label: '招聘管理', to: '/admin/recruitment/jobs' },
    { label: '薪资计算', to: '/admin/payroll' },
    { label: '员工统计', to: '/admin/statistics' },
  ]
  if (privileged.value) {
    items.push(
      { label: '用户管理', to: '/admin/users' },
      { label: '审计日志', to: '/admin/audit' },
      { label: '内容管理', to: '/admin/content' },
    )
  }
  items.push({ label: '面试流程', to: '/interview/hr/processes' })
  return items
})

async function logout() {
  try {
    await authApi.logout()
  } finally {
    router.push('/login')
  }
}
</script>

<style scoped>
.admin-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 1px 8px rgba(23, 33, 31, 0.04);
}
.admin-nav-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: 1440px;
  min-width: 0;
  margin: 0 auto;
  padding: 0 28px;
}
.admin-brand {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 9px;
  color: var(--ink);
  text-decoration: none;
  font-weight: 800;
  letter-spacing: 0.01em;
}
.brand-mark {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
  background: var(--primary);
  color: #fff;
  font-size: 11px;
  letter-spacing: 0.04em;
}
.admin-nav-links {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: thin;
}
.admin-nav-links a {
  flex: 0 0 auto;
  padding: 16px 11px 13px;
  border-bottom: 3px solid transparent;
  color: var(--text-muted);
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
}
.admin-nav-links a:hover {
  color: var(--primary);
  background: var(--primary-soft);
}
.admin-nav-links a.router-link-active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}
.admin-nav-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}
.admin-user {
  max-width: 120px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logout-control {
  min-height: 34px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink-soft);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
.logout-control:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary);
}
@media (max-width: 900px) {
  .admin-nav-inner { gap: 10px; padding: 0 14px; }
  .brand-name { display: none; }
  .admin-nav-links a { padding-inline: 9px; }
}
@media (max-width: 560px) {
  .admin-user { display: none; }
  .admin-nav-actions { gap: 0; }
  .logout-control { padding-inline: 8px; }
}
</style>
