(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', async () => {
        setupStartScreen();
        setupAlertDismiss();
        await Auth.init();
    });

    function setupStartScreen() {
        const roleButtons = document.querySelectorAll('.role-btn');
        roleButtons.forEach(button => {
            button.addEventListener('click', () => {
                roleButtons.forEach(item => item.classList.remove('active'));
                button.classList.add('active');
                Auth.fillDemoCredentials(button.dataset.role);
            });
        });

        document.getElementById('login-form').addEventListener('submit', async event => {
            event.preventDefault();
            await Auth.login();
        });

        document.getElementById('patient-logout-btn').addEventListener('click', () => Auth.logout());
        document.getElementById('doctor-logout-btn').addEventListener('click', () => Auth.logout());

        document.getElementById('patient-sos-btn').addEventListener('click', async () => {
            const user = Auth.getUser();
            const patientId = user?.patientId;

            if (!patientId || !confirm('Send an SOS emergency alert?')) {
                return;
            }

            try {
                await API.triggerSos(patientId, 'Patient-triggered SOS emergency', 'PATIENT');
                Alerts.showToastMessage('danger', 'SOS Sent', 'Emergency alert has been saved.');
                await PatientDashboard.loadDashboard();
            } catch (error) {
                Alerts.showToastMessage('danger', 'SOS Failed', error.message);
            }
        });
    }

    function setupAlertDismiss() {
        document.getElementById('alert-popup-dismiss').addEventListener('click', () => {
            Alerts.dismissPopup();
        });

        document.getElementById('alert-overlay').addEventListener('click', event => {
            if (event.target === event.currentTarget) {
                Alerts.dismissPopup();
            }
        });

        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                Alerts.dismissPopup();
            }
        });
    }

})();
