Patch the lsfg-vk layer (`~/Developer/LSFG-Android/lsfg-vk-android/src/`) to use Android-compatible Vulkan resource sharing instead of OPAQUE_FD.

## Problem
The layer creates images and semaphores with `VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT` / `VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT_KHR` for cross-process FD sharing with the DLL. Turnip/Mesa on Android returns `VK_ERROR_INVALID_EXTERNAL_HANDLE` (-1000072003) for these — OPAQUE_FD is not supported on Android GPUs.

## Solution
Switch to the AHardwareBuffer-based path that `LSFG_3_1::createContextFromAHB()` provides (already exists in the framegen API, used by the LSFG-Android-Application). Use `presentContext(id, -1, {})` + `waitIdle()` for sync instead of semaphore FDs.

## Checklist
- [x] **Mini::Image Android variant** — Added AHB-backed Image constructor + getAhb() accessor
- [x] **LsContext constructor** — Android path uses createContextFromAHB()
- [x] **LsContext::present** — Android path uses presentContext(-1, {}) + waitIdle()
- [x] **Mini::Semaphore** — Removed Semaphore(device, &fd) from Android path
- [x] **Rebuild** — Built, installed v1.3.0-android-arm64-v8a-ahb (versionCode 24)
- [ ] **Test** — Waiting for user to launch Alan Wake with LSFG enabled

## Reflection (Iteration 3)

### What's been accomplished
All code changes are done and the APK is installed. The layer now has a complete Android AHB path that avoids OPAQUE_FD entirely:
- Image creation: AHardwareBuffer-backed VkImages instead of OPAQUE_FD export
- Context creation: `createContextFromAHB()` instead of `createContext(fds)`
- Presentation: `presentContext(-1, {})` + `waitIdle()` instead of semaphore FD sync
- Layer dispatch: Added `ovkGetAndroidHardwareBufferPropertiesANDROID` for AHB property queries

### What's working well
- Build compiles cleanly with NDK 27, c++_static, arm64-v8a
- Desktop Linux path preserved behind `#ifdef __ANDROID__` / `#else` guards
- Previous fixes still in place: stale manifest cleanup, correct env var wiring, TMPDIR patch

### What's not working / blocking
- **User hasn't tested yet** — the AHB patch is installed but we don't know if it works
- Potential concern: `waitIdle()` is a full pipeline stall — it will add latency vs the semaphore-based desktop path, but this is the only correct sync mechanism available on Android without OPAQUE_FD
- Potential concern: The Vortek wrapper intercepts all Vulkan calls including `vkGetAndroidHardwareBufferPropertiesANDROID` — it may not pass this through to Turnip correctly. If framegen's `createContextFromAHB` fails, we may need to check if Vortek supports AHB Vulkan extensions.

### Should the approach be adjusted?
No — the AHB approach is exactly what the LSFG-Android-Application uses successfully. The main unknown is whether Vortek's wrapper layer properly forwards AHB-related Vulkan calls to Turnip. If it doesn't, we may need to bypass Vortek for AHB operations.

### Next priorities
1. **Test** — Get the user to launch and check logcat
2. If AHB creation fails: check if Vortek wraps `vkGetAndroidHardwareBufferPropertiesANDROID` — may need a workaround
3. If context creation succeeds but present fails: debug the waitIdle/present flow
4. Once working: optimize the waitIdle stall (could use fences instead of full device idle)
