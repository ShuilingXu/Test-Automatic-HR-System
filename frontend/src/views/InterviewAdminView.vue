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
        <RouterLink
          class="link-chip"
          :class="{ active: activeTab === 'kb' }"
          to="/interview/hr/knowledge-bases"
          >知识库</RouterLink
        >
        <RouterLink
          class="link-chip"
          :class="{ active: activeTab === 'weights' }"
          to="/interview/hr/weights"
          >岗位权重</RouterLink
        >
        <RouterLink
          v-if="isItAdmin"
          class="link-chip"
          :class="{ active: activeTab === 'system' }"
          to="/interview/hr/system"
          >系统配置</RouterLink
        >
        <RouterLink
          class="link-chip"
          :class="{ active: activeTab === 'process' }"
          to="/interview/hr/processes"
          >面试流程</RouterLink
        >
      </div>

      <section v-if="activeTab === 'kb'" class="surface">
        <h3>知识库与知识点</h3>
        <div class="page-grid">
          <el-form
            :model="kbForm"
            label-position="top"
            class="surface inner-surface"
          >
            <el-form-item label="知识库名称"
              ><el-input v-model="kbForm.knowledgeBaseName"
            /></el-form-item>
            <el-form-item label="技术方向"
              ><el-input v-model="kbForm.techCategory"
            /></el-form-item>
            <el-form-item label="岗位方向"
              ><el-input v-model="kbForm.jobCategory"
            /></el-form-item>
            <el-button type="primary" @click="saveKnowledgeBase"
              >保存知识库</el-button
            >
          </el-form>
          <el-form
            :model="itemForm"
            label-position="top"
            class="surface inner-surface"
          >
            <el-form-item label="所属知识库"
              ><el-select v-model="itemForm.knowledgeBaseId"
                ><el-option
                  v-for="item in knowledgeBases"
                  :key="item.id"
                  :label="item.knowledgeBaseName"
                  :value="item.id" /></el-select
            ></el-form-item>
            <el-form-item label="知识点"
              ><el-input v-model="itemForm.knowledgePoint"
            /></el-form-item>
            <el-form-item label="知识内容"
              ><el-input
                v-model="itemForm.knowledgeContent"
                type="textarea"
                :rows="4"
            /></el-form-item>
            <div class="action-row">
              <el-button type="primary" @click="saveKnowledgeItem">{{
                itemForm.id ? "保存修改" : "保存知识点"
              }}</el-button
              ><el-button @click="resetKnowledgeItemForm">清空新增</el-button>
            </div>
          </el-form>
          <div class="surface inner-surface csv-import-box">
            <h3>CSV批量添加知识点</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="目标知识库"
                ><el-select v-model="itemForm.knowledgeBaseId"
                  ><el-option
                    v-for="item in knowledgeBases"
                    :key="item.id"
                    :label="item.knowledgeBaseName"
                    :value="item.id" /></el-select
              ></el-form-item>
              <el-form-item label="CSV格式"
                ><el-input
                  model-value="knowledgePoint,knowledgeContent,status"
                  disabled
              /></el-form-item>
            </el-form>
            <p class="serial-line">
              支持UTF-8或GBK
              CSV。第一列知识点，第二列知识内容，第三列状态可选，1启用、0停用。
            </p>
            <div class="action-row">
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept=".csv,text/csv"
                :on-change="importKnowledgeItemsCsv"
              >
                <el-button type="primary">上传CSV批量添加</el-button>
              </el-upload>
              <a class="link-chip" href="/knowledge-items-template.csv" download
                >下载CSV模板</a
              >
            </div>
          </div>
        </div>
        <el-table
          :data="knowledgeBases"
          stripe
          class="data-table"
          @row-click="openKnowledgeBase"
        >
          <el-table-column prop="knowledgeBaseName" label="知识库" />
          <el-table-column prop="techCategory" label="技术方向" />
          <el-table-column prop="jobCategory" label="岗位方向" />
          <el-table-column label="操作" width="100"
            ><template #default="scope"
              ><el-button
                text
                type="danger"
                @click.stop="deleteKnowledgeBase(scope.row.id)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <el-table
          :data="knowledgeItems"
          stripe
          class="data-table"
          @row-click="editKnowledgeItem"
        >
          <el-table-column prop="knowledgePoint" label="知识点" />
          <el-table-column
            prop="knowledgeContent"
            label="知识内容"
            min-width="280"
          />
          <el-table-column prop="status" label="状态" width="90"
            ><template #default="scope">{{
              scope.row.status === 0 ? "停用" : "启用"
            }}</template></el-table-column
          >
          <el-table-column label="操作" width="160"
            ><template #default="scope"
              ><el-button text @click.stop="editKnowledgeItem(scope.row)"
                >编辑</el-button
              ><el-button
                text
                type="danger"
                @click.stop="deleteKnowledgeItem(scope.row.id)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </section>

      <section v-if="activeTab === 'weights'" class="surface">
        <h3>岗位知识权重</h3>
        <el-form :model="weightForm" label-position="top" class="form-grid">
          <el-form-item label="招聘岗位"
            ><el-select v-model="weightForm.jobId"
              ><el-option
                v-for="job in jobs"
                :key="job.id"
                :label="job.jobTitle"
                :value="job.id" /></el-select
          ></el-form-item>
          <el-form-item label="知识库"
            ><el-select v-model="weightForm.knowledgeBaseId"
              ><el-option
                v-for="item in knowledgeBases"
                :key="item.id"
                :label="item.knowledgeBaseName"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="权重"
            ><el-input-number v-model="weightForm.weight" :min="1"
          /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveWeight">保存权重</el-button>
        <el-table
          :data="weights"
          stripe
          class="data-table"
          @row-click="openWeight"
        >
          <el-table-column prop="jobId" label="岗位ID" />
          <el-table-column prop="knowledgeBaseId" label="知识库ID" />
          <el-table-column prop="weight" label="权重" />
          <el-table-column label="操作" width="100"
            ><template #default="scope"
              ><el-button
                text
                type="danger"
                @click.stop="deleteWeight(scope.row.id)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </section>

      <section v-if="activeTab === 'system'" class="surface">
        <h3>系统配置</h3>
        <p class="serial-line">
          配置写入服务器 .env
          文件，部分配置需要重启服务生效。敏感字段显示为掩码，保存时留空则不覆盖。
        </p>
        <div class="llm-config-grid">
          <div class="surface inner-surface">
            <h3>阿里云短信</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="AccessKey ID"
                ><el-input v-model="systemConfig.ALIYUN_ACCESS_KEY_ID"
              /></el-form-item>
              <el-form-item label="AccessKey Secret"
                ><el-input
                  v-model="systemConfig.ALIYUN_ACCESS_KEY_SECRET"
                  type="password"
                  show-password
                  placeholder="留空则不覆盖"
              /></el-form-item>
              <el-form-item label="短信签名"
                ><el-input v-model="systemConfig.ALIYUN_SMS_SIGN_NAME"
              /></el-form-item>
              <el-form-item label="短信模板Code"
                ><el-input v-model="systemConfig.ALIYUN_SMS_TEMPLATE_CODE"
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>阿里云语音识别 STT</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="NLS AppKey"
                ><el-input v-model="systemConfig.ALIYUN_STT_APP_KEY"
              /></el-form-item>
              <el-form-item label="NLS Endpoint"
                ><el-input
                  v-model="systemConfig.ALIYUN_STT_ENDPOINT"
                  placeholder="nls-gateway-cn-shanghai.aliyuncs.com"
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>阿里云 OSS</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="Bucket名称"
                ><el-input v-model="systemConfig.ALIYUN_OSS_BUCKET_NAME"
              /></el-form-item>
              <el-form-item label="Endpoint"
                ><el-input v-model="systemConfig.ALIYUN_OSS_ENDPOINT"
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>视频面试 / WebRTC</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="FFmpeg路径"
                ><el-input v-model="systemConfig.INTERVIEW_VIDEO_FFMPEG_PATH"
              /></el-form-item>
              <el-form-item label="视频编码"
                ><el-input v-model="systemConfig.INTERVIEW_VIDEO_VIDEO_CODEC"
              /></el-form-item>
              <el-form-item label="音频编码"
                ><el-input v-model="systemConfig.INTERVIEW_VIDEO_AUDIO_CODEC"
              /></el-form-item>
              <el-form-item label="STUN URLs"
                ><el-input v-model="systemConfig.INTERVIEW_STUN_URLS"
              /></el-form-item>
              <el-form-item label="TURN URLs"
                ><el-input v-model="systemConfig.INTERVIEW_TURN_URLS"
              /></el-form-item>
              <el-form-item label="TURN Username"
                ><el-input v-model="systemConfig.INTERVIEW_TURN_USERNAME"
              /></el-form-item>
              <el-form-item label="TURN Credential"
                ><el-input
                  v-model="systemConfig.INTERVIEW_TURN_CREDENTIAL"
                  type="password"
                  show-password
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>数据库</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="数据库类型"
                ><el-input
                  v-model="systemConfig.DB_TYPE"
                  placeholder="sqlite / mysql / pgsql"
              /></el-form-item>
              <el-form-item label="数据库URL"
                ><el-input v-model="systemConfig.DB_URL"
              /></el-form-item>
              <el-form-item label="用户名"
                ><el-input v-model="systemConfig.DB_USERNAME"
              /></el-form-item>
              <el-form-item label="密码"
                ><el-input
                  v-model="systemConfig.DB_PASSWORD"
                  type="password"
                  show-password
                  placeholder="留空则不覆盖"
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>简历 OCR</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="启用OCR"
                ><el-input
                  v-model="systemConfig.RESUME_OCR_ENABLED"
                  placeholder="true / false"
              /></el-form-item>
              <el-form-item label="Tesseract路径"
                ><el-input v-model="systemConfig.RESUME_OCR_TESSERACT_PATH"
              /></el-form-item>
              <el-form-item label="识别语言"
                ><el-input
                  v-model="systemConfig.RESUME_OCR_LANGUAGE"
                  placeholder="chi_sim+eng"
              /></el-form-item>
              <el-form-item label="DPI"
                ><el-input
                  v-model="systemConfig.RESUME_OCR_DPI"
                  placeholder="200"
              /></el-form-item>
              <el-form-item label="最大页数"
                ><el-input
                  v-model="systemConfig.RESUME_OCR_MAX_PAGES"
                  placeholder="5"
              /></el-form-item>
            </el-form>
          </div>
          <div class="surface inner-surface">
            <h3>SMTP 邮件</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="SMTP服务器"
                ><el-input
                  v-model="systemConfig.SMTP_HOST"
                  placeholder="smtp.example.com"
              /></el-form-item>
              <el-form-item label="端口"
                ><el-input v-model="systemConfig.SMTP_PORT" placeholder="465"
              /></el-form-item>
              <el-form-item label="用户名"
                ><el-input v-model="systemConfig.SMTP_USERNAME"
              /></el-form-item>
              <el-form-item label="密码"
                ><el-input
                  v-model="systemConfig.SMTP_PASSWORD"
                  type="password"
                  show-password
                  placeholder="留空则不覆盖"
              /></el-form-item>
              <el-form-item label="发件人地址"
                ><el-input
                  v-model="systemConfig.SMTP_FROM"
                  placeholder="noreply@example.com"
              /></el-form-item>
              <el-form-item label="SSL"
                ><el-input
                  v-model="systemConfig.SMTP_SSL"
                  placeholder="true / false"
              /></el-form-item>
            </el-form>
          </div>
        </div>
        <h3 style="margin-top: 24px">LLM 模型连接配置</h3>
        <div class="llm-config-grid">
          <div class="surface inner-surface">
            <h3>面试官 LLM A</h3>
            <el-form
              :model="interviewerLlmForm"
              label-position="top"
              class="form-grid"
            >
              <el-form-item label="配置名称"
                ><el-input v-model="interviewerLlmForm.configName"
              /></el-form-item>
              <el-form-item label="OpenAI接口地址"
                ><el-input v-model="interviewerLlmForm.baseUrl"
              /></el-form-item>
              <el-form-item label="API Key"
                ><el-input
                  v-model="interviewerLlmForm.apiKey"
                  type="password"
                  show-password
                  placeholder="编辑留空则保留原密钥"
              /></el-form-item>
              <el-form-item label="模型名称"
                ><el-input v-model="interviewerLlmForm.modelName"
              /></el-form-item>
              <el-form-item label="提问提示词模板" class="wide"
                ><el-input
                  v-model="interviewerLlmForm.promptTemplate"
                  type="textarea"
                  :rows="4"
              /></el-form-item>
            </el-form>
            <div class="action-row">
              <el-button
                type="primary"
                @click="saveRoleLlmConfig(interviewerLlmForm, 'INTERVIEWER')"
                >保存面试官模型</el-button
              ><span class="serial-line"
                >API Key：{{ interviewerKeyLabel }}</span
              >
            </div>
          </div>
          <div class="surface inner-surface">
            <h3>评分 LLM B</h3>
            <el-form
              :model="scorerLlmForm"
              label-position="top"
              class="form-grid"
            >
              <el-form-item label="配置名称"
                ><el-input v-model="scorerLlmForm.configName"
              /></el-form-item>
              <el-form-item label="OpenAI接口地址"
                ><el-input v-model="scorerLlmForm.baseUrl"
              /></el-form-item>
              <el-form-item label="API Key"
                ><el-input
                  v-model="scorerLlmForm.apiKey"
                  type="password"
                  show-password
                  placeholder="编辑留空则保留原密钥"
              /></el-form-item>
              <el-form-item label="模型名称"
                ><el-input v-model="scorerLlmForm.modelName"
              /></el-form-item>
              <el-form-item label="系统级评分提示词" class="wide"
                ><el-input
                  v-model="scorerLlmForm.scoringRulePrompt"
                  type="textarea"
                  :rows="4"
              /></el-form-item>
            </el-form>
            <div class="action-row">
              <el-button
                type="primary"
                @click="saveRoleLlmConfig(scorerLlmForm, 'SCORER')"
                >保存评分模型</el-button
              ><span class="serial-line">API Key：{{ scorerKeyLabel }}</span>
            </div>
          </div>
          <div class="surface inner-surface">
            <h3>简历初筛 LLM</h3>
            <el-form
              :model="resumeReviewLlmForm"
              label-position="top"
              class="form-grid"
            >
              <el-form-item label="配置名称"
                ><el-input v-model="resumeReviewLlmForm.configName"
              /></el-form-item>
              <el-form-item label="OpenAI接口地址"
                ><el-input v-model="resumeReviewLlmForm.baseUrl"
              /></el-form-item>
              <el-form-item label="API Key"
                ><el-input
                  v-model="resumeReviewLlmForm.apiKey"
                  type="password"
                  show-password
                  placeholder="编辑留空则保留原密钥"
              /></el-form-item>
              <el-form-item label="模型名称"
                ><el-input v-model="resumeReviewLlmForm.modelName"
              /></el-form-item>
              <el-form-item label="用户填写提示词" class="wide"
                ><el-input
                  v-model="resumeReviewLlmForm.scoringRulePrompt"
                  type="textarea"
                  :rows="4"
                  placeholder="例如：重点考察岗位硬技能、项目经历、稳定性和薪资匹配度"
              /></el-form-item>
            </el-form>
            <div class="action-row">
              <el-button
                type="primary"
                @click="saveRoleLlmConfig(resumeReviewLlmForm, 'RESUME_REVIEW')"
                >保存简历初筛模型</el-button
              ><span class="serial-line"
                >API Key：{{ resumeReviewKeyLabel }}</span
              >
            </div>
          </div>
          <div class="surface inner-surface">
            <h3>视频概要 LLM</h3>
            <el-form
              :model="videoSummaryLlmForm"
              label-position="top"
              class="form-grid"
            >
              <el-form-item label="配置名称"
                ><el-input v-model="videoSummaryLlmForm.configName"
              /></el-form-item>
              <el-form-item label="OpenAI接口地址"
                ><el-input v-model="videoSummaryLlmForm.baseUrl"
              /></el-form-item>
              <el-form-item label="API Key"
                ><el-input
                  v-model="videoSummaryLlmForm.apiKey"
                  type="password"
                  show-password
                  placeholder="编辑留空则保留原密钥"
              /></el-form-item>
              <el-form-item label="模型名称"
                ><el-input v-model="videoSummaryLlmForm.modelName"
              /></el-form-item>
              <el-form-item label="会议概要提示词" class="wide"
                ><el-input
                  v-model="videoSummaryLlmForm.scoringRulePrompt"
                  type="textarea"
                  :rows="4"
                  placeholder="例如：输出会议概要、关键回答、候选人优势、风险点和后续建议"
              /></el-form-item>
            </el-form>
            <div class="action-row">
              <el-button
                type="primary"
                @click="saveRoleLlmConfig(videoSummaryLlmForm, 'VIDEO_SUMMARY')"
                >保存视频概要模型</el-button
              ><span class="serial-line"
                >API Key：{{ videoSummaryKeyLabel }}</span
              >
            </div>
          </div>
        </div>
        <el-table
          :data="llmConfigs"
          stripe
          class="data-table"
          @row-click="openLlmConfig"
        >
          <el-table-column prop="configName" label="配置名称" />
          <el-table-column prop="modelRole" label="角色" />
          <el-table-column prop="baseUrl" label="接口地址" min-width="220" />
          <el-table-column prop="apiKeyMasked" label="API Key" />
          <el-table-column prop="modelName" label="模型" />
          <el-table-column label="操作" width="100"
            ><template #default="scope"
              ><el-button
                text
                type="danger"
                @click.stop="deleteLlmConfig(scope.row.id)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <div class="action-row">
          <el-button
            type="primary"
            :loading="savingSystemConfig"
            @click="saveSystemConfig"
            >保存系统配置</el-button
          >
        </div>
      </section>

      <section
        v-if="activeTab === 'process' && !isProcessDetail"
        class="surface"
      >
        <h3>候选人面试流程</h3>
        <el-form :model="processSearch" label-position="top" class="form-grid">
          <el-form-item label="搜索候选人"
            ><el-input
              v-model="processSearch.keyword"
              placeholder="候选人ID / 姓名 / 手机 / 邮箱 / 专业 / 学校"
          /></el-form-item>
        </el-form>
        <el-form :model="processForm" label-position="top" class="form-grid">
          <el-form-item label="候选人投递记录"
            ><el-select
              v-model="processForm.recruitmentCandidateId"
              filterable
              clearable
              @change="syncIntervieweeByCandidate"
              ><el-option
                v-for="item in filteredProcessCandidates"
                :key="item.id"
                :label="`ID ${item.id} / ${item.fullName} / ${item.jobTitle || '未绑定岗位'}`"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="投递岗位"
            ><el-select v-model="processForm.jobId" disabled
              ><el-option
                v-for="job in jobs"
                :key="job.id"
                :label="job.jobTitle"
                :value="job.id" /></el-select
          ></el-form-item>
          <el-form-item label="候选人唯一ID"
            ><el-input
              :model-value="processCandidatePreview?.id || '-'"
              disabled
          /></el-form-item>
          <el-form-item label="AI通过阈值"
            ><el-input-number v-model="processForm.aiThresholdScore" :min="1"
          /></el-form-item>
          <el-form-item label="AI最少问答轮数"
            ><el-input-number
              v-model="processForm.aiMinQuestionRounds"
              :min="1"
          /></el-form-item>
          <el-form-item label="AI最多问答轮数"
            ><el-input-number
              v-model="processForm.aiMaxQuestionRounds"
              :min="1"
          /></el-form-item>
          <el-form-item label="切屏终止阈值"
            ><el-input-number
              v-model="processForm.antiCheatSwitchLimit"
              :min="1"
          /></el-form-item>
        </el-form>
        <div v-if="processCandidatePreview" class="candidate-preview">
          <h4>候选人投递预览</h4>
          <div class="preview-grid">
            <div>
              <span>候选人ID</span
              ><strong>{{ processCandidatePreview.id }}</strong>
            </div>
            <div>
              <span>候选人姓名</span
              ><strong>{{ processCandidatePreview.fullName }}</strong>
            </div>
            <div>
              <span>投递岗位</span
              ><strong>{{ processCandidatePreview.jobTitle || "-" }}</strong>
            </div>
            <div>
              <span>联系电话</span
              ><strong>{{ processCandidatePreview.mobilePhone || "-" }}</strong>
            </div>
            <div>
              <span>邮箱</span
              ><strong>{{ processCandidatePreview.email || "-" }}</strong>
            </div>
            <div>
              <span>专业</span
              ><strong>{{ processCandidatePreview.major || "-" }}</strong>
            </div>
            <div>
              <span>毕业院校</span
              ><strong>{{
                processCandidatePreview.graduationSchool || "-"
              }}</strong>
            </div>
            <div>
              <span>状态</span
              ><strong>{{
                processCandidatePreview.interviewStageStatus ||
                processCandidatePreview.applicationStatus ||
                "-"
              }}</strong>
            </div>
          </div>
        </div>
        <div class="link-row">
          <el-button type="primary" @click="startProcess"
            >发起面试流程</el-button
          >
        </div>
        <el-table
          :data="processes"
          stripe
          class="data-table"
          @row-click="openProcess"
        >
          <el-table-column prop="id" label="流程流水号" width="110" />
          <el-table-column prop="recruitmentCandidateId" label="候选人ID" />
          <el-table-column prop="candidateName" label="候选人姓名" />
          <el-table-column prop="questionTitle" label="投递岗位" />
          <el-table-column prop="currentStage" label="当前轮次" />
          <el-table-column prop="processStatusView" label="状态展示" />
          <el-table-column prop="aiAverageScore" label="AI均分" />
          <el-table-column prop="overallStatus" label="总状态" />
          <el-table-column label="操作" width="120"
            ><template #default="scope"
              ><el-button text @click.stop="openProcess(scope.row)"
                >进入面试界面</el-button
              ></template
            ></el-table-column
          >
        </el-table>
      </section>

      <section
        v-if="activeTab === 'process' && isProcessDetail"
        class="surface"
      >
        <div class="detail-headline">
          <div>
            <p class="page-eyebrow">Interview Process</p>
            <h3>
              {{ selectedProcess?.candidateName || "候选人" }} 的面试操作台
            </h3>
          </div>
          <RouterLink class="link-chip" to="/interview/hr/processes"
            >返回流程列表</RouterLink
          >
        </div>
        <div v-if="selectedProcess" class="process-workbench">
          <section class="workbench-panel candidate-panel">
            <h3>面试者个人信息</h3>
            <div class="candidate-info-grid">
              <div>
                <span>姓名</span
                ><strong>{{
                  selectedCandidate?.fullName ||
                  selectedProcess.candidateName ||
                  "-"
                }}</strong>
              </div>
              <div>
                <span>手机号</span
                ><strong>{{ selectedCandidate?.mobilePhone || "-" }}</strong>
              </div>
              <div>
                <span>邮箱</span
                ><strong>{{ selectedCandidate?.email || "-" }}</strong>
              </div>
              <div>
                <span>岗位</span
                ><strong>{{
                  selectedCandidate?.jobTitle ||
                  selectedProcess.questionTitle ||
                  "-"
                }}</strong>
              </div>
              <div>
                <span>专业</span
                ><strong>{{ selectedCandidate?.major || "-" }}</strong>
              </div>
              <div>
                <span>学历</span
                ><strong>{{ selectedCandidate?.educationLevel || "-" }}</strong>
              </div>
              <div>
                <span>毕业院校</span
                ><strong>{{
                  selectedCandidate?.graduationSchool || "-"
                }}</strong>
              </div>
              <div>
                <span>工作年限</span
                ><strong>{{
                  selectedCandidate?.yearsOfExperience ?? "-"
                }}</strong>
              </div>
            </div>
            <div class="resume-ai-box">
              <span>简历AI评价</span>
              <strong>{{
                selectedCandidate?.resumeLlmScore ??
                resumeLlmStatusLabel(selectedCandidate?.resumeLlmStatus)
              }}</strong>
              <p>
                {{ selectedCandidate?.resumeLlmComment || "暂无简历AI评价" }}
              </p>
            </div>
          </section>
          <section class="workbench-panel video-panel">
            <div class="panel-title-row">
              <h3>视频面试</h3>
              <span>{{ selectedProcess.sessionStatus || "未开始" }}</span>
            </div>
            <div
              v-if="
                selectedProcess.videoJoinLink || selectedProcess.videoSerialNo
              "
              class="serial-line"
            >
              <span v-if="selectedProcess.videoSerialNo"
                >视频流水号：{{ selectedProcess.videoSerialNo }}</span
              >
              <el-button
                v-if="selectedProcess.videoJoinLink"
                text
                class="video-link"
                @click="copyVideoJoinLink"
                >复制候选人视频链接</el-button
              >
              <a
                v-if="
                  selectedProcess.recordingPath ||
                  selectedProcess.recordingFileName
                "
                :href="interviewApi.getRecordingUrl(selectedProcess.id)"
                target="_blank"
                class="video-link"
                >查看合并录制文件</a
              >
            </div>
            <div class="video-grid">
              <div class="video-box">
                <span>HR本地视频</span
                ><video ref="hrLocalVideo" autoplay muted playsinline></video>
              </div>
              <div class="video-box">
                <span>面试者远端视频</span
                ><video ref="hrRemoteVideo" autoplay playsinline></video>
              </div>
            </div>
            <div class="summary-box">
              <span>语音转写状态</span>
              <strong>{{ selectedProcess.summaryStatus || "待生成" }}</strong>
              <p v-if="selectedProcess.summaryText">
                <b>会议概要：</b>{{ selectedProcess.summaryText }}
              </p>
              <p v-if="selectedProcess.transcriptText">
                <b>转写文本：</b>{{ selectedProcess.transcriptText }}
              </p>
              <p
                v-if="
                  !selectedProcess.summaryText &&
                  !selectedProcess.transcriptText
                "
              >
                录制拼接完成后自动抽取音频、转写并生成概要。
              </p>
            </div>
          </section>
          <section class="workbench-panel ai-question-panel">
            <h3>AI问答题号</h3>
            <div v-if="selectedProcess.aiRecordingFileName" class="serial-line">
              <a
                :href="interviewApi.getAiRecordingUrl(selectedProcess.id)"
                target="_blank"
                class="video-link"
                >查看AI面试录像</a
              >
            </div>
            <div class="question-number-grid">
              <button
                v-for="item in aiRecords"
                :key="item.id"
                class="question-number"
                :class="{ answered: item.answerContent }"
              >
                Q{{ item.sequenceNo }}
              </button>
            </div>
            <el-table
              :data="aiRecords"
              stripe
              class="data-table compact-ai-table"
            >
              <el-table-column prop="sequenceNo" label="题号" width="70" />
              <el-table-column
                prop="knowledgePoint"
                label="知识点"
                min-width="120"
              />
              <el-table-column
                prop="questionContent"
                label="提问"
                min-width="220"
              />
              <el-table-column
                prop="answerContent"
                label="回答"
                min-width="220"
              />
              <el-table-column prop="averageScore" label="均分" width="80" />
            </el-table>
          </section>
          <section class="workbench-panel action-panel">
            <h3>操作区</h3>
            <div class="process-stats">
              <p>流程流水号：{{ selectedProcess.id }}</p>
              <p>当前状态：{{ selectedProcess.processStatusView || "-" }}</p>
              <p>AI均分：{{ selectedProcess.aiAverageScore ?? "-" }}</p>
              <p>
                AI轮数：{{ selectedProcess.aiMinQuestionRounds || "-" }} -
                {{ selectedProcess.aiMaxQuestionRounds || "-" }}
              </p>
              <p>
                切屏次数：{{ selectedProcess.antiCheatSwitchCount || 0 }} /
                {{ selectedProcess.antiCheatSwitchLimit || 5 }}
              </p>
            </div>
            <div class="action-button-grid">
              <el-button v-if="canApproveAi" @click="approveAi(1)"
                >AI/反作弊人工通过并生成视频任务</el-button
              >
              <el-button v-if="canApproveAi" @click="approveAi(0)"
                >AI/反作弊人工不通过</el-button
              >
              <el-button v-if="canStartVideo" @click="startHrVideoCall"
                >开始视频面</el-button
              >
              <el-button v-if="canStopVideo" @click="stopHrRecording"
                >结束并上传录制</el-button
              >
              <el-button v-if="canApproveVideo" @click="approveVideo(1)"
                >视频面通过进线下面</el-button
              >
              <el-button v-if="canApproveVideo" @click="approveVideo(0)"
                >视频面不通过</el-button
              >
              <el-button v-if="canApproveOnsite" @click="approveOnsite(1)"
                >线下面通过</el-button
              >
              <el-button v-if="canApproveOnsite" @click="approveOnsite(0)"
                >线下面不通过</el-button
              >
              <el-button
                v-if="canTerminate"
                type="danger"
                @click="terminateProcess"
                >终止流程</el-button
              >
            </div>
            <div class="remark-box">
              <span>面试备注</span>
              <el-input
                v-model="processRemark"
                type="textarea"
                :rows="5"
                maxlength="2000"
                show-word-limit
                placeholder="记录面试补充说明、风险点、沟通结论"
              />
              <el-button
                type="primary"
                :loading="savingRemark"
                @click="saveProcessRemark"
                >保存备注</el-button
              >
            </div>
          </section>
        </div>
        <div v-else class="empty-box">正在加载候选人面试流程...</div>
      </section>
    </section>
  </div>
</template>

<script setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  authApi,
  interviewApi,
  recruitmentApi,
  systemApi,
} from "../services/api";
import {
  attachRemoteTrack,
  buildMediaErrorMessage,
  createPeerConnection,
  defaultIceServers,
  isRelayIceCandidate,
  playVideo,
  requestCameraAndMicrophone,
} from "../utils/media";

const sessionUser = ref(
  JSON.parse(localStorage.getItem("session-user") || "null"),
);
const route = useRoute();
const router = useRouter();
const isItAdmin = computed(() => sessionUser.value?.roleCode === "IT_ADMIN");
const activeTab = computed(() => route.meta.interviewTab || "process");
const isProcessDetail = computed(
  () => route.name === "interview-process-detail",
);
const knowledgeBases = ref([]);
const knowledgeItems = ref([]);
const weights = ref([]);
const llmConfigs = ref([]);
const jobs = ref([]);
const recruitmentCandidates = ref([]);
const processes = ref([]);
const aiRecords = ref([]);
const selectedProcess = ref(null);
const selectedCandidate = ref(null);
const videoActive = ref(false);
const processRemark = ref("");
const savingRemark = ref(false);

const hrLocalVideo = ref(null);
const hrRemoteVideo = ref(null);
let hrLocalStream = null;
let hrPeer = null;
let hrPollTimer = null;
let hrRecorder = null;
let hrRecordedChunks = [];
let addedIntervieweeIce = new Set();
let hrRemoteStream = null;
let pendingIntervieweeIce = [];
let hrRecordingStopInProgress = false;
let handledHrRecordingEndSignal = "";
let hrRecordingEndTimer = null;

const kbForm = reactive({
  id: null,
  knowledgeBaseName: "",
  techCategory: "",
  jobCategory: "",
  status: 1,
});
const itemForm = reactive({
  id: null,
  knowledgeBaseId: null,
  knowledgePoint: "",
  knowledgeContent: "",
  status: 1,
});
const weightForm = reactive({
  id: null,
  jobId: null,
  knowledgeBaseId: null,
  weight: 1,
});
const interviewerLlmForm = reactive(createLlmForm("INTERVIEWER"));
const scorerLlmForm = reactive(createLlmForm("SCORER"));
const resumeReviewLlmForm = reactive(createLlmForm("RESUME_REVIEW"));
const videoSummaryLlmForm = reactive(createLlmForm("VIDEO_SUMMARY"));
const processForm = reactive({
  recruitmentCandidateId: null,
  intervieweeUserId: "",
  jobId: null,
  aiThresholdScore: 70,
  aiMinQuestionRounds: 1,
  aiMaxQuestionRounds: 10,
  antiCheatSwitchLimit: 5,
});
const processSearch = reactive({ keyword: "" });
const systemConfig = reactive({
  ALIYUN_ACCESS_KEY_ID: "",
  ALIYUN_ACCESS_KEY_SECRET: "",
  ALIYUN_SMS_SIGN_NAME: "",
  ALIYUN_SMS_TEMPLATE_CODE: "",
  ALIYUN_STT_APP_KEY: "",
  ALIYUN_STT_ENDPOINT: "",
  ALIYUN_OSS_BUCKET_NAME: "",
  ALIYUN_OSS_ENDPOINT: "",
  DB_TYPE: "",
  DB_URL: "",
  DB_USERNAME: "",
  DB_PASSWORD: "",
  JWT_SECRET: "",
  INTERVIEW_VIDEO_FFMPEG_PATH: "",
  INTERVIEW_VIDEO_VIDEO_CODEC: "",
  INTERVIEW_VIDEO_AUDIO_CODEC: "",
  INTERVIEW_STUN_URLS: "",
  INTERVIEW_TURN_URLS: "",
  INTERVIEW_TURN_USERNAME: "",
  INTERVIEW_TURN_CREDENTIAL: "",
  TURN_HOST: "",
  TURN_EXTERNAL_IP: "",
  TURN_PRIVATE_IP: "",
  TURN_REALM: "",
  TURN_MIN_PORT: "",
  TURN_MAX_PORT: "",
  RESUME_OCR_ENABLED: "",
  RESUME_OCR_TESSERACT_PATH: "",
  RESUME_OCR_LANGUAGE: "",
  RESUME_OCR_DPI: "",
  RESUME_OCR_MAX_PAGES: "",
  SMTP_HOST: "",
  SMTP_PORT: "",
  SMTP_USERNAME: "",
  SMTP_PASSWORD: "",
  SMTP_FROM: "",
  SMTP_SSL: "",
});
const savingSystemConfig = ref(false);

const interviewerKeyLabel = computed(
  () =>
    llmConfigs.value.find((item) => item.modelRole === "INTERVIEWER")
      ?.apiKeyMasked || "未配置",
);
const scorerKeyLabel = computed(
  () =>
    llmConfigs.value.find((item) => item.modelRole === "SCORER")
      ?.apiKeyMasked || "未配置",
);
const resumeReviewKeyLabel = computed(
  () =>
    llmConfigs.value.find((item) => item.modelRole === "RESUME_REVIEW")
      ?.apiKeyMasked || "未配置，默认回退评分模型",
);
const videoSummaryKeyLabel = computed(
  () =>
    llmConfigs.value.find((item) => item.modelRole === "VIDEO_SUMMARY")
      ?.apiKeyMasked || "未配置",
);

const canTerminate = computed(
  () => selectedProcess.value?.overallStatus === "IN_PROGRESS",
);
const canApproveAi = computed(
  () => canTerminate.value && selectedProcess.value?.currentStage === "AI",
);
const canStartVideo = computed(
  () =>
    canTerminate.value &&
    selectedProcess.value?.currentStage === "VIDEO" &&
    selectedProcess.value?.videoJoinLink &&
    !videoActive.value,
);
const canStopVideo = computed(() => videoActive.value);
const canApproveVideo = computed(
  () =>
    canTerminate.value &&
    selectedProcess.value?.currentStage === "VIDEO" &&
    ["WAITING_APPROVAL", "RECORDED"].includes(
      selectedProcess.value?.sessionStatus,
    ),
);
const canApproveOnsite = computed(
  () => canTerminate.value && selectedProcess.value?.currentStage === "ONSITE",
);
const filteredProcessCandidates = computed(() => {
  const keyword = processSearch.keyword.trim().toLowerCase();
  const candidates = recruitmentCandidates.value.filter(
    (item) => !item.interviewProcessId && item.applicationStatus !== "REJECTED",
  );
  if (!keyword) return candidates;
  return candidates.filter((item) =>
    [
      item.id,
      item.fullName,
      item.mobilePhone,
      item.email,
      item.major,
      item.graduationSchool,
    ].some((value) =>
      String(value || "")
        .toLowerCase()
        .includes(keyword),
    ),
  );
});
const processCandidatePreview = computed(
  () =>
    recruitmentCandidates.value.find(
      (item) => item.id === processForm.recruitmentCandidateId,
    ) || null,
);

function fail(error) {
  ElMessage.error(error.message || "操作失败");
}
function resumeLlmStatusLabel(status) {
  return (
    { PENDING: "评分中", COMPLETED: "已完成", FAILED: "评分失败" }[status] ||
    "-"
  );
}
async function loadSystemConfig() {
  try {
    const res = (await systemApi.getConfig()).data;
    Object.assign(systemConfig, res);
  } catch (e) {
    fail(e);
  }
}
async function saveSystemConfig() {
  savingSystemConfig.value = true;
  try {
    const updates = {};
    for (const [k, v] of Object.entries(systemConfig)) {
      if (v !== "" && v !== "****") updates[k] = v;
    }
    await systemApi.saveConfig(updates);
    await loadSystemConfig();
    ElMessage.success("系统配置已保存");
  } catch (e) {
    fail(e);
  } finally {
    savingSystemConfig.value = false;
  }
}
async function loadAll() {
  try {
    sessionUser.value = (await authApi.getSession()).data;
    knowledgeBases.value = (await interviewApi.listKnowledgeBases()).data;
    if (isItAdmin.value) {
      llmConfigs.value = (await interviewApi.listLlmConfigs()).data;
      syncLlmForms();
      await loadSystemConfig();
    } else {
      llmConfigs.value = [];
      if (activeTab.value === "llm" || activeTab.value === "system")
        router.replace("/interview/hr/knowledge-bases");
    }
    jobs.value = (await recruitmentApi.listAdminJobs()).data;
    recruitmentCandidates.value = (await recruitmentApi.listCandidates()).data;
    processes.value = (await interviewApi.listProcesses()).data;
    if (selectedProcess.value) {
      selectedProcess.value =
        processes.value.find((item) => item.id === selectedProcess.value.id) ||
        selectedProcess.value;
      if (isProcessDetail.value) {
        aiRecords.value = (
          await interviewApi.listAiRecords({
            processId: selectedProcess.value.id,
          })
        ).data;
        selectedCandidate.value = selectedProcess.value.recruitmentCandidateId
          ? (
              await recruitmentApi.getCandidate(
                selectedProcess.value.recruitmentCandidateId,
              )
            ).data
          : null;
        processRemark.value = selectedProcess.value.remark || "";
      }
    }
    await syncRouteState();
  } catch (error) {
    fail(error);
  }
}
async function selectKnowledgeBase(row) {
  itemForm.knowledgeBaseId = row.id;
  knowledgeItems.value = (
    await interviewApi.listKnowledgeItems({ knowledgeBaseId: row.id })
  ).data;
}
async function openKnowledgeBase(row) {
  await router.push(`/interview/hr/knowledge-bases/${row.id}`);
}
function openWeight(row) {
  Object.assign(weightForm, row);
  router.push(`/interview/hr/weights/${row.id}`);
}
function openLlmConfig(row) {
  editLlmConfig(row);
  router.push("/interview/hr/system");
}
function openProcess(row) {
  router.push(`/interview/hr/processes/${row.id}`);
}
async function saveKnowledgeBase() {
  try {
    await interviewApi.saveKnowledgeBase({ ...kbForm });
    ElMessage.success("知识库已保存");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function deleteKnowledgeBase(id) {
  try {
    await interviewApi.deleteKnowledgeBase(id);
    ElMessage.success("知识库已删除");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function saveKnowledgeItem() {
  try {
    await interviewApi.saveKnowledgeItem({ ...itemForm });
    ElMessage.success("知识点已保存");
    await selectKnowledgeBase({ id: itemForm.knowledgeBaseId });
  } catch (error) {
    fail(error);
  }
}
function editKnowledgeItem(row) {
  Object.assign(itemForm, {
    id: row.id,
    knowledgeBaseId: row.knowledgeBaseId,
    knowledgePoint: row.knowledgePoint,
    knowledgeContent: row.knowledgeContent,
    status: row.status ?? 1,
  });
}
function resetKnowledgeItemForm() {
  Object.assign(itemForm, {
    id: null,
    knowledgeBaseId: itemForm.knowledgeBaseId,
    knowledgePoint: "",
    knowledgeContent: "",
    status: 1,
  });
}
async function importKnowledgeItemsCsv(uploadFile) {
  try {
    if (!itemForm.knowledgeBaseId) {
      ElMessage.warning("请先选择目标知识库");
      return;
    }
    const response = await interviewApi.importKnowledgeItems(
      itemForm.knowledgeBaseId,
      uploadFile.raw,
    );
    ElMessage.success(`已导入 ${response.data.imported} 条知识点`);
    await selectKnowledgeBase({ id: itemForm.knowledgeBaseId });
  } catch (error) {
    fail(error);
  }
}
async function deleteKnowledgeItem(id) {
  try {
    await interviewApi.deleteKnowledgeItem(id);
    ElMessage.success("知识点已删除");
    await selectKnowledgeBase({ id: itemForm.knowledgeBaseId });
  } catch (error) {
    fail(error);
  }
}
async function saveWeight() {
  try {
    await interviewApi.saveJobKnowledgeWeight({ ...weightForm });
    ElMessage.success("权重已保存");
    weights.value = (
      await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })
    ).data;
  } catch (error) {
    fail(error);
  }
}
async function deleteWeight(id) {
  try {
    await interviewApi.deleteJobKnowledgeWeight(id);
    ElMessage.success("权重已删除");
    weights.value = (
      await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })
    ).data;
  } catch (error) {
    fail(error);
  }
}
async function saveRoleLlmConfig(form, role) {
  try {
    await interviewApi.saveLlmConfig({ ...form, modelRole: role });
    ElMessage.success("LLM配置已保存");
    form.apiKey = "";
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function deleteLlmConfig(id) {
  try {
    await interviewApi.deleteLlmConfig(id);
    ElMessage.success("LLM配置已删除");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
function editLlmConfig(row) {
  Object.assign(llmFormByRole(row.modelRole), { ...row, apiKey: "" });
}
function createLlmForm(role) {
  return {
    id: null,
    configName:
      role === "SCORER"
        ? "评分模型"
        : role === "RESUME_REVIEW"
          ? "简历初筛模型"
          : role === "VIDEO_SUMMARY"
            ? "视频概要模型"
            : "面试官模型",
    modelRole: role,
    baseUrl: "",
    apiKey: "",
    modelName: "",
    promptTemplate: "",
    scoringRulePrompt: "",
    status: 1,
  };
}
function llmFormByRole(role) {
  return role === "SCORER"
    ? scorerLlmForm
    : role === "RESUME_REVIEW"
      ? resumeReviewLlmForm
      : role === "VIDEO_SUMMARY"
        ? videoSummaryLlmForm
        : interviewerLlmForm;
}
function syncLlmForms() {
  const interviewer = llmConfigs.value.find(
    (item) => item.modelRole === "INTERVIEWER",
  );
  const scorer = llmConfigs.value.find((item) => item.modelRole === "SCORER");
  const resumeReview = llmConfigs.value.find(
    (item) => item.modelRole === "RESUME_REVIEW",
  );
  const videoSummary = llmConfigs.value.find(
    (item) => item.modelRole === "VIDEO_SUMMARY",
  );
  Object.assign(
    interviewerLlmForm,
    interviewer ? { ...interviewer, apiKey: "" } : createLlmForm("INTERVIEWER"),
  );
  Object.assign(
    scorerLlmForm,
    scorer ? { ...scorer, apiKey: "" } : createLlmForm("SCORER"),
  );
  Object.assign(
    resumeReviewLlmForm,
    resumeReview
      ? { ...resumeReview, apiKey: "" }
      : createLlmForm("RESUME_REVIEW"),
  );
  Object.assign(
    videoSummaryLlmForm,
    videoSummary
      ? { ...videoSummary, apiKey: "" }
      : createLlmForm("VIDEO_SUMMARY"),
  );
}
async function syncIntervieweeByCandidate(candidateId) {
  const candidate = recruitmentCandidates.value.find(
    (item) => item.id === candidateId,
  );
  processForm.intervieweeUserId = candidate?.intervieweeUserId
    ? String(candidate.intervieweeUserId)
    : "";
  processForm.jobId = candidate?.jobId || null;
}
async function startProcess() {
  try {
    if (!processForm.recruitmentCandidateId) {
      ElMessage.warning("请先选择候选人投递记录");
      return;
    }
    if (!processForm.intervieweeUserId) {
      ElMessage.warning("未匹配到候选人账号");
      return;
    }
    if (!processForm.jobId) {
      ElMessage.warning("投递记录未绑定岗位");
      return;
    }
    if (processForm.aiMaxQuestionRounds < processForm.aiMinQuestionRounds) {
      ElMessage.warning("AI最多问答轮数不能小于最少问答轮数");
      return;
    }
    const response = await interviewApi.startProcess({
      ...processForm,
      intervieweeUserId: Number(processForm.intervieweeUserId),
    });
    selectedProcess.value = response.data;
    ElMessage.success("面试流程已发起");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function loadProcessDetail(row) {
  selectedProcess.value = row;
  processRemark.value = row?.remark || "";
  aiRecords.value = (
    await interviewApi.listAiRecords({ processId: row.id })
  ).data;
  selectedCandidate.value = row.recruitmentCandidateId
    ? (await recruitmentApi.getCandidate(row.recruitmentCandidateId)).data
    : null;
}
async function approveAi(approved) {
  try {
    await interviewApi.approveAi(selectedProcess.value.id, { approved });
    ElMessage.success("AI审批完成");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function approveVideo(approved) {
  try {
    await interviewApi.approveVideo(selectedProcess.value.id, { approved });
    ElMessage.success("视频面审批完成");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function approveOnsite(approved) {
  try {
    await interviewApi.approveOnsite(selectedProcess.value.id, { approved });
    ElMessage.success("线下面审批完成");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function terminateProcess() {
  try {
    await interviewApi.terminateProcess(selectedProcess.value.id, {
      approved: 0,
    });
    ElMessage.success("流程已终止");
    await loadAll();
  } catch (error) {
    fail(error);
  }
}
async function saveProcessRemark() {
  if (!selectedProcess.value) return;
  savingRemark.value = true;
  try {
    selectedProcess.value = (
      await interviewApi.updateProcessRemark(selectedProcess.value.id, {
        comment: processRemark.value,
      })
    ).data;
    processRemark.value = selectedProcess.value.remark || "";
    ElMessage.success("备注已保存");
    await loadAll();
  } catch (error) {
    fail(error);
  } finally {
    savingRemark.value = false;
  }
}

async function copyVideoJoinLink() {
  if (!selectedProcess.value?.videoJoinLink) return;
  const url = new URL(
    selectedProcess.value.videoJoinLink,
    window.location.origin,
  ).toString();
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success("候选人视频链接已复制");
  } catch {
    window.prompt("复制候选人视频链接", url);
  }
}

async function startHrVideoCall() {
  if (!selectedProcess.value) return;
  try {
    disconnectHrVideo();
    const sessionResponse = await interviewApi.createVideoSession(
      selectedProcess.value.id,
      {
        approverUserId: sessionUser.value?.id,
        approverName:
          sessionUser.value?.displayName || sessionUser.value?.username,
      },
    );
    selectedProcess.value.videoJoinLink =
      sessionResponse.data?.videoJoinLink ||
      selectedProcess.value.videoJoinLink;
    hrLocalStream = await requestCameraAndMicrophone();
    hrLocalVideo.value.srcObject = hrLocalStream;
    playVideo(hrLocalVideo.value);
    hrPeer = createPeerConnection(await loadIceServers());
    addedIntervieweeIce = new Set();
    pendingIntervieweeIce = [];
    hrLocalStream
      .getTracks()
      .forEach((track) => hrPeer.addTrack(track, hrLocalStream));
    hrRemoteStream = null;
    hrPeer.ontrack = (event) => {
      hrRemoteStream = attachRemoteTrack(
        hrRemoteVideo.value,
        event,
        hrRemoteStream,
      );
    };
    hrPeer.onconnectionstatechange = () => {
      if (["failed", "disconnected"].includes(hrPeer.connectionState)) {
        ElMessage.warning(
          "远端视频连接不稳定，请双方保持页面打开，必要时重新开始视频面",
        );
      }
    };
    hrPeer.onicecandidate = async (event) => {
      if (isRelayIceCandidate(event.candidate)) {
        await interviewApi.addHrIce(selectedProcess.value.id, {
          iceCandidate: JSON.stringify(event.candidate),
        });
      }
    };
    const offer = await hrPeer.createOffer();
    await hrPeer.setLocalDescription(offer);
    await interviewApi.publishVideoOffer(selectedProcess.value.id, {
      offerSdp: JSON.stringify(offer),
    });
    await interviewApi.hrJoin(selectedProcess.value.id);
    videoActive.value = true;
    hrPollTimer = setInterval(async () => {
      const state = (
        await interviewApi.getHrVideoState(selectedProcess.value.id)
      ).data;
      if (state.answerSdp && !hrPeer.currentRemoteDescription) {
        await hrPeer.setRemoteDescription(JSON.parse(state.answerSdp));
        await flushPendingIntervieweeIce();
      }
      if (state.intervieweeIceCandidates) {
        const candidates = state.intervieweeIceCandidates
          .split("\n")
          .filter(Boolean);
        for (const item of candidates) {
          if (!addedIntervieweeIce.has(item)) {
            addedIntervieweeIce.add(item);
            await addIntervieweeIceCandidate(item);
          }
        }
      }
      if (state.sessionStatus === "RECORDING") {
        startHrRecordingIfNeeded();
      }
      if (shouldHandleHrRecordingEnd(state)) {
        handledHrRecordingEndSignal = hrRecordingEndSignalKey(state);
        clearInterval(hrPollTimer);
        hrPollTimer = null;
        scheduleHrRecordingStop(state.recordingEndRequestedAt);
      }
    }, 1000);
    ElMessage.success("HR视频已就绪，等待面试者加入后同步开始录制");
  } catch (error) {
    ElMessage.error(buildMediaErrorMessage(error));
  }
}

async function stopHrRecording() {
  try {
    clearInterval(hrPollTimer);
    hrPollTimer = null;
    const response = await interviewApi.completeVideo(selectedProcess.value.id);
    handledHrRecordingEndSignal = hrRecordingEndSignalKey(response.data || {});
    scheduleHrRecordingStop(response.data?.recordingEndRequestedAt);
  } catch (error) {
    fail(error);
  }
}

function hrRecordingEndSignalKey(state) {
  return (
    state.recordingEndRequestedAt ||
    (state.sessionStatus === "END_REQUESTED" ? "END_REQUESTED" : "")
  );
}

function shouldHandleHrRecordingEnd(state) {
  const signal = hrRecordingEndSignalKey(state);
  return signal && signal !== handledHrRecordingEndSignal;
}

function scheduleHrRecordingStop(endAt) {
  clearTimeout(hrRecordingEndTimer);
  const delay = Math.max(
    new Date(endAt || Date.now()).getTime() - Date.now(),
    0,
  );
  hrRecordingEndTimer = setTimeout(async () => {
    try {
      await stopAndUploadHrRecording();
      disconnectHrVideo();
      await loadAll();
    } catch (error) {
      fail(error);
    }
  }, delay);
}

function startHrRecordingIfNeeded() {
  if (!hrLocalStream || (hrRecorder && hrRecorder.state !== "inactive")) return;
  hrRecorder = new MediaRecorder(hrLocalStream);
  hrRecordedChunks = [];
  hrRecorder.ondataavailable = (event) => {
    if (event.data.size > 0) hrRecordedChunks.push(event.data);
  };
  hrRecorder.start(1000);
  ElMessage.success("双方已进入视频面，录制已同步开始");
}

async function stopAndUploadHrRecording() {
  if (hrRecordingStopInProgress) return;
  if (
    (!hrRecorder || hrRecorder.state === "inactive") &&
    hrRecordedChunks.length === 0
  )
    return;
  hrRecordingStopInProgress = true;
  try {
    if (hrRecorder && hrRecorder.state !== "inactive") {
      const currentRecorder = hrRecorder;
      await new Promise((resolve) => {
        currentRecorder.onstop = resolve;
        currentRecorder.stop();
      });
      hrRecorder = null;
    }
    const blob = new Blob(hrRecordedChunks, { type: "video/webm" });
    if (blob.size > 0) {
      const file = new File([blob], `hr-${selectedProcess.value.id}.webm`, {
        type: "video/webm",
      });
      await interviewApi.uploadHrVideoRecording(selectedProcess.value.id, file);
      hrRecordedChunks = [];
      ElMessage.success("HR录制已上传");
    }
  } finally {
    hrRecordingStopInProgress = false;
  }
}

function disconnectHrVideo() {
  clearInterval(hrPollTimer);
  clearTimeout(hrRecordingEndTimer);
  hrPollTimer = null;
  hrRecordingEndTimer = null;
  hrPeer?.getSenders?.().forEach((sender) => sender.track?.stop());
  hrPeer?.close();
  hrPeer = null;
  hrRecorder = null;
  hrRecordedChunks = [];
  hrRecordingStopInProgress = false;
  handledHrRecordingEndSignal = "";
  hrLocalStream?.getTracks().forEach((track) => track.stop());
  hrLocalStream = null;
  hrRemoteStream = null;
  pendingIntervieweeIce = [];
  if (hrLocalVideo.value) hrLocalVideo.value.srcObject = null;
  if (hrRemoteVideo.value) hrRemoteVideo.value.srcObject = null;
  videoActive.value = false;
}

async function addIntervieweeIceCandidate(item) {
  if (!hrPeer?.remoteDescription) {
    pendingIntervieweeIce.push(item);
    return;
  }
  try {
    await hrPeer.addIceCandidate(JSON.parse(item));
  } catch (error) {
    console.warn("添加面试者 ICE失败", error);
  }
}

async function flushPendingIntervieweeIce() {
  const items = pendingIntervieweeIce;
  pendingIntervieweeIce = [];
  for (const item of items) {
    await addIntervieweeIceCandidate(item);
  }
}

async function loadIceServers() {
  try {
    const response = await interviewApi.getIceServers();
    return response.data?.length ? response.data : defaultIceServers();
  } catch {
    return defaultIceServers();
  }
}

onBeforeUnmount(() => {
  disconnectHrVideo();
});

async function syncRouteState() {
  const id = Number(route.params.id);
  if (!id) return;
  if (route.name === "interview-knowledge-base-detail") {
    const row = knowledgeBases.value.find((item) => item.id === id);
    if (row) await selectKnowledgeBase(row);
  } else if (route.name === "interview-weight-detail") {
    const row = weights.value.find((item) => item.id === id);
    if (row) Object.assign(weightForm, row);
  } else if (route.name === "interview-process-detail") {
    const row = processes.value.find((item) => item.id === id);
    if (row) await loadProcessDetail(row);
  }
}

watch(
  () => route.fullPath,
  () => {
    syncRouteState();
  },
);

onMounted(loadAll);
</script>

<style scoped>
.llm-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.inner-surface {
  background: rgba(255, 255, 255, 0.82);
  border-radius: var(--surface-radius);
  padding: 20px;
}

.inner-surface h3 {
  margin-top: 0;
  margin-bottom: 16px;
  font-size: 16px;
}

.detail-surface {
  margin-top: 18px;
}

.detail-headline {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.detail-headline h3 {
  margin: 6px 0 0;
}

.process-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.workbench-panel {
  min-width: 0;
  border-radius: var(--surface-radius);
  padding: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(17, 49, 69, 0.06);
}

.workbench-panel h3 {
  margin: 0 0 14px;
  font-size: 16px;
}

.candidate-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.candidate-info-grid div,
.resume-ai-box,
.process-stats {
  border-radius: 14px;
  padding: 12px;
  background: #f8f5ef;
}

.candidate-info-grid span,
.resume-ai-box span,
.remark-box span {
  display: block;
  color: var(--text-secondary);
  margin-bottom: 5px;
  font-size: 13px;
}

.candidate-info-grid strong,
.resume-ai-box strong {
  color: var(--brand-ink);
}

.resume-ai-box {
  margin-top: 14px;
}

.resume-ai-box p {
  margin: 8px 0 0;
  line-height: 1.7;
  font-size: 14px;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.panel-title-row span {
  color: #0f6c8f;
  font-weight: 700;
  font-size: 14px;
}

.question-number-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.question-number {
  border: 1px solid rgba(16, 37, 50, 0.14);
  background: #fff;
  color: #102532;
  border-radius: 999px;
  padding: 7px 14px;
  font-weight: 700;
  font-size: 13px;
  transition: all 0.2s ease;
}

.question-number:hover {
  background: #f8f5ef;
  border-color: rgba(16, 37, 50, 0.28);
}

.question-number.answered {
  background: #102532;
  color: #f8f5ef;
  border-color: #102532;
}

.compact-ai-table {
  margin-top: 10px;
}

.process-stats {
  display: grid;
  gap: 6px;
  margin-bottom: 14px;
}

.process-stats p {
  margin: 0;
  color: #42515b;
  font-size: 14px;
}

.action-button-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.remark-box {
  display: grid;
  gap: 10px;
}

.remark-box span {
  font-weight: 600;
}

.csv-import-box {
  grid-column: 1 / -1;
}

.serial-line {
  margin: 8px 0 14px;
  color: #42515b;
  font-size: 14px;
}

.video-link {
  margin-left: 12px;
  color: #0f6c8f;
  font-weight: 700;
  text-decoration: none;
}

.video-link:hover {
  text-decoration: underline;
}

.candidate-preview {
  margin: 12px 0 18px;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(17, 49, 69, 0.06);
}

.candidate-preview h4 {
  margin: 0 0 14px;
  font-size: 15px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.preview-grid span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}

.preview-grid strong {
  color: var(--brand-ink);
  font-size: 14px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0 18px;
}

.video-box {
  background: rgba(255, 255, 255, 0.82);
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(17, 49, 69, 0.06);
}

.video-box span {
  display: block;
  margin-bottom: 10px;
  color: var(--text-secondary);
  font-size: 13px;
}

.video-box video {
  width: 100%;
  min-height: 220px;
  background: #111;
  border-radius: 12px;
}

@media (max-width: 1200px) {
  .llm-config-grid {
    grid-template-columns: 1fr;
  }

  .preview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .process-workbench {
    grid-template-columns: 1fr;
  }

  .video-grid {
    grid-template-columns: 1fr;
  }

  .candidate-info-grid {
    grid-template-columns: 1fr;
  }

  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
