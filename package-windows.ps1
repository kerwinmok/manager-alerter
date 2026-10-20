param(
    [ValidateSet('app-image', 'exe')]
    [string]$PackageType = 'app-image'
)

$ErrorActionPreference = 'Stop'

Push-Location $PSScriptRoot
try {
    $packageInput = Join-Path $PSScriptRoot 'target/package-input'
    if (Test-Path $packageInput) {
        Remove-Item $packageInput -Recurse -Force
    }

    & mvn -B test package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target/package-input/lib"
    if ($LASTEXITCODE -ne 0) {
        throw 'maven build failed'
    }

    $mainJar = 'manager-alerter-1.0.0.jar'
    Copy-Item (Join-Path $PSScriptRoot "target/$mainJar") $packageInput

    $opencvJar = Get-ChildItem (Join-Path $packageInput 'lib') -Filter 'opencv-*.jar' | Select-Object -First 1
    if (-not $opencvJar) {
        throw 'runtime dependencies were not copied'
    }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $trimmedJar = "$($opencvJar.FullName).trimmed"
    $sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($opencvJar.FullName)
    $targetArchive = [System.IO.Compression.ZipFile]::Open($trimmedJar, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $sourceArchive.Entries) {
            if ($entry.FullName -match '^nu/pattern/opencv/(linux|osx)/' -or
                $entry.FullName -match '^nu/pattern/opencv/windows/x86_32/') {
                continue
            }

            $copy = $targetArchive.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
            $inputStream = $entry.Open()
            $outputStream = $copy.Open()
            try {
                $inputStream.CopyTo($outputStream)
            }
            finally {
                $outputStream.Dispose()
                $inputStream.Dispose()
            }
        }
    }
    finally {
        $targetArchive.Dispose()
        $sourceArchive.Dispose()
    }
    Move-Item $trimmedJar $opencvJar.FullName -Force

    $distribution = Join-Path $PSScriptRoot 'dist'
    $appImage = Join-Path $distribution 'DoorwayAlert'
    $archive = Join-Path $distribution 'DoorwayAlert-portable.zip'
    New-Item -ItemType Directory -Force -Path $distribution | Out-Null

    $opencvSizeMb = [Math]::Round($opencvJar.Length / 1MB, 1)

    if (Test-Path $appImage) {
        Remove-Item $appImage -Recurse -Force
    }
    if (Test-Path $archive) {
        Remove-Item $archive -Force
    }

    $jpackageArgs = @(
        '--type', $PackageType,
        '--name', 'DoorwayAlert',
        '--app-version', '1.0.0',
        '--vendor', 'Kerwin Mok',
        '--input', $packageInput,
        '--main-jar', $mainJar,
        '--main-class', 'com.kerwinmok.manageralerter.ManagerAlerterApp',
        '--dest', $distribution
    )
    if ($PackageType -eq 'exe') {
        $jpackageArgs += @('--win-menu', '--win-shortcut', '--win-menu-group', 'Doorway Alert')
    }

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'jpackage failed'
    }

    if ($PackageType -eq 'app-image') {
        Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $archive
        Write-Output "portable app created at $archive (windows x64 opencv, $opencvSizeMb mb staged)"
    }
    else {
        Write-Output "windows installer created in $distribution (windows x64 opencv, $opencvSizeMb mb staged)"
    }
}
finally {
    Pop-Location
}