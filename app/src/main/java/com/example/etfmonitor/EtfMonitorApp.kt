package com.etfmonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.etfmonitor.worker.WorkManagerHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Production Level Application 클래스
 *
 * 최적화 포인트:
 * 1. @HiltAndroidApp: Hilt DI 컨테이너 초기화
 * 2. Hilt가 모든 의존성 생명주기 관리
 * 3. HiltWorkerFactory 주입: Worker에 자동으로 의존성 주입
 * 4. Thread-safe 보장: Hilt가 동시성 문제 자동 처리
 * 5. 메모리 누수 방지: static instance 제거, 모든 의존성은 Hilt가 관리
 */
@HiltAndroidApp
class EtfMonitorApp : Application(), Configuration.Provider {

    /**
     * HiltWorkerFactory 주입
     * WorkManager가 Worker 생성 시 Hilt를 통해 의존성 주입
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager Configuration 제공
     *
     * Production 최적화:
     * - HiltWorkerFactory 사용으로 Worker에 자동 의존성 주입
     * - Worker 생성 시 Repository 등이 자동으로 주입됨
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Schedule Market Oscillator update at 8:00 PM every day
        // WorkManager는 Hilt를 통해 Worker에 의존성을 주입합니다
        WorkManagerHelper.scheduleMarketOscillatorUpdate(
            context = this,
            hour = 20,
            minute = 0
        )

        // Schedule Advanced Analysis at 6:30 PM every day (after market close)
        // 고급 분석: 시총가중 ETF흐름, 수급Divergence, 유동성, 섹터분석, ETF상관관계
        WorkManagerHelper.scheduleAdvancedAnalysis(
            context = this,
            hour = 18,
            minute = 30
        )
    }
}