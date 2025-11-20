package com.etfmonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.chaquo.python.Python
import com.etfmonitor.database.AppDatabase
import com.etfmonitor.repository.*
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
 *
 * Backward Compatibility:
 * - 아직 Hilt로 마이그레이션하지 않은 ViewModel/Component를 위해 임시로 instance 제공
 * - TODO: 모든 Component를 Hilt로 마이그레이션한 후 instance 제거
 */
@HiltAndroidApp
class EtfMonitorApp : Application(), Configuration.Provider {

    companion object {
        /**
         * @Deprecated("Use Hilt injection instead")
         * 임시 backward compatibility를 위한 instance
         * 점진적으로 모든 ViewModel을 @HiltViewModel로 마이그레이션하면 제거 예정
         */
        lateinit var instance: EtfMonitorApp
            private set
    }

    /**
     * HiltWorkerFactory 주입
     * WorkManager가 Worker 생성 시 Hilt를 통해 의존성 주입
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Hilt로 주입된 의존성들
     * @Deprecated("Use Hilt injection in your ViewModel/Component instead")
     */
    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var python: Python

    @Inject
    lateinit var repository: DataRepository

    @Inject
    lateinit var stockRepository: StockRepository

    @Inject
    lateinit var stockAnalysisRepository: StockAnalysisRepository

    @Inject
    lateinit var marketDepositRepository: MarketDepositRepository

    @Inject
    lateinit var fearGreedRepository: FearGreedRepository

    @Inject
    lateinit var marketOscillatorRepository: MarketOscillatorRepository

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
        instance = this  // Backward compatibility

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