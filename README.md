
# Emporia Vue Integration for Hubitat

> A full-featured Hubitat integration for **Emporia Vue** energy monitors with automatic authentication management, device synchronization, scheduled data retrieval, health monitoring, and configurable notifications.

---

## Overview

Emporia Vue Integration brings Emporia energy monitoring devices into Hubitat by creating Parent and Child devices that mirror the structure of your Emporia account.

The integration is designed for unattended operation and includes automatic token management, intelligent retry logic, device synchronization, configurable health monitoring, and optional notifications when user intervention is required.

The project supports multiple Emporia Vue devices, nested circuits, and automatic synchronization when devices or circuits are added, renamed, merged, unmerged, or removed in the Emporia cloud.

---

# Features

## Authentication

- Secure Cognito authentication
- Automatic token refresh before expiration
- Automatic recovery from HTTP 401 responses
- Intelligent distinction between:
  - Authentication failures
  - Network/server failures
- Manual authentication required only after repeated authorization failures

## Reliability

- Automatic retry after successful token refresh
- Network-aware retry and backoff
- Configurable fetch intervals
- Health monitoring
- Optional notifications
- Recovery notifications
- Compatible with Hubitat Platform 2.5.x HTTP response handling

## Device Management

- Automatic discovery
- Parent device creation
- Child device creation
- Multiple Emporia Vue devices per account
- Nested circuit support
- Automatic synchronization
- Automatic orphan removal
- Automatic label updates

## Monitoring

Child devices expose:

- usage
- usagePercentage
- power
- energy
- powerUnit
- energyUnit
- retrievalFrequency
- lastUpdated

---

# Requirements

- Hubitat Elevation C-7, C-8 or newer
- Hubitat Platform 2.5.x or newer
- Emporia Vue cloud account
- Internet connection

---

# Installation

## Install Apps

Install:

- EmporiaVueIntegration
- EmporiaVueIntegration_AutoRetry (optional)

## Install Drivers

Install:

- EmporiaVueParentDriver
- Emporia Circuit Driver

---

# Initial Configuration

1. Enter Emporia credentials.
2. Authenticate.
3. Discover devices.
4. Create / Update / Delete Hubitat devices.
5. Configure retrieval frequency.
6. Configure optional health notifications.

---

# Health Monitoring

The integration continuously monitors data retrieval health.

Supported capabilities:

- consecutive fetch failure tracking
- elapsed time since last successful fetch
- configurable notification thresholds
- configurable notification devices
- custom failure messages
- custom recovery messages
- one notification per outage
- optional recovery notification
- immediate notification when manual authentication becomes required

Notifications are optional and disabled by default.

---

# AutoRetry Edition

The repository also contains **EmporiaVueIntegration_AutoRetry**.

Unlike the standard app, the AutoRetry edition continues background token refresh attempts after `manualAuthRequired=true` using an exponential backoff schedule (up to approximately 12 hours).

This allows unattended recovery after transient authentication or platform issues while still notifying the user when manual authentication may be required.

---

# Device Synchronization

The integration automatically:

- discovers new devices
- creates missing devices
- updates renamed devices
- updates labels
- removes orphaned devices

No manual cleanup is normally required.

---

# Architecture

```
Emporia Cloud
        │
        ▼
Hubitat App
        │
 ├── Authentication
 ├── Scheduling
 ├── Device Discovery
 ├── Health Monitoring
 └── API Communication
        │
        ▼
Parent Devices
        │
        ▼
Child Circuit Devices
```

---

# Settings

## Account

- Email
- Password

## Retrieval

- 1MIN
- 15MIN
- 1H
- 1D
- 1W

Energy units:

- KilowattHours
- Dollars

Date formats:

- yyyy-MM-dd HH:mm:ss
- dd-MM-yyyy HH:mm:ss

## Notifications

- Enable/Disable
- Notification devices
- Failure message
- Recovery message
- Failed fetch threshold
- Failed duration threshold
- Recovery notification

## Logging

- Debug logging

---

# Troubleshooting

## HTTP 401

The integration automatically attempts:

1. Token refresh
2. Retry failed fetch
3. Background retry with backoff (AutoRetry edition)

Manual authentication is only required if refresh authorization repeatedly fails.

## Manual Authentication

If notified that manual authentication is required:

1. Open the app.
2. Verify credentials.
3. Click **Authenticate**.

---

# Known Limitations

- Refreshing a child device refreshes only its parent Emporia monitor.
- The integration depends on the Emporia cloud API.
- Notification delivery depends on the selected Hubitat notification device.

---

# Future Enhancements

- Integration health virtual device
- Additional energy unit conversions
- Additional diagnostics
- Enhanced dashboard support

---

# Development

Author:
**Amit Halperin**

License:
Apache License 2.0

## Development Notes

This integration was developed and continues to be maintained with assistance from **OpenAI ChatGPT**.

ChatGPT was used throughout the project for architecture discussions, implementation assistance, debugging, testing strategies, documentation, and release preparation.

Final design decisions, implementation, validation, testing, and project maintenance remain the responsibility of the project author.

---

# Release Notes

## v1.1.4 (2026-08-02)

### Added

- Compatibility with Hubitat Platform 2.5.1.x parsed HTTP Map responses
- Shared HTTP response parsing compatibility

### Fixed

- Manual authentication parsing
- Token refresh parsing
- HTTP response compatibility
- Parsing errors incorrectly classified as authentication failures

### Improved

- Authentication robustness
- Token refresh reliability
- Overall compatibility with newer Hubitat firmware
- Internal cleanup

## AutoRetry Edition (2026-04-14)

- Added background refresh attempts after manualAuthRequired
- Exponential refresh backoff
- Prevents indefinite stalled authentication state

## v1.1.2 (2026-03-24)

- Improved HTTP 401 handling
- Added fetch health monitoring
- Added configurable notifications
- Added failure and recovery notifications
- Added manual authentication alerts
- General cleanup

## v1.1.1 (2026-01-19)

- Fixed initialize() syntax
- Improved scheduling
- Improved pending retry handling

## v1.1.0 (2026-01-18)

- Automatic token refresh
- Retry/backoff logic
- Fetch retry throttling

## Earlier Releases

- v1.0.5 — Refresh retry scheduling improvements
- v1.0.4 — Multiple devices and nested circuits
- v1.0.3 — Layout improvements
- v1.0.2 — Device synchronization improvements
- v1.0.1 — Scheduling fix
- v1.0.0 — Initial public release
