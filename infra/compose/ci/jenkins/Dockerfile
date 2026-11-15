# Jenkins de CircleGuard: imagen oficial + Docker CLI y Buildx (HU-07), y kubectl, Helm y
# Terraform para la Parte 2 (despliegue en k3s e IaC), con versiones fijas y checksum verificado.
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

ARG KUBECTL_VERSION=v1.30.6
ARG HELM_VERSION=v3.16.2
ARG TERRAFORM_VERSION=1.9.8

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends unzip; \
    rm -rf /var/lib/apt/lists/*; \
    cd /tmp; \
    curl -fsSLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"; \
    curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl.sha256" -o kubectl.sha256; \
    echo "$(cat kubectl.sha256)  kubectl" | sha256sum -c -; \
    install -m 0755 kubectl /usr/local/bin/kubectl; \
    curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz"; \
    curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz.sha256sum"; \
    sha256sum -c "helm-${HELM_VERSION}-linux-amd64.tar.gz.sha256sum"; \
    tar xzf "helm-${HELM_VERSION}-linux-amd64.tar.gz" linux-amd64/helm; \
    install -m 0755 linux-amd64/helm /usr/local/bin/helm; \
    curl -fsSLO "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip"; \
    curl -fsSLO "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_SHA256SUMS"; \
    grep "terraform_${TERRAFORM_VERSION}_linux_amd64.zip" "terraform_${TERRAFORM_VERSION}_SHA256SUMS" | sha256sum -c -; \
    unzip -q "terraform_${TERRAFORM_VERSION}_linux_amd64.zip" terraform; \
    install -m 0755 terraform /usr/local/bin/terraform; \
    rm -rf /tmp/*

USER jenkins

# Plugins baked into the image, so casc.yaml can use a newly added plugin on the
# first start. bootstrap-jenkins.sh still runs jenkins-plugin-cli to update them.
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
