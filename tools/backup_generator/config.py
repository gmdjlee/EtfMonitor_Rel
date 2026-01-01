"""
ETF Monitor Backup Generator Configuration
"""
import os
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class DateRangeConfig:
    """Date range configuration for each data type"""
    # Market Index: 2000~2025
    market_index_start: str = "2000-01-01"
    market_index_end: str = "2025-12-31"

    # Blood Indicator: 2007~2025
    blood_indicator_start: str = "2007-01-01"
    blood_indicator_end: str = "2025-12-31"

    # Market Deposit: 2020~2025
    market_deposit_start: str = "2020-01-01"
    market_deposit_end: str = "2025-12-31"

    # Fear & Greed: 2020~2025 (actual request will be 3x for MA calculation)
    fear_greed_start: str = "2020-01-01"
    fear_greed_end: str = "2025-12-31"

    # ETF Holdings: 2022~2025
    etf_holdings_start: str = "2022-01-01"
    etf_holdings_end: str = "2025-12-31"


@dataclass
class RateLimitConfig:
    """Rate limiting configuration"""
    # Default delay between requests (seconds)
    default_delay: float = 0.5

    # pykrx requests
    pykrx_delay: float = 0.5

    # Naver Finance scraping
    naver_delay: float = 0.5

    # FRED API
    fred_delay: float = 0.3

    # Yahoo Finance
    yahoo_delay: float = 0.3

    # KRX API
    krx_delay: float = 1.0

    # ETF Holdings (more conservative to avoid blocking)
    etf_holdings_delay: float = 2.0

    # Batch sizes
    market_index_batch_days: int = 365
    fear_greed_batch_days: int = 90
    etf_holdings_batch_days: int = 30


@dataclass
class RetryConfig:
    """Retry configuration"""
    max_retries: int = 3
    retry_delay: float = 2.0  # Initial delay
    retry_backoff: float = 2.0  # Exponential backoff multiplier
    max_retry_delay: float = 60.0  # Maximum delay between retries


@dataclass
class ApiConfig:
    """API configuration"""
    # FRED API key (required for Blood Indicator)
    # Get free key from: https://fred.stlouisfed.org/docs/api/api_key.html
    fred_api_key: str = field(default_factory=lambda: os.environ.get("FRED_API_KEY", ""))

    # Request timeout (seconds)
    timeout: int = 30

    # User agent for web requests
    user_agent: str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"


@dataclass
class OutputConfig:
    """Output configuration"""
    # Output directory
    output_dir: str = "output"

    # Checkpoint directory (for resuming interrupted collection)
    checkpoint_dir: str = "checkpoints"

    # Log directory
    log_dir: str = "logs"

    # Backup file name prefix
    backup_prefix: str = "etfmonitor_backup"

    # Enable GZIP compression
    compress: bool = True

    # App version to embed in backup
    app_version: str = "1.0.0"

    # Schema version (must match app's database schema)
    schema_version: int = 19


@dataclass
class EtfConfig:
    """ETF filtering configuration"""
    # Keywords to include in ETF name (at least one must match)
    include_keywords: list = field(default_factory=lambda: ["액티브"])

    # Optional theme keywords
    theme_keywords: list = field(default_factory=lambda: [
        "반도체", "2차전지", "배터리", "AI", "인공지능",
        "로봇", "바이오", "헬스케어", "자동차", "전기차",
        "ESG", "친환경", "클라우드", "메타버스", "게임"
    ])

    # Keywords to exclude
    exclude_keywords: list = field(default_factory=lambda: [
        "인버스", "곱버스", "레버리지", "선물", "채권", "달러", "원자재"
    ])

    # Snapshot type for holdings
    snapshot_type: str = "DAILY"


@dataclass
class Config:
    """Main configuration"""
    date_range: DateRangeConfig = field(default_factory=DateRangeConfig)
    rate_limit: RateLimitConfig = field(default_factory=RateLimitConfig)
    retry: RetryConfig = field(default_factory=RetryConfig)
    api: ApiConfig = field(default_factory=ApiConfig)
    output: OutputConfig = field(default_factory=OutputConfig)
    etf: EtfConfig = field(default_factory=EtfConfig)

    # Markets to collect
    markets: list = field(default_factory=lambda: ["KOSPI", "KOSDAQ"])

    # Enable/disable specific collectors
    collect_market_index: bool = True
    collect_blood_indicator: bool = True
    collect_market_deposit: bool = True
    collect_fear_greed: bool = True
    collect_etf_holdings: bool = True
    collect_stocks: bool = True

    def validate(self) -> list[str]:
        """Validate configuration and return list of warnings"""
        warnings = []

        if self.collect_blood_indicator and not self.api.fred_api_key:
            warnings.append(
                "FRED_API_KEY not set. Blood Indicator collection will fail. "
                "Get free key from: https://fred.stlouisfed.org/docs/api/api_key.html"
            )

        return warnings

    @classmethod
    def from_env(cls) -> "Config":
        """Create configuration from environment variables"""
        config = cls()

        # Override with environment variables if set
        if os.environ.get("FRED_API_KEY"):
            config.api.fred_api_key = os.environ["FRED_API_KEY"]

        if os.environ.get("OUTPUT_DIR"):
            config.output.output_dir = os.environ["OUTPUT_DIR"]

        return config


# Default configuration instance
DEFAULT_CONFIG = Config()


def get_timestamp_str() -> str:
    """Get current timestamp string for file names"""
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def to_ymd(date_str: str) -> str:
    """Convert date string to YYYYMMDD format"""
    if "-" in date_str:
        return date_str.replace("-", "")
    return date_str


def to_iso(date_str: str) -> str:
    """Convert date string to YYYY-MM-DD format"""
    if "-" not in date_str and len(date_str) == 8:
        return f"{date_str[:4]}-{date_str[4:6]}-{date_str[6:8]}"
    return date_str
