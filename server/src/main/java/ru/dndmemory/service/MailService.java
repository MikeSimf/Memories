package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dndmemory.config.AppProperties;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Отправка писем по SMTP без отдельной библиотеки: нужен только исходящий
 * релей, принимать почту не нужно.
 *
 * <p>Письма отправляются через SSL (порт 465 по умолчанию) либо обычным
 * соединением, если {@code dnd.mail.ssl=false}. Если почта не настроена,
 * ссылка пишется в лог — разработка и тест работают без SMTP.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final AppProperties properties;

    public boolean enabled() {
        AppProperties.Mail mail = properties.getMail();
        return mail.isEnabled() && mail.getHost() != null && !mail.getHost().isEmpty();
    }

    /** Отправляет письмо; ошибка SMTP не должна ломать регистрацию или сброс. */
    public boolean send(String to, String subject, String text) {
        AppProperties.Mail mail = properties.getMail();
        if (!enabled()) {
            log.warn("ПОЧТА НЕ НАСТРОЕНА: письмо для {} «{}» не отправлено", to, subject);
            return false;
        }
        try {
            deliver(mail, to, subject, text);
            log.info("Письмо отправлено: {} «{}»", to, subject);
            return true;
        } catch (Exception e) {
            log.error("Не удалось отправить письмо на {}: {}", to, e.getMessage());
            return false;
        }
    }

    private void deliver(AppProperties.Mail mail, String to, String subject, String text)
            throws Exception {
        Socket socket;
        if (mail.isSsl()) {
            socket = SSLSocketFactory.getDefault().createSocket(mail.getHost(), mail.getPort());
        } else {
            socket = new Socket(mail.getHost(), mail.getPort());
        }
        socket.setSoTimeout(15_000);
        try (Socket connection = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
            expect(in, 220);
            command(in, out, "EHLO dnd-memory", 250);
            if (mail.getUsername() != null && !mail.getUsername().isEmpty()) {
                command(in, out, "AUTH LOGIN", 334);
                command(in, out, base64(mail.getUsername()), 334);
                command(in, out, base64(mail.getPassword() == null ? "" : mail.getPassword()), 235);
            }
            command(in, out, "MAIL FROM:<" + mail.getFrom() + ">", 250);
            command(in, out, "RCPT TO:<" + to + ">", 250);
            command(in, out, "DATA", 354);

            String body = base64(text);
            out.write("From: " + fromHeader(mail) + "\r\n");
            out.write("To: <" + to + ">\r\n");
            out.write("Subject: =?UTF-8?B?" + base64(subject) + "?=\r\n");
            out.write("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n");
            out.write("MIME-Version: 1.0\r\n");
            out.write("Content-Type: text/plain; charset=UTF-8\r\n");
            out.write("Content-Transfer-Encoding: base64\r\n\r\n");
            for (int i = 0; i < body.length(); i += 76) {
                out.write(body.substring(i, Math.min(i + 76, body.length())));
                out.write("\r\n");
            }
            out.write(".\r\n");
            out.flush();
            expect(in, 250);
            command(in, out, "QUIT", 221);
        }
    }

    private String fromHeader(AppProperties.Mail mail) {
        String name = mail.getFromName() == null || mail.getFromName().isEmpty()
                ? "DnD Memory" : mail.getFromName();
        return "=?UTF-8?B?" + base64(name) + "?= <" + mail.getFrom() + ">";
    }

    private void command(BufferedReader in, BufferedWriter out, String command, int expected)
            throws Exception {
        out.write(command + "\r\n");
        out.flush();
        expect(in, expected);
    }

    private void expect(BufferedReader in, int expected) throws Exception {
        while (true) {
            String line = in.readLine();
            if (line == null) {
                throw new IllegalStateException("SMTP закрыл соединение");
            }
            if (line.length() >= 3) {
                int code;
                try {
                    code = Integer.parseInt(line.substring(0, 3));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (code != expected) {
                    throw new IllegalStateException("SMTP " + line);
                }
                if (line.length() == 3 || line.charAt(3) != '-') {
                    return;
                }
            }
        }
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
