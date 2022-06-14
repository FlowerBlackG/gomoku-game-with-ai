package com.gardilily.gomoku.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.opengl.Visibility
import android.view.View
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import androidx.core.content.ContextCompat
import com.gardilily.gomoku.R
import kotlin.math.min

class PieceView(context: Context,
                boardWidth: Int,
                boardHeight: Int,
                private val rows: Int,
                private val cols: Int,
                val row: Int,
                val col: Int) : RelativeLayout(context) {

    private var whitePiece = View(context)
    private var blackPiece = View(context)
    private var selectHint = View(context)

    private val pieceSize = min(boardWidth / rows, boardHeight / cols)

    init {
        val layoutParams = LayoutParams(pieceSize, pieceSize)
        this.layoutParams = layoutParams
        isClickable = true

        prepareBackgroundLines()

        whitePiece.visibility = INVISIBLE
        blackPiece.visibility = INVISIBLE
        selectHint.visibility = INVISIBLE

        whitePiece.background = ContextCompat.getDrawable(context, R.drawable.whitepiece)
        blackPiece.background = ContextCompat.getDrawable(context, R.drawable.blackpiece)
        selectHint.background = ContextCompat.getDrawable(context, R.drawable.selecthint)

        addView(whitePiece)
        addView(blackPiece)
        addView(selectHint)
    }

    companion object {
        private const val LINE_COLOR = "#f9e8d0"
    }

    private fun prepareBackgroundLines() {
        val horizontalLine = View(context)
        val verticalLine = View(context)

        addView(horizontalLine)
        addView(verticalLine)

        val hoLineParams = LayoutParams(
            if (col == 0 || col + 1 == cols) {
                pieceSize / 2
            } else {
                pieceSize
            },
            2
        )
        hoLineParams.addRule(CENTER_VERTICAL)
        if (col == 0) {
            hoLineParams.addRule(ALIGN_PARENT_END)
        }
        horizontalLine.layoutParams = hoLineParams
        horizontalLine.setBackgroundColor(Color.parseColor(LINE_COLOR))

        val verLineParams = LayoutParams(
            2,
            if (row == 0 || row + 1 == rows) {
                pieceSize / 2
            } else {
                pieceSize
            }
        )
        verLineParams.addRule(CENTER_HORIZONTAL)
        if (row == 0) {
            verLineParams.addRule(ALIGN_PARENT_BOTTOM)
        }
        verticalLine.layoutParams = verLineParams
        verticalLine.setBackgroundColor(Color.parseColor(LINE_COLOR))
    }

    enum class Status {
        BLACK,
        WHITE,
        SELECT,
        EMPTY
    }

    var status = Status.EMPTY
        set(value) {
            whitePiece.visibility = INVISIBLE
            blackPiece.visibility = INVISIBLE
            selectHint.visibility = INVISIBLE

            when (value) {
                Status.BLACK -> blackPiece.visibility = VISIBLE
                Status.WHITE -> whitePiece.visibility = VISIBLE
                Status.SELECT -> selectHint.visibility = VISIBLE
                else -> {}
            }

            field = value
        }

    fun setOnClickListener(callable: (view: View, row: Int, col: Int) -> Unit) {
        setOnClickListener { view ->
            callable(view, row, col)
        }
    }
}
