# Retired home-Mac demo endpoint. Keep the public route closed.
location ^~ /keyboard-spec/live/ {
    return 410;
    add_header Cache-Control "no-store" always;
}
