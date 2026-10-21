# Inside the existing TLS server. The reverse SSH listener is loopback only.
location ^~ /keyboard-spec/live/ {
    proxy_pass http://127.0.0.1:18562;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 180s;
    proxy_send_timeout 180s;
    proxy_buffering off;
    client_max_body_size 1k;
    add_header Content-Security-Policy "frame-ancestors 'self' https://xiamu-ssr.github.io" always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy no-referrer always;
    access_log off;
}
