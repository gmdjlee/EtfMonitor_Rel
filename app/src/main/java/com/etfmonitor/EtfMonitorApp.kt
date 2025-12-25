package com.etfmonitor

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.etfmonitor.core.worker.WorkManagerHelper
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

        // WorkManager 스케줄링을 메인 스레드 큐 끝으로 지연
        // SurfaceSyncGroup 타임아웃 에러 방지를 위해 앱 시작 후 지연 실행
        Handler(Looper.getMainLooper()).post {
            scheduleAllDailyWorkers()
        }
    }

    /**
     * 모든 일일 Worker 스케줄링
     *
     * 실행 순서 (장 마감 후):
     * 1. 18:00 - ETF 데이터 업데이트
     * 2. 18:15 - Stock 데이터 업데이트
     * 3. 18:30 - 고급 분석 (시총가중, Divergence, 유동성, 섹터, 상관관계)
     * 4. 18:45 - 시장 지수 업데이트
     * 5. 19:00 - 증시 자금 동향 업데이트
     * 6. 19:30 - Fear & Greed Index 업데이트
     * 7. 20:00 - 시장 과매수/과매도 업데이트
     * + 월 1회 데이터 아카이빙
     */
    private fun scheduleAllDailyWorkers() {
        // 1. ETF 데이터 업데이트 (18:00)
        WorkManagerHelper.scheduleEtfUpdate(
            context = this,
            hour = 18,
            minute = 0
        )

        // 2. Stock 데이터 업데이트 (18:15)
        WorkManagerHelper.scheduleStockUpdate(
            context = this,
            hour = 18,
            minute = 15
        )

        // 3. 고급 분석 (18:30) - 시총가중 ETF흐름, 수급Divergence, 유동성, 섹터분석, ETF상관관계
        WorkManagerHelper.scheduleAdvancedAnalysis(
            context = this,
            hour = 18,
            minute = 30
        )

        // 4. 시장 지수 업데이트 (18:45)
        WorkManagerHelper.scheduleMarketIndexUpdate(
            context = this,
            hour = 18,
            minute = 45
        )

        // 5. 증시 자금 동향 업데이트 (19:00)
        WorkManagerHelper.scheduleMarketDepositUpdate(
            context = this,
            hour = 19,
            minute = 0
        )

        // 6. Fear & Greed Index 업데이트 (19:30)
        WorkManagerHelper.scheduleFearGreedUpdate(
            context = this,
            hour = 19,
            minute = 30
        )

        // 7. 시장 과매수/과매도 업데이트 (20:00)
        WorkManagerHelper.scheduleMarketOscillatorUpdate(
            context = this,
            hour = 20,
            minute = 0
        )

        // 8. 데이터 아카이빙 (매월 1일 새벽 3시)
        WorkManagerHelper.scheduleDataArchiving(this)
    }
}