package com.gardilily.gomoku.ui

import android.app.Activity
import android.opengl.Visibility
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.gardilily.gomoku.R
import com.gardilily.gomokuAgent.GomokuAgent
import com.gardilily.gomokuAgent.GomokuAgent.Coord

class GameActivity :  Activity() {

    private val pieceViews = Array(15) { ArrayList<PieceView>() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val boardView = findViewById<LinearLayout>(R.id.game_board)
        boardView.post {
            for (i in 0 until 15) {
                val linearLayout = LinearLayout(this)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                linearLayout.layoutParams = params
                linearLayout.gravity = Gravity.CENTER
                linearLayout.orientation = LinearLayout.HORIZONTAL

                boardView.addView(linearLayout)

                for (j in 0 until 15) {
                    val piece = PieceView(
                        this,
                        boardView.width, boardView.height,
                        15, 15, i, j
                    )
                    linearLayout.addView(piece)
                    piece.setOnClickListener { _, row, col ->
                        handlePieceClick(row, col)
                    }

                    pieceViews[i].add(piece)
                }
            }

            if (agent.agentPieceColor == GomokuAgent.PieceColor.BLACK
                && agent.gameMode == GomokuAgent.GameMode.PVC
            ) {
                val coord = agent.thinkSync()!!
                pieceViews[coord.row][coord.col].status = PieceView.Status.BLACK
                isBlackTurn = !isBlackTurn
            }
        }

        title = findViewById(R.id.game_title)
        gameMode = intent.getIntExtra(INTENT_PARAM_GAME_MODE, GAME_MODE_PVP)

        title.text =
            when (gameMode) {
                GAME_MODE_PVP -> "玩家对战"
                GAME_MODE_PVC_UWHITE, GAME_MODE_PVC_UBLACK -> "请落子"
                else -> "未知错误"
            }

        initAgent()

        initButtons()
    }

    private fun initButtons() {
        // 退出
        findViewById<Button>(R.id.game_btnExit).setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // 悔棋
        findViewById<Button>(R.id.game_btnRegret).setOnClickListener {
            val takeBackList = agent.takeBack(1)
            title.text =
                if (gameMode != GAME_MODE_PVP) {
                    "请落子"
                } else {
                    "玩家对战"
                }

            Toast.makeText(this, "撤回 ${takeBackList.size} 步", Toast.LENGTH_SHORT).show()

            takeBackList.forEach {
                pieceViews[it.row][it.col].status = PieceView.Status.EMPTY
                isBlackTurn = !isBlackTurn
            }

            findViewById<Button>(R.id.game_btnConfirm).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.game_progressBar).visibility = View.GONE
        }

        // 确认
        findViewById<Button>(R.id.game_btnConfirm).setOnClickListener {
            // 基本情况校验
            if (agent.gameStatus != GomokuAgent.GameStatus.DRAW) {
                Toast.makeText(this, "游戏已经结束", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedCoord.row == -1) {
                Toast.makeText(this, "请先选择一个位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 提交落子
            agent.submit(selectedCoord)

            // 屏幕更新
            pieceViews[selectedCoord.row][selectedCoord.col].status = if (isBlackTurn) {
                PieceView.Status.BLACK
            } else {
                PieceView.Status.WHITE
            }

            // 轮换
            isBlackTurn = !isBlackTurn

            // 取消选择
            selectedCoord.row = -1

            if (agent.gameStatus == GomokuAgent.GameStatus.BLACK_WIN) {
                title.text = "黑棋胜利"
            } else if (agent.gameStatus == GomokuAgent.GameStatus.WHITE_WIN) {
                title.text = "白棋胜利"
            }

            if (gameMode != GAME_MODE_PVP && agent.gameStatus == GomokuAgent.GameStatus.DRAW) { // pvc 模式：ai 下棋
                findViewById<Button>(R.id.game_btnConfirm).visibility = View.GONE
                findViewById<ProgressBar>(R.id.game_progressBar).visibility = View.VISIBLE
                title.text = "ai 思考中..."

                agent.thinkAsync {
                    runOnUiThread {
                        title.text = "请落子"

                        if (agent.gameStatus == GomokuAgent.GameStatus.BLACK_WIN) {
                            title.text = "黑棋胜利"
                        } else if (agent.gameStatus == GomokuAgent.GameStatus.WHITE_WIN) {
                            title.text = "白棋胜利"
                        }

                        pieceViews[it.row][it.col].status = if (isBlackTurn) {
                            PieceView.Status.BLACK
                        } else {
                            PieceView.Status.WHITE
                        }

                        isBlackTurn = !isBlackTurn

                        findViewById<Button>(R.id.game_btnConfirm).visibility = View.VISIBLE
                        findViewById<ProgressBar>(R.id.game_progressBar).visibility = View.GONE

                    }
                }
            }
        }
    }

    private fun initAgent() {
        agent = GomokuAgent.Builder()
            .setCols(15)
            .setRows(15)
            .setGameMode(
                when (gameMode) {
                    GAME_MODE_PVP -> GomokuAgent.GameMode.PVP
                    else -> GomokuAgent.GameMode.PVC
                }
            )
            .setAgentPieceColor(
                when (gameMode) {
                    GAME_MODE_PVC_UBLACK -> GomokuAgent.PieceColor.WHITE
                    else -> GomokuAgent.PieceColor.BLACK
                }
            )
            .build()
    }

    private var isBlackTurn = true // 用于pvp

    private lateinit var title: TextView

    private lateinit var agent: GomokuAgent
    private var gameMode: Int? = null
    private val selectedCoord = Coord(-1, -1) // -1 表示未选择。

    private fun handlePieceClick(row: Int, col: Int) {
        if (agent.gameStatus == GomokuAgent.GameStatus.DRAW)
        {
            if (selectedCoord.row != -1) {
                pieceViews[selectedCoord.row][selectedCoord.col].status = PieceView.Status.EMPTY
                selectedCoord.row = -1
                selectedCoord.col = -1
            }

            if (pieceViews[row][col].status != PieceView.Status.BLACK
                && pieceViews[row][col].status != PieceView.Status.WHITE) {
                pieceViews[row][col].status = PieceView.Status.SELECT
                selectedCoord.row = row
                selectedCoord.col = col
            }
        }
    }

    companion object {
        const val INTENT_PARAM_GAME_MODE = "gamemode"

        const val GAME_MODE_PVP = 1
        const val GAME_MODE_PVC_UBLACK = 2
        const val GAME_MODE_PVC_UWHITE = 3
    }
}