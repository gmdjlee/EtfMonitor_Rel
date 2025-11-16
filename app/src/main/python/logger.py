"""
Centralized logging configuration for EtfMonitor application.

This module provides a standardized logging setup for all modules in the application,
ensuring consistent logging format and behavior across the codebase.
"""

import logging
import sys
from typing import Optional


def setup_logger(
    name: str,
    level: int = logging.INFO,
    format_string: Optional[str] = None
) -> logging.Logger:
    """
    Configure and return a logger for the specified module.

    Args:
        name: Name of the logger (typically __name__ from calling module)
        level: Logging level (default: INFO)
        format_string: Custom format string (optional)

    Returns:
        Configured logger instance

    Examples:
        >>> logger = setup_logger(__name__)
        >>> logger.info("Application started")
        >>> logger.error("An error occurred: %s", error_msg)
    """
    logger = logging.getLogger(name)

    # Prevent duplicate handlers
    if logger.handlers:
        return logger

    logger.setLevel(level)

    # Create console handler that writes to stderr
    handler = logging.StreamHandler(sys.stderr)
    handler.setLevel(level)

    # Create formatter
    if format_string is None:
        format_string = '[%(name)s] %(levelname)s: %(message)s'

    formatter = logging.Formatter(format_string)
    handler.setFormatter(formatter)

    # Add handler to logger
    logger.addHandler(handler)

    return logger


def get_logger(name: str) -> logging.Logger:
    """
    Get an existing logger or create a new one with default settings.

    Args:
        name: Name of the logger

    Returns:
        Logger instance
    """
    return setup_logger(name)
