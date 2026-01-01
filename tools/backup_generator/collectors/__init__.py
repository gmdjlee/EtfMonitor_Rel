"""
ETF Monitor Backup Data Collectors
"""
from .base import BaseCollector, CollectorResult
from .market_index import MarketIndexCollector
from .blood_indicator import BloodIndicatorCollector
from .market_deposit import MarketDepositCollector
from .fear_greed import FearGreedCollector
from .etf_holdings import EtfHoldingsCollector
from .stocks import StocksCollector

__all__ = [
    "BaseCollector",
    "CollectorResult",
    "MarketIndexCollector",
    "BloodIndicatorCollector",
    "MarketDepositCollector",
    "FearGreedCollector",
    "EtfHoldingsCollector",
    "StocksCollector",
]
