package com.scx.backend.commonaudit.aspect

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.scx.backend.common.security.AuthContextResolver
import com.scx.backend.commonaudit.annotation.OperationLog
import com.scx.backend.commonaudit.service.OperationLogRecorder
import com.scx.backend.common.util.IpUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.multipart.MultipartFile

/**
 * @description 操作日志切面
 *
 * 拦截标注 [OperationLog] 的 Controller 写方法，记录操作人（X-User-* 头）、
 * 模块动作、HTTP 上下文、入参摘要与结果耗时后落库。
 *
 * 约束：
 *  - 入参序列化脱敏：key 命中 password/secret/token/authorization/verificationCode
 *    的字段打码；文件参数只记文件名与大小；整体截断 2000 字符
 *  - 日志写入失败不影响业务（Recorder 内部捕获）
 *  - 业务异常记录后原样抛出，由全局异常处理器统一响应
 */
@Aspect
@Component
class OperationLogAspect(
    private val operationLogRecorder: OperationLogRecorder,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(OperationLogAspect::class.java)

    /** 入参中需要跳过序列化的 Servlet 原生类型 */
    private val skippedArgTypes = listOf(HttpServletRequest::class, HttpServletResponse::class)

    /** 敏感字段名匹配（小写比较，不含裸 code——角色编码等业务字段非敏感） */
    private val sensitiveKeyPattern = Regex(".*(password|secret|token|authorization|verificationcode).*")

    /**
     * @description 环绕通知：按方法签名上的注解记录操作日志（不依赖切点参数名绑定）
     * @param joinPoint 连接点（标注了 @OperationLog 的 Controller 方法）
     * @returns Any? 原方法返回值（异常时记录后原样抛出）
     *
     * @example POST /api/roles/create 标注 @OperationLog(module="角色管理", action="创建角色") 后自动落库
     */
    @Around("@annotation(com.scx.backend.commonaudit.annotation.OperationLog)")
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        val annotation = (joinPoint.signature as? MethodSignature)
            ?.method
            ?.getAnnotation(OperationLog::class.java)
            ?: return joinPoint.proceed()

        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val principal = request?.let { AuthContextResolver.resolveFromHeader(it) }
        val params = serializeArgs(joinPoint.args)
        val start = System.currentTimeMillis()

        return try {
            val result = joinPoint.proceed()
            operationLogRecorder.record(
                userId = principal?.userId,
                userEmail = principal?.email,
                module = annotation.module,
                action = annotation.action,
                httpMethod = request?.method,
                uri = request?.requestURI,
                ip = request?.let { IpUtils.getClientIp(it) },
                userAgent = request?.getHeader("user-agent"),
                params = params,
                success = true,
                errorMessage = null,
                costMs = System.currentTimeMillis() - start,
            )
            result
        } catch (e: Throwable) {
            operationLogRecorder.record(
                userId = principal?.userId,
                userEmail = principal?.email,
                module = annotation.module,
                action = annotation.action,
                httpMethod = request?.method,
                uri = request?.requestURI,
                ip = request?.let { IpUtils.getClientIp(it) },
                userAgent = request?.getHeader("user-agent"),
                params = params,
                success = false,
                errorMessage = if (e is com.scx.backend.common.exception.SystemException) {
                    "${e.code} ${e.message}"
                } else {
                    e.message ?: e.javaClass.simpleName
                },
                costMs = System.currentTimeMillis() - start,
            )
            throw e
        }
    }

    /**
     * @description 序列化方法入参为 JSON 摘要（跳过 Servlet 对象、文件记名与大小、敏感字段打码）
     * @param args 切面捕获的方法入参
     * @returns String? JSON 数组字符串（最多 2000 字符），无可见参数时返回 null
     *
     * @example serializeArgs(arrayOf(CreateRoleDto(...))) == [{"name":"管理员","code":"ADMIN"}]
     */
    private fun serializeArgs(args: Array<Any?>): String? {
        return try {
            val visible = args.mapNotNull { arg ->
                when {
                    arg == null -> null
                    skippedArgTypes.any { it.java.isInstance(arg) } -> null
                    arg is MultipartFile -> linkedMapOf("filename" to arg.originalFilename, "size" to arg.size)
                    else -> arg
                }
            }
            if (visible.isEmpty()) return null
            val node = objectMapper.valueToTree<JsonNode>(visible)
            if (node.isArray) node.forEach { maskSensitive(it) }
            node.toString().take(2000)
        } catch (e: Exception) {
            // 序列化失败不影响业务，退化为 toString 摘要
            logger.debug("操作日志入参序列化失败: ${e.message}")
            args.joinToString(",", prefix = "[", postfix = "]") { it?.javaClass?.simpleName ?: "null" }.take(2000)
        }
    }

    /**
     * @description 递归打敏 JSON 树中命中敏感命名规则的字段值
     * @param node 待处理的 JSON 节点
     */
    private fun maskSensitive(node: JsonNode) {
        when {
            node.isObject -> {
                node as ObjectNode
                node.fields().forEach { (key, value) ->
                    if (sensitiveKeyPattern.matches(key.lowercase())) {
                        node.put(key, "******")
                    } else {
                        maskSensitive(value)
                    }
                }
            }
            node.isArray -> node.forEach { maskSensitive(it) }
            else -> Unit
        }
    }
}
