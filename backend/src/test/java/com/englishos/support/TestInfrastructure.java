package com.englishos.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.englishos.shared.mail.EmailSender;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** MySQL 8.4 real en contenedor + buzón de correo en memoria para las pruebas de integración. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }

    public static class CapturingEmailSender implements EmailSender {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            sent.add(message);
        }

        public List<EmailMessage> sentTo(String email) {
            return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
        }

        public EmailMessage lastTo(String email) {
            List<EmailMessage> list = sentTo(email);
            if (list.isEmpty()) {
                throw new AssertionError("No se envió ningún correo a " + email);
            }
            return list.getLast();
        }
    }
}
