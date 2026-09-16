# ---------- build ----------
FROM gradle:8.12-jdk21-alpine AS build
WORKDIR /workspace

# Copy only build metadata first so dependency resolution is cached independently
# of source changes.
COPY settings.gradle build.gradle gradle.properties ./
COPY backend/common/build.gradle          backend/common/
COPY backend/tenancy/build.gradle         backend/tenancy/
COPY backend/identity/build.gradle        backend/identity/
COPY backend/subscription/build.gradle    backend/subscription/
COPY backend/crm/build.gradle             backend/crm/
COPY backend/catalog/build.gradle         backend/catalog/
COPY backend/sales/build.gradle           backend/sales/
COPY backend/repairs/build.gradle         backend/repairs/
COPY backend/wholesale/build.gradle       backend/wholesale/
COPY backend/quotations/build.gradle      backend/quotations/
COPY backend/reporting/build.gradle       backend/reporting/
COPY backend/platform/build.gradle        backend/platform/
COPY backend/app/build.gradle             backend/app/
RUN gradle :backend:app:dependencies --no-daemon --quiet || true

COPY backend/ backend/
RUN gradle :backend:app:bootJar --no-daemon -x test

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache wget tzdata fontconfig ttf-dejavu \
    && addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/backend/app/build/libs/pos-saas-api.jar app.jar
RUN mkdir -p /app/tmp && chown -R app:app /app
USER app

# ZGC's baseline memory overhead doesn't fit well in small (e.g. 512Mi) free-tier
# containers; SerialGC with an explicit heap/metaspace cap leaves enough headroom
# for thread stacks, the JIT code cache and native buffers within that limit.
ENV JAVA_TOOL_OPTIONS="-Xmx288m -XX:MaxMetaspaceSize=150m -XX:CompressedClassSpaceSize=48m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/internal/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
