param([switch]$ConnectedTests)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    # Work around a host Java AF_UNIX loopback failure without changing system settings.
    # The path is intentionally nonexistent so Java falls back to TCP for its NIO pipe.
    $priorJavaOptions = $env:JAVA_TOOL_OPTIONS
    $env:JAVA_TOOL_OPTIONS = "$priorJavaOptions -Djdk.net.unixdomain.tmpdir=C:/sg-nonexistent-socket-dir".Trim()
    & .\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build/checks failed.' }
    if ($ConnectedTests) {
        & .\gradlew.bat :app:connectedDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Connected Android tests failed.' }
    }
} finally {
    $env:JAVA_TOOL_OPTIONS = $priorJavaOptions
    Pop-Location
}
