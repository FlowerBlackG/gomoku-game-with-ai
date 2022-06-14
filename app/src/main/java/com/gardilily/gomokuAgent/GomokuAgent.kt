/*
 * 五子棋智能 Agent
 * 课程《人工智能原理与技术课程设计》大作业 组成部分
 * 同济大学电信学院计算机系
 *
 * 小组成员：
 * ...secret
 *
 * 2022年4月9日   于上海市嘉定区创建
 * 2022年4月18日  基本技术测试完毕
 */

package com.gardilily.gomokuAgent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 游戏板结构。
 * 使用二维数组存储游戏板内容。
 * 特别注意，克隆游戏板时，不要用 clone() 函数，要用 GomokuAgent 类内的私有方法 deepCopy().
 */
private typealias GameBoard = Array<Array<GomokuAgent.PieceColor>>

/**
 * 五子棋智能 Agent. 也可作为 PVP 游戏的裁判。
 * 必须使用 GomokuAgent.Builder 完成对象构建。
 *
 * 通过 Builder 设置以下参数：
 *   游戏模式：PVP 或 PVC
 *   Agent 执棋颜色：黑色或白色。注意，当游戏模式为 PVP 时，Agent 执棋颜色参数无效。
 *   行数：棋盘行数。需要在指定范围内。
 *   列数：棋盘列数。需要在指定范围内
 *     行列指定范围可以在 Builder 的伴随对象里找到。
 *
 * PVP 模式中，Agent 对象仅作为裁判，判断游戏是否结束。
 * PVC 模式中，Agent 可以与玩家对战。
 *
 * 对象构建例：
 *
 *     val agent = GomokuAgent.Builder()
 *                     .setCols(15) // 设置列数。
 *                     .setRols(15) // 设置行数。
 *                     .setAgentPieceColor(GomokuAgent.PieceColor.WHITE)
 *                     .setGameMode(GomokuAgent.GameMode.PVC)
 *                     .build() // 生成对象。
 *
 * 使用 submit 方法提交用户落子。必须保证落子不超出棋盘范围，且未被占用，否则会抛出异常。
 *
 * PVC 模式下，agent 可能有以下状态，可以通过读取 agentStatus 获知：
 *   thinking 正在思考
 *   waiting for player 等待玩家落子
 *   sleeping 休眠
 *   game finished 游戏结束
 *
 * agent 处于等待状态时，submit 方法有效，否则使用该方法提交的落子会被退回。
 * 若任何一方胜利，agent 会进入游戏结束状态。
 * 用户提交落子且未胜利时，agent 会进入休眠状态，需要由开发者手动召唤 agent 进入思考状态。
 * 召唤方式有两种：
 *
 *   thinkSync() 阻塞，思考完毕后方法将 agent 落子返回。
 *   thinkAsync(callable) 非阻塞。方法会立即返回。agent 思考完毕，将携带落子坐标执行传入的 callable.
 *
 * 如果没有提交新的落子，但是调用以上方法，将返回 agent 最近一步落子。
 *
 * 通过 takeBack() 方法可以悔棋。具体用法参考方法说明。
 * 该方法允许在 agent 思考的同时使用。如果悔棋时 agent 正在思考，将打断思考过程，且思考结果不会被记录。
 *
 * 通过查询 gameStatus 值获知游戏是否结束。若结束，何方胜利。
 */
class GomokuAgent private constructor(builder: Builder) {

	/*---------------- 基本枚举 ----------------*/

	/**
	 * 游戏胜负状态。
	 */
	enum class GameStatus {
		/** 黑棋胜利。 */
		BLACK_WIN,

		/** 白棋胜利。 */
		WHITE_WIN,

		/** 平局。也可能是未分胜负。 */
		DRAW
	}

	/**
	 * 棋子颜色。
	 */
	enum class PieceColor {
		WHITE, BLACK, EMPTY
	}

	/**
	 * 智能体对象状态。
	 */
	enum class AgentStatus {
		/**
		 * 智能体正在思考如何行棋。
		 */
		THINKING,

		/**
		 * 智能体正在等待玩家进行下一步。
		 */
		WAITING_FOR_PLAYER,

		/**
		 * 智能体不在工作。如果智能体模式是手动思考，则可能进入此状态。
		 */
		SLEEPING,

		/**
		 * 游戏已经结束。
		 */
		GAME_FINISHED
	}

	/**
	 * 游戏模式。
	 */
	enum class GameMode {
		/**
		 * 玩家之间对战。
		 */
		PVP,

		/**
		 * 玩家与智能体对战。
		 */
		PVC
	}

	/*---------------- 设计者类 ----------------*/

	/**
	 * GomokuAgent 设计者。用于构建 GomokuAgent 对象。
	 */
	class Builder {

		companion object {
			/** 游戏板长度最小值。 */
			const val BOARD_MIN_SIZE = 10

			/** 游戏板长度最大值。 */
			const val BOARD_MAX_SIZE = 20
		}

		/** 棋盘行数。 */
		var rows = 15
		fun setRows(rows: Int) = apply {
			this.rows = rows
		}

		/** 棋盘列数。 */
		var cols = 15
		fun setCols(cols: Int) = apply {
			this.cols = cols
		}

		/** 本智能体执棋颜色。 */
		var agentPieceColor = PieceColor.WHITE
		fun setAgentPieceColor(color: PieceColor) = apply {
			this.agentPieceColor = color
		}

		/** 游戏模式。 */
		var gameMode = GameMode.PVC
		fun setGameMode(mode: GameMode) = apply {
			this.gameMode = mode
		}

		/**
		 * 构建 GomokuAgent 对象。
		 */
		fun build(): GomokuAgent {
			// 如果智能体棋子颜色为空，则抛出异常。
			if (agentPieceColor == PieceColor.EMPTY) {
				throw AgentPieceColorNotSetException()
			}

			// 如果棋盘尺寸不符合规则，则抛出异常。
			if (rows < BOARD_MIN_SIZE || rows > BOARD_MAX_SIZE
				|| cols < BOARD_MIN_SIZE || cols > BOARD_MAX_SIZE
			) {
				throw GameBoardSizeInvalidException()
			}

			return GomokuAgent(this)
		}
	}

	/*---------------- 游戏基本参数 ----------------*/

	/** 棋盘行数。 */
	val rows = builder.rows

	/** 棋盘列数。 */
	val cols = builder.cols

	/** 本智能体执棋颜色。 */
	val agentPieceColor = builder.agentPieceColor

	/** 游戏模式。 */
	val gameMode = builder.gameMode

	/*---------------- 游戏状态参数 ----------------*/

	/** 智能体状态变量锁。 */
	private val agentStatusMutex = Mutex()

	/** 智能体状态。 */
	var agentStatus = AgentStatus.WAITING_FOR_PLAYER
		private set

	/**
	 * 游戏状态。
	 * 考虑到胜负判定性能开销极小，本操作在每次落子后立即阻塞式执行。
	 */
	var gameStatus = GameStatus.DRAW
		private set // 允许随意读，但不准从外部设置。

	/*---------------- 基本数据结构 ----------------*/

	/**
	 * 坐标。
	 * 注意，行列数据是可变的整数，传递时需要注意原始数据是否会被修改。
	 * 考虑到 Coord 数据类很轻量，当传递过程存在风险时，可以采用传递副本 (clone()) 的方式避免潜在风险。
	 */
	data class Coord(var row: Int, var col: Int) {

		/*---------------- 各种运算符重载。 ----------------*/

		operator fun unaryMinus(): Coord {
			return Coord(-row, -col)
		}

		operator fun plus(other: Coord): Coord {
			return Coord(this.row + other.row, this.col + other.col)
		}

		operator fun minus(other: Coord): Coord {
			return Coord(this.row - other.row, this.col - other.col)
		}

		operator fun plusAssign(other: Coord) {
			this.row += other.row
			this.col += other.col
		}

		operator fun minusAssign(other: Coord) {
			this.row -= other.row
			this.col -= other.col
		}

		operator fun times(other: Int): Coord {
			return Coord(row * other, col * other)
		}

		operator fun timesAssign(other: Int) {
			this.row *= other
			this.col *= other
		}

		operator fun div(other: Int): Coord {
			return Coord(row / other, col / other)
		}

		operator fun divAssign(other: Int) {
			this.row /= other
			this.col /= other
		}

		/*---------------- 深拷贝 ----------------*/

		/**
		 * 克隆一个副本。
		 */
		fun clone(): Coord {
			return Coord(row, col)
		}

		/*---------------- 方法重写（为适配 hash map） ----------------*/

		override fun hashCode(): Int {
			return this.row * 100000 + this.col
		}

		override fun equals(other: Any?): Boolean {
			return when (other) {
				is Coord -> this.row == other.row && this.col == other.col
				else -> super.equals(other)
			}
		}
	}

	/*---------------- 基本异常 ----------------*/

	/**
	 * 坐标超出棋盘范围。
	 */
	class CoordOutOfBoardException : IndexOutOfBoundsException()

	/**
	 * 坐标已有棋子存在。
	 */
	class CoordOccupiedException : Exception()

	/**
	 * 智能体的棋子颜色为空。
	 */
	class AgentPieceColorNotSetException : IllegalArgumentException()

	/**
	 * 游戏板尺寸不符合规定。
	 */
	class GameBoardSizeInvalidException : IllegalArgumentException()

	/*---------------- 对外方法 ----------------*/

	/**
	 * 悔棋。如果智能体正在思考，该过程会打断智能体的思考。
	 * 如果游戏已经结束，该方法会将游戏撤回到正在进行状态。
	 *
	 * @param steps 悔棋步数。该步数为用户执子的步数。
	 * @return 撤回的所有棋子位置组成的列表。包含用户行棋和机器行棋。
	 */
	fun takeBack(steps: Int): List<Coord> = runBlocking {
		agentStatusMutex.withLock {
			agentStatus = AgentStatus.WAITING_FOR_PLAYER
		}

		val targetSteps =
			when (gameMode) {
				GameMode.PVC -> when {
					agentPieceColor == PieceColor.WHITE && records.size % 2 == 0 -> steps * 2
					agentPieceColor == PieceColor.WHITE -> steps * 2 - 1
					agentPieceColor == PieceColor.BLACK && records.size % 2 != 0 -> steps * 2
					else -> steps * 2 - 1
				}
				else -> steps
			}

		val resultArrayList = ArrayList<Coord>()

		for (i in 0 until targetSteps) {
			if (records.isEmpty()) {
				break
			} else {
				val coord = records.removeLast()
				if (gameMode != GameMode.PVP) {
					historyScore.removeLast()
				}
				gameBoard[coord.row][coord.col] = PieceColor.EMPTY
				resultArrayList.add(coord)
			}
		}

		gameStatus = GameStatus.DRAW

		return@runBlocking resultArrayList.toList()
	}

	/**
	 * 提交一个落子坐标。
	 *
	 * @param coord 落子坐标。
	 * @return 该落子是否被接受。如果智能体正在思考，或者游戏结束，该落子都不会被接受。
	 * @exception CoordOutOfBoardException 落子坐标超出棋盘范围。
	 * @exception CoordOccupiedException 落子位置已经有棋子存在。
	 */
	fun submit(coord: Coord): Boolean {
		if (coordIsNotInBoard(coord)) {
			throw CoordOutOfBoardException()
		}
		else if (gameBoard[coord.row][coord.col] != PieceColor.EMPTY) {
			throw CoordOccupiedException()
		}
		else if (gameStatus != GameStatus.DRAW || agentStatus != AgentStatus.WAITING_FOR_PLAYER) {
			return false // 当前状态不接受新的落子
		}

		// 记录新的落子。
		records.add(coord.clone())
		gameBoard[coord.row][coord.col] = when {
			records.size % 2 == 0 -> PieceColor.WHITE
			else -> PieceColor.BLACK
		}

		if (gameMode == GameMode.PVC) {
			// 更新分数。
			historyScore.add(reEvaluateBoardScore(gameBoard, agentPieceColor.reverse(), coord, historyScore.last()))
		}

		// 立即判断胜负
		if (updateGameStatus(coord) != GameStatus.DRAW) {
			return true
		}

		if (gameMode == GameMode.PVC) {
			// 令智能体进入睡眠状态。
			agentStatus = AgentStatus.SLEEPING
		}

		return true
	}

	/**
	 * 行棋思考。异步并发完成。
	 */
	fun thinkAsync(callable: ((Coord) -> Unit)? = null): Boolean = runBlocking {

		// pvp 模式叫 agent 想个头...
		if (gameMode == GameMode.PVP) {
			return@runBlocking false
		}

		// 先判断是否应该进入思考状态。如果需要，则立即设置状态。
		var shouldThink = false
		agentStatusMutex.withLock {
			if (agentStatus == AgentStatus.SLEEPING) {
				shouldThink = true
				agentStatus = AgentStatus.THINKING
			}
		}

		// 如果不该进入思考状态，返回 false
		if (!shouldThink) {
			return@runBlocking false
		}

		// 准备思考线程

		thinkThread = Thread {
			// 智能体思考过程

			val resultCoord = Coord(0, 0) // 结果存储。

			val searchThread = Thread {
				alphaBetaSearch(gameBoard.deepCopy(), historyScore.last(), SEARCH_DEPTH, resultCoord)
			}

			searchThread.start()
			searchThread.join()

			if (agentStatus == AgentStatus.THINKING) {
				// 更新游戏状态及思考状态
				agentStatus = AgentStatus.WAITING_FOR_PLAYER
				gameBoard[resultCoord.row][resultCoord.col] = agentPieceColor
				records.add(resultCoord.clone())
				updateGameStatus(resultCoord)
				historyScore.add(
					reEvaluateBoardScore(gameBoard, agentPieceColor, resultCoord, historyScore.last())
				)

				// 回调
				if (callable != null) {
					callable(resultCoord)
				}
			}
		} // Thread

		// 启动主思考线程
		thinkThread!!.start()

		// 返回”成功“
		return@runBlocking true
	}

	/**
	 * 行棋思考。内部计算过程同步并发完成。
	 * 调用时必须阻塞式调用，不可并发调用。若并发调用，可能产生未定义的行为。
	 * @return 智能体落子。如果为空，则表示没有可落子的位置，或内部出现异常。
	 */
	fun thinkSync(): Coord? = runBlocking {

		if (gameMode == GameMode.PVP) {
			return@runBlocking null
		}

		thinkAsync() // 启动异步思考过程。

		withContext(Dispatchers.IO) {
			thinkThread?.join() // 阻塞等待异步思考过程结束。
		}

		return@runBlocking when {
			records.isEmpty() -> null
			agentPieceColor == PieceColor.WHITE && records.size % 2 == 0 -> records.last().clone()
			agentPieceColor == PieceColor.BLACK && records.size % 2 == 1 -> records.last().clone()
			else -> null
		}
	}

	/**
	 * 棋子颜色反转。
	 * 反转规则：
	 * 黑 -> 白
	 * 白 -> 黑
	 * 空 -> 空
	 */
	fun PieceColor.reverse(): PieceColor {
		return when (this) {
			PieceColor.WHITE -> PieceColor.BLACK
			PieceColor.BLACK -> PieceColor.WHITE
			PieceColor.EMPTY -> PieceColor.EMPTY
		}
	}

	/*---------------- 内部成员 ----------------*/

	/**
	 * 主思考线程。非空不代表正在计算，有可能只是一个计算完毕的死线程。
	 */
	private var thinkThread: Thread? = null

	/*
	 * 特别注意：
	 *   下面两个成员分别表示游戏板和落子记录。不难发现，它们是紧密关联的。
	 *   任何涉及到对下面任意一个做修改的地方，必须对另一者同步修改！
	 */

	/** 游戏板。 */
	private val gameBoard = Array(builder.rows) { Array(builder.cols) { PieceColor.EMPTY } }

	/**
	 * 落子记录。
	 * 考虑到五子棋游戏总是黑白交替进行，可以简单地认为，下标为偶数的是黑棋，奇数的是白棋。
	 */
	private val records = ArrayList<Coord>()

	/**
	 * 分数历史记录。
	 */
	private val historyScore = ArrayList<Long>()

	/*---------------- 内部方法 ----------------*/

	private fun coordIsInBoard(coord: Coord): Boolean {
		return coord.col >= 0 && coord.row >= 0 && coord.row < rows && coord.col < cols
	}

	private fun coordIsNotInBoard(coord: Coord) = !coordIsInBoard(coord)

	/**
	 * 更新游戏胜负状态。如果游戏结束，会同步更新 agent 的状态为 finished.
	 * 该函数阻塞式运行，严禁在并发中调用。
	 * @param baseCoord 校验的基准点。之后，会以基准点展开，判断基准点附近是否形成某颜色连续五子的局面。
	 */
	private fun updateGameStatus(baseCoord: Coord): GameStatus {
		// 参数检查
		if (gameBoard[baseCoord.row][baseCoord.col] == PieceColor.EMPTY) {
			throw IllegalArgumentException()
		}

		gameStatus = GameStatus.DRAW // 先设置为平局。

		val directions = listOf(
			Coord(1, 0), // 竖直方向
			Coord(0, 1), // 水平方向
			Coord(1, 1), // 左上到右下
			Coord(1, -1) // 左下到右上
		)

		directions.forEach { direction ->

			val currDirection = direction.clone()
			var counter = 1 // 自己是1
			listOf(currDirection, -currDirection).forEach { lineDir ->
				val currentCoord = baseCoord + lineDir
				while (counter < 5 && coordIsInBoard(currentCoord)
					&& gameBoard[baseCoord.row][baseCoord.col] == gameBoard[currentCoord.row][currentCoord.col]
				) {
					counter += 1
					currentCoord += lineDir
				}
			}

			if (counter >= 5) {
				gameStatus =
					if (gameBoard[baseCoord.row][baseCoord.col] == PieceColor.BLACK) {
						GameStatus.BLACK_WIN
					} else {
						GameStatus.WHITE_WIN
					}

				agentStatus = AgentStatus.GAME_FINISHED // 标记为游戏结束。
			}


		}

		if (records.size == rows * cols) { // 如果棋盘已满，就做标记。
			agentStatus = AgentStatus.GAME_FINISHED
		}

		return gameStatus
	}

	/**
	 * 深拷贝。适用于克隆棋盘数据对象。
	 */
	private fun GameBoard.deepCopy(): GameBoard {
		return Array(this.size) { rowIdx -> Array(this[rowIdx].size) { colIdx -> this[rowIdx][colIdx] } }
	}

	/*---------------- 智能计算方法及辅助结构（内部使用） ----------------*/

	/**
	 * 连子类型定义。
	 */
	private data class LineType (
		/**
		 * 线上元素类型。
		 * 我们用整数表示元素类型。对应关系如下：
		 *     1 -> 己方
		 *     0 -> 空
		 *    -1 -> 对方
		 */
		val line: List<Int>,

		/** 赋分。 */
		val score: Long
	)

	/**
	 * 判断连子串是否是回文串。
	 */
	private fun LineType.isPalin(): Boolean {
		for (i in 0 until this.line.size) {
			if (this.line[i] != this.line[this.line.size - 1 - i]) {
				return false
			}
		}

		return false
	}

	/**
	 * 连线种类。
	 *
	 * 我们用整数表示连线元素类型。对应关系如下：
	 *     1 -> 己方
	 *     0 -> 空
	 *    -1 -> 对方
	 */
	private val lineTypes = listOf(
		// 连五
		LineType(listOf(1, 1, 1, 1, 1), 100000),
		// 活四
		LineType(listOf(0, 1, 1, 1, 1, 0), 10000),
		// 冲四
		LineType(listOf(-1, 1, 1, 1, 1, 0), 500),
		LineType(listOf(1, 1, 1, 0, 1), 500),
		LineType(listOf(1, 1, 0, 1, 1), 500),
		// 活三
		LineType(listOf(0, 1, 1, 1, 0), 200),
		LineType(listOf(0, 1, 0, 1, 1, 0), 200),
		// 眠三
		LineType(listOf(-1, 1, 1, 1, 0, 0), 50),
		LineType(listOf(-1, 1, 1, 0, 1, 0), 50),
		LineType(listOf(-1, 1, 0, 1, 1, 0), 50),
		LineType(listOf(1, 1, 0, 0, 1), 50),
		LineType(listOf(1, 0, 1, 0, 1), 50),
		LineType(listOf(-1, 0, 1, 1, 1, 0, -1), 50),
		// 活二
		LineType(listOf(0, 1, 0, 0, 1, 0), 5),
		LineType(listOf(0, 1, 1, 0, 0), 5),
		LineType(listOf(0, 1, 0, 1, 0), 5),
		// 眠二
		LineType(listOf(-1, 1, 1, 0, 0, 0), 3),
		LineType(listOf(-1, 1, 0, 1, 0, 0), 3),
		LineType(listOf(-1, 1, 0, 0, 1, 0), 3),
		LineType(listOf(1, 0, 0, 0, 1, 0), 3),
		LineType(listOf(-1, 0, 1, 0, 1, 0, -1), 3),
		LineType(listOf(-1, 0, 1, 1, 0, -1), 3),
		// 死四
		LineType(listOf(-1, 1, 1, 1, 1, -1), -5),
		// 死三
		LineType(listOf(-1, 1, 1, 1, -1), -5),
		// 死二
		LineType(listOf(-1, 1, 1, -1), -5)
	)

	/**
	 * 在中心的固定方向上寻找某连子类型。
	 * 取连子类型示例中表示己方的坐标作为“锚点”，检查是否匹配。
	 *
	 * @param refBoard 参考棋盘。
	 * @param center 寻找的中心。
	 * @param pieceColor 棋子颜色。
	 * @param lineType 连子类型。
	 * @param direction 方向。该方向会被双向延伸。
	 *
	 * @return 找到的条数。
	 */
	private fun searchLines(refBoard: GameBoard,
							center: Coord,
							pieceColor: PieceColor,
							lineType: LineType,
							direction: Coord
	): Int {
		var counter = 0

		run {
			listOf(direction, -direction).forEach { delta ->
				for (anchor in lineType.line.indices) {
					var match = true
					for (bias in (-anchor) until (lineType.line.size - anchor)) {
						// bias 表示连子类型相对于锚点的起始偏移。
						val nextCoord = center + delta * bias

						if (coordIsNotInBoard(nextCoord)) {
							match = false
							break
						}

						val colorMatch = when {
							refBoard[nextCoord.row][nextCoord.col] == PieceColor.EMPTY
							-> lineType.line[anchor + bias] == 0

							refBoard[nextCoord.row][nextCoord.col] == pieceColor
							-> lineType.line[anchor + bias] == 1

							refBoard[nextCoord.row][nextCoord.col] == pieceColor.reverse()
							-> lineType.line[anchor + bias] == -1

							else -> false
						}

						if (!colorMatch) {
							match = false
							break
						}

					} // for (bias in (-anchor) until (lineType.line.size - anchor))

					if (match) {
						counter += 1
					}

				} // for (anchor in lineType.line.indices)

				if (counter > 0) {
					return@run
				}
			} // listOf(direction, -direction).forEachIndexed { index, delta ->
		} // run

		return counter
	}

	/**
	 * 基于之前计算过的分数，重新评估棋盘分数。本次评估不会全盘评估，而是根据可能影响到的点重新计算。
	 *
	 */
	private fun reEvaluateBoardScore(refGameBoard: GameBoard, pieceColor: PieceColor,
									 updatedCoord: Coord, previousScore: Long
	): Long {
		// 参数校验。
		if (pieceColor == PieceColor.EMPTY) {
			throw IllegalArgumentException()
		}

		var result = previousScore

		val updatedCoordColorOnBoard = refGameBoard[updatedCoord.row][updatedCoord.col]
		refGameBoard[updatedCoord.row][updatedCoord.col] = PieceColor.EMPTY

		var selfScoreShift = 0L // 己方改变的分数。
		var opponentScoreShift = 0L // 对方改变的分数。

		for (side in -1..1 step 2) {
			// 先扣除原来点的分数，再加上新的。
			lineTypes.forEach { lineType -> // 枚举线种类
				listOf(
					Coord(1, 0), Coord(0, 1), Coord(1, 1), Coord(1, -1)
				).forEach { direction -> // 枚举四个大方向

					val selfCounter = searchLines(
						refGameBoard, updatedCoord, pieceColor, lineType, direction
					)

					if (selfCounter > 0) {
						selfScoreShift += selfCounter * lineType.score * side
					}


					val opponentCounter = searchLines(
						refGameBoard, updatedCoord, pieceColor.reverse(), lineType, direction
					)

					if (opponentCounter > 0) {
						opponentScoreShift += opponentCounter * lineType.score * side
					}

				}
			}

			refGameBoard[updatedCoord.row][updatedCoord.col] = pieceColor
		}

		result += when (pieceColor) {
			agentPieceColor -> selfScoreShift - opponentScoreShift
			else -> opponentScoreShift - selfScoreShift
		}

		// 增加落子位置奖励分数。
		result += min(
			min(updatedCoord.row, rows - 1 - updatedCoord.row),
			min(updatedCoord.col, cols - 1 - updatedCoord.col)
		) * when (pieceColor) {
			agentPieceColor -> 1
			else -> -1
		}

		// 恢复原始棋盘状态。如果传入时落子点为空，则我们在计算后不应让它保持为非空。
		refGameBoard[updatedCoord.row][updatedCoord.col] = updatedCoordColorOnBoard

		return result
	}

	/**
	 * 带 α-β 剪枝的极大极小搜索，是智能计算的核心部分。
	 *
	 * @param refBoard 参考游戏板。该游戏板会在思考过程中被不断改变，外部传入的必须是原始游戏板的深拷贝副本。
	 * @param lastSteps 之前的步骤记录。如果非空，则长度必须是 2. 其中，下标为 1 的元素是上一步的，下标为 0 的是更早一步的。
	 *                  外部调用时严禁设置此参数。
	 *
	 */
	private fun alphaBetaSearch(refBoard: GameBoard,
								boardScore: Long,
								remainingDepth: Int,
								resultCoordStore: Coord,
								isMaxLayer: Boolean = true, // 外部调用严禁设置此参数。
								alphaIn: Long = Long.MIN_VALUE, // 外部调用严禁设置此参数。
								betaIn: Long = Long.MAX_VALUE, // 外部调用严禁设置此参数。
								isEntry: Boolean = true, // 外部调用严禁设置此参数。
								lastSteps: List<Coord>? = null // 外部调用严禁设置此参数。
	): Long {

		// 直接结束的要求：已经到底或智能体被要求停止思考。
		if (remainingDepth == 0 || agentStatus != AgentStatus.THINKING) {
			return boardScore
		}

		// 将传入的 alpha 和 beta 做副本，后续对副本进行不断更新。
		var alpha = alphaIn
		var beta = betaIn

		var alphaBetaScore = if (isMaxLayer) {
			Long.MIN_VALUE
		} else {
			Long.MAX_VALUE
		}

		/*
		 * 考察每种可能的落子情况。
		 * 不单独写函数，实现判断剪枝后不再扩展，而不是全部扩展完再剪掉
		 * 从指定中间开始考虑，按左、上、右、下顺序螺旋向外
		 * 螺旋步长：c-1 r-1 c+1 c+2 r+1 r+2 c-1 c-2 c-3 ...
		 */

		val rolePieceColor = if (isMaxLayer) {
			agentPieceColor
		} else {
			agentPieceColor.reverse()
		}

		var r: Int
		var c: Int

		if (isEntry) {
			r = records.last().row
			c = records.last().col
		} else {
			r = lastSteps!![0].row
			c = lastSteps[0].col
		}

		// 旋转拓展中心。
		var centerR = r
		var centerC = c

		val searchMap = HashMap<Coord, Boolean>() // 用于记录每个点是否被搜到过。

		lastStepIdx@ for (lastStepIdx in 0..1) { // 从两个不同的中心开始拓展。
			var maxSteps = 1

			searching@ while (true) {

				plusMinus@ for (plusMinus in -1..1 step 2) {
					for (rc in 0..1) { // 两次，一次给c，一次给r
						for (steps in 1..maxSteps) {
							if (rc == 0) {
								c += plusMinus
							} else {
								r += plusMinus
							}

							val nextCoord = Coord(r, c)

							if (searchMap.keys.contains(nextCoord)) {
								continue // 不要重复搜索。
							} else {
								searchMap[nextCoord] = true // 标记搜索过了。
							}

							if (abs(centerR - r) > 6 && abs(centerC - c) > 6) {
								break@searching
							} else if (coordIsNotInBoard(nextCoord) || refBoard[r][c] != PieceColor.EMPTY) {
								continue
							}

							refBoard[r][c] = rolePieceColor

							val nextScore = alphaBetaSearch(
								refBoard,
								reEvaluateBoardScore(refBoard, rolePieceColor, nextCoord, boardScore),
								remainingDepth - 1,
								resultCoordStore,
								!isMaxLayer,
								alpha,
								beta,
								false,
								when {
									isEntry -> listOf(records.last(), nextCoord)
									else -> listOf(lastSteps!![1], nextCoord)
								}
							)

							alphaBetaScore = if (isMaxLayer) {
								max(alphaBetaScore, nextScore)
							} else {
								min(alphaBetaScore, nextScore)
							}

							refBoard[r][c] = PieceColor.EMPTY

							// α-β 剪枝。
							if ((isMaxLayer && alphaBetaScore >= beta) || (!isMaxLayer && alphaBetaScore <= alpha)) {
								return alphaBetaScore
							}

							// 记录最佳落子。
							if (isEntry && isMaxLayer && alphaBetaScore > alpha) {
								resultCoordStore.row = r
								resultCoordStore.col = c
							}

							// 更新 alpha 或 beta 的值。
							if (isMaxLayer) {
								alpha = max(alpha, alphaBetaScore)
							} else {
								beta = min(beta, alphaBetaScore)
							}
						} // for (steps in 1 .. maxSteps)
					} // for (rc in 0 .. 1) { // 两次，一次给c，一次给r

					maxSteps += 1
				} // for (plusMinus in -1 .. 1 step 2)
			} // while(searching)

			// 转换到下一个坐标。
			if (isEntry && records.size >= 2) {
				r = records[records.size - 2].row
				c = records[records.size - 2].col
				centerR = r
				centerC = c
			} else if (!isEntry) { // 如果不是表层，lastSteps 必须非空并且大小为 2.
				r = lastSteps!![1].row
				c = lastSteps[1].col
				centerR = r
				centerC = c
			} else {
				break@lastStepIdx
			}

		} // for (lastStepIdx in 0 .. 1)

		return alphaBetaScore
	}

	/*---------------- 伴随对象 (companion object) ----------------*/
	companion object {
		/** alpha-beta 搜索深度。 */
		private const val SEARCH_DEPTH = 2
	}

	/*---------------- 构造函数（内部） ----------------*/

	/**
	 * 对象构造函数。
	 */
	init {
		historyScore.add(0L)

		if (agentPieceColor == PieceColor.BLACK && gameMode == GameMode.PVC) { // 如果智能体是黑棋，就走中心位置。
			val coord = Coord(rows / 2, cols / 2)
			records.add(coord)
			gameBoard[coord.row][coord.col] = agentPieceColor

			historyScore.add(
				reEvaluateBoardScore(gameBoard, agentPieceColor, coord, historyScore.last())
			)
		}
	}
}
