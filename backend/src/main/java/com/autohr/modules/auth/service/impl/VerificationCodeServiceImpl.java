package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.service.VerificationCodeService;
import com.autohr.modules.auth.service.CaptchaService;
import com.autohr.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final String[] CONFIG_KEYS = {
            "ALIYUN_SMS_ACCESS_KEY_ID", "ALIYUN_SMS_ACCESS_KEY_SECRET", "ALIYUN_SMS_ENDPOINT", "ALIYUN_SMS_SIGN_NAME", "ALIYUN_SMS_TEMPLATE_CODE",
            "ALIYUN_ACCESS_KEY_ID", "ALIYUN_ACCESS_KEY_SECRET",
            "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "SMTP_SSL_ENABLED", "SMTP_STARTTLS_ENABLED"
    };
    private static final int EXPIRE_MINUTES = 5;
    private static final int RESEND_SECONDS = 60;
    private static final String REGISTER_PURPOSE = "register";
    private static final String PASSWORD_RESET_PURPOSE = "password-reset";

    private final Map<String, CodeRecord> codeStore = new ConcurrentHashMap<>();
    private final SystemConfigService systemConfigService;
    private final CaptchaService captchaService;

    @Override
    public void sendRegisterCode(String mobilePhone, String email, String captchaId, String captchaCode) {
        sendCode(REGISTER_PURPOSE, mobilePhone, email, captchaId, captchaCode);
    }

    @Override
    public void verifyRegisterCode(String mobilePhone, String email, String code) {
        verifyCode(REGISTER_PURPOSE, mobilePhone, email, code);
    }

    @Override
    public void sendPasswordResetCode(String mobilePhone, String email, String captchaId, String captchaCode) {
        sendCode(PASSWORD_RESET_PURPOSE, mobilePhone, email, captchaId, captchaCode);
    }

    @Override
    public void verifyPasswordResetCode(String mobilePhone, String email, String code) {
        verifyCode(PASSWORD_RESET_PURPOSE, mobilePhone, email, code);
    }

    private void sendCode(String purpose, String mobilePhone, String email, String captchaId, String captchaCode) {
        cleanupExpiredCodes();
        captchaService.verifyCaptcha(captchaId, captchaCode);
        String target = normalizeTarget(mobilePhone, email);
        String codeKey = codeKey(purpose, target);
        CodeRecord oldRecord = codeStore.get(codeKey);
        if (oldRecord != null && oldRecord.sentAt().plusSeconds(RESEND_SECONDS).isAfter(LocalDateTime.now())) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        String code = RandomUtil.randomNumbers(6);
        if (target.startsWith("sms:")) {
            sendSms(target.substring(4), code);
        } else {
            sendEmail(target.substring(6), code);
        }
        LocalDateTime now = LocalDateTime.now();
        codeStore.put(codeKey, new CodeRecord(code, now, now.plusMinutes(EXPIRE_MINUTES)));
    }

    private void verifyCode(String purpose, String mobilePhone, String email, String code) {
        cleanupExpiredCodes();
        String target = normalizeTarget(mobilePhone, email);
        String codeKey = codeKey(purpose, target);
        CodeRecord record = codeStore.get(codeKey);
        if (record == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            codeStore.remove(codeKey, record);
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!StrUtil.equals(record.code(), code)) {
            throw new BusinessException("验证码错误");
        }
        codeStore.remove(codeKey);
    }

    private String codeKey(String purpose, String target) {
        return purpose + ':' + target;
    }

    private String normalizeTarget(String mobilePhone, String email) {
        boolean hasPhone = StrUtil.isNotBlank(mobilePhone);
        boolean hasEmail = StrUtil.isNotBlank(email);
        if (hasPhone == hasEmail) {
            throw new BusinessException("手机号和邮箱必须择一提供");
        }
        return hasPhone ? "sms:" + mobilePhone.trim() : "email:" + email.trim().toLowerCase();
    }

    private void sendSms(String mobilePhone, String code) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        // Deprecated generic credentials remain a read-only fallback for existing deployments.
        config.put("ALIYUN_ACCESS_KEY_ID", firstNonBlank(config.get("ALIYUN_SMS_ACCESS_KEY_ID"), config.get("ALIYUN_ACCESS_KEY_ID")));
        config.put("ALIYUN_ACCESS_KEY_SECRET", firstNonBlank(config.get("ALIYUN_SMS_ACCESS_KEY_SECRET"), config.get("ALIYUN_ACCESS_KEY_SECRET")));
        requireConfig(config, "ALIYUN_ACCESS_KEY_ID", "阿里云AccessKey ID未配置");
        requireConfig(config, "ALIYUN_ACCESS_KEY_SECRET", "阿里云AccessKey Secret未配置");
        requireConfig(config, "ALIYUN_SMS_SIGN_NAME", "短信签名未配置");
        requireConfig(config, "ALIYUN_SMS_TEMPLATE_CODE", "短信模板Code未配置");
        try {
            Config aliyunConfig = new Config()
                    .setAccessKeyId(config.get("ALIYUN_ACCESS_KEY_ID"))
                    .setAccessKeySecret(config.get("ALIYUN_ACCESS_KEY_SECRET"));
            aliyunConfig.endpoint = StrUtil.blankToDefault(config.get("ALIYUN_SMS_ENDPOINT"), "dysmsapi.aliyuncs.com");
            Client client = new Client(aliyunConfig);
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(mobilePhone)
                    .setSignName(config.get("ALIYUN_SMS_SIGN_NAME"))
                    .setTemplateCode(config.get("ALIYUN_SMS_TEMPLATE_CODE"))
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.sendSms(request);
            String responseCode = response == null || response.getBody() == null ? null : response.getBody().getCode();
            if (!StrUtil.equalsIgnoreCase(responseCode, "OK")) {
                String responseMessage = response == null || response.getBody() == null ? "无响应内容" : response.getBody().getMessage();
                String requestId = response == null || response.getBody() == null ? "" : response.getBody().getRequestId();
                throw new BusinessException("短信验证码发送失败: " + StrUtil.blankToDefault(responseMessage, responseCode)
                        + (StrUtil.isBlank(requestId) ? "" : " (RequestId: " + requestId + ")"));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BusinessException("短信验证码发送失败");
        }
    }

    private void sendEmail(String email, String code) {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        requireConfig(config, "SMTP_HOST", "SMTP服务器未配置");
        requireConfig(config, "SMTP_PORT", "SMTP端口未配置");
        requireConfig(config, "SMTP_USERNAME", "SMTP用户名未配置");
        requireConfig(config, "SMTP_PASSWORD", "SMTP密码未配置");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.get("SMTP_HOST"));
        sender.setPort(parseSmtpPort(config.get("SMTP_PORT")));
        sender.setUsername(config.get("SMTP_USERNAME"));
        sender.setPassword(config.get("SMTP_PASSWORD"));
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", StrUtil.blankToDefault(config.get("SMTP_SSL_ENABLED"), "false"));
        props.put("mail.smtp.starttls.enable", StrUtil.blankToDefault(config.get("SMTP_STARTTLS_ENABLED"), "true"));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(StrUtil.blankToDefault(config.get("SMTP_FROM"), config.get("SMTP_USERNAME")));
        message.setTo(email);
        message.setSubject("人事系统验证码");
        message.setText("您的验证码为：" + code + "，有效期" + EXPIRE_MINUTES + "分钟。");
        try {
            sender.send(message);
        } catch (Exception ex) {
            throw new BusinessException("邮件验证码发送失败");
        }
    }

    private void requireConfig(Map<String, String> config, String key, String message) {
        if (StrUtil.isBlank(config.get(key))) {
            throw new BusinessException(message);
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StrUtil.isNotBlank(preferred) ? preferred : StrUtil.blankToDefault(fallback, "");
    }

    private int parseSmtpPort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new BusinessException("SMTP端口必须是 1 到 65535 的整数");
        }
    }

    private void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codeStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record CodeRecord(String code, LocalDateTime sentAt, LocalDateTime expiresAt) {}
}
