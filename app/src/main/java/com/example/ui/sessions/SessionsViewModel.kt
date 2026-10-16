package com.example.ui.sessions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.usage.DetailedSession
import com.example.usage.SessionsDashboardData
import com.example.usage.SessionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SessionsViewModel : ViewModel() {

    private val _dashboardData = MutableStateFlow<SessionsDashboardData?>(null)
    val dashboardData: StateFlow<SessionsDashboardData?> = _dashboardData.asStateFlow()

    private val _selectedCalendar = MutableStateFlow(Calendar.getInstance())
    val selectedCalendar: StateFlow<Calendar> = _selectedCalendar.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: App Statistics, 1: Session Timeline
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("Daily") // "Daily", "Weekly"
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    private val _selectedSessionForDetail = MutableStateFlow<DetailedSession?>(null)
    val selectedSessionForDetail: StateFlow<DetailedSession?> = _selectedSessionForDetail.asStateFlow()

    private var liveTickerJob: Job? = null

    fun loadData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = SessionsRepository.getSessionsDashboardData(context, _selectedCalendar.value)
            _dashboardData.value = data

            startLiveTicker()
        }
    }

    private fun startLiveTicker() {
        liveTickerJob?.cancel()
        liveTickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _dashboardData.value
                if (current != null && current.isScreenActive) {
                    _dashboardData.value = current.copy(
                        currentSessionDurationMs = current.currentSessionDurationMs + 1000L,
                        screenOnMs = current.screenOnMs + 1000L
                    )
                }
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setSelectedPeriod(period: String) {
        _selectedPeriod.value = period
    }

    fun selectSessionForDetail(session: DetailedSession?) {
        _selectedSessionForDetail.value = session
    }

    fun navigateDate(context: Context, daysOffset: Int) {
        val newCal = _selectedCalendar.value.clone() as Calendar
        newCal.add(Calendar.DAY_OF_YEAR, daysOffset)
        // Do not navigate past today
        if (newCal.timeInMillis > System.currentTimeMillis()) return
        _selectedCalendar.value = newCal
        loadData(context)
    }

    fun goToToday(context: Context) {
        _selectedCalendar.value = Calendar.getInstance()
        loadData(context)
    }

    override fun onCleared() {
        super.onCleared()
        liveTickerJob?.cancel()
    }
}
