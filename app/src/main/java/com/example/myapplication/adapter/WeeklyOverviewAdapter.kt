package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class WeeklyOverviewAdapter(
    private var weeklyData: List<DayData>,
    private val onDateClick: (String) -> Unit
) : RecyclerView.Adapter<WeeklyOverviewAdapter.WeekViewHolder>() {

    private var selectedDate: String = ""

    data class DayData(
        val date: String,
        val displayDate: String,
        val mealCount: Int,
        val hasBreakfast: Boolean,
        val hasLunch: Boolean,
        val hasDinner: Boolean
    )

    class WeekViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.week_day_card)
        val dateText: TextView = view.findViewById(R.id.week_day_date)
        val mealCountText: TextView = view.findViewById(R.id.week_day_meal_count)
        val breakfastIndicator: View = view.findViewById(R.id.breakfast_indicator)
        val lunchIndicator: View = view.findViewById(R.id.lunch_indicator)
        val dinnerIndicator: View = view.findViewById(R.id.dinner_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weekly_overview, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val dayData = weeklyData[position]

        holder.dateText.text = dayData.displayDate
        holder.mealCountText.text = "${dayData.mealCount} meals"

        // Show/hide meal indicators
        holder.breakfastIndicator.visibility = if (dayData.hasBreakfast) View.VISIBLE else View.INVISIBLE
        holder.lunchIndicator.visibility = if (dayData.hasLunch) View.VISIBLE else View.INVISIBLE
        holder.dinnerIndicator.visibility = if (dayData.hasDinner) View.VISIBLE else View.INVISIBLE

        // Highlight selected date
        if (dayData.date == selectedDate) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE)
        }

        holder.cardView.setOnClickListener {
            onDateClick(dayData.date)
        }
    }

    override fun getItemCount() = weeklyData.size

    fun updateWeeklyData(newData: List<DayData>, currentDate: String) {
        weeklyData = newData
        selectedDate = currentDate
        notifyDataSetChanged()
    }
    fun updateSelectedDate(date: String) {
        selectedDate = date
        notifyDataSetChanged()
    }
}