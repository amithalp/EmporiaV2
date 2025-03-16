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

This version ensures **better compatibility** with **InfluxDB, Grafana**, and **Hubitat Dashboard**, making energy monitoring more **accurate and intuitive**.
