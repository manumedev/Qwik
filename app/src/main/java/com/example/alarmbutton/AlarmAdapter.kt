package com.example.alarmbutton

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AlarmAdapter(
    private val onDismiss: (AlarmInfo) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    private var alarms: List<AlarmInfo> = emptyList()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.alarmTime)
        val dismissButton: Button = view.findViewById(R.id.dismissButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.timeText.text = timeFormat.format(alarm.time.time)
        holder.dismissButton.setOnClickListener { onDismiss(alarm) }
    }

    override fun getItemCount() = alarms.size

    fun updateAlarms(newAlarms: List<AlarmInfo>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }
} 