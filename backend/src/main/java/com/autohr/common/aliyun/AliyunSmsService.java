package com.autohr.common.aliyun;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.autohr.common.exception.BusinessException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Service
public class AliyunSmsService {

    @Value("${aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name:}")
    private String signName;

    @Value("${aliyun.sms.template-code:}")
    private String templateCode;

    private IAcsClient client;

    @PostConstruct
    public void init() {
        if (StrUtil.isNotBlank(accessKeyId) && StrUtil.isNotBlank(accessKeySecret)) {
            DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
            client = new DefaultAcsClient(profile);
        }
    }

    public boolean isConfigured() {
        return client != null && StrUtil.isNotBlank(signName) && StrUtil.isNotBlank(templateCode);
    }

    public void sendSms(String phoneNumbers, String templateParamJson) {
        if (!isConfigured()) {
            throw new BusinessException("阿里云短信服务未配置，请设置ALIYUN_ACCESS_KEY_ID、ALIYUN_ACCESS_KEY_SECRET、ALIYUN_SMS_SIGN_NAME、ALIYUN_SMS_TEMPLATE_CODE");
        }
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", phoneNumbers);
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateCode);
        if (StrUtil.isNotBlank(templateParamJson)) {
            request.putQueryParameter("TemplateParam", templateParamJson);
        }
        try {
            CommonResponse response = client.getCommonResponse(request);
            Map<String, Object> result = JSONUtil.parseObj(response.getData());
            String code = (String) result.get("Code");
            if (!"OK".equals(code)) {
                throw new BusinessException("短信发送失败: " + result.get("Message"));
            }
        } catch (ClientException ex) {
            throw new BusinessException("短信发送异常: " + ex.getMessage());
        }
    }

    public void sendVerificationCode(String phone, String code) {
        sendSms(phone, "{\"code\":\"" + code + "\"}");
    }
}
