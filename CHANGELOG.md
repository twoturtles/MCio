# Changelog

## 1.3.0+1.21.3 - 2025-06-05
- Track built-in Minecraft stats and send updates to the agent
- Moving protocol to use options. This should help with performance
  as new data is added, and ease backward compatibility
- MCIO_PROTOCOL_VERSION = 6

## 1.2.0+1.21.3 - 2025-05-26
- Preload initial chunks in sync mode.
- Add Open-To-LAN support
- Default to the Steve skin.
  Also allow selection among default skins.
- MCIO_PROTOCOL_VERSION = 5

## 1.1.0+1.21.3 - 2025-05-16
- Fractional cursor positions. This allows for POV changes
  of < 0.15 degrees, matching MineRL.
- MCIO_PROTOCOL_VERSION = 5

## 1.0.0+1.21.3 - 2025-05-09
- MCIO_PROTOCOL_VERSION = 4

## 0.6.1+1.21.3 - 2025-05-05
- Update readme for mcio_ctrl
- MCIO_PROTOCOL_VERSION = 4

## 0.6.0+1.21.3 - 2025-05-03
- More consistent clear input behavior in sync mode
- Clear input also sets the mouse to a default position
- MCIO_PROTOCOL_VERSION = 4

## 0.5.0+1.21.3 - 2025-04-06
- Change protocol to combine keys and mouse buttons in the same list
- MCIO_PROTOCOL_VERSION = 4

## 0.4.0+1.21.3 - 2025-03-11
- Add key/button tracking
- Add clean_input action
- Add option to hide Minecraft window
- Add option to disable FPS limits and vsync
- Add raw frame support and make it the default.
  Raw frames are currently exported upside-down for
  performance reasons.
- Removed jpeg and png frame export. Too slow.
- Client and server tick fast in sync mode
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
