FROM node:24-alpine AS build

WORKDIR /app
COPY package.json pnpm-lock.yaml* ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY index.html tsconfig.json tsconfig.node.json vite.config.ts ./
COPY src ./src
RUN pnpm build

FROM nginx:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
