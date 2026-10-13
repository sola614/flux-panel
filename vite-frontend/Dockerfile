# 多阶段构建 - 构建阶段
FROM node:20.19.0 AS builder

WORKDIR /app

# 使用 npm ci 严格依据 package-lock.json 安装依赖，保证构建结果可复现
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:stable-alpine AS production-stage

COPY nginx.conf /etc/nginx/nginx.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"] 