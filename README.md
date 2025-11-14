# teaclave-storm

Crash triage harness for exercising Apache Teaclave TEE SDK enclaves under different thread-affinity scenarios.

## Overview

- Designed to isolate and document a `StackOverflowError` that occurs when tearing down a Teaclave Substrate VM enclave from a thread other than the one that created it.
- Provides a minimal local workload (`SimpleService`) that reliably reproduces the failure for further analysis or thesis citation.
- All commands run inside the repository root; no external services are required.

## Local Tests

Trigger the full set of enclave lifecycle scenarios with:

```bash
make run-local
```

The run spins up the topology once and executes four deterministic experiments before the crash occurs. Each experiment logs thread IDs for enclave creation, service invocation, and destruction so that thread-affinity issues are easy to spot.

### Test Matrix

| Test | Lifecycle Description | Expected Result | Notes |
| --- | --- | --- | --- |
| 1 | Create + use enclave on worker thread, destroy on main thread | ✅ Pass | Validates baseline request/response flow. |
| 2 | Create on main thread, use on worker thread, destroy on main thread | ✅ Pass | Shows that cross-thread *use* is tolerated while lifecycle stays on creator thread. |
| 3 | Create, use, destroy entirely in worker thread | ✅ Pass | Confirms Graal/Teaclave behave when a single thread owns the enclave. |
| 4 | Create + use enclave on worker thread, destroy on main thread | ❌ Fails with `StackOverflowError` | Reproduces the teardown crash described below. |

An excerpt from the failing run (test 4) looks like:

```
Response from enclave in thread 30: 4 daerht morf olleH
Done
Fatal error: StackOverflowError: Enabling the yellow zone of the stack did not make any stack space available.
...
com.oracle.svm.core.thread.VMThreads.detachAllThreadsExceptCurrentWithoutCleanupForTearDown
```

## Crash Investigation Summary

### Reproduction

1. Run `make run-local`.
2. Allow the four experiments to complete; no external input is needed.
3. During experiment 4 the harness intentionally tears down the enclave from a different Java thread than the one that created it.
4. `SimpleService`’s enclave destruction then triggers the fatal `StackOverflowError`.

### Findings

- The Teaclave Java SDK (0.1.0) stores the `isolateThreadHandle` captured during enclave creation and requires teardown to occur on the same thread.
- When test 4 invokes `enclave.destroy()` from the main thread, Graal/Substrate receives a stale `IsolateThread`. The VM cannot re-establish the stack “yellow zone” and aborts with the observed `StackOverflowError`.
- Diagnostics show `StackOverflowCheckImpl.stackBoundaryTL = 1` and the stack trace ends in `CEntryPointNativeFunctions.detachAllThreadsAndTearDownIsolate`, which matches Teaclave’s native teardown path and confirms the thread mismatch.

### Mitigations / Next Steps

1. Keep local development on `MOCK_IN_JVM` / `MOCK_IN_SVM` backends until enclave lifecycle management is refactored.
2. Ensure the same Java thread that created the enclave also destroys it—e.g., wrap the enclave in a single-threaded executor and run `destroy()` via a final task submitted from `cleanup()`.
3. Long term, consider extending the Teaclave host SDK so isolates can be detached from a different thread or re-bound before teardown.

## References

- Teaclave host SDK internals: inspect `org.apache.teaclave.javasdk.host.TeeSdkEnclave` inside `tools/javaenclave/jar/sdk/host/host-0.1.0.jar`.
- Crash logs from the local harness (see `make run-local` output) provide reproducible evidence for thesis documentation.
