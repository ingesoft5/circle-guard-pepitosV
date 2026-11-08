# Jenkins de CircleGuard: imagen oficial + Docker CLI y Buildx (HU-07).
# No incluye un daemon: Jenkins usa el Docker del host CI a través de
# /var/run/docker.sock (montado en infra/compose/ci/docker-compose.yml).
FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
 && chmod a+r /etc/apt/keyrings/docker.gpg \
 && . /etc/os-release \
 && printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian %s stable\n' \
      "$(dpkg --print-architecture)" "${VERSION_CODENAME}" > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

USER jenkins

# Plugins baked into the image, so casc.yaml can use a newly added plugin on the
# first start. bootstrap-jenkins.sh still runs jenkins-plugin-cli to update them.
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
