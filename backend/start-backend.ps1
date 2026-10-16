
$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    throw "Backend .env file not found: $envFile"
}

foreach ($line in Get-Content $envFile) {
    $line = $line.Trim()

    if (-not $line -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -lt 1) {
        continue
    }

    $key = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()

    if ($key -notin @("GITHUB_TOKEN", "OPENROUTER_API_KEY")) {
        continue
    }

    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "The $key entry in .env is empty."
    }

    Set-Item -Path "Env:$key" -Value $value
}

foreach ($key in @("GITHUB_TOKEN", "OPENROUTER_API_KEY")) {
    $value = (Get-Item "Env:$key" -ErrorAction SilentlyContinue).Value

    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required credential: $key"
    }
}

Write-Host "Credentials loaded. Values are hidden."
Set-Location $PSScriptRoot
& .\mvnw.cmd spring-boot:run
