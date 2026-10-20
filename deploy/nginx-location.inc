# Personal Keyboard static Spec; no device or application backend.
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
location ^~ /keyboard-spec/ { return 404; }
