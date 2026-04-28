Patch the lsfg-vk layer (`~/Developer/LSFG-Android/lsfg-vk-android/src/`) to use Android-compatible Vulkan resource sharing instead of OPAQUE_FD.

## Problem
The layer creates images and semaphores with `VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT` / `VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT_KHR` for cross-process FD sharing with the DLL. Turnip/Mesa on Android returns `VK_ERROR_INVALID_EXTERNAL_HANDLE` (-1000072003) for these — OPAQUE_FD is not supported on Android GPUs.

## Solution
Switch to the AHardwareBuffer-based path that `LSFG_3_1::createContextFromAHB()` provides (already exists in the framegen API, used by the LSFG-Android-Application). Use `presentContext(id, -1, {})` + `waitIdle()` for sync instead of semaphore FDs.

## Checklist
- [x] **Mini::Image Android variant** — AHB-backed Image constructor using `vkGetImageMemoryRequirements` instead of `vkGetAndroidHardwareBufferPropertiesANDROID` (Vortek doesn't support the latter)
- [x] **LsContext constructor** — Android path uses `createContextFromAHB()`
- [x] **LsContext::present** — Android path uses `presentContext(-1, {})` + `waitIdle()`
- [x] **Mini::Semaphore** — Removed `Semaphore(device, &fd)` from Android path
- [x] **Layer AHB function** — Made `ovkGetAndroidHardwareBufferPropertiesANDROID` optional (won't crash if Vortek doesn't provide it)
- [x] **Rebuild** — Built, installed v1.4.0-android-arm64-v8a-ahb-no-props (versionCode 25)
- [x] **Vortek AHB serializer** — Added AHB pNext serialization to `~/Developer/vortek` (feat/ahb-extension-support branch) for when server can be rebuilt
- [ ] **Test** — Waiting for user to launch Alan Wake with LSFG enabled

## Bug history and fixes applied
1. **Stale layer manifest**: Old VkLayer_LS_frame_generation.json from a previous install was being loaded. Fix: LsfgVkManager now deletes stale files from all container home dirs.
2. **Wrong method**: LSFG code was in `execShellCommand()` (wineserver -k) instead of `execGuestProgram()` (game launch). Fix: Moved to execGuestProgram().
3. **TMPDIR crash**: Layer wrote `/tmp/lsfg-vk_last` (not writable on Android) and called `exit(1)`. Fix: Patched to use `TMPDIR` env var.
4. **OPAQUE_FD semaphore error**: `VK_ERROR_INVALID_EXTERNAL_HANDLE` (-1000072003) because Turnip doesn't support OPAQUE_FD semaphore export. Fix: Switched to AHB + waitIdle() path.
5. **Missing AHB function**: Vortek doesn't provide `vkGetAndroidHardwareBufferPropertiesANDROID`, causing device layer init to fail. Fix: Made it optional, bypassed with `vkGetImageMemoryRequirements` instead.

## Remaining risks
- **Vortek pNext serialization**: `VkImportAndroidHardwareBufferInfoANDROID` in `vkAllocateMemory`'s pNext chain must be serialized through Vortek's IPC. The Vortek repo has been patched but the server (libvortekrenderer.so) also needs the new request handler. The client-side serializer changes ARE in the current Vortek repo branch but NOT yet in the prebuilt server.
- **Framegen AHB context**: `LSFG_3_1::createContextFromAHB()` requires `VK_ANDROID_external_memory_android_hardware_buffer` extension. If Vortek reports this extension but can't pass through the AHB import calls, context creation will fail inside framegen.
- **waitIdle() latency**: Full device idle stalls on every frame. Will add latency vs desktop semaphore path.
