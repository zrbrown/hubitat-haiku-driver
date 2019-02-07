metadata {
    definition(name: "Haiku Fan", namespace: "community", author: "Zack Brown") {
        capability "FanControl"
        capability "SwitchLevel"
    }
}

preferences {
    section("Device Selection") {
        input("haikuDevice", "enum", title: "Select Device", description: "", required: true, defaultValue: "", options: getAllHaikuDevices())
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def getAllHaikuDevices() {
    def udpListeningSocket = null
    try {
        udpListeningSocket = new DatagramSocket(31415)
        udpListeningSocket.setSoTimeout(1000);
    } catch (BindException e) {
        log.error "getAllHaikuDevices: Haiku port 31415 on Hubitat is in use"
    }

    try {
        def haikuCommand = generateCommand("ALL", "DEVICE", "ID", "GET")

        def hubAction = new hubitat.device.HubAction(haikuCommand,
                hubitat.device.Protocol.LAN,
                [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
                 destinationAddress: "192.168.1.255:31415"])
        sendHubCommand(hubAction)

        def optionsMap = new LinkedHashMap()
        def start = new Date()
        while (new Date().getTime() - start.getTime() < 3000) {
            try {
                if (udpListeningSocket != null) {
                    def receiveData = new byte[128]
                    def receivePacket = new DatagramPacket(receiveData, receiveData.length)
                    udpListeningSocket.receive(receivePacket)
                    def data = receivePacket.getData()

                    def response = new String(data).trim()
                    if (!response.isEmpty() && response.charAt(0) == '(') {
                        String address = receivePacket.getAddress().getHostAddress()
                        if (logEnable) log.debug "Live signal from Haiku[${address}] ${response}"

                        def responseParts = response.tokenize(';')
                        def room = responseParts.get(0).substring(1)
                        def model = responseParts.get(4).substring(0, responseParts.get(4).length() - 1)
                        optionsMap[(address)] = "${room} [${model}]"
                    }
                }
            } catch (SocketTimeoutException e) {
            }
        }

        return optionsMap
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

def setLevel(level) {
    setLevel(level, 0)
}

def setLevel(level, duration) {
    if (logEnable) log.debug "setting level to ${level} for ${device.deviceNetworkId}"

    def udpListeningSocket = null
    try {
        udpListeningSocket = new DatagramSocket(31415)
    } catch (BindException e) {
        log.error "Haiku port 31415 on Hubitat is in use"
    }

    try {
        def haikuCommand = generateCommand("Bedroom Fan", "LIGHT", "PWR", "ON")

        def hubAction = new hubitat.device.HubAction(haikuCommand,
                hubitat.device.Protocol.LAN,
                [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
                 destinationAddress: "${settings.haikuDevice}:31415"])
        sendHubCommand(hubAction)

        if (udpListeningSocket != null) {
            def receiveData = new byte[128]
            def receivePacket = new DatagramPacket(receiveData, receiveData.length)
            udpListeningSocket.receive(receivePacket)
            def data = receivePacket.getData()

            def response = new String(data)
            def address = receivePacket.getAddress().getHostAddress()
            if (logEnable) log.debug "Response from Haiku[${address}] ${response}"
        }
    } catch (Exception e) {
        log.error "Call to on failed: ${e.message}"
    } finally {
        if (udpListeningSocket != null) {
            udpListeningSocket.close()
        }
    }
}

def setSpeed(fanspeed){
    if (logEnable) log.debug "in setspeed"
}

def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}"
}

static def generateCommand(location, device, function, command) {
    return "<${location};${device};${function};${command}>"
}