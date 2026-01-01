#!/usr/bin/env python3
"""
ETF Monitor Backup Data Generator

Generates backup data files for the ETF Monitor Android app.
Run this script on PC to create historical data backups that can be
restored in the app.

Usage:
    python main.py                    # Run all collectors
    python main.py --market-index     # Run only market index collector
    python main.py --resume           # Resume interrupted collection
    python main.py --help             # Show help

Requirements:
    pip install -r requirements.txt

Environment Variables:
    FRED_API_KEY    - Required for Blood Indicator collection
                      Get free key from: https://fred.stlouisfed.org/docs/api/api_key.html
"""
import argparse
import logging
import sys
import time
from datetime import datetime
from pathlib import Path

from rich.console import Console
from rich.logging import RichHandler
from rich.panel import Panel
from rich.table import Table

from config import Config, get_timestamp_str
from backup_builder import BackupBuilder, BackupData
from collectors import (
    MarketIndexCollector,
    BloodIndicatorCollector,
    MarketDepositCollector,
    FearGreedCollector,
    EtfHoldingsCollector,
    StocksCollector,
    CollectorResult,
)


console = Console()


def setup_logging(config: Config, verbose: bool = False):
    """Setup logging configuration"""
    log_dir = Path(config.output.log_dir)
    log_dir.mkdir(parents=True, exist_ok=True)

    log_file = log_dir / f"backup_{get_timestamp_str()}.log"

    level = logging.DEBUG if verbose else logging.INFO

    logging.basicConfig(
        level=level,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        handlers=[
            RichHandler(console=console, show_time=False, show_path=False),
            logging.FileHandler(log_file, encoding="utf-8"),
        ],
    )

    return logging.getLogger("main")


def print_header():
    """Print application header"""
    console.print(Panel.fit(
        "[bold cyan]ETF Monitor Backup Data Generator[/bold cyan]\n"
        "[dim]Creates backup files for the ETF Monitor Android app[/dim]",
        border_style="cyan"
    ))


def print_config_summary(config: Config, args: argparse.Namespace):
    """Print configuration summary"""
    table = Table(title="Configuration")
    table.add_column("Setting", style="cyan")
    table.add_column("Value", style="green")

    # Date ranges
    if args.market_index or args.all:
        table.add_row(
            "Market Index",
            f"{config.date_range.market_index_start} ~ {config.date_range.market_index_end}"
        )
    if args.blood_indicator or args.all:
        table.add_row(
            "Blood Indicator",
            f"{config.date_range.blood_indicator_start} ~ {config.date_range.blood_indicator_end}"
        )
    if args.market_deposit or args.all:
        table.add_row(
            "Market Deposit",
            f"{config.date_range.market_deposit_start} ~ {config.date_range.market_deposit_end}"
        )
    if args.fear_greed or args.all:
        table.add_row(
            "Fear & Greed",
            f"{config.date_range.fear_greed_start} ~ {config.date_range.fear_greed_end}"
        )
    if args.etf_holdings or args.all:
        table.add_row(
            "ETF Holdings",
            f"{config.date_range.etf_holdings_start} ~ {config.date_range.etf_holdings_end}"
        )

    table.add_row("Output Directory", config.output.output_dir)
    table.add_row("Compression", "Enabled" if config.output.compress else "Disabled")

    console.print(table)
    console.print()


def run_collectors(config: Config, args: argparse.Namespace, logger: logging.Logger) -> BackupData:
    """Run enabled collectors and return collected data"""
    data = BackupData()
    results: list[tuple[str, CollectorResult]] = []

    start_time = time.time()

    # Determine which collectors to run
    run_all = args.all or not any([
        args.market_index,
        args.blood_indicator,
        args.market_deposit,
        args.fear_greed,
        args.etf_holdings,
        args.stocks,
    ])

    # Market Index
    if run_all or args.market_index:
        console.print("\n[bold blue]>>> Market Index[/bold blue]")
        collector = MarketIndexCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("Market Index", result))
        if result.success:
            data.market_indices = result.data

    # Blood Indicator
    if run_all or args.blood_indicator:
        console.print("\n[bold blue]>>> Blood Indicator[/bold blue]")
        collector = BloodIndicatorCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("Blood Indicator", result))
        if result.success:
            data.blood_indicators = result.data

    # Market Deposit
    if run_all or args.market_deposit:
        console.print("\n[bold blue]>>> Market Deposit[/bold blue]")
        collector = MarketDepositCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("Market Deposit", result))
        if result.success:
            data.market_deposits = result.data

    # Fear & Greed
    if run_all or args.fear_greed:
        console.print("\n[bold blue]>>> Fear & Greed[/bold blue]")
        collector = FearGreedCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("Fear & Greed", result))
        if result.success:
            data.fear_greed_indices = result.data

    # ETF Holdings (includes ETF list)
    if run_all or args.etf_holdings:
        console.print("\n[bold blue]>>> ETF Holdings[/bold blue]")
        collector = EtfHoldingsCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("ETF Holdings", result))
        if result.success and result.data:
            data.etfs = result.data.get("etfs", [])
            data.holdings = result.data.get("holdings", [])

    # Stocks
    if run_all or args.stocks:
        console.print("\n[bold blue]>>> Stocks[/bold blue]")
        collector = StocksCollector(config)
        result = collector.collect(resume=args.resume)
        results.append(("Stocks", result))
        if result.success:
            data.stocks = result.data

            # Mark ETF holdings
            if data.holdings:
                collector.mark_etf_holdings(data.stocks, data.holdings)

    # Print results summary
    console.print()
    print_results_summary(results, time.time() - start_time)

    # Check for failures
    failures = [name for name, result in results if not result.success]
    if failures:
        console.print(f"\n[bold red]Warning: {len(failures)} collector(s) failed: {', '.join(failures)}[/bold red]")
        if not args.force:
            console.print("[dim]Use --force to create backup with partial data[/dim]")
            if not args.no_backup:
                return None

    return data


def print_results_summary(results: list[tuple[str, CollectorResult]], total_time: float):
    """Print collection results summary"""
    table = Table(title="Collection Results")
    table.add_column("Collector", style="cyan")
    table.add_column("Status", style="green")
    table.add_column("Records", justify="right")
    table.add_column("Time", justify="right")

    total_records = 0

    for name, result in results:
        status = "[green]Success[/green]" if result.success else "[red]Failed[/red]"
        records = f"{result.record_count:,}" if result.success else "-"
        time_str = f"{result.elapsed_seconds:.1f}s"

        table.add_row(name, status, records, time_str)

        if result.success:
            total_records += result.record_count

    table.add_row("", "", "", "")
    table.add_row(
        "[bold]Total[/bold]",
        "",
        f"[bold]{total_records:,}[/bold]",
        f"[bold]{total_time:.1f}s[/bold]"
    )

    console.print(table)


def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(
        description="ETF Monitor Backup Data Generator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python main.py                      # Run all collectors
    python main.py --market-index       # Run only market index
    python main.py --resume             # Resume interrupted collection
    python main.py --no-backup          # Only collect, don't create backup file

Environment Variables:
    FRED_API_KEY    Required for Blood Indicator (get from fred.stlouisfed.org)
    OUTPUT_DIR      Override output directory
        """
    )

    # Collector selection
    parser.add_argument("--all", "-a", action="store_true",
                        help="Run all collectors (default)")
    parser.add_argument("--market-index", action="store_true",
                        help="Collect market index data (2000~2025)")
    parser.add_argument("--blood-indicator", action="store_true",
                        help="Collect Blood Indicator data (2007~2025)")
    parser.add_argument("--market-deposit", action="store_true",
                        help="Collect market deposit data (2020~2025)")
    parser.add_argument("--fear-greed", action="store_true",
                        help="Collect Fear & Greed data (2020~2025)")
    parser.add_argument("--etf-holdings", action="store_true",
                        help="Collect ETF and holdings data (2022~2025)")
    parser.add_argument("--stocks", action="store_true",
                        help="Collect stock master data")

    # Options
    parser.add_argument("--resume", "-r", action="store_true",
                        help="Resume from checkpoint if available")
    parser.add_argument("--no-backup", action="store_true",
                        help="Only collect data, don't create backup file")
    parser.add_argument("--force", "-f", action="store_true",
                        help="Create backup even if some collectors fail")
    parser.add_argument("--output", "-o", type=str,
                        help="Custom output filename (without extension)")
    parser.add_argument("--no-compress", action="store_true",
                        help="Disable GZIP compression")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Enable verbose logging")

    args = parser.parse_args()

    # Setup
    config = Config.from_env()

    if args.no_compress:
        config.output.compress = False

    logger = setup_logging(config, args.verbose)

    # Print header
    print_header()

    # Validate configuration
    warnings = config.validate()
    for warning in warnings:
        console.print(f"[yellow]Warning: {warning}[/yellow]")

    if warnings:
        console.print()

    # Print configuration summary
    print_config_summary(config, args)

    # Run collectors
    try:
        data = run_collectors(config, args, logger)
    except KeyboardInterrupt:
        console.print("\n[yellow]Interrupted. Progress has been saved to checkpoints.[/yellow]")
        console.print("[dim]Run with --resume to continue from where you left off.[/dim]")
        sys.exit(1)
    except Exception as e:
        logger.exception("Collection failed")
        console.print(f"\n[red]Error: {e}[/red]")
        sys.exit(1)

    if data is None:
        console.print("\n[red]Backup not created due to collection failures.[/red]")
        sys.exit(1)

    # Create backup file
    if not args.no_backup:
        console.print("\n[bold blue]>>> Creating Backup File[/bold blue]")
        try:
            builder = BackupBuilder(config)
            output_path = builder.build(data, args.output)
            console.print(f"\n[bold green]Backup created successfully![/bold green]")
            console.print(f"[dim]File: {output_path}[/dim]")
        except Exception as e:
            logger.exception("Failed to create backup")
            console.print(f"\n[red]Failed to create backup: {e}[/red]")
            sys.exit(1)

    console.print("\n[bold green]Done![/bold green]")


if __name__ == "__main__":
    main()
