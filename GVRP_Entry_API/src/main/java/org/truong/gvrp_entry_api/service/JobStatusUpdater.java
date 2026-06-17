package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobStatusUpdater {

    private final OptimizationJobRepository jobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long jobId, String errorMessage) {
        OptimizationJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(OptimizationJobStatus.FAILED);
            job.setErrorMessage("Internal error: " + errorMessage);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }
}