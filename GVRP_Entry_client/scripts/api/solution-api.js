/**
 * Solutions API Client
 * Handles route planning and optimization jobs
 */
// ============================================
// ROUTE PLANNING & OPTIMIZATION API
// ============================================
/**
 * Submit route planning job
 * POST /api/v1/jobs/plan
 */
async function submitRoutePlanningJob(request) {
    try {
        const response = await fetch(`${API_BASE_URL}/jobs/plan`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(request)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to submit job');
        }
        return await response.json();
    } catch (error) {
        console.error('Submit job error:', error);
        throw error;
    }
}
/**
 * Get current running job
 * GET /api/v1/jobs/current
 */
async function getCurrentRunningJob() {
    try {
        const response = await fetch(`${API_BASE_URL}/jobs/current`, {
            headers: getHeaders()
        });
        if (response.status === 204) {
            return null; // No running job
        }
        if (!response.ok) {
            throw new Error('Failed to fetch current job');
        }
        return await response.json();
    } catch (error) {
        console.error('Get current job error:', error);
        throw error;
    }
}
/**
 * Get job history
 * GET /api/v1/jobs/history?limit=10
 */
async function getJobHistory(limit = 10) {
    try {
        const response = await fetch(
            `${API_BASE_URL}/jobs`,
            { headers: getHeaders() }
        );

        if (!response.ok) {
            throw new Error('Failed to fetch job history');
        }
        return await response.json();
    } catch (error) {
        console.error('Get job history error:', error);
        throw error;
    }
}
/**
 * Get job by ID
 * GET /api/v1/jobs/{id}
 */
async function getJobById(jobId) {
    try {
        const response = await fetch(
            `${API_BASE_URL}/jobs/${jobId}`,
            { headers: getHeaders() }
        );
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to fetch job');
        }
        return await response.json();
    } catch (error) {
        console.error('Get job error:', error);
        throw error;
    }
}
/**
 * Cancel running job
 * DELETE /api/v1/jobs/{id}
 */
async function cancelJob(jobId) {
    try {
        const response = await fetch(
            `${API_BASE_URL}/jobs/${jobId}`,
            {
                method: 'DELETE',
                headers: getHeaders()
            }
        );
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to cancel job');
        }
        return true;
    } catch (error) {
        console.error('Cancel job error:', error);
        throw error;
    }
}

/**
 * Get solution by ID
 * GET /api/v1/solutions/{id}
 * @param {number} solutionId
 * @returns {Promise<Object>} Solution DTO with routes
 */
async function getSolutionById(solutionId) {
    try {
        const response = await fetch(
            `${API_BASE_URL}/solutions/${solutionId}`,
            { headers: getHeaders() }
        );

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to fetch solution');
        }

        return await response.json();
    } catch (error) {
        console.error('Get solution error:', error);
        throw error;
    }
}

// Export to window
window.submitRoutePlanningJob = submitRoutePlanningJob;
window.getCurrentRunningJob = getCurrentRunningJob;
window.getJobHistory = getJobHistory;
window.getJobById = getJobById;
window.cancelJob = cancelJob;
window.getSolutionById = getSolutionById;
console.log('Jobs API Client loaded.');
