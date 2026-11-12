package com.xiye.garden.auth.service;

import com.xiye.garden.auth.entity.VerificationPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationMailSender {
    private final JavaMailSender sender;
    @Value("${garden.mail.from}")
    private String from;

    public void send(String email, VerificationPurpose purpose, String code) {
        var mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(email);
        String label = purpose == VerificationPurpose.REGISTER ? "注册账号" : "重置密码";
        mail.setSubject("专注花园 · " + label + "验证码");
        mail.setText("你正在申请" + label + "。验证码：" + code + "\n验证码10分钟内有效，仅可使用一次。请勿向他人透露。\n如非本人操作，请忽略此邮件。");
        sender.send(mail);
    }
}
