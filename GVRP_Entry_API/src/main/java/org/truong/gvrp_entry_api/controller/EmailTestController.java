package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.service.EmailService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/test/emails")
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @GetMapping("/preview/success")
    public String previewSuccessEmail() {
        User user = userRepository.findById(1L).orElseThrow();

        OptimizationJob mockJob = OptimizationJob.builder()
                .id(123L)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .completedAt(LocalDateTime.now())
                .build();

        Solution mockSolution = Solution.builder()
                .id(456L)
                .totalDistance(new BigDecimal("125.50"))
                .totalCO2(new BigDecimal("45.20"))
                .totalServiceTime(new BigDecimal("240"))
                .totalVehiclesUsed(5)
                .servedOrders(48)
                .unservedOrders(0)
                .build();

        emailService.sendOptimizationSuccessEmail(user, mockJob, mockSolution);
        return "Success email sent!";
    }
}
