# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# heif-convert (libheif-tools): tạo bản xem trước JPEG cho ảnh HEIC/HEIF của iPhone.
# Không có gói này thì upload HEIC vẫn chạy, chỉ thiếu ảnh xem trước.
RUN apk add --no-cache libheif-tools || echo "libheif-tools not available - HEIC preview disabled"
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8310
ENTRYPOINT ["java", "-jar", "app.jar"]
