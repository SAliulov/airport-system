FROM nginx:alpine
COPY clients/web-dispatcher/web-dispatcher-app/dist /usr/share/nginx/dispatcher
COPY clients/web-board/web-board-app/dist /usr/share/nginx/board
COPY docker/nginx.conf /etc/nginx/nginx.conf