package com.voicehabit.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.presentation.theme.*
import kotlinx.coroutines.launch

class DuroWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result CANCELED by default in case user backs out
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(applicationContext)
        val habitRepo = HabitRepositoryImpl(db.habitDao())

        setContent {
            var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                habits = habitRepo.getAllHabitsList()
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DuroBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DuroAsterisk(size = 22.dp, color = DuroOrange)
                        Text(
                            text = "Duro Habit Card",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select Habit to assign to this Widget:",
                        fontSize = 13.sp,
                        color = DuroTextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (habits.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Нет созданных привычек", color = DuroTextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(habits) { habit ->
                                val cardColor = try {
                                    Color(android.graphics.Color.parseColor(habit.colorHex))
                                } catch (e: Exception) {
                                    DuroOrange
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(1.dp, DuroBorder, RoundedCornerShape(18.dp))
                                        .clickable {
                                            saveHabitForWidget(this@DuroWidgetConfigureActivity, appWidgetId, habit.id)
                                            val appWidgetManager = AppWidgetManager.getInstance(this@DuroWidgetConfigureActivity)
                                            DuroHabitCardWidgetProvider.updateSingleWidget(
                                                this@DuroWidgetConfigureActivity,
                                                appWidgetManager,
                                                appWidgetId
                                            )

                                            val resultValue = Intent().apply {
                                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            }
                                            setResult(RESULT_OK, resultValue)
                                            finish()
                                        },
                                    color = DuroSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = habit.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DuroTextPrimary
                                            )
                                            Text(
                                                text = "${habit.category} • ${habit.displayType} • ${habit.currentStreak} дн.",
                                                fontSize = 12.sp,
                                                color = DuroTextSecondary
                                            )
                                        }

                                        Text(
                                            text = "Выбрать",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DuroOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "duro_widget_prefs"
        private const val PREF_PREFIX_KEY = "widget_habit_"

        fun saveHabitForWidget(context: Context, appWidgetId: Int, habitId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_PREFIX_KEY + appWidgetId, habitId).apply()
        }

        fun loadHabitIdForWidget(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        fun deleteWidgetPref(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_PREFIX_KEY + appWidgetId).apply()
        }
    }
}
