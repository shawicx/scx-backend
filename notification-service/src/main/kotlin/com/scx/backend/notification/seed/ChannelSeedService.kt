package com.scx.backend.notification.seed

import com.scx.backend.common.util.IdGenerator
import com.scx.backend.notification.entity.Channel
import com.scx.backend.notification.repository.ChannelRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * @description 渠道种子初始化：启动时幂等种入三个系统渠道
 *
 * EMAIL_CODE（邮箱验证码，EMAIL 投递）/ INBOX（站内信）/ ANNOUNCEMENT（通知公告），
 * isSystem=true 不可删除。异常仅记日志不阻断启动（与 identity SeedService 模式一致；
 * identity 过渡期进程内打包本类，重复执行按 code 幂等跳过）。
 */
@Component
class ChannelSeedService(
    private val channelRepository: ChannelRepository,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ChannelSeedService::class.java)

    override fun run(args: ApplicationArguments) {
        try {
            seedChannels()
        } catch (e: Exception) {
            logger.error("渠道种子初始化失败", e)
        }
    }

    /**
     * @description 幂等种入系统渠道（按 code 判断存在性）
     */
    fun seedChannels() {
        data class Seed(val code: String, val name: String, val type: String, val description: String)

        listOf(
            Seed("EMAIL_CODE", "邮箱验证码", "EMAIL", "系统事务邮件：验证码、欢迎、密码重置"),
            Seed("INBOX", "站内信", "INBOX", "系统站内信渠道，投递到用户收件箱"),
            Seed("ANNOUNCEMENT", "通知公告", "ANNOUNCEMENT", "系统公告渠道，投递到公告列表，支持置顶与级别"),
        ).forEach { seed ->
            if (channelRepository.findByCode(seed.code) == null) {
                channelRepository.save(
                    Channel(
                        id = IdGenerator.nextId(),
                        code = seed.code,
                        name = seed.name,
                        type = seed.type,
                        description = seed.description,
                        isSystem = true,
                        isActive = true,
                    ),
                )
                logger.info("已创建系统渠道: {}", seed.code)
            }
        }
    }
}
