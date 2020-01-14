package ch.smes.pihoot.utils

import ch.smes.pihoot.models.Answer
import ch.smes.pihoot.models.AnswerColor

object ColorUtils {

    fun assignColorToAnswers(answers: MutableList<Answer>) {
        val colors = AnswerColor.values().asList().shuffled()

        answers.forEachIndexed { index, answer -> answer.color = colors[index] }
    }
}