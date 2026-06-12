package com.autohr.modules.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.autohr.common.exception.BusinessException;
import com.autohr.modules.auth.dto.CaptchaVO;
import com.autohr.modules.auth.service.CaptchaService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final int EXPIRE_MINUTES = 5;
    private final Map<String, CaptchaRecord> captchaStore = new ConcurrentHashMap<>();

    @Override
    public CaptchaVO createCaptcha() {
        cleanupExpired();
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        captchaStore.put(captchaId, new CaptchaRecord(code, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        return new CaptchaVO(captchaId, createImageBase64(code));
    }

    @Override
    public void verifyCaptcha(String captchaId, String captchaCode) {
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(captchaCode)) {
            throw new BusinessException("图形验证码必填");
        }
        CaptchaRecord record = captchaStore.remove(captchaId);
        if (record == null || record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("图形验证码已过期，请刷新后重试");
        }
        if (!StrUtil.equalsIgnoreCase(record.code(), captchaCode.trim())) {
            throw new BusinessException("图形验证码错误");
        }
    }

    private String createImageBase64(String code) {
        int width = 128;
        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(246, 241, 232));
        graphics.fillRect(0, 0, width, height);
        for (int i = 0; i < 9; i++) {
            graphics.setColor(randomSoftColor());
            graphics.drawLine(RandomUtil.randomInt(width), RandomUtil.randomInt(height), RandomUtil.randomInt(width), RandomUtil.randomInt(height));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            graphics.setColor(randomTextColor());
            graphics.rotate(Math.toRadians(RandomUtil.randomInt(-18, 18)), 24 + i * 24, 28);
            graphics.drawString(String.valueOf(code.charAt(i)), 16 + i * 26, 32);
            graphics.rotate(Math.toRadians(RandomUtil.randomInt(-18, 18) * -1), 24 + i * 24, 28);
        }
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            throw new BusinessException("图形验证码生成失败");
        }
    }

    private Color randomSoftColor() {
        return new Color(RandomUtil.randomInt(150, 220), RandomUtil.randomInt(150, 220), RandomUtil.randomInt(150, 220));
    }

    private Color randomTextColor() {
        return new Color(RandomUtil.randomInt(25, 90), RandomUtil.randomInt(45, 110), RandomUtil.randomInt(60, 130));
    }

    private String randomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt(RandomUtil.randomInt(chars.length())));
        }
        return builder.toString();
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record CaptchaRecord(String code, LocalDateTime expiresAt) {}
}
