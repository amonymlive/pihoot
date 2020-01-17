package ch.smes.pihoot.services

import ch.smes.pihoot.exceptions.BadRequestException
import ch.smes.pihoot.exceptions.InternalServerErrorException
import ch.smes.pihoot.exceptions.NotFoundException
import ch.smes.pihoot.models.*
import ch.smes.pihoot.repositories.GameRepository
import ch.smes.pihoot.utils.ColorUtils
import ch.smes.pihoot.utils.IdUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant


@Service
class GameService {

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var quizService: QuizService

    @Autowired
    private lateinit var websocketService: WebsocketService

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    fun getOne(gameId: String): Game {
        val game = gameRepository.findById(gameId)

        if (game.isEmpty) {
            throw NotFoundException("Game with id $gameId could not be found")
        }

        return game.get()
    }

    fun getQueueingGames(): List<Game> = gameRepository.findAllByStateIs(GameState.QUEUING)

    fun getPlayersOfGame(gameId: String): List<Player> = getOne(gameId).players

    fun getScore(gameId: String) = getOne(gameId).players.map { it.id!! to it.score }.toMap()

    fun checkAnswerAndUpdateScore(gameId: String, playerId: String, answer: AnswerColor): Boolean {
        val game = getOne(gameId)
        val player = game.players.find { it.id == playerId }
                ?: throw NotFoundException("Player {$playerId} could not be found in game {$gameId}")
        val question = game.quiz?.questions?.find { it.state == QuestionState.IN_PROGRESS }
                ?: throw InternalServerErrorException("There is currently no question in progress for game {$gameId}")
        val isCorrect = question.answers.find { it.color == answer }?.isCorrect ?: false

        question.answerCount += 1

        websocketService.updateAnswerCountForGame(gameId, question.answerCount);

        if (isCorrect) {
            val score = (Instant.now().toEpochMilli() - question.beginTimestamp!!.toEpochMilli()) * -(1/35L) + (7100/7L)

            player.score += when {
                score > 1000 -> 1000
                score < 600 -> 600
                else -> score.toInt()
            }

            saveOrUpdate(game)
        }

        return isCorrect
    }

    fun createGame(quizId: String) = gameRepository.save(Game(
            quiz = quizService.getOne(quizId).also { quiz ->
                quiz.questions.shuffle()
                quiz.questions.forEach {
                    it.answers.shuffle()
                    ColorUtils.assignColorToAnswers(it.answers)
                }
            },
            colorCode = generateSequence { AnswerColor.values().random() }.take(8).toList()
    ))

    fun updateGameState(gameId: String, newState: GameState) {
        val game = getOne(gameId)

        game.state = newState

        saveOrUpdate(game)
    }

    fun updateQuestionState(gameId: String, questionId: String, newState: QuestionState) {
        mongoTemplate.updateFirst(
                Query(where("_id").`is`(gameId).and("quiz.questions._id").`is`(questionId)),
                Update().set("quiz.questions.$.state", newState),
                Game::class.java
        )
    }

    fun saveOrUpdate(game: Game): Game = gameRepository.save(game)

    fun addPlayer(gameId: String): Player {
        val game = getOne(gameId)

        if (game.state != GameState.QUEUING) {
            throw BadRequestException("Game must be queueing to be joinable. Current game state: ${game.state}")
        }

        val newPlayer = Player(
                IdUtils.generateId(),
                PlayerColor.values().filter { game.players.none { player -> player.color == it } }.random()
        )

        game.players.add(newPlayer)

        gameRepository.save(game)

        return newPlayer
    }
}