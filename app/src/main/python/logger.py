"""
Logging configuration for EtfMonitor.
Provides backward compatibility wrapper for core.get_logger.
"""
from core import get_logger

# Backward compatibility alias
setup_logger = get_logger

# Default logger instance for simple imports: from logger import log
log = get_logger("etfmonitor")
