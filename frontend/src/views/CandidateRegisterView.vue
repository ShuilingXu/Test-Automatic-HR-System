<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Candidate</p>
      <h1 class="page-title">测试者报名页面原型</h1>
      <p class="page-subtitle">用于后续接入招聘发布、候选人资料采集和面试批次分配。</p>
      <div class="page-grid">
        <el-form :model="form" label-position="top" class="surface">
          <h3>报名信息</h3>
          <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.mobile" /></el-form-item>
          <el-form-item label="应聘岗位"><el-input v-model="form.position" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="简历链接"><el-input v-model="form.resumeUrl" /></el-form-item>
          <el-button type="primary" @click="submit">提交报名预留</el-button>
        </el-form>
        <div class="surface">
          <h3>报名流程预留</h3>
          <ol>
            <li>候选人提交报名信息。</li>
            <li>招聘系统生成候选人记录。</li>
            <li>面试系统分配线上面试链接。</li>
            <li>入职后通过 HR 绑定表关联员工档案。</li>
          </ol>
          <RouterLink class="link-chip" to="/candidate/interview">进入线上面试</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'

const form = reactive({ name: '', mobile: '', position: '', major: '', resumeUrl: '' })
function submit() { ElMessage.success('报名接口已预留，当前为前端原型') }
</script>
