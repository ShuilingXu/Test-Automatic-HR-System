<template>
  <div class="page-shell console-shell">
    <aside class="console-side">
      <p class="page-eyebrow">Auto HR Console</p>
      <h1>人事管理测试台</h1>
      <p>用于直观测试 HR API，同时保留招聘、绩效、面试和测试者入口。</p>
      <nav>
        <button v-for="item in tabs" :key="item.key" :class="{ active: activeTab === item.key }" @click="activeTab = item.key">
          {{ item.label }}
        </button>
      </nav>
      <RouterLink class="side-link" to="/login">登录与会话预留</RouterLink>
      <RouterLink class="side-link" to="/candidate/register">测试者报名</RouterLink>
      <RouterLink class="side-link" to="/candidate/interview">线上面试</RouterLink>
      <RouterLink class="side-link" to="/interview/admin">面试系统管理</RouterLink>
    </aside>

    <main class="console-main">
      <section v-if="activeTab === 'dashboard'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Dashboard</p>
            <h2>HR 数据总览</h2>
          </div>
          <el-button type="primary" @click="loadAll">刷新</el-button>
        </div>
        <div class="metric-grid">
          <article v-for="item in metrics" :key="item.label" class="metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
      </section>

      <section v-if="activeTab === 'departments'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Departments</p>
            <h2>部门管理</h2>
          </div>
          <el-button @click="loadDepartments">刷新</el-button>
        </div>
        <el-form :model="departmentForm" label-position="top" class="form-grid">
          <el-form-item label="部门名称"><el-input v-model="departmentForm.departmentName" /></el-form-item>
          <el-form-item label="部门编码"><el-input v-model="departmentForm.departmentCode" /></el-form-item>
          <el-form-item label="上级部门">
            <el-select v-model="departmentForm.parentDepartmentId" clearable>
              <el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门负责人">
            <el-select v-model="departmentForm.managerEmployeeId" clearable>
              <el-option v-for="item in employees" :key="item.id" :label="item.fullName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门职能简介" class="wide"><el-input v-model="departmentForm.description" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveDepartment">保存部门</el-button>
        <div class="list-block">
          <button v-for="item in departments" :key="item.id" @click="loadDepartmentDetail(item.id)">
            <strong>{{ item.departmentName }}</strong><span>{{ item.managerEmployeeName || '未设置负责人' }}</span>
          </button>
        </div>
        <pre v-if="departmentDetail">{{ departmentDetail }}</pre>
      </section>

      <section v-if="activeTab === 'employees'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Employees</p>
            <h2>员工管理</h2>
          </div>
          <el-button @click="loadEmployees">刷新</el-button>
        </div>
        <el-form :model="employeeForm" label-position="top" class="form-grid">
          <el-form-item label="姓名"><el-input v-model="employeeForm.fullName" /></el-form-item>
          <el-form-item label="身份证号"><el-input v-model="employeeForm.idCardNo" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="employeeForm.mobilePhone" /></el-form-item>
          <el-form-item label="招聘专业"><el-input v-model="employeeForm.recruitmentMajor" /></el-form-item>
          <el-form-item label="岗位"><el-input v-model="employeeForm.positionName" /></el-form-item>
          <el-form-item label="直属部门">
            <el-select v-model="employeeForm.departmentId">
              <el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="员工状态">
            <el-select v-model="employeeForm.employmentStatus">
              <el-option label="待入职" :value="0" />
              <el-option label="已入职" :value="1" />
              <el-option label="停用" :value="2" />
              <el-option label="已离职" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="银行卡号"><el-input v-model="employeeForm.bankAccountNo" /></el-form-item>
          <el-form-item label="开户银行"><el-input v-model="employeeForm.bankName" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveEmployee">保存员工</el-button>
        <el-table :data="employees" stripe class="data-table">
          <el-table-column prop="fullName" label="姓名" />
          <el-table-column prop="departmentName" label="部门" />
          <el-table-column prop="positionName" label="岗位" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text @click="loadEmployeeDetail(scope.row.id)">详情</el-button></template></el-table-column>
        </el-table>
        <pre v-if="employeeDetail">{{ employeeDetail }}</pre>
      </section>

      <section v-if="activeTab === 'bindings'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Integrations</p>
            <h2>系统挂接</h2>
          </div>
          <el-button @click="loadBindings">刷新</el-button>
        </div>
        <el-form :model="bindingForm" label-position="top" class="form-grid">
          <el-form-item label="模块"><el-select v-model="bindingForm.moduleCode"><el-option label="招聘" value="RECRUITMENT" /><el-option label="绩效" value="PERFORMANCE" /><el-option label="面试" value="INTERVIEW" /></el-select></el-form-item>
          <el-form-item label="业务类型"><el-input v-model="bindingForm.businessType" /></el-form-item>
          <el-form-item label="员工"><el-select v-model="bindingForm.employeeId" clearable><el-option v-for="item in employees" :key="item.id" :label="item.fullName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="部门"><el-select v-model="bindingForm.departmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="外部引用"><el-input v-model="bindingForm.externalRef" /></el-form-item>
          <el-form-item label="状态"><el-input v-model="bindingForm.bindingStatus" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveBinding">保存挂接</el-button>
        <el-table :data="bindings" stripe class="data-table">
          <el-table-column prop="moduleCode" label="模块" />
          <el-table-column prop="businessType" label="业务类型" />
          <el-table-column prop="employeeName" label="员工" />
          <el-table-column prop="departmentName" label="部门" />
        </el-table>
      </section>

      <section v-if="activeTab === 'recruitment'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Recruitment</p>
            <h2>招聘岗位维护</h2>
          </div>
          <el-button @click="loadRecruitment">刷新</el-button>
        </div>
        <el-form :model="jobForm" label-position="top" class="form-grid">
          <el-form-item label="岗位名称"><el-input v-model="jobForm.jobTitle" /></el-form-item>
          <el-form-item label="岗位编码"><el-input v-model="jobForm.jobCode" /></el-form-item>
          <el-form-item label="招聘部门"><el-input v-model="jobForm.departmentName" /></el-form-item>
          <el-form-item label="工作地点"><el-input v-model="jobForm.workLocation" /></el-form-item>
          <el-form-item label="岗位类型"><el-input v-model="jobForm.jobType" /></el-form-item>
          <el-form-item label="招聘人数"><el-input-number v-model="jobForm.headcount" :min="1" /></el-form-item>
          <el-form-item label="薪资范围"><el-input v-model="jobForm.salaryRange" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="jobForm.status"><el-option label="开放" :value="1" /><el-option label="关闭" :value="0" /></el-select></el-form-item>
          <el-form-item label="岗位职责" class="wide"><el-input v-model="jobForm.responsibilities" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="任职要求" class="wide"><el-input v-model="jobForm.requirements" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <div class="action-row">
          <el-button type="primary" @click="saveJob">保存岗位</el-button>
          <el-button @click="resetJobForm">清空</el-button>
        </div>
        <el-table :data="jobs" stripe class="data-table">
          <el-table-column prop="jobTitle" label="岗位" min-width="140" />
          <el-table-column prop="departmentName" label="部门" min-width="120" />
          <el-table-column prop="headcount" label="人数" width="80" />
          <el-table-column prop="status" label="状态" width="90"><template #default="scope">{{ scope.row.status === 1 ? '开放' : '关闭' }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text @click="editJob(scope.row)">编辑</el-button></template></el-table-column>
        </el-table>
        <h3 class="sub-title">报名记录</h3>
        <el-table :data="candidates" stripe class="data-table">
          <el-table-column prop="fullName" label="姓名" min-width="100" />
          <el-table-column prop="jobTitle" label="岗位" min-width="140" />
          <el-table-column prop="mobilePhone" label="手机号" min-width="120" />
          <el-table-column prop="major" label="专业" min-width="120" />
          <el-table-column prop="resumeFileName" label="简历" min-width="140" />
          <el-table-column prop="applicationStatus" label="状态" width="110" />
        </el-table>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hrApi, recruitmentApi } from '../services/api'

const tabs = [
  { key: 'dashboard', label: '总览' },
  { key: 'departments', label: '部门' },
  { key: 'employees', label: '员工' },
  { key: 'bindings', label: '挂接' },
  { key: 'recruitment', label: '招聘' },
]

const activeTab = ref('dashboard')
const dashboard = reactive({ departmentCount: 0, employeeCount: 0, activeEmployeeCount: 0, pendingOnboardingCount: 0, recruitmentBindingCount: 0, performanceBindingCount: 0 })
const departments = ref([])
const employees = ref([])
const bindings = ref([])
const jobs = ref([])
const candidates = ref([])
const departmentDetail = ref(null)
const employeeDetail = ref(null)

const departmentForm = reactive({ departmentName: '', departmentCode: '', parentDepartmentId: null, managerEmployeeId: null, description: '' })
const employeeForm = reactive({ fullName: '', idCardNo: '', mobilePhone: '', recruitmentMajor: '', positionName: '', departmentId: null, employmentStatus: 1, bankAccountNo: '', bankName: '' })
const bindingForm = reactive({ moduleCode: 'RECRUITMENT', businessType: 'EMPLOYEE_SYNC', employeeId: null, departmentId: null, externalRef: '', bindingStatus: 'ACTIVE', payload: '{"source":"frontend-demo"}' })
const jobForm = reactive({ id: null, jobTitle: '', jobCode: '', departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', status: 1 })

const metrics = computed(() => [
  { label: '部门', value: dashboard.departmentCount },
  { label: '员工', value: dashboard.employeeCount },
  { label: '在职', value: dashboard.activeEmployeeCount },
  { label: '待入职', value: dashboard.pendingOnboardingCount },
  { label: '招聘挂接', value: dashboard.recruitmentBindingCount },
  { label: '绩效挂接', value: dashboard.performanceBindingCount },
])

function fail(error) { ElMessage.error(error.message || '请求失败') }
async function loadDashboard() { try { Object.assign(dashboard, (await hrApi.getDashboard()).data) } catch (error) { fail(error) } }
async function loadDepartments() { try { departments.value = (await hrApi.listDepartments()).data } catch (error) { fail(error) } }
async function loadEmployees() { try { employees.value = (await hrApi.listEmployees()).data } catch (error) { fail(error) } }
async function loadBindings() { try { bindings.value = (await hrApi.listBindings()).data } catch (error) { fail(error) } }
async function loadRecruitment() { try { jobs.value = (await recruitmentApi.listAdminJobs()).data; candidates.value = (await recruitmentApi.listCandidates()).data } catch (error) { fail(error) } }
async function loadAll() { await Promise.all([loadDashboard(), loadDepartments(), loadEmployees(), loadBindings(), loadRecruitment()]) }
async function loadDepartmentDetail(id) { try { departmentDetail.value = (await hrApi.getDepartmentDetail(id)).data } catch (error) { fail(error) } }
async function loadEmployeeDetail(id) { try { employeeDetail.value = (await hrApi.getEmployeeDetail(id)).data } catch (error) { fail(error) } }
async function saveDepartment() { try { await hrApi.saveDepartment({ ...departmentForm }); ElMessage.success('部门已保存'); await loadAll() } catch (error) { fail(error) } }
async function saveEmployee() { try { await hrApi.saveEmployee({ ...employeeForm }); ElMessage.success('员工已保存'); await loadAll() } catch (error) { fail(error) } }
async function saveBinding() { try { await hrApi.saveBinding({ ...bindingForm }); ElMessage.success('挂接已保存'); await loadAll() } catch (error) { fail(error) } }
function resetJobForm() { Object.assign(jobForm, { id: null, jobTitle: '', jobCode: '', departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', status: 1 }) }
function editJob(row) { Object.assign(jobForm, row) }
async function saveJob() { try { await recruitmentApi.saveJob({ ...jobForm }); ElMessage.success('招聘岗位已保存'); resetJobForm(); await loadRecruitment() } catch (error) { fail(error) } }

onMounted(loadAll)
</script>

<style scoped>
.console-shell { display: grid; grid-template-columns: 300px 1fr; gap: 24px; }
.console-side { background: #102532; color: #f4efe7; border-radius: 28px; padding: 26px; display: flex; flex-direction: column; gap: 16px; min-height: calc(100vh - 56px); }
.console-side h1 { margin: 0; line-height: 1.1; }
.console-side p { color: rgba(244, 239, 231, 0.76); line-height: 1.7; }
.console-side nav { display: grid; gap: 10px; }
.console-side button, .side-link { border: 1px solid rgba(240, 182, 111, 0.18); background: rgba(255,255,255,0.06); color: inherit; border-radius: 16px; padding: 12px 14px; text-align: left; text-decoration: none; cursor: pointer; }
.console-side button.active { background: rgba(240, 182, 111, 0.22); }
.console-main { display: grid; gap: 18px; }
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 20px; }
.topline h2 { margin: 6px 0 0; }
.metric-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 14px; }
.metric { background: #f8f5ef; border-radius: 18px; padding: 18px; }
.metric span { display: block; color: #6d7a83; margin-bottom: 8px; }
.metric strong { font-size: 30px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.wide { grid-column: 1 / -1; }
.list-block { display: grid; gap: 10px; margin-top: 18px; }
.list-block button { border: 0; border-radius: 16px; padding: 14px; background: #f8f5ef; display: flex; justify-content: space-between; cursor: pointer; }
.action-row { display: flex; gap: 12px; margin-top: 4px; }
.sub-title { margin: 24px 0 0; }
.data-table { margin-top: 18px; }
pre { white-space: pre-wrap; background: #102532; color: #f4efe7; border-radius: 18px; padding: 16px; max-height: 300px; overflow: auto; }
@media (max-width: 980px) { .console-shell { grid-template-columns: 1fr; } .metric-grid, .form-grid { grid-template-columns: 1fr; } }
</style>
