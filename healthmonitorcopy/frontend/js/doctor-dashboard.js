const DoctorDashboard = {
    doctorId: null,
    selectedPatientId: null,
    selectedPatient: null,
    refreshTimer: null,
    charts: {},
    shownAlertIds: new Set(),
    eventsReady: false,

    async init() {
        const user = Auth.getUser();
        this.doctorId = user.userId || CONFIG.DEMO_DOCTOR_ID;

        if (!this.eventsReady) {
            document.getElementById('back-to-overview').addEventListener('click', () => this.showOverview());
            document.getElementById('refresh-patients-btn').addEventListener('click', () => this.loadOverview());
            document.getElementById('request-ai-btn').addEventListener('click', () => this.requestAiAnalysis());
            document.getElementById('doctor-sos-btn').addEventListener('click', () => this.triggerSos());
            document.getElementById('consultation-form').addEventListener('submit', event => this.saveConsultation(event));
            this.eventsReady = true;
        }

        await this.loadOverview();

        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
        }
        this.refreshTimer = setInterval(() => {
            if (this.selectedPatientId) {
                this.loadPatientDetail(this.selectedPatientId);
            } else {
                this.loadOverview();
            }
        }, CONFIG.REFRESH_INTERVAL);
    },

    async loadOverview() {
        try {
            const overviewData = await API.getDoctorOverview(this.doctorId);
            this.renderPatientCards(overviewData);

            const alerts = await API.getActiveAlerts();
            this.renderActiveAlerts(alerts);
            this.setText('doctor-alert-count', alerts.length);
            this.setText('d-active-alert-count', alerts.length);
            this.showNewAlertPopup(alerts);
        } catch (error) {
            Alerts.showToastMessage('danger', 'Dashboard Error', error.message);
        }
    },

    showOverview() {
        this.selectedPatientId = null;
        this.selectedPatient = null;
        document.getElementById('doctor-overview').classList.add('active');
        document.getElementById('doctor-patient-detail').classList.remove('active');
        this.loadOverview();
    },

    async selectPatient(patientId) {
        this.selectedPatientId = patientId;
        document.getElementById('doctor-overview').classList.remove('active');
        document.getElementById('doctor-patient-detail').classList.add('active');
        await this.loadPatientDetail(patientId);
    },

    async loadPatientDetail(patientId) {
        try {
            const [data, patient] = await Promise.all([
                API.getDoctorPatientView(this.doctorId, patientId),
                API.getPatientById(patientId),
            ]);

            this.selectedPatient = patient;
            this.renderPatientHeader(data.summary, patient);
            this.renderDetailVitals(data.summary);
            this.renderCharts(data.readings || []);
            this.renderDetailReadings(data.readings || []);
            this.renderAlertHistory(data.alertHistory || []);
            this.renderConsultations(data.consultations || []);
            this.renderActiveAlerts(data.activeAlerts || []);
            this.setText('d-active-alert-count', (data.activeAlerts || []).length);
            this.showNewAlertPopup(data.activeAlerts || []);
        } catch (error) {
            Alerts.showToastMessage('danger', 'Patient Error', error.message);
        }
    },

    renderPatientCards(overviewData) {
        const grid = document.getElementById('doctor-patients-grid');
        if (!overviewData.length) {
            grid.innerHTML = '<div class="empty-state">No patients found.</div>';
            return;
        }

        grid.innerHTML = overviewData.map(entry => {
            const patient = entry.patient;
            const reading = entry.latestReading;
            const alertCount = entry.activeAlerts || 0;
            const aiRisk = entry.summary?.aiRiskLevel || reading?.aiRiskLevel || 'UNKNOWN';

            return `
                <div class="patient-card ${alertCount ? 'critical' : ''}" onclick="DoctorDashboard.selectPatient('${patient.id}')">
                    <div class="patient-card-header">
                        <div class="patient-card-info">
                            <div class="patient-card-avatar">${this.initial(patient.name)}</div>
                            <div>
                                <div class="patient-card-name">${this.escapeHtml(patient.name || 'Unknown')}</div>
                                <div class="patient-card-id">${this.escapeHtml(patient.deviceId || 'No device linked')}</div>
                            </div>
                        </div>
                        <span class="patient-card-status ${alertCount ? 'critical' : 'active'}">${alertCount ? 'ALERT' : (patient.status || 'ACTIVE')}</span>
                    </div>
                    <div class="patient-card-vitals">
                        <div class="patient-vital-mini"><div class="label">HR</div><div class="value">${reading ? Math.round(reading.heartRate) : '--'}</div></div>
                        <div class="patient-vital-mini"><div class="label">SpO2</div><div class="value">${reading ? Number(reading.spo2).toFixed(0) : '--'}</div></div>
                        <div class="patient-vital-mini"><div class="label">Temp</div><div class="value">${reading ? Number(reading.temperature).toFixed(1) : '--'}</div></div>
                    </div>
                    <div class="patient-card-footer">
                        <span>AI: ${aiRisk}</span>
                        <span class="patient-card-arrow">View</span>
                    </div>
                </div>
            `;
        }).join('');
    },

    renderPatientHeader(summary, patient) {
        const name = patient?.name || summary?.patientName || 'Unknown Patient';
        this.setText('detail-patient-name', name);
        this.setText('detail-patient-avatar', this.initial(name));
        this.setText('detail-patient-meta',
            `Status: ${patient?.status || 'ACTIVE'} | Blood: ${patient?.bloodType || '--'} | Device: ${patient?.deviceId || '--'}`);
        this.setText('detail-patient-phone', patient?.phone || '--');
        this.setText('detail-patient-email', patient?.email || '--');

        const statusBadge = document.getElementById('detail-patient-status');
        const isCritical = Number(summary?.activeAlerts || 0) > 0;
        statusBadge.textContent = isCritical ? 'ALERT' : (patient?.status || 'ACTIVE');
        statusBadge.className = `detail-status-badge ${isCritical ? 'critical' : ''}`;
    },

    renderDetailVitals(summary) {
        if (!summary) {
            return;
        }

        this.setText('d-heartrate', Math.round(summary.latestHeartRate));
        this.setText('d-spo2', Number(summary.latestSpo2).toFixed(1));
        this.setText('d-temp', Number(summary.latestTemperature).toFixed(1));
        this.setText('d-steps', Number(summary.totalSteps24h || 0).toLocaleString());

        this.setStatus('d-hr-status', summary.heartRateStatus);
        this.setStatus('d-spo2-status', summary.spo2Status);
        this.setStatus('d-temp-status', summary.tempStatus);

        if (summary.aiRiskLevel && summary.aiRiskLevel !== 'UNKNOWN') {
            document.getElementById('ai-analysis-content').innerHTML = this.aiResultHtml({
                riskLevel: summary.aiRiskLevel,
                confidence: summary.aiRiskConfidence,
                message: `Latest stored DL4J result: ${summary.aiRiskLevel}`,
            });
        }
    },

    renderCharts(readings) {
        if (!window.Chart) {
            return;
        }

        const ordered = [...readings].reverse();
        const labels = ordered.map(reading => this.formatShortTime(reading.recordedAt));

        this.drawLineChart('doctor-heart-chart', labels, ordered.map(r => r.heartRate), 'Heart Rate', '#c93c3c');
        this.drawLineChart('doctor-temp-chart', labels, ordered.map(r => r.temperature), 'Temperature', '#a96500');
        this.drawLineChart('doctor-spo2-chart', labels, ordered.map(r => r.spo2), 'SpO2', '#2f6fed');
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

    renderDetailReadings(readings) {
        const tbody = document.getElementById('doctor-readings-body');
        this.setText('d-reading-count', readings.length);

        if (!readings.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state">No readings available.</td></tr>';
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

    renderActiveAlerts(alerts) {
        const container = document.getElementById('doctor-active-alerts');
        if (!alerts.length) {
            container.innerHTML = '<div class="empty-state">No active alerts. All patients are stable.</div>';
            return;
        }

        container.innerHTML = alerts.map(alert => this.alertHtml(alert, true)).join('');
    },

    renderAlertHistory(alerts) {
        const container = document.getElementById('doctor-alert-history');
        if (!alerts.length) {
            container.innerHTML = '<div class="empty-state">No alert history for this patient.</div>';
            return;
        }

        container.innerHTML = alerts.map(alert => this.alertHtml(alert, alert.active)).join('');
    },

    renderConsultations(consultations) {
        const container = document.getElementById('doctor-consultations');
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

    async saveConsultation(event) {
        event.preventDefault();
        if (!this.selectedPatientId) {
            return;
        }

        const textarea = document.getElementById('consultation-suggestions');
        const suggestions = textarea.value.trim();
        if (!suggestions) {
            return;
        }

        try {
            await API.saveConsultation(this.selectedPatientId, suggestions);
            textarea.value = '';
            Alerts.showToastMessage('success', 'Saved', 'Consultation saved.');
            await this.loadPatientDetail(this.selectedPatientId);
        } catch (error) {
            Alerts.showToastMessage('danger', 'Save Failed', error.message);
        }
    },

    async requestAiAnalysis() {
        const content = document.getElementById('ai-analysis-content');
        content.innerHTML = '<p class="ai-loading">Running simple DL4J analysis...</p>';

        try {
            const heartRate = parseFloat(document.getElementById('d-heartrate').textContent) || 0;
            const spo2 = parseFloat(document.getElementById('d-spo2').textContent) || 0;
            const temp = parseFloat(document.getElementById('d-temp').textContent) || 0;
            const result = await API.getAiAnalysis(heartRate, spo2, temp);
            content.innerHTML = this.aiResultHtml(result);
        } catch (error) {
            content.innerHTML = `<p style="color: var(--warning);">DL4J analysis unavailable: ${this.escapeHtml(error.message)}</p>`;
        }
    },

    async acknowledgeAlert(alertId) {
        try {
            await API.acknowledgeAlert(alertId, 'Acknowledged from doctor dashboard');
            Alerts.showToastMessage('success', 'Alert Acknowledged', 'The alert has been marked inactive.');

            if (this.selectedPatientId) {
                await this.loadPatientDetail(this.selectedPatientId);
            } else {
                await this.loadOverview();
            }
        } catch (error) {
            Alerts.showToastMessage('danger', 'Error', error.message);
        }
    },

    async triggerSos() {
        if (!this.selectedPatientId) {
            return;
        }

        if (!confirm('Send an SOS emergency alert for this patient?')) {
            return;
        }

        try {
            await API.triggerSos(this.selectedPatientId, 'Doctor-triggered SOS emergency', 'DOCTOR');
            Alerts.showToastMessage('danger', 'SOS Sent', 'Emergency alert has been saved.');
            await this.loadPatientDetail(this.selectedPatientId);
        } catch (error) {
            Alerts.showToastMessage('danger', 'SOS Failed', error.message);
        }
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

    alertHtml(alert, showAction) {
        return `
            <div class="alert-item ${alert.severity === 'CRITICAL' ? 'critical' : 'warning'}">
                <div class="alert-item-icon">${this.escapeHtml(alert.alertType || '!')}</div>
                <div class="alert-item-content">
                    <div class="alert-item-title">${this.escapeHtml(alert.message)}</div>
                    <div class="alert-item-meta">
                        ${this.escapeHtml(alert.alertType)} | ${this.escapeHtml(alert.severity)} | ${this.formatTime(alert.triggeredAt)}
                        ${alert.doctorNote ? ` | Note: ${this.escapeHtml(alert.doctorNote)}` : ''}
                    </div>
                </div>
                ${showAction ? `<div class="alert-item-actions"><button class="acknowledge-btn" onclick="DoctorDashboard.acknowledgeAlert('${alert.id}')">Acknowledge</button></div>` : ''}
            </div>
        `;
    },

    aiResultHtml(result) {
        const confidence = Math.round(Number(result.confidence || 0) * 100);
        return `
            <p><strong>Risk Level:</strong> ${this.escapeHtml(result.riskLevel)}</p>
            <p><strong>Confidence:</strong> ${confidence}%</p>
            <p>${this.escapeHtml(result.message || 'DL4J analysis completed.')}</p>
        `;
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

    initial(name) {
        return (name || '?').charAt(0).toUpperCase();
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
