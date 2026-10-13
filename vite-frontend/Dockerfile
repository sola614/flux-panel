# 多阶段构建 - 构建阶段
FROM node:20.19.0 AS builder

WORKDIR /app

# ⚠️ package-lock.json 必须包含「所有平台」的原生 optional 依赖。
#   若在 Windows / macOS 上生成 lockfile，npm 会按当前平台裁剪掉其他平台的变体
#   （例如 lightningcss-linux-x64-gnu、@tailwindcss/oxide-linux-x64-gnu），
#   容器内 vite build 就会报 "Cannot find module './lightningcss.linux-x64-gnu.node'"。
#   重新生成 lockfile 时，请在 Linux 上、或在一个「没有 node_modules 的空目录」里执行：
#       mkdir /tmp/lockgen && cp package.json /tmp/lockgen/
#       cd /tmp/lockgen && npm install --package-lock-only
#   再把它拷回 vite-frontend/package-lock.json。当前 lockfile 已补齐全部平台变体。
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