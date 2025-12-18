package org.truong.gvrp_entry_api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final OptimizationJobRepository jobRepository;
    private final SolutionRepository solutionRepository;
    private final TemplateEngine templateEngine;

    @Value("${spring.app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Send optimization success email
     * @param userId User who submitted the job
     * @param jobId Optimization job
     * @param solutionId Solution result
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOptimizationSuccessEmail(Long userId, Long jobId, Long solutionId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("User with ID " + userId + " not found.", "user")
        );
        OptimizationJob job = jobRepository.findById(jobId).orElseThrow(
                () -> new ResourceNotFoundException("Resource not found.", "job")
        );
        Solution solution = solutionRepository.findById(solutionId).orElseThrow(
                () -> new ResourceNotFoundException("Resource not found.", "solution")
        );


        log.info("Sending success email to {} for job #{}", user.getEmail(), job.getId());

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("userName", user.getFullName());
            variables.put("jobId", job.getId());
            variables.put("createdAt", formatDateTime(job.getCreatedAt()));
            variables.put("completedAt", formatDateTime(job.getCompletedAt()));
            variables.put("duration", calculateDuration(job.getCreatedAt(), job.getCompletedAt()));

            // Solution metrics
            variables.put("totalDistance", formatDistance(solution.getTotalDistance()));
            variables.put("totalCO2", formatCO2(solution.getTotalCO2()));
            variables.put("totalServiceTime", formatTime(solution.getTotalTime()));
            variables.put("vehiclesUsed", solution.getTotalVehiclesUsed());
            variables.put("servedOrders", solution.getServedOrders());
            variables.put("unservedOrders", solution.getUnservedOrders());
            variables.put("routeCount", solution.getNumberOfRoutes());

            // Link to solution
            variables.put("solutionUrl", frontendUrl + "/solutions/" + solution.getId());

            String subject = "✅ Optimization Completed - Job #" + job.getId();
            String htmlContent = buildEmailContent("optimization-success", variables);

            sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("Success email sent to {} for job #{}", user.getEmail(), job.getId());

        } catch (Exception e) {
            log.error("Failed to send success email to {} for job #{}: {}",
                    user.getEmail(), job.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send optimization failure email
     * @param userId User who submitted the job
     * @param jobId Optimization job
     * @param error Exception that caused the failure
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOptimizationFailureEmail(Long userId, Long jobId, Exception error) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResourceNotFoundException("User with ID " + userId + " not found.", "user")
        );
        OptimizationJob job = jobRepository.findById(jobId).orElseThrow(
                () -> new ResourceNotFoundException("Resource not found.", "job")
        );

        log.info("Sending failure email to {} for job #{}", user.getEmail(), job.getId());

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("userName", user.getFullName());
            variables.put("jobId", job.getId());
            variables.put("createdAt", formatDateTime(job.getCreatedAt()));
            variables.put("failedAt", formatDateTime(LocalDateTime.now()));
            variables.put("duration", calculateDuration(job.getCreatedAt(), LocalDateTime.now()));

            // Error details
            variables.put("errorMessage", error.getMessage());
            variables.put("errorType", error.getClass().getSimpleName());

            // Suggestions
            variables.put("suggestions", generateFailureSuggestions(error));

            // Link to job history
            variables.put("jobHistoryUrl", frontendUrl + "/solutions/jobs/history");

            String subject = "❌ Optimization Failed - Job #" + job.getId();
            String htmlContent = buildEmailContent("/resources/templates/optimization-failure", variables);

            sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("Failure email sent to {} for job #{}", user.getEmail(), job.getId());

        } catch (Exception e) {
            log.error("Failed to send failure email to {} for job #{}: {}",
                    user.getEmail(), job.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send optimization cancelled email
     * @param user User who cancelled the job
     * @param job Optimization job
     */
    @Async
    public void sendOptimizationCancelledEmail(User user, OptimizationJob job) {
        log.info("Sending cancellation email to {} for job #{}", user.getEmail(), job.getId());

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("userName", user.getFullName());
            variables.put("jobId", job.getId());
            variables.put("createdAt", formatDateTime(job.getCreatedAt()));
            variables.put("cancelledAt", formatDateTime(job.getCancelledAt()));
            variables.put("duration", calculateDuration(job.getCreatedAt(), job.getCancelledAt()));

            // Link to create new job
            variables.put("planningUrl", frontendUrl + "/planning");

            String subject = "🚫 Optimization Cancelled - Job #" + job.getId();
            String htmlContent = buildEmailContent("/resources/templates/optimization-cancelled", variables);

            sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("Cancellation email sent to {} for job #{}", user.getEmail(), job.getId());

        } catch (Exception e) {
            log.error("Failed to send cancellation email to {} for job #{}: {}",
                    user.getEmail(), job.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send HTML email
     * @param to Recipient email
     * @param subject Email subject
     * @param htmlContent HTML content
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * Build email content from Thymeleaf template
     * @param templateName Template name
     * @param variables Template variables
     * @return HTML content
     */
    private String buildEmailContent(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    // ==================== HELPER METHODS ====================

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(FORMATTER);
    }

    private String formatDistance(BigDecimal distance) {
        if (distance == null) return "0 km";
        return String.format("%.2f km", distance);
    }

    private String formatCO2(BigDecimal co2) {
        if (co2 == null) return "0 kg";
        return String.format("%.2f kg", co2);
    }

    private String formatTime(BigDecimal time) {
        if (time == null) return "0 min";
        return String.format("%.0f hours", time);
    }

    private String calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "N/A";

        Duration duration = Duration.between(start, end);
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;

        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }

    private String generateFailureSuggestions(Exception error) {
        String errorMsg = error.getMessage().toLowerCase();

        if (errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
            return "The optimization took too long. Try reducing the number of orders or vehicles.";
        } else if (errorMsg.contains("infeasible") || errorMsg.contains("no solution")) {
            return "No valid solution found. Check vehicle capacities and time windows.";
        } else if (errorMsg.contains("depot") || errorMsg.contains("location")) {
            return "Check that all depots and order locations have valid coordinates.";
        } else if (errorMsg.contains("capacity")) {
            return "Vehicle capacity may be insufficient. Try adding more vehicles or reducing order demands.";
        } else {
            return "Please contact support if the problem persists.";
        }
    }
}