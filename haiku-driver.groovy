metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
        capability "Switch"
    }
}

preferences {
    section("Device Selection") {
        input("deviceName", "text", title: "Device Name", description: "", required: true, defaultValue: "")
        input("deviceIp", "text", title: "Device IP Address", description: "", required: true, defaultValue: "")
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.debug "installed"
}

def initialize() {
    log.debug "initialized"
}

def updated() {
    log.debug "updated"
}

def parse(String description) {
    log.debug "parse description: ${description}"
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def on() {
    sendLightPowerCommand("ON")
}

def off() {
    sendLightPowerCommand("OFF")
}

def sendLightPowerCommand(String command) {
    sendCommand("LIGHT", "PWR", command)
    sendEvent(name: "switch", value: "${command}", isStateChange: true)
}

def setLevel(level) {
    setLevel(level, 0)
}

def setLevel(level, duration) {
    sendLightLevelCommand(level)
    sendEvent(name: "level", value: "${level}", isStateChange: true)
}

def sendLightLevelCommand(level) {
    if (level > 16) {
        level = 16
    }
    if (level < 0) {
        level = 0
    }

    sendCommand("LIGHT", "LEVEL", "SET;${level}")
}

def setSpeed(fanspeed){
    switch (fanspeed) {
        case "on":
            sendFanPowerCommand("ON")
            break
        case "off":
            sendFanPowerCommand("OFF")
            break
        case "low":
            sendFanSpeedCommand(1)
            break
        case "medium-low":
            sendFanSpeedCommand(2)
            break
        case "medium":
            sendFanSpeedCommand(4)
            break
        case "medium-high":
            sendFanSpeedCommand(6)
            break
        case "high":
            sendFanSpeedCommand(7)
            break
    }
}

def sendFanPowerCommand(String command) {
    sendCommand("FAN", "PWR", command)
}

def refreshFanSpeed() {
    sendCommand("FAN", "SPD", "GET;ACTUAL")
}

def sendFanSpeedCommand(int level) {
    sendCommand("FAN", "SPD", "SET;${level}")
}

def sendCommand(String haikuSubDevice, String haikuFunction, String command) {
        def haikuCommand = generateCommand(haikuSubDevice, haikuFunction, command)
        sendUDPRequest(settings.deviceIp, "31415", haikuCommand)
}

static def generateCommand(haikuSubDevice, haikuFunction, command) {
    return "<ALL;${haikuSubDevice};${haikuFunction};${command}>"
}

def sendUDPRequest(address, port, payload) {
    def hubAction = new hubitat.device.HubAction(payload,
            hubitat.device.Protocol.LAN,
            [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
             destinationAddress: "${address}:${port}"])
    sendHubCommand(hubAction)
}