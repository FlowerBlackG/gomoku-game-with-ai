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
                GAME_MODE_PVP -> "????????????"
                GAME_MODE_PVC_UWHITE, GAME_MODE_PVC_UBLACK -> "?????????"
                else -> "????????????"
            }

        initAgent()

        initButtons()
    }

    private fun initButtons() {
        // ??????
        findViewById<Button>(R.id.game_btnExit).setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // ??????
        findViewById<Button>(R.id.game_btnRegret).setOnClickListener {
            val takeBackList = agent.takeBack(1)
            title.text =
                if (gameMode != GAME_MODE_PVP) {
                    "?????????"
                } else {
                    "????????????"
                }

            Toast.makeText(this, "?????? ${takeBackList.size} ???", Toast.LENGTH_SHORT).show()

            takeBackList.forEach {
                pieceViews[it.row][it.col].status = PieceView.Status.EMPTY
                isBlackTurn = !isBlackTurn
            }

            findViewById<Button>(R.id.game_btnConfirm).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.game_progressBar).visibility = View.GONE
        }

        // ??????
        findViewById<Button>(R.id.game_btnConfirm).setOnClickListener {
            // ??????????????????
            if (agent.gameStatus != GomokuAgent.GameStatus.DRAW) {
                Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedCoord.row == -1) {
                Toast.makeText(this, "????????????????????????", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ????????????
            agent.submit(selectedCoord)

            // ????????????
            pieceViews[selectedCoord.row][selectedCoord.col].status = if (isBlackTurn) {
                PieceView.Status.BLACK
            } else {
                PieceView.Status.WHITE
            }

            // ??????
            isBlackTurn = !isBlackTurn

            // ????????????
            selectedCoord.row = -1

            if (agent.gameStatus == GomokuAgent.GameStatus.BLACK_WIN) {
                title.text = "????????????"
            } else if (agent.gameStatus == GomokuAgent.GameStatus.WHITE_WIN) {
                title.text = "????????????"
            }

            if (gameMode != GAME_MODE_PVP && agent.gameStatus == GomokuAgent.GameStatus.DRAW) { // pvc ?????????ai ??????
                findViewById<Button>(R.id.game_btnConfirm).visibility = View.GONE
                findViewById<ProgressBar>(R.id.game_progressBar).visibility = View.VISIBLE
                title.text = "ai ?????????..."

                agent.thinkAsync {
                    runOnUiThread {
                        title.text = "?????????"

                        if (agent.gameStatus == GomokuAgent.GameStatus.BLACK_WIN) {
                            title.text = "????????????"
                        } else if (agent.gameStatus == GomokuAgent.GameStatus.WHITE_WIN) {
                            title.text = "????????????"
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

    private var isBlackTurn = true // ??????pvp

    private lateinit var title: TextView

    private lateinit var agent: GomokuAgent
    private var gameMode: Int? = null
    private val selectedCoord = Coord(-1, -1) // -1 ??????????????????

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