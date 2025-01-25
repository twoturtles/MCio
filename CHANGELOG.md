# Changelog

## 0.4.0+1.21.3 - 2025-01-XX
- Add key/button tracking
- Add clean_input action
- Add option to hide Minecraft window
- Add option to disable FPS limits and vsync
- Add raw frame support and make it the default.
  Raw frames are currently exported upside-down for
  performance reasons.
- MCIO_PROTOCOL_VERSION = 3

## 0.3.0+1.21.3 - 2025-01-14
- Change from zmq pub/sub to push/pull
- Action and observation ports are configurable via env variables
- Option to send frames as jpeg
- MCIO_PROTOCOL_VERSION = 2

## 0.2.0+1.21.3 - 2024-12-23
- Add Stop to protocol
- Reverse bind/connect for the action port
- MCIO_PROTOCOL_VERSION = 1

## 0.1.2+1.21.3 - 2024-12-16
- Default to mode ASYNC
- MCIO_PROTOCOL_VERSION = 0

## 0.1.1+1.21.3 - 2024-12-08
- Fix frame alignment issue
- MCIO_PROTOCOL_VERSION = 0

## 0.1.0+1.21.3 - 2024-12-05
- Initial release of the **MCio** mod for Minecraft.
- MCIO_PROTOCOL_VERSION = 0
