metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
        capability "Switch"
        capability "Refresh"
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

def refresh() {
    sendLightPowerCommand("GET")
    sendCommand("LIGHT", "LEVEL", "GET;ACTUAL")
    refreshFanSpeed()
}

def parse(String description) {
    log.debug "parse description: ${description}"
    def map = parseLanMessage(description)
    def bytes = map["payload"].decodeHex()
    def response = new String(bytes)
    log.debug "parse response: ${response}"
    def values = response[1..-2].split(';')
    switch (values[1]) {
        case "LIGHT":
            switch (values[2]) {
                case "PWR":
                    return createEvent(name: "switch", value: values[3].toLowerCase())
                case "LEVEL":
                    return createEvent(name: "level", value: values[4])
            }
            break
        case "FAN":
            switch (values[2]) {
                case "PWR":
                    switch (values[3]) {
                        case "OFF":
                            return createEvent(name: "speed", value: "off")
                        case "ON":
                            refreshFanSpeed()
                            break;
                    }
                    break
                case "SPD":
                    switch (values[4]) {
                        case "1":
                            return createEvent(name: "speed", value: "low")
                        case "2":
                            return createEvent(name: "speed", value: "medium-low")
                        case "3":
                        case "4":
                            return createEvent(name: "speed", value: "medium")
                        case "5":
                        case "6":
                            return createEvent(name: "speed", value: "medium-high")
                        case "7":
                            return createEvent(name: "speed", value: "high")
                    }
                    break
            }
            break
    }

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
}

def setLevel(level) {
    setLevel(level, 0)
}

def setLevel(level, duration) {
    sendLightLevelCommand(level)
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