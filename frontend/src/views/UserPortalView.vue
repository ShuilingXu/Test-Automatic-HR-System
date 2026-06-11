<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interviewee Portal</p>
          <h1 class="page-title">面试者门户</h1>
        </div>
        <div class="link-row">
          <RouterLink
            class="link-chip"
            :class="{ active: activePage === 'profile' }"
            to="/user/profile"
            >个人资料</RouterLink
          >
          <RouterLink
            class="link-chip"
            :class="{ active: activePage === 'applications' }"
            to="/user/applications"
            >报名记录</RouterLink
          >
          <RouterLink class="link-chip" to="/candidate/register"
            >去报名</RouterLink
          >
          <el-button @click="logout">退出登录</el-button>
        </div>
      </div>
      <div v-if="activePage === 'profile'" class="page-grid">
        <el-form :model="profileForm" label-position="top" class="surface">
          <h3>个人资料</h3>
          <el-form-item label="姓名"
            ><el-input v-model="profileForm.displayName"
          /></el-form-item>
          <el-form-item label="手机号"
            ><el-input v-model="profileForm.mobilePhone"
          /></el-form-item>
          <el-form-item label="邮箱"
            ><el-input v-model="profileForm.email"
          /></el-form-item>
          <el-button type="primary" @click="saveProfile">保存资料</el-button>
        </el-form>
        <div class="surface">
          <h3>当前会话</h3>
          <div class="kv-grid">
            <div>
              <span>用户名</span><strong>{{ session.username || "-" }}</strong>
            </div>
            <div>
              <span>角色</span><strong>{{ session.roleCode || "-" }}</strong>
            </div>
            <div>
              <span>资料完成</span
              ><strong>{{
                session.profileCompleted === 1 ? "是" : "否"
              }}</strong>
            </div>
            <div>
              <span>邮箱</span><strong>{{ session.email || "-" }}</strong>
            </div>
          </div>
        </div>
      </div>
      <div
        v-if="activePage === 'applications'"
        class="surface application-panel"
      >
        <div class="section-head">
          <div>
            <h3>我的报名记录</h3>
          </div>
          <el-button @click="loadMyCandidates">刷新</el-button>
        </div>
        <div v-if="candidates.length === 0" class="empty-box">
          暂无报名记录，完成报名后会显示在这里。
        </div>
        <div v-else class="application-list">
          <div
            v-for="item in candidates"
            :key="item.id"
            class="application-card"
            :class="{ selected: selectedCandidate?.id === item.id }"
            @click="openApplication(item)"
          >
            <div>
              <strong>{{ item.jobTitle || `岗位 ${item.jobId}` }}</strong>
              <span
                >报名编号：{{ item.id }} / 状态：{{
                  item.interviewStageStatus ||
                  item.applicationStatus ||
                  "已提交"
                }}</span
              >
              <small>简历：{{ item.resumeFileName || "未上传" }}</small>
            </div>
            <RouterLink
              v-if="item.interviewProcessId"
              class="link-chip"
              :to="`/interview/interviewee/processes/${item.interviewProcessId}`"
              @click.stop
              >进入面试</RouterLink
            >
            <span v-else class="pending-chip">等待 HR 发起面试</span>
          </div>
        </div>
        <div v-if="selectedCandidate" class="application-detail">
          <h3>报名记录详情</h3>
          <div class="kv-grid">
            <div>
              <span>报名编号</span><strong>{{ selectedCandidate.id }}</strong>
            </div>
            <div>
              <span>岗位</span
              ><strong>{{
                selectedCandidate.jobTitle || selectedCandidate.jobId
              }}</strong>
            </div>
            <div>
              <span>状态</span
              ><strong>{{
                selectedCandidate.interviewStageStatus ||
                selectedCandidate.applicationStatus ||
                "-"
              }}</strong>
            </div>
            <div>
              <span>流程流水号</span
              ><strong>{{
                selectedCandidate.interviewProcessId || "-"
              }}</strong>
            </div>
            <div>
              <span>简历</span
              ><strong>{{
                selectedCandidate.resumeFileName || "未上传"
              }}</strong>
            </div>
            <div>
              <span>投递时间</span
              ><strong>{{ selectedCandidate.createdAt || "-" }}</strong>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi, recruitmentApi } from "../services/api";

const router = useRouter();
const route = useRoute();
const activePage = computed(() => route.meta.portalPage || "applications");
const session = reactive({
  username: "",
  roleCode: "",
  email: "",
  profileCompleted: 0,
});
const profileForm = reactive({ displayName: "", mobilePhone: "", email: "" });
const candidates = ref([]);
const selectedCandidate = ref(null);

async function loadSession() {
  try {
    const response = await authApi.getSession();
    Object.assign(session, response.data);
    Object.assign(profileForm, {
      displayName: response.data.displayName || "",
      mobilePhone: response.data.mobilePhone || "",
      email: response.data.email || "",
    });
    await loadMyCandidates();
  } catch (error) {
    ElMessage.error(error.message || "请先登录");
    router.push("/login");
  }
}

async function loadMyCandidates() {
  try {
    candidates.value = (await recruitmentApi.listMyCandidates()).data;
    syncRouteState();
  } catch (error) {
    ElMessage.error(error.message || "报名记录加载失败");
  }
}

async function saveProfile() {
  try {
    const response = await authApi.updateProfile({ ...profileForm });
    Object.assign(session, response.data);
    ElMessage.success("资料已保存");
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  }
}

async function logout() {
  await authApi.logout();
  router.push("/login");
}

function openApplication(item) {
  selectedCandidate.value = item;
  router.push(`/user/applications/${item.id}`);
}

function syncRouteState() {
  const id = Number(route.params.id);
  selectedCandidate.value = id
    ? candidates.value.find((item) => item.id === id) || null
    : null;
}

onMounted(loadSession);
watch(() => route.fullPath, syncRouteState);
</script>

<style scoped>
.application-panel {
  margin-top: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-head h3 {
  margin: 0;
}

.application-list {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.application-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(17, 49, 69, 0.06);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.application-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(16, 37, 50, 0.08);
  border-color: rgba(17, 49, 69, 0.12);
}

.application-card.selected {
  outline: 2px solid rgba(15, 108, 143, 0.35);
  border-color: transparent;
}

.application-detail {
  margin-top: 18px;
  padding: 20px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(17, 49, 69, 0.06);
}

.application-detail h3 {
  margin-top: 0;
  margin-bottom: 14px;
}

.application-card strong,
.application-card span,
.application-card small {
  display: block;
}

.application-card strong {
  font-size: 15px;
  margin-bottom: 4px;
}

.application-card span {
  margin: 6px 0;
  color: #61727d;
  font-size: 14px;
}

.application-card small {
  color: var(--text-secondary);
  font-size: 13px;
}

.pending-chip {
  padding: 8px 14px;
  border-radius: 999px;
  background: #eef2f4;
  color: #61727d;
  white-space: nowrap;
  font-size: 13px;
}

@media (max-width: 900px) {
  .topline {
    flex-direction: column;
  }

  .section-head,
  .application-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .application-card .link-chip {
    margin-top: 8px;
  }
}
</style>
