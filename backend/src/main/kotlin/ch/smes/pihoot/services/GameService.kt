package ch.smes.pihoot.services

import ch.smes.pihoot.exceptions.BadRequestException
import ch.smes.pihoot.exceptions.NotFoundException
import ch.smes.pihoot.models.*
import ch.smes.pihoot.repositories.GameRepository
import ch.smes.pihoot.utils.IdUtils
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service


@Service
class GameService {

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var quizService: QuizService

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

    fun createGame(quizId: String) = gameRepository.save(Game(
            quiz = quizService.getOne(quizId),
            colorCode = generateSequence { AnswerColor.values().random() }.take(8).toList()
    ))

    fun updateGameState(gameId: String, newState: GameState) {
        val game = getOne(gameId)

        game.state = newState

        saveOrUpdate(game)
    }

    fun updateQuestionState(gameId: String, questionId: String, newState: QuestionState) {
        mongoTemplate.updateFirst(
                Query(where("_id").`is`(ObjectId(gameId)).and("quiz.questions._id").`is`(questionId)),
                Update().set("quiz.questions.$.state", newState),
                MutableCollection::class.java
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
                PlayerColor.values().filter { game.players.any { player -> player.color == it } }.random()
        )

        game.players.add(newPlayer)

        gameRepository.save(game)

        return newPlayer
    }
}