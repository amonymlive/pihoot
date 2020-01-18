package ch.smes.pihoot.controllers

import ch.smes.pihoot.dtos.PlayerDTO
import ch.smes.pihoot.mappers.PlayerMapper
import ch.smes.pihoot.models.AnswerColor
import ch.smes.pihoot.models.GameState
import ch.smes.pihoot.models.QuestionState
import ch.smes.pihoot.services.GameService
import ch.smes.pihoot.services.WebsocketService
import ch.smes.pihoot.services.ZMQPubService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

/**
 * Controller for any game related HTTP request
 */
@RestController
@RequestMapping("/api/game")
class GameController {

    @Autowired
    private lateinit var gameService: GameService

    @Autowired
    private lateinit var websocketService: WebsocketService

    @Autowired
    private lateinit var zmqPubService: ZMQPubService

    @Autowired
    private lateinit var playerMapper: PlayerMapper

    /**
     * Start a queueing game
     *
     * Once a game has been created, it must be started
     */
    @PostMapping("/{gameId}/start")
    fun startGame(@PathVariable gameId: String) {
        gameService.updateGameState(gameId, GameState.IN_GAME)

        zmqPubService.updateQueueingGames()
    }

    /**
     * End a game
     *
     * Once a game has finished, it must be ended
     */
    @PostMapping("/{gameId}/end")
    fun endGame(@PathVariable gameId: String) {
        gameService.updateGameState(gameId, GameState.ENDED)
    }

    /**
     * Begin a question for a certain game
     *
     * The Raspberry Pi must be notified, when it can start taking and sending answers
     */
    @PostMapping("/{gameId}/question/{questionId}/begin")
    fun beginQuestion(@PathVariable gameId: String, @PathVariable questionId: String) {
        gameService.updateQuestionState(gameId, questionId, QuestionState.IN_PROGRESS)

        val game = gameService.getOne(gameId)

        zmqPubService.beginQuestion(gameId, game.quiz
                ?.questions
                ?.find { it.id == questionId }
                ?.answers
                ?.map { it.color }
                ?.requireNoNulls()
                .orEmpty())
    }

    /**
     * End a question for a certain game
     *
     * The Raspberry Pi must be notified, when it can stop taking and sending answers
     */
    @PostMapping("/{gameId}/question/{questionId}/end")
    fun endQuestion(@PathVariable gameId: String, @PathVariable questionId: String) {
        gameService.updateQuestionState(gameId, questionId, QuestionState.ENDED)

        zmqPubService.endQuestion(gameId)
    }

    /**
     * Join a game as a player
     *
     * A Raspberry Pi must first join a game to participate in it
     */
    @PostMapping("/{gameId}/join")
    fun joinGame(@PathVariable gameId: String): PlayerDTO {
        val newPlayer = gameService.addPlayer(gameId)

        websocketService.updatePlayersForGame(gameId)

        return playerMapper.toDto(newPlayer)
    }

    /**
     * Answer the current running question for a game
     */
    @PostMapping("/{gameId}/answer/{playerId}")
    fun answerQuestion(
            @RequestBody answer: AnswerColor,
            @PathVariable gameId: String,
            @PathVariable playerId: String
    ): Boolean = gameService.checkAnswerAndUpdateScore(gameId, playerId, answer)

    /**
     * Retrieve the score for each player in a game
     */
    @PostMapping("/{gameId}/score")
    fun getScore(@PathVariable gameId: String): Map<String, Int> = gameService.getScore(gameId)
}