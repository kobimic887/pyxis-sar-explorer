$ErrorActionPreference = 'Stop'
$out = Join-Path $PWD 'packaged-check/installed'
New-Item -ItemType Directory -Force $out | Out-Null
$msi = (Get-ChildItem installers/*.msi | Select-Object -First 1).FullName
$installed = $false
try {
    $p = Start-Process msiexec.exe -ArgumentList @('/i', "`"$msi`"", '/qn', '/norestart', '/L*v', "`"$out/install.log`"") -Wait -PassThru
    if ($p.ExitCode -notin @(0,3010)) { throw "MSI install failed: $($p.ExitCode)" }
    $installed = $true
    $launcher = Join-Path $env:ProgramFiles 'Pyxis SAR Explorer/Pyxis SAR Explorer.exe'
    if (!(Test-Path $launcher)) { throw "Installed launcher missing: $launcher" }
    Write-Output "PASS: MSI installed per-machine launcher at $launcher"
    foreach ($ext in @('dwar','dwam','dwat')) {
        $key = Get-Item "Registry::HKEY_LOCAL_MACHINE\Software\Classes\.$ext"
        $progId = $key.GetValue('')
        $command = (Get-Item "Registry::HKEY_LOCAL_MACHINE\Software\Classes\$progId\shell\open\command").GetValue('')
        if (!$command.Contains('Pyxis SAR Explorer') -or !$command.Contains('%1')) { throw "Bad association: $command" }
        "$ext => $progId => $command" | Tee-Object -FilePath "$out/associations.txt" -Append
    }
    python pyxis/check-packaged-app.py "$launcher" --evidence-dir packaged-check/installed/macro
    if ($LASTEXITCODE -ne 0) { throw 'Installed macro check failed' }
    python pyxis/check-packaged-app.py "$launcher" --document --evidence-dir packaged-check/installed/document
    if ($LASTEXITCODE -ne 0) { throw 'Installed .dwar argument check failed' }
} finally {
    if ($installed) {
        $p = Start-Process msiexec.exe -ArgumentList @('/x', "`"$msi`"", '/qn', '/norestart', '/L*v', "`"$out/uninstall.log`"") -Wait -PassThru
        if ($p.ExitCode -notin @(0,3010)) { throw "MSI uninstall failed: $($p.ExitCode)" }
        Write-Output 'PASS: MSI uninstalled'
    }
}
