package com.scx.backend.file.storage

import com.scx.backend.common.exception.SystemException
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

/**
 * @description MinIO 对象存储封装
 *
 * 桶保持私有读写，对外访问一律通过预签名 URL。
 * 桶存在性检查为懒加载（首次上传时确认/创建），服务启动期不强制依赖 MinIO 可用。
 * MinIO 各类异常在此统一包装为 SystemException（SERVICE_UNAVAILABLE），
 * 上层业务无需感知存储细节。
 */
@Service
class MinioStorageService(
    @Qualifier("minioClient") private val minioClient: MinioClient,
    @Qualifier("minioUrlClient") private val minioUrlClient: MinioClient,
    @Value("\${minio.endpoint}") private val endpoint: String,
    @Value("\${minio.public-endpoint:}") private val publicEndpoint: String,
    @Value("\${minio.bucket}") private val bucket: String,
    @Value("\${minio.presign-expiry-seconds:3600}") private val presignExpirySeconds: Int,
) {

    private val logger = LoggerFactory.getLogger(MinioStorageService::class.java)

    /** 桶已确认存在的缓存标记（懒加载，首次成功确认/创建后置位） */
    @Volatile
    private var bucketReady = false

    /**
     * @description 上传对象到 MinIO
     * @param objectKey 对象键（存储路径）
     * @param mimeType MIME 类型
     * @param size 文件大小（字节）
     * @param data 文件内容
     */
    fun put(objectKey: String, mimeType: String, size: Int, data: ByteArray) {
        try {
            ensureBucket()
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey)
                    .stream(ByteArrayInputStream(data), size.toLong(), -1)
                    .contentType(mimeType)
                    .build(),
            )
        } catch (ex: SystemException) {
            throw ex
        } catch (ex: Exception) {
            logger.error("MinIO 上传失败: bucket={} object={}", bucket, objectKey, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用，上传失败")
        }
    }

    /**
     * @description 删除 MinIO 对象（补偿性清理，失败仅告警不抛出）
     * @param objectKey 对象键
     */
    fun remove(objectKey: String) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(objectKey)
                    .build(),
            )
        } catch (ex: Exception) {
            logger.warn("MinIO 对象清理失败（忽略）: bucket={} object={}", bucket, objectKey, ex)
        }
    }

    /**
     * @description 生成对象的临时预签名下载 URL（私有桶唯一对外访问方式）
     * @param objectKey 对象键
     * @returns String 预签名 URL（有效期 minio.presign-expiry-seconds）
     */
    fun presignedGetUrl(objectKey: String): String =
        try {
            minioUrlClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucket)
                    .`object`(objectKey)
                    .expiry(presignExpirySeconds)
                    .build(),
            )
        } catch (ex: Exception) {
            logger.error("MinIO 预签名 URL 生成失败: bucket={} object={}", bucket, objectKey, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用")
        }

    /**
     * @description 生成对象的逻辑访问地址（仅用于入库持久化，非可直接访问的直链）
     * @param objectKey 对象键
     * @returns String {endpoint}/{bucket}/{objectKey}
     */
    fun logicalUrl(objectKey: String): String =
        "${(publicEndpoint.ifBlank { endpoint }).trimEnd('/')}/$bucket/$objectKey"

    /**
     * @description 确认桶存在，不存在则创建（懒加载 + 缓存）
     */
    @Synchronized
    private fun ensureBucket() {
        if (bucketReady) return
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
                logger.info("MinIO 桶已创建: {}", bucket)
            } else {
                logger.info("MinIO 桶已就绪: {}", bucket)
            }
            bucketReady = true
        } catch (ex: Exception) {
            logger.error("MinIO 桶确认/创建失败: bucket={}", bucket, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用")
        }
    }
}
