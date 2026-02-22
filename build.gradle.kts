import com.google.protobuf.gradle.*

plugins {
	java
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.4"
	jacoco
}

group = "com.devoops"
version = "0.0.1-SNAPSHOT"
description = "Rating service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

val grpcVersion = "1.68.0"

dependencies {
	// Web and Core
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

	// MongoDB
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// MapStruct
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

	// implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    //zipkin(tracing)
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

	// RabbitMQ
	implementation("org.springframework.boot:spring-boot-starter-amqp")

	// gRPC Client
	implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
	implementation("io.grpc:grpc-protobuf:$grpcVersion")
	implementation("io.grpc:grpc-stub:$grpcVersion")
	implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
	compileOnly("javax.annotation:javax.annotation-api:1.3.2")

	// testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.testcontainers:junit-jupiter:1.20.4")
	testImplementation("org.testcontainers:mongodb:1.20.4")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:3.25.5"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				id("grpc")
			}
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports { xml.required = true }
}
