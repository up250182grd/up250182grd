package com.englishos.identity.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.englishos.config.AppProperties;
import com.englishos.shared.mail.EmailSender;
import com.englishos.shared.mail.EmailSender.EmailMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía los correos de identidad DESPUÉS de confirmar la transacción: si el envío falla, la cuenta o el token
 * ya existen y el usuario puede pedir un reenvío; si la transacción se revierte, no se envía nada.
 */
@Component
class IdentityEmails {

    private static final Logger log = LoggerFactory.getLogger(IdentityEmails.class);

    private final EmailSender emailSender;
    private final AppProperties properties;

    IdentityEmails(EmailSender emailSender, AppProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
    }

    record VerificationEmailRequested(String email, String displayName, String token) {
    }

    record PasswordResetEmailRequested(String email, String token) {
    }

    @TransactionalEventListener
    void on(VerificationEmailRequested event) {
        String link = properties.baseUrl() + "/verify-email?token=" + encode(event.token());
        String hours = String.valueOf(properties.security().emailVerificationTtl().toHours());
        sendQuietly(new EmailMessage(event.email(), "Confirma tu correo en EnglishOS", """
                Hola %s:

                Gracias por registrarte en EnglishOS. Confirma tu correo con este enlace:

                %s

                El enlace caduca en %s horas. Si no creaste esta cuenta, ignora este mensaje.
                """.formatted(event.displayName(), link, hours)));
    }

    @TransactionalEventListener
    void on(PasswordResetEmailRequested event) {
        String link = properties.baseUrl() + "/reset-password?token=" + encode(event.token());
        long minutes = properties.security().passwordResetTtl().toMinutes();
        sendQuietly(new EmailMessage(event.email(), "Restablece tu contraseña de EnglishOS", """
                Recibimos una solicitud para restablecer tu contraseña. Usa este enlace:

                %s

                El enlace caduca en %d minutos y solo funciona una vez. Si no lo solicitaste, ignora este mensaje;
                tu contraseña no cambiará.
                """.formatted(link, minutes)));
    }

    private void sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RuntimeException ex) {
            log.warn("Correo transaccional no enviado ({}); el usuario puede solicitar un reenvío.",
                    ex.getClass().getSimpleName());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
