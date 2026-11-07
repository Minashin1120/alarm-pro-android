package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuizOptionCard
import com.example.math.MathProblem
import com.example.math.MathProblemGenerator
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CrimsonAlarm
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathPracticeScreen(
  onBack: () -> Unit
) {
  var selectedDifficulty by remember { mutableStateOf("MEDIUM") }
  var problem by remember { mutableStateOf(MathProblemGenerator.generate(selectedDifficulty)) }
  var correctCount by remember { mutableIntStateOf(0) }
  var totalCount by remember { mutableIntStateOf(0) }
  var feedbackMessage by remember { mutableStateOf<String?>(null) }
  var isCorrectFeedback by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "計算脳トレ・練習モード",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Slate100
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("practice_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "戻る",
              tint = Slate100
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              problem = MathProblemGenerator.generate(selectedDifficulty)
              feedbackMessage = null
            }
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "新しい問題",
              tint = AmberPrimary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
      )
    },
    containerColor = Slate950
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Difficulty Selector Rows
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("EASY" to "簡単", "MEDIUM" to "普通", "HARD" to "難しい").forEach { (key, label) ->
            val isSelected = selectedDifficulty == key
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) AmberPrimary else Slate900)
                .border(1.dp, if (isSelected) AmberPrimary else Slate700, RoundedCornerShape(10.dp))
                .clickable {
                  selectedDifficulty = key
                  problem = MathProblemGenerator.generate(key)
                  feedbackMessage = null
                }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Slate950 else Slate200
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("EXPERT" to "超難問", "NIGHTMARE" to "鬼畜・極限").forEach { (key, label) ->
            val isSelected = selectedDifficulty == key
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) AmberPrimary else Slate900)
                .border(1.dp, if (isSelected) AmberPrimary else Slate700, RoundedCornerShape(10.dp))
                .clickable {
                  selectedDifficulty = key
                  problem = MathProblemGenerator.generate(key)
                  feedbackMessage = null
                }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Slate950 else Slate200
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Stats Banner
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(Slate900)
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "練習スコア",
          fontSize = 13.sp,
          color = Slate400
        )
        Text(
          text = "正解数: $correctCount / $totalCount 問",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = EmeraldSuccess
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Math Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, AmberPrimary.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = problem.questionText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Slate100,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(12.dp))

          AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            feedbackMessage?.let { msg ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isCorrectFeedback) EmeraldDark else CrimsonAlarm.copy(alpha = 0.3f))
                  .padding(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text(
                  text = msg,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isCorrectFeedback) EmeraldSuccess else Color.White
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // 4 Choice Options
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        for (row in 0..1) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            for (col in 0..1) {
              val index = row * 2 + col
              val optionValue = problem.options.getOrNull(index) ?: 0
              QuizOptionCard(
                modifier = Modifier
                  .weight(1f)
                  .height(70.dp),
                value = optionValue,
                onClick = {
                  totalCount++
                  if (optionValue == problem.correctAnswer) {
                    correctCount++
                    isCorrectFeedback = true
                    feedbackMessage = "正解！素晴らしい！"
                  } else {
                    isCorrectFeedback = false
                    feedbackMessage = "不正解！正解は ${problem.correctAnswer}"
                  }
                  problem = MathProblemGenerator.generate(selectedDifficulty)
                }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Text(
        text = "アラーム鳴動時と同じ問題が出題されます。\n事前に解いて難易度を確かめておきましょう。",
        fontSize = 12.sp,
        color = Slate400,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
        modifier = Modifier.padding(bottom = 12.dp)
      )
    }
  }
}
