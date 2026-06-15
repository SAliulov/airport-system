FROM nginx:alpine
COPY clients/web-dispatcher/web-dispatcher-app/dist /usr/share/nginx/dispatcher
COPY clients/web-board/web-board-app/dist /usr/share/nginx/board
COPY certs/fullchain.cer /etc/nginx/ssl/fullchain.cer
COPY certs/45.133.74.67.key /etc/nginx/ssl/45.133.74.67.key
COPY docker/nginx.conf /etc/nginx/nginx.conf