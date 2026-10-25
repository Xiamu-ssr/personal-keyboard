location = /keyboard-api { return 308 /keyboard-api/; }
location /keyboard-api/ {
    client_max_body_size 8m;
    proxy_pass http://127.0.0.1:8089;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $keyboard_cloud_upgrade;
    proxy_request_buffering off;
    proxy_buffering off;
    proxy_read_timeout 120s;
    proxy_send_timeout 120s;
}
