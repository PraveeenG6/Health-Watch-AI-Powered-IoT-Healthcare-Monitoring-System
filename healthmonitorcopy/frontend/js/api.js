const API = {
    authHeader: localStorage.getItem('healthwatchAuth') || '',

    setCredentials(userId, password) {
        this.authHeader = `Basic ${btoa(`${userId}:${password}`)}`;
        localStorage.setItem('healthwatchAuth', this.authHeader);
    },

    clearAuth() {
        this.authHeader = '';
        localStorage.removeItem('healthwatchAuth');
    },

    async request(endpoint, options = {}) {
        if (!CONFIG.API_BASE_URL_CONFIGURED) {
            throw new Error('Backend URL is not configured. Set BACKEND_API_BASE_URL in js/config.js.');
        }

        const { noAuth, headers, ...fetchOptions } = options;
        const requestHeaders = {
            'Content-Type': 'application/json',
            ...(headers || {}),
        };

        if (!noAuth && this.authHeader) {
            requestHeaders.Authorization = this.authHeader;
        }

        let response;
        try {
            response = await fetch(CONFIG.API_BASE_URL + endpoint, {
                ...fetchOptions,
                headers: requestHeaders,
            });
        } catch (error) {
            console.error('Backend request failed', error);
            throw new Error(`Cannot connect to backend at ${CONFIG.API_BASE_URL}. Check the backend public URL, CORS, HTTPS, and network access.`);
        }

        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.message || `Request failed with status ${response.status}`);
        }

        if (response.status === 204) {
            return null;
        }

        return response.json();
    },

    get(endpoint) {
        return this.request(endpoint);
    },

    post(endpoint, body, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: 'POST',
            body: JSON.stringify(body),
        });
    },

    put(endpoint, body) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(body),
        });
    },

    async login(userId, password) {
        const user = await this.post(CONFIG.ENDPOINTS.AUTH_LOGIN, { userId, password }, { noAuth: true });
        this.setCredentials(userId, password);
        return user;
    },

    getPatients() {
        return this.get(CONFIG.ENDPOINTS.PATIENTS);
    },

    getPatientById(patientId) {
        return this.get(`${CONFIG.ENDPOINTS.PATIENTS}/${patientId}`);
    },

    getPatientDashboard(patientId) {
        return this.get(`${CONFIG.ENDPOINTS.DASHBOARD_PATIENT}/${patientId}`);
    },

    getDoctorOverview(doctorId) {
        return this.get(`${CONFIG.ENDPOINTS.DASHBOARD_DOCTOR}/${doctorId}/overview`);
    },

    getDoctorPatientView(doctorId, patientId) {
        return this.get(`${CONFIG.ENDPOINTS.DASHBOARD_DOCTOR}/${doctorId}/patient/${patientId}`);
    },

    getActiveAlerts() {
        return this.get(CONFIG.ENDPOINTS.ALERTS_ACTIVE);
    },

    acknowledgeAlert(alertId, note = '') {
        return this.put(`${CONFIG.ENDPOINTS.ALERT_ACKNOWLEDGE}/${alertId}/acknowledge`, { note });
    },

    triggerSos(patientId, message, triggeredBy) {
        return this.post(CONFIG.ENDPOINTS.SOS, { patientId, message, triggeredBy });
    },

    getAiAnalysis(heartRate, spo2, temp) {
        return this.get(`${CONFIG.ENDPOINTS.AI_ANALYZE}?heartRate=${heartRate}&spo2=${spo2}&temp=${temp}`);
    },

    saveConsultation(patientId, suggestions) {
        return this.post(CONFIG.ENDPOINTS.CONSULTATIONS, { patientId, suggestions });
    },

    getConsultations(patientId) {
        return this.get(`${CONFIG.ENDPOINTS.CONSULTATIONS}/patient/${patientId}`);
    },

    sendChatMessage(message) {
        return this.post(CONFIG.ENDPOINTS.CHATBOT, { message });
    },
};
