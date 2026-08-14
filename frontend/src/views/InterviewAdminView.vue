<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interview HR</p>
          <h1 class="page-title">面试系统 HR 入口</h1>
        </div>
        <RouterLink class="link-chip" to="/admin">返回管理后台</RouterLink>
      </div>

      <div class="sub-tabs">
        <RouterLink class="link-chip" :class="{ active: activeTab === 'kb' }" to="/interview/hr/knowledge-bases">知识库</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'weights' }" to="/interview/hr/weights">岗位权重</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'template' }" to="/interview/hr/templates">流程模板</RouterLink>
        <RouterLink v-if="isItAdmin" class="link-chip" :class="{ active: activeTab === 'system' }" to="/interview/hr/system">系统配置</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'process' }" to="/interview/hr/processes">面试流程</RouterLink>
      </div>

      <section v-if="activeTab === 'kb'" class="surface">
        <h3>知识库与知识点</h3>
        <div class="page-grid">
          <el-form :model="kbForm" label-position="top" class="surface inner-surface">
            <el-form-item label="知识库名称"><el-input v-model="kbForm.knowledgeBaseName" /></el-form-item>
            <el-form-item label="技术方向"><el-input v-model="kbForm.techCategory" /></el-form-item>
            <el-form-item label="岗位方向"><el-input v-model="kbForm.jobCategory" /></el-form-item>
            <el-button type="primary" @click="saveKnowledgeBase">保存知识库</el-button>
          </el-form>
          <el-form :model="itemForm" label-position="top" class="surface inner-surface">
            <el-form-item label="所属知识库"><el-select v-model="itemForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="知识点"><el-input v-model="itemForm.knowledgePoint" /></el-form-item>
            <el-form-item label="知识内容"><el-input v-model="itemForm.knowledgeContent" type="textarea" :rows="4" /></el-form-item>
            <div class="action-row"><el-button type="primary" @click="saveKnowledgeItem">{{ itemForm.id ? '保存修改' : '保存知识点' }}</el-button><el-button @click="resetKnowledgeItemForm">清空新增</el-button></div>
          </el-form>
          <div class="surface inner-surface csv-import-box">
            <h3>CSV批量添加知识点</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="目标知识库"><el-select v-model="itemForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="CSV格式"><el-input model-value="knowledgePoint,knowledgeContent,status" disabled /></el-form-item>
            </el-form>
            <p class="serial-line">支持UTF-8或GBK CSV。第一列知识点，第二列知识内容，第三列状态可选，1启用、0停用。</p>
            <div class="action-row">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".csv,text/csv" :on-change="importKnowledgeItemsCsv">
                <el-button type="primary">上传CSV批量添加</el-button>
              </el-upload>
              <a class="link-chip" href="/knowledge-items-template.csv" download>下载CSV模板</a>
            </div>
          </div>
        </div>
        <el-table :data="knowledgeBases" stripe class="data-table" @row-click="openKnowledgeBase">
          <el-table-column prop="knowledgeBaseName" label="知识库" />
          <el-table-column prop="techCategory" label="技术方向" />
          <el-table-column prop="jobCategory" label="岗位方向" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteKnowledgeBase(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
        <el-table :data="knowledgeItems" stripe class="data-table" @row-click="editKnowledgeItem">
          <el-table-column prop="knowledgePoint" label="知识点" />
          <el-table-column prop="knowledgeContent" label="知识内容" min-width="280" />
          <el-table-column prop="status" label="状态" width="90"><template #default="scope">{{ scope.row.status === 0 ? '停用' : '启用' }}</template></el-table-column>
          <el-table-column label="操作" width="160"><template #default="scope"><el-button text @click.stop="editKnowledgeItem(scope.row)">编辑</el-button><el-button text type="danger" @click.stop="deleteKnowledgeItem(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'weights'" class="surface">
        <h3>岗位知识权重</h3>
        <el-form :model="weightForm" label-position="top" class="form-grid">
          <el-form-item label="招聘岗位"><el-select v-model="weightForm.jobId"><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="知识库"><el-select v-model="weightForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="权重"><el-input-number v-model="weightForm.weight" :min="1" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveWeight">保存权重</el-button>
        <el-table :data="weights" stripe class="data-table" @row-click="openWeight">
          <el-table-column prop="jobId" label="岗位ID" />
          <el-table-column prop="knowledgeBaseId" label="知识库ID" />
          <el-table-column prop="weight" label="权重" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteWeight(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'system'" class="surface">
        <div class="config-heading"><div><h3>面试系统配置</h3><p class="serial-line">运行参数保存到服务器 .env，保存后部分参数需要重启服务生效。</p></div><span class="env-badge">.env</span></div>
        <div class="config-tabs" role="tablist"><button v-for="tab in configTabs" :key="tab.key" :class="{ active: configTab === tab.key }" @click="configTab = tab.key">{{ tab.label }}</button></div>

        <template v-if="configTab !== 'models'">
          <div class="config-panel-head"><div><h3>{{ currentConfigGroup.title }}</h3><p>{{ currentConfigGroup.description }}</p></div></div>
          <el-form label-position="top" class="config-form-grid">
            <el-form-item v-for="field in currentConfigGroup.fields" :key="field.key" :label="field.label">
              <el-input v-model="systemConfig[field.key]" :type="field.secret ? 'password' : 'text'" :show-password="field.secret" :placeholder="field.placeholder || ''" />
            </el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" :loading="savingSystemConfig" @click="saveSystemConfig">保存当前参数</el-button></div>
        </template>

        <template v-else>
          <div class="model-tabs"><button v-for="model in modelTabs" :key="model.role" :class="{ active: modelTab === model.role }" @click="modelTab = model.role">{{ model.label }}</button></div>
          <div class="model-editor">
            <div class="config-panel-head"><div><h3>{{ currentModel.label }}</h3><p>{{ currentModel.description }}</p></div><span class="key-state">密钥：{{ currentModel.keyLabel() }}</span></div>
            <el-form :model="currentModel.form" label-position="top" class="config-form-grid">
              <el-form-item label="配置名称"><el-input v-model="currentModel.form.configName" /></el-form-item>
              <el-form-item :label="currentModel.role === 'VIDEO_TRANSCRIBER' ? 'NLS 网关地址' : '接口地址'"><el-input v-model="currentModel.form.baseUrl" /></el-form-item>
              <el-form-item :label="currentModel.role === 'VIDEO_TRANSCRIBER' ? 'AccessKey Secret' : 'API Key'"><el-input v-model="currentModel.form.apiKey" type="password" show-password placeholder="编辑留空则保留原密钥" /></el-form-item>
              <el-form-item :label="currentModel.role === 'VIDEO_TRANSCRIBER' ? 'AppKey' : '模型名称'"><el-input v-model="currentModel.form.modelName" /></el-form-item>
              <el-form-item v-if="currentModel.role === 'VIDEO_TRANSCRIBER'" label="AccessKey ID" class="wide"><el-input v-model="currentModel.form.promptTemplate" /></el-form-item>
              <el-form-item v-else :label="currentModel.promptLabel" class="wide"><el-input v-model="currentModel.form[currentModel.promptField]" type="textarea" :rows="6" /></el-form-item>
            </el-form>
            <div class="action-row"><el-button type="primary" @click="saveRoleLlmConfig(currentModel.form, currentModel.role)">保存模型配置</el-button></div>
          </div>
        </template>
      </section>

      <section v-if="activeTab === 'template'" class="surface">
        <div class="template-heading">
          <div><h3>面试流程模板</h3><p class="serial-line">配置可重复使用的 AI 与视频面试顺序；候选人发起后会生成独立快照。</p></div>
          <el-button @click="resetTemplateForm">新建模板</el-button>
        </div>
        <div class="template-editor">
          <el-form :model="templateForm" label-position="top" class="form-grid">
            <el-form-item label="模板名称"><el-input v-model="templateForm.templateName" maxlength="128" show-word-limit placeholder="例如：研发岗位三轮面试" /></el-form-item>
            <el-form-item label="状态"><el-select v-model="templateForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="模板说明" class="wide"><el-input v-model="templateForm.description" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="说明该流程的适用岗位或使用场景" /></el-form-item>
          </el-form>
          <div class="stage-list-head"><div><h4>面试阶段</h4><span>按顺序执行，可重复添加 AI 或视频面试。</span></div><el-button type="primary" @click="addTemplateStage">添加阶段</el-button></div>
          <div v-if="templateForm.stages.length" class="template-stage-list">
            <div v-for="(stage, index) in templateForm.stages" :key="stage.key" class="template-stage-row">
              <span class="stage-order">{{ index + 1 }}</span>
              <el-input v-model="stage.stageName" maxlength="128" placeholder="自定义阶段名称" />
              <el-radio-group v-model="stage.stageType" class="stage-type-switch"><el-radio-button label="AI">AI 面试</el-radio-button><el-radio-button label="VIDEO">视频面试</el-radio-button></el-radio-group>
              <el-select v-if="stage.stageType === 'AI'" v-model="stage.knowledgeBaseId" placeholder="选择题库"><el-option v-for="item in enabledKnowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select>
              <span v-else class="human-stage-note">由 HR 发起并完成视频面试</span>
              <div class="stage-row-actions">
                <el-button text :disabled="index === 0" @click="moveTemplateStage(index, -1)">上移</el-button>
                <el-button text :disabled="index === templateForm.stages.length - 1" @click="moveTemplateStage(index, 1)">下移</el-button>
                <el-button text type="danger" @click="removeTemplateStage(index)">删除</el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-box">请添加至少一个 AI 面试或视频面试阶段。</div>
          <div class="action-row"><el-button type="primary" :loading="savingTemplate" @click="saveProcessTemplate">保存模板</el-button><el-button @click="resetTemplateForm">清空</el-button></div>
        </div>
        <el-table :data="processTemplates" stripe class="data-table" @row-click="editProcessTemplate">
          <el-table-column prop="templateName" label="模板名称" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column label="流程阶段" min-width="260"><template #default="scope"><span class="template-stage-summary">{{ templateStageSummary(scope.row) }}</span></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="160"><template #default="scope"><el-button text @click.stop="editProcessTemplate(scope.row)">编辑</el-button><el-button text type="danger" @click.stop="deleteProcessTemplate(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'process' && !isProcessDetail" class="surface">
        <h3>候选人面试流程</h3>
        <el-form :model="processSearch" label-position="top" class="form-grid">
          <el-form-item label="搜索候选人"><el-input v-model="processSearch.keyword" placeholder="候选人ID / 姓名 / 手机 / 邮箱 / 专业 / 学校" /></el-form-item>
        </el-form>
        <el-form :model="processForm" label-position="top" class="form-grid">
          <el-form-item label="候选人投递记录"><el-select v-model="processForm.recruitmentCandidateId" filterable clearable @change="syncIntervieweeByCandidate"><el-option v-for="item in filteredProcessCandidates" :key="item.id" :label="`ID ${item.id} / ${item.fullName} / ${item.jobTitle || '未绑定岗位'}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="投递岗位"><el-select v-model="processForm.jobId" disabled><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="候选人唯一ID"><el-input :model-value="processCandidatePreview?.id || '-'" disabled /></el-form-item>
          <el-form-item label="流程模板"><el-select v-model="processForm.templateId" clearable placeholder="选择模板（不选则沿用旧流程）"><el-option v-for="item in enabledProcessTemplates" :key="item.id" :label="item.templateName" :value="item.id"><span>{{ item.templateName }}</span><small class="template-option-detail">{{ templateStageSummary(item) }}</small></el-option></el-select></el-form-item>
          <el-form-item label="AI通过阈值"><el-input-number v-model="processForm.aiThresholdScore" :min="1" /></el-form-item>
          <el-form-item label="低分追问阈值"><el-input-number v-model="processForm.aiFollowUpThreshold" :min="0" :max="100" /></el-form-item>
          <el-form-item label="AI最少问答轮数"><el-input-number v-model="processForm.aiMinQuestionRounds" :min="1" /></el-form-item>
          <el-form-item label="AI最多问答轮数"><el-input-number v-model="processForm.aiMaxQuestionRounds" :min="1" /></el-form-item>
          <el-form-item label="切屏终止阈值"><el-input-number v-model="processForm.antiCheatSwitchLimit" :min="1" /></el-form-item>
          <el-form-item label="AI输出模式"><el-select v-model="processForm.aiOutputMode"><el-option label="普通输出" value="NORMAL" /><el-option label="流式输出" value="STREAM" /></el-select></el-form-item>
        </el-form>
        <div v-if="processCandidatePreview" class="candidate-preview">
          <h4>候选人投递预览</h4>
          <div class="preview-grid">
            <div><span>候选人ID</span><strong>{{ processCandidatePreview.id }}</strong></div>
            <div><span>候选人姓名</span><strong>{{ processCandidatePreview.fullName }}</strong></div>
            <div><span>投递岗位</span><strong>{{ processCandidatePreview.jobTitle || '-' }}</strong></div>
            <div><span>联系电话</span><strong>{{ processCandidatePreview.mobilePhone || '-' }}</strong></div>
            <div><span>邮箱</span><strong>{{ processCandidatePreview.email || '-' }}</strong></div>
            <div><span>专业</span><strong>{{ processCandidatePreview.major || '-' }}</strong></div>
            <div><span>毕业院校</span><strong>{{ processCandidatePreview.graduationSchool || '-' }}</strong></div>
            <div><span>状态</span><strong>{{ processCandidatePreview.interviewStageStatus || processCandidatePreview.applicationStatus || '-' }}</strong></div>
          </div>
        </div>
        <div class="link-row"><el-button type="primary" @click="startProcess">发起面试流程</el-button></div>
        <el-table :data="processes" stripe class="data-table" @row-click="openProcess">
          <el-table-column prop="id" label="流程流水号" width="110" />
          <el-table-column prop="recruitmentCandidateId" label="候选人ID" />
          <el-table-column prop="candidateName" label="候选人姓名" />
          <el-table-column prop="questionTitle" label="投递岗位" />
          <el-table-column prop="templateName" label="流程模板" min-width="140"><template #default="scope">{{ scope.row.templateName || '旧版固定流程' }}</template></el-table-column>
          <el-table-column prop="currentStage" label="当前轮次" />
          <el-table-column prop="processStatusView" label="状态展示" />
          <el-table-column prop="aiAverageScore" label="AI均分" />
          <el-table-column prop="overallStatus" label="总状态" />
          <el-table-column label="操作" min-width="250">
            <template #default="scope">
              <el-button text @click.stop="openProcess(scope.row)">进入面试界面</el-button>
              <el-button v-if="isAiApprovalPending(scope.row)" text type="primary" @click.stop="approveAi(scope.row, 1)">AI审批通过</el-button>
              <el-button v-if="isAiApprovalPending(scope.row)" text type="danger" @click.stop="approveAi(scope.row, 0)">AI审批不通过</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'process' && isProcessDetail" class="surface">
        <div class="detail-headline">
          <div>
            <p class="page-eyebrow">Interview Process</p>
            <h3>{{ selectedProcess?.candidateName || '候选人' }} 的面试操作台</h3>
          </div>
          <RouterLink class="link-chip" to="/interview/hr/processes">返回流程列表</RouterLink>
        </div>
        <div v-if="selectedProcess" class="process-workbench">
          <section class="workbench-panel candidate-panel">
            <h3>面试者个人信息</h3>
            <div class="candidate-info-grid">
              <div><span>姓名</span><strong>{{ selectedCandidate?.fullName || selectedProcess.candidateName || '-' }}</strong></div>
              <div><span>手机号</span><strong>{{ selectedCandidate?.mobilePhone || '-' }}</strong></div>
              <div><span>邮箱</span><strong>{{ selectedCandidate?.email || '-' }}</strong></div>
              <div><span>岗位</span><strong>{{ selectedCandidate?.jobTitle || selectedProcess.questionTitle || '-' }}</strong></div>
              <div><span>专业</span><strong>{{ selectedCandidate?.major || '-' }}</strong></div>
              <div><span>学历</span><strong>{{ selectedCandidate?.educationLevel || '-' }}</strong></div>
              <div><span>毕业院校</span><strong>{{ selectedCandidate?.graduationSchool || '-' }}</strong></div>
              <div><span>工作年限</span><strong>{{ selectedCandidate?.yearsOfExperience ?? '-' }}</strong></div>
            </div>
            <div class="resume-ai-box">
              <span>简历AI评价</span>
              <strong>{{ selectedCandidate?.resumeLlmScore ?? resumeLlmStatusLabel(selectedCandidate?.resumeLlmStatus) }}</strong>
              <p>{{ selectedCandidate?.resumeLlmComment || '暂无简历AI评价' }}</p>
            </div>
          </section>
          <section v-if="selectedProcess.stages?.length" class="workbench-panel stage-overview-panel">
            <div class="panel-title-row"><h3>流程阶段</h3><span>{{ selectedProcess.templateName || '流程模板' }}</span></div>
            <ol class="process-stage-timeline">
              <li v-for="stage in selectedProcess.stages" :key="stage.processStageId || stage.id" :class="{ active: stage.processStageId === selectedProcess.processStageId, complete: ['PASSED', 'REJECTED'].includes(stage.stageStatus) }">
                <span class="timeline-index">{{ stage.sequenceNo }}</span>
                <div><strong>{{ stage.stageName }}</strong><small>{{ stage.stageType === 'AI' ? `AI 面试${stage.knowledgeBaseName ? ` · ${stage.knowledgeBaseName}` : ''}` : '视频面试 · HR 主持' }}</small></div>
                <el-tag size="small" :type="stageStatusTagType(stage.stageStatus)">{{ stageStatusLabel(stage.stageStatus) }}</el-tag>
              </li>
            </ol>
          </section>
          <section class="workbench-panel video-panel">
            <div class="panel-title-row"><h3>视频面试</h3><span>{{ selectedProcess.sessionStatus || '未开始' }}</span></div>
            <div v-if="selectedProcess.videoJoinLink || selectedProcess.videoSerialNo" class="serial-line">
              <span v-if="selectedProcess.videoSerialNo">视频流水号：{{ selectedProcess.videoSerialNo }}</span>
              <el-button v-if="selectedProcess.videoJoinLink" text class="video-link" @click="copyVideoJoinLink">复制候选人视频链接</el-button>
              <a v-if="selectedProcess.recordingPath || selectedProcess.recordingFileName" :href="interviewApi.getRecordingUrl(selectedProcess.id, selectedProcess.processStageId)" target="_blank" class="video-link">查看合并录制文件</a>
            </div>
            <div class="video-grid">
              <div class="video-box"><span>HR本地视频</span><video ref="hrLocalVideo" autoplay muted playsinline></video></div>
              <div class="video-box"><span>面试者远端视频</span><video ref="hrRemoteVideo" autoplay playsinline></video></div>
            </div>
            <div class="video-summary-box">
              <div class="summary-status-row">
                <span>音频转写/会议概要状态：{{ selectedProcess.summaryStatus || '未生成' }}</span>
                <el-button
                  v-if="selectedProcess.recordingPath || selectedProcess.recordingFileName"
                  size="small"
                  :loading="retryingVideoSummary"
                  @click="retryVideoSummary"
                >重新生成</el-button>
              </div>
              <div>
                <strong>会议概要</strong>
                <div v-if="selectedProcess.summaryText" class="summary-document">
                  <template v-for="(line, index) in summaryLines(selectedProcess.summaryText)" :key="index">
                    <h4 v-if="line.kind === 'heading'">{{ line.text }}</h4>
                    <p v-else-if="line.kind === 'list'" class="summary-list-item">{{ line.text }}</p>
                    <p v-else>{{ line.text }}</p>
                  </template>
                </div>
                <p v-else>暂无概要，双侧录制完成后自动生成。</p>
              </div>
              <p class="transcript-text"><strong>转写文本</strong>{{ selectedProcess.transcriptText || '暂无转写文本' }}</p>
            </div>
          </section>
          <section class="workbench-panel ai-question-panel">
            <div class="panel-title-row">
              <h3>AI问答题号</h3>
              <a v-if="selectedProcess.aiRecordingPath || selectedProcess.aiRecordingFileName" :href="interviewApi.getAiRecordingUrl(selectedProcess.id, selectedProcess.processStageId)" target="_blank" class="video-link">查看AI问答视频</a>
            </div>
            <div class="question-number-grid">
              <button v-for="item in aiRecords" :key="item.id" class="question-number" :class="{ answered: item.answerContent }">Q{{ item.sequenceNo }}</button>
            </div>
            <el-table :data="aiRecords" stripe class="data-table compact-ai-table">
              <el-table-column v-if="selectedProcess.stages?.length" prop="stageName" label="阶段" min-width="120" />
              <el-table-column prop="sequenceNo" label="题号" width="70" />
              <el-table-column prop="knowledgePoint" label="知识点" min-width="120" />
              <el-table-column prop="questionContent" label="提问" min-width="220" />
              <el-table-column prop="answerContent" label="回答" min-width="220" />
              <el-table-column prop="averageScore" label="均分" width="80" />
            </el-table>
          </section>
          <section class="workbench-panel action-panel">
            <h3>操作区</h3>
            <div class="process-stats">
              <p>流程流水号：{{ selectedProcess.id }}</p>
              <p>当前状态：{{ selectedProcess.processStatusView || '-' }}</p>
              <p>AI均分：{{ selectedProcess.aiAverageScore ?? '-' }}</p>
              <p>低分追问阈值：{{ selectedProcess.aiFollowUpThreshold ?? 70 }}</p>
              <p>AI轮数：{{ selectedProcess.aiMinQuestionRounds || '-' }} - {{ selectedProcess.aiMaxQuestionRounds || '-' }}</p>
              <p>切屏次数：{{ selectedProcess.antiCheatSwitchCount || 0 }} / {{ selectedProcess.antiCheatSwitchLimit || 5 }}</p>
            </div>
            <div class="action-button-grid">
              <el-button v-if="canApproveAi" type="primary" @click="approveAi(selectedProcess, 1)">AI审批通过</el-button>
              <el-button v-if="canApproveAi" type="danger" plain @click="approveAi(selectedProcess, 0)">AI审批不通过</el-button>
              <el-button v-if="canStartVideo" @click="startHrVideoCall">开始视频面</el-button>
              <el-button v-if="canStopVideo" @click="stopHrRecording">结束并上传录制</el-button>
              <el-button v-if="canApproveVideo" @click="approveVideo(1)">{{ selectedProcess.stageName || '视频面试' }}通过</el-button>
              <el-button v-if="canApproveVideo" @click="approveVideo(0)">{{ selectedProcess.stageName || '视频面试' }}不通过</el-button>
              <el-button v-if="canApproveOnsite" @click="approveOnsite(1)">线下面通过</el-button>
              <el-button v-if="canApproveOnsite" @click="approveOnsite(0)">线下面不通过</el-button>
              <el-button v-if="canTerminate" type="danger" @click="terminateProcess">终止流程</el-button>
            </div>
            <div class="remark-box">
              <span>面试备注</span>
              <el-input v-model="processRemark" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="记录面试补充说明、风险点、沟通结论" />
              <el-button type="primary" :loading="savingRemark" @click="saveProcessRemark">保存备注</el-button>
            </div>
          </section>
        </div>
        <div v-else class="empty-box">正在加载候选人面试流程...</div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, interviewApi, recruitmentApi, systemApi } from '../services/api'
import { attachRemoteTrack, buildMediaErrorMessage, createPeerConnection, defaultIceServers, isRelayIceCandidate, playVideo, requestCameraAndMicrophone } from '../utils/media'

const sessionUser = ref(JSON.parse(localStorage.getItem('session-user') || 'null'))
const route = useRoute()
const router = useRouter()
const isItAdmin = computed(() => sessionUser.value?.roleCode === 'IT_ADMIN')
const activeTab = computed(() => route.meta.interviewTab || 'process')
const isProcessDetail = computed(() => route.name === 'interview-process-detail')
const knowledgeBases = ref([])
const knowledgeItems = ref([])
const weights = ref([])
const llmConfigs = ref([])
const jobs = ref([])
const recruitmentCandidates = ref([])
const processes = ref([])
const processTemplates = ref([])
const aiRecords = ref([])
const selectedProcess = ref(null)
const selectedCandidate = ref(null)
const videoActive = ref(false)
const processRemark = ref('')
const savingRemark = ref(false)
const savingSystemConfig = ref(false)
const configTab = ref('notifications')
const modelTab = ref('INTERVIEWER')

const hrLocalVideo = ref(null)
const hrRemoteVideo = ref(null)
let hrLocalStream = null
let hrPeer = null
let hrPollTimer = null
let hrRecorder = null
let hrRecordedChunks = []
let addedIntervieweeIce = new Set()
let hrRemoteStream = null
let pendingIntervieweeIce = []
let hrRecordingStopInProgress = false
let handledHrRecordingEndSignal = ''
let hrRecordingEndTimer = null

const kbForm = reactive({ id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 })
const itemForm = reactive({ id: null, knowledgeBaseId: null, knowledgePoint: '', knowledgeContent: '', status: 1 })
const weightForm = reactive({ id: null, jobId: null, knowledgeBaseId: null, weight: 1 })
const interviewerLlmForm = reactive(createLlmForm('INTERVIEWER'))
const scorerLlmForm = reactive(createLlmForm('SCORER'))
const resumeReviewLlmForm = reactive(createLlmForm('RESUME_REVIEW'))
const videoTranscriberLlmForm = reactive(createLlmForm('VIDEO_TRANSCRIBER'))
const videoSummaryLlmForm = reactive(createLlmForm('VIDEO_SUMMARY'))
const processForm = reactive({ recruitmentCandidateId: null, intervieweeUserId: '', jobId: null, templateId: null, aiThresholdScore: 70, aiFollowUpThreshold: 70, aiMinQuestionRounds: 5, aiMaxQuestionRounds: 10, antiCheatSwitchLimit: 5, aiOutputMode: 'NORMAL' })
const processSearch = reactive({ keyword: '' })
const templateForm = reactive(createTemplateForm())
const savingTemplate = ref(false)
const retryingVideoSummary = ref(false)
const systemConfig = reactive({
  ALIYUN_ACCESS_KEY_ID: '',
  ALIYUN_ACCESS_KEY_SECRET: '',
  ALIYUN_SMS_SIGN_NAME: '',
  ALIYUN_SMS_TEMPLATE_CODE: '',
  SMTP_HOST: '',
  SMTP_PORT: '',
  SMTP_USERNAME: '',
  SMTP_PASSWORD: '',
  SMTP_FROM: '',
  SMTP_SSL_ENABLED: '',
  SMTP_STARTTLS_ENABLED: '',
  ALIYUN_STT_APP_KEY: '', ALIYUN_STT_ENDPOINT: '', ALIYUN_OSS_BUCKET_NAME: '', ALIYUN_OSS_ENDPOINT: '',
  DB_TYPE: '', DB_URL: '', DB_USERNAME: '', DB_PASSWORD: '', JWT_SECRET: '',
  INTERVIEW_VIDEO_FFMPEG_PATH: '', INTERVIEW_VIDEO_VIDEO_CODEC: '', INTERVIEW_VIDEO_AUDIO_CODEC: '',
  INTERVIEW_STUN_URLS: '', INTERVIEW_TURN_URLS: '', INTERVIEW_TURN_USERNAME: '', INTERVIEW_TURN_CREDENTIAL: '',
  TURN_HOST: '', TURN_EXTERNAL_IP: '', TURN_PRIVATE_IP: '', TURN_REALM: '', TURN_MIN_PORT: '', TURN_MAX_PORT: '',
  RESUME_OCR_ENABLED: '', RESUME_OCR_TESSERACT_PATH: '', RESUME_OCR_LANGUAGE: '', RESUME_OCR_DPI: '', RESUME_OCR_MAX_PAGES: '',
})

const configTabs = [
  { key: 'notifications', label: '通知服务' },
  { key: 'media', label: '面试媒体' },
  { key: 'database', label: '数据库与安全' },
  { key: 'resume', label: '简历识别' },
  { key: 'models', label: 'AI 模型' },
]
const configGroups = {
  notifications: { title: '通知服务', description: '短信验证码与注册邮件使用的接口参数。', fields: [
    { key: 'ALIYUN_ACCESS_KEY_ID', label: '阿里云 AccessKey ID' }, { key: 'ALIYUN_ACCESS_KEY_SECRET', label: '阿里云 AccessKey Secret', secret: true, placeholder: '留空则不覆盖' }, { key: 'ALIYUN_SMS_SIGN_NAME', label: '短信签名' }, { key: 'ALIYUN_SMS_TEMPLATE_CODE', label: '短信模板 Code' }, { key: 'SMTP_HOST', label: 'SMTP 服务器', placeholder: 'smtp.example.com' }, { key: 'SMTP_PORT', label: 'SMTP 端口', placeholder: '587' }, { key: 'SMTP_USERNAME', label: 'SMTP 用户名' }, { key: 'SMTP_PASSWORD', label: 'SMTP 密码', secret: true, placeholder: '留空则不覆盖' }, { key: 'SMTP_FROM', label: '发件人' }, { key: 'SMTP_SSL_ENABLED', label: 'SSL 启用', placeholder: 'true / false' }, { key: 'SMTP_STARTTLS_ENABLED', label: 'STARTTLS 启用', placeholder: 'true / false' },
  ] },
  media: { title: '面试媒体', description: '视频编码、WebRTC 网络和语音转文字服务参数。', fields: [
    { key: 'INTERVIEW_VIDEO_FFMPEG_PATH', label: 'FFmpeg 路径', placeholder: 'ffmpeg' }, { key: 'INTERVIEW_VIDEO_VIDEO_CODEC', label: '视频编码器' }, { key: 'INTERVIEW_VIDEO_AUDIO_CODEC', label: '音频编码器' }, { key: 'INTERVIEW_STUN_URLS', label: 'STUN 地址' }, { key: 'INTERVIEW_TURN_URLS', label: 'TURN 地址' }, { key: 'INTERVIEW_TURN_USERNAME', label: 'TURN 用户名' }, { key: 'INTERVIEW_TURN_CREDENTIAL', label: 'TURN 凭证', secret: true }, { key: 'ALIYUN_STT_APP_KEY', label: '阿里云 STT AppKey' }, { key: 'ALIYUN_STT_ENDPOINT', label: '阿里云 STT Endpoint' }, { key: 'ALIYUN_OSS_BUCKET_NAME', label: 'OSS Bucket' }, { key: 'ALIYUN_OSS_ENDPOINT', label: 'OSS Endpoint' }, { key: 'TURN_HOST', label: 'TURN 主机' }, { key: 'TURN_EXTERNAL_IP', label: 'TURN 外部 IP' }, { key: 'TURN_PRIVATE_IP', label: 'TURN 内部 IP' }, { key: 'TURN_REALM', label: 'TURN Realm' }, { key: 'TURN_MIN_PORT', label: 'TURN 最小端口' }, { key: 'TURN_MAX_PORT', label: 'TURN 最大端口' },
  ] },
  database: { title: '数据库与安全', description: '数据库连接和 JWT 签名参数。敏感值以掩码显示。', fields: [
    { key: 'DB_TYPE', label: '数据库类型', placeholder: 'sqlite / mysql / postgresql' }, { key: 'DB_URL', label: '数据库 URL' }, { key: 'DB_USERNAME', label: '数据库用户名' }, { key: 'DB_PASSWORD', label: '数据库密码', secret: true, placeholder: '留空则不覆盖' }, { key: 'JWT_SECRET', label: 'JWT Secret', secret: true, placeholder: '至少 32 位，留空则不覆盖' },
  ] },
  resume: { title: '简历识别', description: '简历 OCR 的开关、语言和处理限制。', fields: [
    { key: 'RESUME_OCR_ENABLED', label: 'OCR 启用', placeholder: 'true / false' }, { key: 'RESUME_OCR_TESSERACT_PATH', label: 'Tesseract 路径' }, { key: 'RESUME_OCR_LANGUAGE', label: '识别语言', placeholder: 'chi_sim+eng' }, { key: 'RESUME_OCR_DPI', label: '识别 DPI' }, { key: 'RESUME_OCR_MAX_PAGES', label: '最大页数' },
  ] },
}
const currentConfigGroup = computed(() => configGroups[configTab.value] || configGroups.notifications)
const interviewerKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'INTERVIEWER')?.apiKeyMasked || '未配置')
const scorerKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'SCORER')?.apiKeyMasked || '未配置')
const resumeReviewKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'RESUME_REVIEW')?.apiKeyMasked || '未配置，默认回退评分模型')
const videoTranscriberKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'VIDEO_TRANSCRIBER')?.apiKeyMasked || '未配置')
const videoSummaryKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'VIDEO_SUMMARY')?.apiKeyMasked || '未配置')
const modelTabs = [
  { role: 'INTERVIEWER', label: '面试官', description: '生成面试问题和追问。', keyLabel: () => interviewerKeyLabel.value, form: interviewerLlmForm, promptField: 'promptTemplate', promptLabel: '提问提示词模板' },
  { role: 'SCORER', label: '评分器', description: '根据回答和岗位要求生成评分。', keyLabel: () => scorerKeyLabel.value, form: scorerLlmForm, promptField: 'scoringRulePrompt', promptLabel: '评分提示词' },
  { role: 'RESUME_REVIEW', label: '简历初筛', description: '对候选人简历进行初步匹配。', keyLabel: () => resumeReviewKeyLabel.value, form: resumeReviewLlmForm, promptField: 'scoringRulePrompt', promptLabel: '筛选提示词' },
  { role: 'VIDEO_TRANSCRIBER', label: '视频转写', description: '将视频面试转换为文本。', keyLabel: () => videoTranscriberKeyLabel.value, form: videoTranscriberLlmForm, promptField: 'promptTemplate', promptLabel: '提示词' },
  { role: 'VIDEO_SUMMARY', label: '会议概要', description: '总结视频面试表现和建议。', keyLabel: () => videoSummaryKeyLabel.value, form: videoSummaryLlmForm, promptField: 'scoringRulePrompt', promptLabel: '概要提示词' },
]
const currentModel = computed(() => modelTabs.find((item) => item.role === modelTab.value) || modelTabs[0])

const canTerminate = computed(() => selectedProcess.value?.overallStatus === 'IN_PROGRESS')
const canApproveAi = computed(() => isAiApprovalPending(selectedProcess.value))
const canStartVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && selectedProcess.value?.videoJoinLink && !videoActive.value)
const canStopVideo = computed(() => videoActive.value)
const canApproveVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && ['WAITING_APPROVAL', 'RECORDED'].includes(selectedProcess.value?.sessionStatus))
const canApproveOnsite = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'ONSITE')
const enabledKnowledgeBases = computed(() => knowledgeBases.value.filter((item) => item.status === 1))
const enabledProcessTemplates = computed(() => processTemplates.value.filter((item) => item.status === 1))
const filteredProcessCandidates = computed(() => {
  const keyword = processSearch.keyword.trim().toLowerCase()
  const candidates = recruitmentCandidates.value.filter((item) => !item.interviewProcessId && item.applicationStatus !== 'REJECTED')
  if (!keyword) return candidates
  return candidates.filter((item) => [item.id, item.fullName, item.mobilePhone, item.email, item.major, item.graduationSchool]
    .some((value) => String(value || '').toLowerCase().includes(keyword)))
})
const processCandidatePreview = computed(() => recruitmentCandidates.value.find((item) => item.id === processForm.recruitmentCandidateId) || null)

function fail(error) { ElMessage.error(error.message || '操作失败') }
function summaryLines(markdown) {
  return String(markdown || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line) => {
    if (/^#{1,6}\s+/.test(line)) return { kind: 'heading', text: line.replace(/^#{1,6}\s+/, '').replace(/\*\*/g, '') }
    if (/^(?:[-*]|\d+\.)\s+/.test(line)) return { kind: 'list', text: line.replace(/^([-*]|\d+\.)\s+/, '').replace(/\*\*/g, '') }
    return { kind: 'text', text: line.replace(/\*\*/g, '') }
  })
}
function resumeLlmStatusLabel(status) { return ({ PENDING: '评分中', COMPLETED: '已完成', FAILED: '评分失败' })[status] || '-' }
function isAiApprovalPending(process) { return process?.overallStatus === 'IN_PROGRESS' && process?.currentStage === 'AI' && process?.stageStatus === 'WAITING_APPROVAL' }
async function loadAll() {
  try {
    sessionUser.value = (await authApi.getSession()).data
    knowledgeBases.value = (await interviewApi.listKnowledgeBases()).data
    if (isItAdmin.value) {
      llmConfigs.value = (await interviewApi.listLlmConfigs()).data
      Object.assign(systemConfig, (await systemApi.getConfig()).data)
      syncLlmForms()
    } else {
      llmConfigs.value = []
      if (activeTab.value === 'llm') router.replace('/interview/hr/knowledge-bases')
    }
    jobs.value = (await recruitmentApi.listAdminJobs()).data
    recruitmentCandidates.value = (await recruitmentApi.listCandidates()).data
    processTemplates.value = (await interviewApi.listProcessTemplates()).data
    processes.value = (await interviewApi.listProcesses()).data
    if (selectedProcess.value) {
      selectedProcess.value = processes.value.find((item) => item.id === selectedProcess.value.id) || selectedProcess.value
      if (isProcessDetail.value) {
        aiRecords.value = (await interviewApi.listAiRecords({ processId: selectedProcess.value.id })).data
        selectedCandidate.value = selectedProcess.value.recruitmentCandidateId ? (await recruitmentApi.getCandidate(selectedProcess.value.recruitmentCandidateId)).data : null
        processRemark.value = selectedProcess.value.remark || ''
      }
    }
    await syncRouteState()
  } catch (error) { fail(error) }
}
async function selectKnowledgeBase(row) { itemForm.knowledgeBaseId = row.id; knowledgeItems.value = (await interviewApi.listKnowledgeItems({ knowledgeBaseId: row.id })).data }
async function openKnowledgeBase(row) { await router.push(`/interview/hr/knowledge-bases/${row.id}`) }
function openWeight(row) { Object.assign(weightForm, row); router.push(`/interview/hr/weights/${row.id}`) }
function openLlmConfig(row) { editLlmConfig(row) }
function openProcess(row) { router.push(`/interview/hr/processes/${row.id}`) }
async function saveKnowledgeBase() { try { await interviewApi.saveKnowledgeBase({ ...kbForm }); ElMessage.success('知识库已保存'); await loadAll() } catch (error) { fail(error) } }
async function deleteKnowledgeBase(id) { try { await interviewApi.deleteKnowledgeBase(id); ElMessage.success('知识库已删除'); await loadAll() } catch (error) { fail(error) } }
async function saveKnowledgeItem() { try { await interviewApi.saveKnowledgeItem({ ...itemForm }); ElMessage.success('知识点已保存'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
function editKnowledgeItem(row) { Object.assign(itemForm, { id: row.id, knowledgeBaseId: row.knowledgeBaseId, knowledgePoint: row.knowledgePoint, knowledgeContent: row.knowledgeContent, status: row.status ?? 1 }) }
function resetKnowledgeItemForm() { Object.assign(itemForm, { id: null, knowledgeBaseId: itemForm.knowledgeBaseId, knowledgePoint: '', knowledgeContent: '', status: 1 }) }
async function importKnowledgeItemsCsv(uploadFile) { try { if (!itemForm.knowledgeBaseId) { ElMessage.warning('请先选择目标知识库'); return } const response = await interviewApi.importKnowledgeItems(itemForm.knowledgeBaseId, uploadFile.raw); ElMessage.success(`已导入 ${response.data.imported} 条知识点`); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
async function deleteKnowledgeItem(id) { try { await interviewApi.deleteKnowledgeItem(id); ElMessage.success('知识点已删除'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
async function saveWeight() { try { await interviewApi.saveJobKnowledgeWeight({ ...weightForm }); ElMessage.success('权重已保存'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { fail(error) } }
async function deleteWeight(id) { try { await interviewApi.deleteJobKnowledgeWeight(id); ElMessage.success('权重已删除'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { fail(error) } }
async function saveRoleLlmConfig(form, role) { try { await interviewApi.saveLlmConfig({ ...form, modelRole: role }); ElMessage.success('LLM配置已保存'); form.apiKey = ''; await loadAll() } catch (error) { fail(error) } }
async function deleteLlmConfig(id) { try { await interviewApi.deleteLlmConfig(id); ElMessage.success('LLM配置已删除'); await loadAll() } catch (error) { fail(error) } }
async function saveSystemConfig() { savingSystemConfig.value = true; try { await systemApi.saveConfig({ ...systemConfig }); ElMessage.success('系统配置已保存'); Object.assign(systemConfig, (await systemApi.getConfig()).data) } catch (error) { fail(error) } finally { savingSystemConfig.value = false } }
function createTemplateForm() { return { id: null, templateName: '', description: '', status: 1, stages: [] } }
function createTemplateStage() { return { key: `${Date.now()}-${Math.random()}`, stageName: '', stageType: 'AI', knowledgeBaseId: null } }
function resetTemplateForm() { Object.assign(templateForm, createTemplateForm()) }
function addTemplateStage() { templateForm.stages.push(createTemplateStage()) }
function removeTemplateStage(index) { templateForm.stages.splice(index, 1) }
function moveTemplateStage(index, offset) { const target = index + offset; if (target < 0 || target >= templateForm.stages.length) return; const [stage] = templateForm.stages.splice(index, 1); templateForm.stages.splice(target, 0, stage) }
function templateStageSummary(template) { return (template?.stages || []).map((stage) => stage.stageName || (stage.stageType === 'AI' ? 'AI 面试' : '视频面试')).join(' -> ') || '暂无阶段' }
function stageStatusLabel(status) { return ({ PENDING: '待开始', READY: '待开始', IN_PROGRESS: '进行中', UPLOADING: '上传中', WAITING_APPROVAL: '待审批', PASSED: '已通过', REJECTED: '未通过' })[status] || status || '-' }
function stageStatusTagType(status) { return ({ PASSED: 'success', REJECTED: 'danger', WAITING_APPROVAL: 'warning', IN_PROGRESS: 'primary', UPLOADING: 'warning' })[status] || 'info' }
async function editProcessTemplate(row) { try { const template = (await interviewApi.getProcessTemplate(row.id)).data; Object.assign(templateForm, { id: template.id, templateName: template.templateName, description: template.description || '', status: template.status ?? 1, stages: (template.stages || []).map((stage) => ({ key: `${stage.id}-${Date.now()}`, stageName: stage.stageName, stageType: stage.stageType, knowledgeBaseId: stage.knowledgeBaseId || null })) }); } catch (error) { fail(error) } }
async function saveProcessTemplate() { if (!templateForm.templateName.trim()) { ElMessage.warning('请填写模板名称'); return } if (!templateForm.stages.length) { ElMessage.warning('请至少添加一个面试阶段'); return } const invalidAiStage = templateForm.stages.find((stage) => stage.stageType === 'AI' && !stage.knowledgeBaseId); if (invalidAiStage) { ElMessage.warning(`请为“${invalidAiStage.stageName || 'AI 面试'}”选择题库`); return } const invalidName = templateForm.stages.find((stage) => !stage.stageName.trim()); if (invalidName) { ElMessage.warning('请填写每个阶段的展示名称'); return } savingTemplate.value = true; try { const saved = (await interviewApi.saveProcessTemplate({ id: templateForm.id, templateName: templateForm.templateName.trim(), description: templateForm.description.trim(), status: templateForm.status, stages: templateForm.stages.map((stage, index) => ({ stageName: stage.stageName.trim(), stageType: stage.stageType, knowledgeBaseId: stage.stageType === 'AI' ? stage.knowledgeBaseId : null, sequenceNo: index + 1 })) })).data; ElMessage.success('流程模板已保存'); await loadAll(); await editProcessTemplate(saved) } catch (error) { fail(error) } finally { savingTemplate.value = false } }
async function deleteProcessTemplate(id) { try { await interviewApi.deleteProcessTemplate(id); ElMessage.success('流程模板已删除'); if (templateForm.id === id) resetTemplateForm(); await loadAll() } catch (error) { fail(error) } }
function editLlmConfig(row) { Object.assign(llmFormByRole(row.modelRole), { ...row, apiKey: '' }) }
function createLlmForm(role) { return { id: null, configName: role === 'SCORER' ? '评分模型' : role === 'RESUME_REVIEW' ? '简历初筛模型' : role === 'VIDEO_TRANSCRIBER' ? '阿里云视频语音转文字' : role === 'VIDEO_SUMMARY' ? '视频会议概要模型' : '面试官模型', modelRole: role, baseUrl: role === 'VIDEO_TRANSCRIBER' ? 'wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1' : '', apiKey: '', modelName: '', promptTemplate: '', scoringRulePrompt: '', status: 1 } }
function llmFormByRole(role) { return role === 'SCORER' ? scorerLlmForm : role === 'RESUME_REVIEW' ? resumeReviewLlmForm : role === 'VIDEO_TRANSCRIBER' ? videoTranscriberLlmForm : role === 'VIDEO_SUMMARY' ? videoSummaryLlmForm : interviewerLlmForm }
function syncLlmForms() {
  const interviewer = llmConfigs.value.find((item) => item.modelRole === 'INTERVIEWER')
  const scorer = llmConfigs.value.find((item) => item.modelRole === 'SCORER')
  const resumeReview = llmConfigs.value.find((item) => item.modelRole === 'RESUME_REVIEW')
  const videoTranscriber = llmConfigs.value.find((item) => item.modelRole === 'VIDEO_TRANSCRIBER')
  const videoSummary = llmConfigs.value.find((item) => item.modelRole === 'VIDEO_SUMMARY')
  Object.assign(interviewerLlmForm, interviewer ? { ...interviewer, apiKey: '' } : createLlmForm('INTERVIEWER'))
  Object.assign(scorerLlmForm, scorer ? { ...scorer, apiKey: '' } : createLlmForm('SCORER'))
  Object.assign(resumeReviewLlmForm, resumeReview ? { ...resumeReview, apiKey: '' } : createLlmForm('RESUME_REVIEW'))
  Object.assign(videoTranscriberLlmForm, videoTranscriber ? { ...videoTranscriber, apiKey: '' } : createLlmForm('VIDEO_TRANSCRIBER'))
  Object.assign(videoSummaryLlmForm, videoSummary ? { ...videoSummary, apiKey: '' } : createLlmForm('VIDEO_SUMMARY'))
}
async function syncIntervieweeByCandidate(candidateId) { const candidate = recruitmentCandidates.value.find((item) => item.id === candidateId); processForm.intervieweeUserId = candidate?.intervieweeUserId ? String(candidate.intervieweeUserId) : ''; processForm.jobId = candidate?.jobId || null }
async function startProcess() { try { if (!processForm.recruitmentCandidateId) { ElMessage.warning('请先选择候选人投递记录'); return } if (!processForm.intervieweeUserId) { ElMessage.warning('未匹配到候选人账号'); return } if (!processForm.jobId) { ElMessage.warning('投递记录未绑定岗位'); return } if (!processForm.templateId) { ElMessage.warning('请选择流程模板'); return } if (processForm.aiMaxQuestionRounds < processForm.aiMinQuestionRounds) { ElMessage.warning('AI最多问答轮数不能小于最少问答轮数'); return } const response = await interviewApi.startProcess({ ...processForm, intervieweeUserId: Number(processForm.intervieweeUserId) }); selectedProcess.value = response.data; ElMessage.success('面试流程已发起'); await loadAll() } catch (error) { fail(error) } }
async function loadProcessDetail(row) {
  selectedProcess.value = row
  processRemark.value = row?.remark || ''
  aiRecords.value = (await interviewApi.listAiRecords({ processId: row.id })).data
  selectedCandidate.value = row.recruitmentCandidateId ? (await recruitmentApi.getCandidate(row.recruitmentCandidateId)).data : null
}
async function approveAi(process, approved) { try { await interviewApi.approveAi(process.id, { approved }); ElMessage.success(`${process.stageName || 'AI面试'}审批完成`); await loadAll() } catch (error) { fail(error) } }
async function approveVideo(approved) { try { await interviewApi.approveVideo(selectedProcess.value.id, { approved }); ElMessage.success(`${selectedProcess.value?.stageName || '视频面试'}审批完成`); await loadAll() } catch (error) { fail(error) } }
async function approveOnsite(approved) { try { await interviewApi.approveOnsite(selectedProcess.value.id, { approved }); ElMessage.success('线下面审批完成'); await loadAll() } catch (error) { fail(error) } }
async function terminateProcess() { try { await interviewApi.terminateProcess(selectedProcess.value.id, { approved: 0 }); ElMessage.success('流程已终止'); await loadAll() } catch (error) { fail(error) } }
async function saveProcessRemark() {
  if (!selectedProcess.value) return
  savingRemark.value = true
  try {
    selectedProcess.value = (await interviewApi.updateProcessRemark(selectedProcess.value.id, { comment: processRemark.value })).data
    processRemark.value = selectedProcess.value.remark || ''
    ElMessage.success('备注已保存')
    await loadAll()
  } catch (error) { fail(error) } finally { savingRemark.value = false }
}

async function retryVideoSummary() {
  if (!selectedProcess.value || retryingVideoSummary.value) return
  retryingVideoSummary.value = true
  try {
    selectedProcess.value = (await interviewApi.retryVideoSummary(selectedProcess.value.id)).data
    ElMessage.success('已开始重新生成转写与会议概要')
    for (let attempt = 0; attempt < 90; attempt += 1) {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      const rows = (await interviewApi.listProcesses()).data
      const latest = rows.find((item) => item.id === selectedProcess.value.id)
      if (latest) selectedProcess.value = latest
      if (!['PENDING', 'PROCESSING'].includes(latest?.summaryStatus)) break
    }
    if (selectedProcess.value.summaryStatus === 'COMPLETED') ElMessage.success('转写与会议概要已生成')
    else if (selectedProcess.value.summaryStatus?.startsWith('FAILED')) ElMessage.error(selectedProcess.value.summaryStatus)
  } catch (error) {
    fail(error)
  } finally {
    retryingVideoSummary.value = false
  }
}

async function copyVideoJoinLink() {
  if (!selectedProcess.value?.videoJoinLink) return
  const url = new URL(selectedProcess.value.videoJoinLink, window.location.origin).toString()
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('候选人视频链接已复制')
  } catch {
    window.prompt('复制候选人视频链接', url)
  }
}

async function startHrVideoCall() {
  if (!selectedProcess.value) return
  try {
    disconnectHrVideo()
    const sessionResponse = await interviewApi.createVideoSession(selectedProcess.value.id, {
      approverUserId: sessionUser.value?.id,
      approverName: sessionUser.value?.displayName || sessionUser.value?.username,
    })
    selectedProcess.value.videoJoinLink = sessionResponse.data?.videoJoinLink || selectedProcess.value.videoJoinLink
    hrLocalStream = await requestCameraAndMicrophone()
    hrLocalVideo.value.srcObject = hrLocalStream
    playVideo(hrLocalVideo.value)
    hrPeer = createPeerConnection(await loadIceServers())
    addedIntervieweeIce = new Set()
    pendingIntervieweeIce = []
    hrLocalStream.getTracks().forEach((track) => hrPeer.addTrack(track, hrLocalStream))
    hrRemoteStream = null
    hrPeer.ontrack = (event) => { hrRemoteStream = attachRemoteTrack(hrRemoteVideo.value, event, hrRemoteStream) }
    hrPeer.onconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(hrPeer.connectionState)) {
        ElMessage.warning('远端视频连接不稳定，请双方保持页面打开，必要时重新开始视频面')
      }
    }
    hrPeer.onicecandidate = async (event) => {
      if (isRelayIceCandidate(event.candidate)) {
        await interviewApi.addHrIce(selectedProcess.value.id, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    const offer = await hrPeer.createOffer()
    await hrPeer.setLocalDescription(offer)
    await interviewApi.publishVideoOffer(selectedProcess.value.id, { offerSdp: JSON.stringify(offer) })
    await interviewApi.hrJoin(selectedProcess.value.id)
    videoActive.value = true
    hrPollTimer = setInterval(async () => {
      const state = (await interviewApi.getHrVideoState(selectedProcess.value.id)).data
      if (state.answerSdp && !hrPeer.currentRemoteDescription) {
        await hrPeer.setRemoteDescription(JSON.parse(state.answerSdp))
        await flushPendingIntervieweeIce()
      }
      if (state.intervieweeIceCandidates) {
        const candidates = state.intervieweeIceCandidates.split('\n').filter(Boolean)
        for (const item of candidates) {
          if (!addedIntervieweeIce.has(item)) {
            addedIntervieweeIce.add(item)
            await addIntervieweeIceCandidate(item)
          }
        }
      }
      if (state.sessionStatus === 'RECORDING') {
        startHrRecordingIfNeeded()
      }
      if (shouldHandleHrRecordingEnd(state)) {
        handledHrRecordingEndSignal = hrRecordingEndSignalKey(state)
        clearInterval(hrPollTimer)
        hrPollTimer = null
        scheduleHrRecordingStop(state.recordingEndRequestedAt)
      }
    }, 1000)
    ElMessage.success('HR视频已就绪，等待面试者加入后同步开始录制')
  } catch (error) { ElMessage.error(buildMediaErrorMessage(error)) }
}

async function stopHrRecording() {
  try {
    clearInterval(hrPollTimer)
    hrPollTimer = null
    const response = await interviewApi.completeVideo(selectedProcess.value.id)
    handledHrRecordingEndSignal = hrRecordingEndSignalKey(response.data || {})
    scheduleHrRecordingStop(response.data?.recordingEndRequestedAt)
  } catch (error) { fail(error) }
}

function hrRecordingEndSignalKey(state) {
  return state.recordingEndRequestedAt || (state.sessionStatus === 'END_REQUESTED' ? 'END_REQUESTED' : '')
}

function shouldHandleHrRecordingEnd(state) {
  const signal = hrRecordingEndSignalKey(state)
  return signal && signal !== handledHrRecordingEndSignal
}

function scheduleHrRecordingStop(endAt) {
  clearTimeout(hrRecordingEndTimer)
  const delay = Math.max(new Date(endAt || Date.now()).getTime() - Date.now(), 0)
  hrRecordingEndTimer = setTimeout(async () => {
    try {
      await stopAndUploadHrRecording()
      disconnectHrVideo()
      await loadAll()
    } catch (error) {
      fail(error)
    }
  }, delay)
}

function startHrRecordingIfNeeded() {
  if (!hrLocalStream || (hrRecorder && hrRecorder.state !== 'inactive')) return
  hrRecorder = new MediaRecorder(hrLocalStream)
  hrRecordedChunks = []
  hrRecorder.ondataavailable = (event) => { if (event.data.size > 0) hrRecordedChunks.push(event.data) }
  hrRecorder.start(1000)
  ElMessage.success('双方已进入视频面，录制已同步开始')
}

async function stopAndUploadHrRecording() {
  if (hrRecordingStopInProgress) return
  if ((!hrRecorder || hrRecorder.state === 'inactive') && hrRecordedChunks.length === 0) return
  hrRecordingStopInProgress = true
  try {
    if (hrRecorder && hrRecorder.state !== 'inactive') {
      const currentRecorder = hrRecorder
      await new Promise((resolve) => {
        currentRecorder.onstop = resolve
        currentRecorder.stop()
      })
      hrRecorder = null
    }
    const blob = new Blob(hrRecordedChunks, { type: 'video/webm' })
    if (blob.size > 0) {
      const file = new File([blob], `hr-${selectedProcess.value.id}.webm`, { type: 'video/webm' })
      await interviewApi.uploadHrVideoRecording(selectedProcess.value.id, file)
      hrRecordedChunks = []
      ElMessage.success('HR录制已上传')
    }
  } finally {
    hrRecordingStopInProgress = false
  }
}

function disconnectHrVideo() {
  clearInterval(hrPollTimer)
  clearTimeout(hrRecordingEndTimer)
  hrPollTimer = null
  hrRecordingEndTimer = null
  hrPeer?.getSenders?.().forEach((sender) => sender.track?.stop())
  hrPeer?.close()
  hrPeer = null
  hrRecorder = null
  hrRecordedChunks = []
  hrRecordingStopInProgress = false
  handledHrRecordingEndSignal = ''
  hrLocalStream?.getTracks().forEach((track) => track.stop())
  hrLocalStream = null
  hrRemoteStream = null
  pendingIntervieweeIce = []
  if (hrLocalVideo.value) hrLocalVideo.value.srcObject = null
  if (hrRemoteVideo.value) hrRemoteVideo.value.srcObject = null
  videoActive.value = false
}

async function addIntervieweeIceCandidate(item) {
  if (!hrPeer?.remoteDescription) {
    pendingIntervieweeIce.push(item)
    return
  }
  try {
    await hrPeer.addIceCandidate(JSON.parse(item))
  } catch (error) {
    console.warn('添加面试者 ICE失败', error)
  }
}

async function flushPendingIntervieweeIce() {
  const items = pendingIntervieweeIce
  pendingIntervieweeIce = []
  for (const item of items) {
    await addIntervieweeIceCandidate(item)
  }
}

async function loadIceServers() {
  try {
    const response = await interviewApi.getIceServers()
    return response.data?.length ? response.data : defaultIceServers()
  } catch {
    return defaultIceServers()
  }
}

onBeforeUnmount(() => {
  disconnectHrVideo()
})

async function syncRouteState() {
  const id = Number(route.params.id)
  if (!id) return
  if (route.name === 'interview-knowledge-base-detail') {
    const row = knowledgeBases.value.find((item) => item.id === id)
    if (row) await selectKnowledgeBase(row)
  } else if (route.name === 'interview-weight-detail') {
    const row = weights.value.find((item) => item.id === id)
    if (row) Object.assign(weightForm, row)
  } else if (route.name === 'interview-llm-config-detail') {
    const row = llmConfigs.value.find((item) => item.id === id)
    if (row) editLlmConfig(row)
  } else if (route.name === 'interview-process-detail') {
    const row = processes.value.find((item) => item.id === id)
    if (row) await loadProcessDetail(row)
  }
}

watch(() => route.fullPath, () => {
  syncRouteState()
})

onMounted(loadAll)
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.sub-tabs { margin: 22px 0; }
.form-grid { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.llm-config-grid { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.wide { grid-column: 1 / -1; }
.section-title { margin-top: 28px; }
.inner-surface { min-width: 0; background: var(--surface); }
.detail-surface { margin-top: 18px; }
.detail-headline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.detail-headline h3 { margin: 6px 0 0; }
.process-workbench { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); grid-template-rows: auto auto; gap: 16px; }
.workbench-panel { min-width: 0; border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 16px; background: var(--surface); box-shadow: var(--shadow-card); }
.workbench-panel h3 { margin: 0 0 12px; }
.candidate-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.candidate-info-grid div, .resume-ai-box, .process-stats { border-radius: var(--radius-sm); padding: 10px; background: var(--surface-soft); border: 1px solid var(--border); }
.candidate-info-grid span, .resume-ai-box span, .remark-box span { display: block; color: var(--text-muted); margin-bottom: 5px; }
.candidate-info-grid strong, .resume-ai-box strong { color: var(--ink); }
.resume-ai-box { margin-top: 12px; }
.resume-ai-box p { margin: 8px 0 0; line-height: 1.7; }
.panel-title-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.panel-title-row span { color: var(--primary); font-weight: 700; }
.question-number-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.question-number { border: 1px solid var(--border-strong); background: var(--surface); color: var(--ink); border-radius: 999px; padding: 7px 12px; font-weight: 700; }
.question-number.answered { background: var(--primary); color: #ffffff; border-color: var(--primary); }
.compact-ai-table { margin-top: 8px; }
.process-stats { display: grid; gap: 4px; margin-bottom: 12px; }
.process-stats p { margin: 0; color: var(--ink-soft); }
.action-button-grid { min-width: 0; display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
.template-heading, .stage-list-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.template-heading { margin-bottom: 18px; }.template-heading h3, .stage-list-head h4 { margin: 0; }.stage-list-head { margin: 18px 0 12px; }.stage-list-head span, .template-option-detail, .human-stage-note { color: var(--text-muted); font-size: 12px; }.template-stage-list { display: grid; gap: 10px; }.template-stage-row { display: grid; grid-template-columns: 32px minmax(150px, 1.2fr) minmax(180px, .9fr) minmax(180px, 1fr) auto; gap: 10px; align-items: center; padding: 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface-soft); }.stage-order { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: #dceee7; color: #175c50; font-weight: 800; font-size: 13px; }.stage-type-switch { white-space: nowrap; }.human-stage-note { min-width: 160px; }.stage-row-actions { display: flex; gap: 2px; white-space: nowrap; }.template-stage-summary { color: var(--text-muted); line-height: 1.6; }.template-option-detail { display: block; margin-top: 3px; }.stage-overview-panel { grid-column: 1 / -1; }.process-stage-timeline { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }.process-stage-timeline li { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--border); }.process-stage-timeline li:last-child { border-bottom: 0; }.process-stage-timeline li.active { color: var(--primary); }.process-stage-timeline li.complete { opacity: .72; }.timeline-index { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: #e9edf0; color: #66737c; font-size: 12px; font-weight: 800; }.process-stage-timeline li.active .timeline-index { background: #dceee7; color: #175c50; }.process-stage-timeline strong, .process-stage-timeline small { display: block; }.process-stage-timeline small { margin-top: 3px; color: var(--text-muted); font-size: 12px; }
.remark-box { display: grid; gap: 10px; }
.csv-import-box { grid-column: 1 / -1; }
.serial-line { min-width: 0; margin: 8px 0 14px; color: var(--ink-soft); overflow-wrap: anywhere; }
.video-link { display: inline-flex; max-width: 100%; margin-left: 12px; color: var(--primary); font-weight: 700; text-decoration: none; overflow-wrap: anywhere; }
.candidate-preview { margin: 10px 0 16px; padding: 16px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--surface); }
.candidate-preview h4 { margin: 0 0 12px; }
.preview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.preview-grid span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 4px; }
.preview-grid strong { color: var(--ink); }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; margin: 12px 0 18px; }
.video-box { background: var(--surface); padding: 12px; border-radius: var(--radius-md); border: 1px solid var(--border); }
.video-box span { display: block; margin-bottom: 8px; color: var(--text-muted); }
.video-box video { width: 100%; min-height: 220px; background: #111; border-radius: var(--radius-md); }
.video-summary-box { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-soft); }
.video-summary-box span { color: var(--primary); font-weight: 800; }
.summary-status-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.video-summary-box p { margin: 0; color: var(--ink-soft); line-height: 1.7; }
.video-summary-box strong { display: block; margin-bottom: 4px; color: var(--ink); }
.summary-document { display: grid; gap: 7px; max-height: 460px; padding-right: 8px; overflow-y: auto; }
.summary-document h4 { margin: 12px 0 2px; color: var(--ink); font-size: 15px; }
.summary-document .summary-list-item { position: relative; padding-left: 16px; }
.summary-document .summary-list-item::before { position: absolute; left: 2px; color: var(--primary); content: '•'; }
.transcript-text { white-space: pre-wrap; }
.data-table { margin-top: 18px; }
.config-heading { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; }.config-heading h3, .config-panel-head h3 { margin: 0; }.config-heading .serial-line { margin: 8px 0 0; }.env-badge { padding: 5px 9px; border: 1px solid #bfd3c9; border-radius: 5px; background: #edf6f1; color: #175c50; font-size: 12px; font-weight: 700; }
.config-tabs, .model-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 24px 0; padding-bottom: 12px; border-bottom: 1px solid var(--border); }.config-tabs button, .model-tabs button { padding: 10px 14px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--ink-soft); font: inherit; font-size: 13px; font-weight: 650; cursor: pointer; }.config-tabs button:hover, .model-tabs button:hover { color: var(--primary); border-color: var(--primary); }.config-tabs button.active, .model-tabs button.active { color: #fff; background: var(--primary); border-color: var(--primary); }
.config-panel-head { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin: 6px 0 18px; }.config-panel-head p { margin: 7px 0 0; color: var(--text-muted); line-height: 1.6; }.config-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 18px; padding: 20px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-soft); }.model-editor { max-width: 880px; }.key-state { color: var(--text-muted); font-size: 13px; }.model-editor .action-row { margin-top: 18px; }
@media (max-width: 900px) { .topline, .detail-headline { flex-direction: column; } .form-grid, .video-grid, .llm-config-grid, .preview-grid, .process-workbench, .candidate-info-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .template-stage-row { grid-template-columns: 32px 1fr; }.stage-type-switch, .template-stage-row > .el-select, .human-stage-note, .stage-row-actions { grid-column: 2; }.stage-row-actions { flex-wrap: wrap; } }
@media (max-width: 700px) { .config-form-grid { grid-template-columns: 1fr; } .config-heading, .config-panel-head { flex-direction: column; } }
</style>
