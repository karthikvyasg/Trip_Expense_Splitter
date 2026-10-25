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
    val settlements: List<String>,
    val members: ArrayList<String> = arrayListOf(),
    val mapKeys: ArrayList<String> = arrayListOf(),
    val mapValues: ArrayList<String> = arrayListOf(),
    val isUnequal: Boolean = false,
    val unequalItemsJson: String = ""
)

class TripHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("TripHistoryPrefs", Context.MODE_PRIVATE)

    fun saveTrip(item: TripHistoryItem) {
        val currentList = getHistory().toMutableList()
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

                val settlementsArray = obj.optJSONArray("settlements") ?: JSONArray()
                val settlements = mutableListOf<String>()
                for (j in 0 until settlementsArray.length()) {
                    settlements.add(settlementsArray.getString(j))
                }

                val membersArray = obj.optJSONArray("members") ?: JSONArray()
                val members = ArrayList<String>()
                for (j in 0 until membersArray.length()) {
                    members.add(membersArray.getString(j))
                }

                val keysArray = obj.optJSONArray("mapKeys") ?: JSONArray()
                val mapKeys = ArrayList<String>()
                for (j in 0 until keysArray.length()) {
                    mapKeys.add(keysArray.getString(j))
                }

                val valuesArray = obj.optJSONArray("mapValues") ?: JSONArray()
                val mapValues = ArrayList<String>()
                for (j in 0 until valuesArray.length()) {
                    mapValues.add(valuesArray.getString(j))
                }

                list.add(
                    TripHistoryItem(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        date = obj.getString("date"),
                        totalAmount = obj.getDouble("totalAmount"),
                        memberCount = obj.getInt("memberCount"),
                        settlements = settlements,
                        members = members,
                        mapKeys = mapKeys,
                        mapValues = mapValues,
                        isUnequal = obj.optBoolean("isUnequal", false),
                        unequalItemsJson = obj.optString("unequalItemsJson", "")
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

            val membersArray = JSONArray()
            item.members.forEach { membersArray.put(it) }
            obj.put("members", membersArray)

            val keysArray = JSONArray()
            item.mapKeys.forEach { keysArray.put(it) }
            obj.put("mapKeys", keysArray)

            val valuesArray = JSONArray()
            item.mapValues.forEach { valuesArray.put(it) }
            obj.put("mapValues", valuesArray)

            obj.put("isUnequal", item.isUnequal)
            obj.put("unequalItemsJson", item.unequalItemsJson)

            jsonArray.put(obj)
        }
        prefs.edit().putString("history_json", jsonArray.toString()).apply()
    }
}