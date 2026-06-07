<template>
  <div class="page-shell">
    <section class="page-card">
      <p class="page-eyebrow">Online Interview</p>
      <h1 class="page-title">线上面试页面原型</h1>
      <p class="page-subtitle">这里预留候选人身份校验、题目加载、音视频检测和答题提交接口。</p>
      <div class="page-grid">
        <div class="surface interview-stage">
          <div class="camera-box">视频预览区</div>
          <div class="link-row">
            <el-button type="primary">开始面试预留</el-button>
            <el-button>设备检测预留</el-button>
          </div>
        </div>
        <div class="surface">
          <h3>面试任务</h3>
          <div class="kv-grid">
            <div><span>候选人</span><strong>待登录</strong></div>
            <div><span>批次</span><strong>未分配</strong></div>
            <div><span>题目接口</span><strong>/api/interview/questions</strong></div>
            <div><span>提交接口</span><strong>/api/interview/submissions</strong></div>
          </div>
          <RouterLink class="link-chip" to="/candidate/register">返回报名页</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>

<style scoped>
.camera-box { height: 320px; border-radius: 22px; background: #102532; color: #f4efe7; display: grid; place-items: center; font-size: 22px; }
</style>
