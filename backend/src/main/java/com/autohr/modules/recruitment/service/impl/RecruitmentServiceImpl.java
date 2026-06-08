package com.autohr.modules.recruitment.service.impl;

import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.entity.SysUser;
import com.autohr.modules.auth.mapper.SysUserMapper;
import com.autohr.modules.auth.service.AuditLogService;
import com.autohr.modules.interview.entity.InterviewProcess;
import com.autohr.modules.interview.entity.InterviewAiRecord;
import com.autohr.modules.interview.entity.InterviewVideoSession;
import com.autohr.modules.interview.mapper.InterviewAiRecordMapper;
import com.autohr.modules.interview.mapper.InterviewProcessMapper;
import com.autohr.modules.interview.mapper.InterviewVideoSessionMapper;
import com.autohr.modules.recruitment.dto.CandidateApplyRequest;
import com.autohr.modules.recruitment.dto.CandidateVO;
import com.autohr.modules.recruitment.dto.JobSaveRequest;
import com.autohr.modules.recruitment.dto.JobVO;
import com.autohr.modules.recruitment.dto.ResumeFileVO;
import com.autohr.modules.recruitment.entity.RecruitmentCandidate;
import com.autohr.modules.recruitment.entity.RecruitmentJob;
import com.autohr.modules.recruitment.entity.RecruitmentResumeFile;
import com.autohr.modules.recruitment.mapper.RecruitmentCandidateMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentJobMapper;
import com.autohr.modules.recruitment.mapper.RecruitmentResumeFileMapper;
import com.autohr.modules.recruitment.service.RecruitmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecruitmentServiceImpl implements RecruitmentService {

    private static final Path RESUME_DIR = Paths.get(System.getProperty("user.dir"), "uploads", "resumes");

    private final RecruitmentJobMapper jobMapper;
    private final RecruitmentCandidateMapper candidateMapper;
    private final RecruitmentResumeFileMapper resumeFileMapper;
    private final SysUserMapper sysUserMapper;
    private final AuditLogService auditLogService;
    private final InterviewProcessMapper interviewProcessMapper;
    private final InterviewAiRecordMapper interviewAiRecordMapper;
    private final InterviewVideoSessionMapper interviewVideoSessionMapper;

    @Override
    @Transactional
    public JobVO saveJob(JobSaveRequest request) {
        RecruitmentJob job = request.getId() == null ? new RecruitmentJob() : requireJob(request.getId());
        BeanUtils.copyProperties(request, job);
        if (StrUtil.isBlank(job.getJobCode())) {
            job.setJobCode(buildJobCode(request.getJobTitle()));
        }
        job.setStatus(Objects.requireNonNullElse(request.getStatus(), 1));
        job.setPublishDate(Objects.requireNonNullElse(request.getPublishDate(), LocalDate.now()));
        if (request.getId() == null) {
            job.setId(nextId(jobMapper.selectList(null).stream().map(RecruitmentJob::getId).toList()));
            jobMapper.insert(job);
        } else {
            jobMapper.updateById(job);
        }
        return toJobVO(requireJob(job.getId()));
    }

    @Override
    public List<JobVO> listJobs(Integer status, String departmentName, String jobType, String keyword) {
        List<RecruitmentJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<RecruitmentJob>()
                .eq(status != null, RecruitmentJob::getStatus, status)
                .eq(StrUtil.isNotBlank(departmentName), RecruitmentJob::getDepartmentName, departmentName)
                .eq(StrUtil.isNotBlank(jobType), RecruitmentJob::getJobType, jobType)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(RecruitmentJob::getJobTitle, keyword)
                        .or().like(RecruitmentJob::getDepartmentName, keyword)
                        .or().like(RecruitmentJob::getJobCode, keyword)
                        .or().like(RecruitmentJob::getWorkLocation, keyword)
                        .or().like(RecruitmentJob::getSalaryRange, keyword))
                .orderByDesc(RecruitmentJob::getId));
        return jobs.stream().map(this::toJobVO).toList();
    }

    @Override
    public JobVO getJob(Long id) {
        return toJobVO(requireJob(id));
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        requireJob(id);
        Long candidateCount = candidateMapper.selectCount(new LambdaQueryWrapper<RecruitmentCandidate>().eq(RecruitmentCandidate::getJobId, id));
        if (candidateCount > 0) {
            throw new BusinessException("该岗位已有报名记录，不能删除");
        }
        jobMapper.deleteById(id);
    }

    @Override
    @Transactional
    public CandidateVO apply(CandidateApplyRequest request, String intervieweeUsername) {
        requireJob(request.getJobId());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, intervieweeUsername).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException("面试者用户不存在");
        }
        RecruitmentCandidate candidate = new RecruitmentCandidate();
        BeanUtils.copyProperties(request, candidate);
        candidate.setId(nextId(candidateMapper.selectList(null).stream().map(RecruitmentCandidate::getId).toList()));
        candidate.setApplicationStatus("SUBMITTED");
        candidate.setIntervieweeUserId(user.getId());
        candidateMapper.insert(candidate);
        auditLogService.log(user.getId(), displayName(user), user.getRoleCode(), "RECRUITMENT", "APPLY_CANDIDATE", "RECRUITMENT_CANDIDATE", String.valueOf(candidate.getId()), candidate.getFullName() + " 投递岗位 " + request.getJobId());
        return toCandidateVO(requireCandidate(candidate.getId()), loadJobMap(), loadResumeMap());
    }

    @Override
    public List<CandidateVO> listCandidates(Long jobId, String status, String interviewStageStatus, String keyword) {
        List<RecruitmentCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<RecruitmentCandidate>()
                .eq(jobId != null, RecruitmentCandidate::getJobId, jobId)
                .eq(StrUtil.isNotBlank(status), RecruitmentCandidate::getApplicationStatus, status)
                .eq(StrUtil.isNotBlank(interviewStageStatus), RecruitmentCandidate::getInterviewStageStatus, interviewStageStatus)
                .and(StrUtil.isNotBlank(keyword), q -> q.like(RecruitmentCandidate::getFullName, keyword)
                        .or().like(RecruitmentCandidate::getMobilePhone, keyword)
                        .or().like(RecruitmentCandidate::getMajor, keyword)
                        .or().like(RecruitmentCandidate::getEmail, keyword)
                        .or().like(RecruitmentCandidate::getGraduationSchool, keyword))
                .orderByDesc(RecruitmentCandidate::getId));
        Map<Long, RecruitmentJob> jobMap = loadJobMap();
        Map<Long, RecruitmentResumeFile> resumeMap = loadResumeMap();
        return candidates.stream().map(item -> toCandidateVO(item, jobMap, resumeMap)).toList();
    }

    @Override
    public CandidateVO getCandidate(Long id) {
        return toCandidateVO(requireCandidate(id), loadJobMap(), loadResumeMap());
    }

    @Override
    @Transactional
    public void deleteCandidate(Long id) {
        RecruitmentCandidate candidate = requireCandidate(id);
        List<InterviewProcess> processes = interviewProcessMapper.selectList(new LambdaQueryWrapper<InterviewProcess>()
                .eq(InterviewProcess::getRecruitmentCandidateId, id));
        for (InterviewProcess process : processes) {
            interviewAiRecordMapper.delete(new LambdaQueryWrapper<InterviewAiRecord>().eq(InterviewAiRecord::getProcessId, process.getId()));
            interviewVideoSessionMapper.delete(new LambdaQueryWrapper<InterviewVideoSession>().eq(InterviewVideoSession::getProcessId, process.getId()));
            interviewProcessMapper.deleteById(process.getId());
        }
        resumeFileMapper.delete(new LambdaQueryWrapper<RecruitmentResumeFile>().eq(RecruitmentResumeFile::getCandidateId, id));
        candidateMapper.deleteById(id);
    }

    @Override
    @Transactional
    public ResumeFileVO uploadResume(Long candidateId, MultipartFile file) {
        RecruitmentCandidate candidate = requireCandidate(candidateId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("简历文件不能为空");
        }
        try {
            Files.createDirectories(RESUME_DIR);
            String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "resume.bin");
            String suffix = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
            String storedName = UUID.randomUUID() + suffix;
            Path target = RESUME_DIR.resolve(storedName).normalize().toAbsolutePath();
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            RecruitmentResumeFile resumeFile = new RecruitmentResumeFile();
            resumeFile.setId(nextId(resumeFileMapper.selectList(null).stream().map(RecruitmentResumeFile::getId).toList()));
            resumeFile.setCandidateId(candidateId);
            resumeFile.setOriginalFileName(originalName);
            resumeFile.setStoredFileName(storedName);
            resumeFile.setFilePath(target.toString());
            resumeFile.setContentType(file.getContentType());
            resumeFile.setFileSize(file.getSize());
            resumeFileMapper.insert(resumeFile);
            candidate.setResumeFileId(resumeFile.getId());
            candidateMapper.updateById(candidate);
            SysUser user = candidate.getIntervieweeUserId() == null ? null : sysUserMapper.selectById(candidate.getIntervieweeUserId());
            auditLogService.log(candidate.getIntervieweeUserId(), user == null ? candidate.getFullName() : displayName(user), user == null ? "INTERVIEWEE" : user.getRoleCode(), "RECRUITMENT", "UPLOAD_RESUME", "RECRUITMENT_RESUME", String.valueOf(resumeFile.getId()), originalName);
            return toResumeFileVO(resumeFileMapper.selectById(resumeFile.getId()));
        } catch (IOException ex) {
            throw new BusinessException("简历上传失败: " + ex.getMessage());
        }
    }

    @Override
    public RecruitmentResumeFile getResumeFile(Long id) {
        RecruitmentResumeFile resumeFile = resumeFileMapper.selectById(id);
        if (resumeFile == null) {
            throw new BusinessException("简历文件不存在: " + id);
        }
        return resumeFile;
    }

    private RecruitmentJob requireJob(Long id) {
        RecruitmentJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException("招聘岗位不存在: " + id);
        }
        return job;
    }

    private RecruitmentCandidate requireCandidate(Long id) {
        RecruitmentCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException("报名记录不存在: " + id);
        }
        return candidate;
    }

    private Map<Long, RecruitmentJob> loadJobMap() {
        return jobMapper.selectList(null).stream().collect(Collectors.toMap(RecruitmentJob::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, RecruitmentResumeFile> loadResumeMap() {
        return resumeFileMapper.selectList(null).stream().collect(Collectors.toMap(RecruitmentResumeFile::getId, Function.identity(), (a, b) -> a));
    }

    private JobVO toJobVO(RecruitmentJob job) {
        JobVO vo = new JobVO();
        BeanUtils.copyProperties(job, vo);
        return vo;
    }

    private CandidateVO toCandidateVO(RecruitmentCandidate candidate, Map<Long, RecruitmentJob> jobMap, Map<Long, RecruitmentResumeFile> resumeMap) {
        CandidateVO vo = new CandidateVO();
        BeanUtils.copyProperties(candidate, vo);
        RecruitmentJob job = jobMap.get(candidate.getJobId());
        if (job != null) {
            vo.setJobTitle(job.getJobTitle());
        }
        RecruitmentResumeFile resumeFile = resumeMap.get(candidate.getResumeFileId());
        if (resumeFile != null) {
            vo.setResumeFileName(resumeFile.getOriginalFileName());
        }
        return vo;
    }

    private ResumeFileVO toResumeFileVO(RecruitmentResumeFile resumeFile) {
        ResumeFileVO vo = new ResumeFileVO();
        BeanUtils.copyProperties(resumeFile, vo);
        return vo;
    }

    private String buildJobCode(String jobTitle) {
        return "JOB-" + Math.abs(Objects.requireNonNullElse(jobTitle, "RECRUITMENT").hashCode());
    }

    private String displayName(SysUser user) {
        return StrUtil.blankToDefault(user.getDisplayName(), user.getUsername());
    }

    private Long nextId(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).max(Long::compareTo).map(id -> id + 1).orElse(1L);
    }
}
