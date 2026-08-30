# Contributing to Termdroid

Thank you for your interest in contributing to Termdroid!

## Development Guidelines

1. **Keep PRs focused**: Solve one root cause per PR with the minimal coherent diff.
2. **Modern SDK Standards**: Target modern Android SDKs (API 37+). Do not lower `targetSdk`.
3. **Graceful Degradation**: Always probe capabilities at runtime instead of hardcoding `Build.VERSION` checks.
4. **Security First**: Privileged tools (`ToolRisk.PRIVILEGED`) must always require explicit user confirmation.
5. **No Orphan Storage**: All data, caches, and runtimes must reside inside `context.filesDir` or `context.cacheDir`.

## Building and Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```
