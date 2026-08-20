package com.heihun.easytiermd3.data.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.heihun.easytiermd3.core.api.EasyTierIntents

/**
 * 通过显式组件名启动 Foreground Service，避免 data -> service 模块依赖。
 * TODO: Phase 5 迁移到基于 Hilt 的 Service 绑定/接口化设计。
 */
object ServiceStarter {

    private const val SERVICE_CLASS = "com.heihun.easytiermd3.service.EasyTierForegroundService"

    fun startCore(context: Context, networkId: String) {
        val intent = Intent(EasyTierIntents.ACTION_START_CORE)
            .setClassName(context, SERVICE_CLASS)
            .putExtra(EasyTierIntents.EXTRA_NETWORK_ID, networkId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopCore(context: Context) {
        val intent = Intent(EasyTierIntents.ACTION_STOP_CORE)
            .setClassName(context, SERVICE_CLASS)
        context.startService(intent)
    }
}