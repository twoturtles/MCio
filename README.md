
# Minecraft Input / Output (MCio)

MCio is a Fabric mod that creates a network interface to Minecraft, enabling programmatic control through simulated keyboard/mouse inputs while providing video frame output and state information. It's designed primarily for AI researchers developing agents in the Minecraft environment.

## Key Features

- **Gymnasium Environment**: Synchronous mode provides a step-based interface compatible with the Gymnasium RL library. The python module includes a Gymnasium 1.0 environment.
- **Asynchronous Mode**: Real-time interface where the game continues regardless of agent action timing.
- **Python Integration**: Comes with a companion Python module ([mcio_remote](https://github.com/twoturtles/mcio_remote)) for seamless interaction with the mod
- **Low-Level Interface**: Operates at the pixel/keyboard/mouse level, similar to MineRL, rather than API-level interaction like Mineflayer

## Why Choose MCio?

MCio aims to lower the barrier to entry for Minecraft AI research:

- **Easy Setup**: Skip the Java build process - just install the mod and pip module
- **Modern Foundation**: Built on Fabric, making it more maintainable for future Minecraft versions
- **Streamlined Development**: Get straight to reinforcement learning without infrastructure hurdles

## Current Status

This is an early release focused on core functionality. While it does not yet match MineRL's extensive feature set, we're actively developing MCio. In particular, currently there is no automatic world creation. I hope to add those types of features in a separate repo, keeping the core MCio as simple as possible We welcome feedback from MineRL users on features they'd need for potential migration.

## Getting Started

For setup instructions and documentation, visit our GitHub repositories:
- Mod: [MCio](https://github.com/twoturtles/MCio)
- Python Module: [mcio_remote](https://github.com/twoturtles/mcio_remote)

