package com.englishos.shared.mail;

/** Puerto de envío de correo transaccional. La implementación SMTP es sustituible (y se reemplaza en pruebas). */
public interface EmailSender {

    void send(EmailMessage message);

    record EmailMessage(String to, String subject, String textBody) {
    }
}
