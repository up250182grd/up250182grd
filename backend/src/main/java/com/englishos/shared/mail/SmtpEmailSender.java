package com.englishos.shared.mail;

import com.englishos.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.mail().from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.textBody());
        try {
            mailSender.send(mail);
        } catch (MailException ex) {
            // No se registra el destinatario ni el cuerpo (contienen datos personales y tokens).
            log.error("No se pudo enviar el correo transaccional: {}", ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
