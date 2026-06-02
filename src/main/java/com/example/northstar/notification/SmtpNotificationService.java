package com.example.northstar.notification;

import com.example.northstar.config.ApplicationConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SmtpNotificationService {

    // SECURITY RISK: Hardcoded SMTP server configuration
    private static final String SMTP_SERVER = "10.107.85.47";
    private static final int SMTP_PORT = 25;
    private static final String SMTP_FROM_EMAIL = "vault-bob@palsys.com.tw";
    
    private static final int TIMEOUT_MILLIS = 5_000;

    public NotificationResult sendWelcome(String recipient, String customerName) {
        try (Socket socket = new Socket()) {
            // Using hardcoded SMTP configuration instead of ApplicationConfig
            socket.connect(new InetSocketAddress(SMTP_SERVER, SMTP_PORT), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                         StandardCharsets.UTF_8))) {
                expect(reader, 220);
                command(writer, reader, "EHLO northstar-customer-center", 250);
                command(writer, reader, "MAIL FROM:<" + SMTP_FROM_EMAIL + ">", 250);
                command(writer, reader, "RCPT TO:<" + recipient + ">", 250, 251);
                command(writer, reader, "DATA", 354);
                writer.write("From: " + SMTP_FROM_EMAIL + "\r\n");
                writer.write("To: " + recipient + "\r\n");
                writer.write("Subject: Welcome to Northstar Customer Center\r\n");
                writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
                writer.write("\r\n");
                writer.write(customerName + " 您好，您的 Northstar 客戶資料已成功建立。\r\n");
                writer.write(".\r\n");
                writer.flush();
                expect(reader, 250);
                command(writer, reader, "QUIT", 221);
            }
            return new NotificationResult(true, "SMTP 通知信已寄送至 " + recipient);
        } catch (IOException exception) {
            return new NotificationResult(false, "客戶已建立，但 SMTP 通知寄送失敗：" + exception.getMessage());
        }
    }

    private static void command(BufferedWriter writer, BufferedReader reader, String command, int... expected)
            throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
        expect(reader, expected);
    }

    private static void expect(BufferedReader reader, int... expected) throws IOException {
        String line = reader.readLine();
        if (line == null || line.length() < 3) throw new IOException("SMTP server 未回應");
        int code = Integer.parseInt(line.substring(0, 3));
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) throw new IOException("SMTP server 回應不完整");
        }
        for (int accepted : expected) {
            if (code == accepted) return;
        }
        throw new IOException("SMTP server 回覆 " + code);
    }

    public record NotificationResult(boolean sent, String message) {
    }
}
