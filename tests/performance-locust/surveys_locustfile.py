"""Survey load and the stress ramp. Pass the stage host with --host.

    locust -f tests/performance-locust/surveys_locustfile.py --host http://STAGE
"""

import uuid

from locust import HttpUser, LoadTestShape, between, task


class SurveyUser(HttpUser):
    wait_time = between(0.5, 1.5)

    @task
    def submit_survey(self):
        self.client.post(
            "/api/v1/surveys",
            json={
                "anonymousId": str(uuid.uuid4()),
                "hasFever": False,
                "hasCough": False,
            },
            name="/api/v1/surveys",
        )


class SurveyStressShape(LoadTestShape):
    """Each pair is the moment the ramp reaches that many users."""

    stages = (
        (60, 10, 2),
        (120, 30, 5),
        (180, 60, 10),
        (240, 120, 10),
    )

    def tick(self):
        elapsed = self.get_run_time()
        for duration, users, spawn_rate in self.stages:
            if elapsed < duration:
                return users, spawn_rate
        return None
