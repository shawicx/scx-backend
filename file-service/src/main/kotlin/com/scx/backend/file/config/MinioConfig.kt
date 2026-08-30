package com.scx.backend.file.config

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @description MinIO 客户端配置
 *
 * 提供两个客户端 Bean：
 * - minioClient：服务内部调用（endpoint 为服务可达地址，如容器内 http://minio:9000）
 * - minioUrlClient：生成对外访问 URL（endpoint 为浏览器可达地址 MINIO_PUBLIC_ENDPOINT，
 *   未配置时复用 minioClient），避免容器内部地址生成的预签名 URL 浏览器无法访问
 */
@Configuration
class MinioConfig {

    /**
     * @description 服务内部调用的 MinIO 客户端
     * @param endpoint MinIO S3 API 地址
     * @param accessKey 访问密钥
     * @param secretKey 私有密钥
     * @returns MinioClient
     */
    @Bean
    fun minioClient(
        @Value("\${minio.endpoint}") endpoint: String,
        @Value("\${minio.access-key}") accessKey: String,
        @Value("\${minio.secret-key}") secretKey: String,
    ): MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    /**
     * @description 生成对外 URL 的 MinIO 客户端
     *
     * 预签名 URL 使用客户端自身 endpoint 参与签名，因此浏览器可达地址需要独立的客户端。
     *
     * @param publicEndpoint 浏览器可达的 MinIO 地址，为空时复用 minioClient
     * @param accessKey 访问密钥
     * @param secretKey 私有密钥
     * @param minioClient 服务内部客户端（publicEndpoint 为空时直接返回）
     * @returns MinioClient
     */
    @Bean
    fun minioUrlClient(
        @Value("\${minio.public-endpoint:}") publicEndpoint: String,
        @Value("\${minio.access-key}") accessKey: String,
        @Value("\${minio.secret-key}") secretKey: String,
        @Qualifier("minioClient") minioClient: MinioClient,
    ): MinioClient =
        if (publicEndpoint.isBlank()) {
            minioClient
        } else {
            MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build()
        }
}
