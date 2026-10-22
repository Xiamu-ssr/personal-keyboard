FROM node:24-bookworm-slim
WORKDIR /srv/cloud
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && mkdir -p /data/sync && chown node:node /data/sync
COPY --chown=node:node . .
ENV HOST=0.0.0.0 PORT=8080 NODE_ENV=production NODE_OPTIONS=--max-old-space-size=384 STORAGE_ROOT=/data/sync
USER node
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=4s CMD node -e "fetch('http://127.0.0.1:8080'+(process.env.PATH_PREFIX||'')+'/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.mjs"]
