"""
Base collector class with rate limiting, retry logic, and checkpoint support
"""
import json
import logging
import os
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from rich.console import Console
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn

from config import Config, RetryConfig


console = Console()


@dataclass
class CollectorResult:
    """Result of data collection"""
    success: bool
    data: list[dict] = field(default_factory=list)
    record_count: int = 0
    error_message: Optional[str] = None
    elapsed_seconds: float = 0.0
    checkpoint_file: Optional[str] = None


class BaseCollector(ABC):
    """Base class for all data collectors"""

    def __init__(self, config: Config, name: str):
        self.config = config
        self.name = name
        self.logger = logging.getLogger(f"collector.{name}")
        self._last_request_time = 0.0
        self._setup_directories()

    def _setup_directories(self):
        """Create necessary directories"""
        Path(self.config.output.checkpoint_dir).mkdir(parents=True, exist_ok=True)
        Path(self.config.output.log_dir).mkdir(parents=True, exist_ok=True)

    @property
    @abstractmethod
    def collector_type(self) -> str:
        """Return collector type identifier"""
        pass

    @property
    def checkpoint_file(self) -> Path:
        """Get checkpoint file path"""
        return Path(self.config.output.checkpoint_dir) / f"{self.collector_type}_checkpoint.json"

    @property
    def data_file(self) -> Path:
        """Get intermediate data file path"""
        return Path(self.config.output.checkpoint_dir) / f"{self.collector_type}_data.json"

    def rate_limit(self, delay: Optional[float] = None):
        """Apply rate limiting between requests"""
        if delay is None:
            delay = self.config.rate_limit.default_delay

        elapsed = time.time() - self._last_request_time
        if elapsed < delay:
            sleep_time = delay - elapsed
            time.sleep(sleep_time)
        self._last_request_time = time.time()

    def retry_with_backoff(
        self,
        func,
        *args,
        retry_config: Optional[RetryConfig] = None,
        **kwargs
    ) -> Any:
        """Execute function with exponential backoff retry"""
        if retry_config is None:
            retry_config = self.config.retry

        last_exception = None
        delay = retry_config.retry_delay

        for attempt in range(retry_config.max_retries + 1):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                last_exception = e
                if attempt < retry_config.max_retries:
                    self.logger.warning(
                        f"Attempt {attempt + 1} failed: {e}. Retrying in {delay:.1f}s..."
                    )
                    time.sleep(delay)
                    delay = min(
                        delay * retry_config.retry_backoff,
                        retry_config.max_retry_delay
                    )

        raise last_exception

    def save_checkpoint(self, state: dict):
        """Save collection progress checkpoint"""
        checkpoint = {
            "collector": self.collector_type,
            "timestamp": datetime.now().isoformat(),
            "state": state
        }
        with open(self.checkpoint_file, "w", encoding="utf-8") as f:
            json.dump(checkpoint, f, ensure_ascii=False, indent=2)
        self.logger.debug(f"Checkpoint saved: {self.checkpoint_file}")

    def load_checkpoint(self) -> Optional[dict]:
        """Load collection progress checkpoint"""
        if not self.checkpoint_file.exists():
            return None

        try:
            with open(self.checkpoint_file, "r", encoding="utf-8") as f:
                checkpoint = json.load(f)
            self.logger.info(f"Checkpoint loaded: {checkpoint.get('timestamp')}")
            return checkpoint.get("state")
        except Exception as e:
            self.logger.warning(f"Failed to load checkpoint: {e}")
            return None

    def clear_checkpoint(self):
        """Clear checkpoint file after successful completion"""
        if self.checkpoint_file.exists():
            self.checkpoint_file.unlink()
            self.logger.debug("Checkpoint cleared")

    def save_data(self, data: list[dict]):
        """Save collected data to intermediate file"""
        with open(self.data_file, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        self.logger.debug(f"Data saved: {len(data)} records")

    def load_data(self) -> list[dict]:
        """Load collected data from intermediate file"""
        if not self.data_file.exists():
            return []

        try:
            with open(self.data_file, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            self.logger.warning(f"Failed to load data: {e}")
            return []

    def clear_data(self):
        """Clear intermediate data file after completion"""
        if self.data_file.exists():
            self.data_file.unlink()
            self.logger.debug("Intermediate data cleared")

    @abstractmethod
    def collect(self, resume: bool = True) -> CollectorResult:
        """
        Collect data and return result.

        Args:
            resume: If True, try to resume from checkpoint

        Returns:
            CollectorResult with collected data
        """
        pass

    def get_timestamp_ms(self) -> int:
        """Get current timestamp in milliseconds"""
        return int(datetime.now().timestamp() * 1000)

    def parse_date(self, date_str: str) -> datetime:
        """Parse date string in various formats"""
        formats = [
            "%Y-%m-%d",
            "%Y%m%d",
            "%Y.%m.%d",
            "%Y/%m/%d",
        ]
        for fmt in formats:
            try:
                return datetime.strptime(date_str, fmt)
            except ValueError:
                continue
        raise ValueError(f"Cannot parse date: {date_str}")

    def format_date_ymd(self, dt: datetime) -> str:
        """Format datetime as YYYYMMDD"""
        return dt.strftime("%Y%m%d")

    def format_date_iso(self, dt: datetime) -> str:
        """Format datetime as YYYY-MM-DD"""
        return dt.strftime("%Y-%m-%d")

    def log_progress(self, current: int, total: int, message: str = ""):
        """Log collection progress"""
        pct = (current / total * 100) if total > 0 else 0
        self.logger.info(f"[{current}/{total}] ({pct:.1f}%) {message}")
