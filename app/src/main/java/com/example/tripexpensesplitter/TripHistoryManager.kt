package com.example.tripexpensesplitter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TripHistoryItem(
    val id: Long,
    val title: String,
    val date: String,
    val totalAmount: Double,
    val memberCount: Int,
    val settlements: List<String>
)

class TripHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("TripHistoryPrefs", Context.MODE_PRIVATE)

    fun saveTrip(item: TripHistoryItem) {
        val currentList = getHistory().toMutableList()
        // If already saved with this ID, replace it; otherwise add to top
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
        } else {
            currentList.add(0, item)
        }
        persistList(currentList)
    }

    fun deleteTrip(tripId: Long) {
        val currentList = getHistory().toMutableList()
        currentList.removeAll { it.id == tripId }
        persistList(currentList)
    }

    fun clearAllHistory() {
        prefs.edit().remove("history_json").apply()
    }

    fun getHistory(): List<TripHistoryItem> {
        val jsonString = prefs.getString("history_json", null) ?: return emptyList()
        val list = mutableListOf<TripHistoryItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val settlementsArray = obj.getJSONArray("settlements")
                val settlements = mutableListOf<String>()
                for (j in 0 until settlementsArray.length()) {
                    settlements.add(settlementsArray.getString(j))
                }
                list.add(
                    TripHistoryItem(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        date = obj.getString("date"),
                        totalAmount = obj.getDouble("totalAmount"),
                        memberCount = obj.getInt("memberCount"),
                        settlements = settlements
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun persistList(list: List<TripHistoryItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("date", item.date)
            obj.put("totalAmount", item.totalAmount)
            obj.put("memberCount", item.memberCount)

            val settlementsArray = JSONArray()
            item.settlements.forEach { settlementsArray.put(it) }
            obj.put("settlements", settlementsArray)

            jsonArray.put(obj)
        }
        prefs.edit().putString("history_json", jsonArray.toString()).apply()
    }
}