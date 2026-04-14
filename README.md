# EmporiaVue Integration for Hubitat

This project integrates **Emporia Vue** devices into the **Hubitat** platform, enabling real-time energy monitoring and management. The integration supports **token-based authentication**, **device discovery**, **scheduled data retrieval**, **dynamic updates to child devices**, and **health monitoring with notifications**.

## Features
- **Authentication**: Secure **token-based authentication** with Emporia’s API.
- **Automatic Token Refresh**: Automatically refreshes tokens before expiry and retries failed fetches after a successful refresh.
- **Device Discovery**: Automatically **discovers Emporia devices** linked to your account.
- **Child Device Management**: Creates **Hubitat child devices** for each circuit, ensuring full synchronization.
- **Data Retrieval**: Supports **scheduled** and **manual** retrieval of energy usage.
- **Health Monitoring & Notifications**:
  - Tracks data fetch success and failure
  - Detects prolonged fetch failures
  - Supports **configurable notifications**
  - Sends a **single alert per outage**
  - Sends a **recovery notification** when normal operation resumes
  - Sends an **immediate alert** when manual authentication is required
- **Device Labeling**: Ensures **unique labels** to avoid conflicts in **InfluxDB and Grafana**.
- **Custom Attributes**: Updates child devices with:
  - `usage`, `usagePercentage`, `power`, `energy`
  - `powerUnit`, `energyUnit`, `retrievalFrequency`, `lastUpdated`

## Installation

### 1. **Add the App and Drivers to Hubitat**
- Copy the **app** and **drivers** code into Hubitat.
- Install:
  - **EmporiaVueIntegration** (App)
  - **EmporiaVueIntegration_AutoRetry** (App, optional)
  - **EmporiaVueParentDriver** (Parent Device Driver)
  - **Emporia Circuit Driver** (Child Device Driver)

### 2. **Install the App**
- In Hubitat, navigate to **Apps** → Add **EmporiaVueIntegration**.

### 3. **Configure Authentication**
- Enter **Emporia email and password**.
- Click **"Authenticate"** to initiate **token-based authentication**.

### 4. **Discover Devices**
- Click **"Discover Devices"** to find Emporia Vue devices.
- Select devices to **import as Parent and Child Devices**.

### 5. **Configure Data Retrieval**
- Choose **retrieval frequency**, **energy units**, and **date format**.
- Click **Save Settings**.

### 6. **Optional: Configure Health Notifications**
- Enable **Health Monitoring Notifications**.
- Select one or more **notification devices**.
- Define custom **failure** and **recovery** messages.
- Configure thresholds for alerts:
  - after **X failed fetch attempts**
  - after **X minutes without successful data retrieval**
  - or **either condition**

## Usage

### Authentication
- **Authenticate**: Login using **Emporia credentials**.
- **Token Refresh**: Tokens **automatically refresh** before expiration.
- If the API returns **HTTP 401**, the app attempts token refresh automatically and retries the failed fetch.
- Network/server failures are treated differently from authentication failures:
  - **Network/server failures** retry automatically with backoff.
  - **Authentication failures** retry a limited number of times before requiring manual re-authentication.
- **Manual Authenticate** is required only if the refresh token is invalid or repeated refresh attempts fail.

### Optional: AutoRetry App Variant (recommended for unattended recovery)
This repository also includes **`EmporiaVueIntegration_AutoRetry.groovy`**, which installs as a separate Hubitat app named **EmporiaVueIntegration_AutoRetry**.

It is functionally the same as the main app, except for one behavior change:
- If the app enters `manualAuthRequired=true` due to repeated refresh-token authorization failures, it will **continue attempting token refresh in the background** on a **slow backoff schedule** (starting around 60 minutes and doubling up to a 12-hour cap).

This helps prevent the integration from getting “stuck” indefinitely in manual-auth-required mode after transient outages or misclassified failures, while still notifying you that manual authentication may be needed.

### Device Management
- **Discover Devices**: Lists available **Emporia Vue** devices.
- **Create/Update/Delete Devices**:
  - **Adds new circuits** detected.
  - **Updates existing circuits** if changed in the Emporia app.
  - **Removes orphaned circuits** that no longer exist.

### Device Labeling
- Devices include **their parent device** in their **label** to prevent **naming conflicts** in **InfluxDB/Grafana**.

**Example Naming Updates**:
- **Before**: `"Main"`
- **After**: `"Emp-406720-Main"` and `"Emp-430515-Main"`

Labels are **automatically updated**.

### Data Retrieval
- **Scheduled Retrieval**: Runs automatically at the configured interval.
- **Manual Retrieval**:
  - Clicking **"Refresh"** on a **child device** refreshes its **parent Emporia device only**.

### Health Monitoring & Notifications
The app monitors its operational health and can notify users when issues occur.

Supported behavior:
- Tracks consecutive data fetch failures
- Tracks elapsed time since last successful data retrieval
- Sends a **single notification per outage**
- Sends an optional **recovery notification** when normal operation resumes
- Sends an **immediate notification** when `manualAuthRequired` becomes true

This approach helps surface real issues while avoiding unnecessary alert noise.

## Attributes

Each **child device** updates with the following attributes:

| Attribute | Description |
|----------|-------------|
| **usage** | Energy consumption in the last interval |
| **usagePercentage** | Share of total energy usage |
| **power** | Instantaneous power consumption |
| **energy** | Accumulated energy consumption |
| **powerUnit** | Unit of power (e.g., Watts) |
| **energyUnit** | Configurable unit (`KilowattHours` / `Dollars`) |
| **retrievalFrequency** | Data retrieval interval |
| **lastUpdated** | Timestamp of the last update |

## Settings

### Emporia Account Settings
- **Email**: Your Emporia account email.
- **Password**: Your Emporia account password.

### Data Retrieval Settings
- **Retrieval Frequency**: `1MIN`, `15MIN`, `1H`, `1D`, `1W`
- **Energy Unit**: `KilowattHours` or `Dollars`
- **Date Format**: `yyyy-MM-dd HH:mm:ss` or `dd-MM-yyyy HH:mm:ss`

### Health Notification Settings
- Enable **Health Notifications**
- Select **Notification Device(s)**
- Configure **Failure Notification Message**
- Configure **Recovery Notification Message**
- Configure **Failure Thresholds**
  - number of failed fetch attempts
  - elapsed minutes since last success
- Enable optional **Recovery Notification**

### Debug Logging
- **Enable Debug Logging**: Toggle logs **on/off**.

## Debugging and Logs

Logs provide insight into **authentication**, **token refresh**, **device creation**, **data retrieval**, **health monitoring**, and **attribute updates**.

### Example Logs

**Successful Authentication**

app:2242025-03-15 10:10:10.123info Authentication successful. Token expires at 2025-03-15T15:10:10Z.


**Successful Data Fetch**

app:2242025-03-15 11:11:11.123info Fetching data for all monitored devices...
app:2242025-03-15 11:11:11.456debug Updated child device EmporiaVue406720-2: usage=0.0153, power=921, energy=0.02, powerUnit=Watt.
app:2242025-03-15 11:11:11.976info Data fetch and update completed successfully.


**Failure Notification**

app:632026-03-25 19:21:23.243warn Sending health notification: Emporia app alert: data fetch is failing.
app:632026-03-25 19:21:23.267warn Notification command sent to: iPhone Amit
app:632026-03-25 19:21:23.270warn Health failure notification sent.


**Recovery Notification**

app:632026-03-25 19:25:01.912warn Sending health notification: Emporia app recovery: data fetch is working again.
app:632026-03-25 19:25:01.959warn Notification command sent to: iPhone Amit
app:632026-03-25 19:25:01.966info Health recovery notification sent.


## Known Limitations

- Refreshing a **child device** triggers a data retrieval for its **parent device only**, not for all devices.
- The integration depends on Emporia cloud API behavior and token lifecycle behavior, which may change over time.
- Notification delivery depends on the selected Hubitat notification device configuration.

## Future Enhancements
- Expose integration health status via a **virtual device attribute** for Rule Machine or automation triggers.
- Support additional **energy unit conversions**.
- Continue improving **log clarity and diagnostics**.

## Author
Amit Halperin

## Development Notes
This integration was developed with assistance from **ChatGPT (OpenAI GPT-5.3)** during iterative design, debugging, and feature development.

## Version History

### v1.1.2-autoretry.2 (2026-04-14)
- Added `EmporiaVueIntegration_AutoRetry.groovy` app variant that continues token refresh attempts on slow backoff even after `manualAuthRequired=true` (prevents indefinite “stuck” state after transient outages/misclassification).
- Documented AutoRetry variant in README.

### v1.1.2 (2026-03-24)
- Fixed HTTP 401 handling when returned via exception path during data fetch
- Added fetch health monitoring (success/failure tracking)
- Added configurable health notifications
- Implemented single failure alert per outage
- Added optional recovery notifications
- Added manual authentication alerts
- Reset health tracking after successful authentication or token refresh
- Code cleanup and helper method consolidation

### v1.1.1 (2026-01-19)
- Fix initialize() syntax error
- Ensure pending flags are initialized
- Use numeric GID list for fetch-all
- Prevent unscheduling unrelated jobs
- Safer runIn callbacks

### v1.1.0 (2026-01-18)
- Automatic token refresh on HTTP 401
- Network-aware retry/backoff logic
- Fetch retry throttling
- Updated authenticate() to reset manual auth flags
- Scheduling improvements


