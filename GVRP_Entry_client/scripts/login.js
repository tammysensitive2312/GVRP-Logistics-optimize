/**
 * Login Page Logic
 * Handles form submission and UI interactions
 */

// Check if already logged in
document.addEventListener('DOMContentLoaded', function() {
    if (isAuthenticated()) {
        window.location.href = 'index.html';
        return;
    }

    // Initialize form
    initLoginForm();

    // Auto-fill if remembered
    autoFillRememberedCredentials();
});

/**
 * Initialize login form
 */
function initLoginForm() {
    const form = document.getElementById('login-form');

    form.addEventListener('submit', async function(event) {
        event.preventDefault();
        await handleLogin();
    });

    // Enter key to submit
    form.querySelectorAll('input').forEach(input => {
        input.addEventListener('keypress', function(event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                form.requestSubmit();
            }
        });
    });
}

/**
 * Handle login form submission
 */
async function handleLogin(event) {
    if (event) event.preventDefault();

    // Get form data
    const branchName = document.getElementById('branch-name').value.trim();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const rememberMe = document.getElementById('remember-me').checked;

    // Validation
    if (!branchName || !username || !password) {
        showError('Vui lòng điền đầy đủ thông tin');
        return;
    }

    // Hide previous errors
    hideError();

    // Show loading state
    setLoginLoading(true);

    try {
        console.log('1. Calling login API...');
        // Call login API
        const authData = await login({
            branch_name: branchName,
            username: username,
            password: password
        });

        console.log('2. Login success, data:', authData);

        // Save session
        saveSession(authData, rememberMe);
        console.log('3. Session saved. Token:', localStorage.getItem('access_token'));

        // Show success message
        showToast('Đăng nhập thành công!', 'success');

        console.log('4. Setting timeout for redirect...');
        // Redirect after short delay
        setTimeout(() => {
            console.log('5. Timeout finished, calling redirect function');
            redirectAfterLogin();
        }, 500);

    } catch (error) {
        console.error('Login failed:', error);
        showError(error.message || 'Đăng nhập thất bại. Vui lòng thử lại.');
        setLoginLoading(false);
    }
}

/**
 * Set login button loading state
 * @param {boolean} loading
 */
function setLoginLoading(loading) {
    const button = document.getElementById('btn-login');
    const btnText = button.querySelector('.btn-text');
    const btnLoader = button.querySelector('.btn-loader');

    button.disabled = loading;

    if (loading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'flex';
    } else {
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

/**
 * Show error message
 * @param {string} message
 */
function showError(message) {
    const errorDiv = document.getElementById('login-error');
    errorDiv.textContent = message;
    errorDiv.style.display = 'flex';

    // Shake animation
    errorDiv.style.animation = 'shake 0.5s';
    setTimeout(() => {
        errorDiv.style.animation = '';
    }, 500);
}

/**
 * Hide error message
 */
function hideError() {
    const errorDiv = document.getElementById('login-error');
    errorDiv.style.display = 'none';
}

/**
 * Toggle password visibility
 */
function togglePassword() {
    const passwordInput = document.getElementById('password');
    const eyeIcon = document.getElementById('eye-icon');

    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
        eyeIcon.textContent = '🙈';
    } else {
        passwordInput.type = 'password';
        eyeIcon.textContent = '👁️';
    }
}

/**
 * Handle forgot password
 * @param {Event} event
 */
function handleForgotPassword(event) {
    event.preventDefault();
    showToast('Tính năng này đang được phát triển', 'error');
}

/**
 * Handle register
 * @param {Event} event
 */
function handleRegister(event) {
    event.preventDefault();
    showToast('Tính năng đăng ký đang được phát triển', 'error');
}

/**
 * Auto-fill remembered credentials
 */
function autoFillRememberedCredentials() {
    const rememberMe = localStorage.getItem('vrp_remember_me');

    if (rememberMe === 'true') {
        const user = getCurrentUser();
        const branch = getCurrentBranch();

        if (user && branch) {
            document.getElementById('branch-name').value = branch.name;
            document.getElementById('username').value = user.username;
            document.getElementById('remember-me').checked = true;

            // Focus on password field
            document.getElementById('password').focus();
        }
    }
}

/**
 * Show toast notification
 * @param {string} message
 * @param {string} type - 'success', 'error', or ''
 */
function showToast(message, type = '') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast show ' + type;

    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Add shake animation to CSS dynamically
const style = document.createElement('style');
style.textContent = `
    @keyframes shake {
        0%, 100% { transform: translateX(0); }
        10%, 30%, 50%, 70%, 90% { transform: translateX(-5px); }
        20%, 40%, 60%, 80% { transform: translateX(5px); }
    }
`;
document.head.appendChild(style);

console.log('Login page ready!');