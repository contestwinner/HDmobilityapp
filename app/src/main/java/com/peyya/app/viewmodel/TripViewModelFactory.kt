package com.peyya.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.peyya.app.data.TripProgressStore
import com.peyya.app.data.VisitLog
import com.peyya.app.service.GatewayFactory
import com.peyya.app.service.SpeechGuide

class TripViewModelFactory(
    private val appContext: Context,
    private val forceMock: Boolean = false,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TripViewModel::class.java)) {
            "Unknown ViewModel ${modelClass.name}"
        }
        val gateways = GatewayFactory.create(appContext, forceMock = forceMock)
        val speech = SpeechGuide(appContext)
        return TripViewModel(
            navi = gateways.navi,
            ignition = gateways.ignition,
            speech = speech,
            link = gateways.link,
            progressStore = TripProgressStore(appContext),
            visitLog = VisitLog.get(appContext),
        ) as T
    }
}
