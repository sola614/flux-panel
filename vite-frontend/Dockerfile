# 多阶段构建 - 构建阶段
FROM node:20.19.0 AS builder

WORKDIR /app

# 说明：此处必须使用 npm install，不能用 npm ci。
#   在 Windows / macOS 上生成的 package-lock.json 会按当前平台裁剪 optionalDependencies
#   （例如 lightningcss、@tailwindcss/oxide 的原生二进制），npm ci 严格按 lockfile 安装，
#   在 Linux 容器里就会缺少 lightningcss.linux-x64-gnu.node 等文件，导致 vite build 失败。
#   npm install 仍会遵循 lockfile 中已锁定的版本号，同时按目标平台补装对应的原生依赖。
COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:stable-alpine AS production-stage

COPY nginx.conf /etc/nginx/nginx.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"] 