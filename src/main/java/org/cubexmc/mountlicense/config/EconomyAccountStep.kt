package org.cubexmc.mountlicense.config

import org.cubexmc.config.MigrationContext
import org.cubexmc.config.MigrationStep

/**
 * config v2 -> v3：加入 `economy.account`，让收上来的注册费能转进服务器账户而不是凭空消失。
 *
 * 默认写空串 —— 空 = 保持 v2 的行为（销毁）。**不替服主猜一个账户往里转钱**：
 * 猜错的后果是钱进了别人的口袋，比不入账更糟。
 *
 * 已经有这个键（服主手写过、或迁移跑过一半）时原样保留，所以重复执行是安全的。
 */
class EconomyAccountStep : MigrationStep {
    override fun fromVersion(): Int = 2

    override fun toVersion(): Int = 3

    override fun description(): String = "Add economy.account (where charged money is routed)."

    override fun migrate(context: MigrationContext) {
        if (!context.yaml().contains("economy.account")) {
            context.yaml()["economy.account"] = ""
        }
    }
}
