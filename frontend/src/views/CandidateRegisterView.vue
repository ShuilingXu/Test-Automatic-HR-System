<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Candidate</p>
      <h1 class="page-title">测试者报名</h1>
      <p class="page-subtitle">登录并完成个人信息后，选择开放岗位，填写报名表并上传简历。</p>
      <div class="page-grid">
        <el-form :model="form" label-position="top" class="surface">
          <h3>报名信息</h3>
          <el-form-item label="应聘岗位">
            <el-select v-model="form.jobId" placeholder="选择开放岗位">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobTitle} / ${item.departmentName}`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.fullName" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.mobilePhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="身份证号"><el-input v-model="form.idCardNo" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="学历"><el-input v-model="form.educationLevel" /></el-form-item>
          <el-form-item label="毕业院校"><el-input v-model="form.graduationSchool" /></el-form-item>
          <el-form-item label="工作年限"><el-input-number v-model="form.yearsOfExperience" :min="0" /></el-form-item>
          <el-form-item label="期望薪资"><el-input v-model="form.expectedSalary" /></el-form-item>
          <el-form-item label="自我介绍"><el-input v-model="form.selfIntroduction" type="textarea" :rows="4" /></el-form-item>
          <el-upload :auto-upload="false" :limit="1" :on-change="pickResume" :on-remove="removeResume">
            <el-button>选择简历文件</el-button>
            <template #tip><div class="upload-tip">支持 PDF、Word、图片等常见简历文件，后端当前限制 20MB。</div></template>
          </el-upload>
          <div class="link-row">
            <el-button type="primary" @click="submit">提交报名并上传简历</el-button>
            <RouterLink class="link-chip" to="/candidate/interview">进入线上面试</RouterLink>
          </div>
        </el-form>
        <div class="surface">
          <h3>开放岗位</h3>
          <div class="job-list">
            <button v-for="item in jobs" :key="item.id" @click="form.jobId = item.id">
              <strong>{{ item.jobTitle }}</strong>
              <span>{{ item.departmentName }} / {{ item.workLocation || '地点待定' }}</span>
              <small>{{ item.salaryRange || '薪资面议' }}</small>
            </button>
          </div>
          <div v-if="submittedCandidate" class="result-box">
            <h3>报名成功</h3>
            <p>报名编号：{{ submittedCandidate.id }}</p>
            <p>应聘岗位：{{ submittedCandidate.jobTitle }}</p>
            <p>简历：{{ submittedCandidate.resumeFileName || '未上传' }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, recruitmentApi } from '../services/api'

const router = useRouter()
const jobs = ref([])
const resumeFile = ref(null)
const submittedCandidate = ref(null)
const form = reactive({ jobId: null, fullName: '', mobilePhone: '', email: '', idCardNo: '', major: '', educationLevel: '', graduationSchool: '', yearsOfExperience: 0, expectedSalary: '', selfIntroduction: '' })

function fail(error) { ElMessage.error(error.message || '操作失败') }
function pickResume(uploadFile) { resumeFile.value = uploadFile.raw }
function removeResume() { resumeFile.value = null }
async function loadJobs() { try { jobs.value = (await recruitmentApi.listOpenJobs()).data } catch (error) { fail(error) } }
async function loadSession() {
  try {
    const response = await authApi.getSession()
    const user = response.data
    if (user.roleCode !== 'INTERVIEWEE') {
      ElMessage.error('仅面试者可以报名')
      router.push('/login')
      return
    }
    if (user.profileCompleted !== 1) {
      ElMessage.warning('请先在用户门户完善信息')
      router.push('/user')
      return
    }
    form.fullName = user.displayName || ''
    form.mobilePhone = user.mobilePhone || ''
    form.email = user.email || ''
    localStorage.setItem('session-user', JSON.stringify(user))
  } catch (error) {
    fail(error)
    router.push('/login')
  }
}
async function submit() {
  try {
    const applyResponse = await recruitmentApi.apply({ ...form })
    let candidate = applyResponse.data
    if (resumeFile.value) {
      const resumeResponse = await recruitmentApi.uploadResume(candidate.id, resumeFile.value)
      candidate = { ...candidate, resumeFileId: resumeResponse.data.id, resumeFileName: resumeResponse.data.originalFileName }
    }
    submittedCandidate.value = candidate
    ElMessage.success('报名已提交')
  } catch (error) { fail(error) }
}

onMounted(async () => {
  await loadSession()
  await loadJobs()
})
</script>

<style scoped>
.job-list { display: grid; gap: 12px; }
.job-list button { border: 0; border-radius: 16px; padding: 14px; text-align: left; background: rgba(255, 255, 255, 0.82); cursor: pointer; }
.job-list strong, .job-list span, .job-list small { display: block; }
.job-list span { margin: 6px 0; color: #61727d; }
.upload-tip { color: #6d7a83; margin-top: 8px; }
.result-box { margin-top: 20px; padding: 16px; border-radius: 18px; background: #102532; color: #f4efe7; }
</style>
