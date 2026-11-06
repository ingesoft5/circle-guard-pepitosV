# Runs the Playwright suite with Chromium only. The official Playwright image ships three
# browsers (about 3.5 GB) and the CI host disk is small.
FROM node:20.18.1-bookworm-slim

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    CI=true
WORKDIR /e2e

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund \
    && npx playwright install --with-deps --only-shell chromium \
    && rm -rf /var/lib/apt/lists/* /root/.npm

COPY playwright.config.ts ./
COPY tests ./tests
RUN chown -R node:node /e2e

USER node
CMD ["npx", "playwright", "test"]
