/* ----------------------------------------------------------------------------------------------------------------
 * EmporiaVueIntegration_AutoRetry
 *
 * This is a forked variant of EmporiaVueIntegration (v1.1.2) intended for safer unattended recovery:
 * - Keeps the original behavior, but avoids getting permanently "wedged" in manualAuthRequired after transient issues.
 * - When manualAuthRequired becomes true, the app will continue to attempt token refresh on a slow backoff schedule.
 *
 * Original source: https://github.com/amithalp/EmporiaV2
 * ----------------------------------------------------------------------------------------------------------------
 */
/**
 * NOTE
 * - This file is intentionally separate so you can keep your original app code unchanged.
 * - Install in Hubitat as a NEW app (different name) so you can switch between them.
 */

import groovy.json.*

static String version() { return '1.1.2-autoretry.1' }

definition(
    name: "EmporiaVueIntegration_AutoRetry",
    namespace: "amithalp",
    author: "Amit Halperin",
    description: "Emporia Vue integration for Hubitat with token management and device creation (auto-retry token refresh even after manual-auth flag).",
    category: "Utility",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "discoverDevicesPage")
}

//------------------------------- App pages --------------------------------------------------------------------------------------------------------------
def mainPage() {
    dynamicPage(name: "mainPage", title: " EmporiaVue Integration (AutoRetry) v${version()} ", install: true, uninstall: true) {
        section(" Emporia Account Settings Enter the same email and password as in the Emporia phone app. Then click Update. ") {
            input "email", "text", title: "Emporia Email", required: true, width: 6
            input "password", "password", title: "Emporia Password", required: true, width: 6
            input name: "updateCredentialsButton", type: "button", title: "Update"
        }
        section(" Authentication Click the Authenticate button to start authentication. ") {
            input name: "authenticateButton", type: "button", title: "Authenticate"
            paragraph " Auth status: " + (state.authStatus ?: " No authentication attempt has been made yet. ")
        }
        section(" Device Management ") {
            href(name: "discoverDevices", title: "Discover Devices", required: false, page: "discoverDevicesPage")
        }
        section(" Data Retrieval Settings ") {
            input name: "retrievalFrequency", type: "enum", title: "Retrieval Frequency", options: ["1MIN", "15MIN", "1H", "1D", "1W"], submitOnChange: true, width: 4
            input name: "energyUnit", type: "enum", title: "Energy Unit", options: ["KilowattHours", "Dollars"], submitOnChange: true, width: 4
            input name: "dateFormat", type: "enum", title: "Date Format", options: ["yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy HH:mm:ss"], submitOnChange: true, width: 4
            paragraph "Click Save Changes to apply the data retrieval settings."
            input name: "applySettingsButton", type: "button", title: "Save Changes"
        }
        section(" Health Monitoring Notifications (Optional) ") {
            input name: "enableHealthNotifications",
                type: "bool",
                title: "Enable health notifications",
                defaultValue: false,
                submitOnChange: true

            if (enableHealthNotifications) {

                input name: "notificationDevices",
                    type: "capability.notification",
                    title: "Notification device(s)",
                    multiple: true,
                    required: false

                input name: "notificationMessageFailure",
                    type: "text",
                    title: "Failure notification text",
                    defaultValue: "Emporia app alert: data fetch is failing.",
                    required: false

                input name: "notificationMessageRecovery",
                    type: "text",
                    title: "Recovery notification text",
                    defaultValue: "Emporia app recovery: data fetch is working again.",
                    required: false

                input name: "notifyAfterMode",
                    type: "enum",
                    title: "Send failure notification after",
                    options: [
                        "tries" : "X failed fetch tries",
                        "minutes" : "X minutes without success",
                        "tries_or_minutes": "Either X tries or X minutes"
                    ],
                    defaultValue: "tries_or_minutes",
                    required: true,
                    submitOnChange: true

                if (notifyAfterMode in ["tries", "tries_or_minutes"]) {
                    input name: "notifyAfterTries",
                        type: "number",
                        title: "Number of failed fetch tries",
                        defaultValue: 5,
                        range: "1..1000",
                        required: true
                }

                if (notifyAfterMode in ["minutes", "tries_or_minutes"]) {
                    input name: "notifyAfterMinutes",
                        type: "number",
                        title: "Number of minutes without successful fetch",
                        defaultValue: 10,
                        range: "1..10080",
                        required: true
                }

                input name: "sendRecoveryNotification",
                    type: "bool",
                    title: "Send recovery notification when app starts working again",
                    defaultValue: true,
                    required: false
            }
        }
        section(" Debug Settings ") {
            input name: "debugLog", type: "bool", title: "Enable Debug Logging", defaultValue: true, required: false
        }
        section(" Important ") {
            paragraph "Click Done to save the app after completing setup. The app will not be saved otherwise."
            paragraph "If you click Remove the app will be deleted along with all devices created by the app."
        }

    }
}

def discoverDevicesPage() {
    dynamicPage(name: "discoverDevicesPage") {
        section(" Discover Emporia Devices ") {
            paragraph "Click Find Devices to discover available devices."
            input name: "findDevicesButton", type: "button", title: "Find Devices"
            if (state.discoveredDevices?.isEmpty()) {
                paragraph "No devices found. Click Find Devices to start discovery."
            } else {
                state.discoveredDevices.each { device ->
                    input name: "selectedDevice_${device.deviceGid}", type: "bool", title: "${device.deviceGid} - ${device.manufacturerDeviceId}", required: false
                }
                input name: "updateSelectionButton", type: "button", title: "Update Selection"
                input name: "createDevicesButton", type: "button", title: "Create/Update/Delete Hubitat Devices"
                paragraph "Clicking the Create/Update/Delete Hubitat Devices button will compare your current Hubitat devices with the EmporiaVue circuits and either create, update, or delete Hubitat devices accordingly."
            }
        }
    }
}

//------------------------------- Basic app functions ----------------------------------------------------------------------------------------------------
def installed() {
    log.info "EmporiaVueIntegration_AutoRetry app installed"
    initialize()
}

def updated() {
    log.info "EmporiaVueIntegration_AutoRetry app updated"
    unschedule(fetchData) // Only unschedule the data fetch job
    initialize()
}

def initialize() {
    state.pendingRetry = (state.pendingRetry != null) ? state.pendingRetry : false
    state.pendingFetchDeviceGid = (state.pendingFetchDeviceGid != null) ? state.pendingFetchDeviceGid : null

    state.debugLog = (settings.debugLog != null) ? settings.debugLog : true
    if (state.debugLog) log.debug "Debug logging is ${state.debugLog ? 'enabled' : 'disabled'}."

    log.info "Initializing EmporiaVueIntegration_AutoRetry app"
    state.authStatus = state.authStatus ?: "Waiting for user to authenticate..."
    state.discoveredDevices = state.discoveredDevices ?: []
    state.emporiaDeviceGids = state.emporiaDeviceGids ?: []

    state.lastRefreshAttempt = state.lastRefreshAttempt ?: 0
    state.refreshFailureCount = state.refreshFailureCount ?: 0
    state.authFailureCount = state.authFailureCount ?: 0
    state.manualAuthRequired = state.manualAuthRequired ?: false

    // AutoRetry additions: even if manualAuthRequired becomes true, keep trying refresh on slow backoff.
    state.manualAuthBackoffMinutes = (state.manualAuthBackoffMinutes ?: 60) as Integer   // start with 60 minutes
    state.manualAuthNextRetryAt = (state.manualAuthNextRetryAt ?: 0L) as Long

    // Health / notification tracking
    state.lastSuccessfulFetch = state.lastSuccessfulFetch ?: null
    state.firstFailureTime = state.firstFailureTime ?: null
    state.lastFailureTime = state.lastFailureTime ?: null
    state.consecutiveFetchFailures = state.consecutiveFetchFailures ?: 0
    state.lastFetchError = state.lastFetchError ?: null
    state.lastFetchErrorType = state.lastFetchErrorType ?: null
    state.failureAlertSent = state.failureAlertSent ?: false
    state.manualAuthAlertSent = state.manualAuthAlertSent ?: false

    if (settings.enableHealthNotifications && !settings.notificationDevices) {
        log.warn "Health notifications are enabled but no notification devices are selected."
    }

    if (state.tokenExpiry) {
        if (state.debugLog) log.debug "Token expiry detected. Scheduling token refresh."
        scheduleTokenRefresh()
    } else {
        if (state.debugLog) log.debug "No token expiry found. Skipping token refresh scheduling."
    }

    if (settings.retrievalFrequency) {
        if (state.debugLog) log.debug "Scheduling data retrieval with frequency: ${settings.retrievalFrequency}."
        scheduleDataRetrieval()
    } else {
        log.warn "Retrieval frequency not set. Skipping data retrieval scheduling."
    }
}

def uninstalled() {
    getChildDevices().each { device ->
        deleteChildDevice(device.deviceNetworkId)
    }
    // optional cleanup
    state.parentDevices = []
    state.emporiaDeviceGids = []
    log.info "All child devices deleted."
}

def truncateToTwoDecimals(value) {
    return Math.floor(value * 100) / 100
}

def getFormattedDate() {
    def format = settings.dateFormat ?: "yyyy-MM-dd HH:mm:ss"
    return new Date().format(format, location.timeZone)
}

// Handle button clicks
def appButtonHandler(buttonName) {
    state.updatedSettings = [
        retrievalFrequency: settings.retrievalFrequency,
        energyUnit: settings.energyUnit
    ]

    switch (buttonName) {
        case "updateCredentialsButton":
            updateCredentials()
            break
        case "authenticateButton":
            authenticate()
            break
        case "findDevicesButton":
            discoverDevices()
            break
        case "updateSelectionButton":
            updateSelectedDevices()
            break
        case "applySettingsButton":
            applySettings()
            break
        case "createDevicesButton":
            createSelectedDevices()
            break
        default:
            log.warn "Unhandled button click: $buttonName"
    }
}

//------------------------------- Authentication process -------------------------------------------------------------------------------------------------
def updateCredentials() {
    if (!settings.email || !settings.password) {
        log.warn "Email and password must be provided to update credentials."
    } else {
        state.authStatus = "Credentials updated. You may now authenticate."
        log.info state.authStatus
    }
}

def authenticate() {
    if (!settings.email || !settings.password) {
        state.authStatus = " Error: Email and password must be provided for authentication. "
        log.error "Authentication failed: Email and password are required."
        return
    }

    def authEndpoint = "https://cognito-idp.us-east-2.amazonaws.com/"
    def headers = [
        "Content-Type": "application/x-amz-json-1.1",
        "X-Amz-Target": "AWSCognitoIdentityProviderService.InitiateAuth"
    ]
    def body = JsonOutput.toJson([
        AuthFlow: "USER_PASSWORD_AUTH",
        ClientId: "4qte47jbstod8apnfic0bunmrq",
        AuthParameters: [
            USERNAME: settings.email,
            PASSWORD: settings.password
        ]
    ])
    def params = [uri: authEndpoint, headers: headers, body: body]

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                def responseData = new JsonSlurper().parseText(resp.getData().getText('UTF-8'))
                if (responseData.AuthenticationResult) {
                    state.idToken = responseData.AuthenticationResult.IdToken
                    state.accessToken = responseData.AuthenticationResult.AccessToken
                    state.refreshToken = responseData.AuthenticationResult.RefreshToken
                    state.tokenExpiry = now() + (responseData.AuthenticationResult.ExpiresIn * 1000)

                    state.manualAuthRequired = false
                    state.authFailureCount = 0
                    state.refreshFailureCount = 0
                    state.lastRefreshAttempt = 0
                    state.pendingFetchDeviceGid = null
                    state.pendingRetry = false
                    state.manualAuthBackoffMinutes = 60
                    state.manualAuthNextRetryAt = 0L

                    state.authStatus = " Authentication successful. Token expires at ${new Date(state.tokenExpiry)}. "
                    resetFetchHealthState()
                    log.info "Authentication successful for user: ${settings.email}"

                    scheduleTokenRefresh()
                } else {
                    state.authStatus = " Error: AuthenticationResult missing in response. "
                    log.error "Authentication failed: Missing AuthenticationResult in response."
                }
            } else {
                state.authStatus = " Error: Authentication failed. HTTP status: ${resp.status}. "
                log.error "Authentication failed. HTTP Status: ${resp.status}"
            }
        }
    } catch (e) {
        state.authStatus = " Error during authentication: ${e.message} "
        log.error "Authentication error: ${e.message}"
    }
}

def scheduleTokenRefresh() {
    if (state.tokenExpiry) {
        def refreshTime = (state.tokenExpiry - now() - 300000) / 1000 // Refresh 5 minutes before expiry
        if (refreshTime > 0) {
            runIn(refreshTime.toInteger(), refreshToken)
            log.info "Token refresh scheduled in ${refreshTime.toInteger()} seconds."
            state.authStatus = " Token refresh scheduled. Expires at ${new Date(state.tokenExpiry)}. "
        } else {
            log.warn "Token already expired or too close to expiry. Immediate refresh required."
            refreshToken()
        }
    } else {
        log.warn "Token expiry time not available. Cannot schedule refresh."
        state.authStatus = " Unable to schedule token refresh: Token expiry unavailable. "
    }
}

// refreshToken(forceRetry = false) - forceRetry indicates caller expects a retry of pending fetch after success
def refreshToken(forceRetry = false) {
    // AutoRetry change:
    // - Do NOT permanently block refresh attempts just because manualAuthRequired is true.
    // - Instead, allow refresh attempts on a slow backoff schedule (plus immediate attempts when forceRetry is true).
    if (state.manualAuthRequired && !forceRetry) {
        Long nextAt = (state.manualAuthNextRetryAt ?: 0L) as Long
        if (nextAt > now()) {
            if (state.debugLog) log.debug "manualAuthRequired=true; next refresh attempt scheduled at ${new Date(nextAt)}. Skipping for now."
            return
        }
        if (state.debugLog) log.debug "manualAuthRequired=true; attempting slow backoff refresh now."
    }

    def authEndpoint = "https://cognito-idp.us-east-2.amazonaws.com/"
    def headers = [
        "Content-Type": "application/x-amz-json-1.1",
        "X-Amz-Target": "AWSCognitoIdentityProviderService.InitiateAuth"
    ]
    def body = JsonOutput.toJson([
        AuthFlow: "REFRESH_TOKEN_AUTH",
        ClientId: "4qte47jbstod8apnfic0bunmrq",
        AuthParameters: [
            REFRESH_TOKEN: state.refreshToken
        ]
    ])
    def params = [uri: authEndpoint, headers: headers, body: body]

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                def responseData = new JsonSlurper().parseText(resp.getData().getText('UTF-8'))
                if (responseData.AuthenticationResult) {
                    state.idToken = responseData.AuthenticationResult.IdToken
                    state.tokenExpiry = now() + (responseData.AuthenticationResult.ExpiresIn * 1000)
                    state.authStatus = " Token refreshed successfully. Expires at ${new Date(state.tokenExpiry)}. "
                    log.info "Token refreshed successfully. New expiry: ${new Date(state.tokenExpiry)}."
                    state.tokenRefreshRetries = 0
                    state.refreshFailureCount = 0
                    state.authFailureCount = 0
                    state.manualAuthRequired = false
                    state.manualAuthBackoffMinutes = 60
                    state.manualAuthNextRetryAt = 0L
                    resetFetchHealthState()
                    scheduleTokenRefresh()

                    if (forceRetry || state.pendingRetry) {
                        runIn(2, performPendingFetch)
                    }
                } else {
                    log.error "Token refresh response missing AuthenticationResult."
                    handleRefreshFailure('auth')
                }
            } else {
                def responseText = resp.getData()?.getText('UTF-8') ?: ""
                def lower = responseText.toLowerCase()
                if (lower.contains('notauthorized') || lower.contains('not authorized') || lower.contains('not_authorized')) {
                    log.error "Token refresh failed: Cognito NotAuthorizedException or invalid refresh token."
                    handleRefreshFailure('auth')
                } else {
                    log.warn "Token refresh failed with HTTP status ${resp.status}. Will treat as network/server error and retry with backoff."
                    handleRefreshFailure('network')
                }
            }
        }
    } catch (e) {
        def msg = e?.message?.toString() ?: ""
        def lower = msg.toLowerCase()
        def isNetworkError = lower.contains('unknownhost') || lower.contains('connect') || lower.contains('timed out') || lower.contains('timeout') || lower.contains('connection refused')
        if (isNetworkError) {
            log.warn "Network error during token refresh: ${msg}. Will back off and retry."
            handleRefreshFailure('network')
        } else {
            log.error "Unexpected error during token refresh: ${msg}."
            handleRefreshFailure('auth')
        }
    }
}

def handleRefreshFailure(type = 'network') {
    if (type == 'network') {
        state.refreshFailureCount = (state.refreshFailureCount ?: 0) + 1
        def retryDelaySec = 5 * 60
        log.warn "Scheduling token refresh retry #${state.refreshFailureCount} in ${retryDelaySec} seconds (network/server error)."
        runIn(retryDelaySec, refreshToken)
        state.authStatus = " Token refresh failed due to network/server error. Will retry in ${retryDelaySec/60} minutes. "
        return
    }

    state.authFailureCount = (state.authFailureCount ?: 0) + 1
    def maxAuthAttempts = 5

    if (state.authFailureCount >= maxAuthAttempts) {
        state.manualAuthRequired = true
        state.authStatus = " Token refresh failed: refresh token invalid or revoked. Manual Authenticate required. "

        sendManualAuthRequiredNotificationIfNeeded()

        // AutoRetry change:
        // Even in manualAuthRequired, keep attempting refresh on a slow backoff schedule.
        Integer minutes = (state.manualAuthBackoffMinutes ?: 60) as Integer
        if (minutes < 15) minutes = 15
        if (minutes > (12 * 60)) minutes = 12 * 60 // cap at 12h

        Long nextAt = now() + (minutes * 60 * 1000L)
        state.manualAuthNextRetryAt = nextAt
        log.warn "manualAuthRequired=true. Will continue slow refresh attempts every ~${minutes} minutes. Next attempt at ${new Date(nextAt)}."
        runIn((minutes * 60) as Integer, refreshToken)

        // increase backoff for next time, capped
        state.manualAuthBackoffMinutes = Math.min(minutes * 2, 12 * 60) as Integer

        log.error "Token refresh auth failures reached ${state.authFailureCount}. Manual Authenticate is required, but auto-retry will continue in the background."
        return
    } else {
        def retryDelaySec = 60
        log.warn "Token refresh auth failure #${state.authFailureCount}. Will retry in ${retryDelaySec} seconds (up to ${maxAuthAttempts} attempts)."
        runIn(retryDelaySec, refreshToken)
        state.authStatus = " Token refresh authorization failed (attempt ${state.authFailureCount}/${maxAuthAttempts}). Will retry in ${retryDelaySec} seconds. "
        return
    }
}

//------------------------------- Device discovery and creation ---------------------------------------------------------------------------------------
def discoverDevices() {
    def host = "https://api.emporiaenergy.com/"
    def command = "customers/devices"
    try {
        httpGet([uri: "${host}${command}", headers: ['authtoken': state.idToken]]) { resp ->
            if (resp.status == 200) {
                state.discoveredDevices = resp.data.devices
                log.info "Discovered devices: ${state.discoveredDevices.size()}"
            } else {
                log.error "Failed to discover devices. HTTP status: ${resp.status}"
            }
        }
    } catch (e) {
        log.error "Error during device discovery: ${e.message}"
    }
}

def updateSelectedDevices() {
    log.info "Selected devices updated."
}

def createSelectedDevices() {
    state.validChildDeviceIds = []
    def selectedDevices = state.discoveredDevices.findAll { settings["selectedDevice_${it.deviceGid}"] == true }

    if (!selectedDevices) {
        log.warn "No devices selected for creation."
        return
    }

    // rebuild numeric list from selections
    state.emporiaDeviceGids = selectedDevices.collect { it.deviceGid.toString() }

    state.parentDevices = selectedDevices.collect { device ->
        def deviceId = "EmporiaVue${device.deviceGid}"
        def parentDevice = getChildDevice(deviceId) ?: addChildDevice(
            "amithalp",
            "EmporiaVueParentDriver",
            deviceId,
            [name: deviceId, isComponent: false]
        )

        if (parentDevice) {
            log.info "Ensured parent device: ${deviceId}"
            def usageData = fetchInitialDeviceData(device.deviceGid)
            def validChildDeviceIds = []

            usageData?.each { channel ->
                def allChannels = [channel] + (channel.nestedDevices ?: [])
                allChannels.each { nestedChannel ->
                    if (nestedChannel.usage != null && nestedChannel.name) {
                        def childDeviceId = "${deviceId}-${nestedChannel.channelNum ?: nestedChannel.name.hashCode()}"
                        validChildDeviceIds << childDeviceId

                        def childName = nestedChannel.name
                        def formattedLabel = "Emp-${device.deviceGid}-${childName} (${nestedChannel.channelNum ?: 'N/A'})"

                        def existingChildDevice = parentDevice.getChildDevice(childDeviceId)
                        if (!existingChildDevice) {
                            parentDevice.addChildDevice(
                                "amithalp",
                                "Emporia Circuit Driver",
                                childDeviceId,
                                [name: childName, label: formattedLabel, isComponent: true]
                            )
                            log.info "Created child device: ${childDeviceId} with label: ${formattedLabel}"
                        } else {
                            if (existingChildDevice.getLabel() != formattedLabel) {
                                existingChildDevice.setLabel(formattedLabel)
                                log.info "Updated child device label: ${childDeviceId} -> ${formattedLabel}"
                            } else {
                                log.info "Child device label is already correct: ${childDeviceId}"
                            }
                        }
                    }
                }
            }

            state.validChildDeviceIds = (state.validChildDeviceIds ?: []) + validChildDeviceIds
        }

        deviceId
    }

    runIn(20, deleteOrphanedDevices)

    // ensure scheduling exists once parents exist
    if (settings.retrievalFrequency) scheduleDataRetrieval()
}

def deleteOrphanedDevices() {
    if (!state.validChildDeviceIds || state.validChildDeviceIds.isEmpty()) {
        log.warn "No valid child device IDs recorded. Skipping orphan deletion."
        return
    }
    state.parentDevices.each { parentId ->
        def parentDevice = getChildDevice(parentId)
        if (parentDevice) {
            def existingChildIds = parentDevice.getChildDevices().collect { it.deviceNetworkId }
            def orphanIds = existingChildIds - state.validChildDeviceIds

            orphanIds.each { orphanId ->
                parentDevice.deleteChildDevice(orphanId)
                log.info "Deleted orphaned child device: ${orphanId}"
            }
        }
    }
}

def fetchInitialDeviceData(deviceGid) {
    def host = "https://api.emporiaenergy.com/"
    def instant = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    def command = "AppAPI?apiMethod=getDeviceListUsages&deviceGids=${deviceGid}&instant=${instant}&scale=1H&energyUnit=KilowattHours"

    try {
        def response = httpGet([uri: "${host}${command}", headers: ['authtoken': state.idToken]]) { resp -> resp.data }
        if (response?.deviceListUsages?.devices?.size() > 0) {
            def devices = response.deviceListUsages.devices
            if (state.debugLog) log.debug "Fetched usage data for device ${deviceGid}: ${devices}"
            return devices[0].channelUsages
        } else {
            log.warn "No usage data returned for device ${deviceGid}."
            return null
        }
    } catch (e) {
        log.error "Error fetching usage data for device ${deviceGid}: ${e.message}"
        return null
    }
}

//------------------------------- Data retrival handling -------------------------------------------------------------------------------------------------
def applySettings() {
    log.info "Applying settings... Current: Frequency=${state.retrievalFrequency}, Unit=${state.energyUnit}, DateFormat=${state.dateFormat}"

    def frequencyChanged = settings.retrievalFrequency != state.retrievalFrequency
    def unitChanged = settings.energyUnit != state.energyUnit
    def dateFormatChanged = settings.dateFormat != state.dateFormat

    state.retrievalFrequency = settings.retrievalFrequency
    state.energyUnit = settings.energyUnit
    state.dateFormat = settings.dateFormat

    log.info "Updated settings: Frequency=${state.retrievalFrequency}, Unit=${state.energyUnit}, DateFormat=${state.dateFormat}"
    if (frequencyChanged) {
        log.info "Retrieval frequency changed. Initiating reschedule..."
        scheduleDataRetrieval()
    }

    if (unitChanged || dateFormatChanged) {
        log.info "Settings updated successfully. Non-frequency changes applied: Energy Unit and/or Date Format."
    }
}

def scheduleDataRetrieval() {
    if (!state.emporiaDeviceGids || state.emporiaDeviceGids.isEmpty()) {
        log.warn "No Emporia deviceGids found. Skipping data retrieval scheduling."
        return
    }

    try {
        unschedule(fetchData)

        switch (settings.retrievalFrequency) {
            case "1MIN":
                schedule("0 * * * * ?", fetchData)
                break
            case "15MIN":
                schedule("0 */15 * * * ?", fetchData)
                break
            case "1H":
                schedule("0 0 * * * ?", fetchData)
                break
            case "1D":
                schedule("0 0 0 * * ?", fetchData)
                break
            case "1W":
                schedule("0 0 0 ? * 1", fetchData)
                break
            default:
                log.error "Unsupported retrieval frequency: ${settings.retrievalFrequency}"
                return
        }

        log.info "Data retrieval scheduled using CRON for every ${settings.retrievalFrequency}."
    } catch (e) {
        log.error "Failed to schedule data retrieval: ${e.message}"
    }
}

def fetchData(deviceGid = null) {
    log.info deviceGid ? "Fetching data for device GID: ${deviceGid}" : "Fetching data for all monitored devices..."

    def targetGids = deviceGid ? [deviceGid.toString()] : (state.emporiaDeviceGids ?: [])
    if (!targetGids || targetGids.isEmpty()) {
        log.warn "No monitored devices found. Skipping data fetch."
        return
    }

    def instant = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    def host = "https://api.emporiaenergy.com/"
    def deviceGids = targetGids.join("+")
    def command = "AppAPI?apiMethod=getDeviceListUsages&deviceGids=${deviceGids}&instant=${instant}&scale=${settings.retrievalFrequency}&energyUnit=${settings.energyUnit}"

    def params = [
        uri: "${host}${command}",
        headers: ['authtoken': state.idToken]
    ]

    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (state.debugLog) log.debug "Raw API Response: ${resp.data}"

                def data = resp.data?.deviceListUsages?.devices
                if (!data) {
                    log.warn "No usage data found in response."
                    recordFetchFailure("empty_response", "No usage data found in response")
                    return
                }

                data.each { device ->
                    def parentDeviceId = "EmporiaVue${device.deviceGid}"
                    def parentDevice = getChildDevice(parentDeviceId)
                    if (!parentDevice) {
                        log.warn "Parent device ${parentDeviceId} not found. Skipping."
                        return
                    }
                    processChannelUsages(parentDevice, device.channelUsages, parentDeviceId)
                }

                recordFetchSuccess()
                log.info "Data fetch and update completed successfully."
            } else if (resp.status == 401) {
                recordFetchFailure("http_401", "HTTP 401 Unauthorized")
                log.warn "Received HTTP 401 from API response. Will attempt token refresh and retry (throttled)."
                attemptRefreshAndRetry(deviceGid)
                return
            } else {
                recordFetchFailure("http_status", "HTTP status: ${resp.status}")
                log.error "Failed to fetch data. HTTP status: ${resp.status}"
                return
            }
        }
    } catch (e) {
        def msg = e?.message?.toString() ?: ""
        def lower = msg.toLowerCase()

        def is401Error =
            lower.contains("status code: 401") ||
            lower.contains("status code 401") ||
            lower.contains("http 401") ||
            lower.contains("401 unauthorized") ||
            lower.contains("unauthorized")

        def isNetworkError =
            lower.contains("unknownhost") ||
            lower.contains("connect") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("connection refused")

        if (is401Error) {
            recordFetchFailure("http_401", msg)
            log.warn "Received HTTP 401 during data fetch exception path. Will attempt token refresh and retry (throttled). Error: ${msg}"
            attemptRefreshAndRetry(deviceGid)
            return
        }

        if (isNetworkError) {
            recordFetchFailure("network", msg)
            log.warn "Network error during data fetch (will keep scheduled fetch running): ${msg}"
            return
        }

        recordFetchFailure("exception", msg)
        log.error "Error during data fetch: ${msg}"
        return
    }
}

def updateChildDeviceAttributes(childDevice, channelData) {
    def energyUnit = settings.energyUnit ?: "KilowattHours"
    def usage = channelData.usage ?: 0
    def percentage = truncateToTwoDecimals(channelData.percentage ?: 0)
    def currentTimestamp = getFormattedDate()

    childDevice.sendEvent(name: "usage", value: usage)
    childDevice.sendEvent(name: "usagePercentage", value: percentage)
    childDevice.sendEvent(name: "energyUnit", value: energyUnit)
    childDevice.sendEvent(name: "retrievalFrequency", value: settings.retrievalFrequency)
    childDevice.sendEvent(name: "lastUpdated", value: currentTimestamp)

    def power, energy, powerUnit
    if (energyUnit == "KilowattHours") {
        powerUnit = "Watt"
        switch (settings.retrievalFrequency) {
            case "1MIN":
                power = Math.round(usage * 60 * 1000)
                energy = Math.round(usage * 100) / 100.0
                break
            case "15MIN":
                power = Math.round(usage * 4 * 1000)
                energy = Math.round(usage * 100) / 100.0
                break
            case "1H":
                power = Math.round(usage * 1000)
                energy = Math.round(usage * 100) / 100.0
                break
            case "1D":
                power = Math.round((usage / 24) * 1000)
                energy = Math.round(usage * 100) / 100.0
                break
            case "1W":
                power = Math.round(usage / 24 / 7 * 1000)
                energy = Math.round(usage * 100) / 100.0
                break
        }
    } else {
        powerUnit = null
        power = null
        energy = usage
    }

    childDevice.sendEvent(name: "power", value: power)
    childDevice.sendEvent(name: "energy", value: energy)
    childDevice.sendEvent(name: "powerUnit", value: powerUnit)

    if (state.debugLog) {
        log.debug "Updated child device ${childDevice.deviceNetworkId}: usage=${usage}, power=${power}, energy=${energy}, powerUnit=${powerUnit}, lastUpdated=${currentTimestamp}"
    }
}

def processChannelUsages(parentDevice, channelUsages, parentDeviceId) {
    channelUsages.each { channel ->
        def channelNum = channel.channelNum ?: channel.name.hashCode()
        def childDeviceId = "${parentDeviceId}-${channelNum}"
        def childDevice = parentDevice.getChildDevice(childDeviceId)

        if (childDevice) {
            updateChildDeviceAttributes(childDevice, channel)
        } else {
            log.warn "Child device ${childDeviceId} not found under parent ${parentDeviceId}. Skipping update."
        }

        if (channel.nestedDevices && channel.nestedDevices.size() > 0) {
            channel.nestedDevices.each { nestedDevice ->
                def nestedParentDeviceId = "EmporiaVue${nestedDevice.deviceGid}"
                def nestedParentDevice = getChildDevice(nestedParentDeviceId)

                if (nestedParentDevice) {
                    processChannelUsages(nestedParentDevice, nestedDevice.channelUsages, nestedParentDeviceId)
                } else {
                    log.warn "Nested parent device ${nestedParentDeviceId} not found. Skipping nested updates."
                }
            }
        }
    }
}

private void attemptRefreshAndRetry(deviceGid = null) {
    // AutoRetry: still throttle refresh attempts, but do not block just because manualAuthRequired is true.
    def nowTs = now()
    def lastAttempt = state.lastRefreshAttempt ?: 0
    def minIntervalMs = 60 * 1000

    if ((nowTs - lastAttempt) < minIntervalMs) {
        if (state.debugLog) log.debug "Refresh attempt throttled. Last attempt ${(nowTs - lastAttempt)} ms ago."
        state.pendingFetchDeviceGid = deviceGid
        runIn(((minIntervalMs - (nowTs - lastAttempt)) / 1000).toInteger() + 5, performPendingFetch)
        return
    }

    state.lastRefreshAttempt = nowTs
    state.pendingFetchDeviceGid = deviceGid
    state.pendingRetry = true
    refreshToken(true)
}

def performPendingFetch() {
    def gid = state.pendingFetchDeviceGid
    state.pendingFetchDeviceGid = null
    state.pendingRetry = false
    if (gid) {
        fetchData(gid)
    } else {
        fetchData()
    }
}

private void recordFetchSuccess() {
    def hadFailureAlert = (state.failureAlertSent == true)

    state.lastSuccessfulFetch = now()
    state.firstFailureTime = null
    state.lastFailureTime = null
    state.consecutiveFetchFailures = 0
    state.lastFetchError = null
    state.lastFetchErrorType = null

    if (state.debugLog) {
        log.debug "Fetch health reset after successful data fetch."
    }

    if (hadFailureAlert) {
        sendRecoveryNotificationIfNeeded()
    }

    state.failureAlertSent = false
    state.manualAuthAlertSent = false
}

private void recordFetchFailure(String errorType = "unknown", String errorMessage = "") {
    if (!state.firstFailureTime) {
        state.firstFailureTime = now()
    }

    state.lastFailureTime = now()
    state.consecutiveFetchFailures = (state.consecutiveFetchFailures ?: 0) + 1
    state.lastFetchErrorType = errorType
    state.lastFetchError = errorMessage ?: "Unknown fetch error"

    if (state.debugLog) {
        log.debug "Fetch failure recorded. type=${state.lastFetchErrorType}, count=${state.consecutiveFetchFailures}, message=${state.lastFetchError}"
    }

    sendFailureNotificationIfNeeded()
}

private Long getCurrentFailureDurationMinutes() {
    if (!state.firstFailureTime) return 0L
    return ((now() - (state.firstFailureTime as Long)) / 60000L) as Long
}

private void resetFetchHealthState() {
    state.firstFailureTime = null
    state.lastFailureTime = null
    state.consecutiveFetchFailures = 0
    state.lastFetchError = null
    state.lastFetchErrorType = null
    state.failureAlertSent = false
    state.manualAuthAlertSent = false
}

private Boolean notificationsEnabledAndConfigured() {
    return settings.enableHealthNotifications == true && settings.notificationDevices
}

private Integer getNotifyAfterTriesThreshold() {
    return (settings.notifyAfterTries ?: 5) as Integer
}

private Integer getNotifyAfterMinutesThreshold() {
    return (settings.notifyAfterMinutes ?: 10) as Integer
}

private String getFailureNotificationBaseText() {
    return settings.notificationMessageFailure ?: "Emporia app alert: data fetch is failing."
}

private String getRecoveryNotificationBaseText() {
    return settings.notificationMessageRecovery ?: "Emporia app recovery:"
}

private Boolean shouldSendFailureNotification() {
    if (!notificationsEnabledAndConfigured()) return false
    if (state.failureAlertSent == true) return false

    String mode = settings.notifyAfterMode ?: "tries_or_minutes"
    Integer failedTries = (state.consecutiveFetchFailures ?: 0) as Integer
    Long failedMinutes = getCurrentFailureDurationMinutes()

    Integer triesThreshold = getNotifyAfterTriesThreshold()
    Integer minutesThreshold = getNotifyAfterMinutesThreshold()

    switch (mode) {
        case "tries":
            return failedTries >= triesThreshold
        case "minutes":
            return failedMinutes >= minutesThreshold
        case "tries_or_minutes":
            return (failedTries >= triesThreshold) || (failedMinutes >= minutesThreshold)
        default:
            return false
    }
}

private String buildFailureNotificationMessage() {
    String baseText = getFailureNotificationBaseText()
    Integer failedTries = (state.consecutiveFetchFailures ?: 0) as Integer
    Long failedMinutes = getCurrentFailureDurationMinutes()
    String errorType = state.lastFetchErrorType ?: "unknown"
    String errorMsg = state.lastFetchError ?: "Unknown fetch error"

    return "${baseText} Failed tries: ${failedTries}. Failed for: ${failedMinutes} minute(s). Last error type: ${errorType}. Last error: ${errorMsg}"
}

private String buildRecoveryNotificationMessage() {
    String baseText = getRecoveryNotificationBaseText()
    return "${baseText} Data fetching has resumed successfully."
}

private String buildManualAuthRequiredNotificationMessage() {
    String baseText = getFailureNotificationBaseText()
    String errorType = state.lastFetchErrorType ?: "auth"
    String errorMsg = state.lastFetchError ?: "Manual authentication is required"

    return "${baseText} Manual authentication is required. The app could not recover automatically. Last error type: ${errorType}. Last error: ${errorMsg}"
}

private void sendHealthNotification(String msg) {
    if (!notificationsEnabledAndConfigured()) {
        log.warn "Health notification skipped: notifications not enabled or no devices configured."
        return
    }

    log.warn "Sending health notification: ${msg}"

    settings.notificationDevices.each { dev ->
        try {
            log.warn "Sending notification to device: ${dev.displayName}"
            dev.deviceNotification(msg)
            log.warn "Notification command sent to: ${dev.displayName}"
        } catch (e) {
            log.warn "Failed to send notification to ${dev}: ${e?.message}"
        }
    }
}

private void sendFailureNotificationIfNeeded() {
    if (!shouldSendFailureNotification()) return

    String msg = buildFailureNotificationMessage()
    sendHealthNotification(msg)
    state.failureAlertSent = true

    log.warn "Health failure notification sent."
}

private void sendRecoveryNotificationIfNeeded() {
    if (!notificationsEnabledAndConfigured()) return
    if (settings.sendRecoveryNotification != true) return
    if (state.failureAlertSent != true) return

    String msg = buildRecoveryNotificationMessage()
    sendHealthNotification(msg)

    log.info "Health recovery notification sent."
}

private void sendManualAuthRequiredNotificationIfNeeded() {
    if (!notificationsEnabledAndConfigured()) return
    if (state.manualAuthAlertSent == true) return

    String msg = buildManualAuthRequiredNotificationMessage()
    sendHealthNotification(msg)
    state.manualAuthAlertSent = true

    log.warn "Manual authentication required notification sent."
}

