package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxEtfRepositoryImpl
import com.etfmonitor.core.database.entities.Etf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * UseCase for retrieving filtered ETF list via kotlin_krx.
 *
 * PHASE 3 MIGRATION (T-011): Replaces PyKrxClient.getFilteredEtfList() in EtfRepositoryImpl.
 * Uses KrxEtf.getEtfTickerList() + parallel name lookups + keyword filtering.
 *
 * C1 FIX: Returns Result<List<Etf>> (ticker + name), not List<String>
 * C2 FIX: Filters by ETF name (Korean keywords), not ticker codes
 *
 * TECHNICAL DEBT (C2): Injects concrete KrxEtfRepositoryImpl instead of interface.
 */
class GetKrxEtfListUseCase @Inject constructor(
    private val krxEtfRepository: KrxEtfRepositoryImpl
) {
    companion object {
        private const val PARALLEL_LIMIT = 10  // Concurrent API calls limit
    }

    suspend operator fun invoke(
        date: String,
        includeKeywords: List<String> = emptyList(),
        excludeKeywords: List<String> = emptyList()
    ): Result<List<Etf>> = coroutineScope {
        krxEtfRepository.getEtfList(date).mapCatching { tickers ->
            // Fetch ETF names in parallel (C1 fix: construct Etf entities with ticker + name)
            val etfs = tickers.chunked(PARALLEL_LIMIT).flatMap { chunk ->
                chunk.map { ticker ->
                    async {
                        val nameResult = krxEtfRepository.getEtfName(ticker, date)
                        val name = nameResult.getOrElse { "" }
                        Etf(ticker = ticker, name = name)
                    }
                }.awaitAll()
            }

            // Filter by ETF name (C2 fix: Korean keywords match against name, not ticker)
            if (includeKeywords.isEmpty() && excludeKeywords.isEmpty()) {
                return@mapCatching etfs
            }

            etfs.filter { etf ->
                val includeMatch = if (includeKeywords.isEmpty()) {
                    true
                } else {
                    includeKeywords.any { keyword ->
                        etf.name.contains(keyword, ignoreCase = true)
                    }
                }

                val excludeMatch = if (excludeKeywords.isEmpty()) {
                    false
                } else {
                    excludeKeywords.any { keyword ->
                        etf.name.contains(keyword, ignoreCase = true)
                    }
                }

                includeMatch && !excludeMatch
            }
        }
    }
}
