@echo off
REM =====================================================================
REM 03_verify_checksums.bat
REM Xac nhan file dataset chua bi doi ke tu luc dong bang.
REM CHAY TRUOC MOI DOT BENCHMARK. Dataset lech thi ket qua khong so duoc.
REM
REM Exit code 0 = khop, 1 = lech hoac thieu file.
REM =====================================================================

setlocal enabledelayedexpansion
set OUT=..\datasets\vn6183
set FAILCOUNT=0

call :check schema.sql
call :check data.sql
call :check branches_scrubbed.sql

echo.
if %FAILCOUNT% GTR 0 (
    echo *** DATASET KHONG KHOP THAM CHIEU ^(%FAILCOUNT% van de^).
    echo *** Dung benchmark lai cho den khi giai quyet xong.
    endlocal
    exit /b 1
)
echo Dataset khop tham chieu. Co the chay benchmark.
endlocal
exit /b 0


:check
set "F=%~1"

if not exist "%OUT%\%F%" (
    echo [THIEU]  %F%  -- da chay 02_export_dataset_vn.bat chua?
    set /a FAILCOUNT+=1
    goto :eof
)
if not exist "%OUT%\%F%.sha256" (
    echo [THIEU]  %F%.sha256
    set /a FAILCOUNT+=1
    goto :eof
)

set "CUR="
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%OUT%\%F%" SHA256') do (
    if not defined CUR set "CUR=%%H"
)
set "REF="
for /f "skip=1 delims=" %%H in ('type "%OUT%\%F%.sha256"') do (
    if not defined REF set "REF=%%H"
)

REM certutil ban cu chen dau cach giua cac byte -- bo het truoc khi so
set "CUR=!CUR: =!"
set "REF=!REF: =!"

if /i "!CUR!"=="!REF!" (
    echo [OK]     %F%
) else (
    echo [LECH]   %F%
    echo          hien tai  : !CUR!
    echo          tham chieu: !REF!
    set /a FAILCOUNT+=1
)
goto :eof
