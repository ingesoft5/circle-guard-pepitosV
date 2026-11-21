"""Check-in load. Pass the ingress with --host; this file does not set one.

    locust -f tests/performance-locust/access_locustfile.py --host http://INGRESS
"""

from locust import HttpUser, between, task


class GateCheckInUser(HttpUser):
    wait_time = between(0.2, 0.8)

    @task
    def validate_qr(self):
        self.client.post(
            "/svc/gateway/api/v1/gate/validate",
            json={"token": "load-test-token"},
            name="/api/v1/gate/validate",
        )
