metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
        capability "Switch"
    }
}

preferences {
    section("Device Selection") {
        input("haikuDevice", "enum", title: "Select Device", description: "", required: true, defaultValue: "", options: getAllHaikuDevices())
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    if (logEnable) log.debug "installed"
}

def updated() {
    if (logEnable) log.debug "updated"
}

def getAllHaikuDevices() {
    def udpListeningSocket = null
    try {
        def haikuCommand = generateCommand("ALL", "DEVICE", "ID", "GET")
        sendUDPRequest("192.168.1.255", "31415", haikuCommand)

        udpListeningSocket = new DatagramSocket(31415)
        udpListeningSocket.setSoTimeout(3000);
        def optionsMap = new LinkedHashMap()
        try {
            while (true) {
                def receiveData = new byte[128]
                def receivePacket = new DatagramPacket(receiveData, receiveData.length)
                udpListeningSocket.receive(receivePacket)
                def data = receivePacket.getData()

                def response = new String(data).trim()
                if (!response.isEmpty() && response.charAt(0) == '(') {
                    String address = receivePacket.getAddress().getHostAddress()
                    if (logEnable) log.debug "Alive signal from Haiku[${address}] ${response}"

                    def responseParts = response.tokenize(';')

                    if (responseParts.size() > 1 && responseParts.get(1) == "DEVICE") {
                        def room = responseParts.get(0).substring(1)
                        def model = responseParts.get(4).substring(0, responseParts.get(4).length() - 1)
                        optionsMap[("${address};${room}")] = "${room} [${model}]"
                    }
                }
            }
        } catch (SocketTimeoutException e) {
        }

        return optionsMap
    } catch (BindException e) {
        log.warn "getAllHaikuDevices: Haiku port 31415 on Hubitat is in use"
    } catch (Exception e) {
        log.error "Call to on failed: ${e.message}"
    } finally {
        if (udpListeningSocket != null) {
            udpListeningSocket.close()
        }
    }
    return []
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

def sendCommand(String haikuSubDevice, String haikuFunction, String command) {
    if (logEnable) log.debug "setting level to ${level} for ${device.deviceNetworkId}"

    def udpListeningSocket = null
    try {
        def ipAndRoom = settings.haikuDevice.tokenize(';')
        def haikuCommand = generateCommand(ipAndRoom.get(1), haikuSubDevice, haikuFunction, command)
        sendUDPRequest(ipAndRoom.get(0), "31415", haikuCommand)

        udpListeningSocket = new DatagramSocket(31415)
        def receiveData = new byte[128]
        def receivePacket = new DatagramPacket(receiveData, receiveData.length)
        udpListeningSocket.receive(receivePacket)
        def data = receivePacket.getData()

        def response = new String(data)
        def address = receivePacket.getAddress().getHostAddress()
        if (logEnable) log.debug "sendCommand: Response from Haiku[${address}] ${response}"
    } catch (BindException e) {
        log.warn "Haiku port 31415 on Hubitat is in use"
    } catch (Exception e) {
        log.error "Call to on failed: ${e.message}"
    } finally {
        if (udpListeningSocket != null) {
            udpListeningSocket.close()
        }
    }
}

def setLevel(level) {
    setLevel(level, 0)
}

def setLevel(level, duration) {

}

def setSpeed(fanspeed){
    if (logEnable) log.debug "in setspeed"
}

def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}"
}

static def generateCommand(haikuLocation, haikuSubDevice, haikuFunction, command) {
    return "<${haikuLocation};${haikuSubDevice};${haikuFunction};${command}>"
}

def sendUDPRequest(address, port, payload) {
    def hubAction = new hubitat.device.HubAction(payload,
            hubitat.device.Protocol.LAN,
            [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
             destinationAddress: "${address}:${port}"])
    sendHubCommand(hubAction)
}