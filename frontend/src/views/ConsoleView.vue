<template>
  <div class="page-shell console-shell">
    <aside class="console-side">
      <p class="page-eyebrow">Auto HR Console</p>
      <h1>管理后台</h1>
      <p>用于直观测试 HR、招聘、面试与鉴权系统。用户管理中心仅 IT 管理员可见。</p>
      <nav>
        <button v-for="item in tabs" :key="item.key" :class="{ active: activeTab === item.key }" @click="activeTab = item.key">
          {{ item.label }}
        </button>
      </nav>
      <el-button class="side-link logout-btn" @click="logout">退出登录</el-button>
      <RouterLink class="side-link" to="/interview/hr">线上面试</RouterLink>
      <RouterLink class="side-link" to="/interview/hr">面试系统管理</RouterLink>
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

      <section v-if="activeTab === 'audit'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Audit</p>
            <h2>审计日志</h2>
          </div>
          <el-button @click="loadAuditLogs">刷新</el-button>
        </div>
        <el-form :model="auditFilter" label-position="top" class="filter-grid">
          <el-form-item label="日志分类"><el-select v-model="auditFilter.moduleCode" clearable><el-option label="后台管理日志" value="ADMIN" /><el-option label="面试流程日志" value="INTERVIEW" /><el-option label="报名者投递日志" value="RECRUITMENT" /></el-select></el-form-item>
          <el-form-item label="动作"><el-input v-model="auditFilter.actionCode" placeholder="如 审批AI / 投递报名 / 重置密码" /></el-form-item>
          <el-form-item label="关键词"><el-input v-model="auditFilter.keyword" placeholder="操作人 / 对象ID / 详情" /></el-form-item>
          <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadAuditLogs">查询</el-button><el-button @click="resetAuditFilter">重置</el-button></div></el-form-item>
        </el-form>
        <div class="audit-grid">
          <article v-for="group in auditGroups" :key="group.key" class="audit-panel">
            <div class="audit-panel-head">
              <h3>{{ group.title }}</h3>
              <span>{{ group.items.length }} 条</span>
            </div>
            <el-table :data="group.items" stripe class="data-table compact-table" max-height="520">
              <el-table-column prop="operatorUsername" label="操作人" min-width="100" />
              <el-table-column label="角色" min-width="90"><template #default="scope">{{ localizeRole(scope.row.operatorRoleCode) }}</template></el-table-column>
              <el-table-column label="动作" min-width="150"><template #default="scope">{{ localizeAction(scope.row.actionCode) }}</template></el-table-column>
              <el-table-column label="对象类型" min-width="130"><template #default="scope">{{ localizeTarget(scope.row.targetType) }}</template></el-table-column>
              <el-table-column prop="targetId" label="对象ID" width="90" />
              <el-table-column label="详情" min-width="180"><template #default="scope">{{ localizeDetail(scope.row.detail) }}</template></el-table-column>
              <el-table-column prop="createdAt" label="时间" min-width="165" />
            </el-table>
          </article>
        </div>
      </section>

      <section v-if="activeTab === 'users'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Users</p>
            <h2>用户管理中心</h2>
          </div>
          <el-button @click="loadUsers">刷新</el-button>
        </div>
        <el-table :data="users" stripe class="data-table" @row-click="editUser">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="displayName" label="姓名" />
          <el-table-column prop="roleCode" label="角色" />
          <el-table-column prop="mobilePhone" label="电话" />
          <el-table-column prop="status" label="状态"><template #default="scope">{{ scope.row.status === 1 ? '启用' : '停用' }}</template></el-table-column>
        </el-table>
        <el-form :model="userForm" label-position="top" class="form-grid">
          <el-form-item label="用户名"><el-input v-model="userForm.username" disabled /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item>
          <el-form-item label="角色"><el-select v-model="userForm.roleCode"><el-option v-if="isItAdmin" label="IT管理员" value="IT_ADMIN" /><el-option v-if="isItAdmin" label="HR管理员" value="HR_ADMIN" /><el-option label="HR用户" value="HR_USER" /><el-option label="面试者" value="INTERVIEWEE" /></el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="userForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
          <el-form-item label="手机号"><el-input v-model="userForm.mobilePhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="userForm.newPassword" type="password" show-password placeholder="至少6位，仅重置时填写" /></el-form-item>
        </el-form>
        <div class="action-row"><el-button type="primary" @click="saveUser">保存用户</el-button><el-button type="warning" @click="resetUserPassword">重置密码</el-button></div>
      </section>

      <section v-if="activeTab === 'departments'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Departments</p>
            <h2>部门管理</h2>
          </div>
          <el-button @click="loadDepartments">刷新</el-button>
        </div>
        <div class="sub-tabs">
          <button :class="{ active: departmentMode === 'create' }" @click="showCreateDepartment">新增部门</button>
          <button :class="{ active: departmentMode === 'query' }" @click="departmentMode = 'query'">查询部门</button>
          <button :class="{ active: departmentMode === 'edit' }" @click="departmentMode = 'edit'">修改部门</button>
        </div>
        <template v-if="departmentMode === 'create' || departmentMode === 'edit'">
          <el-form :model="departmentForm" label-position="top" class="form-grid">
            <el-form-item label="部门名称"><el-input v-model="departmentForm.departmentName" /></el-form-item>
            <el-form-item label="部门编码"><el-input v-model="departmentForm.departmentCode" /></el-form-item>
            <el-form-item label="上级部门"><el-select v-model="departmentForm.parentDepartmentId" clearable placeholder="不能选择本部门"><el-option v-for="item in availableParentDepartments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门负责人"><el-select v-model="departmentForm.managerEmployeeId" clearable filterable placeholder="从员工列表选择负责人"><el-option v-for="item in employees" :key="item.id" :label="item.fullName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门状态"><el-select v-model="departmentForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="排序"><el-input-number v-model="departmentForm.sortOrder" :min="0" /></el-form-item>
            <el-form-item label="部门职能简介" class="wide"><el-input v-model="departmentForm.description" type="textarea" :rows="3" /></el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" @click="saveDepartment">{{ departmentMode === 'create' ? '新增部门' : '保存修改' }}</el-button><el-button @click="resetDepartmentForm">清空</el-button></div>
        </template>
        <template v-if="departmentMode === 'query'">
          <el-form :model="departmentFilter" label-position="top" class="filter-grid">
            <el-form-item label="上级部门"><el-select v-model="departmentFilter.parentDepartmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门状态"><el-select v-model="departmentFilter.status" clearable><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="departmentFilter.keyword" placeholder="部门名称 / 编码 / 简介" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadDepartments">查询</el-button><el-button @click="resetDepartmentFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="departments" stripe class="data-table" @row-click="editDepartment">
            <el-table-column prop="departmentName" label="部门名称" min-width="140" />
            <el-table-column prop="departmentCode" label="部门编码" min-width="120" />
            <el-table-column prop="parentDepartmentName" label="上级部门" min-width="120" />
            <el-table-column prop="managerEmployeeName" label="部门负责人" min-width="120" />
            <el-table-column prop="description" label="部门职能简介" min-width="180" />
            <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteDepartment(scope.row.id)">删除</el-button></template></el-table-column>
          </el-table>
        </template>
      </section>

      <section v-if="activeTab === 'employees'" class="page-card">
        <div class="topline"><div><p class="page-eyebrow">Employees</p><h2>员工管理</h2></div><el-button @click="loadEmployees">刷新</el-button></div>
        <div class="sub-tabs"><button :class="{ active: employeeMode === 'create' }" @click="showCreateEmployee">新增员工</button><button :class="{ active: employeeMode === 'query' }" @click="employeeMode = 'query'">查询员工</button><button :class="{ active: employeeMode === 'edit' }" @click="employeeMode = 'edit'">修改员工</button></div>
        <template v-if="employeeMode === 'create' || employeeMode === 'edit'">
          <el-form :model="employeeForm" label-position="top" class="form-grid">
            <el-form-item label="工号"><el-input v-model="employeeForm.employeeCode" placeholder="查询和修改时作为业务主键" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="employeeForm.fullName" /></el-form-item>
            <el-form-item label="身份证号"><el-input v-model="employeeForm.idCardNo" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="employeeForm.mobilePhone" /></el-form-item>
            <el-form-item label="招聘专业"><el-input v-model="employeeForm.recruitmentMajor" /></el-form-item>
            <el-form-item label="岗位"><el-input v-model="employeeForm.positionName" /></el-form-item>
            <el-form-item label="直属部门"><el-select v-model="employeeForm.departmentId"><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="员工状态"><el-select v-model="employeeForm.employmentStatus"><el-option label="待入职" :value="0" /><el-option label="已入职" :value="1" /><el-option label="停用" :value="2" /><el-option label="已离职" :value="3" /></el-select></el-form-item>
            <el-form-item label="银行卡号"><el-input v-model="employeeForm.bankAccountNo" /></el-form-item>
            <el-form-item label="开户银行"><el-input v-model="employeeForm.bankName" /></el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" @click="saveEmployee">{{ employeeMode === 'create' ? '新增员工' : '保存修改' }}</el-button><el-button @click="resetEmployeeForm">清空</el-button></div>
        </template>
        <template v-if="employeeMode === 'query'">
          <el-form :model="employeeFilter" label-position="top" class="filter-grid">
            <el-form-item label="直属部门"><el-select v-model="employeeFilter.departmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="员工状态"><el-select v-model="employeeFilter.employmentStatus" clearable><el-option label="待入职" :value="0" /><el-option label="已入职" :value="1" /><el-option label="停用" :value="2" /><el-option label="已离职" :value="3" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="employeeFilter.keyword" placeholder="姓名 / 工号 / 手机 / 岗位" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadEmployees">查询</el-button><el-button @click="resetEmployeeFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="employees" stripe class="data-table" @row-click="editEmployee"><el-table-column prop="employeeCode" label="工号" /><el-table-column prop="fullName" label="姓名" /><el-table-column prop="departmentName" label="部门" /><el-table-column prop="positionName" label="岗位" /><el-table-column prop="mobilePhone" label="电话" /><el-table-column prop="bankName" label="开户银行" /><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteEmployee(scope.row.id)">删除</el-button></template></el-table-column></el-table>
        </template>
      </section>

      <section v-if="activeTab === 'bindings'" class="page-card">
        <div class="topline"><div><p class="page-eyebrow">Integrations</p><h2>系统挂接</h2></div><el-button @click="loadBindings">刷新</el-button></div>
        <el-form :model="bindingForm" label-position="top" class="form-grid"><el-form-item label="模块"><el-select v-model="bindingForm.moduleCode"><el-option label="招聘" value="RECRUITMENT" /><el-option label="绩效" value="PERFORMANCE" /><el-option label="面试" value="INTERVIEW" /></el-select></el-form-item><el-form-item label="业务类型"><el-input v-model="bindingForm.businessType" /></el-form-item><el-form-item label="员工"><el-select v-model="bindingForm.employeeId" clearable><el-option v-for="item in employees" :key="item.id" :label="item.fullName" :value="item.id" /></el-select></el-form-item><el-form-item label="部门"><el-select v-model="bindingForm.departmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item><el-form-item label="外部引用"><el-input v-model="bindingForm.externalRef" /></el-form-item><el-form-item label="状态"><el-input v-model="bindingForm.bindingStatus" /></el-form-item></el-form>
        <el-button type="primary" @click="saveBinding">保存挂接</el-button>
        <el-table :data="bindings" stripe class="data-table"><el-table-column prop="moduleCode" label="模块" /><el-table-column prop="businessType" label="业务类型" /><el-table-column prop="employeeName" label="员工" /><el-table-column prop="departmentName" label="部门" /></el-table>
      </section>

      <section v-if="activeTab === 'recruitment'" class="page-card">
        <div class="topline"><div><p class="page-eyebrow">Recruitment</p><h2>招聘后台</h2></div><el-button @click="loadRecruitment">刷新</el-button></div>
        <div class="sub-tabs"><button :class="{ active: recruitmentMode === 'jobCreate' }" @click="showCreateJob">新增岗位</button><button :class="{ active: recruitmentMode === 'jobQuery' }" @click="recruitmentMode = 'jobQuery'">查询岗位</button><button :class="{ active: recruitmentMode === 'jobEdit' }" @click="recruitmentMode = 'jobEdit'">修改岗位</button><button :class="{ active: recruitmentMode === 'candidates' }" @click="recruitmentMode = 'candidates'">候选人信息</button></div>
        <template v-if="recruitmentMode === 'jobCreate' || recruitmentMode === 'jobEdit'">
          <el-form :model="jobForm" label-position="top" class="form-grid"><el-form-item label="岗位名称"><el-input v-model="jobForm.jobTitle" /></el-form-item><el-form-item label="岗位编码"><el-input v-model="jobForm.jobCode" /></el-form-item><el-form-item label="招聘部门"><el-select v-model="jobForm.departmentName" filterable placeholder="从数据库部门中选择"><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.departmentName" /></el-select></el-form-item><el-form-item label="工作地点"><el-input v-model="jobForm.workLocation" /></el-form-item><el-form-item label="岗位类型"><el-input v-model="jobForm.jobType" /></el-form-item><el-form-item label="招聘人数"><el-input-number v-model="jobForm.headcount" :min="1" /></el-form-item><el-form-item label="薪资范围"><el-input v-model="jobForm.salaryRange" /></el-form-item><el-form-item label="状态"><el-select v-model="jobForm.status"><el-option label="开放" :value="1" /><el-option label="关闭" :value="0" /></el-select></el-form-item><el-form-item label="岗位职责" class="wide"><el-input v-model="jobForm.responsibilities" type="textarea" :rows="3" /></el-form-item><el-form-item label="任职要求" class="wide"><el-input v-model="jobForm.requirements" type="textarea" :rows="3" /></el-form-item></el-form>
          <div class="action-row"><el-button type="primary" @click="saveJob">{{ recruitmentMode === 'jobCreate' ? '新增岗位' : '保存修改' }}</el-button><el-button @click="resetJobForm">清空</el-button></div>
        </template>
        <template v-if="recruitmentMode === 'jobQuery'">
          <el-form :model="jobFilter" label-position="top" class="filter-grid">
            <el-form-item label="招聘部门"><el-select v-model="jobFilter.departmentName" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.departmentName" /></el-select></el-form-item>
            <el-form-item label="岗位类型"><el-input v-model="jobFilter.jobType" placeholder="全职 / 实习 / 外包" /></el-form-item>
            <el-form-item label="岗位状态"><el-select v-model="jobFilter.status" clearable><el-option label="开放" :value="1" /><el-option label="关闭" :value="0" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="jobFilter.keyword" placeholder="岗位 / 编码 / 地点 / 薪资" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadJobs">查询</el-button><el-button @click="resetJobFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="jobs" stripe class="data-table" @row-click="editJob"><el-table-column prop="jobTitle" label="岗位" min-width="140" /><el-table-column prop="departmentName" label="部门" min-width="120" /><el-table-column prop="headcount" label="人数" width="80" /><el-table-column prop="status" label="状态" width="90"><template #default="scope">{{ scope.row.status === 1 ? '开放' : '关闭' }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteJob(scope.row.id)">删除</el-button></template></el-table-column></el-table>
        </template>
        <template v-if="recruitmentMode === 'candidates'">
          <el-form :model="candidateFilter" label-position="top" class="filter-grid">
            <el-form-item label="岗位"><el-select v-model="candidateFilter.jobId" clearable><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
            <el-form-item label="投递状态"><el-select v-model="candidateFilter.status" clearable><el-option label="已投递" value="SUBMITTED" /><el-option label="面试中" value="INTERVIEWING" /></el-select></el-form-item>
            <el-form-item label="面试阶段"><el-select v-model="candidateFilter.interviewStageStatus" clearable><el-option label="简历待查" value="简历待查" /><el-option label="AI面" value="AI面" /><el-option label="AI待审批" value="AI待审批" /><el-option label="视频面" value="视频面" /><el-option label="视频待审批" value="视频待审批" /><el-option label="线下面" value="线下面" /><el-option label="已通过" value="已通过" /><el-option label="已拒绝" value="已拒绝" /><el-option label="已终止" value="已终止" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="candidateFilter.keyword" placeholder="姓名 / 手机 / 专业 / 学校" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadCandidates">查询</el-button><el-button @click="resetCandidateFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="candidates" stripe class="data-table" @row-click="selectCandidate"><el-table-column prop="fullName" label="报名者姓名" min-width="120" /><el-table-column prop="mobilePhone" label="联系电话" min-width="130" /><el-table-column prop="jobTitle" label="岗位" min-width="140" /><el-table-column prop="interviewStageStatus" label="面试状态" min-width="120" /><el-table-column prop="interviewProcessId" label="流程流水号" min-width="120" /><el-table-column label="简历" min-width="150"><template #default="scope"><a v-if="scope.row.resumeFileId" class="resume-link" :href="resumeUrl(scope.row.resumeFileId)" target="_blank" @click.stop>{{ scope.row.resumeFileName || '查看简历' }}</a><span v-else>未上传</span></template></el-table-column><el-table-column label="操作" width="220"><template #default="scope"><el-button text @click.stop="startCandidateInterview(scope.row)">发起面试</el-button><el-button text type="danger" @click.stop="deleteCandidate(scope.row.id)">删除候选人</el-button></template></el-table-column><el-table-column prop="applicationStatus" label="状态" width="110" /></el-table>
          <div v-if="selectedCandidate" class="candidate-detail"><h3>候选人信息</h3><div class="detail-grid"><div><span>姓名</span><strong>{{ selectedCandidate.fullName }}</strong></div><div><span>联系电话</span><strong>{{ selectedCandidate.mobilePhone }}</strong></div><div><span>应聘岗位</span><strong>{{ selectedCandidate.jobTitle }}</strong></div><div><span>面试状态</span><strong>{{ selectedCandidate.interviewStageStatus || '简历待查' }}</strong></div><div><span>流程流水号</span><strong>{{ selectedCandidate.interviewProcessId || '-' }}</strong></div><div><span>面试者用户ID</span><strong>{{ selectedCandidate.intervieweeUserId || '-' }}</strong></div><div><span>专业</span><strong>{{ selectedCandidate.major }}</strong></div><div><span>邮箱</span><strong>{{ selectedCandidate.email || '-' }}</strong></div><div><span>身份证号</span><strong>{{ selectedCandidate.idCardNo || '-' }}</strong></div><div><span>学历</span><strong>{{ selectedCandidate.educationLevel || '-' }}</strong></div><div><span>毕业院校</span><strong>{{ selectedCandidate.graduationSchool || '-' }}</strong></div><div><span>工作年限</span><strong>{{ selectedCandidate.yearsOfExperience ?? '-' }}</strong></div><div><span>期望薪资</span><strong>{{ selectedCandidate.expectedSalary || '-' }}</strong></div></div><div class="intro-box"><span>个人简介</span><p>{{ selectedCandidate.selfIntroduction || '未填写' }}</p></div><a v-if="selectedCandidate.resumeFileId" class="resume-link" :href="resumeUrl(selectedCandidate.resumeFileId)" target="_blank">打开 PDF / 简历文件</a></div>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, hrApi, recruitmentApi } from '../services/api'

const router = useRouter()
const sessionUser = ref(JSON.parse(localStorage.getItem('session-user') || 'null'))
const isItAdmin = computed(() => sessionUser.value?.roleCode === 'IT_ADMIN')
const isHrAdmin = computed(() => sessionUser.value?.roleCode === 'HR_ADMIN')
const tabs = computed(() => {
  const base = [
    { key: 'dashboard', label: '总览' },
    { key: 'departments', label: '部门' },
    { key: 'employees', label: '员工' },
    { key: 'bindings', label: '挂接' },
    { key: 'recruitment', label: '招聘' },
  ]
  if (isItAdmin.value || isHrAdmin.value) {
    base.unshift({ key: 'audit', label: '审计日志' })
    base.unshift({ key: 'users', label: '用户管理中心' })
  }
  return base
})

const activeTab = ref('dashboard')
const departmentMode = ref('create')
const employeeMode = ref('create')
const recruitmentMode = ref('jobCreate')
const dashboard = reactive({ departmentCount: 0, employeeCount: 0, activeEmployeeCount: 0, pendingOnboardingCount: 0, recruitmentBindingCount: 0, performanceBindingCount: 0 })
const departments = ref([])
const employees = ref([])
const bindings = ref([])
const jobs = ref([])
const candidates = ref([])
const users = ref([])
const auditLogs = ref([])
const selectedCandidate = ref(null)

const userForm = reactive({ id: null, username: '', displayName: '', roleCode: 'HR_USER', status: 1, mobilePhone: '', email: '', newPassword: '' })
const departmentForm = reactive({ id: null, departmentName: '', departmentCode: '', parentDepartmentId: null, managerEmployeeId: null, description: '', sortOrder: 0, status: 1 })
const employeeForm = reactive({ id: null, employeeCode: '', fullName: '', idCardNo: '', mobilePhone: '', recruitmentMajor: '', positionName: '', departmentId: null, employmentStatus: 1, bankAccountNo: '', bankName: '' })
const bindingForm = reactive({ moduleCode: 'RECRUITMENT', businessType: 'EMPLOYEE_SYNC', employeeId: null, departmentId: null, externalRef: '', bindingStatus: 'ACTIVE', payload: '{"source":"frontend-demo"}' })
const jobForm = reactive({ id: null, jobTitle: '', jobCode: '', departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', status: 1 })
const auditFilter = reactive({ moduleCode: '', actionCode: '', keyword: '' })
const departmentFilter = reactive({ parentDepartmentId: null, status: null, keyword: '' })
const employeeFilter = reactive({ departmentId: null, employmentStatus: null, keyword: '' })
const jobFilter = reactive({ status: null, departmentName: '', jobType: '', keyword: '' })
const candidateFilter = reactive({ jobId: null, status: '', interviewStageStatus: '', keyword: '' })

const roleLabels = {
  IT_ADMIN: 'IT管理员',
  HR_ADMIN: 'HR管理员',
  HR_USER: 'HR用户',
  INTERVIEWEE: '面试者',
}
const actionLabels = {
  UPDATE_USER: '更新用户',
  RESET_USER_PASSWORD: '重置用户密码',
  CREATE_DEPARTMENT: '新增部门',
  UPDATE_DEPARTMENT: '修改部门',
  DELETE_DEPARTMENT: '删除部门',
  CREATE_EMPLOYEE: '新增员工',
  UPDATE_EMPLOYEE: '修改员工',
  DELETE_EMPLOYEE: '删除员工',
  CREATE_BINDING: '新增系统挂接',
  UPDATE_BINDING: '修改系统挂接',
  CREATE_RECRUITMENT_JOB: '新增招聘岗位',
  UPDATE_RECRUITMENT_JOB: '修改招聘岗位',
  DELETE_RECRUITMENT_JOB: '删除招聘岗位',
  DELETE_RECRUITMENT_CANDIDATE: '删除候选人',
  APPLY_CANDIDATE: '投递报名',
  UPLOAD_RESUME: '上传简历',
  CREATE_VIDEO_SESSION: '创建视频面试任务',
  INTERVIEWEE_JOIN_VIDEO: '面试者加入视频',
  HR_JOIN_VIDEO: 'HR加入视频',
  APPROVE_AI: '审批AI面试',
  APPROVE_VIDEO: '审批视频面试',
  APPROVE_ONSITE: '审批线下面试',
  TERMINATE_PROCESS: '终止面试流程',
  PUBLISH_VIDEO_OFFER: '发布视频连接邀请',
  SUBMIT_VIDEO_ANSWER: '提交视频连接应答',
  ADD_HR_ICE: '添加HR网络候选',
  ADD_INTERVIEWEE_ICE: '添加面试者网络候选',
  UPLOAD_RECORDING: '上传面试录制',
}
const targetLabels = {
  SYS_USER: '系统用户',
  HR_DEPARTMENT: '部门',
  HR_EMPLOYEE: '员工',
  HR_INTEGRATION_BINDING: '系统挂接',
  RECRUITMENT_JOB: '招聘岗位',
  RECRUITMENT_CANDIDATE: '候选人',
  RECRUITMENT_RESUME: '简历文件',
  INTERVIEW_PROCESS: '面试流程',
  VIDEO_SESSION: '视频面试会话',
}
const detailLabels = {
  INTERVIEWING: '面试中',
  SUBMITTED: '已投递',
  REJECTED: '已拒绝',
  PASSED: '已通过',
  TERMINATED: '已终止',
  ice: '网络候选信息',
}

const metrics = computed(() => [
  { label: '部门', value: dashboard.departmentCount },
  { label: '员工', value: dashboard.employeeCount },
  { label: '在职', value: dashboard.activeEmployeeCount },
  { label: '待入职', value: dashboard.pendingOnboardingCount },
  { label: '招聘挂接', value: dashboard.recruitmentBindingCount },
  { label: '绩效挂接', value: dashboard.performanceBindingCount },
])
const availableParentDepartments = computed(() => departments.value.filter((item) => item.id !== departmentForm.id))
const auditGroups = computed(() => [
  { key: 'admin', title: '后台管理日志', items: auditLogs.value.filter((item) => !['INTERVIEW', 'RECRUITMENT'].includes(item.moduleCode)) },
  { key: 'interview', title: '面试流程日志', items: auditLogs.value.filter((item) => item.moduleCode === 'INTERVIEW') },
  { key: 'recruitment', title: '报名者投递日志', items: auditLogs.value.filter((item) => item.moduleCode === 'RECRUITMENT') },
].filter((group) => !auditFilter.moduleCode || group.key === auditFilter.moduleCode.toLowerCase()))

function fail(error) { ElMessage.error(error.message || '请求失败') }
function localizeRole(value) { return roleLabels[value] || value || '-' }
function localizeAction(value) { return actionLabels[value] || value || '-' }
function localizeTarget(value) { return targetLabels[value] || value || '-' }
function localizeDetail(value) {
  if (!value) return '-'
  return Object.entries({ ...roleLabels, ...actionLabels, ...targetLabels, ...detailLabels }).reduce((text, [key, label]) => String(text).replaceAll(key, label), value)
}
async function loadSession() { try { const response = await authApi.getSession(); sessionUser.value = response.data; localStorage.setItem('session-user', JSON.stringify(response.data)); if (!(isItAdmin.value || isHrAdmin.value) && activeTab.value === 'users') { activeTab.value = 'dashboard' } } catch (error) { fail(error); router.push('/login') } }
async function loadDashboard() { try { Object.assign(dashboard, (await hrApi.getDashboard()).data) } catch (error) { fail(error) } }
async function loadDepartments() { try { departments.value = (await hrApi.listDepartments(cleanParams(departmentFilter))).data } catch (error) { fail(error) } }
async function loadEmployees() { try { employees.value = (await hrApi.listEmployees(cleanParams(employeeFilter))).data } catch (error) { fail(error) } }
async function loadBindings() { try { bindings.value = (await hrApi.listBindings()).data } catch (error) { fail(error) } }
async function loadJobs() { try { jobs.value = (await recruitmentApi.listAdminJobs(cleanParams(jobFilter))).data } catch (error) { fail(error) } }
async function loadCandidates() { try { candidates.value = (await recruitmentApi.listCandidates(cleanParams(candidateFilter))).data } catch (error) { fail(error) } }
async function loadRecruitment() { await Promise.all([loadJobs(), loadCandidates()]) }
async function loadUsers() { if (!(isItAdmin.value || isHrAdmin.value)) return; try { users.value = (await authApi.listUsers()).data } catch (error) { fail(error) } }
async function loadAuditLogs() { if (!(isItAdmin.value || isHrAdmin.value)) return; try { const params = cleanParams({ ...auditFilter, actionCode: resolveActionCode(auditFilter.actionCode) }); auditLogs.value = (await authApi.listAuditLogs(params)).data } catch (error) { fail(error) } }
async function loadAll() { await Promise.all([loadSession(), loadDashboard(), loadDepartments(), loadEmployees(), loadBindings(), loadRecruitment()]); if (isItAdmin.value || isHrAdmin.value) { await Promise.all([loadUsers(), loadAuditLogs()]) } }
async function saveDepartment() { try { await hrApi.saveDepartment({ ...departmentForm }); ElMessage.success('部门已保存'); await loadAll() } catch (error) { fail(error) } }
async function saveEmployee() { try { await hrApi.saveEmployee({ ...employeeForm }); ElMessage.success('员工已保存'); await loadAll() } catch (error) { fail(error) } }
async function saveBinding() { try { await hrApi.saveBinding({ ...bindingForm }); ElMessage.success('挂接已保存'); await loadAll() } catch (error) { fail(error) } }
async function deleteDepartment(id) { try { await hrApi.deleteDepartment(id); ElMessage.success('部门已删除'); if (departmentForm.id === id) resetDepartmentForm(); await loadAll() } catch (error) { fail(error) } }
async function deleteEmployee(id) { try { await hrApi.deleteEmployee(id); ElMessage.success('员工已删除'); if (employeeForm.id === id) resetEmployeeForm(); await loadAll() } catch (error) { fail(error) } }
function resetJobForm() { Object.assign(jobForm, { id: null, jobTitle: '', jobCode: '', departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', status: 1 }) }
function showCreateJob() { resetJobForm(); recruitmentMode.value = 'jobCreate' }
function editJob(row) { Object.assign(jobForm, row); recruitmentMode.value = 'jobEdit' }
async function saveJob() { try { await recruitmentApi.saveJob({ ...jobForm }); ElMessage.success('招聘岗位已保存'); resetJobForm(); await loadRecruitment() } catch (error) { fail(error) } }
async function deleteJob(id) { try { await recruitmentApi.deleteJob(id); ElMessage.success('岗位已删除'); resetJobForm(); await loadRecruitment() } catch (error) { fail(error) } }
function resetAuditFilter() { Object.assign(auditFilter, { moduleCode: '', actionCode: '', keyword: '' }); loadAuditLogs() }
function resetDepartmentFilter() { Object.assign(departmentFilter, { parentDepartmentId: null, status: null, keyword: '' }); loadDepartments() }
function resetEmployeeFilter() { Object.assign(employeeFilter, { departmentId: null, employmentStatus: null, keyword: '' }); loadEmployees() }
function resetJobFilter() { Object.assign(jobFilter, { status: null, departmentName: '', jobType: '', keyword: '' }); loadJobs() }
function resetCandidateFilter() { Object.assign(candidateFilter, { jobId: null, status: '', interviewStageStatus: '', keyword: '' }); loadCandidates() }
function resetDepartmentForm() { Object.assign(departmentForm, { id: null, departmentName: '', departmentCode: '', parentDepartmentId: null, managerEmployeeId: null, description: '', sortOrder: 0, status: 1 }) }
function showCreateDepartment() { resetDepartmentForm(); departmentMode.value = 'create' }
function editDepartment(row) { Object.assign(departmentForm, row); if (departmentForm.parentDepartmentId === departmentForm.id) departmentForm.parentDepartmentId = null; departmentMode.value = 'edit' }
function resetEmployeeForm() { Object.assign(employeeForm, { id: null, employeeCode: '', fullName: '', idCardNo: '', mobilePhone: '', recruitmentMajor: '', positionName: '', departmentId: null, employmentStatus: 1, bankAccountNo: '', bankName: '' }) }
function showCreateEmployee() { resetEmployeeForm(); employeeMode.value = 'create' }
function editEmployee(row) { Object.assign(employeeForm, row); employeeMode.value = 'edit' }
function selectCandidate(row) { selectedCandidate.value = row }
function resumeUrl(id) { return `/api/recruitment/resumes/${id}` }
async function startCandidateInterview(candidate) {
  try {
    const userList = (await authApi.listUsers({ roleCode: 'INTERVIEWEE', keyword: candidate.mobilePhone })).data
    const interviewee = userList.find((item) => item.mobilePhone === candidate.mobilePhone)
    if (!interviewee) {
      ElMessage.warning('未找到对应面试者账号，请先注册并完善资料')
      return
    }
    await interviewApi.startProcess({ recruitmentCandidateId: candidate.id, intervieweeUserId: interviewee.id, jobId: candidate.jobId, aiThresholdScore: 7 })
    ElMessage.success('面试流程已发起')
    await loadRecruitment()
  } catch (error) { fail(error) }
}
async function deleteCandidate(id) {
  try {
    await recruitmentApi.deleteCandidate(id)
    ElMessage.success('候选人已删除')
    if (selectedCandidate.value?.id === id) {
      selectedCandidate.value = null
    }
    await loadRecruitment()
  } catch (error) { fail(error) }
}
function editUser(row) { Object.assign(userForm, row, { newPassword: '' }) }
async function saveUser() { try { await authApi.updateUser(userForm.id, { roleCode: userForm.roleCode, status: userForm.status, displayName: userForm.displayName, mobilePhone: userForm.mobilePhone, email: userForm.email }); ElMessage.success('用户已保存'); await loadUsers() } catch (error) { fail(error) } }
async function resetUserPassword() { try { if (!userForm.id) { ElMessage.warning('请先选择用户'); return } if (!userForm.newPassword || userForm.newPassword.length < 6) { ElMessage.warning('新密码长度不能少于6位'); return } await authApi.updateUser(userForm.id, { newPassword: userForm.newPassword }); userForm.newPassword = ''; ElMessage.success('密码已重置'); await loadUsers() } catch (error) { fail(error) } }
async function logout() { try { await authApi.logout(); router.push('/login') } catch (error) { fail(error) } }
function cleanParams(source) { return Object.fromEntries(Object.entries(source).filter(([, value]) => value !== '' && value !== null && value !== undefined)) }
function resolveActionCode(value) {
  if (!value) return ''
  return Object.entries(actionLabels).find(([key, label]) => key === value || label.includes(value))?.[0] || value
}

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
.sub-tabs { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 18px; }
.sub-tabs button { border: 1px solid rgba(16, 37, 50, 0.12); background: #f8f5ef; color: #102532; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
.sub-tabs button.active { background: #102532; color: #f4efe7; }
.action-row { display: flex; gap: 12px; margin-top: 4px; }
.resume-link { color: #0f6c8f; font-weight: 700; text-decoration: none; }
.candidate-detail { margin-top: 18px; padding: 18px; border-radius: 20px; background: #f8f5ef; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.detail-grid div, .intro-box { background: rgba(255,255,255,0.82); border-radius: 14px; padding: 12px; }
.detail-grid span, .intro-box span { display: block; color: #6d7a83; margin-bottom: 6px; }
.intro-box { margin: 12px 0; }
.intro-box p { margin: 0; line-height: 1.7; }
.data-table { margin-top: 18px; }
.audit-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.audit-panel { min-width: 0; padding: 14px; border-radius: 20px; background: #f8f5ef; }
.audit-panel-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.audit-panel-head h3 { margin: 0; }
.audit-panel-head span { color: #6d7a83; font-weight: 700; }
.compact-table { margin-top: 12px; }
@media (max-width: 1200px) { .audit-grid { grid-template-columns: 1fr; } }
@media (max-width: 980px) { .console-shell { grid-template-columns: 1fr; } .metric-grid, .form-grid { grid-template-columns: 1fr; } }
</style>
