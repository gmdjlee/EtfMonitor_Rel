"""
Backup Builder
Builds backup file in app-compatible format
"""
import gzip
import json
import logging
import os
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from rich.console import Console
from rich.table import Table

from config import Config, get_timestamp_str


console = Console()
logger = logging.getLogger("backup_builder")


@dataclass
class BackupData:
    """Container for all backup data"""
    etfs: list[dict] = None
    stocks: list[dict] = None
    holdings: list[dict] = None
    market_indices: list[dict] = None
    market_deposits: list[dict] = None
    fear_greed_indices: list[dict] = None
    blood_indicators: list[dict] = None

    # Optional data (not collected by default)
    settings: list[dict] = None
    market_oscillators: list[dict] = None
    daily_etf_statistics: list[dict] = None
    price_caches: list[dict] = None
    stock_analysis_data: list[dict] = None
    ai_analysis_results: list[dict] = None
    ai_chat_sessions: list[dict] = None
    ai_chat_messages: list[dict] = None
    correlation_results: list[dict] = None
    sector_analyses: list[dict] = None
    etf_correlation_caches: list[dict] = None
    liquidity_analyses: list[dict] = None
    stock_indicator_ai_results: list[dict] = None
    enhanced_predictions: list[dict] = None
    search_histories: list[dict] = None


class BackupBuilder:
    """Builds backup file from collected data"""

    def __init__(self, config: Config):
        self.config = config
        Path(config.output.output_dir).mkdir(parents=True, exist_ok=True)

    def build(
        self,
        data: BackupData,
        filename: Optional[str] = None
    ) -> str:
        """
        Build backup file from collected data.

        Args:
            data: BackupData containing all collected data
            filename: Optional custom filename (without extension)

        Returns:
            Path to the created backup file
        """
        # Generate filename
        if filename is None:
            timestamp = get_timestamp_str()
            filename = f"{self.config.output.backup_prefix}_{timestamp}"

        # Determine extension
        ext = ".etfbackup.gz" if self.config.output.compress else ".etfbackup"
        output_path = Path(self.config.output.output_dir) / f"{filename}{ext}"

        # Build metadata
        metadata = self._build_metadata(data)

        # Build entity data
        entity_data = self._build_entity_data(data)

        # Create backup structure
        backup = {
            "metadata": metadata,
            "data": entity_data
        }

        # Serialize to JSON
        json_str = json.dumps(backup, ensure_ascii=False, separators=(",", ":"))
        json_bytes = json_str.encode("utf-8")

        # Write file (compressed or not)
        if self.config.output.compress:
            with gzip.open(output_path, "wb") as f:
                f.write(json_bytes)
        else:
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(json_str)

        file_size = output_path.stat().st_size
        logger.info(f"Backup created: {output_path} ({self._format_size(file_size)})")

        # Print summary
        self._print_summary(metadata, output_path, file_size)

        return str(output_path)

    def _build_metadata(self, data: BackupData) -> dict:
        """Build backup metadata"""
        # Calculate date range
        all_dates = []

        if data.market_indices:
            all_dates.extend(d["date"] for d in data.market_indices)
        if data.holdings:
            all_dates.extend(d["date"] for d in data.holdings)
        if data.fear_greed_indices:
            all_dates.extend(d["date"] for d in data.fear_greed_indices)
        if data.blood_indicators:
            all_dates.extend(d["date"] for d in data.blood_indicators)
        if data.market_deposits:
            all_dates.extend(d["date"] for d in data.market_deposits)

        date_range = None
        if all_dates:
            all_dates.sort()
            date_range = {
                "startDate": all_dates[0],
                "endDate": all_dates[-1]
            }

        # Build entity counts
        entity_counts = {}
        selected_entities = []

        if data.etfs:
            entity_counts["etfs"] = len(data.etfs)
            selected_entities.append("ETF")
        if data.stocks:
            entity_counts["stocks"] = len(data.stocks)
            selected_entities.append("STOCK")
        if data.holdings:
            entity_counts["holdings"] = len(data.holdings)
            selected_entities.append("HOLDING")
        if data.market_indices:
            entity_counts["marketIndices"] = len(data.market_indices)
            selected_entities.append("MARKET_INDEX")
        if data.market_deposits:
            entity_counts["marketDeposits"] = len(data.market_deposits)
            selected_entities.append("MARKET_DEPOSIT")
        if data.fear_greed_indices:
            entity_counts["fearGreedIndices"] = len(data.fear_greed_indices)
            selected_entities.append("FEAR_GREED_INDEX")
        if data.blood_indicators:
            entity_counts["bloodIndicators"] = len(data.blood_indicators)
            selected_entities.append("BLOOD_INDICATOR")
        if data.market_oscillators:
            entity_counts["marketOscillators"] = len(data.market_oscillators)
            selected_entities.append("MARKET_OSCILLATOR")
        if data.daily_etf_statistics:
            entity_counts["dailyEtfStatistics"] = len(data.daily_etf_statistics)
            selected_entities.append("DAILY_ETF_STATISTICS")

        return {
            "version": 1,
            "appVersion": self.config.output.app_version,
            "schemaVersion": self.config.output.schema_version,
            "createdAt": int(datetime.now().timestamp() * 1000),
            "deviceName": "PC Backup Generator",
            "backupType": "SELECTIVE",
            "dateRange": date_range,
            "selectedEntities": selected_entities,
            "entityCounts": entity_counts
        }

    def _build_entity_data(self, data: BackupData) -> dict:
        """Build entity data structure"""
        return {
            "etfs": data.etfs,
            "stocks": data.stocks,
            "settings": data.settings,
            "holdings": data.holdings,
            "marketDeposits": data.market_deposits,
            "fearGreedIndices": data.fear_greed_indices,
            "marketOscillators": data.market_oscillators,
            "marketIndices": data.market_indices,
            "dailyEtfStatistics": data.daily_etf_statistics,
            "bloodIndicators": data.blood_indicators,
            "priceCaches": data.price_caches,
            "stockAnalysisData": data.stock_analysis_data,
            "aiAnalysisResults": data.ai_analysis_results,
            "aiChatSessions": data.ai_chat_sessions,
            "aiChatMessages": data.ai_chat_messages,
            "correlationResults": data.correlation_results,
            "sectorAnalyses": data.sector_analyses,
            "etfCorrelationCaches": data.etf_correlation_caches,
            "liquidityAnalyses": data.liquidity_analyses,
            "stockIndicatorAIResults": data.stock_indicator_ai_results,
            "enhancedPredictions": data.enhanced_predictions,
            "searchHistories": data.search_histories
        }

    def _format_size(self, size_bytes: int) -> str:
        """Format file size for display"""
        for unit in ["B", "KB", "MB", "GB"]:
            if size_bytes < 1024:
                return f"{size_bytes:.1f} {unit}"
            size_bytes /= 1024
        return f"{size_bytes:.1f} TB"

    def _print_summary(self, metadata: dict, output_path: Path, file_size: int):
        """Print backup summary to console"""
        table = Table(title="Backup Summary")
        table.add_column("Property", style="cyan")
        table.add_column("Value", style="green")

        table.add_row("File", str(output_path.name))
        table.add_row("Size", self._format_size(file_size))
        table.add_row("Schema Version", str(metadata["schemaVersion"]))

        if metadata["dateRange"]:
            date_range = f"{metadata['dateRange']['startDate']} ~ {metadata['dateRange']['endDate']}"
            table.add_row("Date Range", date_range)

        table.add_row("Entities", ", ".join(metadata["selectedEntities"]))

        # Entity counts
        for entity, count in metadata["entityCounts"].items():
            table.add_row(f"  {entity}", f"{count:,}")

        console.print(table)


def load_backup(file_path: str) -> dict:
    """Load and parse a backup file"""
    path = Path(file_path)

    if path.suffix == ".gz" or file_path.endswith(".etfbackup.gz"):
        with gzip.open(path, "rb") as f:
            content = f.read().decode("utf-8")
    else:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

    return json.loads(content)


def validate_backup(backup: dict) -> list[str]:
    """Validate backup structure and return list of issues"""
    issues = []

    if "metadata" not in backup:
        issues.append("Missing metadata")
        return issues

    metadata = backup["metadata"]

    if "schemaVersion" not in metadata:
        issues.append("Missing schema version")
    elif metadata["schemaVersion"] > 19:  # Current app schema
        issues.append(f"Schema version {metadata['schemaVersion']} is newer than supported (19)")

    if "data" not in backup:
        issues.append("Missing data section")

    return issues
