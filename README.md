# Minecraft Input/Output (MCio)

### [MCio mod](https://github.com/twoturtles/MCio) | [mcio_ctrl](https://github.com/twoturtles/mcio_ctrl) | [Documentation](https://github.com/twoturtles/mcio_ctrl/wiki) | [Discord](https://discord.gg/vaFEBynG)

MCio is a Fabric mod that provides a streamlined, high-performance network interface to Minecraft, specifically tailored for AI research. It allows for seamless programmatic control via simulated keyboard and mouse inputs and delivers real-time video frames and game state information through a ZeroMQ (ZMQ) interface.

## Key Features

* **Modern, Extensible Design:** Built using modern tooling for easier support and rapid updates to newer Minecraft versions.
* **Mod Compatibility:** Seamlessly integrates with performance-enhancing mods like Sodium to significantly boost training speeds.
* **Customizable Environment:** Supports resource packs for varying the appearance and textures in Minecraft, enabling diverse training scenarios.
* **Rapid Development Cycle:** Quickly start, connect, and reconnect AI agents without needing to restart Minecraft, streamlining experimentation and debugging.
* **Clear, Defined Interface:** Uses a well-structured and decoupled protocol, simplifying integration with external systems and agents.
* **Companion Python Library:** Comes with a comprehensive Python library (`mcio_ctrl`) that facilitates quick and intuitive interfacing with the mod.
* **Synchronous Mode:** Optimized for high-speed AI training, allowing fast and efficient simulations.
* **Asynchronous Mode:** Ideal for real-time interaction and play, allowing humans and AI agents to simultaneously engage within the same Minecraft environment.
* **Headless Support:** Easily run Minecraft in headless mode with GPU acceleration, facilitating efficient, remote, and automated AI training setups.

## Quick Links

* **Documentation:** Explore detailed documentation and user guides on our [Wiki](https://github.com/twoturtles/mcio_ctrl/wiki).

* **MCio Mod:**
  * [GitHub Repository](https://github.com/twoturtles/MCio)
  * [Modrinth Project Page](https://modrinth.com/mod/mcio)

* **Python Interface (`mcio_ctrl`):**
  * [GitHub Repository](https://github.com/twoturtles/mcio_ctrl)
  * [PyPI Package](https://pypi.org/project/mcio_ctrl/)
