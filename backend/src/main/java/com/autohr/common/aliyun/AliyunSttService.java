package com.autohr.common.aliyun;

import com.autohr.common.exception.BusinessException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class AliyunSttService {

    @Value("${aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.stt.app-key:}")
    private String appKey;

    @Value("${aliyun.stt.endpoint:nls-gateway-cn-shanghai.aliyuncs.com}")
    private String endpoint;

    public boolean isConfigured() {
        return StrUtil.isNotBlank(accessKeyId)
                && StrUtil.isNotBlank(accessKeySecret)
                && StrUtil.isNotBlank(appKey);
    }

    public String transcribe(String audioFilePath) {
        if (!isConfigured()) {
            throw new BusinessException("阿里云语音识别未配置，请设置ALIYUN_ACCESS_KEY_ID、ALIYUN_ACCESS_KEY_SECRET、ALIYUN_STT_APP_KEY");
        }
        Path path = Path.of(audioFilePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new BusinessException("音频文件不可读: " + audioFilePath);
        }
        try {
            String token = createToken();
            byte[] audioBytes = Files.readAllBytes(path);
            String url = "https://" + endpoint + "/stream/v1/asr";
            HttpResponse response = HttpRequest.post(url)
                    .header("X-NLS-Token", token)
                    .header("X-NLS-AppKey", appKey)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", String.valueOf(audioBytes.length))
                    .body(audioBytes)
                    .timeout(120000)
                    .execute();
            if (!response.isOk()) {
                throw new BusinessException("语音识别请求失败，HTTP " + response.getStatus() + ": " + response.body());
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            int status = result.getInt("status", -1);
            if (status != 20000000) {
                throw new BusinessException("语音识别失败: " + result.getStr("message", response.body()));
            }
            String text = result.getStr("result", "");
            if (StrUtil.isBlank(text)) {
                throw new BusinessException("语音识别返回为空");
            }
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取音频文件失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException("语音识别异常: " + ex.getMessage());
        }
    }

    private String createToken() {
        JSONObject payload = new JSONObject();
        payload.set("AccessKeyId", accessKeyId);
        payload.set("Format", "JSON");
        payload.set("RegionId", "cn-shanghai");
        payload.set("SignatureMethod", "HMAC-SHA1");
        payload.set("SignatureNonce", String.valueOf(System.nanoTime()));
        payload.set("SignatureVersion", "1.0");
        payload.set("Timestamp", java.time.Instant.now().toString().substring(0, 19) + "Z");
        payload.set("Version", "2019-02-28");
        payload.set("Action", "CreateToken");
        try {
            payload.set("Signature", signTokenRequest(payload));
        } catch (Exception ex) {
            throw new BusinessException("生成NLS Token签名失败: " + ex.getMessage());
        }
        HttpResponse response = HttpRequest.get("https://nls-meta.cn-shanghai.aliyuncs.com/")
                .form(payload)
                .timeout(10000)
                .execute();
        if (!response.isOk()) {
            throw new BusinessException("获取NLS Token失败，HTTP " + response.getStatus());
        }
        JSONObject body = JSONUtil.parseObj(response.body());
        JSONObject token = body.getByPath("Token", JSONObject.class);
        if (token == null || StrUtil.isBlank(token.getStr("Id"))) {
            throw new BusinessException("获取NLS Token失败: " + response.body());
        }
        return token.getStr("Id");
    }

    private String signTokenRequest(JSONObject params) throws Exception {
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        params.forEach((k, v) -> sorted.put(k, String.valueOf(v)));
        StringBuilder canonicalQuery = new StringBuilder();
        for (var entry : sorted.entrySet()) {
            if (canonicalQuery.length() > 0) canonicalQuery.append("&");
            canonicalQuery.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        String stringToSign = "GET&" + urlEncode("/") + "&" + urlEncode(canonicalQuery.toString());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(signBytes);
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (java.io.UnsupportedEncodingException ex) {
            return value;
        }
    }
}
