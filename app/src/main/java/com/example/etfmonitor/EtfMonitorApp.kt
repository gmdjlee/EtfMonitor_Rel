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
 * 2. 수동 싱글톤 패턴 제거: Hilt가 모든 의존성 생명주기 관리
 * 3. HiltWorkerFactory 주입: Worker에 자동으로 의존성 주입
 * 4. Thread-safe 보장: Hilt가 동시성 문제 자동 처리
 *
 * 기존 문제점 해결:
 * - lateinit instance: 메모리 누수 위험 제거
 * - lazy 초기화: Hilt Singleton으로 대체하여 초기화 시점 최적화
 * - 직접 의존성 관리: 모듈 기반 의존성 주입으로 테스트 용이성 향상
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
        // WorkManager는 이제 Hilt를 통해 Worker에 의존성을 주입합니다
        WorkManagerHelper.scheduleMarketOscillatorUpdate(
            context = this,
            hour = 20,
            minute = 0
        )
    }
}

/**
 * 마이그레이션 가이드:
 *
 * 기존 코드:
 *   val app = application as EtfMonitorApp
 *   val repository = app.repository
 *
 * 변경 후:
 *   @Inject lateinit var repository: DataRepository
 *
 * ViewModel에서:
 *   @HiltViewModel
 *   class MyViewModel @Inject constructor(
 *       private val repository: DataRepository
 *   ) : ViewModel()
 *
 * Worker에서:
 *   @HiltWorker
 *   class MyWorker @AssistedInject constructor(
 *       @Assisted context: Context,
 *       @Assisted params: WorkerParameters,
 *       private val repository: DataRepository
 *   ) : CoroutineWorker(context, params)
 */