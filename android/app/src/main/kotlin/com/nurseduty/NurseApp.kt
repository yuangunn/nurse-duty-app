package com.nurseduty

import android.app.Application
import androidx.room.Room
import com.nurseduty.alarm.AlarmScheduler
import com.nurseduty.data.AppDatabase
import com.nurseduty.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NurseApp : Application() {
    lateinit var repository: Repository
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "nurseduty.db").build()
        val scheduler = AlarmScheduler(this, getSharedPreferences("sched", MODE_PRIVATE))
        repository = Repository(db, scheduler, this)
        scope.launch {
            repository.seedPresetsIfEmpty()
            repository.rescheduleNow()
        }
    }
}
