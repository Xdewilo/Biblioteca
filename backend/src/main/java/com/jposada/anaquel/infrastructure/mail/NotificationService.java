// by Jeremy Posada
package com.jposada.anaquel.infrastructure.mail;

import com.jposada.anaquel.infrastructure.config.MailProperties;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Ningun metodo lanza excepciones: un fallo de SMTP no tumba un prestamo ya registrado. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    private final LoanRepository loanRepository;
    private final AppUserRepository userRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public void sendLoanConfirmation(Long loanId) {
        loanRepository.findById(loanId).ifPresentOrElse(loan -> {
            Map<String, Object> model = loanModel(loan);
            model.put("greeting", "Tu prestamo quedo registrado");
            send(loan.getBorrowerEmail(),
                    "Prestamo confirmado: %s".formatted(loan.getBook().getTitle()),
                    "email/loan-confirmation", model);
        }, () -> log.warn("No se envio la confirmacion: el prestamo {} no existe", loanId));
    }

    @Transactional(readOnly = true)
    public boolean sendDueSoonReminder(Long loanId) {
        return loanRepository.findById(loanId).map(loan -> {
            Map<String, Object> model = loanModel(loan);
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), loan.getDueDate());
            model.put("daysLeft", days);
            model.put("dueToday", days <= 0);
            return send(loan.getBorrowerEmail(),
                    "Tu prestamo vence pronto: %s".formatted(loan.getBook().getTitle()),
                    "email/loan-due-soon", model);
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public void sendAccountBlockedNotice(Long userId, Instant blockedUntil, long lateReturns) {
        userRepository.findById(userId).ifPresent(user -> {
            Map<String, Object> model = new HashMap<>();
            model.put("userName", user.getName());
            model.put("lateReturns", lateReturns);
            model.put("blockedUntil", formatInstant(blockedUntil));
            model.put("appUrl", mailProperties.appUrl());
            send(user.getEmail(),
                    "Tu cuenta quedo bloqueada temporalmente para nuevos prestamos",
                    "email/account-blocked", model);
        });
    }

    @Transactional(readOnly = true)
    public void sendBookAvailableNotice(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            Map<String, Object> model = new HashMap<>();
            model.put("bookTitle", reservation.getBook().getTitle());
            model.put("bookAuthor", reservation.getBook().getAuthor());
            model.put("bookIsbn", reservation.getBook().getIsbn());
            model.put("coverUrl", reservation.getBook().getCoverUrl());
            model.put("requestedAt", formatInstant(reservation.getRequestedAt()));
            model.put("appUrl", mailProperties.appUrl());
            send(reservation.getRequesterEmail(),
                    "Ya esta disponible: %s".formatted(reservation.getBook().getTitle()),
                    "email/book-available", model);
        });
    }

    @Transactional(readOnly = true)
    public boolean sendOverdueNotice(Long loanId) {
        return loanRepository.findById(loanId).map(loan -> {
            Map<String, Object> model = loanModel(loan);
            model.put("daysOverdue", loan.daysOverdue(LocalDate.now()));
            return send(loan.getBorrowerEmail(),
                    "Prestamo vencido: %s".formatted(loan.getBook().getTitle()),
                    "email/loan-overdue", model);
        }).orElse(false);
    }

    private Map<String, Object> loanModel(Loan loan) {
        Map<String, Object> model = new HashMap<>();
        model.put("borrowerName", loan.getBorrowerName());
        model.put("bookTitle", loan.getBook().getTitle());
        model.put("bookAuthor", loan.getBook().getAuthor());
        model.put("bookIsbn", loan.getBook().getIsbn());
        model.put("coverUrl", loan.getBook().getCoverUrl());
        model.put("loanDate", loan.getLoanDate().format(DATE_FORMAT));
        model.put("dueDate", loan.getDueDate().format(DATE_FORMAT));
        model.put("appUrl", mailProperties.appUrl());
        return model;
    }

    /** @return true si el SMTP acepto el mensaje (el scheduler lo usa para marcar el aviso). */
    private boolean send(String to, String subject, String template, Map<String, Object> model) {
        try {
            Context context = new Context(new Locale("es", "CO"));
            context.setVariables(model);
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            setFrom(helper);

            mailSender.send(message);
            log.info("Correo enviado a {} | asunto: {}", to, subject);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo '{}' a {}: {}", subject, to, e.getMessage(), e);
            return false;
        }
    }

    private void setFrom(MimeMessageHelper helper) throws MessagingException {
        try {
            helper.setFrom(mailProperties.from(), mailProperties.fromName());
        } catch (UnsupportedEncodingException e) {
            helper.setFrom(mailProperties.from());
        }
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
    }
}
