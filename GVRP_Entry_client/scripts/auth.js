/**
 * Authentication Module
 * Handles login, logout, session management
 */

// Configuration
const AUTH_API_URL = 'http://localhost:8080/api/v1/auth';
const MOCK_AUTH = false; // Set to false when backend is ready

// Session storage keys
const SESSION_KEYS = {
    TOKEN: 'vrp_auth_token',
    USER: 'vrp_user_data',
    BRANCH: 'vrp_branch_data',
    REMEMBER: 'vrp_remember_me'
};

// ============================================
// AUTHENTICATION API
// ============================================

/**
 * Login with credentials
 * @param {Object} credentials - {branchName, username, password}
 * @returns {Promise<Object>} Auth response with token and user data
 */
async function login(credentials) {

    try {
        const response = await fetch(`${AUTH_API_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(credentials)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Login failed');
        }

        return await response.json();
    } catch (error) {
        console.error('Login error:', error);
        throw error;
    }
}

/**
 * Logout current user
 */
async function logout() {
    try {
        const token = getAuthToken();
        await fetch(`${AUTH_API_URL}/logout`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        clearSession();
        window.location.href = 'login.html';
    } catch (error) {
        console.error('Logout error:', error);
        clearSession();
        window.location.href = 'login.html';
    }
}

/**
 * Check if user is authenticated
 * @returns {boolean}
 */
function isAuthenticated() {
    const token = getAuthToken();
    const user = getCurrentUser();

    if (!token || !user) {
        return false;
    }


    try {
        const payload = parseJWT(token);

        // JWT uses seconds (payload.exp), Date.now() uses milliseconds.
        const tokenExpirationTimeMs = payload.exp * 1000;
        const currentTimeMs = Date.now();

        // Điều kiện kiểm tra: Thời gian hết hạn < Thời gian hiện tại
        if (payload.exp && tokenExpirationTimeMs < currentTimeMs) {
            console.warn('  AUTHENTICATION FAILED: Token is EXPIRED.');
            clearSession();
            return false;
        }
    } catch (error) {
        // Invalid token format
        console.error('  AUTHENTICATION FAILED: Invalid token format (JWT parsing error).', error);
        return false;
    }

    return true;
}

/**
 * Get authentication token
 * @returns {string|null}
 */
function getAuthToken() {
    return sessionStorage.getItem(SESSION_KEYS.TOKEN) ||
        localStorage.getItem(SESSION_KEYS.TOKEN);
}

/**
 * Get current user data
 * @returns {Object|null}
 */
function getCurrentUser() {
    const userJson = sessionStorage.getItem(SESSION_KEYS.USER) ||
        localStorage.getItem(SESSION_KEYS.USER);

    try {
        return userJson ? JSON.parse(userJson) : null;
    } catch (error) {
        return null;
    }
}

/**
 * Get current branch data
 * @returns {Object|null}
 */
function getCurrentBranch() {
    const branchId = sessionStorage.getItem(SESSION_KEYS.BRANCH) ||
        localStorage.getItem(SESSION_KEYS.BRANCH);

    return branchId
}

/**
 * Save authentication session
 * @param {Object} authData - {token, user, branch}
 * @param {boolean} rememberMe
 */
function saveSession(authData, rememberMe = false) {
    const storage = rememberMe ? localStorage : sessionStorage;

    storage.setItem(SESSION_KEYS.TOKEN, authData.access_token);

    const userObject = {
        id: authData.user_id,
        username: authData.username,
        role: authData.role
    };
    storage.setItem(SESSION_KEYS.USER, JSON.stringify(userObject));
    storage.setItem(SESSION_KEYS.BRANCH, authData.branch_id);

    if (rememberMe) {
        localStorage.setItem(SESSION_KEYS.REMEMBER, 'true');
    }
}

/**
 * Clear authentication session
 */
function clearSession() {
    // Clear sessionStorage
    sessionStorage.removeItem(SESSION_KEYS.TOKEN);
    sessionStorage.removeItem(SESSION_KEYS.USER);
    sessionStorage.removeItem(SESSION_KEYS.BRANCH);

    // Clear localStorage
    localStorage.removeItem(SESSION_KEYS.TOKEN);
    localStorage.removeItem(SESSION_KEYS.USER);
    localStorage.removeItem(SESSION_KEYS.BRANCH);
    localStorage.removeItem(SESSION_KEYS.REMEMBER);
}

/**
 * Parse JWT token
 * @param {string} token
 * @returns {Object}
 */
function parseJWT(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
            atob(base64).split('').map(c => {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join('')
        );
        return JSON.parse(jsonPayload);
    } catch (error) {
        console.error('Invalid JWT token:', error);
        return {};
    }
}

/**
 * Protect page - redirect to login if not authenticated
 */
function requireAuth() {
    if (!isAuthenticated()) {
        // Save current URL for redirect after login
        sessionStorage.setItem('vrp_redirect_url', window.location.href);
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

/**
 * Redirect to intended page after login
 */
function redirectAfterLogin() {
    console.log('Executing redirectAfterLogin');
    const redirectUrl = sessionStorage.getItem('vrp_redirect_url');
    sessionStorage.removeItem('vrp_redirect_url');

    if (redirectUrl && redirectUrl.includes('solution=')) {
        console.log('Redirecting to solution URL:', redirectUrl);
        sessionStorage.removeItem('vrp_redirect_url');
        window.location.href = redirectUrl;
        return;
    }

    if (redirectUrl && !redirectUrl.includes('login.html')) {
        console.log('Redirecting to:', redirectUrl);
        sessionStorage.removeItem('vrp_redirect_url');
        window.location.href = redirectUrl;
        return;
    }

    console.log('Redirecting to: index.html');
    window.location.href = 'index.html';
}

// ============================================
// EXPORT FUNCTIONS
// ============================================

window.login = login;
window.logout = logout;
window.isAuthenticated = isAuthenticated;
window.getAuthToken = getAuthToken;
window.getCurrentUser = getCurrentUser;
window.getCurrentBranch = getCurrentBranch;
window.saveSession = saveSession;
window.clearSession = clearSession;
window.requireAuth = requireAuth;
window.redirectAfterLogin = redirectAfterLogin;

console.log('Auth module loaded.');