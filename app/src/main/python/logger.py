"""
Logging configuration for EtfMonitor.
Provides backward compatibility wrapper for core.get_logger.
"""
from core import get_logger

# Backward compatibility alias
setup_logger = get_logger
