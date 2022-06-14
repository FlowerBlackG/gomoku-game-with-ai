package com.gardilily.gomoku.cloud

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.concurrent.thread

class AppUpdater {
    companion object {
        /**
         * 发起一个安全的网络请求。防止因为 Exception 而程序崩溃。
         *
         * @return 请求成功时，返回请求结果。请求失败时，返回 null
         */
        fun safeNetworkRequest(req: Request, client: OkHttpClient): Response? {
            return try {
                val res = client.newCall(req).execute()
                res
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 检查更新。
         *
         * @param showDialogIfIsUpToDate - 如果已经是最新版本，是否需要展示提示框。
         */
        fun checkUpdate(activity: Activity, showDialogIfIsUpToDate: Boolean = false) {
            thread {
                val packageManager = activity.packageManager
                val packageName = activity.packageName

                val tarUrl = "secret?" +
                        "version=" + packageManager.getPackageInfo(packageName, 0).longVersionCode

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(tarUrl)
                    .build()

                val response = safeNetworkRequest(request, client)

                if (response == null) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "网络异常", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }

                val jsonObj = JSONObject(response.body?.string())

                if (!jsonObj.getBoolean("isLatest")) {
                    // 有更新可用
                    val updateMsg = "版本：" + jsonObj.getString("newVersionName") +
                            "\n时间：" + jsonObj.getString("newVersionBuildTime") +
                            "\n说明：\n\n" + jsonObj.getString("updateLog") + "\n"

                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle("有更新可用")
                            .setMessage(updateMsg)
                            .setPositiveButton("更新") { _, _ ->

                                val uri = Uri.parse(jsonObj.getString("updateUrl"))
                                activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                } else {
                    // 已是最新版
                    if (showDialogIfIsUpToDate) {
                        activity.runOnUiThread {
                            AlertDialog.Builder(activity)
                                .setTitle("当前版本已是最新")
                                .setMessage("当前版本：${packageManager.getPackageInfo(packageName, 0).versionName} (${packageManager.getPackageInfo(packageName, 0).longVersionCode})")
                                .setPositiveButton("好耶", null)
                                .show()
                        }
                    }
                }
            }
        }

    }
}