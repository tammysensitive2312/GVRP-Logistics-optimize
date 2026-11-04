<#
.SYNOPSIS
  SEC-001: give the Entry API its own MySQL account instead of root, with a fresh random password.

.DESCRIPTION
  Run by the user (not by an agent). Creates or re-keys `gvrp_app`@`%` with privileges on `gvrp_db` only,
  then writes `spring.datasource.username` / `spring.datasource.password` into the git-ignored
  GVRP_Entry_API/src/main/resources/application-local.properties. The new password is never printed.
  The MySQL root password is prompted by the mysql client and is not stored.

  The container `sembackend-db-1` belongs to another compose project; this script does NOT change root.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/security/rotate-db-app-user.ps1
#>
param(
  [string]$Container = 'sembackend-db-1',
  [string]$Database = 'gvrp_db',
  [string]$AppUser = 'gvrp_app'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$local = Join-Path $repo 'GVRP_Entry_API\src\main\resources\application-local.properties'
if (-not (Test-Path $local)) { throw "Missing $local (copy application-local.properties.example first)." }

# 32 random bytes -> URL-safe text without characters that need SQL or properties escaping.
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

$sql = @"
CREATE USER IF NOT EXISTS '$AppUser'@'%' IDENTIFIED BY '$password';
ALTER USER '$AppUser'@'%' IDENTIFIED BY '$password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER, EXECUTE, CREATE ROUTINE, ALTER ROUTINE, LOCK TABLES ON ``$Database``.* TO '$AppUser'@'%';
FLUSH PRIVILEGES;
"@
# Root password: prompted here (not echoed), passed to the client through MYSQL_PWD for this call only.
# The SQL (with the new app password) goes through stdin, not the command line.
$secure = Read-Host "MySQL root password of container '$Container'" -AsSecureString
$rootPwd = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$env:MYSQL_PWD = $rootPwd
try {
  $sql | docker exec -i -e MYSQL_PWD $Container mysql -uroot
  if ($LASTEXITCODE -ne 0) { throw 'mysql failed; nothing was written to application-local.properties.' }
} finally {
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  $rootPwd = $null
}

$text = [IO.File]::ReadAllText($local)
$nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
$lines = $text -split "`r?`n"
$found = @{ user = $false; pass = $false }
for ($i = 0; $i -lt $lines.Length; $i++) {
  if ($lines[$i] -match '^\s*spring\.datasource\.username\s*=') { $lines[$i] = "spring.datasource.username=$AppUser"; $found.user = $true }
  elseif ($lines[$i] -match '^\s*spring\.datasource\.password\s*=') { $lines[$i] = "spring.datasource.password=$password"; $found.pass = $true }
}
if (-not ($found.user -and $found.pass)) { throw 'datasource keys not found in application-local.properties; add them and re-run.' }
[IO.File]::WriteAllText($local, ($lines -join $nl), (New-Object Text.UTF8Encoding $false))
$password = $null
Write-Host "Done: Entry API now uses '$AppUser' on '$Database' (password written to application-local.properties, not shown). Restart the Entry API."
