# 로컬 개발 서버 실행용 래퍼 스크립트
# .env 파일의 값을 환경변수로 주입한 뒤 gradlew bootRun을 실행한다.
# (build.gradle의 .env 자동 주입 로직은 `test` 태스크 전용이라 bootRun에는 적용되지 않음 — 그 공백을 메우는 스크립트)

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $idx = $line.IndexOf('=')
            $key = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim()
            if ($key) {
                [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
}

Set-Location $projectRoot
& "$projectRoot\gradlew.bat" bootRun
exit $LASTEXITCODE
