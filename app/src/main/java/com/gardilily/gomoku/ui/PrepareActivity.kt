package com.gardilily.gomoku.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.gardilily.gomoku.R
import com.gardilily.gomoku.cloud.AppUpdater

class PrepareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prepare)

        prepareButtons()
        AppUpdater.checkUpdate(this)
    }

    private fun prepareButtons() {
        findViewById<Button>(R.id.prepare_btnPvp).setOnClickListener {
            softJump(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.INTENT_PARAM_GAME_MODE, GameActivity.GAME_MODE_PVP)
            )
        }

        findViewById<Button>(R.id.prepare_btnPvcPlayerBlack).setOnClickListener {
            softJump(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.INTENT_PARAM_GAME_MODE, GameActivity.GAME_MODE_PVC_UBLACK)
            )
        }

        findViewById<Button>(R.id.prepare_btnPvcPlayerWhite).setOnClickListener {
            softJump(
                Intent(this, GameActivity::class.java)
                    .putExtra(GameActivity.INTENT_PARAM_GAME_MODE, GameActivity.GAME_MODE_PVC_UWHITE)
            )
        }
    }

    private fun softJump(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}