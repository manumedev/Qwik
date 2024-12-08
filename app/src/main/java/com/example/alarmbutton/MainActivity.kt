package com.example.alarmbutton

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.util.*
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.app.ActivityManager

class MainActivity : AppCompatActivity() {
    private lateinit var buttonContainer: LinearLayout
    private lateinit var unitContainer: LinearLayout
    private lateinit var alarmsRecycler: RecyclerView
    private lateinit var alarmAdapter: AlarmAdapter
    private val qwikAlarms = mutableListOf<AlarmInfo>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var isFirstDismissAfterLaunch = true
    
    private enum class TimeUnit(val multiplier: Int) {
        MINUTE(1),
        HOUR(60);
        
        companion object {
            fun fromString(value: String) = when(value) {
                "HOUR" -> HOUR
                else -> MINUTE
            }
        }
    }
    
    private var currentUnit = TimeUnit.MINUTE
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFirstDismissAfterLaunch = true
        setContentView(R.layout.activity_main)
        
        buttonContainer = findViewById(R.id.buttonContainer)
        unitContainer = findViewById(R.id.unitContainer)
        
        // Load saved unit preference
        val prefs = getSharedPreferences("QwikPrefs", MODE_PRIVATE)
        currentUnit = TimeUnit.fromString(prefs.getString("unit", "MINUTE") ?: "MINUTE")
        
        createUnitButtons()
        createAlarmButtons()
        
        alarmsRecycler = findViewById(R.id.activeAlarmsRecycler)
        alarmsRecycler.layoutManager = LinearLayoutManager(this)
        alarmAdapter = AlarmAdapter { calendar ->
            dismissAlarm(calendar)
        }
        alarmsRecycler.adapter = alarmAdapter
        
        // Initial load of alarms
        loadAlarms()
    }

    private fun createUnitButtons() {
        val units = listOf("Min", "Hour")
        units.forEach { unit ->
            Button(this).apply {
                text = unit
                background = ContextCompat.getDrawable(context, R.drawable.unit_button_background)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                isSelected = when(unit) {
                    "Hour" -> currentUnit == TimeUnit.HOUR
                    else -> currentUnit == TimeUnit.MINUTE
                }
                setBackgroundColor(ContextCompat.getColor(context, 
                    if (isSelected) R.color.unitButtonSelected 
                    else R.color.unitButtonUnselected
                ))
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(8, 4, 8, 4)
                }

                setOnClickListener {
                    for (i in 0 until unitContainer.childCount) {
                        (unitContainer.getChildAt(i) as Button).apply { 
                            isSelected = false
                            setBackgroundColor(ContextCompat.getColor(context, R.color.unitButtonUnselected))
                        }
                    }
                    isSelected = true
                    setBackgroundColor(ContextCompat.getColor(context, R.color.unitButtonSelected))
                    currentUnit = when(unit) {
                        "Hour" -> TimeUnit.HOUR
                        else -> TimeUnit.MINUTE
                    }
                    // Save unit preference
                    getSharedPreferences("QwikPrefs", MODE_PRIVATE)
                        .edit()
                        .putString("unit", currentUnit.name)
                        .apply()
                    createAlarmButtons()
                }
                unitContainer.addView(this)
            }
        }
    }

    private fun createAlarmButtons() {
        buttonContainer.removeAllViews()
        var rowLayout: LinearLayout? = null
        
        val increments = when(currentUnit) {
            TimeUnit.MINUTE -> (5..60 step 5).toList() // 12 buttons: 5 to 60 by 5
            TimeUnit.HOUR -> (1..12).toList() // 12 buttons: 1 to 12 by 1
        }
        
        increments.forEachIndexed { index, value ->
            if (index % 2 == 0) {
                rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 16, 0, 16) // Increased vertical spacing
                    }
                }
                buttonContainer.addView(rowLayout)
            }

            Button(this).apply {
                text = "+$value"
                background = ContextCompat.getDrawable(context, R.drawable.alarm_button_background)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 16f // Make text bigger
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(16, 0, 16, 0) // Increased horizontal spacing
                    height = resources.getDimensionPixelSize(R.dimen.alarm_button_height)
                }

                setOnClickListener {
                    setAlarm(value * currentUnit.multiplier)
                    showSnackbar(value, currentUnit)
                }
                rowLayout?.addView(this)
            }
        }
    }

    private fun showSnackbar(value: Int, unit: TimeUnit) {
        val unitText = when(unit) {
            TimeUnit.HOUR -> if (value == 1) "hour" else "hours"
            TimeUnit.MINUTE -> if (value == 1) "minute" else "minutes"
        }
        Snackbar.make(
            buttonContainer,
            "Alarm set for $value $unitText",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun setAlarm(minutes: Int) {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
        }

        val alarmId = System.currentTimeMillis().toInt()

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Qwik Alarm")
        }

        try {
            startActivity(intent)
            // Try to find the created alarm's URI
            val alarmUri = findCreatedAlarmUri(calendar)
            
            // Add to our list with URI
            val alarmInfo = AlarmInfo(
                alarmId.toLong(),
                calendar,
                alarmUri
            )
            qwikAlarms.add(alarmInfo)
            updateActiveAlarms()
            saveAlarms()
        } catch (e: Exception) {
            Snackbar.make(
                buttonContainer,
                "No alarm app found",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun findCreatedAlarmUri(calendar: Calendar): String? {
        val uris = listOf(
            "content://com.android.deskclock/alarm",
            "content://com.android.alarm/alarm",
            "content://com.google.android.deskclock/alarm"
        )

        // Get time range (±30 seconds)
        val startTime = calendar.clone() as Calendar
        val endTime = calendar.clone() as Calendar
        startTime.add(Calendar.SECOND, -30)
        endTime.add(Calendar.SECOND, 30)

        for (uriString in uris) {
            try {
                val uri = Uri.parse(uriString)
                val cursor = contentResolver.query(
                    uri,
                    arrayOf("_id", "hour", "minutes"),
                    null,  // Get all alarms and filter in code
                    null,
                    "_id DESC"
                )

                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(c.getColumnIndexOrThrow("_id"))
                        val hour = c.getInt(c.getColumnIndexOrThrow("hour"))
                        val minute = c.getInt(c.getColumnIndexOrThrow("minutes"))

                        // Create a calendar for comparison
                        val alarmTime = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }

                        // Check if alarm is within our time range
                        if (alarmTime.after(startTime) && alarmTime.before(endTime)) {
                            Log.d("QwikAlarm", "Found matching alarm with id: $id for time ${timeFormat.format(calendar.time)}")
                            return "$uriString/$id"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("QwikAlarm", "Error finding alarm URI", e)
            }
        }
        return null
    }

    private fun dismissAlarm(alarmInfo: AlarmInfo) {
        val systemDismissSuccessful = tryDismissWithContentProvider(alarmInfo)

        if (systemDismissSuccessful) {
            qwikAlarms.remove(alarmInfo)
            updateActiveAlarms()
            saveAlarms()
            
            Snackbar.make(
                buttonContainer,
                "Alarm dismissed",
                Snackbar.LENGTH_SHORT
            ).show()
        } else {
            Snackbar.make(
                buttonContainer,
                "Could not dismiss alarm",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun tryDismissWithContentProvider(alarmInfo: AlarmInfo): Boolean {
        try {
            val hour = alarmInfo.time.get(Calendar.HOUR_OF_DAY)
            val minute = alarmInfo.time.get(Calendar.MINUTE)

            Log.d("QwikAlarm", "Trying to dismiss alarm at $hour:$minute")

            // Dismiss alarm and return to Qwik
            val dismissed = dismissAndReturnToQwik(hour, minute)
            if (!dismissed) return false

            // Verify if alarm was dismissed by checking content provider
            val uris = listOf(
                "content://com.android.deskclock/alarm",
                "content://com.android.alarm/alarm",
                "content://com.google.android.deskclock/alarm"
            )

            for (uriString in uris) {
                try {
                    val uri = Uri.parse(uriString)
                    val cursor = contentResolver.query(
                        uri,
                        arrayOf("_id", "enabled"),
                        "hour = ? AND minutes = ?",
                        arrayOf(hour.toString(), minute.toString()),
                        null
                    )

                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val enabled = c.getInt(c.getColumnIndexOrThrow("enabled"))
                            if (enabled == 0) {
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QwikAlarm", "Error checking alarm status with URI $uriString", e)
                }
            }

            return true // Return true even if we couldn't verify, as we tried to dismiss
        } catch (e: Exception) {
            Log.e("QwikAlarm", "Error dismissing alarm", e)
            return false
        }
    }

    private fun updateActiveAlarms() {
        val now = Calendar.getInstance()
        val twelveHoursLater = Calendar.getInstance().apply {
            add(Calendar.HOUR, 12)
        }
        
        // Filter alarms within next 12 hours and not passed
        val activeAlarms = qwikAlarms
            .filter { alarm ->
                alarm.time.after(now) && alarm.time.before(twelveHoursLater)
            }
            .sortedBy { it.time.timeInMillis }
        
        alarmAdapter.updateAlarms(activeAlarms)
    }

    private fun saveAlarms() {
        val prefs = getSharedPreferences("QwikPrefs", MODE_PRIVATE)
        val alarmSet = qwikAlarms.map { alarm ->
            "${alarm.id},${alarm.time.timeInMillis},${alarm.alarmUri ?: ""}"
        }.toSet()
        
        prefs.edit()
            .putStringSet("alarms", alarmSet)
            .apply()
    }

    private fun loadAlarms() {
        val prefs = getSharedPreferences("QwikPrefs", MODE_PRIVATE)
        val alarmSet = prefs.getStringSet("alarms", emptySet()) ?: emptySet()
        
        qwikAlarms.clear()
        alarmSet.forEach { alarmString ->
            try {
                val parts = alarmString.split(",")
                val id = parts[0].toLong()
                val time = parts[1].toLong()
                val uri = if (parts.size > 2) parts[2] else null
                
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = time
                }
                qwikAlarms.add(AlarmInfo(id, calendar, uri))
            } catch (e: Exception) {
                Log.e("QwikAlarm", "Error loading alarm: $alarmString", e)
            }
        }
        updateActiveAlarms()
    }

    override fun onResume() {
        super.onResume()
        // Refresh alarms when app comes to foreground
        updateActiveAlarms()
    }

    override fun onPause() {
        super.onPause()
        saveAlarms() // Save alarms when app goes to background
    }

    private fun dismissAndReturnToQwik(hour: Int, minute: Int): Boolean {
        try {
            // Try to delete using system's DELETE_ALARM action
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(intent)

            // Give system time to process the dismiss
            Thread.sleep(500)

            // Return to Qwik
            Handler(Looper.getMainLooper()).postDelayed({
                if (isFirstDismissAfterLaunch) {
                    // Special handling for first dismiss
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        addCategory(Intent.CATEGORY_HOME)
                        action = Intent.ACTION_MAIN
                    }
                    finishAffinity() // Close all activities including system clock
                    startActivity(launchIntent)
                    updateActiveAlarms()
                    isFirstDismissAfterLaunch = false
                } else {
                    // Keep the working code for subsequent dismisses
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        addCategory(Intent.CATEGORY_HOME)
                        action = Intent.ACTION_MAIN
                    }
                    finishAffinity()
                    startActivity(launchIntent)
                    updateActiveAlarms()
                }
            }, 1000)

            return true
        } catch (e: Exception) {
            Log.e("QwikAlarm", "Error in dismissAndReturnToQwik", e)
            return false
        }
    }
}

data class AlarmInfo(
    val id: Long,
    val time: Calendar,
    val alarmUri: String? = null
) 