const PatientDashboard = {
    patientId: null,
    refreshTimer: null,
    charts: {},
    shownAlertIds: new Set(),
    eventsReady: false,

    async init() {
        const user = Auth.getUser();
        this.patientId = user.patientId;

        if (!this.eventsReady) {
            document.getElementById('chatbot-form').addEventListener('submit', event => this.sendChat(event));
            this.eventsReady = true;
        }

        await this.loadDashboard();

        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
        }
        this.refreshTimer = setInterval(() => this.loadDashboard(), CONFIG.REFRESH_INTERVAL);
    },

    async loadDashboard() {
        try {
            const data = await API.getPatientDashboard(this.patientId);
            const readings = data.recentReadings || [];
            const alerts = data.activeAlerts || [];

            this.renderSummary(data.summary);
            this.renderCharts(readings);
            this.renderReadings(readings);
            this.renderConsultations(data.consultations || []);
            this.updateStatusBanner(alerts);
            this.showNewAlertPopup(alerts);
        } catch (error) {
            Alerts.showToastMessage('danger', 'Dashboard Error', error.message);
        }
    },

    renderSummary(summary) {
        if (!summary) {
            return;
        }

        this.setText('p-heartrate', Math.round(summary.latestHeartRate));
        this.setText('p-spo2', Number(summary.latestSpo2).toFixed(1));
        this.setText('p-temp', Number(summary.latestTemperature).toFixed(1));
        this.setText('p-steps', Number(summary.totalSteps24h || 0).toLocaleString());

        this.setStatus('p-hr-status', summary.heartRateStatus);
        this.setStatus('p-spo2-status', summary.spo2Status);
        this.setStatus('p-temp-status', summary.tempStatus);

        this.setCardAlert('card-heartrate', summary.heartRateStatus);
        this.setCardAlert('card-spo2', summary.spo2Status);
        this.setCardAlert('card-temp', summary.tempStatus);

        this.setText('patient-last-updated', `Last updated: ${this.formatTime(summary.lastUpdated)}`);
    },

    renderCharts(readings) {
        if (!window.Chart) {
            return;
        }

        const ordered = [...readings].reverse();
        const labels = ordered.map(reading => this.formatShortTime(reading.recordedAt));

        this.drawLineChart('patient-heart-chart', labels, ordered.map(r => r.heartRate), 'Heart Rate', '#c93c3c');
        this.drawLineChart('patient-temp-chart', labels, ordered.map(r => r.temperature), 'Temperature', '#a96500');
        this.drawLineChart('patient-spo2-chart', labels, ordered.map(r => r.spo2), 'SpO2', '#2f6fed');
    },

    drawLineChart(canvasId, labels, values, label, color) {
        const canvas = document.getElementById(canvasId);
        if (!canvas) {
            return;
        }

        const data = {
            labels,
            datasets: [{
                label,
                data: values,
                borderColor: color,
                backgroundColor: `${color}22`,
                borderWidth: 2,
                tension: 0.35,
                fill: true,
                pointRadius: 3,
            }],
        };

        if (this.charts[canvasId]) {
            this.charts[canvasId].data = data;
            this.charts[canvasId].update();
            return;
        }

        this.charts[canvasId] = new Chart(canvas, {
            type: 'line',
            data,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { display: false } },
                    y: { beginAtZero: false },
                },
            },
        });
    },

    renderReadings(readings) {
        const tbody = document.getElementById('patient-readings-body');
        this.setText('p-reading-count', readings.length);

        if (!readings.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state">No readings yet. Send sensor data to the backend.</td></tr>';
            return;
        }

        tbody.innerHTML = readings.map(reading => `
            <tr>
                <td>${this.formatTime(reading.recordedAt)}</td>
                <td><strong>${Math.round(reading.heartRate)}</strong> BPM</td>
                <td><strong>${Number(reading.spo2).toFixed(1)}</strong>%</td>
                <td><strong>${Number(reading.temperature).toFixed(1)}</strong> C</td>
                <td>${Number(reading.stepCount || 0).toLocaleString()}</td>
                <td><span class="fall-badge ${reading.fallDetected ? 'yes' : 'no'}">${reading.fallDetected ? 'YES' : 'No'}</span></td>
                <td>${this.buildStatusTags(reading)}</td>
            </tr>
        `).join('');
    },

    renderConsultations(consultations) {
        const container = document.getElementById('patient-consultations');
        if (!consultations.length) {
            container.innerHTML = '<div class="empty-state">No consultations recorded.</div>';
            return;
        }

        container.innerHTML = consultations.map(item => `
            <div class="note-card">
                <div class="note-header">
                    <span class="note-doctor">${this.escapeHtml(item.doctorName || item.doctorId || 'Doctor')}</span>
                    <span class="note-type">${this.formatTime(item.consultationTime)}</span>
                </div>
                <div class="note-content">${this.escapeHtml(item.suggestions)}</div>
            </div>
        `).join('');
    },

    updateStatusBanner(alerts) {
        const banner = document.getElementById('patient-status-banner');
        const message = document.getElementById('patient-status-message');

        if (!alerts.length) {
            banner.className = 'status-banner';
            message.textContent = 'All vitals are within normal range';
            return;
        }

        const hasCritical = alerts.some(alert => alert.severity === 'CRITICAL');
        banner.className = `status-banner ${hasCritical ? 'danger' : 'warning'}`;
        message.textContent = `${alerts.length} active alert(s): ${alerts[0].message}`;
    },

    showNewAlertPopup(alerts) {
        const alert = alerts.find(item => !this.shownAlertIds.has(item.id));
        if (!alert) {
            return;
        }

        this.shownAlertIds.add(alert.id);
        Alerts.showPopup(alert);
        Alerts.showToast(alert);
    },

    async sendChat(event) {
        event.preventDefault();
        const input = document.getElementById('chatbot-message');
        const message = input.value.trim();
        if (!message) {
            return;
        }

        this.addChatMessage('user', message);
        input.value = '';

        try {
            const response = await API.sendChatMessage(message);
            this.addChatMessage('bot', response.reply);
        } catch (error) {
            this.addChatMessage('bot', error.message);
        }
    },

    addChatMessage(type, text) {
        const container = document.getElementById('chatbot-messages');
        const row = document.createElement('div');
        row.className = `chat-message ${type}`;
        row.textContent = text;
        container.appendChild(row);
        container.scrollTop = container.scrollHeight;
    },

    setStatus(elementId, status) {
        const element = document.getElementById(elementId);
        if (!element) {
            return;
        }

        element.textContent = status || '--';
        element.className = 'vital-status';
        if (status === 'NORMAL') {
            element.classList.add('normal');
        } else if (status === 'HIGH' || status === 'CAUTION') {
            element.classList.add('warning');
        } else if (status === 'LOW' || status === 'EMERGENCY') {
            element.classList.add('danger');
        }
    },

    setCardAlert(cardId, status) {
        const card = document.getElementById(cardId);
        if (card) {
            card.classList.toggle('alerting', status && status !== 'NORMAL');
        }
    },

    buildStatusTags(reading) {
        const tags = [];
        if (reading.heartRateStatus && reading.heartRateStatus !== 'NORMAL') {
            tags.push(`<span class="status-tag warning">HR ${reading.heartRateStatus}</span>`);
        }
        if (reading.spo2Status && reading.spo2Status !== 'NORMAL') {
            tags.push(`<span class="status-tag danger">SpO2 ${reading.spo2Status}</span>`);
        }
        if (reading.tempStatus && reading.tempStatus !== 'NORMAL') {
            tags.push(`<span class="status-tag warning">Temp ${reading.tempStatus}</span>`);
        }
        if (reading.fallAlert || reading.fallDetected) {
            tags.push('<span class="status-tag danger">FALL</span>');
        }
        if (reading.aiRiskLevel && reading.aiRiskLevel !== 'NORMAL') {
            const css = reading.aiRiskLevel === 'EMERGENCY' ? 'danger' : 'warning';
            tags.push(`<span class="status-tag ${css}">AI ${reading.aiRiskLevel}</span>`);
        }
        return tags.length ? tags.join(' ') : '<span class="status-tag normal">OK</span>';
    },

    setText(elementId, value) {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = value;
        }
    },

    formatTime(isoString) {
        if (!isoString) {
            return '--';
        }
        return new Date(isoString).toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
        });
    },

    formatShortTime(isoString) {
        if (!isoString) {
            return '--';
        }
        return new Date(isoString).toLocaleTimeString('en-IN', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
        });
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text || '';
        return div.innerHTML;
    },
};
