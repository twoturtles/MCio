# Minecraft Input/Output (MCio)

### [MCio mod](https://github.com/twoturtles/MCio) | [mcio_ctrl](https://github.com/twoturtles/mcio_ctrl) | [Documentation](https://github.com/twoturtles/mcio_ctrl/wiki) | [Discord](https://discord.gg/PBfdc27h4q)

MCio is a Fabric mod that exposes a network interface to Minecraft for AI research. It accepts keyboard/mouse input and sends back video frames and game state over ZeroMQ.

## Features

* Python library ([mcio_ctrl](https://github.com/twoturtles/mcio_ctrl)) with Gymnasium environments
* Faster than real-time performance (>13x on an M3 laptop)
* Works with performance mods like Sodium
* Supports resource packs for varied training environments
* Connect/reconnect agents without restarting Minecraft
* Decoupled ZMQ protocol for easy integration
* Synchronous mode for fast training
* Asynchronous mode for real-time human/AI interaction
* Headless mode with GPU acceleration
* [VPT and STEVE-1 support](https://github.com/jxiong21029/mcio-vpt-example) on modern Minecraft with [Sodium](https://modrinth.com/mod/sodium)

## Links

* [Documentation / Wiki](https://github.com/twoturtles/mcio_ctrl/wiki)
* [MCio mod](https://github.com/twoturtles/MCio) ([Modrinth](https://modrinth.com/mod/mcio))
* [mcio_ctrl](https://github.com/twoturtles/mcio_ctrl) ([PyPI](https://pypi.org/project/mcio_ctrl/))
