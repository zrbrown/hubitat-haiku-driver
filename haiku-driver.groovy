import groovy.transform.Field

metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
        capability "Switch"
        capability "Light"
        capability "Refresh"
        capability "Sensor"
        capability "Presence Sensor"

        command "reverseFan"
        command "getDeviceID"
        command "whooshOff"
        command "whooshOn"
        command "sleepOn"
        command "sleepOff"
        command "fanAutoOn"
        command "fanAutoOff"
        command "refreshFanSpeed"
        command "setFanLevel",["number"]

        attribute "fanDirection", "string"
        attribute "deviceID", "string"
        attribute "whoosh", "string"
        attribute "sleep", "string"
        attribute "fanAuto", "string"
        attribute "fanLevel", "number"
        attribute "lightPresent", "string"
        attribute "SNSROCCcurr", "string"
    }
}

preferences {
    section("Device Selection") {
        input("deviceName", "text", title: "Device Name", description: "", required: true, defaultValue: "")
        input("deviceIp", "text", title: "Device IP Address", description: "", required: true, defaultValue: "")
        input("deviceID", "text", title: "Device ID", description: "", required: true, defaultValue: "ALL")
        input("logEnable", "bool", title: "Enable debug logging", defaultValue: true)
    }
}

//
// Constants
//
// Number of light graduations Haiku supports.
@Field final int HAIKU_LIGHT_LEVELS = 16

// Ratio of light levels to percentage level. 1 Haiku light level every 6.25%
@Field final double HAIKU_LIGHT_SPREAD = (double)Math.ceil(100/HAIKU_LIGHT_LEVELS)

@Field final int HAIKU_FAN_LEVELS = 7

@Field final double HAIKU_FAN_SPREAD = (double)Math.ceil(100/HAIKU_FAN_LEVELS)

def installed() {
    log.debug "installed"
}

def updated() {
    log.debug "updated"
}

def setFanDirection(String direction) {
    sendCommand("FAN", "DIR", "SET;${direction}")
}

def getDeviceID() {
     sendCommand("DEVICE", "ID", "GET")   
}

def reverseFan() {
    if (device.currentValue("fanDirection") == "FWD") {
        setFanDirection("REV")
    } else {
        setFanDirection("FWD")
    }
}

def refresh() {
    sendCommand("DEVICE","LIGHT","GET")
    sendCommand("LIGHT", "LEVEL", "GET;ACTUAL")
    sendCommand("FAN", "WHOOSH", "GET;STATUS")
    sendCommand("SLEEP", "STATE", "GET")
    sendCommand("FAN", "AUTO", "GET")
    sendCommand("FAN", "DIR", "GET")
    sendCommand("SNSROCC","STATUS", "GET")
    sendCommand("SNSROCC","TIMEOUT", "GET;CURR")
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
                    return sendEvent(name: "switch", value: values[3].toLowerCase())
                case "LEVEL":
                    def events = [];
                    if (values[4] == "0") {
                        events << sendEvent(name: "switch", value: "off")
                    } else {
                        events << sendEvent(name: "switch", value: "on")
                    }
                    int level = (int)Math.ceil(values[4].toInteger() * HAIKU_LIGHT_SPREAD)
                    events << sendEvent(name: "level", value: level)
                    return events;
            }
            break
        case "FAN":
            switch (values[2]) {
                case "PWR":
                    refreshFanSpeed()
                    return sendEvent(name: "speed", value: values[3].toLowerCase())
                case "SPD":
                    int fanLevel = (int)Math.ceil(values[4].toInteger() * HAIKU_FAN_SPREAD)
                    sendEvent(name: "fanLevel", value: fanLevel)
                    switch (values[4]) {
                        case "0":
                            return sendEvent(name: "speed", value: "off")
                        case "1":
                            return sendEvent(name: "speed", value: "low")
                        case "2":
                            return sendEvent(name: "speed", value: "medium-low")
                        case "3":
                        case "4":
                            return sendEvent(name: "speed", value: "medium")
                        case "5":
                        case "6":
                            return sendEvent(name: "speed", value: "medium-high")
                        case "7":
                            return sendEvent(name: "speed", value: "high")
                    }
                    break
                case "AUTO":
                    return sendEvent(name: "fanAuto", value: values[3].toLowerCase())
                case "DIR":
                    refreshFanSpeed()
                    return sendEvent(name: "fanDirection", value: values[3].toLowerCase())
                case "WHOOSH":
                    return sendEvent(name: "whoosh", value: values[4].toLowerCase())
            }
            break
        case "SLEEP":
            return sendEvent(name: "sleep", value: values[3].toLowerCase())
        case "DEVICE":
            switch (values[2].toLowerCase()) {
                case "id":
                    return sendEvent(name: "deviceID", value: values[3])
                case "light":
                    return sendEvent(name: "lightPresent", value: values[3].toLowerCase())
            }
            break
        case "SNSROCC":
            switch (values[2].toLowerCase()){
                case "status":
                    switch (values[3].toLowerCase()){
                        case "occupied":
                            return sendEvent(name: "presence", value: "present")
                        case "unoccupied":
                            return sendEvent(name: "presence", value: "not present")
                    }
                    break
                case "timeout":
                    switch (values[3].toLowerCase()){
                        case "curr":
                            return sendEvent(name: "SNSROCCcurr", value: values[4])
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

def whooshOn() {
    sendFanWhooshCommand("ON")
}

def whooshOff() {
    sendFanWhooshCommand("OFF")
}

def fanAutoOn() {
    sendFanAutoCommand("ON")
}

def fanAutoOff() {
    sendFanAutoCommand("OFF")
}

def sleepOn() {
    sendFanSleepCommand("ON")
}

def sleepOff() {
    sendFanSleepCommand("OFF")
}

def sendLightPowerCommand(String command) {
    sendCommand("LIGHT", "PWR", command)
}

def sendFanWhooshCommand(String command) {
    sendCommand("FAN", "WHOOSH", command)
}

def sendFanAutoCommand(String command) {
    sendCommand("FAN", "AUTO", command)
}

def sendFanSleepCommand(String command) {
    sendCommand("SLEEP", "STATE", command)
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
    
    int haikuLevel = (int)Math.ceil(level / HAIKU_LIGHT_SPREAD)
    log.debug "level [${level}] haikuLevel [${haikuLevel}]"

    sendCommand("LIGHT", "LEVEL", "SET;${haikuLevel}")
}

def setFanLevel(level) {
    setFanLevel(level, 0)
}

def setFanLevel(level, duration) {
    sendFanLevel(level)
}

def sendFanLevel(level) {
    if (level > 100) {
        level = 100
    }
    if (level < 0) {
        level = 0
    }
    
    int haikuFanLevel = (int)Math.ceil(level / HAIKU_FAN_SPREAD)
    log.debug "Fan Spread [${HAIKU_FAN_SPREAD}]"
    log.debug "Fan level [${level}] haikuFanLevel [${haikuFanLevel}]"

    sendFanSpeedCommand(haikuFanLevel)
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
    log.debug "DeviceID [${settings.deviceID}] Device [${haikuSubDevice}] Function [${haikuFunction}] Command [${command}]"
        def haikuCommand = generateCommand(settings.deviceID,haikuSubDevice, haikuFunction, command)
        sendUDPRequest(settings.deviceIp, "31415", haikuCommand)
}

static def generateCommand(deviceID,haikuSubDevice, haikuFunction, command) {
    return "<${deviceID};${haikuSubDevice};${haikuFunction};${command}>"
}

def sendUDPRequest(address, port, payload) {
    def hubAction = new hubitat.device.HubAction(payload,
            hubitat.device.Protocol.LAN,
            [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
             destinationAddress: "${address}:${port}"])
    sendHubCommand(hubAction)
    pauseExecution(100)
}
