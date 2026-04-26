package com.taomic.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.taomic.agent.AgentApp
import com.taomic.agent.ui.MainActivity

/**
 * 常驻前台服务：让 AgentOS 在用户呼出浮窗后脱离 Activity 生命周期保持就绪。
 *
 * 责任划分（与 [AgentApp] 的边界）：
 *  - [AgentApp] 持业务状态：SkillRunner / IntentRouter / SkillRegistry。
 *    单例，与 Application 同生命周期；浮窗未显示时也常驻在内存。
 *  - 本 Service 持 UI 资源：前台通知 + 浮窗显示。Service 起则浮窗起，
 *    Service 停则浮窗下。这样系统的 START_STICKY / 重启逻辑天然带回浮窗。
 *
 * 启动时机：MainActivity 引导页用户首次点"显示浮窗"时
 * [Context.startForegroundService] 拉起本 Service（用户可见操作，满足 Android 12+
 * background-service 启动限制）。
 *
 * Android 14+：foregroundServiceType=specialUse + manifest PropertyDecl 已声明。
 *
 * V0.1：通知极简（title + body）。V0.7 加"停止"action / "金额确认"等富 UI。
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat()
        (application as? AgentApp)?.showBubble()
        Log.i(TAG, "service onCreate; bubble shown via AgentApp facade")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 重复 startForegroundService 不副作用：浮窗已显示则 no-op，已被回收则补出来
        (application as? AgentApp)?.let { app ->
            if (!app.isBubbleShown()) {
                app.showBubble()
                Log.i(TAG, "onStartCommand: re-showing bubble after process restart")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        (application as? AgentApp)?.hideBubble()
        Log.i(TAG, "service onDestroy; bubble hidden")
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AgentOS 运行中",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "前台服务通知，让 AgentOS 在系统中保持就绪"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val openMain = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = PendingIntent.getActivity(this, 0, openMain, pendingFlags)

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AgentOS 已就绪")
            .setContentText("浮窗常驻；点击进入主界面")
            .setOngoing(true)
            .setContentIntent(tap)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须声明 type；与 manifest 中 foregroundServiceType 一致
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG: String = "AgentFgSvc"
        private const val CHANNEL_ID = "agent_running_channel"
        private const val NOTIFICATION_ID = 0xA1

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
