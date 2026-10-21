FROM node:22-alpine
WORKDIR /srv/cloud
COPY --chown=node:node . .
ENV HOST=0.0.0.0 PORT=8080 NODE_ENV=production
USER node
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD node -e "fetch('http://127.0.0.1:8080/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.mjs"]
