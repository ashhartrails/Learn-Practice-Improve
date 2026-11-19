package com.example.learnanything.core.ui.question

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import com.example.learnanything.core.model.question.Question
import com.example.learnanything.core.ui.text.RichText

/** The input for one question. Controlled: the caller holds [answer]. */
@Composable
fun QuestionInput(
    question: Question,
    answer: Answer?,
    onAnswerChange: (Answer) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RichText(question.prompt, style = MaterialTheme.typography.titleMedium)
        when (question) {
            is Question.SingleChoice -> {
                val selected = (answer as? Answer.Choice)?.selected.orEmpty()
                question.options.forEachIndexed { i, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = i in selected, enabled = enabled, role = Role.RadioButton) {
                                onAnswerChange(Answer.Choice(setOf(i)))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i in selected, onClick = null, enabled = enabled)
                        RichText(option, Modifier.padding(start = 8.dp))
                    }
                }
            }
            is Question.MultiChoice -> {
                val selected = (answer as? Answer.Choice)?.selected.orEmpty()
                Text("Select all that apply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                question.options.forEachIndexed { i, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = i in selected, enabled = enabled, role = Role.Checkbox) { on ->
                                onAnswerChange(Answer.Choice(if (on) selected + i else selected - i))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = i in selected, onCheckedChange = null, enabled = enabled)
                        RichText(option, Modifier.padding(start = 8.dp))
                    }
                }
            }
            is Question.Numeric -> OutlinedTextField(
                value = (answer as? Answer.Number)?.text.orEmpty(),
                onValueChange = { onAnswerChange(Answer.Number(it.take(40))) },
                label = { Text("Your answer") },
                suffix = question.unit?.let { { Text(it) } },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            is Question.ShortText -> OutlinedTextField(
                value = (answer as? Answer.Text)?.text.orEmpty(),
                onValueChange = { onAnswerChange(Answer.Text(it.take(100))) },
                label = { Text("Your answer") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun Answer?.isFilled(): Boolean = when (this) {
    null -> false
    is Answer.Choice -> selected.isNotEmpty()
    is Answer.Number -> text.isNotBlank()
    is Answer.Text -> text.isNotBlank()
}

/** A question with a Check button and, once graded, its feedback. */
@Composable
fun QuestionCard(
    question: Question,
    answer: Answer?,
    result: GradeResult?,
    onAnswerChange: (Answer) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            QuestionInput(question, answer, onAnswerChange, enabled = result == null)
            if (result == null) {
                Button(onClick = onCheck, enabled = answer.isFilled(), modifier = Modifier.align(Alignment.End)) { Text("Check") }
            } else {
                Feedback(result)
            }
        }
    }
}

@Composable
fun Feedback(result: GradeResult, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.correct) colors.primaryContainer else colors.errorContainer,
            contentColor = if (result.correct) colors.onPrimaryContainer else colors.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (result.correct) Icons.Default.Check else Icons.Default.Close, contentDescription = null)
                Text(
                    if (result.correct) "Correct" else "Not quite",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                TrustLabel()
            }
            if (!result.correct) Text("Answer: ${result.expected}", style = MaterialTheme.typography.bodyMedium)
            RichText(result.explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Shown on every grade. M1 grades are all checked by code ("Exact"); AI-graded arrives in M2. */
@Composable
fun TrustLabel(aiGraded: Boolean = false) {
    AssistChip(
        onClick = {},
        label = { Text(if (aiGraded) "AI-graded" else "Exact") },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
