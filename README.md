## GL Error Chat

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](http://www.gnu.org/licenses/lgpl-3.0.html)

Client-side Fabric debug mod for Minecraft 1.21.11: every OpenGL error polled by
`GlStateManager#_getError` (`GL11.glGetError`) is printed to the chat with as much detail as possible.

Based on [Fallen-Breath/fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template).

### What it prints

For every non-zero error returned by `GL11.glGetError`:

- the error code, both in decimal and hexadecimal, together with the name of the GL constant
  (e.g. `GL_INVALID_ENUM (0x0500)`)
- the call site and the stack trace of the code that polled the error
- the current thread and whether it is the render thread
- the game state (open screen, dimension, player position)
- the GL device information (backend, vendor, renderer, version, max texture size)

The message can be clicked to copy the whole report; the full report is always written to the game log as well.

Repeating errors are merged: the same error coming from the same call site within 5 seconds only produces one
one-line reminder instead of a full report, so a broken mod cannot flood the chat.

### Build

```
./gradlew build
```

The jar is written to `versions/1.21.11/build/libs/`.