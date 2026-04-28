Patch the lsfg-vk layer (`~/Developer/LSFG-Android/lsfg-vk-android/src/`) to use Android-compatible Vulkan resource sharing instead of OPAQUE_FD.

## Problem
The layer creates images and semaphores with `VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT` / `VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT_KHR` for cross-process FD sharing with the DLL. Turnip/Mesa on Android returns `VK_ERROR_INVALID_EXTERNAL_HANDLE` (-1000072003) for these — OPAQUE_FD is not supported on Android GPUs.

## Solution
Switch to the AHardwareBuffer-based path that `LSFG_3_1::createContextFromAHB()` provides (already exists in the framegen API, used by the LSFG-Android-Application). Use `presentContext(id, -1, {})` + `waitIdle()` for sync instead of semaphore FDs.

## Checklist
- [ ] **Mini::Image Android variant** — Add `#ifdef __ANDROID__` path in `image.cpp` / `image.hpp` that creates images backed by AHardwareBuffer (no OPAQUE_FD export). The AHB is shared with framegen via `createContextFromAHB`.
- [ ] **LsContext constructor** — Under `#ifdef __ANDROID__`, create AHB-backed input/output images and call `LSFG_3_1::createContextFromAHB()` / `LSFG_3_1P::createContextFromAHB()` instead of the FD-based `createContext()`.
- [ ] **LsContext::present** — Under `#ifdef __ANDROID__`, call `presentContext(*lsfgCtxId, -1, {})` + `waitIdle()` instead of creating exported semaphores and passing FDs. Replace the semaphore-based render pipeline with vkDeviceWaitIdle or fence-based synchronization.
- [ ] **Mini::Semaphore** — Remove `Semaphore(device, &fd)` usage from Android path. Keep basic `Semaphore(device)` which works fine (no export).
- [ ] **Rebuild** — Rebuild the layer .so with `c++_static`, `LSFGVK_ANDROID_WINE=ON`, `VK_USE_PLATFORM_ANDROID_KHR=1`, strip, copy to GameNative assets, bump RUNTIME_VERSION, build+install APK.
- [ ] **Test** — Verify Alan Wake launches and LSFG layer initializes without the semaphore error.

## Key files
- `~/Developer/LSFG-Android/lsfg-vk-android/src/context.cpp` — Main file to patch
- `~/Developer/LSFG-Android/lsfg-vk-android/src/mini/image.cpp` + `image.hpp` — Add AHB-backed image
- `~/Developer/LSFG-Android/lsfg-vk-android/src/mini/semaphore.cpp` + `semaphore.hpp` — No changes needed (basic constructor works)
- `~/Developer/LSFG-Android/lsfg-vk-android/framegen/public/lsfg_3_1.hpp` — API reference (createContextFromAHB, waitIdle, presentContext)
- `~/Developer/LSFG-Android/LSFG-Android-Application/app/src/main/cpp/lsfg_render_loop.cpp` — Reference implementation for AHB path
- `~/Developer/LSFG-Android/LSFG-Android-Application/app/src/main/cpp/ahb_image_bridge.cpp` — AHB image helper functions
- GameNative: `app/src/main/java/app/gamenative/utils/LsfgVkManager.kt` — RUNTIME_VERSION bump

## Reference: LSFG-Android-Application AHB flow
```
// Context creation
g.framegenCtxId = LSFG_3_1::createContextFromAHB(
    g.inSlot[0].ahb, g.inSlot[1].ahb, outAhbs,
    g.inSlot[0].extent, g.inSlot[0].format);

// Present (no semaphore FDs)
LSFG_3_1::presentContext(g.framegenCtxId, -1, {});
LSFG_3_1::waitIdle();  // blocks until framegen GPU work done
```

## Constraints
- Layer is built with NDK 27, arm64-v8a, API 26, c++_static, Release
- `LSFGVK_ANDROID_WINE=ON` and `VK_USE_PLATFORM_ANDROID_KHR=1` are set at build time, so `__ANDROID__` is defined
- The OPAQUE_FD desktop path must remain intact for non-Android builds (use `#ifdef __ANDROID__`)
- AHardwareBuffer requires `<android/hardware_buffer.h>` and linking against `-landroid` (NDK provides this)
- Framegen's internal Vulkan device shares AHB storage — we keep ownership/refcount on our side
