metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
        capability "Switch"
        capability "Light"
        capability "Refresh"

        command "reverseFan"

        attribute "fanDirection", "string"
    }
}

preferences {
    section("Device Selection") {
        input("deviceName", "text", title: "Device Name", description: "", required: true, defaultValue: "")
        input("deviceIp", "text", title: "Device IP Address", description: "", required: true, defaultValue: "")
        input("logEnable", "bool", title: "Enable debug logging", defaultValue: true)
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

def setFanDirection(String direction) {
    sendCommand("FAN", "DIR", "SET;${direction}")
}

def reverseFan() {
    if (device.currentValue("fanDirection") == "FWD") {
        setFanDirection("REV")
    } else {
        setFanDirection("FWD")
    }
}

def refresh() {
    sendCommand("LIGHT", "LEVEL", "GET;ACTUAL")
    sendCommand("FAN", "DIR", "GET")
}

def parse(String description) {
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
                    def events = [];
                    if (values[4] == "0") {
                        events << createEvent(name: "switch", value: "off")
                    } else {
                        events << createEvent(name: "switch", value: "on")
                    }
                    level = Math.round(values[4].toInteger() * 6.25)
                    log.debug "Using new level ${level}"
                    events << createEvent(name: "level", value: level)
                    return events;
            }
            break
        case "FAN":
            switch (values[2]) {
                case "PWR":
                    refreshFanSpeed()
                    return createEvent(name: "speed", value: values[3].toLowerCase())
                case "SPD":
                    switch (values[4]) {
                        case "0":
                            return createEvent(name: "speed", value: "off")
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
                case "DIR":
                    refreshFanSpeed()
                    return createEvent(name: "fanDirection", value: values[3])
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
    if (level > 100) {
        level = 100
    }
    if (level < 0) {
        level = 0
    }
    
    Integer haikuLevel = Math.round(level / 6.25)
    log.debug "level [${level}] haikuLevel [${haikuLevel}]"

    sendCommand("LIGHT", "LEVEL", "SET;${haikuLevel}")
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
