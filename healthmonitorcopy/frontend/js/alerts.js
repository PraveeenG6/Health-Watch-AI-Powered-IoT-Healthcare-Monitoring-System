const Alerts = {
    showPopup(alert) {
        const overlay = document.getElementById('alert-overlay');
        const title = document.getElementById('alert-popup-title');
        const message = document.getElementById('alert-popup-message');
        const details = document.getElementById('alert-popup-details');
        const info = this.getAlertInfo(alert.alertType);

        document.getElementById('alert-popup-icon').textContent = info.icon;
        title.textContent = info.title;
        message.textContent = alert.message || info.defaultMessage;
        details.innerHTML = `
            <div class="detail-row"><span class="label">Type</span><span class="value">${alert.alertType || '--'}</span></div>
            <div class="detail-row"><span class="label">Severity</span><span class="value">${alert.severity || '--'}</span></div>
            <div class="detail-row"><span class="label">Time</span><span class="value">${this.formatTime(alert.triggeredAt)}</span></div>
            <div class="detail-row"><span class="label">Patient ID</span><span class="value">${alert.patientId || '--'}</span></div>
        `;

        overlay.classList.remove('hidden');
    },

    dismissPopup() {
        document.getElementById('alert-overlay').classList.add('hidden');
    },

    showToast(alert) {
        const info = this.getAlertInfo(alert.alertType);
        const type = alert.severity === 'CRITICAL' ? 'danger' : 'warning';
        this.showToastMessage(type, info.title, alert.message || info.defaultMessage);
    },

    showToastMessage(type, title, message) {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `
            <span class="toast-icon">${type === 'success' ? 'OK' : '!'}</span>
            <div class="toast-body">
                <div class="toast-title">${title}</div>
                <div class="toast-message">${message}</div>
            </div>
        `;
        container.appendChild(toast);

        setTimeout(() => {
            if (toast.parentNode) {
                toast.remove();
            }
        }, 5000);
    },

    getAlertInfo(alertType) {
        const info = {
            FALL: { icon: '!', title: 'Fall Detected', defaultMessage: 'A fall has been detected.' },
            HEART_RATE_HIGH: { icon: 'HR', title: 'High Heart Rate', defaultMessage: 'Heart rate is above the safe limit.' },
            HEART_RATE_LOW: { icon: 'HR', title: 'Low Heart Rate', defaultMessage: 'Heart rate is below the safe limit.' },
            SPO2_LOW: { icon: 'O2', title: 'Low SpO2', defaultMessage: 'Oxygen level is below the safe limit.' },
            TEMP_HIGH: { icon: 'T', title: 'High Temperature', defaultMessage: 'Temperature is above the safe limit.' },
            TEMP_LOW: { icon: 'T', title: 'Low Temperature', defaultMessage: 'Temperature is below the safe limit.' },
            AI_RISK: { icon: 'AI', title: 'AI Risk Alert', defaultMessage: 'The AI model detected a health risk.' },
            SOS: { icon: 'SOS', title: 'SOS Emergency', defaultMessage: 'An SOS alert has been triggered.' },
        };
        return info[alertType] || { icon: '!', title: 'Health Alert', defaultMessage: 'A health alert has been triggered.' };
    },

    formatTime(isoString) {
        if (!isoString) {
            return 'Just now';
        }
        return new Date(isoString).toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
        });
    },
};
