/**
 * 인증 관련 유틸리티 함수들
 * 모든 페이지에서 재사용 가능한 토큰 관리 및 API 호출 함수들
 */

const Auth = {
    /**
     * localStorage에서 access token을 가져옵니다
     * @returns {string|null} access token 또는 null
     */
    getAccessToken() {
        return localStorage.getItem("access_token");
    },

    /**
     * localStorage에서 refresh token을 가져옵니다
     * @returns {string|null} refresh token 또는 null
     */
    getRefreshToken() {
        return localStorage.getItem("refresh_token");
    },

    /**
     * 토큰들을 localStorage에 저장합니다
     * @param {string} accessToken - access token
     * @param {string} refreshToken - refresh token
     */
    saveTokens(accessToken, refreshToken) {
        localStorage.setItem("access_token", accessToken);
        if (refreshToken) {
            localStorage.setItem("refresh_token", refreshToken);
        }
    },

    /**
     * 저장된 토큰들을 모두 제거합니다
     */
    clearTokens() {
        localStorage.removeItem("access_token");
        localStorage.removeItem("refresh_token");
    },

    /**
     * 인증이 필요한 API 요청을 위한 헤더를 생성합니다
     * @param {Object} additionalHeaders - 추가 헤더들
     * @returns {Object} Authorization 헤더가 포함된 헤더 객체
     */
    createAuthHeaders(additionalHeaders = {}) {
        const token = this.getAccessToken();
        const headers = {
            ...additionalHeaders
        };

        if (token) {
            headers.Authorization = `Bearer ${token}`;
        }

        return headers;
    },

    /**
     * 인증이 필요한 fetch 요청을 수행합니다
     * @param {string} url - 요청 URL
     * @param {Object} options - fetch 옵션
     * @returns {Promise<Response>} fetch 응답
     */
    async authenticatedFetch(url, options = {}) {
        const headers = this.createAuthHeaders(options.headers);

        const response = await fetch(url, {
            ...options,
            headers
        });

        // 401 또는 403 응답시 토큰 제거 및 리다이렉트
        if (response.status === 401 || response.status === 403) {
            this.clearTokens();
            this.redirectToAuth();
            throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.');
        }

        return response;
    },

    /**
     * 인증이 필요한 JSON API 요청을 수행합니다
     * @param {string} url - 요청 URL
     * @param {Object} options - fetch 옵션
     * @returns {Promise<Object>} JSON 응답
     */
    async authenticatedFetchJson(url, options = {}) {
        const headers = this.createAuthHeaders({
            'Content-Type': 'application/json',
            ...options.headers
        });

        const response = await this.authenticatedFetch(url, {
            ...options,
            headers
        });

        if (!response.ok) {
            throw new Error(`API 요청 실패: ${response.status} ${response.statusText}`);
        }

        return await response.json();
    },

    /**
     * POST 요청으로 JSON 데이터를 전송합니다
     * @param {string} url - 요청 URL
     * @param {Object} data - 전송할 데이터
     * @param {Object} options - 추가 fetch 옵션
     * @returns {Promise<Object>} JSON 응답
     */
    async post(url, data = null, options = {}) {
        return this.authenticatedFetchJson(url, {
            method: 'POST',
            body: data ? JSON.stringify(data) : null,
            ...options
        });
    },

    /**
     * PUT 요청으로 JSON 데이터를 전송합니다
     * @param {string} url - 요청 URL
     * @param {Object} data - 전송할 데이터
     * @param {Object} options - 추가 fetch 옵션
     * @returns {Promise<Object>} JSON 응답
     */
    async put(url, data = null, options = {}) {
        return this.authenticatedFetchJson(url, {
            method: 'PUT',
            body: data ? JSON.stringify(data) : null,
            ...options
        });
    },

    /**
     * DELETE 요청을 전송합니다
     * @param {string} url - 요청 URL
     * @param {Object} options - 추가 fetch 옵션
     * @returns {Promise<Object>} JSON 응답
     */
    async delete(url, options = {}) {
        return this.authenticatedFetchJson(url, {
            method: 'DELETE',
            ...options
        });
    },

    /**
     * GET 요청을 전송합니다
     * @param {string} url - 요청 URL
     * @param {Object} options - 추가 fetch 옵션
     * @returns {Promise<Object>} JSON 응답
     */
    async get(url, options = {}) {
        return this.authenticatedFetchJson(url, options);
    },

    /**
     * 토큰 유효성을 확인합니다
     * @returns {boolean} 토큰이 존재하는지 여부
     */
    hasValidToken() {
        return !!this.getAccessToken();
    },

    /**
     * 인증 페이지로 리다이렉트합니다
     */
    redirectToAuth() {
        const currentUrl = window.location.href;
        const params = new URLSearchParams({
            response_type: "code",
            client_id: "oauth2-client",
            redirect_uri: currentUrl,
            scope: "openid profile read write",
            state: crypto.randomUUID()
        });

        window.location.href = `http://localhost:9000/oauth2/authorize?${params.toString()}`;
    },

    /**
     * 로그아웃을 수행합니다
     */
    logout() {
        this.clearTokens();
        window.location.href = "http://localhost:9000/logout";
    },

    /**
     * 권한 확인을 수행합니다
     * @param {string} endpoint - 권한 확인 API 엔드포인트
     * @returns {Promise<Object>} 권한 정보
     */
    async checkPermission(endpoint = '/admin/api/check-permission') {
        if (!this.hasValidToken()) {
            throw new Error('토큰이 없습니다.');
        }

        return this.get(endpoint);
    },

    /**
     * 페이지 로드시 토큰 확인 및 권한 검증을 수행합니다
     * @param {string} permissionEndpoint - 권한 확인 엔드포인트
     * @param {string} redirectUrl - 권한이 없을 때 이동할 URL
     * @returns {Promise<Object>} 권한 정보
     */
    async initPageAuth(permissionEndpoint = '/admin/api/check-permission', redirectUrl = '/admin/home') {
        try {
            if (!this.hasValidToken()) {
                window.location.href = `${redirectUrl}?error=no_token`;
                return;
            }

            const permissionData = await this.checkPermission(permissionEndpoint);

            if (permissionData && !permissionData.hasPermission) {
                alert('❌ 접근 권한이 없습니다. 관리자에게 문의하세요.');
                window.location.href = redirectUrl;
                return;
            }

            return permissionData;

        } catch (error) {
            console.error('페이지 권한 확인 중 오류:', error);
            window.location.href = `${redirectUrl}?error=permission_check_failed`;
            throw error;
        }
    }
};

// 전역 객체로 노출
window.AuthUtils = Auth;