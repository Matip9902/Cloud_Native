FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG ANGULAR_CONFIGURATION=development
RUN npm run build -- --configuration "$ANGULAR_CONFIGURATION"

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/certs && \
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
      -keyout /etc/nginx/certs/frontend.key \
      -out /etc/nginx/certs/frontend.crt \
      -subj "/CN=cloud-native-frontend"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/angular-app-cloud/browser /usr/share/nginx/html
EXPOSE 80 443
