# Include inside the existing HTTPS server. Static resources only.
location ^~ /keyboard-spec/live/ {
    add_header Cache-Control "no-store" always;
    return 410;
}
location = /keyboard-spec { return 308 /keyboard-spec/; }
location = /keyboard-spec/index.html { return 308 /keyboard-spec/; }

location = /keyboard-spec/ {
    root /var/www/personal-keyboard-spec/current;
    try_files /index.html =404;
    default_type text/html;
    charset utf-8;
    add_header Cache-Control "no-cache" always;
    add_header X-Robots-Tag "noindex, nofollow, noarchive" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer" always;
}

location = /keyboard-spec/demo/doudou-keyboard.apk {
    alias /var/www/personal-keyboard-spec/current/demo/doudou-keyboard.apk;
    default_type application/vnd.android.package-archive;
    limit_except GET HEAD { deny all; }
    add_header Cache-Control "no-cache" always;
    add_header X-Robots-Tag "noindex, nofollow, noarchive" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer" always;
    add_header Content-Disposition 'attachment; filename="doudou-keyboard.apk"' always;
}

# Named capture is constrained to the release allowlist, never an arbitrary path.
location ~ ^/keyboard-spec/demo/(?<keyboard_spec_media>artifact\.json|(?:home|typing|tools|voice|memory|people|advisor|palette|palette-dark|translation|recorder|emoji|credits|pricing)\.png|(?:keyboard-tour|advisor-tour)\.mp4)$ {
    alias /var/www/personal-keyboard-spec/current/demo/$keyboard_spec_media;
    types {
        application/json json;
        image/png png;
        video/mp4 mp4;
    }
    default_type application/octet-stream;
    limit_except GET HEAD { deny all; }
    add_header Cache-Control "no-cache" always;
    add_header X-Robots-Tag "noindex, nofollow, noarchive" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer" always;
}

location /keyboard-spec/ { return 404; }
