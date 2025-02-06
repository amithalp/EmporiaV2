# EmporiaVue Integration for Hubitat

This project integrates Emporia Vue devices into the Hubitat platform, enabling energy monitoring and management directly within the Hubitat environment. The integration supports token-based authentication, device discovery, data retrieval, and dynamic updates to child devices.

## Features
- **Authentication**: Secure token-based authentication with Emporia's API.
- **Device Discovery**: Automatically discovers Emporia devices associated with your account.
- **Child Device Creation**: Creates Hubitat devices for each Emporia device and its channels.
- **Data Retrieval**: Scheduled or manual retrieval of usage data.
- **Dynamic Settings**: User-configurable settings for retrieval frequency, energy units, and date formats.
- **Custom Attributes**: Updates child devices with attributes like `usage`, `usagePercentage`, `power`, `energy`, `powerUnit`, `energyUnit`, `retrievalFrequency`, and `lastUpdated`.

## Installation

1. **Add the App and Drivers to Hubitat**:
   - Copy the provided app and drivers code into your Hubitat environment.
   - Add the "EmporiaVueIntegration" app.
   - Add the "EmporiaVueParentDriver" and "Emporia Circuit Driver" drivers.

2. **Install the App**:
   - Go to Apps in the Hubitat interface and add the "EmporiaVueIntegration" app.

3. **Configure Authentication**:
   - Enter your Emporia account email and password.
   - Click the "Authenticate" button to initiate token authentication.

4. **Discover Devices**:
   - Use the "Discover Devices" option to find Emporia devices associated with your account.
   - Select devices and create parent and child devices in Hubitat.

5. **Configure Data Retrieval**:
   - Choose retrieval frequency, energy units (e.g., KilowattHours or Dollars), and date format.
   - Save changes to apply the settings.

## Usage

### Authentication
- **Authenticate**: Initiate token-based authentication using your Emporia credentials.
- **Token Refresh**: Tokens are automatically refreshed 5 minutes before expiration.

### Device Management
- **Discover Devices**: Finds and lists available Emporia devices.
- **Create/Update/Delete Devices**: Adds selected devices as parent and create a child device for every Circuit in Hubitat. As of version 1.0.2, devices will be added, updated or deleted to match the circuits defined in the Emporia app.

### Data Retrieval
- **Scheduled Retrieval**: Retrieves data at the configured frequency.
- **Manual Retrieval**: Use the "Refresh" button on child devices to trigger data fetch for all devices under the same parent.

### Attributes
The integration updates the following attributes on child devices:
- `usage`: Energy usage value returned from the API.
- `usagePercentage`: Percentage of the total energy usage.
- `power`: Calculated power based on `usage` and `retrievalFrequency`.
- `energy`: Energy value, derived directly from `usage`.
- `powerUnit`: Unit of power (e.g., Watts).
- `energyUnit`: Configurable unit for energy (e.g., KilowattHours or Dollars).
- `retrievalFrequency`: Data retrieval interval.
- `lastUpdated`: Timestamp of the last update.

## Settings

### Emporia Account Settings
- Email: Your Emporia account email.
- Password: Your Emporia account password.

### Data Retrieval Settings
- **Retrieval Frequency**: Interval for data retrieval (`1MIN`, `15MIN`, `1H`, `1D`, `1W`).
- **Energy Unit**: Choose between `KilowattHours` or `Dollars`.
- **Date Format**: Select between `yyyy-MM-dd HH:mm:ss` or `dd-MM-yyyy HH:mm:ss`.

### Debug Logging
- **Enable Debug Logging**: Option to enable or disable detailed logs (default: enabled).

## Debugging and Logs
Logs provide detailed information about authentication, device creation, data retrieval, and attribute updates. Use the debug logging option to troubleshoot any issues during setup or operation.

## Example Logs
- **Successful Authentication**:
  ```
  app:2242025-01-12 10:10:10.123info Authentication successful. Token expires at 2025-01-12T15:10:10Z.
  ```
- **Data Fetch**:
  ```
  app:2242025-01-12 11:11:11.123info Fetching data for all monitored devices...
  app:2242025-01-12 11:11:11.456debug Updated child device EmporiaVue123456-1: usage=1.23, power=1230, energy=1.23, powerUnit=Watt.
  ```
- **Device Discovery**:
  ```
  app:2242025-01-12 12:12:12.123info Discovered devices: 3
  ```

## Known Limitations
- Refreshing a child device triggers data retrieval for all devices under the same parent.
- Energy units are limited to `KilowattHours` and `Dollars` for simplicity.

## Future Enhancements
- Add support for more energy units and retrieval intervals.
- Optimize logging verbosity for better user experience.

## Support
For questions or issues, please contact the repository maintainer or submit an issue on GitHub.

---

### Author
**Amit Halperin**

