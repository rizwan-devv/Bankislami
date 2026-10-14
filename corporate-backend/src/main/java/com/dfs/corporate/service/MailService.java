package com.dfs.corporate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String brandName;

    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from}") String from,
            @Value("${app.brand-name:Bank Islami Corporate}") String brandName) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.brandName = brandName != null && !brandName.isBlank()
                ? brandName.trim() : "Bank Islami Corporate";
    }

    /** Portal brand for email subjects/bodies (replaces legacy PayFast Corporate copy). */
    public String brand() {
        return brandName;
    }

    public void send(String to, String subject, String body) {
        String brandedSubject = brandCopy(subject);
        String brandedBody = brandCopy(body);
        if (!enabled) {
            log.info("[MAIL-DEV] to={} subject={} body=\n{}", to, brandedSubject, brandedBody);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(brandedSubject);
            msg.setText(brandedBody);
            mailSender.send(msg);
            log.info("Mail sent from={} to={} subject={}", from, to, brandedSubject);
            log.info("[MAIL-DEV] to={} subject={} body=\n{}", to, brandedSubject, brandedBody);
        } catch (Exception e) {
            // Demo-safe: SMTP failure must not fail signup / submit / approve
            log.error("Mail send failed from={} to={} subject={}: {}", from, to, brandedSubject, e.getMessage());
            log.info("[MAIL-FALLBACK] to={} subject={} body=\n{}", to, brandedSubject, brandedBody);
        }
    }

    private String brandCopy(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
                .replace("PayFast Corporate", brandName)
                .replace("Your Corporate XPin", "Your " + brandName + " XPin");
    }
}
