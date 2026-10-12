package org.truong.gvrp_entry_api.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.JobHistoryView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobHistoryMappingTest {
    @Test
    void historyProjectionPreservesExistingResponseFields() {
        var mapper = new OptimizationJobMapper();
        var created = LocalDateTime.of(2026, 10, 4, 10, 0);
        var branch = new Branch();
        branch.setId(2L);
        var user = new User();
        user.setId(3L);
        user.setFullName("Test User");
        var solution = new Solution();
        solution.setId(4L);

        for (var status : OptimizationJobStatus.values()) {
            for (boolean hasSolution : new boolean[]{false, true}) {
                var job = OptimizationJob.builder()
                        .id(1L).branch(branch).createdBy(user).status(status)
                        .externalJobId("external-1").estimatedDurationMinutes(10)
                        .errorMessage("test message").createdAt(created)
                        .cancelledAt(created.plusMinutes(2))
                        .solution(hasSolution ? solution : null)
                        .inputData("{\"notNeededForHistory\":true}").build();
                Map<String, Object> fields = new HashMap<>();
                fields.put("id", job.getId());
                fields.put("branchId", branch.getId());
                fields.put("userId", user.getId());
                fields.put("userName", user.getFullName());
                fields.put("status", status);
                fields.put("externalJobId", job.getExternalJobId());
                fields.put("estimatedDurationMinutes", job.getEstimatedDurationMinutes());
                fields.put("errorMessage", job.getErrorMessage());
                fields.put("createdAt", created);
                fields.put("cancelledAt", job.getCancelledAt());
                fields.put("solutionId", hasSolution ? solution.getId() : null);
                var view = new SpelAwareProxyProjectionFactory()
                        .createProjection(JobHistoryView.class, fields);
                assertThat(mapper.fromHistory(view)).usingRecursiveComparison()
                        .isEqualTo(mapper.toDTO(job));

                job.setCompletedAt(created.plusMinutes(3));
                fields.put("completedAt", job.getCompletedAt());
                view = new SpelAwareProxyProjectionFactory()
                        .createProjection(JobHistoryView.class, fields);
                assertThat(mapper.fromHistory(view)).usingRecursiveComparison()
                        .isEqualTo(mapper.toDTO(job));
            }
        }
    }
}
