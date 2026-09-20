package com.scx.backend.file.storage

import com.scx.backend.common.exception.SystemException
import io.minio.AbortMultipartUploadArgs
import io.minio.BucketExistsArgs
import io.minio.CompleteMultipartUploadArgs
import io.minio.CreateMultipartUploadArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.UploadPartArgs
import io.minio.messages.Part
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** CompleteMultipartUpload 所需的分片清单项（分片号 + ETag） */
data class PartEtag(
    val partNumber: Int,
    val etag: String,
)

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
    @Qualifier("minioAsyncClient") private val minioAsyncClient: MinioAsyncClient,
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
     * @description 发起 MinIO 分片上传（S3 Multipart Upload，9.x 异步客户端）
     * @param objectKey 对象键（存储路径）
     * @param mimeType MIME 类型
     * @returns String MinIO 侧 uploadId（后续分片上传与合并的凭据）
     */
    fun createMultipartUpload(objectKey: String, mimeType: String): String =
        try {
            ensureBucket()
            await(
                minioAsyncClient.createMultipartUpload(
                    CreateMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .`object`(objectKey)
                        .headers(Http.Headers(mapOf("Content-Type" to mimeType)))
                        .build(),
                ),
            ).result().uploadId()
        } catch (ex: SystemException) {
            throw ex
        } catch (ex: Exception) {
            logger.error("MinIO 发起分片上传失败: bucket={} object={}", bucket, objectKey, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用，上传失败")
        }

    /**
     * @description 上传单个分片（先落临时文件再交给 SDK，堆内不驻留分片内容）
     *
     * MinIO 9.x 的 UploadPartArgs 仅接受 byte[]/ByteBuffer/RandomAccessFile，
     * 分片上限 64MB，为避免堆内存峰值采用临时文件中转。
     * @param objectKey 对象键
     * @param uploadId MinIO 侧 uploadId
     * @param partNumber 分片号（从 1 开始）
     * @param size 分片大小（字节）
     * @param data 分片内容流
     * @returns String 分片 ETag（去除引号、小写化的内容 MD5）
     */
    fun uploadPart(objectKey: String, uploadId: String, partNumber: Int, size: Long, data: InputStream): String {
        val tempFile = File.createTempFile("scx-chunk-", ".part")
        try {
            tempFile.outputStream().use { output -> data.copyTo(output) }
            if (tempFile.length() != size) {
                throw SystemException.invalidParameter("分片实际大小与声明不符：应为 $size 字节，实际 ${tempFile.length()} 字节")
            }
            RandomAccessFile(tempFile, "r").use { raf ->
                return try {
                    await(
                        minioAsyncClient.uploadPart(
                            UploadPartArgs.builder()
                                .bucket(bucket)
                                .`object`(objectKey)
                                .uploadId(uploadId)
                                .partNumber(partNumber)
                                .file(raf, size)
                                .build(),
                        ),
                    ).part().etag().removeSurrounding("\"").lowercase()
                } catch (ex: SystemException) {
                    throw ex
                } catch (ex: Exception) {
                    logger.error("MinIO 分片上传失败: bucket={} object={} part={}", bucket, objectKey, partNumber, ex)
                    throw SystemException.serviceUnavailable("文件存储服务暂时不可用，分片上传失败")
                }
            }
        } finally {
            if (!tempFile.delete()) {
                logger.warn("分片临时文件删除失败: {}", tempFile.absolutePath)
            }
        }
    }

    /**
     * @description 完成分片上传（MinIO 服务端合并分片为完整对象，零拷贝）
     * @param objectKey 对象键
     * @param uploadId MinIO 侧 uploadId
     * @param parts 分片清单（分片号 + ETag，按分片号升序）
     */
    fun completeMultipartUpload(objectKey: String, uploadId: String, parts: List<PartEtag>) {
        try {
            await(
                minioAsyncClient.completeMultipartUpload(
                    CompleteMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .`object`(objectKey)
                        .uploadId(uploadId)
                        .parts(parts.map { Part(it.partNumber, it.etag) }.toTypedArray())
                        .build(),
                ),
            )
        } catch (ex: SystemException) {
            throw ex
        } catch (ex: Exception) {
            logger.error("MinIO 完成分片上传失败: bucket={} object={} uploadId={}", bucket, objectKey, uploadId, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用，合并分片失败")
        }
    }

    /**
     * @description 中止分片上传（MinIO 侧释放已传分片占用的存储）
     * @param objectKey 对象键
     * @param uploadId MinIO 侧 uploadId
     */
    fun abortMultipartUpload(objectKey: String, uploadId: String) {
        try {
            await(
                minioAsyncClient.abortMultipartUpload(
                    AbortMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .`object`(objectKey)
                        .uploadId(uploadId)
                        .build(),
                ),
            )
        } catch (ex: SystemException) {
            throw ex
        } catch (ex: Exception) {
            logger.error("MinIO 中止分片上传失败: bucket={} object={} uploadId={}", bucket, objectKey, uploadId, ex)
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用，取消上传失败")
        }
    }

    /**
     * @description 同步等待异步客户端调用完成（multipart API 仅异步提供）
     * @param future 异步调用句柄
     * @returns T 调用结果
     */
    private fun <T> await(future: CompletableFuture<T>): T =
        try {
            future.get()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用")
        } catch (ex: ExecutionException) {
            throw SystemException.serviceUnavailable("文件存储服务暂时不可用")
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
