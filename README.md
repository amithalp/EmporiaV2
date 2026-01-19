# EmporiaVue Integration for Hubitat

This project integrates **Emporia Vue** devices into the **Hubitat** platform, enabling real-time energy monitoring and management. The integration supports **token-based authentication**, **device discovery**, **data retrieval**, and **dynamic updates to child devices**.

## Features
- **Authentication**: Secure **token-based authentication** with Emporia’s API.
- **Device Discovery**: Automatically **discovers Emporia devices** linked to your account.
- **Child Device Management**: Creates **Hubitat child devices** for each circuit, ensuring full synchronization.
- **Data Retrieval**: Supports **scheduled** and **manual** retrieval of energy usage.
- **Device Labeling**: Ensures **unique labels** to avoid conflicts in **InfluxDB and Grafana**.
- **Custom Attributes**: Updates child devices with:
  - `usage`, `usagePercentage`, `power`, `energy`
  - `powerUnit`, `energyUnit`, `retrievalFrequency`, `lastUpdated`

## Installation

### 1. **Add the App and Drivers to Hubitat**
- Copy the **app** and **drivers** code into Hubitat.
- Install:
  - **EmporiaVueIntegration** (App)
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

## Usage

### Authentication
- **Authenticate**: Login using **Emporia credentials**.
- **Token Refresh**: Tokens **automatically refresh** 5 minutes before expiration.
- As of app version 1.0.5, the app will auto reschedule refresh token in case of failure.
  
### Device Management
- **Discover Devices**: Lists available **Emporia Vue** devices.
- **Create/Update/Delete Devices**:
  - **Adds new circuits** detected.
  - **Updates existing circuits** if changed in the Emporia app.
  - **Removes orphaned circuits** that no longer exist.

### Device Labeling
- Devices now include **their parent device** in their **label** to prevent **naming conflicts** in **InfluxDB/Grafana**.
- **Example Naming Updates**:
  - **Before**: `"Main"`
  - **After**: `"Emp-406720-Main"` and `"Emp-430515-Main"`  
- Labels are **automatically updated**.

### Data Retrieval
- **Scheduled Retrieval**: Runs automatically at **configured intervals**.
- **Manual Retrieval**: Click **"Refresh"** on a **child device** to trigger an update.

## Attributes

Each **child device** updates with the following attributes:

| Attribute           | Description |
|--------------------|-------------|
| **usage**          | Energy consumption in the last interval |
| **usagePercentage** | Share of total energy usage |
| **power**         | Instantaneous power consumption |
| **energy**        | Accumulated energy consumption |
| **powerUnit**     | Unit of power (e.g., Watts) |
| **energyUnit**    | Configurable unit (KilowattHours/Dollars) |
| **retrievalFrequency** | Data retrieval interval |
| **lastUpdated**   | Timestamp of the last update |

## Settings

### Emporia Account Settings
- **Email**: Your Emporia account email.
- **Password**: Your Emporia account password.

### Data Retrieval Settings
- **Retrieval Frequency**: `1MIN`, `15MIN`, `1H`, `1D`, `1W`.
- **Energy Unit**: `KilowattHours` or `Dollars`.
- **Date Format**: `yyyy-MM-dd HH:mm:ss` or `dd-MM-yyyy HH:mm:ss`.

### Debug Logging
- **Enable Debug Logging**: Toggle logs **on/off**.

## Debugging and Logs

Logs provide insights into **authentication**, **device creation**, **data retrieval**, and **attribute updates**. 

### Example Logs
#### Successful Authentication
app:2242025-03-15 10:10:10.123info Authentication successful. Token expires at 2025-03-15T15:10:10Z.

shell
Copy
Edit
#### Data Fetch
app:2242025-03-15 11:11:11.123info Fetching data for all monitored devices... app:2242025-03-15 11:11:11.456debug Updated child device EmporiaVue406720-2: usage=0.0153, power=921, energy=0.02, powerUnit=Watt.

shell
Copy
Edit
#### Device Discovery
app:2242025-03-15 12:12:12.123info Discovered 4 Emporia devices.

markdown
Copy
Edit

## Known Limitations
- **Refreshing a child device** triggers data retrieval for **all devices under the same parent**.
- **InfluxDB** exports **device labels**, which is now addressed by **renaming** devices during creation.

## Future Enhancements
- **Support for additional energy units** beyond KilowattHours/Dollars.
- **Optimize logging verbosity** for a better experience.

## Support
For **questions or issues**, submit an **issue** on GitHub.

## Author
**Amit Halperin**

## What’s New?
✅ **Support for multiple (even nested) EmporiaVue devices**
✅ **Automatic Device Labeling**  
✅ **Ensures Unique Names in InfluxDB/Grafana**  
✅ **Improved Debug Logging & Device Updates**  
✅ **Rescheduling token refresh in case of failure**

This version ensures **better compatibility** with **InfluxDB, Grafana**, and **Hubitat Dashboard**, making energy monitoring more **accurate and intuitive**.
### v1.1.0 (2026-01-18)
- Automatic token refresh when API returns HTTP 401, with an immediate retry of the failed fetch once the refresh succeeds.
- Throttled refresh attempts to avoid rapid retry storms (minimum 60 seconds between automatic refresh attempts).
- Differentiation between network/server failures and authentication failures — network outages won't force manual authentication and will recover automatically when connectivity returns.
- Backoff strategy for network-related refresh failures (retry every 5 minutes).
- Cap authentication failure retries (5 attempts) before requiring manual Authenticate and setting state.authStatus accordingly.
- Updated authenticate() to clear manual-auth flags and failure counters on success.
- Updated scheduling behavior: updated() now unschedules only the data retrieval job instead of all scheduled jobs.

#### Testing v1.1.0
To test the new features:
1. **Authentication success**: After clicking Authenticate, verify that `state.authStatus` shows success message and `state.manualAuthRequired` is `false`.
2. **401 triggers auto-refresh**: Force a 401 response (e.g., by waiting for token expiry or manually invalidating token). Confirm the app automatically refreshes tokens and retries the failed fetch within ~1 minute.
3. **Network outage recovery**: Simulate a network outage (e.g., disconnect network). Confirm data fetch logs network warnings without requiring manual authentication. When connectivity returns, confirm automatic recovery.
4. **Invalid refresh token**: Revoke the refresh token or simulate a Cognito `NotAuthorizedException`. Confirm the auth failure counter increments, and after 5 auth failures, `state.manualAuthRequired` becomes `true` and logs instruct to manually Authenticate.
